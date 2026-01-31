# ============================================================
# NOTEBOOK CELL 1 — METADATA
# ============================================================
# Databricks Cost Calculator (Azure, Legacy Workspace)
# Author: Your Team
# Purpose: Calculate per-job-run Databricks costs using REST APIs
# Notes:
#   - Designed for Databricks Notebook execution
#   - ObservabilityServiceClient intentionally removed
#   - Results displayed directly in notebook
# ============================================================


# ============================================================
# NOTEBOOK CELL 2 — IMPORTS & LOGGING
# ============================================================
import os
import requests
import logging
from datetime import datetime, timedelta, date
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from decimal import Decimal

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("databricks-cost-calculator")


# ============================================================
# NOTEBOOK CELL 3 — CONFIGURATION & SECRETS
# ============================================================

# Databricks workspace URL (no trailing slash)
DATABRICKS_WORKSPACE_URL = "https://adb-XXXXXXXXXXXX.XX.azuredatabricks.net"

# Personal Access Token stored in Databricks Secrets
DATABRICKS_ACCESS_TOKEN = dbutils.secrets.get(
    scope="databricks-secrets",
    key="databricks-pat"
)

# Target date (default: yesterday)
TARGET_DATE: date = (datetime.now() - timedelta(days=1)).date()

logger.info(f"Target calculation date: {TARGET_DATE}")


# ============================================================
# NOTEBOOK CELL 4 — DATA MODELS
# ============================================================

@dataclass
class JobRunCost:
    run_id: int
    job_id: int
    job_name: str
    calculator_id: str
    calculator_name: str
    cluster_id: str
    start_time: datetime
    end_time: datetime
    duration_seconds: int
    status: str

    driver_node_type: str
    worker_node_type: str
    worker_count: int
    spot_instances_used: bool

    dbu_cost: Decimal
    vm_cost: Decimal
    storage_cost: Decimal
    total_cost: Decimal

    calculation_timestamp: datetime
    calculation_version: str
    confidence_score: Decimal


# ============================================================
# NOTEBOOK CELL 5 — AZURE VM PRICING CATALOG
# ============================================================

class AzureVMPricingCatalog:
    PRICING = {
        'Standard_DS3_v2': {'on_demand': 0.192, 'spot': 0.038},
        'Standard_DS4_v2': {'on_demand': 0.384, 'spot': 0.077},
        'Standard_DS5_v2': {'on_demand': 0.768, 'spot': 0.154},
        'Standard_E8ds_v4': {'on_demand': 0.608, 'spot': 0.122},
        'Standard_E16ds_v4': {'on_demand': 1.216, 'spot': 0.243},
        'Standard_F8s_v2': {'on_demand': 0.338, 'spot': 0.068},
    }

    @classmethod
    def get_price(cls, vm_sku: str, spot: bool = False) -> Decimal:
        if vm_sku not in cls.PRICING:
            logger.warning(f"VM SKU {vm_sku} not found, using fallback price")
            return Decimal("0.50")
        key = "spot" if spot else "on_demand"
        return Decimal(str(cls.PRICING[vm_sku][key]))


# ============================================================
# NOTEBOOK CELL 6 — DBU PRICING
# ============================================================

class DatabricksDBUPricing:
    DBU_PRICES = {
        'JOBS_COMPUTE': Decimal('0.15'),
        'JOBS_COMPUTE_PHOTON': Decimal('0.22'),
    }

    DBU_RATES = {
        'jobs_standard': 2.0,
        'jobs_photon': 3.0,
    }

    @classmethod
    def calculate_dbu_cost(
        cls,
        runtime_hours: Decimal,
        num_nodes: int,
        photon_enabled: bool
    ) -> Tuple[Decimal, Decimal]:

        rate = cls.DBU_RATES['jobs_photon'] if photon_enabled else cls.DBU_RATES['jobs_standard']
        sku = 'JOBS_COMPUTE_PHOTON' if photon_enabled else 'JOBS_COMPUTE'

        dbu_units = runtime_hours * Decimal(num_nodes) * Decimal(rate)
        dbu_cost = dbu_units * cls.DBU_PRICES[sku]

        return dbu_units, dbu_cost


# ============================================================
# NOTEBOOK CELL 7 — DATABRICKS REST CLIENT
# ============================================================

class DatabricksRestClient:

    def __init__(self, workspace_url: str, access_token: str):
        self.workspace_url = workspace_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json"
        })

    def get_job_runs(
        self,
        start_time: datetime,
        end_time: datetime,
        limit: int = 1000
    ) -> List[Dict]:

        url = f"{self.workspace_url}/api/2.1/jobs/runs/list"

        params = {
            "start_time_from": int(start_time.timestamp() * 1000),
            "start_time_to": int(end_time.timestamp() * 1000),
            "limit": limit
        }

        runs, has_more = [], True

        while has_more:
            resp = self.session.get(url, params=params)
            resp.raise_for_status()
            payload = resp.json()

            runs.extend(payload.get("runs", []))
            has_more = payload.get("has_more", False)
            params["page_token"] = payload.get("next_page_token")

        logger.info(f"Retrieved {len(runs)} job runs")
        return runs


# ============================================================
# NOTEBOOK CELL 8 — COST CALCULATOR
# ============================================================

class CostCalculator:

    def __init__(self, db_client: DatabricksRestClient):
        self.db_client = db_client
        self.vm_pricing = AzureVMPricingCatalog()
        self.dbu_pricing = DatabricksDBUPricing()

    def calculate_daily_costs(self, target_date: date) -> List[JobRunCost]:

        start_time = datetime.combine(target_date, datetime.min.time())
        end_time = datetime.combine(target_date, datetime.max.time())

        runs = self.db_client.get_job_runs(start_time, end_time)

        completed = [
            r for r in runs
            if r.get("state", {}).get("result_state") in ("SUCCESS", "FAILED", "CANCELED")
        ]

        logger.info(f"Completed runs: {len(completed)}")

        costs = []
        for run in completed:
            try:
                costs.append(self._calculate_run_cost(run))
            except Exception as e:
                logger.error(f"Run {run.get('run_id')} failed: {e}")

        return costs

    def _calculate_run_cost(self, run: Dict) -> JobRunCost:

        start = datetime.fromtimestamp(run["start_time"] / 1000)
        end = datetime.fromtimestamp(run.get("end_time", run["start_time"]) / 1000)

        duration_seconds = int((end - start).total_seconds())
        hours = Decimal(duration_seconds) / Decimal(3600)

        cluster_spec = run.get("cluster_spec", {})
        driver_type = cluster_spec.get("driver_node_type_id", "Standard_DS3_v2")
        worker_type = cluster_spec.get("node_type_id", "Standard_DS3_v2")
        workers = cluster_spec.get("num_workers", 2)

        azure_attrs = cluster_spec.get("azure_attributes", {})
        spot = azure_attrs.get("availability") == "SPOT_AZURE"
        photon = cluster_spec.get("runtime_engine") == "PHOTON"

        vm_cost = (
            self.vm_pricing.get_price(driver_type, spot) * hours +
            self.vm_pricing.get_price(worker_type, spot) * hours * workers
        )

        _, dbu_cost = self.dbu_pricing.calculate_dbu_cost(
            hours, workers + 1, photon
        )

        storage_cost = hours * Decimal("0.02")
        total_cost = vm_cost + dbu_cost + storage_cost

        job_name = run.get("task", {}).get("task_key", "unknown")

        return JobRunCost(
            run_id=run["run_id"],
            job_id=run.get("job_id", 0),
            job_name=job_name,
            calculator_id="unknown",
            calculator_name=job_name,
            cluster_id=run["cluster_instance"]["cluster_id"],
            start_time=start,
            end_time=end,
            duration_seconds=duration_seconds,
            status=run["state"]["result_state"],
            driver_node_type=driver_type,
            worker_node_type=worker_type,
            worker_count=workers,
            spot_instances_used=spot,
            dbu_cost=dbu_cost,
            vm_cost=vm_cost,
            storage_cost=storage_cost,
            total_cost=total_cost,
            calculation_timestamp=datetime.utcnow(),
            calculation_version="v1.0.0",
            confidence_score=Decimal("0.95")
        )


# ============================================================
# NOTEBOOK CELL 9 — EXECUTION
# ============================================================

db_client = DatabricksRestClient(
    DATABRICKS_WORKSPACE_URL,
    DATABRICKS_ACCESS_TOKEN
)

calculator = CostCalculator(db_client)
costs = calculator.calculate_daily_costs(TARGET_DATE)

logger.info(f"Total runs with cost calculated: {len(costs)}")


# ============================================================
# NOTEBOOK CELL 10 — DISPLAY RESULTS
# ============================================================

from pyspark.sql import Row
from pyspark.sql.functions import sum as spark_sum

if costs:
    rows = [
        Row(
            run_id=c.run_id,
            job_name=c.job_name,
            total_cost_usd=float(c.total_cost),
            dbu_cost_usd=float(c.dbu_cost),
            vm_cost_usd=float(c.vm_cost),
            storage_cost_usd=float(c.storage_cost)
        )
        for c in costs
    ]

    df = spark.createDataFrame(rows)
    display(df.orderBy(df.total_cost_usd.desc()))

    display(
        df.select(
            spark_sum("dbu_cost_usd").alias("total_dbu_cost_usd"),
            spark_sum("vm_cost_usd").alias("total_vm_cost_usd"),
            spark_sum("storage_cost_usd").alias("total_storage_cost_usd"),
            spark_sum("total_cost_usd").alias("grand_total_cost_usd"),
        )
    )
else:
    print("No completed job runs found.")
