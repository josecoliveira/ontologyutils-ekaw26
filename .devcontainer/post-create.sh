#!/bin/bash
# ------------------------------------------------------------------
# Post-creation setup for ontologyutils Dev Container
# Steps:
#   1. Build the Java project (compile only, skip tests)
#   2. Create Python virtual environment and install dependencies
# ------------------------------------------------------------------
set -e

echo "========================================"
echo " Step 1/2: Building ontologyutils with Maven"
echo "========================================"
# Allow container user to write to workspace (adds w permission for everyone, no ownership change)
CURRENT_DIR="$(pwd)"
sudo chmod -R a+w "$CURRENT_DIR" || true
# Skip clean phase to avoid permission issues on bind-mounted volumes
mvn package -DskipTests -q

echo ""
echo "========================================"
echo " Step 2/2: Setting up Python virtual environment"
echo "========================================"
PYTHON_DIR="repair-power-index-replication-ekaw26"
# --clear: the venv lives in the bind-mounted workspace, so it survives a
# container rebuild. Without clearing, a venv created under a different
# architecture (e.g. amd64 under emulation) keeps its incompatible wheels and
# the packages fail to import.
python3 -m venv --clear "${PYTHON_DIR}/.venv"
source "${PYTHON_DIR}/.venv/bin/activate"
pip install --upgrade pip -q
pip install -r "${PYTHON_DIR}/requirements.txt" -q

echo ""
echo "========================================"
echo " Setup complete!"
echo "========================================"
echo " Java:  $(java -version 2>&1 | head -1)"
echo " Maven: $(mvn --version 2>&1 | head -1)"
echo " Python venv: ${PWD}/${PYTHON_DIR}/.venv"
echo ""
echo " Quick sanity check:"
echo "   source ${PYTHON_DIR}/.venv/bin/activate"
echo "   python -c \"import pandas; print('pandas', pandas.__version__)\""
echo "   python ${PYTHON_DIR}/run_trials.py 1"
