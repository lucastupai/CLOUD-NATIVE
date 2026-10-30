# VidaSalud — README de pruebas realizadas (EP3)

**Asignatura:** Desarrollo Cloud Native I (DSY1107)  
**Etapa:** Evaluación Parcial N.º 3 — RabbitMQ  
**Entorno de prueba:** Windows 10, Visual Studio Code, Docker Desktop, PowerShell y RabbitMQ Management.  
**Alcance:** Esta bitácora recoge las pruebas **reportadas como exitosas durante la sesión guiada**. No reemplaza una nueva ejecución automatizada ni acredita aún el despliegue en Azure/AWS.

## 1. Objetivo

Comprobar que la incorporación de RabbitMQ permite procesar eventos de citas de VidaSalud sin alterar los endpoints originales: exchanges, routing keys, bindings, colas, consumo con ACK manual, fallos con NACK y DLQ, y operaciones REST del administrador.

## 2. Servicios y puertos

| Servicio | Puerto local | Función |
|---|---:|---|
| Angular | 4200 | Interfaz, login MSAL |
| BFF | 8080 | API protegida con JWT; no validada completamente en estas pruebas |
| Appointments | 8081 | Gestión de citas y productor de eventos |
| Catalog | 8082 | Catálogo de prestaciones y boxes |
| Notifications | 8083 (solo localhost) | Consumidores RabbitMQ y logs |
| Rabbit Admin | 8084 (solo localhost) | Administración REST del broker |
| RabbitMQ AMQP | 5672 (solo localhost) | Mensajería |
| RabbitMQ Management | 15672 (solo localhost) | Administración web |

Desde la carpeta `infra/apps`, para consultar estado y registros:

```powershell
docker compose -f compose.local.yml ps
docker compose -f compose.local.yml logs --tail=60 appointments catalog notifications rabbit-admin rabbitmq
```

## 3. Resultado resumido de las comprobaciones

| ID | Comprobación | Resultado informado |
|---|---|---|
| P01 | `npm ci` en Angular | Correcto: 957 paquetes instalados; se informaron advertencias y vulnerabilidades de dependencias |
| P02 | `npm run build` | Correcto: `Application bundle generation complete` |
| P03 | Acceso a `/login` | La pantalla de login cargó; `/` mostraba pantalla blanca relacionada posiblemente con la ruta protegida/MSAL, no diagnosticada de forma concluyente |
| P04 | `docker compose ... config` | Sintaxis del Compose aceptada |
| P05 | Inicio de RabbitMQ y acceso a Management | Correcto; inicialmente se utilizó la contraseña anterior `replace-with-local-password` |
| P06 | Tres colas y dos exchanges de VidaSalud | Visibles en Management |
| P07 | Publicación manual de evento válido | Consumidor Notifications registró el mensaje |
| P08 | Publicación manual con `simulateFailure: true` | Mensaje observado en la DLQ |
| P09 | Crear cola por API Rabbit Admin | Correcto; apareció en Management |
| P10 | Crear cola con nombre vacío | Rechazado con HTTP 400 |
| P11 | Eliminar cola por API | Correcto; desapareció de Management |
| P12 | Crear exchange, cola y binding de prueba | Correcto |
| P13 | Publicar con routing key de prueba | Mensaje apareció en la cola de prueba |
| P14 | Limpiar recursos de prueba | El usuario confirmó el funcionamiento del procedimiento completo |
| P15 | Arrancar Catalog y Appointments | Logs mostraron `Started CatalogApplication` y `Started AppointmentsApplication`, H2 local y Tomcat en 8082/8081 |
| P16 | Salud de Appointments y Catalog | `UP` según la comprobación del usuario |
| P17 | Crear cita mediante POST | Cita creada correctamente |
| P18 | Verificar evento real en Notifications | El usuario confirmó recepción del evento generado por Appointments |

**Pendiente para cierre de EP3:** pruebas automatizadas completas, confirmación de publicación de cambio de estado `appointment.status.changed`, revisión final del control de acceso BFF/MSAL con un tenant propio, despliegue cloud y evidencias gráficas.

## 4. Topología RabbitMQ comprobada

| Tipo | Nombre |
|---|---|
| Direct exchange principal | `vidasalud.appointments.exchange` |
| Cola de notificaciones | `vidasalud.notifications.queue` |
| Routing key de cita creada | `appointment.created` |
| Cola de cambios de estado | `vidasalud.status.queue` |
| Routing key de estado | `appointment.status.changed` |
| Dead-letter exchange | `vidasalud.appointments.dlx` |
| Dead-letter queue | `vidasalud.notifications.dlq` |
| Routing key de mensajes rechazados | `appointment.failed` |

**Explicación:** el productor publica en el exchange principal con una routing key; el binding selecciona la cola correspondiente. El consumidor confirma con `basicAck` tras procesar correctamente. Si se produce un error, registra el fallo y usa `basicNack(..., requeue=false)`; la configuración de dead-letter lleva el mensaje rechazado hacia la DLQ. El consumidor de DLQ no se inicia automáticamente para permitir inspección.

## 5. Prueba P07 — evento válido

1. Abrir `http://localhost:15672` y acceder a **Exchanges → vidasalud.appointments.exchange**.
2. En **Publish message**, introducir la routing key `appointment.created`.
3. Publicar el siguiente JSON (con `content_type: application/json`, si está disponible):

```json
{
  "eventId": "test-001",
  "eventType": "appointment.created",
  "appointment": { "id": 1001 }
}
```

4. Inspeccionar los logs:

```powershell
docker compose -f compose.local.yml logs --tail=80 notifications
```

**Resultado confirmado:** apareció `Notificación generada evento=test-001 tipo=appointment.created cita=1001` o registro equivalente. El consumidor ejecuta ACK manual al terminar sin excepción.

## 6. Prueba P08 — fallo controlado y DLQ

En el mismo exchange, publicar con routing key `appointment.created`:

```json
{
  "eventId": "test-error-001",
  "eventType": "appointment.created",
  "appointment": { "id": 1002 },
  "simulateFailure": true
}
```

Consultar logs del consumidor y después **Queues and Streams → vidasalud.notifications.dlq**.

**Resultado confirmado:** el mensaje fallido apareció en la DLQ. `simulateFailure` provoca una excepción intencional en `NotificationService`, que el listener registra antes de enviar NACK sin reencolar. Esto permite demostrar una ruta de error sin interferir con los datos de citas.

## 7. Pruebas P09–P14 — administrador REST

**Importante:** utilizar los valores **actuales** de la última versión, no los antiguos `replace`. El encabezado `X-Admin-Key` es independiente de la contraseña del broker. Ejecutar los ejemplos en PowerShell desde `infra/apps`.

```powershell
$headers = @{ "X-Admin-Key" = "vidasalud-admin-local-2026-key" }
```

### Crear una cola (HTTP 201)

```powershell
$body = @{ name = "vidasalud.test.queue"; durable = $true } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/queues" -Method POST -Headers $headers -ContentType "application/json" -Body $body
```

Se confirmó la presencia de `vidasalud.test.queue` en Management.

### Rechazar nombre vacío (HTTP 400)

```powershell
$body = @{ name = ""; durable = $true } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/queues" -Method POST -Headers $headers -ContentType "application/json" -Body $body
```

**Resultado confirmado:** HTTP 400. En Windows PowerShell una respuesta no exitosa puede aparecer como excepción roja; en esta prueba es el resultado esperado.

### Eliminar cola (HTTP 204)

```powershell
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/queues/vidasalud.test.queue" -Method DELETE -Headers $headers
```

Se confirmó que desapareció del panel.

### Crear exchange, cola y binding

```powershell
$exchangeBody = @{ name = "vidasalud.test.exchange"; type = "direct"; durable = $true } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/exchanges" -Method POST -Headers $headers -ContentType "application/json" -Body $exchangeBody

$queueBody = @{ name = "vidasalud.test.queue"; durable = $true } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/queues" -Method POST -Headers $headers -ContentType "application/json" -Body $queueBody

$bindingBody = @{ exchange = "vidasalud.test.exchange"; queue = "vidasalud.test.queue"; routingKey = "test.created" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/bindings" -Method POST -Headers $headers -ContentType "application/json" -Body $bindingBody
```

Publicar desde `vidasalud.test.exchange` con routing key `test.created` y este payload:

```json
{"eventId":"test-binding-001","message":"Prueba de binding VidaSalud"}
```

**Resultado confirmado:** el mensaje apareció como listo (*Ready*) en `vidasalud.test.queue`, demostrando el enrutamiento.

### Eliminar binding y limpiar recursos

```powershell
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/bindings" -Method DELETE -Headers $headers -ContentType "application/json" -Body $bindingBody
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/queues/vidasalud.test.queue" -Method DELETE -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8084/api/rabbit-admin/exchanges/vidasalud.test.exchange" -Method DELETE -Headers $headers
```

No borrar las tres colas ni los exchanges principales de VidaSalud.

## 8. Pruebas P15–P18 — integración Appointments con RabbitMQ

```powershell
docker compose -f compose.local.yml up -d --build rabbitmq notifications catalog appointments
docker compose -f compose.local.yml ps
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8082/actuator/health
```

**Resultado confirmado:** los dos servicios respondieron como activos. Los logs mostraron H2 en memoria bajo el perfil `local`.

Crear una cita directamente contra Appointments (prueba de infraestructura local, sin pasar por el BFF):

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

La fecha anterior corresponde al ejemplo usado durante las pruebas de octubre de 2026: **en nuevas ejecuciones, cambiar `scheduledAt` por una fecha y hora futura**, ya que el DTO valida `@FutureOrPresent`. El `serviceId` debe existir en el catálogo local.

**Resultado confirmado por el usuario:** la cita se creó y Notifications registró el evento. Se comprobó el recorrido:

`POST /api/appointments → persistencia H2 → publicación appointment.created → exchange → cola → Notifications → ACK`.

## 9. Evidencias recomendadas para la entrega

Capturar: (1) contenedores `Up`, (2) colas/exchanges, (3) evento válido en logs, (4) evento fallido visible en DLQ, (5) creación/validación/eliminación con respuestas HTTP, (6) binding y mensaje *Ready*, (7) creación de cita y log correlacionado de notificación. Evitar incluir contraseñas, claves o JWT en capturas.

## 10. Limitaciones y próximos pasos

- No se acreditó autenticación completa ni navegación al dashboard por Entra ID; las credenciales/IDs del compañero siguen pendientes de reemplazo al configurar las cuentas cloud propias.
- El procesamiento de notificaciones es una **simulación registrada en logs**, no un correo realmente enviado.
- La recepción de un evento tras crear una cita se comprobó; eso no demuestra por sí solo garantías transaccionales fuertes ante caída del broker. La implementación básica no usa patrón outbox.
- Las advertencias de seguridad de npm requieren revisión antes de utilizar el sistema fuera de las pruebas académicas.
- La sesión confirmó ejecuciones locales; todavía no se han comprobado todas las pruebas automatizadas ni un despliegue completo en AWS Academy/Azure for Students.
