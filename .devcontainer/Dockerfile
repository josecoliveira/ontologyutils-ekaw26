# ------------------------------------------------------------------
# ontologyutils Dev Container — Java 17 + Maven + Python 3
# Base: maven:3.9-eclipse-temurin-17 (Debian Bookworm, JDK 17 + Maven 3.9)
# ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17

# Avoid interactive prompts during apt install
ENV DEBIAN_FRONTEND=noninteractive

# Install Python 3, pip, and venv (Python 3.11 on Debian Bookworm)
RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        python3-venv \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user for VS Code (matches the default remoteUser convention)
# The base maven image already has a user with UID 1000, so we use 1001.
ARG USERNAME=vscode
ARG USER_UID=1001
ARG USER_GID=$USER_UID
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && apt-get update && apt-get install -y --no-install-recommends sudo \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME \
    && rm -rf /var/lib/apt/lists/*

# Restore the default DEBIAN_FRONTEND
ENV DEBIAN_FRONTEND=dialog

# Set the default user
USER $USERNAME

# Default working directory (workspace is mounted here by devcontainer.json)
WORKDIR /workspace
