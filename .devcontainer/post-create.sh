#!/bin/bash
# ------------------------------------------------------------------
# Post-creation setup for ontologyutils Dev Container
# Steps:
#   1. Configure Maven settings for GitHub Packages authentication
#   2. Build the Java project (compile only, skip tests)
#   3. Create Python virtual environment and install dependencies
# ------------------------------------------------------------------
set -e

echo "========================================"
echo " Step 1/3: Configuring Maven settings for GitHub Packages"
echo "========================================"

# Try to load env vars from a local .env file (useful on Windows where
# ${localEnv:VAR} in devcontainer.json may not resolve properly).
ENV_FILE=".devcontainer/devcontainer.env"
if [ -f "$ENV_FILE" ]; then
    echo "Sourcing environment from $ENV_FILE ..."
    set -a
    # shellcheck source=/dev/null
    . "$ENV_FILE"
    set +a
fi

if [ -z "$GITHUB_USER" ] || [ -z "$GITHUB_TOKEN" ]; then
    echo "WARNING: GITHUB_USER and/or GITHUB_TOKEN are not set."
    echo "The build may fail when resolving dependencies from GitHub Packages."
    echo ""
    echo "To fix this, use one of the following methods:"
    echo ""
    echo "  1) Set the variables in your host shell before starting VS Code:"
    echo "     export GITHUB_USER=<your-username>"
    echo "     export GITHUB_TOKEN=<your-token>"
    echo ""
    echo "  2) Create .devcontainer/devcontainer.env from the template:"
    echo "     cp .devcontainer/devcontainer.env.example .devcontainer/devcontainer.env"
    echo "     # Then edit .devcontainer/devcontainer.env with your credentials"
    echo ""
    echo "  The dev container will pick them up on rebuild."
    echo ""
    echo "Continuing with build (may fail if GitHub Packages requires auth)..."
else
    mkdir -p ~/.m2
    python3 -c "
import os
settings = '''<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"
  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
  xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd\">
  <servers>
    <server>
      <id>github</id>
      <username>\${env.GITHUB_USER}</username>
      <password>\${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>'''
path = os.path.expanduser('~/.m2/settings.xml')
with open(path, 'w') as f:
    f.write(settings)
print('Maven settings.xml created at', path)
"
fi

echo ""
echo "========================================"
echo " Step 2/3: Building ontologyutils with Maven"
echo "========================================"
# Allow container user to write to workspace (adds w permission for everyone, no ownership change)
CURRENT_DIR="$(pwd)"
sudo chmod -R a+w "$CURRENT_DIR" || true
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
