# VidaSalud — Evaluación Parcial N.º 3: RabbitMQ & Cloud Native

## 1. Integración de la Arquitectura RabbitMQ

Se implementa la **Topología Oficial de 6 Colas (3 flujos principales + 3 DLQ) y 3 Exchanges** especificada en el caso semestral VidaSalud y la pauta de evaluación Duoc UC:

### Topología de Mensajería:
| Cola Principal | Propósito | DLQ Asociada | Binding Direct (`cmd.direct`) | Binding Topic (`cmd.topic`) |
|---|---|---|---|---|
| **`q.cmd.email`** | Email/push al paciente (cita solicitada, confirmada, llamado a box, cierre, cancelación) | `q.cmd.email.dlq` | `email.send` | `email.*` |
| **`q.cmd.admission`** | Ticket de admisión y asignación de box clínico | `q.cmd.admission.dlq` | `admission.ticket` | `admission.#` |
| **`q.cmd.record`** | Generación de comprobante / resumen clínico en PDF | `q.cmd.record.dlq` | `record.gen` | `record.*` |

### Exchanges declarados:
* **`cmd.direct`** (DirectExchange, durable): Enrutamiento directo y eficiente por caso de uso.
* **`cmd.topic`** (TopicExchange, durable): Enrutamiento dinámico por sub-patrones de eventos.
* **`cmd.dead.dlx`** (DirectExchange, durable): Exchange para recepción de Dead Letter Queues (DLQ).

---

## 2. Eventos Asíncronos al Cambiar de Estado (`updateStatus`)

Al invocar `POST /api/appointments` y `PUT /api/appointments/{id}/status`, `ms-vidasalud-appointments` publica un **Envelope Común Cloud-Native** (`type`, `eventId`, `timestamp`, `traceId`, `correlationId`, `payload`) garantizando la ejecución después del commit transaccional:

1. **`SOLICITADA` (Creación):**
   * Publica a `cmd.direct` con rkey `email.send` y `cmd.topic` con `email.solicitada`.
   * `EmailConsumer` procesa el envío de confirmación de solicitud al paciente.
2. **`CONFIRMADA`:**
   * Se reserva el cupo en el Box mediante `ms-vidasalud-catalog`.
   * Publica a `q.cmd.email` (`email.send`): Notificación de cita confirmada con número de box.
   * Publica a `q.cmd.admission` (`admission.ticket`): Generación de ticket de admisión para el box clínico.
3. **`EN_ESPERA`:**
   * Publica a `q.cmd.admission` (`admission.ticket`): Registro de llegada del paciente a la sala de espera.
4. **`EN_ATENCION`:**
   * Publica a `q.cmd.admission` (`admission.ticket`): Llamado formal a box clínico.
   * Publica a `q.cmd.email` (`email.send`): Push/email notificando al paciente presentarse al box.
5. **`CERRADA`:**
   * Publica a `q.cmd.record` (`record.gen`): Dispara la generación del resumen clínico en PDF.
   * Publica a `q.cmd.email` (`email.send`): Notificación de alta y comprobante al paciente.
6. **`CANCELADA`:**
   * Publica a `q.cmd.email` (`email.send`): Notificación de cancelación de la atención.

---

## 3. Gestión Manual de DLQ y Confirmación de Mensajes (ACK/NACK)

* **ACK Explícito:** Todos los consumidores (`EmailConsumer`, `AdmissionConsumer`, `RecordConsumer`) operan con `ackMode = "MANUAL"` y ejecutan `channel.basicAck(deliveryTag, false)` únicamente tras completar con éxito el procesamiento.
* **Gestión Manual de DLQ (`DlqManualManagementService`):**
  * Ante errores no recuperables o simulados (`simulateFailure: true`), el consumidor captura la excepción explícitamente.
  * Enriquece el mensaje con encabezados de auditoría: `x-original-queue`, `x-death-reason`, `x-exception-class`, `x-failed-at`, y `x-management-mode: MANUAL_DISPATCH`.
  * Publica manualmente el mensaje hacia `cmd.dead.dlx` con su routing key correspondiente (`email.dlq`, `admission.dlq`, `record.dlq`).
  * Emite el ACK en la cola original para retirarlo formalmente, impidiendo bloqueos de cola o reintentos infinitos no controlados.

---

## 4. Microservicio Administrador (`ms-vidasalud-rabbit-admin`)

Expone una API REST formal para administrar dinámicamente recursos de RabbitMQ con validación de DTOs y manejo de errores:

| Método | Endpoint | Descripción | Body de ejemplo |
|---|---|---|---|
| **POST** | `/api/queues` | Crea una cola durable | `{"name":"q.demo.test","durable":true}` |
| **GET** | `/api/queues/{name}` | Consulta métricas y estado de la cola | — |
| **DELETE** | `/api/queues/{name}` | Elimina una cola | — |
| **POST** | `/api/exchanges` | Crea un exchange (`direct`, `topic`, `fanout`, `headers`) | `{"name":"demo.topic","type":"topic"}` |
| **DELETE** | `/api/exchanges/{name}` | Elimina un exchange | — |
| **POST** | `/api/bindings` | Crea un enlace cola-exchange | `{"exchange":"demo.topic","queue":"q.demo.test","routingKey":"demo.*"}` |
| **DELETE** | `/api/bindings` | Elimina un enlace | Igual que POST |

* *Validación de Entrada:* DTOs con `@NotBlank` y `@Pattern` impiden nombres vacíos o formatos inválidos, respondiendo con HTTP 400 y detalle de campos.
* *Compatibilidad:* Admite también las rutas `/api/rabbit-admin/*` con cabecera opcional/requerida `X-Admin-Key`.

---

## 5. Despliegue en la Nube (AWS EC2 / Learner Lab)

La infraestructura queda modularizada en `infra/`:
* `infra/apps/compose.yml`: Microservicios backend (BFF, Appointments, Catalog, Notifications, Rabbit-Admin) para `ec2-apps`.
* `infra/mq/compose.yml`: Clúster RabbitMQ de 2 nodos con Management UI para `ec2-mq`.
* `infra/kafka/compose.yml`: Zookeeper (3 nodos) + Kafka (3 brokers) + Kafka UI para `ec2-kafka`.
* `infra/cloud/GUIA_DESPLIEGUE_AWS_LAB.md`: Guía detallada para crear las instancias EC2 y Security Groups en el laboratorio.
* `infra/cloud/user-data-docker.sh`: Script User Data para aprovisionar automáticamente Docker y Docker Compose en las instancias EC2.

---

## 6. Pruebas Locales Rápidas

Desde `infra/apps`:
```powershell
docker compose -f compose.local.yml up -d --build
```
1. Ingresar a `http://localhost:15672` (Usuario: `vidasalud`, Contraseña: `vidasalud-local`).
2. Comprobar que existen las 6 colas (`q.cmd.email`, `q.cmd.admission`, `q.cmd.record`, y sus 3 DLQs) y los exchanges (`cmd.direct`, `cmd.topic`, `cmd.dead.dlx`).
3. Crear o actualizar una cita mediante la interfaz Angular (`http://localhost:4200`) o via HTTP POST al BFF (`http://localhost:8080/api/appointments`).
4. Observar en los logs cómo se procesan las notificaciones y tickets correspondientes:
```powershell
docker compose -f compose.local.yml logs -f notifications
```
