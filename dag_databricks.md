1️⃣ Authentication model (Azure AD → Databricks)
Databricks on Azure accepts AAD access tokens with:

resource = https://databricks.azure.net/
Required headers for Databricks REST
Authorization: Bearer <AAD_ACCESS_TOKEN>
2️⃣ Airflow Connections & Variables (recommended)
Airflow Connection: azure_databricks
Field	Value
Host	https://<your-workspace>.azuredatabricks.net
Conn Type	HTTP
Airflow Variables (Service Principal option)
AZURE_TENANT_ID
AZURE_CLIENT_ID
AZURE_CLIENT_SECRET
If you’re using Managed Identity, you do not need these variables.

3️⃣ Helper: Azure AD token provider
azure_auth.py
import os
from azure.identity import (
ClientSecretCredential,
ManagedIdentityCredential
)

DATABRICKS_RESOURCE = "https://databricks.azure.net/"

def get_aad_token():
"""
Returns an Azure AD access token for Azure Databricks.
Supports Managed Identity and Service Principal.
"""
tenant_id = os.getenv("AZURE_TENANT_ID")
client_id = os.getenv("AZURE_CLIENT_ID")
client_secret = os.getenv("AZURE_CLIENT_SECRET")

    if client_id and client_secret and tenant_id:
        credential = ClientSecretCredential(
            tenant_id=tenant_id,
            client_id=client_id,
            client_secret=client_secret
        )
    else:
        # Managed Identity (VM, AKS, App Service, etc.)
        credential = ManagedIdentityCredential()

    token = credential.get_token(f"{DATABRICKS_RESOURCE}.default")
    return token.token
4️⃣ Databricks REST client (clean abstraction)
databricks_client.py
import requests
from airflow.hooks.base import BaseHook
from azure_auth import get_aad_token


class DatabricksClient:
def __init__(self, conn_id="azure_databricks"):
conn = BaseHook.get_connection(conn_id)
self.base_url = conn.host.rstrip("/")

    def _headers(self):
        return {
            "Authorization": f"Bearer {get_aad_token()}",
            "Content-Type": "application/json"
        }

    def get(self, endpoint, params=None):
        url = f"{self.base_url}{endpoint}"
        response = requests.get(
            url,
            headers=self._headers(),
            params=params,
            timeout=30
        )
        response.raise_for_status()
        return response.json()

    # ---- API wrappers ----

    def list_job_runs(self, job_id=None, limit=25):
        params = {"limit": limit}
        if job_id:
            params["job_id"] = job_id
        return self.get("/api/2.1/jobs/runs/list", params)

    def get_job_run(self, run_id):
        return self.get(
            "/api/2.1/jobs/runs/get",
            {"run_id": run_id}
        )

    def get_instance_pool(self, instance_pool_id):
        return self.get(
            "/api/2.0/instance-pools/get",
            {"instance_pool_id": instance_pool_id}
        )
5️⃣ Airflow DAG
databricks_metadata_dag.py
from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime, timedelta

from databricks_client import DatabricksClient


default_args = {
"owner": "data-platform",
"depends_on_past": False,
"retries": 2,
"retry_delay": timedelta(minutes=5),
}

def fetch_job_runs(**context):
client = DatabricksClient()

    runs = client.list_job_runs(limit=10)
    context["ti"].xcom_push(key="job_runs", value=runs)

def fetch_single_run(**context):
runs = context["ti"].xcom_pull(
task_ids="list_job_runs",
key="job_runs"
)

    if not runs.get("runs"):
        return

    run_id = runs["runs"][0]["run_id"]
    client = DatabricksClient()
    run_details = client.get_job_run(run_id)

    context["ti"].xcom_push(
        key="run_details",
        value=run_details
    )

def fetch_instance_pool(**context):
client = DatabricksClient()

    # Example instance pool id (could come from config / XCom)
    instance_pool_id = "your-instance-pool-id"

    pool_details = client.get_instance_pool(instance_pool_id)
    context["ti"].xcom_push(
        key="instance_pool",
        value=pool_details
    )


with DAG(
dag_id="databricks_metadata_collector",
start_date=datetime(2024, 1, 1),
schedule_interval="@hourly",
catchup=False,
default_args=default_args,
tags=["databricks", "azure", "metadata"],
) as dag:

    list_job_runs = PythonOperator(
        task_id="list_job_runs",
        python_callable=fetch_job_runs
    )

    get_job_run = PythonOperator(
        task_id="get_job_run",
        python_callable=fetch_single_run
    )

    get_instance_pool = PythonOperator(
        task_id="get_instance_pool",
        python_callable=fetch_instance_pool
    )

    list_job_runs >> get_job_run
    list_job_runs >> get_instance_pool
6️⃣ Security & production tips (important)
✅ Token lifetime
AAD tokens are short-lived → fetch per request (as shown)

✅ RBAC
Your AAD identity must have:

Workspace access

Jobs Viewer (or higher)

Instance Pool Viewer

✅ Rate limiting
Databricks APIs will rate-limit:

Add retries with backoff if calling in loops

✅ Secrets
Prefer Managed Identity

Otherwise store SP secrets in Azure Key Vault → Airflow Secrets Backend

7️⃣ Want next?
I can:

Convert this into an Airflow custom hook

Add pagination handling

Persist metadata into Delta / Azure SQL

Switch to deferrable operators

Show PAT-based fallback (if AAD is not enabled)

Just tell me where you want to take it 👌

--------------------------------

Convert this into an Airflow custom hook:

Perfect next step 👍 — this absolutely belongs in a custom hook.

Below is a clean, production-ready Airflow custom hook for Azure Databricks with Azure AD authentication, built to:

Use Managed Identity or Service Principal

Handle AAD token acquisition

Expose typed Databricks REST methods

Plug cleanly into DAGs and Operators

I’ll keep it idiomatic Airflow and easy to extend.

📁 Recommended structure
airflow/
├── hooks/
│   └── azure_databricks_hook.py
├── dags/
│   └── databricks_metadata_dag.py
1️⃣ Custom Hook: AzureDatabricksHook
airflow/hooks/azure_databricks_hook.py
import os
import requests
from airflow.hooks.base import BaseHook
from airflow.exceptions import AirflowException

from azure.identity import (
ClientSecretCredential,
ManagedIdentityCredential
)


class AzureDatabricksHook(BaseHook):
"""
Airflow Hook for Azure Databricks using Azure AD authentication.
Supports Managed Identity and Service Principal.
"""

    conn_name_attr = "databricks_conn_id"
    default_conn_name = "azure_databricks"
    conn_type = "azure_databricks"
    hook_name = "Azure Databricks (AAD)"

    DATABRICKS_RESOURCE = "https://databricks.azure.net/"

    def __init__(self, databricks_conn_id=None):
        super().__init__()
        self.databricks_conn_id = databricks_conn_id or self.default_conn_name
        self._base_url = None

    # ---------- Connection / Auth ----------

    def get_base_url(self):
        if not self._base_url:
            conn = self.get_connection(self.databricks_conn_id)
            if not conn.host:
                raise AirflowException(
                    "Azure Databricks connection must have a host "
                    "(https://<workspace>.azuredatabricks.net)"
                )
            self._base_url = conn.host.rstrip("/")
        return self._base_url

    def get_aad_token(self):
        """
        Acquire Azure AD token for Databricks.
        """
        tenant_id = os.getenv("AZURE_TENANT_ID")
        client_id = os.getenv("AZURE_CLIENT_ID")
        client_secret = os.getenv("AZURE_CLIENT_SECRET")

        if client_id and client_secret and tenant_id:
            self.log.debug("Using Service Principal authentication")
            credential = ClientSecretCredential(
                tenant_id=tenant_id,
                client_id=client_id,
                client_secret=client_secret,
            )
        else:
            self.log.debug("Using Managed Identity authentication")
            credential = ManagedIdentityCredential()

        token = credential.get_token(f"{self.DATABRICKS_RESOURCE}.default")
        return token.token

    def get_headers(self):
        return {
            "Authorization": f"Bearer {self.get_aad_token()}",
            "Content-Type": "application/json",
        }

    # ---------- Low-level REST ----------

    def _request(self, method, endpoint, params=None, json=None):
        url = f"{self.get_base_url()}{endpoint}"

        response = requests.request(
            method=method,
            url=url,
            headers=self.get_headers(),
            params=params,
            json=json,
            timeout=30,
        )

        if not response.ok:
            raise AirflowException(
                f"Databricks API call failed "
                f"[{response.status_code}]: {response.text}"
            )

        return response.json()

    def get(self, endpoint, params=None):
        return self._request("GET", endpoint, params=params)

    def post(self, endpoint, json=None):
        return self._request("POST", endpoint, json=json)

    # ---------- Databricks API wrappers ----------

    def list_job_runs(self, job_id=None, limit=25):
        params = {"limit": limit}
        if job_id:
            params["job_id"] = job_id
        return self.get("/api/2.1/jobs/runs/list", params=params)

    def get_job_run(self, run_id):
        return self.get(
            "/api/2.1/jobs/runs/get",
            params={"run_id": run_id},
        )

    def get_instance_pool(self, instance_pool_id):
        return self.get(
            "/api/2.0/instance-pools/get",
            params={"instance_pool_id": instance_pool_id},
        )
2️⃣ Usage in a DAG
airflow/dags/databricks_metadata_dag.py
from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime

from airflow.hooks.azure_databricks_hook import AzureDatabricksHook


def list_runs(**context):
hook = AzureDatabricksHook()
runs = hook.list_job_runs(limit=10)
context["ti"].xcom_push(key="runs", value=runs)


def get_first_run_details(**context):
runs = context["ti"].xcom_pull(task_ids="list_job_runs", key="runs")
if not runs.get("runs"):
return

    run_id = runs["runs"][0]["run_id"]
    hook = AzureDatabricksHook()
    run_details = hook.get_job_run(run_id)

    context["ti"].xcom_push(
        key="run_details",
        value=run_details,
    )


with DAG(
dag_id="databricks_metadata_collector",
start_date=datetime(2024, 1, 1),
schedule_interval="@hourly",
catchup=False,
tags=["databricks", "azure"],
) as dag:

    list_job_runs = PythonOperator(
        task_id="list_job_runs",
        python_callable=list_runs,
    )

    get_job_run = PythonOperator(
        task_id="get_job_run",
        python_callable=get_first_run_details,
    )

    list_job_runs >> get_job_run
3️⃣ Airflow Connection definition
Connection ID: azure_databricks

Field	Value
Conn Type	HTTP
Host	https://<workspace>.azuredatabricks.net
Auth is handled entirely via Azure AD — no credentials stored in the connection.

4️⃣ Why this hook design works well
✅ Stateless and thread-safe
✅ Token acquired per call (safe for short-lived AAD tokens)
✅ Easy to extend for new endpoints
✅ Clean error handling (fails DAG fast)
✅ Compatible with Airflow 2.x standards

5️⃣ Optional enhancements (very common in real systems)
If you want, I can add:

🔁 Automatic pagination for runs/list

🔄 Retry & exponential backoff inside the hook

🧪 Unit tests with mocked AAD + Databricks

🔐 Azure Key Vault integration

🧩 Convert this into a DatabricksOperator on top of the hook

Just tell me how “enterprise-grade” you want to go 😄



----------------------------



Love it — this is exactly the right hardening set for a real platform hook.
Below I’ll extend the hook with pagination, retries + exponential backoff, and then show unit tests with mocked AAD + Databricks.

I’ll keep everything Airflow-idiomatic and testable.

1️⃣ Updated Hook with Pagination + Retry/Backoff
Key design choices
Retry handled inside the hook, not the DAG

Exponential backoff with jitter

Pagination for jobs/runs/list using:

has_more

next_page_token

airflow/hooks/azure_databricks_hook.py
import os
import time
import random
import requests
from typing import Dict, List, Optional

from airflow.hooks.base import BaseHook
from airflow.exceptions import AirflowException

from azure.identity import (
ClientSecretCredential,
ManagedIdentityCredential,
)


class AzureDatabricksHook(BaseHook):
"""
Airflow Hook for Azure Databricks using Azure AD authentication.
"""

    conn_name_attr = "databricks_conn_id"
    default_conn_name = "azure_databricks"
    conn_type = "azure_databricks"
    hook_name = "Azure Databricks (AAD)"

    DATABRICKS_RESOURCE = "https://databricks.azure.net/"

    def __init__(
        self,
        databricks_conn_id: str = None,
        max_retries: int = 3,
        retry_delay: float = 1.0,
        max_retry_delay: float = 30.0,
    ):
        super().__init__()
        self.databricks_conn_id = databricks_conn_id or self.default_conn_name
        self.max_retries = max_retries
        self.retry_delay = retry_delay
        self.max_retry_delay = max_retry_delay
        self._base_url = None

    # ------------------------------------------------------------------
    # Auth / Connection
    # ------------------------------------------------------------------

    def get_base_url(self) -> str:
        if not self._base_url:
            conn = self.get_connection(self.databricks_conn_id)
            if not conn.host:
                raise AirflowException(
                    "Databricks connection must define a host"
                )
            self._base_url = conn.host.rstrip("/")
        return self._base_url

    def get_aad_token(self) -> str:
        tenant_id = os.getenv("AZURE_TENANT_ID")
        client_id = os.getenv("AZURE_CLIENT_ID")
        client_secret = os.getenv("AZURE_CLIENT_SECRET")

        if tenant_id and client_id and client_secret:
            credential = ClientSecretCredential(
                tenant_id=tenant_id,
                client_id=client_id,
                client_secret=client_secret,
            )
        else:
            credential = ManagedIdentityCredential()

        token = credential.get_token(f"{self.DATABRICKS_RESOURCE}.default")
        return token.token

    def _headers(self) -> Dict[str, str]:
        return {
            "Authorization": f"Bearer {self.get_aad_token()}",
            "Content-Type": "application/json",
        }

    # ------------------------------------------------------------------
    # Retry + Backoff
    # ------------------------------------------------------------------

    def _sleep_with_backoff(self, attempt: int):
        delay = min(
            self.retry_delay * (2 ** attempt),
            self.max_retry_delay,
        )
        jitter = random.uniform(0, delay * 0.1)
        time.sleep(delay + jitter)

    def _request(
        self,
        method: str,
        endpoint: str,
        params: Optional[dict] = None,
        json: Optional[dict] = None,
    ) -> dict:
        url = f"{self.get_base_url()}{endpoint}"

        for attempt in range(self.max_retries + 1):
            try:
                response = requests.request(
                    method=method,
                    url=url,
                    headers=self._headers(),
                    params=params,
                    json=json,
                    timeout=30,
                )

                if response.status_code >= 500:
                    raise AirflowException(
                        f"Server error {response.status_code}"
                    )

                if not response.ok:
                    raise AirflowException(
                        f"Databricks API error "
                        f"[{response.status_code}]: {response.text}"
                    )

                return response.json()

            except Exception as exc:
                if attempt >= self.max_retries:
                    raise AirflowException(
                        f"Databricks API request failed after retries"
                    ) from exc

                self.log.warning(
                    "Databricks API call failed (attempt %s/%s). Retrying...",
                    attempt + 1,
                    self.max_retries,
                )
                self._sleep_with_backoff(attempt)

        raise AirflowException("Unreachable")

    def get(self, endpoint: str, params: Optional[dict] = None) -> dict:
        return self._request("GET", endpoint, params=params)

    # ------------------------------------------------------------------
    # Databricks APIs
    # ------------------------------------------------------------------

    def list_job_runs_paginated(
        self,
        job_id: Optional[int] = None,
        page_size: int = 100,
    ) -> List[dict]:
        """
        Returns *all* job runs using automatic pagination.
        """
        all_runs: List[dict] = []
        next_page_token = None

        while True:
            params = {
                "limit": page_size,
            }
            if job_id:
                params["job_id"] = job_id
            if next_page_token:
                params["page_token"] = next_page_token

            response = self.get("/api/2.1/jobs/runs/list", params=params)

            runs = response.get("runs", [])
            all_runs.extend(runs)

            if not response.get("has_more"):
                break

            next_page_token = response.get("next_page_token")

        self.log.info("Fetched %s job runs", len(all_runs))
        return all_runs

    def get_job_run(self, run_id: int) -> dict:
        return self.get(
            "/api/2.1/jobs/runs/get",
            params={"run_id": run_id},
        )

    def get_instance_pool(self, instance_pool_id: str) -> dict:
        return self.get(
            "/api/2.0/instance-pools/get",
            params={"instance_pool_id": instance_pool_id},
        )
2️⃣ Example DAG Usage (Pagination)
def collect_all_runs(**context):
hook = AzureDatabricksHook()
runs = hook.list_job_runs_paginated(page_size=50)
context["ti"].xcom_push(key="all_runs", value=runs)
3️⃣ Unit Tests (Mocked AAD + Databricks)
Tooling
pip install pytest requests-mock
tests/test_azure_databricks_hook.py
import pytest
from airflow.exceptions import AirflowException

from airflow.hooks.azure_databricks_hook import AzureDatabricksHook
Mock AAD token
@pytest.fixture
def hook(monkeypatch):
def fake_token(*args, **kwargs):
return "fake-token"

    hook = AzureDatabricksHook()
    monkeypatch.setattr(hook, "get_aad_token", fake_token)
    monkeypatch.setattr(
        hook,
        "get_base_url",
        lambda: "https://dummy.azuredatabricks.net",
    )
    return hook
Test pagination
def test_list_job_runs_paginated(requests_mock, hook):
url = "https://dummy.azuredatabricks.net/api/2.1/jobs/runs/list"

    requests_mock.get(
        url,
        [
            {
                "json": {
                    "runs": [{"run_id": 1}],
                    "has_more": True,
                    "next_page_token": "token-1",
                }
            },
            {
                "json": {
                    "runs": [{"run_id": 2}],
                    "has_more": False,
                }
            },
        ],
    )

    runs = hook.list_job_runs_paginated(page_size=1)

    assert len(runs) == 2
    assert runs[0]["run_id"] == 1
    assert runs[1]["run_id"] == 2
Test retry logic
def test_retry_on_server_error(requests_mock, hook):
url = "https://dummy.azuredatabricks.net/api/2.1/jobs/runs/get"

    requests_mock.get(
        url,
        [
            {"status_code": 500},
            {"json": {"run_id": 123}},
        ],
    )

    result = hook.get_job_run(run_id=123)
    assert result["run_id"] == 123
Test hard failure after retries
def test_failure_after_retries(requests_mock, hook):
url = "https://dummy.azuredatabricks.net/api/2.1/jobs/runs/get"

    requests_mock.get(url, status_code=500)

    with pytest.raises(AirflowException):
        hook.get_job_run(run_id=999)
