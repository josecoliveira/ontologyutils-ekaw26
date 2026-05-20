# Analysis tools

This folder contains Python helpers used to run experiments and analyze IIC results.

## run_trials.py

Purpose:
- Run repeated single-trial Java experiments via Maven (`www.ontologyutils.apps.SingleTrialExperiment`).
- Collect successful-trial outputs as one-row CSVs into `analysis/data/shapley-shapley`.
- Produce a per-run log named `run_trials-<ontology>-<timestamp>.log` in `analysis/` with detailed per-attempt outputs and a timing summary.

Usage:

```bash
python analysis/run_trials.py <ontology-path> [N_TRIALS]
```

- `<ontology-path>`: path to the ontology file to run (required).
- `[N_TRIALS]`: optional integer to override the default `N_TRIALS` configured in the script (useful for quick tests).

Behavior highlights:
- JVM flags `-Xms1g -Xmx8g -Xss8m` are passed to the Java process to reduce out-of-memory failures.
- The script writes a master CSV named `iic-<ontology>-<run_id>.csv` into `analysis/data/shapley-shapley`.
- Per-execution log files are written to `analysis/run_trials-<ontology>-<timestamp>.log` and include a `Timing summary` block with:
  - wall clock start/end and total seconds
  - per-outcome aggregates for `success`, `time_limit_exceeded`, and `memory_limit_exceeded` (count, total seconds, average seconds)
  - per-attempt runtimes and outcomes

Example:

```bash
python analysis/run_trials.py src/test/resources/ekaw26/cleanup/pe.owl 1
```

Notes:
- The script intentionally does not attempt to fix Java memory issues; it only attempts to reduce them by passing larger heap/stack arguments.
- The Java call is invoked via `mvn exec:java` so Maven and a JDK must be installed.

## analyze_iic.py

Purpose:
- Read the per-trial CSV outputs in `analysis/data/shapley-shapley` and compute summary statistics (mean, sd, 95% CI) per ontology and comparison.
- Write a CSV summary to `analysis/output/shapley-shapley/iic_summary.csv` and a Markdown report `analysis/output/shapley-shapley/iic_report.md`.
- Create a LaTeX table `analysis/output/shapley-shapley/iic_summary.tex` for inclusion in manuscripts.

Usage:

```bash
python analysis/analyze_iic.py
```

Requirements:
- `numpy`, `pandas`, `scipy` (install into the analysis virtualenv).

Decision rule used in the report:
- Reject H0 if the lower bound of the 95% confidence interval is greater than 0.5.

## Outputs

- `analysis/data/shapley-shapley/*.csv` — per-run CSVs created by `run_trials.py`.
- `analysis/output/shapley-shapley/iic_summary.csv` — aggregated numeric summary.
- `analysis/output/shapley-shapley/iic_report.md` — human-readable Markdown report.
- `analysis/output/shapley-shapley/iic_summary.tex` — LaTeX table.

## Quick checklist

- Ensure Maven and Java 17+ are installed for running `run_trials.py`.
- Activate the `analysis/venv` (if used) and install `numpy pandas scipy` before running `analyze_iic.py`.
