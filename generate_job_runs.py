import csv
import random
import uuid
from datetime import datetime, timedelta

# ---------------------------------------------------
# CONFIGURATION
# ---------------------------------------------------

OUTPUT_FILE = "job-run-generated.csv"
NUMBER_OF_ROWS = 100

CALCULATOR_MAPPING = {
    "1111": "capital",
    "2222": "portfolio",
    "3333": "groupreporting",
    "4444": "consenrichment"
}

DRIVER_NODE_TYPE = "Standard_D16ds_v5"
WORKER_NODE_TYPE = "Standard_D16ds_v5"

SPARK_VERSION = "14.3.x-scala2.12"
RUNTIME_ENGINE = "STANDARD"
CLUSTER_SOURCE = "JOB"
WORKLOAD_TYPE = "Jobs Compute"
REGION = "eastus2"
TASK_VERSION = "12.2.0"

# ---------------------------------------------------
# HEADER ORDER — NOW MATCHES YOUR SQL EXACTLY
# ---------------------------------------------------

HEADER = [
    "run_id", "job_id", "job_name",
    "calculator_id", "calculator_name",
    "frequency", "reporting_date",
    "cluster_id",
    "start_time", "end_time", "duration_seconds", "duration_hours",
    "status",
    "driver_node_type", "worker_node_type", "worker_count",
    "spot_instances", "photon_enabled",
    "spark_version", "runtime_engine", "cluster_source", "workload_type", "region",
    "autoscale_enabled", "autoscale_min", "autoscale_max",
    "task_version", "context_id", "megdp_run_id", "tenant_abb",
    "dbu_cost_usd", "vm_cost_usd", "storage_cost_usd", "total_cost_usd"
]

# ---------------------------------------------------
# HELPERS
# ---------------------------------------------------

def random_reporting_date():
    days_back = random.randint(0, 29)
    date_value = datetime.utcnow() - timedelta(days=days_back)
    return date_value.strftime("%Y-%m-%d")


def generate_row(index):
    calculator_ids = list(CALCULATOR_MAPPING.keys())
    calculator_id = calculator_ids[index % 4]
    calculator_name = CALCULATOR_MAPPING[calculator_id]

    start_time = datetime.utcnow() + timedelta(hours=index)
    duration_seconds = random.choice([600, 900, 1200, 1800])
    end_time = start_time + timedelta(seconds=duration_seconds)

    return [
        str(500000000000000 + index),  # run_id
        str(18900125280000 + index),  # job_id
        f"JOB-{calculator_id}-{index}",
        calculator_id,
        calculator_name,
        "DAILY",                      # frequency (fixed)
        random_reporting_date(),      # reporting_date (last 30 days)
        "0209-220017-8ajfaxt3",
        start_time.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
        end_time.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
        duration_seconds,
        round(duration_seconds / 3600, 2),
        random.choice(["SUCCESS", "FAILED"]),
        DRIVER_NODE_TYPE,
        WORKER_NODE_TYPE,
        random.choice([4, 6, 8]),
        random.choice(["true", "false"]),
        random.choice(["true", "false"]),
        SPARK_VERSION,
        RUNTIME_ENGINE,
        CLUSTER_SOURCE,
        WORKLOAD_TYPE,
        REGION,
        random.choice(["true", "false"]),
        0,
        0,
        TASK_VERSION,
        str(uuid.uuid4()),
        str(uuid.uuid4()),
        random.choice(["FRCA", "USCA", "UKLN", "DEFR"]),
        0,  # dbu_cost_usd
        0,  # vm_cost_usd
        0,  # storage_cost_usd
        0   # total_cost_usd
    ]


# ---------------------------------------------------
# CSV WRITER
# ---------------------------------------------------

def generate_csv():
    with open(OUTPUT_FILE, mode="w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(HEADER)

        for i in range(1, NUMBER_OF_ROWS + 1):
            writer.writerow(generate_row(i))

    print(f"\n✅ CSV file generated successfully: {OUTPUT_FILE}")
    print(f"📊 Rows generated: {NUMBER_OF_ROWS}")


if __name__ == "__main__":
    generate_csv()