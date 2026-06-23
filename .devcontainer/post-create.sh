#!/bin/bash
set -e

echo "=== Installing Python dependencies ==="
# pip is pre-installed via Debian (v24.0); skip upgrade to avoid RECORD-file
# errors from Debian-managed packages. 24.0 is recent enough for all deps.
python3 -m pip install -r repair-power-index-replication-ekaw26/requirements.txt --break-system-packages

echo "=== Building Java project (skipping tests for first build) ==="
mvn clean package -DskipTests

echo "=== Post-create setup complete ==="
echo "Java project JAR: $(pwd)/target/shaded-ontologyutils-0.1.0.jar"
echo "Python environment ready in repair-power-index-replication-ekaw26/"
