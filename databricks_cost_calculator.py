"""
Databricks Cost Calculator for Azure (Legacy Workspace)
Uses Databricks REST API to calculate per-run costs without Unity Catalog
"""

import os
import requests
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
import logging
from dataclasses import dataclass, asdict
from decimal import Decimal
import json

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class ClusterConfig:
    """Cluster configuration details"""
    cluster_id: str
    cluster_name: str
    driver_node_type: str
    worker_node_type: str
    num_workers: int
    autoscale_min_workers: Optional[int]
    autoscale_max_workers: Optional[int]
    spot_instances: bool
    databricks_runtime_version: str
    azure_attributes: Dict


@dataclass
class JobRunCost:
    """Cost details for a single job run"""
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
    
    # Cluster details
    driver_node_type: str
    worker_node_type: str
    worker_count: int
    spot_instances_used: bool
    
    # Costs (USD)
    dbu_cost: Decimal
    vm_cost: Decimal
    storage_cost: Decimal
    total_cost: Decimal
    
    # Metadata
    calculation_timestamp: datetime
    calculation_version: str
    confidence_score: Decimal


class AzureVMPricingCatalog:
    """Azure VM pricing catalog for Databricks"""
    
    # Prices in USD per hour (update these based on your region)
    # https://azure.microsoft.com/en-us/pricing/details/virtual-machines/
    PRICING = {
        # General Purpose (D-series v2)
        'Standard_DS3_v2': {'on_demand': 0.192, 'spot': 0.038},
        'Standard_DS4_v2': {'on_demand': 0.384, 'spot': 0.077},
        'Standard_DS5_v2': {'on_demand': 0.768, 'spot': 0.154},
        'Standard_DS12_v2': {'on_demand': 0.384, 'spot': 0.077},
        'Standard_DS13_v2': {'on_demand': 0.768, 'spot': 0.154},
        'Standard_DS14_v2': {'on_demand': 1.536, 'spot': 0.307},
        
        # Memory Optimized (E-series v4)
        'Standard_E4ds_v4': {'on_demand': 0.304, 'spot': 0.061},
        'Standard_E8ds_v4': {'on_demand': 0.608, 'spot': 0.122},
        'Standard_E16ds_v4': {'on_demand': 1.216, 'spot': 0.243},
        'Standard_E32ds_v4': {'on_demand': 2.432, 'spot': 0.486},
        
        # Compute Optimized (F-series v2)
        'Standard_F4s_v2': {'on_demand': 0.169, 'spot': 0.034},
        'Standard_F8s_v2': {'on_demand': 0.338, 'spot': 0.068},
        'Standard_F16s_v2': {'on_demand': 0.676, 'spot': 0.135},
        
        # Storage Optimized (L-series v3)
        'Standard_L8s_v3': {'on_demand': 0.624, 'spot': 0.125},
        'Standard_L16s_v3': {'on_demand': 1.248, 'spot': 0.250},
    }
    
    @classmethod
    def get_price(cls, vm_sku: str, spot: bool = False) -> Decimal:
        """Get hourly price for VM SKU"""
        if vm_sku not in cls.PRICING:
            logger.warning(f"VM SKU {vm_sku} not in pricing catalog, using default")
            return Decimal('0.50')  # Fallback price
        
        price_type = 'spot' if spot else 'on_demand'
        return Decimal(str(cls.PRICING[vm_sku][price_type]))


class DatabricksDBUPricing:
    """Databricks DBU pricing for Azure"""
    
    # DBU prices per unit (varies by region and workload type)
    # https://azure.microsoft.com/en-us/pricing/details/databricks/
    DBU_PRICES = {
        'JOBS_COMPUTE': Decimal('0.15'),           # Jobs Compute
        'JOBS_COMPUTE_PHOTON': Decimal('0.22'),    # Jobs Compute with Photon
        'ALL_PURPOSE_COMPUTE': Decimal('0.55'),    # All-Purpose Compute
        'ALL_PURPOSE_PHOTON': Decimal('0.82'),     # All-Purpose with Photon
    }
    
    # DBU consumption rates (DBUs per VM-hour)
    # Based on VM SKU and Databricks runtime
    DBU_RATES = {
        'jobs_standard': 2.0,     # Standard jobs workload
        'jobs_photon': 3.0,       # Jobs with Photon (higher DBU, but faster)
        'all_purpose': 2.5,       # Interactive workload
    }
    
    @classmethod
    def calculate_dbu_cost(
        cls, 
        runtime_hours: Decimal, 
        num_nodes: int,
        workload_type: str = 'jobs_standard',
        photon_enabled: bool = False
    ) -> Tuple[Decimal, Decimal]:
        """
        Calculate DBU cost
        Returns: (dbu_units_consumed, dbu_cost_usd)
        """
        # Determine DBU rate
        if photon_enabled:
            dbu_rate = cls.DBU_RATES['jobs_photon']
            sku = 'JOBS_COMPUTE_PHOTON'
        else:
            dbu_rate = cls.DBU_RATES.get(workload_type, 2.0)
            sku = 'JOBS_COMPUTE' if workload_type.startswith('jobs') else 'ALL_PURPOSE_COMPUTE'
        
        # Calculate DBU units consumed
        dbu_units = Decimal(str(runtime_hours)) * Decimal(str(num_nodes)) * Decimal(str(dbu_rate))
        
        # Calculate cost
        dbu_cost = dbu_units * cls.DBU_PRICES[sku]
        
        return dbu_units, dbu_cost


class DatabricksRestClient:
    """Client for Databricks REST API"""
    
    def __init__(self, workspace_url: str, access_token: str):
        """
        Initialize Databricks REST API client
        
        Args:
            workspace_url: Databricks workspace URL (e.g., https://adb-1234567890.12.azuredatabricks.net)
            access_token: Personal access token or service principal token
        """
        self.workspace_url = workspace_url.rstrip('/')
        self.headers = {
            'Authorization': f'Bearer {access_token}',
            'Content-Type': 'application/json'
        }
        self.session = requests.Session()
        self.session.headers.update(self.headers)
    
    def get_job_runs(
        self, 
        start_time: datetime, 
        end_time: datetime,
        job_id: Optional[int] = None,
        limit: int = 1000
    ) -> List[Dict]:
        """
        Get job runs within time range using Jobs API 2.1
        https://docs.databricks.com/api/workspace/jobs/listruns
        """
        url = f"{self.workspace_url}/api/2.1/jobs/runs/list"
        
        # Convert to epoch milliseconds
        start_time_ms = int(start_time.timestamp() * 1000)
        end_time_ms = int(end_time.timestamp() * 1000)
        
        params = {
            'start_time_from': start_time_ms,
            'start_time_to': end_time_ms,
            'limit': limit,
            'expand_tasks': False
        }
        
        if job_id:
            params['job_id'] = job_id
        
        all_runs = []
        has_more = True
        
        while has_more:
            response = self.session.get(url, params=params)
            response.raise_for_status()
            data = response.json()
            
            runs = data.get('runs', [])
            all_runs.extend(runs)
            
            # Check pagination
            has_more = data.get('has_more', False)
            if has_more and runs:
                params['page_token'] = data.get('next_page_token')
        
        logger.info(f"Retrieved {len(all_runs)} job runs")
        return all_runs
    
    def get_run_details(self, run_id: int) -> Dict:
        """Get detailed information about a specific run"""
        url = f"{self.workspace_url}/api/2.1/jobs/runs/get"
        params = {'run_id': run_id}
        
        response = self.session.get(url, params=params)
        response.raise_for_status()
        return response.json()
    
    def get_cluster_details(self, cluster_id: str) -> Dict:
        """Get cluster configuration details"""
        url = f"{self.workspace_url}/api/2.0/clusters/get"
        params = {'cluster_id': cluster_id}
        
        response = self.session.get(url, params=params)
        response.raise_for_status()
        return response.json()
    
    def get_cluster_events(
        self, 
        cluster_id: str,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        limit: int = 500
    ) -> List[Dict]:
        """Get cluster lifecycle events"""
        url = f"{self.workspace_url}/api/2.0/clusters/events"
        
        data = {
            'cluster_id': cluster_id,
            'order': 'ASC',
            'limit': limit
        }
        
        if start_time:
            data['start_time'] = int(start_time.timestamp() * 1000)
        if end_time:
            data['end_time'] = int(end_time.timestamp() * 1000)
        
        response = self.session.post(url, json=data)
        response.raise_for_status()
        return response.json().get('events', [])


class CostCalculator:
    """Main cost calculation engine"""
    
    def __init__(self, databricks_client: DatabricksRestClient):
        self.db_client = databricks_client
        self.vm_pricing = AzureVMPricingCatalog()
        self.dbu_pricing = DatabricksDBUPricing()
    
    def calculate_run_cost(self, run_data: Dict) -> JobRunCost:
        """
        Calculate cost for a single job run
        
        Args:
            run_data: Run data from Databricks Jobs API
        
        Returns:
            JobRunCost object with all cost details
        """
        run_id = run_data['run_id']
        logger.info(f"Calculating cost for run_id: {run_id}")
        
        # Extract basic run information
        job_id = run_data.get('job_id', 0)
        job_name = run_data.get('task', {}).get('task_key', 'unknown')
        
        # Parse timestamps
        start_time = datetime.fromtimestamp(run_data['start_time'] / 1000)
        end_time = datetime.fromtimestamp(run_data.get('end_time', run_data['start_time']) / 1000)
        duration_seconds = int((end_time - start_time).total_seconds())
        duration_hours = Decimal(str(duration_seconds / 3600))
        
        status = run_data['state']['life_cycle_state']
        result_state = run_data['state'].get('result_state', 'UNKNOWN')
        
        # Get cluster details
        cluster_id = run_data['cluster_instance']['cluster_id']
        cluster_spec = run_data.get('cluster_spec', {})
        
        # Parse cluster configuration
        driver_node_type = cluster_spec.get('driver_node_type_id', 'Standard_DS3_v2')
        worker_node_type = cluster_spec.get('node_type_id', 'Standard_DS3_v2')
        
        # Worker count (handle both fixed and autoscale)
        if 'num_workers' in cluster_spec:
            worker_count = cluster_spec['num_workers']
        elif 'autoscale' in cluster_spec:
            # For autoscale, use average (min + max) / 2
            autoscale = cluster_spec['autoscale']
            worker_count = (autoscale['min_workers'] + autoscale['max_workers']) // 2
        else:
            worker_count = 2  # Default fallback
        
        # Check for spot instances
        azure_attributes = cluster_spec.get('azure_attributes', {})
        spot_instances = azure_attributes.get('availability', 'ON_DEMAND_AZURE') == 'SPOT_AZURE'
        
        # Check for Photon
        runtime_engine = cluster_spec.get('runtime_engine', 'STANDARD')
        photon_enabled = runtime_engine == 'PHOTON'
        
        # Calculate VM costs
        driver_vm_cost = self._calculate_vm_cost(
            driver_node_type, duration_hours, 1, spot_instances
        )
        
        worker_vm_cost = self._calculate_vm_cost(
            worker_node_type, duration_hours, worker_count, spot_instances
        )
        
        total_vm_cost = driver_vm_cost + worker_vm_cost
        
        # Calculate DBU costs
        total_nodes = 1 + worker_count  # Driver + workers
        dbu_units, dbu_cost = self.dbu_pricing.calculate_dbu_cost(
            runtime_hours=duration_hours,
            num_nodes=total_nodes,
            workload_type='jobs_standard',
            photon_enabled=photon_enabled
        )
        
        # Storage cost (simplified - typically small for job clusters)
        # In production, integrate with Azure Cost Management API
        storage_cost = duration_hours * Decimal('0.02')  # Rough estimate
        
        # Total cost
        total_cost = dbu_cost + total_vm_cost + storage_cost
        
        # Extract calculator info from job name
        # Assuming naming convention like: "calculator_<id>_<name>"
        calculator_id = job_name.split('_')[1] if '_' in job_name else 'unknown'
        calculator_name = job_name
        
        return JobRunCost(
            run_id=run_id,
            job_id=job_id,
            job_name=job_name,
            calculator_id=calculator_id,
            calculator_name=calculator_name,
            cluster_id=cluster_id,
            start_time=start_time,
            end_time=end_time,
            duration_seconds=duration_seconds,
            status=result_state,
            driver_node_type=driver_node_type,
            worker_node_type=worker_node_type,
            worker_count=worker_count,
            spot_instances_used=spot_instances,
            dbu_cost=dbu_cost,
            vm_cost=total_vm_cost,
            storage_cost=storage_cost,
            total_cost=total_cost,
            calculation_timestamp=datetime.utcnow(),
            calculation_version='v1.0.0',
            confidence_score=Decimal('0.95')  # High confidence for job clusters
        )
    
    def _calculate_vm_cost(
        self, 
        vm_sku: str, 
        hours: Decimal, 
        count: int, 
        spot: bool
    ) -> Decimal:
        """Calculate VM cost for given configuration"""
        hourly_rate = self.vm_pricing.get_price(vm_sku, spot)
        return hourly_rate * hours * Decimal(str(count))
    
    def calculate_daily_costs(self, target_date: datetime.date) -> List[JobRunCost]:
        """
        Calculate costs for all runs on a specific date
        
        Args:
            target_date: Date to calculate costs for
        
        Returns:
            List of JobRunCost objects
        """
        # Define time range for the day
        start_time = datetime.combine(target_date, datetime.min.time())
        end_time = datetime.combine(target_date, datetime.max.time())
        
        # Fetch all runs for the day
        runs = self.db_client.get_job_runs(start_time, end_time)
        
        # Filter to only completed runs
        completed_runs = [
            r for r in runs 
            if r['state'].get('result_state') in ['SUCCESS', 'FAILED', 'CANCELED']
        ]
        
        logger.info(f"Found {len(completed_runs)} completed runs for {target_date}")
        
        # Calculate cost for each run
        costs = []
        for run in completed_runs:
            try:
                cost = self.calculate_run_cost(run)
                costs.append(cost)
            except Exception as e:
                logger.error(f"Error calculating cost for run {run['run_id']}: {e}")
                continue
        
        return costs


class ObservabilityServiceClient:
    """Client for posting costs to Observability Service"""
    
    def __init__(self, api_url: str, api_token: str):
        self.api_url = api_url.rstrip('/')
        self.headers = {
            'Authorization': f'Bearer {api_token}',
            'Content-Type': 'application/json'
        }
    
    def post_costs(self, costs: List[JobRunCost], calculation_date: datetime.date) -> Dict:
        """
        Post calculated costs to Observability Service
        
        Args:
            costs: List of JobRunCost objects
            calculation_date: Date these costs were calculated for
        
        Returns:
            API response
        """
        url = f"{self.api_url}/api/v1/calculator-runs/costs"
        
        # Convert costs to dict format
        runs_data = []
        for cost in costs:
            run_dict = {
                'databricks_run_id': cost.run_id,
                'calculator_id': cost.calculator_id,
                'calculator_name': cost.calculator_name,
                'cluster_id': cost.cluster_id,
                'start_time': cost.start_time.isoformat(),
                'end_time': cost.end_time.isoformat(),
                'duration_seconds': cost.duration_seconds,
                'run_status': cost.status,
                'driver_node_type': cost.driver_node_type,
                'worker_node_type': cost.worker_node_type,
                'worker_count': cost.worker_count,
                'spot_instance_used': cost.spot_instances_used,
                'photon_enabled': False,  # Add if available
                'dbu_cost_usd': float(cost.dbu_cost),
                'vm_driver_cost_usd': float(cost.vm_cost * Decimal('0.15')),  # Estimate
                'vm_worker_cost_usd': float(cost.vm_cost * Decimal('0.85')),  # Estimate
                'vm_total_cost_usd': float(cost.vm_cost),
                'storage_cost_usd': float(cost.storage_cost),
                'total_cost_usd': float(cost.total_cost),
                'allocation_method': 'direct_job_cluster',
                'confidence_score': float(cost.confidence_score),
                'calculation_date': calculation_date.isoformat(),
                'calculation_timestamp': cost.calculation_timestamp.isoformat(),
                'is_retry': False,  # Add logic to detect retries
                'attempt_number': 0
            }
            runs_data.append(run_dict)
        
        payload = {
            'calculation_date': calculation_date.isoformat(),
            'calculation_version': 'v1.0.0',
            'runs': runs_data
        }
        
        response = requests.post(url, json=payload, headers=self.headers)
        response.raise_for_status()
        
        logger.info(f"Posted {len(runs_data)} cost records to Observability Service")
        return response.json()


def main():
    """Main execution function"""
    # Configuration from environment variables
    DATABRICKS_WORKSPACE_URL = os.getenv('DATABRICKS_WORKSPACE_URL')
    DATABRICKS_ACCESS_TOKEN = os.getenv('DATABRICKS_ACCESS_TOKEN')
    OBSERVABILITY_API_URL = os.getenv('OBSERVABILITY_API_URL')
    OBSERVABILITY_API_TOKEN = os.getenv('OBSERVABILITY_API_TOKEN')
    
    # Target date (default to yesterday)
    target_date_str = os.getenv('TARGET_DATE')
    if target_date_str:
        target_date = datetime.strptime(target_date_str, '%Y-%m-%d').date()
    else:
        target_date = (datetime.now() - timedelta(days=1)).date()
    
    logger.info(f"Calculating costs for {target_date}")
    
    # Initialize clients
    db_client = DatabricksRestClient(DATABRICKS_WORKSPACE_URL, DATABRICKS_ACCESS_TOKEN)
    calculator = CostCalculator(db_client)
    obs_client = ObservabilityServiceClient(OBSERVABILITY_API_URL, OBSERVABILITY_API_TOKEN)
    
    # Calculate costs
    costs = calculator.calculate_daily_costs(target_date)
    
    # Post to Observability Service
    if costs:
        result = obs_client.post_costs(costs, target_date)
        logger.info(f"Successfully posted costs: {result}")
    else:
        logger.warning(f"No costs calculated for {target_date}")


if __name__ == '__main__':
    main()
