"""
Airflow DAG for Databricks Calculator Cost Attribution
Uses Databricks REST API to calculate costs for job runs
"""

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.http.sensors.http import HttpSensor
from airflow.utils.dates import days_ago
from datetime import datetime, timedelta
import os
import sys

# Add the cost calculator module to path
sys.path.append('/opt/airflow/dags/scripts')

from databricks_cost_calculator import (
    DatabricksRestClient,
    CostCalculator,
    ObservabilityServiceClient
)

default_args = {
    'owner': 'finops',
    'depends_on_past': False,
    'email': ['finops@company.com'],
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
}

def calculate_and_post_costs(**context):
    """
    Main task: Calculate costs and post to Observability Service
    """
    import logging
    logger = logging.getLogger(__name__)
    
    # Get execution date
    execution_date = context['ds']
    target_date = datetime.strptime(execution_date, '%Y-%m-%d').date()
    
    logger.info(f"Processing cost attribution for {target_date}")
    
    # Get configuration from Airflow Variables or environment
    DATABRICKS_WORKSPACE_URL = os.getenv('DATABRICKS_WORKSPACE_URL')
    DATABRICKS_ACCESS_TOKEN = os.getenv('DATABRICKS_ACCESS_TOKEN')
    OBSERVABILITY_API_URL = os.getenv('OBSERVABILITY_API_URL')
    OBSERVABILITY_API_TOKEN = os.getenv('OBSERVABILITY_API_TOKEN')
    
    # Initialize clients
    db_client = DatabricksRestClient(DATABRICKS_WORKSPACE_URL, DATABRICKS_ACCESS_TOKEN)
    calculator = CostCalculator(db_client)
    obs_client = ObservabilityServiceClient(OBSERVABILITY_API_URL, OBSERVABILITY_API_TOKEN)
    
    # Calculate costs
    logger.info("Fetching job runs from Databricks...")
    costs = calculator.calculate_daily_costs(target_date)
    
    if not costs:
        logger.warning(f"No completed runs found for {target_date}")
        return {
            'status': 'no_data',
            'date': str(target_date),
            'runs_processed': 0
        }
    
    logger.info(f"Calculated costs for {len(costs)} runs")
    
    # Post to Observability Service
    logger.info("Posting costs to Observability Service...")
    result = obs_client.post_costs(costs, target_date)
    
    # Calculate summary statistics
    total_cost = sum(c.total_cost for c in costs)
    total_dbu_cost = sum(c.dbu_cost for c in costs)
    total_vm_cost = sum(c.vm_cost for c in costs)
    
    summary = {
        'status': 'success',
        'date': str(target_date),
        'runs_processed': len(costs),
        'total_cost_usd': float(total_cost),
        'total_dbu_cost_usd': float(total_dbu_cost),
        'total_vm_cost_usd': float(total_vm_cost),
        'api_response': result
    }
    
    logger.info(f"Cost attribution completed: {summary}")
    
    # Push summary to XCom for downstream tasks
    context['ti'].xcom_push(key='cost_summary', value=summary)
    
    return summary


def validate_results(**context):
    """
    Validate that costs were successfully calculated and posted
    """
    import logging
    logger = logging.getLogger(__name__)
    
    # Pull summary from previous task
    summary = context['ti'].xcom_pull(task_ids='calculate_costs', key='cost_summary')
    
    if not summary or summary['status'] != 'success':
        raise ValueError(f"Cost calculation failed or returned no data: {summary}")
    
    runs_processed = summary.get('runs_processed', 0)
    
    if runs_processed == 0:
        logger.warning("No runs were processed - this may be expected for weekends/holidays")
        return True
    
    # Validation checks
    total_cost = summary.get('total_cost_usd', 0)
    
    # Check 1: Reasonable cost range
    if total_cost <= 0:
        raise ValueError(f"Total cost is zero or negative: ${total_cost}")
    
    # Check 2: Not abnormally high (adjust threshold as needed)
    if total_cost > 50000:  # $50k threshold
        logger.warning(f"Unusually high daily cost detected: ${total_cost}")
        # Could send alert here
    
    # Check 3: DBU cost should be reasonable proportion
    dbu_cost = summary.get('total_dbu_cost_usd', 0)
    dbu_percentage = (dbu_cost / total_cost * 100) if total_cost > 0 else 0
    
    if dbu_percentage < 20 or dbu_percentage > 80:
        logger.warning(f"DBU cost percentage unusual: {dbu_percentage:.1f}%")
    
    logger.info(f"Validation passed: {runs_processed} runs, ${total_cost:.2f} total cost")
    return True


def send_summary_notification(**context):
    """
    Send daily summary notification (Slack, email, etc.)
    """
    import logging
    logger = logging.getLogger(__name__)
    
    summary = context['ti'].xcom_pull(task_ids='calculate_costs', key='cost_summary')
    
    if not summary:
        logger.warning("No summary data available")
        return
    
    execution_date = context['ds']
    
    message = f"""
    📊 Databricks Cost Attribution Summary - {execution_date}
    
    ✅ Status: {summary['status'].upper()}
    📈 Runs Processed: {summary.get('runs_processed', 0)}
    💰 Total Cost: ${summary.get('total_cost_usd', 0):.2f}
    ├─ DBU Cost: ${summary.get('total_dbu_cost_usd', 0):.2f}
    └─ VM Cost: ${summary.get('total_vm_cost_usd', 0):.2f}
    
    View details: https://your-dashboard.com/costs?date={execution_date}
    """
    
    logger.info(message)
    
    # TODO: Send to Slack, Teams, or email
    # Example for Slack:
    # from airflow.providers.slack.hooks.slack_webhook import SlackWebhookHook
    # slack_hook = SlackWebhookHook(http_conn_id='slack_webhook')
    # slack_hook.send(text=message)


with DAG(
    'databricks_calculator_cost_attribution',
    default_args=default_args,
    description='Daily cost attribution for Databricks calculator runs using REST API',
    schedule_interval='0 6 * * *',  # 6 AM UTC daily
    start_date=days_ago(1),
    catchup=False,
    tags=['finops', 'cost-attribution', 'databricks'],
    max_active_runs=1,
) as dag:
    
    # Task 1: Check if Observability Service is healthy
    check_api_health = HttpSensor(
        task_id='check_observability_api',
        http_conn_id='observability_service',
        endpoint='/health',
        timeout=60,
        poke_interval=10,
        mode='poke',
    )
    
    # Task 2: Calculate costs and post to API
    calculate_costs = PythonOperator(
        task_id='calculate_costs',
        python_callable=calculate_and_post_costs,
        provide_context=True,
    )
    
    # Task 3: Validate results
    validate = PythonOperator(
        task_id='validate_results',
        python_callable=validate_results,
        provide_context=True,
    )
    
    # Task 4: Send summary notification
    notify = PythonOperator(
        task_id='send_notification',
        python_callable=send_summary_notification,
        provide_context=True,
    )
    
    # Define task dependencies
    check_api_health >> calculate_costs >> validate >> notify


# Optional: Backfill DAG for historical data
with DAG(
    'databricks_cost_attribution_backfill',
    default_args=default_args,
    description='Backfill historical cost data',
    schedule_interval=None,  # Manual trigger only
    start_date=days_ago(1),
    catchup=False,
    tags=['finops', 'cost-attribution', 'backfill'],
) as backfill_dag:
    
    def backfill_date_range(**context):
        """
        Backfill costs for a date range
        Use: airflow dags trigger databricks_cost_attribution_backfill \
             --conf '{"start_date": "2024-01-01", "end_date": "2024-01-31"}'
        """
        import logging
        from datetime import datetime, timedelta
        logger = logging.getLogger(__name__)
        
        # Get date range from DAG config
        conf = context.get('dag_run').conf or {}
        start_date_str = conf.get('start_date')
        end_date_str = conf.get('end_date')
        
        if not start_date_str or not end_date_str:
            raise ValueError("Must provide start_date and end_date in DAG config")
        
        start_date = datetime.strptime(start_date_str, '%Y-%m-%d').date()
        end_date = datetime.strptime(end_date_str, '%Y-%m-%d').date()
        
        logger.info(f"Backfilling costs from {start_date} to {end_date}")
        
        # Initialize clients
        DATABRICKS_WORKSPACE_URL = os.getenv('DATABRICKS_WORKSPACE_URL')
        DATABRICKS_ACCESS_TOKEN = os.getenv('DATABRICKS_ACCESS_TOKEN')
        OBSERVABILITY_API_URL = os.getenv('OBSERVABILITY_API_URL')
        OBSERVABILITY_API_TOKEN = os.getenv('OBSERVABILITY_API_TOKEN')
        
        db_client = DatabricksRestClient(DATABRICKS_WORKSPACE_URL, DATABRICKS_ACCESS_TOKEN)
        calculator = CostCalculator(db_client)
        obs_client = ObservabilityServiceClient(OBSERVABILITY_API_URL, OBSERVABILITY_API_TOKEN)
        
        # Process each day
        current_date = start_date
        total_runs = 0
        total_cost = 0
        
        while current_date <= end_date:
            logger.info(f"Processing {current_date}...")
            
            try:
                costs = calculator.calculate_daily_costs(current_date)
                
                if costs:
                    obs_client.post_costs(costs, current_date)
                    total_runs += len(costs)
                    total_cost += sum(float(c.total_cost) for c in costs)
                    logger.info(f"{current_date}: {len(costs)} runs, ${sum(float(c.total_cost) for c in costs):.2f}")
                else:
                    logger.info(f"{current_date}: No runs found")
                
            except Exception as e:
                logger.error(f"Error processing {current_date}: {e}")
            
            current_date += timedelta(days=1)
        
        summary = {
            'start_date': start_date_str,
            'end_date': end_date_str,
            'total_runs_processed': total_runs,
            'total_cost_usd': total_cost
        }
        
        logger.info(f"Backfill completed: {summary}")
        return summary
    
    backfill_task = PythonOperator(
        task_id='backfill_costs',
        python_callable=backfill_date_range,
        provide_context=True,
    )
