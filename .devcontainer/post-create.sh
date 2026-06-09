#!/bin/bash
set -e

echo "=== Installing Python dependencies ==="
python3 -m pip install --upgrade pip
python3 -m pip install -r repair-power-index-replication-ekaw26/requirements.txt

echo "=== Building Java project (skipping tests for first build) ==="
mvn clean package -DskipTests

echo "=== Post-create setup complete ==="
echo "Java project JAR: $(pwd)/target/shaded-ontologyutils-0.1.0.jar"
echo "Python environment ready in repair-power-index-replication-ekaw26/"
