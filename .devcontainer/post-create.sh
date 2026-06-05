#!/bin/bash
# ------------------------------------------------------------------
# Post-creation setup for ontologyutils Dev Container
# Runs inside the container after it is created.
# Steps:
#   1. Install Fact++ jar into local Maven repository
#   2. Fix workspace permissions (needed for bind-mounted volumes)
#   3. Build the Java project (compile + test)
#   4. Create Python virtual environment and install dependencies
# ------------------------------------------------------------------
set -e

echo "========================================"
echo " Step 1/4: Installing Fact++ Maven dependency"
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
echo " Step 2/4: Fixing workspace permissions"
echo "========================================"
sudo chown -R vscode:vscode /workspaces/ontologyutils-ekaw26

echo ""
echo "========================================"
echo " Step 3/4: Building ontologyutils with Maven"
echo "========================================"
mvn package -DskipTests -q

echo ""
echo "========================================"
echo " Step 4/4: Setting up Python virtual environment"
echo "========================================"
PYTHON_DIR="repair-power-index-replication-ekaw26"
python3 -m venv "${PYTHON_DIR}/.venv"
source "${PYTHON_DIR}/.venv/bin/activate"
pip install --upgrade pip -q
pip install -r "${PYTHON_DIR}/analysis/requirements.txt" -q

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
echo "   python ${PYTHON_DIR}/analysis/run_trials.py 1"
