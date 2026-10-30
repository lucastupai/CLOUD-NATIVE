# Guía de Montaje en AWS Learner Lab / EC2 — VidaSalud

Esta guía detalla los pasos exactos para crear las instancias EC2 en el laboratorio de AWS (AWS Academy / Learner Lab) y desplegar la arquitectura completa de microservicios, clúster RabbitMQ y Kafka solicitada en la **Evaluación Parcial N.º 3**.

---

## 1. Arquitectura de Instancias en AWS EC2

Según los requerimientos del caso, se definen 3 servidores EC2:

| Nombre de Instancia | Tipo sugerido | AMI recomendada | Función / Contenedores |
|---|---|---|---|
| **`ec2-mq`** | `t3.small` / `t3.medium` | Amazon Linux 2023 | Clúster RabbitMQ (2 nodos) + Management UI |
| **`ec2-apps`** | `t3.medium` | Amazon Linux 2023 | BFF, Appointments, Catalog, Notifications, Rabbit-Admin |
| **`ec2-kafka`** | `t3.medium` / `t3.large` | Amazon Linux 2023 | Zookeeper (3 nodos) + Kafka (3 brokers) + Kafka UI |

> **Nota para Learner Lab:** Si el crédito o límite de vCPUs es ajustado, se puede iniciar primero `ec2-mq` y `ec2-apps` para la entrega de RabbitMQ (EP3).

---

## 2. Configuración de Security Groups (Grupos de Seguridad)

Crea 3 Security Groups en tu VPC por defecto de AWS:

### A. `sg-vidasalud-mq` (Para ec2-mq)
* **SSH (22):** Mi IP (o `0.0.0.0/0` para acceso del grupo)
* **AMQP (5672):** Tráfico desde `sg-vidasalud-apps` (o la subred VPC `172.31.0.0/16`)
* **RabbitMQ Web Management (15672):** Mi IP o `0.0.0.0/0` (para demostración docente)

### B. `sg-vidasalud-apps` (Para ec2-apps)
* **SSH (22):** Mi IP (o `0.0.0.0/0`)
* **HTTP BFF (8080):** `0.0.0.0/0` (para conexión desde el frontend Angular / API Gateway)
* **APIs de microservicios (8081, 8082, 8083, 8084):** Opcional para pruebas directas desde tu IP.

### C. `sg-vidasalud-kafka` (Para ec2-kafka)
* **SSH (22):** Mi IP
* **Kafka Broker (9092, 9093, 9094):** Desde la VPC / `sg-vidasalud-apps`
* **Kafka UI (8085):** Mi IP o `0.0.0.0/0`

---

## 3. Lanzamiento de las Instancias EC2

1. En la consola de AWS EC2 → **Launch instances**.
2. **Nombre:** `ec2-mq` (repetir para `ec2-apps` y `ec2-kafka`).
3. **AMI:** Amazon Linux 2023 AMI (HVM).
4. **Key Pair:** Selecciona tu par de claves `.pem` (o `vockey` de AWS Learner Lab).
5. **Network settings:** Asigna el Security Group correspondiente (`sg-vidasalud-mq`).
6. **Advanced details → User data:** Pega el contenido de `infra/cloud/user-data-docker.sh` para que Docker y Docker Compose queden instalados automáticamente al iniciar.
7. Haz clic en **Launch instance**.

---

## 4. Despliegue en `ec2-mq` (RabbitMQ)

1. Conéctate por SSH a la IP pública de `ec2-mq`:
   ```bash
   ssh -i tu-clave.pem ec2-user@<IP_PUBLICA_EC2_MQ>
   ```
2. Clona el repositorio o sube la carpeta `infra/mq`:
   ```bash
   git clone <URL_REPOSITORIO_GITHUB>
   cd CLOUD-main/infra/mq
   ```
3. Inicia el clúster RabbitMQ:
   ```bash
   docker compose up -d
   ```
4. Comprueba el estado:
   ```bash
   docker compose ps
   ```
5. Accede a la consola web de RabbitMQ en el navegador:
   `http://<IP_PUBLICA_EC2_MQ>:15672`  
   *(Usuario: `vidasalud` / Password: `vidasalud-local` o la configurada en .env)*

---

## 5. Despliegue en `ec2-apps` (Microservicios)

1. Conéctate por SSH a la IP pública de `ec2-apps`:
   ```bash
   ssh -i tu-clave.pem ec2-user@<IP_PUBLICA_EC2_APPS>
   ```
2. Clona el repositorio:
   ```bash
   git clone <URL_REPOSITORIO_GITHUB>
   cd CLOUD-main/infra/apps
   ```
3. Configura la IP privada de `ec2-mq` en el archivo `.env`:
   ```bash
   nano .env
   ```
   Agrega:
   ```ini
   RABBITMQ_HOST=<IP_PRIVADA_EC2_MQ>
   RABBITMQ_PORT=5672
   RABBITMQ_USER=vidasalud
   RABBITMQ_PASSWORD=vidasalud-local
   ENTRA_TENANT_ID=d6edb470-bae6-41a0-b8c5-c2f55613b833
   ENTRA_API_CLIENT_ID=2590980d-413c-42a8-a1c7-fc1289f4fb44
   CORS_ALLOWED_ORIGINS=http://localhost:4200,https://<URL_FRONTEND>
   ```
4. Construye e inicia los microservicios:
   ```bash
   docker compose up -d --build
   ```
5. Verifica los logs:
   ```bash
   docker compose logs -f notifications
   ```
   Observarás que las colas (`q.cmd.email`, `q.cmd.admission`, `q.cmd.record`) y los exchanges (`cmd.direct`, `cmd.topic`, `cmd.dead.dlx`) se declaran automáticamente en RabbitMQ.

---

## 6. Conexión del Frontend Angular

En `frontend-vidasalud/src/environments/environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  entra: {
    clientId: '3d3e2f6b-858d-4cf0-92ea-53497416e34d',
    tenantId: 'd6edb470-bae6-41a0-b8c5-c2f55613b833',
    authority: 'https://login.microsoftonline.com/d6edb470-bae6-41a0-b8c5-c2f55613b833',
    redirectUri: 'http://localhost:4200', // o tu URL en S3 / Amplify
    postLogoutRedirectUri: 'http://localhost:4200/login'
  },
  apiBaseUrl: 'http://<IP_PUBLICA_EC2_APPS>:8080',
  apiScope: 'api://2590980d-413c-42a8-a1c7-fc1289f4fb44/access_as_user'
};
```
