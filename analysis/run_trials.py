"""Run multiple single-trial Java runs via Maven and collect successful CSV outputs.

This script is intentionally configured by editing the constants below.
It runs trials sequentially, calling mvn exec:java for the Java class
www.ontologyutils.apps.SingleTrialExperiment. Pass the ontology path as the
first CLI argument. Each successful trial writes a one-row CSV named
iic-{ontologyBasename}-{seed}.csv into OUT_DIR.

Failures are recorded in RUN_LOG_PATH (stdout/stderr excerpts). Partial CSVs
from failed runs are removed so only successful trials remain in OUT_DIR.
"""

from __future__ import annotations

import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
import csv

# ----------------- CONFIGURE HERE -----------------
# Number of successful trials to collect
N_TRIALS = 100
BASE_SEED = 13
STEP = 5
# Resume counters (set non-zero to resume a previous run)
START_SUCCESSFUL_TRIALS = 0
START_ATTEMPTED_TRIALS = 0
# Output directory for per-trial CSVs (analyze_iic.py expects files under analysis/data/..)
OUT_DIR = Path(__file__).parent / "data" / "shapley-shapley"
# Run log path
RUN_LOG_PATH = Path(__file__).parent / "run_trials.log"

# Timeouts (seconds) — match names used in RepairComparisonExperiment.java
REMOVAL_TIMEOUT_SECONDS = 300
WEAKENING_TIMEOUT_SECONDS = 300
POWER_INDEX_TIMEOUT_SECONDS = 300
MAKE_INCONSISTENT_TIMEOUT_SECONDS = 300

# Maven command template. We use mvn exec:java and pass args via -Dexec.args
# The formatted args string must be quoted as a single argument to Maven.
MVN_BASE = ["mvn", "-q", "exec:java", "-Dexec.mainClass=www.ontologyutils.apps.SingleTrialExperiment"]
# -------------------------------------------------

OUT_DIR.mkdir(parents=True, exist_ok=True)

# Run id (timestamp) appended to output filenames so analyze_iic.py can strip the trailing digits
RUN_ID = datetime.now().strftime("%Y%m%d%H%M%S%f")


def timestamp():
	return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def write_log_header(handle, trial_index, seed, out_path):
	handle.write(f"----- Trial {trial_index} seed={seed} out={out_path} at {timestamp()} -----\n")


def run_trial(trial_index, seed, out_path, ontology_path):
	# Build exec.args string
	args_list = [
		"--ontology", str(ontology_path),
		"--seed", str(seed),
		"--out", str(out_path),
		"--run-id", str(RUN_ID),
		"--removal-timeout-secs", str(REMOVAL_TIMEOUT_SECONDS),
		"--weakening-timeout-secs", str(WEAKENING_TIMEOUT_SECONDS),
		"--power-index-timeout-secs", str(POWER_INDEX_TIMEOUT_SECONDS),
		"--make-inconsistent-timeout-secs", str(MAKE_INCONSISTENT_TIMEOUT_SECONDS),
	]
	# Join args into a single string; ensure proper quoting for spaces (none expected here)
	exec_args = " ".join(str(a) for a in args_list)
	# Build a single command string to run via shell so mvn is resolved the same way as in a user shell
	# Use exec:java before -Dexec.mainClass to avoid lifecycle parsing issues
	cmd_str = "mvn -q exec:java -Dexec.mainClass=www.ontologyutils.apps.SingleTrialExperiment -Dexec.args=\"" + exec_args + "\""
	proc = subprocess.run(cmd_str, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=True)
	return proc.returncode, proc.stdout, proc.stderr


def verify_csv(out_path):
	try:
		with open(out_path, "r", encoding="utf-8") as fh:
			lines = [l.strip() for l in fh.readlines() if l.strip()]
		if len(lines) < 2:
			return False, "CSV has no data rows"
		header = lines[0]
		data = lines[-1]
		parts = [p.strip() for p in data.split(",")]
		if len(parts) < 3:
			return False, "CSV data row has fewer than 3 columns"
		# try parse floats
		for p in parts[:3]:
			float(p)
		return True, "OK"
	except Exception as e:
		return False, f"CSV verification error: {e}"


def main():
	if len(sys.argv) < 2:
		raise SystemExit(f"Usage: {Path(sys.argv[0]).name} <ontology-path>")

	ontology_path = Path(sys.argv[1])
	successes = START_SUCCESSFUL_TRIALS
	failures = []
	attempted = START_ATTEMPTED_TRIALS

	# prepare master CSV for this run (single CSV file)
	ontology_basename = ontology_path.stem
	master_filename = f"iic-{ontology_basename}-{RUN_ID}.csv"
	master_path = OUT_DIR / master_filename
	if not master_path.exists():
		with open(master_path, "w", encoding="utf-8", newline="") as mfh:
			writer = csv.writer(mfh)
			writer.writerow(["iic_power_vs_random", "iic_power_vs_not_in_largest_mcs", "iic_power_vs_weakening", "run_id"])

	# patterns to extract IIC values from Java stdout
	import re
	pat_random = re.compile(r"IIC \(Power index wrt Random removal\):\s*([0-9eE+\-\.]+)")
	pat_mcs = re.compile(r"IIC \(Power index wrt Not-in-largest-MCS removal\):\s*([0-9eE+\-\.]+)")
	pat_weak = re.compile(r"IIC \(Power index wrt Weakening\):\s*([0-9eE+\-\.]+)")

	with open(RUN_LOG_PATH, "a", encoding="utf-8") as log:
		log.write(f"\n=== run_trials started at {timestamp()} run_id={RUN_ID} ===\n")
		# Continue until we have N_TRIALS successful trials
		while successes < N_TRIALS:
			attempt_number = attempted + 1
			seed = BASE_SEED + attempted * STEP
			# use a temporary output file for Java (not the master) to avoid conflicts
			temp_out = OUT_DIR / f"iic-temp-{seed}-{RUN_ID}.csv"
			write_log_header(log, attempt_number, seed, temp_out)
			log.flush()

			retcode, stdout, stderr = run_trial(attempt_number, seed, temp_out, ontology_path)

			# write Java stdout/stderr into the run log for traceability
			if stdout:
				log.write("--- stdout ---\n")
				log.write(stdout + "\n")
			if stderr:
				log.write("--- stderr ---\n")
				log.write(stderr[:2000] + ("\n...[truncated]\n" if len(stderr) > 2000 else "\n"))

			# parse stdout for the three IIC values
			if retcode == 0:
				m1 = pat_random.search(stdout)
				m2 = pat_mcs.search(stdout)
				m3 = pat_weak.search(stdout)
				if m1 and m2 and m3:
					try:
						vals = [float(m1.group(1)), float(m2.group(1)), float(m3.group(1)), RUN_ID]
						with open(master_path, "a", encoding="utf-8", newline="") as mfh:
							writer = csv.writer(mfh)
							writer.writerow(vals)
						log.write(f"SUCCESS: attempt={attempt_number} seed={seed} appended to {master_path}\n")
						successes += 1
					except Exception as e:
						log.write(f"FAIL: attempt={attempt_number} seed={seed} - append error: {e}\n")
						failures.append((attempt_number, seed, f"append_error_{e}"))
				else:
					log.write(f"FAIL: attempt={attempt_number} seed={seed} - could not parse IIC values from stdout\n")
					failures.append((attempt_number, seed, "parse_error"))
			else:
				log.write(f"FAIL: attempt={attempt_number} seed={seed} - process exited with code {retcode}\n")
				failures.append((attempt_number, seed, f"exit_code_{retcode}"))

			# cleanup temp_out if created
			try:
				if temp_out.exists():
					temp_out.unlink()
			except Exception:
				pass

			attempted += 1
			log.write(f"progress: {successes} successful, {len(failures)} failed, {attempted} attempted so far\n")
			log.flush()
			# small sleep to avoid hammering disk/CPU
			time.sleep(0.1)

		log.write(f"=== run_trials finished at {timestamp()} - {successes} successes, {len(failures)} failures, {attempted} attempted ===\n")
		if failures:
			log.write("Failed trials:\n")
			for t, s, m in failures:
				log.write(f"  trial={t} seed={s} msg={m}\n")
		log.write("\n")

	print(f"Done: {successes} successes, {len(failures)} failures. Master CSV: {master_path}. See {RUN_LOG_PATH}")


if __name__ == "__main__":
	main()





















