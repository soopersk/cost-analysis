📄 1-Page IAM / Security Runbook
Azure Databricks – Read-Only API Access for Cost Analysis

Purpose
Grant a Service Principal (SP) the minimum required permissions to read Azure Databricks metadata (job runs, cluster specs, instance pools) for cost analysis, without allowing any mutations.

Scope of Access
The Service Principal is allowed to:

Read job run metadata

Read instance pool configuration

Perform GET-only Databricks REST API calls

The Service Principal is not allowed to:

Run or modify jobs

Create or modify clusters

Create or modify instance pools

Perform admin operations

Architecture Summary
Layer	Control	Why
Azure	RBAC	Allow OAuth token exchange
Databricks	Workspace entitlements	Control API surface
Databricks	Object ACLs	Enforce least privilege
Required Configuration
1️⃣ Azure RBAC (mandatory)
Resource: Azure Databricks Workspace
Role: Contributor
Principal: Service Principal (client ID)

⚠️ Reader is insufficient and will cause OAuth/API failures.

2️⃣ Databricks Workspace Entitlements
Grant the Service Principal:

✅ Workspace access

✅ Jobs access

❌ Do NOT grant:

Workspace Admin

Allow cluster create

Allow job run

3️⃣ Databricks Object-Level Permissions
Jobs
Permission: Can View

Scope: All jobs (or job group)

Required APIs:

/api/2.1/jobs/runs/list

/api/2.1/jobs/runs/get

Instance Pools
Permission: Can View

Scope: All instance pools

Required API:

/api/2.8/instance-pools/get

Verification Checklist
Expected to SUCCEED
GET /api/2.1/jobs/runs/list

GET /api/2.1/jobs/runs/get

GET /api/2.8/instance-pools/get

Expected to FAIL (403)
POST /api/2.0/clusters/create

POST /api/2.1/jobs/run-now

403 failures here confirm least privilege.

Security Notes
No secrets stored in Databricks

OAuth via Azure AD Service Principal

Access is auditable via Azure RBAC + Databricks audit logs

🧠 2) Automation Script — Grant Permissions to ALL Jobs + Instance Pools
This script:

Lists all jobs

Lists all instance pools

Grants Can View to a Service Principal

Is idempotent (safe to re-run)

Prerequisites
Databricks admin token (one-time setup)

Workspace URL

Service Principal client ID

Python Script
import requests

DATABRICKS_HOST = "https://<your-workspace>.azuredatabricks.net"
ADMIN_TOKEN = "<DATABRICKS_ADMIN_PAT>"
SP_CLIENT_ID = "<SERVICE_PRINCIPAL_CLIENT_ID>"

HEADERS = {
"Authorization": f"Bearer {ADMIN_TOKEN}",
"Content-Type": "application/json"
}

def get_all_jobs():
jobs = []
url = f"{DATABRICKS_HOST}/api/2.1/jobs/list"
params = {"limit": 25}
while True:
resp = requests.get(url, headers=HEADERS, params=params)
resp.raise_for_status()
data = resp.json()
jobs.extend(data.get("jobs", []))
if not data.get("has_more"):
break
params["offset"] = data["offset"] + len(data["jobs"])
return jobs

def get_all_instance_pools():
resp = requests.get(
f"{DATABRICKS_HOST}/api/2.0/instance-pools/list",
headers=HEADERS
)
resp.raise_for_status()
return resp.json().get("instance_pools", [])

def grant_job_view(job_id):
url = f"{DATABRICKS_HOST}/api/2.1/permissions/jobs/{job_id}"
payload = {
"access_control_list": [
{
"service_principal_name": SP_CLIENT_ID,
"permission_level": "CAN_VIEW"
}
]
}
requests.patch(url, headers=HEADERS, json=payload).raise_for_status()

def grant_pool_view(pool_id):
url = f"{DATABRICKS_HOST}/api/2.0/permissions/instance-pools/{pool_id}"
payload = {
"access_control_list": [
{
"service_principal_name": SP_CLIENT_ID,
"permission_level": "CAN_VIEW"
}
]
}
requests.patch(url, headers=HEADERS, json=payload).raise_for_status()

if __name__ == "__main__":
print("Granting job permissions...")
for job in get_all_jobs():
grant_job_view(job["job_id"])
print(f"✔ Job {job['job_id']}")

    print("Granting instance pool permissions...")
    for pool in get_all_instance_pools():
        grant_pool_view(pool["instance_pool_id"])
        print(f"✔ Pool {pool['instance_pool_name']}")

    print("Done. Least-privilege access applied.")
Recommended Hardening (Optional)
Put SP in a Databricks group

Grant permissions to the group, not the SP

Rotate admin PAT after script execution

Run this script as part of onboarding automation

Perfect timing for this refactor — moving off az CLI is exactly what security teams want to see 👍
Below is a clean, production-grade MSAL-based version of what you already have, with zero Azure CLI dependency.

I’ll walk you through:

What changes conceptually

Exact MSAL-based Python code

Why this still requires Azure RBAC = Contributor

Common MSAL + Databricks gotchas

1️⃣ Conceptual change (what’s different)
Before
Python
→ az account get-access-token
→ AAD token
→ Databricks REST
After (pure MSAL)
Python
→ MSAL (client credentials)
→ AAD token for Databricks resource
→ Databricks REST
Key points:

Same AAD token

Same Databricks OAuth audience

No shell calls

Works locally, in CI, in containers, in Azure

2️⃣ Required inputs (environment variables)
Set these:

export AZURE_TENANT_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
export AZURE_CLIENT_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
export AZURE_CLIENT_SECRET="*****"
export AZURE_SUBSCRIPTION_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
export AZURE_RESOURCE_GROUP=""
export MEGDP_AZ_DATABRICKS_NAME="<workspace-name>"
3️⃣ Install dependencies
pip install msal requests databricks-sdk
4️⃣ Pure MSAL implementation (drop-in replacement)
import os
import json
import logging
import requests
import msal
from databricks.sdk import WorkspaceClient

DATABRICKS_RESOURCE = "2ff814a6-3304-4ab8-85cb-cd0e6f879c1d"
ARM_RESOURCE = "https://management.azure.com/.default"

def get_aad_token_for_databricks():
tenant_id = os.environ["AZURE_TENANT_ID"]
client_id = os.environ["AZURE_CLIENT_ID"]
client_secret = os.environ["AZURE_CLIENT_SECRET"]

    authority = f"https://login.microsoftonline.com/{tenant_id}"

    app = msal.ConfidentialClientApplication(
        client_id=client_id,
        client_credential=client_secret,
        authority=authority
    )

    # Databricks requires this specific scope
    result = app.acquire_token_for_client(
        scopes=[f"{DATABRICKS_RESOURCE}/.default"]
    )

    if "access_token" not in result:
        raise RuntimeError(
            f"Failed to acquire token: {result.get('error')} - "
            f"{result.get('error_description')}"
        )

    return result["access_token"]

def get_databricks_workspace_url():
subscription_id = os.environ["AZURE_SUBSCRIPTION_ID"]
resource_group = os.environ["AZURE_RESOURCE_GROUP"]
workspace_name = os.environ["MEGDP_AZ_DATABRICKS_NAME"]

    arm_token = get_aad_token_for_arm()

    url = (
        f"https://management.azure.com/subscriptions/{subscription_id}"
        f"/resourceGroups/{resource_group}"
        f"/providers/Microsoft.Databricks/workspaces/{workspace_name}"
        f"?api-version=2018-04-01"
    )

    headers = {
        "Authorization": f"Bearer {arm_token}"
    }

    resp = requests.get(url, headers=headers)
    resp.raise_for_status()

    return resp.json()["properties"]["workspaceUrl"]

def get_aad_token_for_arm():
tenant_id = os.environ["AZURE_TENANT_ID"]
client_id = os.environ["AZURE_CLIENT_ID"]
client_secret = os.environ["AZURE_CLIENT_SECRET"]

    authority = f"https://login.microsoftonline.com/{tenant_id}"

    app = msal.ConfidentialClientApplication(
        client_id=client_id,
        client_credential=client_secret,
        authority=authority
    )

    result = app.acquire_token_for_client(scopes=[ARM_RESOURCE])

    if "access_token" not in result:
        raise RuntimeError(
            f"Failed to acquire ARM token: {result}"
        )

    return result["access_token"]

def get_api_client():
databricks_token = get_aad_token_for_databricks()
workspace_url = get_databricks_workspace_url()

    logging.debug(f"Databricks workspace URL: {workspace_url}")

    return WorkspaceClient(
        host=f"https://{workspace_url}",
        token=databricks_token
    )

# ---- Usage ----

api_client = get_api_client()

jobs_api = api_client.jobs
pools_api = api_client.instance_pools
clusters_api = api_client.clusters
5️⃣ Why this still needs Contributor (important)
Even with MSAL:

get_databricks_workspace_url() calls Azure Resource Manager

ARM requires Contributor on the Databricks workspace resource

Reader will fail with 403 here

This is not a Databricks limitation — it’s how ARM authorization works.

Once the token is issued:

Azure RBAC is done

Databricks permissions enforce read-only behavior

6️⃣ What this SP can and cannot do (final check)
✅ Allowed
jobs_api.list_runs()
jobs_api.get_run(run_id=123)
pools_api.get(pool_id="abc")
❌ Blocked (403)
clusters_api.create(...)
jobs_api.run_now(...)
That’s least privilege achieved.

7️⃣ Common MSAL + Databricks pitfalls
❌ Using https://databricks.azure.net/.default
✔️ Must use the GUID resource ID

❌ Logging tokens
✔️ Treat access tokens as secrets

❌ Assuming Azure RBAC enforces read-only
✔️ Databricks ACLs do that

using azure-identity:


Perfect — this is the cleanest and most idiomatic solution for your case.
Below is a production-ready azure-identity implementation that replaces both az CLI and raw MSAL, while keeping the same behavior and security model you already validated.

I’ll keep it tight and precise.

What this version does
Uses azure-identity (ClientSecretCredential)

Acquires:
* Databricks AAD token
* ARM token (to discover workspace URL)
* No shell calls
* Token caching handled automatically
* Works locally, in CI, containers, and Azure

Dependencies:

`pip install azure-identity requests databricks-sdk`

Required environment variables:

```
AZURE_TENANT_ID
AZURE_CLIENT_ID
AZURE_CLIENT_SECRET
AZURE_SUBSCRIPTION_ID
AZURE_RESOURCE_GROUP
MEGDP_AZ_DATABRICKS_NAME
```

Constants:

```
DATABRICKS_RESOURCE_SCOPE = "2ff814a6-3304-4ab8-85cb-cd0e6f879c1d/.default"
ARM_SCOPE = "https://management.azure.com/.default"
```

Full implementation (azure-identity):
```python
import os
import logging
import requests
from azure.identity import ClientSecretCredential
from databricks.sdk import WorkspaceClient

def get_credential():
    return ClientSecretCredential(
    tenant_id=os.environ["AZURE_TENANT_ID"],
    client_id=os.environ["AZURE_CLIENT_ID"],
    client_secret=os.environ["AZURE_CLIENT_SECRET"],
)

def get_workspace_url(credential):
    subscription_id = os.environ["AZURE_SUBSCRIPTION_ID"]
    resource_group = os.environ["AZURE_RESOURCE_GROUP"]
    workspace_name = os.environ["MEGDP_AZ_DATABRICKS_NAME"]

    token = credential.get_token(ARM_SCOPE).token

    url = (
        f"https://management.azure.com/subscriptions/{subscription_id}"
        f"/resourceGroups/{resource_group}"
        f"/providers/Microsoft.Databricks/workspaces/{workspace_name}"
        f"?api-version=2018-04-01"
    )

    headers = {"Authorization": f"Bearer {token}"}
    resp = requests.get(url, headers=headers)
    resp.raise_for_status()

    return resp.json()["properties"]["workspaceUrl"]

def get_databricks_token(credential):
    return credential.get_token(DATABRICKS_RESOURCE_SCOPE).token

def get_api_client():
    credential = get_credential()
    workspace_url = get_workspace_url(credential)
    databricks_token = get_databricks_token(credential)

    logging.debug(f"Databricks workspace URL: {workspace_url}")

    return WorkspaceClient(
        host=f"https://{workspace_url}",
        token=databricks_token
    )
```

# ------------------
# Usage
# ------------------

api_client = get_api_client()

jobs_api = api_client.jobs
pools_api = api_client.instance_pools
clusters_api = api_client.clusters
Why this is the recommended version
✅ Security
No CLI dependency
No shell execution
Tokens cached and rotated automatically

✅ Least privilege preserved
Azure RBAC: Contributor (required)
Databricks permissions enforce read-only

✅ Operationally clean
Works in:
GitHub Actions
Azure DevOps
Kubernetes
VM / App Service

Optional improvement (Databricks SDK native auth)
The Databricks SDK also supports passing the credential directly:

client = WorkspaceClient(
host=f"https://{workspace_url}",
azure_credential=credential
)
This lets the SDK:
Acquire tokens lazily
Refresh automatically

---
# scala code to emit node details for cost calculations

```scala
import org.apache.spark.sql.SparkSession
import scala.util.parsing.json.JSONObject
import java.time.Instant
import scala.io.Source
import play.api.libs.json._

val spark: SparkSession = SparkSession.builder.getOrCreate()

// ----------------------
// 1️⃣ Capture Databricks cluster/job metadata
// ----------------------
val conf = spark.sparkContext.getConf

val clusterId      = conf.get("spark.databricks.clusterUsageTags.clusterId", "unknown")
val clusterName    = conf.get("spark.databricks.clusterUsageTags.clusterName", "unknown")
val nodeType       = conf.get("spark.databricks.clusterUsageTags.nodeType", "unknown")
val driverNodeType = conf.get("spark.databricks.clusterUsageTags.driverNodeType", "unknown")
val numWorkers     = conf.get("spark.databricks.clusterUsageTags.clusterWorkers", "unknown")
val instancePoolId = conf.get("spark.databricks.clusterUsageTags.instancePoolId", "none")
val jobId          = conf.get("spark.databricks.job.id", "unknown")
val runId          = conf.get("spark.databricks.job.runId", "unknown")

// ----------------------
// 2️⃣ Capture timing info
// ----------------------
val jobStartTime = Instant.now.toEpochMilli

// ----------------------
// 3️⃣ Detect if this node is a spot/preemptible node
// ----------------------
def isSpotNode(): Boolean = {
try {
val metadataUrl = "http://169.254.169.254/metadata/instance?api-version=2021-02-01"
val jsonStr = Source.fromURL(metadataUrl, "utf-8").mkString
val json = Json.parse(jsonStr)
val evictionPolicy = (json \ "compute" \ "evictionPolicy").asOpt[String].getOrElse("None")
evictionPolicy != "None"  // Spot/preemptible if evictionPolicy set
} catch {
case e: Exception =>
println(s"Warning: Unable to detect spot node: ${e.getMessage}")
false
}
}

val spotNode = isSpotNode()

// ----------------------
// 4️⃣ Your job logic here
// ----------------------
// Example: simple count
val df = spark.range(1, 1000000)
val count = df.count()

val jobEndTime = Instant.now.toEpochMilli
val durationMs = jobEndTime - jobStartTime

// ----------------------
// 5️⃣ Assemble JSON
// ----------------------
val costMetadata = JSONObject(Map(
"clusterId"      -> clusterId,
"clusterName"    -> clusterName,
"nodeType"       -> nodeType,
"driverNodeType" -> driverNodeType,
"numWorkers"     -> numWorkers,
"instancePoolId" -> instancePoolId,
"jobId"          -> jobId,
"runId"          -> runId,
"startTimeMs"    -> jobStartTime.toString,
"endTimeMs"      -> jobEndTime.toString,
"durationMs"     -> durationMs.toString,
"recordCount"    -> count.toString,
"spotNode"       -> spotNode.toString
))

// ----------------------
// 6️⃣ Output JSON
// ----------------------
// Option A: log to console
println(costMetadata.toString())

// Option B: write to Delta table / storage
spark.createDataset(Seq(costMetadata.toString())).write.mode("append").text("/mnt/logs/job_cost_metadata")

```

