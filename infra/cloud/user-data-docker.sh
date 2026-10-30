#!/bin/bash
# Script de aprovisionamiento automatizado (User Data) para Amazon Linux 2023 / Ubuntu
# VidaSalud - Plataforma Cloud Native (Duoc UC)

set -e

echo "=== INICIANDO CONFIGURACIÓN DE NODO EC2 ==="

if command -v dnf &> /dev/null; then
    # Amazon Linux 2023 / RHEL / Fedora
    dnf update -y
    dnf install -y docker git
    systemctl enable docker
    systemctl start docker
    usermod -aG docker ec2-user

    # Instalar Docker Compose Plugin v2
    DOCKER_CONFIG=${DOCKER_CONFIG:-/usr/local/lib/docker}
    mkdir -p $DOCKER_CONFIG/cli-plugins
    curl -SL https://github.com/docker/compose/releases/download/v2.29.1/docker-compose-linux-x86_64 -o $DOCKER_CONFIG/cli-plugins/docker-compose
    chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose
    ln -s $DOCKER_CONFIG/cli-plugins/docker-compose /usr/local/bin/docker-compose || true

elif command -v apt-get &> /dev/null; then
    # Ubuntu / Debian
    apt-get update -y
    apt-get install -y ca-certificates curl gnupg git
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable docker
    systemctl start docker
    usermod -aG docker ubuntu
fi

echo "=== DOCKER Y DOCKER COMPOSE INSTALADOS EXITOSAMENTE ==="
