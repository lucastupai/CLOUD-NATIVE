# VidaSalud EP3 — README de instalación y despliegue local

**Plataforma:** Windows 10, PowerShell, Visual Studio Code y Docker Desktop (WSL 2).  
**Objetivo:** ejecutar la implementación actual de VidaSalud con RabbitMQ sin depender de Azure for Students ni AWS Academy para las pruebas de mensajería.

## 1. Requisitos

- Visual Studio Code y extensiones recomendadas: Java Extension Pack, Spring Boot Extension Pack, Angular Language Service, Docker y YAML.
- Git.
- Node.js **22** con npm (para Angular 19).
- Docker Desktop con motor Linux/WSL 2 iniciado.
- JDK **21** si se requiere compilar/depurar Spring Boot desde el equipo. El build con Docker ya incluye Java/Maven en sus imágenes.
- Acceso a los archivos del proyecto `ev3Cloud-main`.

Validación desde la terminal de VS Code:

```powershell
git --version
node --version
npm --version
java -version
docker --version
docker compose version
docker info
```

Si PowerShell impide usar `npm.ps1`, puede ejecutarse `npm.cmd` o configurar para el usuario, si es apropiado:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

## 2. Estructura del proyecto

```text
ev3Cloud-main/
├── frontend-vidasalud/
├── ms-vidasalud-bff/
├── ms-vidasalud-appointments/
├── ms-vidasalud-catalog/
├── ms-vidasalud-notifications/
├── ms-vidasalud-rabbit-admin/
└── infra/
    └── apps/
        ├── compose.local.yml
        └── .env.rabbitmq.example
```

Abre la carpeta raíz en VS Code. Todos los comandos siguientes parten desde esa carpeta, salvo indicación contraria.

## 3. Configurar RabbitMQ para desarrollo

En una terminal PowerShell:

```powershell
cd .\infra\apps
Copy-Item .env.rabbitmq.example .env
Get-Content .env
```

Valores de **la versión más reciente del proyecto**:

```dotenv
RABBITMQ_USER=vidasalud
RABBITMQ_PASSWORD=vidasalud-local
RABBIT_ADMIN_KEY=vidasalud-admin-local-2026-key
```

Son valores de demostración, **solo para entorno local**. El archivo `.env` no debe publicarse con credenciales reales. Las variables de Azure y AWS quedan para una fase posterior.

**Nota sobre volúmenes antiguos:** si RabbitMQ ya se inició con otra contraseña y se conserva `rabbitmq_data`, las variables `RABBITMQ_DEFAULT_*` no recrean usuarios existentes. Para sincronizar la contraseña sin borrar mensajes, con el contenedor activo:

```powershell
docker compose -f compose.local.yml exec rabbitmq rabbitmqctl change_password vidasalud vidasalud-local
```

## 4. Ejecutar RabbitMQ y microservicios nuevos

Con Docker Desktop activo, desde `infra/apps`:

```powershell
docker compose -f compose.local.yml config --quiet
docker compose -f compose.local.yml up -d --build rabbitmq notifications rabbit-admin
docker compose -f compose.local.yml ps
```

Abrir `http://localhost:15672` con usuario `vidasalud` y contraseña `vidasalud-local`.

Revisar en **Queues and Streams**:

- `vidasalud.notifications.queue`
- `vidasalud.status.queue`
- `vidasalud.notifications.dlq`

Revisar en **Exchanges**:

- `vidasalud.appointments.exchange`
- `vidasalud.appointments.dlx`

Logs:

```powershell
docker compose -f compose.local.yml logs --tail=80 rabbitmq notifications rabbit-admin
```

## 5. Ejecutar microservicios de citas y catálogo

Estos servicios utilizan H2 en memoria con perfil `local`, así que no se necesita instalar Oracle para las pruebas locales.

```powershell
docker compose -f compose.local.yml up -d --build catalog appointments
docker compose -f compose.local.yml ps
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8082/actuator/health
```

Verificar `status: UP` y registros de inicio:

```powershell
docker compose -f compose.local.yml logs --tail=60 catalog appointments
```

La configuración `compose.local.yml` incluye `RABBITMQ_ENABLED: "true"` para Appointments, de manera que puede publicar eventos en el broker `rabbitmq` de la red Docker.

## 6. Ejecutar Angular por separado

Abrir **otra terminal** de VS Code en la raíz del proyecto:

```powershell
cd .\frontend-vidasalud
npm ci
npm run build
npm start
```

Abrir `http://localhost:4200/login`.

**Atención:** Angular mantiene los registros MSAL del desarrollo previo; `/login` puede visualizarse, pero el login completo y las rutas protegidas (`/dashboard`) pueden fallar hasta registrar las aplicaciones en **tu propio Microsoft Entra ID**. No es necesario desactivar guards ni modificar rutas para probar RabbitMQ. `npm ci` puede mostrar advertencias de dependencias; no aplicar `npm audit fix --force` automáticamente sin revisar compatibilidad.

## 7. BFF (opcional durante las pruebas locales)

Para arrancar el contenedor existente:

```powershell
cd ..\infra\apps
docker compose -f compose.local.yml up -d --build bff
```

**Limitación:** `compose.local.yml` contiene `ENTRA_TENANT_ID=REPLACE_TENANT_ID` y `ENTRA_API_CLIENT_ID=REPLACE_API_CLIENT_ID`. Son placeholders de Azure pendientes. No habrá un login/JWT válido a través del BFF hasta configurar tu tenant y registro de API. No se deben sustituir por IDs inventados ni eliminar la seguridad para las pruebas.

## 8. Prueba mínima de mensajería

En RabbitMQ Management: **Exchanges → vidasalud.appointments.exchange → Publish message**.

Routing key: `appointment.created`.

```json
{"eventId":"local-ok-001","eventType":"appointment.created","appointment":{"id":1001}}
```

Comprobar:

```powershell
docker compose -f compose.local.yml logs --tail=40 notifications
```

Debe aparecer `Notificación generada` con el ID correspondiente.

Para verificar DLQ, publicar en el mismo exchange y con la misma routing key:

```json
{"eventId":"local-error-001","eventType":"appointment.created","appointment":{"id":1002},"simulateFailure":true}
```

La cola `vidasalud.notifications.dlq` debe recibir el mensaje rechazado. Su consumidor está desactivado al inicio para permitir la inspección de mensajes fallidos.

## 9. Prueba del administrador REST

Desde PowerShell, configurar el encabezado con la clave **local**:

```powershell
$headers = @{ "X-Admin-Key" = "vidasalud-admin-local-2026-key" }
$body = @{ name = "vidasalud.test.queue"; durable = $true } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/queues" -Method POST -Headers $headers -ContentType "application/json" -Body $body
```

Verificar que aparece en Management; luego eliminar:

```powershell
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/queues/vidasalud.test.queue" -Method DELETE -Headers $headers
```

Todos los endpoints disponibles y las pruebas de exchange/binding están detallados en `README_PRUEBAS_EP3.md`.

## 10. Prueba real de creación de cita

Con RabbitMQ, Notifications, Catalog y Appointments ejecutándose:

```powershell
$body = @{
  patientId = "PAC-001"
  patientName = "Paciente Prueba"
  patientEmail = "prueba@vidasalud.cl"
  serviceId = 1
  scheduledAt = "2026-11-15T10:00:00-03:00"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/appointments" -Method POST -ContentType "application/json" -Body $body

docker compose -f compose.local.yml logs --tail=80 appointments notifications
```

**Actualizar `scheduledAt` por una fecha/hora futura** si ejecutas esta prueba después de noviembre de 2026. El catálogo debe contener el `serviceId` usado. Verificar que se creó la cita y que Notifications registró `appointment.created`.

La llamada directa a `localhost:8081` comprueba la integración de microservicios; no equivale a validar la seguridad JWT del BFF.

## 11. Diagnóstico y limpieza

Consultar estado y logs:

```powershell
docker compose -f compose.local.yml ps
docker compose -f compose.local.yml logs --tail=100 rabbitmq notifications rabbit-admin appointments catalog
```

Si Rabbit Admin devuelve **401**, revisar la variable `RABBIT_ADMIN_KEY` del `.env` local y el encabezado `X-Admin-Key`. Si RabbitMQ Management devuelve **Not Authorized**, revisar la contraseña interna del usuario, sobre todo si existe un volumen anterior.

Detener contenedores conservando los datos del volumen RabbitMQ:

```powershell
docker compose -f compose.local.yml down
```

**No usar `down -v` a menos que realmente quieras borrar las colas persistentes, usuarios y mensajes**. Las bases H2 en memoria pierden los datos al detener/recrear sus respectivos servicios.

## 12. Alcance de esta guía

El procedimiento local permite comprobar la mensajería sin cuentas cloud. Quedan para una etapa posterior: nuevo tenant/registro de aplicaciones en Azure for Students; recursos AWS Academy EC2/API Gateway; configuración de seguridad JWT y despliegue seguro, sin exponer Rabbit Admin ni RabbitMQ Management a Internet.
