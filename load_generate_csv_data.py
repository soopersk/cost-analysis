import csv
import random
import uuid
import argparse
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Callable, List, Optional

# ---------------------------------------------------
# CONFIGURATION
# ---------------------------------------------------

OUTPUT_FILE = "job-run-generated.csv"
NUMBER_OF_ROWS = 100

CALCULATOR_MAPPING = {
    "1111": "capital",
    "2222": "portfolio",
    "3333": "groupreporting",
    "4444": "consenrichment",
}

DRIVER_NODE_TYPE = "Standard_D16ds_v5"
WORKER_NODE_TYPE = "Standard_D16ds_v5"
SPARK_VERSION = "14.3.x-scala2.12"
RUNTIME_ENGINE = "STANDARD"
CLUSTER_SOURCE = "JOB"
WORKLOAD_TYPE = "Jobs Compute"
REGION = "eastus2"
TASK_VERSION = "12.2.0"

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
    "dbu_cost_usd", "vm_cost_usd", "storage_cost_usd", "total_cost_usd",
]

# ---------------------------------------------------
# COLUMN SPEC — declarative column override/addition
# ---------------------------------------------------

@dataclass
class ColumnSpec:
    """
    Describes how to populate a single CSV column.

    Attributes:
        name:       Column header name. If it already exists in the CSV it will
                    be overwritten; otherwise a new column is appended.
        values:     Explicit list of values to pick from randomly.
        generator:  Callable that accepts (row_index: int) -> Any.
                    Used when `values` is empty / not provided.
        weight:     Optional probability weights aligned with `values`.
    """
    name: str
    values: List[Any] = field(default_factory=list)
    generator: Optional[Callable[[int], Any]] = None
    weights: Optional[List[float]] = None

    def sample(self, row_index: int = 0) -> Any:
        if self.values:
            return random.choices(self.values, weights=self.weights, k=1)[0]
        if self.generator:
            return self.generator(row_index)
        raise ValueError(f"ColumnSpec '{self.name}' has neither values nor a generator.")


# ---------------------------------------------------
# HELPERS
# ---------------------------------------------------

def last_n_business_dates(n: int, reference: Optional[datetime] = None) -> List[str]:
    """Return the last *n* business dates (Mon–Fri) as 'YYYY-MM-DD' strings."""
    ref = reference or datetime.utcnow()
    dates: List[str] = []
    current = ref
    while len(dates) < n:
        current -= timedelta(days=1)
        if current.weekday() < 5:          # 0=Mon … 4=Fri
            dates.append(current.strftime("%Y-%m-%d"))
    return dates


# ---------------------------------------------------
# DEFAULT COLUMN SPECS
# (override frequency + reporting_date from the original script)
# ---------------------------------------------------

DEFAULT_COLUMN_SPECS: List[ColumnSpec] = [
    ColumnSpec(
        name="reporting_date",
        values=last_n_business_dates(7),   # last 7 business days
    ),
    ColumnSpec(
        name="frequency",
        values=["DAILY", "MONTHLY"],
    ),
]

# ---------------------------------------------------
# ROW GENERATOR (produces the base row dict)
# ---------------------------------------------------

def generate_row(index: int) -> dict:
    calculator_ids = list(CALCULATOR_MAPPING.keys())
    calculator_id = calculator_ids[index % len(calculator_ids)]
    calculator_name = CALCULATOR_MAPPING[calculator_id]

    start_time = datetime.utcnow() + timedelta(hours=index)
    duration_seconds = random.choice([600, 900, 1200, 1800])
    end_time = start_time + timedelta(seconds=duration_seconds)

    values = [
        str(500000000000000 + index),
        str(18900125280000 + index),
        f"JOB-{calculator_id}-{index}",
        calculator_id,
        calculator_name,
        "DAILY",                        # will be overridden by ColumnSpec
        "",                             # reporting_date placeholder
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
        0, 0, 0, 0,
    ]
    return dict(zip(HEADER, values))


# ---------------------------------------------------
# CORE ENGINE
# ---------------------------------------------------

def apply_column_specs(
    rows: List[dict],
    specs: List[ColumnSpec],
    existing_headers: List[str],
) -> tuple[List[dict], List[str]]:
    """
    Apply each ColumnSpec to every row.

    - Overwrites the value if the column already exists.
    - Appends the column if it is new.

    Returns (updated_rows, final_header_list).
    """
    headers = list(existing_headers)
    for spec in specs:
        if spec.name not in headers:
            headers.append(spec.name)

    updated_rows = []
    for i, row in enumerate(rows):
        new_row = dict(row)
        for spec in specs:
            new_row[spec.name] = spec.sample(row_index=i)
        updated_rows.append(new_row)

    return updated_rows, headers


def load_csv(path: str) -> tuple[List[dict], List[str]]:
    """Read an existing CSV and return (rows_as_dicts, ordered_headers)."""
    with open(path, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        headers = list(reader.fieldnames or [])
        rows = [dict(row) for row in reader]
    return rows, headers


def write_csv(path: str, rows: List[dict], headers: List[str]) -> None:
    with open(path, mode="w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=headers, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


# ---------------------------------------------------
# MAIN FUNCTION
# ---------------------------------------------------

def generate_csv(
    output_file: str = OUTPUT_FILE,
    number_of_rows: int = NUMBER_OF_ROWS,
    input_file: Optional[str] = None,
    column_specs: Optional[List[ColumnSpec]] = None,
) -> None:
    """
    Generate (or enhance) a CSV file.

    Args:
        output_file:    Destination path for the output CSV.
        number_of_rows: How many rows to generate (ignored when input_file is given).
        input_file:     Path to an existing CSV to enhance. When provided, new rows
                        are NOT generated — the existing data is enriched in-place.
        column_specs:   List of ColumnSpec objects describing columns to add/overwrite.
                        Defaults to DEFAULT_COLUMN_SPECS (reporting_date + frequency).
    """
    specs = column_specs if column_specs is not None else DEFAULT_COLUMN_SPECS

    # ── Load source rows ──────────────────────────────────────────────────────
    if input_file:
        source_path = Path(input_file)
        if not source_path.exists():
            raise FileNotFoundError(f"Input file not found: {input_file}")
        rows, headers = load_csv(input_file)
        print(f"📂 Loaded {len(rows)} rows from: {input_file}")
    else:
        rows = [generate_row(i) for i in range(1, number_of_rows + 1)]
        headers = list(HEADER)
        print(f"🔨 Generated {len(rows)} fresh rows")

    # ── Apply column overrides / additions ────────────────────────────────────
    if specs:
        rows, headers = apply_column_specs(rows, specs, headers)
        applied = [s.name for s in specs]
        print(f"🔧 Applied column specs: {applied}")

    # ── Write output ──────────────────────────────────────────────────────────
    write_csv(output_file, rows, headers)
    print(f"\n✅ CSV written successfully: {output_file}")
    print(f"📊 Total rows: {len(rows)}  |  Columns: {len(headers)}")


# ---------------------------------------------------
# CLI
# ---------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate or enhance a job-run CSV with configurable column ranges."
    )
    parser.add_argument("-o", "--output", default=OUTPUT_FILE, help="Output CSV path")
    parser.add_argument("-n", "--rows", type=int, default=NUMBER_OF_ROWS,
                        help="Number of rows to generate (ignored with --input)")
    parser.add_argument("-i", "--input", default=None,
                        help="Existing CSV to enhance instead of generating fresh rows")
    parser.add_argument("--business-days", type=int, default=7,
                        help="Number of recent business days for reporting_date (default: 7)")
    parser.add_argument("--frequencies", nargs="+", default=["DAILY", "MONTHLY"],
                        metavar="FREQ",
                        help="Frequency values to sample from (default: DAILY MONTHLY)")
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    # Build specs from CLI args — easy to extend further
    specs = [
        ColumnSpec(
            name="reporting_date",
            values=last_n_business_dates(args.business_days),
        ),
        ColumnSpec(
            name="frequency",
            values=args.frequencies,
        ),
    ]

    generate_csv(
        output_file=args.output,
        number_of_rows=args.rows,
        input_file=args.input,
        column_specs=specs,
    )


if __name__ == "__main__":
    main()