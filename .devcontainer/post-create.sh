#!/bin/bash
# ------------------------------------------------------------------
# Post-creation setup for ontologyutils Dev Container
# Steps:
#   1. Install Fact++ jar into local Maven repository
#   2. Build the Java project (compile only, skip tests)
#   3. Create Python virtual environment and install dependencies
# ------------------------------------------------------------------
set -e

echo "========================================"
echo " Step 1/3: Installing Fact++ Maven dependency"
echo "========================================"
mvn install:install-file \
    -Dfile=lib/factplusplus-1.7.0.3.jar \
    -DgroupId=ontologyutils \
    -DartifactId=factplusplus \
    -Dversion=1.7.0.3 \
    -Dpackaging=jar \
    -q

echo ""
echo "========================================"
echo " Step 2/3: Building ontologyutils with Maven"
echo "========================================"
# Allow container user to write to workspace (adds w permission for everyone, no ownership change)
sudo chmod -R a+w /workspaces/ontologyutils-ekaw26 || true
# Skip clean phase to avoid permission issues on bind-mounted volumes
mvn package -DskipTests -q

echo ""
echo "========================================"
echo " Step 3/3: Setting up Python virtual environment"
echo "========================================"
PYTHON_DIR="repair-power-index-replication-ekaw26"
python3 -m venv "${PYTHON_DIR}/.venv"
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
