# Azure Databricks Cost Attribution - REST API Setup Guide

## 🎯 Architecture Overview

Since you have a **legacy Azure Databricks workspace without Unity Catalog**, we use the **Databricks REST API** to calculate costs.

```
┌────────────────────────────────────────────────────────────┐
│  AIRFLOW (Daily @ 6 AM UTC)                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 1. Fetch job runs (Jobs API)                         │  │
│  │ 2. Get cluster configs (Clusters API)                │  │
│  │ 3. Calculate costs (Python)                          │  │
│  │ 4. POST to Observability Service                     │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────┬───────────────────────────────────────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
    ▼            ▼            ▼
┌─────────┐  ┌─────────┐  ┌──────────────┐
│Databricks│  │  Azure  │  │Observability │
│REST API  │  │Cost Mgmt│  │   Service    │
└─────────┘  └─────────┘  └──────────────┘
```

## 📋 Prerequisites

### 1. Databricks Access
- Azure Databricks workspace URL
- Personal Access Token (PAT) or Service Principal
- Access to Jobs API and Clusters API

### 2. Infrastructure
- Airflow 2.8+ (or any Python environment)
- PostgreSQL 13+ (Observability Service database)
- Python 3.9+

### 3. Optional
- Azure Cost Management API access (for VM pricing validation)

## 🚀 Quick Start

### Step 1: Create Databricks Personal Access Token

```bash
# In Databricks workspace UI:
# User Settings → Developer → Access Tokens → Generate New Token
# Save the token securely

export DATABRICKS_ACCESS_TOKEN="dapi1234567890abcdef..."
export DATABRICKS_WORKSPACE_URL="https://adb-1234567890.12.azuredatabricks.net"
```

### Step 2: Set Up Python Environment

```bash
# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

### Step 3: Configure Environment Variables

Create `.env` file:

```bash
# Databricks Configuration
DATABRICKS_WORKSPACE_URL=https://adb-1234567890.12.azuredatabricks.net
DATABRICKS_ACCESS_TOKEN=dapi1234567890abcdef...

# Observability Service Configuration
OBSERVABILITY_API_URL=http://localhost:8080
OBSERVABILITY_API_TOKEN=your_api_token_here

# Optional: Azure Cost Management
AZURE_SUBSCRIPTION_ID=your-subscription-id
AZURE_TENANT_ID=your-tenant-id
AZURE_CLIENT_ID=your-client-id
AZURE_CLIENT_SECRET=your-client-secret

# Processing Configuration
TARGET_DATE=  # Leave empty for yesterday, or set to YYYY-MM-DD
```

### Step 4: Test Cost Calculation

```bash
# Test the cost calculator standalone
python databricks_cost_calculator.py
```

Expected output:
```
INFO:__main__:Calculating costs for 2024-01-29
INFO:databricks_cost_calculator:Retrieved 45 job runs
INFO:databricks_cost_calculator:Calculating cost for run_id: 123456
INFO:databricks_cost_calculator:Calculating cost for run_id: 123457
...
INFO:databricks_cost_calculator:Posted 45 cost records to Observability Service
INFO:__main__:Successfully posted costs: {'success': True, ...}
```

### Step 5: Deploy to Airflow

```bash
# Copy files to Airflow
cp databricks_cost_calculator.py /opt/airflow/dags/scripts/
cp airflow_dag_databricks_cost.py /opt/airflow/dags/

# Set Airflow variables
airflow variables set DATABRICKS_WORKSPACE_URL "https://adb-..."
airflow variables set OBSERVABILITY_API_URL "http://..."

# Set Airflow connections (for secrets)
airflow connections add databricks_token \
  --conn-type http \
  --conn-password "dapi123..."

# Trigger DAG manually for testing
airflow dags trigger databricks_calculator_cost_attribution
```

## 📊 How Cost Calculation Works

### 1. Fetch Job Runs

Uses **Databricks Jobs API 2.1**:
```bash
GET https://<workspace>.azuredatabricks.net/api/2.1/jobs/runs/list
?start_time_from=1706486400000
&start_time_to=1706572799000
&limit=1000
```

Returns runs with:
- `run_id`, `job_id`, `job_name`
- `start_time`, `end_time`
- `cluster_instance.cluster_id`
- `cluster_spec` (node types, worker count, spot instances)
- `state.result_state` (SUCCESS, FAILED, etc.)

### 2. Calculate Costs

For each run:

**DBU Cost:**
```python
# Formula
dbu_units = (1 + worker_count) × runtime_hours × dbu_rate
dbu_cost = dbu_units × dbu_price

# Example
# 3 workers + 1 driver = 4 nodes
# Runtime: 1.5 hours
# DBU rate: 2.0 (Jobs Compute)
dbu_units = 4 × 1.5 × 2.0 = 12 DBUs
dbu_cost = 12 × $0.15 = $1.80
```

**VM Cost:**
```python
# Formula
driver_cost = driver_vm_rate × runtime_hours × spot_multiplier
worker_cost = worker_vm_rate × runtime_hours × worker_count × spot_multiplier
vm_cost = driver_cost + worker_cost

# Example
# Driver: Standard_DS3_v2 @ $0.192/hour
# Workers: 3 × Standard_DS3_v2 @ $0.192/hour
# Spot instances: 80% discount (multiplier = 0.2)
# Runtime: 1.5 hours
driver_cost = 0.192 × 1.5 × 0.2 = $0.058
worker_cost = 0.192 × 1.5 × 3 × 0.2 = $0.173
vm_cost = $0.231
```

**Total Cost:**
```python
total_cost = dbu_cost + vm_cost + storage_cost
# Example: $1.80 + $0.231 + $0.03 = $2.06
```

### 3. Post to Observability Service

```bash
POST http://observability-api/api/v1/calculator-runs/costs
Content-Type: application/json

{
  "calculation_date": "2024-01-29",
  "calculation_version": "v1.0.0",
  "runs": [
    {
      "databricks_run_id": 123456,
      "calculator_id": "calc_001",
      "cluster_id": "0129-abc123-xyz",
      "total_cost_usd": 2.06,
      "dbu_cost_usd": 1.80,
      "vm_total_cost_usd": 0.231,
      ...
    }
  ]
}
```

## 🔧 Updating VM Pricing

VM prices are hardcoded in `AzureVMPricingCatalog` class. Update these based on your Azure region:

```python
class AzureVMPricingCatalog:
    PRICING = {
        'Standard_DS3_v2': {
            'on_demand': 0.192,  # Update from Azure pricing calculator
            'spot': 0.038
        },
        # Add your VM SKUs here
    }
```

**Where to get prices:**
1. Go to: https://azure.microsoft.com/en-us/pricing/calculator/
2. Select "Virtual Machines"
3. Choose your region (e.g., "East US")
4. Find your SKU (e.g., "DS3 v2")
5. Note both "Pay as You Go" and "Spot" prices

**Or use Azure Pricing API:**
```python
# Optional: Fetch prices dynamically
from azure.mgmt.commerce import UsageManagementClient
from azure.identity import DefaultAzureCredential

credential = DefaultAzureCredential()
client = UsageManagementClient(credential, subscription_id)
rate_card = client.rate_card.get(filter="...")
```

## 🔐 Security Best Practices

### 1. Databricks Token Management

**Use Service Principal (recommended):**
```bash
# Create Azure AD Service Principal
az ad sp create-for-rbac --name databricks-cost-calculator

# Grant access to Databricks workspace
# In Databricks Admin Console:
# Settings → Identity and access → Service principals → Add

# Use OAuth token instead of PAT
export AZURE_CLIENT_ID=<service-principal-app-id>
export AZURE_CLIENT_SECRET=<service-principal-password>
export AZURE_TENANT_ID=<your-tenant-id>
```

**In Python:**
```python
from azure.identity import ClientSecretCredential
import requests

credential = ClientSecretCredential(
    tenant_id=os.getenv('AZURE_TENANT_ID'),
    client_id=os.getenv('AZURE_CLIENT_ID'),
    client_secret=os.getenv('AZURE_CLIENT_SECRET')
)

# Get Databricks token
token = credential.get_token(
    "2ff814a6-3304-4ab8-85cb-cd0e6f879c1d/.default"  # Databricks resource ID
).token
```

### 2. Store Secrets in Airflow

```bash
# Don't hardcode tokens!
# Use Airflow Connections or Variables

# Create connection
airflow connections add databricks_api \
  --conn-type http \
  --conn-host "adb-1234567890.12.azuredatabricks.net" \
  --conn-password "dapi..."

# In DAG, use:
from airflow.hooks.base import BaseHook
conn = BaseHook.get_connection('databricks_api')
token = conn.password
```

### 3. API Rate Limits

Databricks REST API limits:
- **Jobs API**: ~100 requests/minute
- **Clusters API**: ~100 requests/minute

**Handle rate limits:**
```python
import time
from requests.exceptions import HTTPError

def call_api_with_retry(func, max_retries=3):
    for attempt in range(max_retries):
        try:
            return func()
        except HTTPError as e:
            if e.response.status_code == 429:  # Rate limit
                wait_time = int(e.response.headers.get('Retry-After', 60))
                logger.warning(f"Rate limited, waiting {wait_time}s...")
                time.sleep(wait_time)
            else:
                raise
    raise Exception("Max retries exceeded")
```

## 📈 Monitoring & Validation

### Daily Validation Checks

The Airflow DAG includes automatic validation:

```python
# 1. Check cost is non-zero
assert total_cost > 0, "Total cost cannot be zero"

# 2. Check reasonable range
assert total_cost < 50000, "Unusually high daily cost"

# 3. Check DBU proportion
dbu_pct = dbu_cost / total_cost * 100
assert 20 < dbu_pct < 80, "DBU cost proportion unusual"

# 4. Compare with yesterday
yesterday_cost = get_yesterday_cost()
variance = abs(total_cost - yesterday_cost) / yesterday_cost
if variance > 0.5:  # 50% change
    send_alert(f"Cost changed by {variance*100:.1f}%")
```

### Cost Reconciliation with Azure

```bash
# Weekly reconciliation job
# Compare calculated costs vs Azure Cost Management

python reconcile_costs.py --start-date 2024-01-22 --end-date 2024-01-28
```

### Grafana Dashboard Metrics

```python
# Export Prometheus metrics
from prometheus_client import Counter, Gauge

cost_total = Gauge('databricks_cost_total_usd', 'Total daily cost')
cost_dbu = Gauge('databricks_cost_dbu_usd', 'DBU cost')
runs_processed = Counter('databricks_runs_processed', 'Runs processed')

# In cost calculator
cost_total.set(float(total_cost))
cost_dbu.set(float(total_dbu_cost))
runs_processed.inc(len(costs))
```

## 🐛 Troubleshooting

### Issue: "No runs found"

**Check:**
```bash
# 1. Verify date range
curl -H "Authorization: Bearer $TOKEN" \
  "https://$WORKSPACE/api/2.1/jobs/runs/list?limit=10"

# 2. Check if jobs completed
# Runs must have result_state in [SUCCESS, FAILED, CANCELED]

# 3. Verify job actually ran
# Check Databricks UI: Workflows → Job Runs
```

### Issue: "Authentication failed"

**Check:**
```bash
# Test token
curl -H "Authorization: Bearer $TOKEN" \
  "https://$WORKSPACE/api/2.0/clusters/list"

# Should return 200 OK with cluster list
# If 403: Token expired or lacks permissions
# If 401: Invalid token format
```

### Issue: "VM SKU not in pricing catalog"

**Solution:**
```python
# Add your VM SKU to AzureVMPricingCatalog.PRICING
# Or set a default fallback
logger.warning(f"Unknown SKU: {vm_sku}, using default price")
return Decimal('0.50')  # Default hourly rate
```

### Issue: "Costs seem too high/low"

**Debug:**
```python
# Add detailed logging
logger.debug(f"Run {run_id}:")
logger.debug(f"  Duration: {duration_hours} hours")
logger.debug(f"  Workers: {worker_count}")
logger.debug(f"  VM SKU: {worker_node_type}")
logger.debug(f"  Spot: {spot_instances}")
logger.debug(f"  DBU rate: {dbu_rate}")
logger.debug(f"  VM cost: ${vm_cost}")
logger.debug(f"  DBU cost: ${dbu_cost}")
```

## 📚 API Reference

### Databricks Jobs API
https://docs.databricks.com/api/workspace/jobs

**Key Endpoints:**
```bash
# List runs
GET /api/2.1/jobs/runs/list

# Get run details
GET /api/2.1/jobs/runs/get?run_id={run_id}

# List jobs
GET /api/2.1/jobs/list
```

### Databricks Clusters API
https://docs.databricks.com/api/workspace/clusters

**Key Endpoints:**
```bash
# Get cluster details
GET /api/2.0/clusters/get?cluster_id={cluster_id}

# Get cluster events
POST /api/2.0/clusters/events
```

## 🔄 Backfilling Historical Data

```bash
# Trigger backfill DAG
airflow dags trigger databricks_cost_attribution_backfill \
  -c '{"start_date": "2024-01-01", "end_date": "2024-01-31"}'

# Monitor progress
airflow dags state databricks_cost_attribution_backfill

# Check logs
airflow tasks logs databricks_cost_attribution_backfill backfill_costs
```

## 🎉 Success Criteria

After setup, you should have:

✅ Daily automated cost calculation at 6 AM UTC  
✅ Costs posted to Observability Service database  
✅ Beautiful dashboard showing cost trends  
✅ Validation alerts for anomalies  
✅ 95%+ cost attribution accuracy  

**Verify:**
```bash
# Check database
psql -h localhost -U observability_user -d observability_db -c \
  "SELECT COUNT(*), SUM(total_cost_usd) FROM finops.calculator_run_costs WHERE reporting_date = CURRENT_DATE - 1;"

# Check dashboard
curl http://localhost:8080/api/v1/cost-analytics?period=7d | jq
```

## 📞 Support

**Common issues:**
- Rate limiting → Add retry logic with exponential backoff
- Missing VM SKUs → Update pricing catalog
- Authentication → Rotate tokens, check permissions
- No data → Verify jobs ran and completed

**Logs location:**
- Airflow: `/opt/airflow/logs/`
- Application: Check stdout or configure file logging

---

**Your cost attribution pipeline is now ready! 🚀**

Monitor daily, optimize continuously, and enjoy cost visibility into your Databricks workloads.
