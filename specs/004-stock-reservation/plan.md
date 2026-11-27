# Plan 004 — Reserva de stock desde `order.placed` con resultado y compensación

Diseño técnico de `specs/004-stock-reservation/spec.md` para `catalog-api`. Respeta
`AGENTS.md` y las skills que menciona: `/spring-boot-project-creator` en su **opción
Layered** (sin regenerar el proyecto con Spring Initializr),
`/spring-boot-layered-template` para ubicar la mensajería y las clases nuevas, y
`/clean-code-guard` como guardia de calidad. Cubre los RF-1 … RF-28 y los NFR-1 … NFR-7
de la spec. Agrega una única migración Flyway (`V3`), no agrega dependencias —el
starter `spring-boot-starter-amqp` ya está en `pom.xml`— y no declara exchanges, colas,
bindings, TTL ni DLQ.

## 1. Objetivo técnico

Consumir `order.placed` desde la cola `stock.reserve`, reservar de forma idempotente por
`orderId`, persistir el resultado y publicar `stock.reserved` o `stock.rejected` en el
exchange `stock`, confirmando la recepción (ACK) solo después de que la reserva o su
registro hayan quedado confirmados. La reserva es **todo o nada**: no deja reservas
parciales, no permite sobreventa y nunca deja stock negativo. El stock nulo se trata
como ilimitado y no se descuenta. Solo son reservables productos publicados y no
eliminados lógicamente. Los ítems repetidos del mismo producto se suman antes de
verificar disponibilidad. **(RF-1 … RF-28, NFR-1 … NFR-7)**

## 2. Alcance y principios

- Capacidad dueña: **catálogo** (`com.friendlyeshop.catalog`). Todo se resuelve con la
  base `catalog`; no se leen ni escriben tablas de otros dominios. **(NFR-1, RF-24)**
- **Layered Architecture**: se usan los paquetes existentes y se agrega la extensión
  `messaging/rabbit/`, prevista por `/spring-boot-layered-template` para consumidores,
  publicadores y payloads del broker. No se crean paquetes DDD.
- **Sin topología**: `fes-infra` declara exchanges, colas, bindings, retry y DLQ. El
  servicio solo consume la cola existente `stock.reserve` y publica en el exchange
  existente `stock`. No crea beans `Queue`, `Exchange`, `Binding` ni políticas de TTL.
  **(NFR-2, NFR-3)**
- **Idempotencia anclada al `orderId`**: una fila por pedido en `stock_reservations` con
  clave primaria `order_id`. El `eventId` se guarda para trazabilidad y se reutiliza al
  republicar el resultado almacenado; no decide una nueva reserva. **(RF-3, RF-4)**
- **Todo o nada**: los productos se bloquean y validan antes de aplicar cualquier
  descuento; si un producto falla, no se descuenta ninguno. La transacción de la reserva
  revierte cualquier cambio si aparece un error posterior. **(RF-9, RF-10, RF-11)**
- **ACK manual**: la recepción se confirma solo después de persistir el resultado y
  publicarlo; una falla transitoria deja el mensaje sin confirmar y `fes-infra` decide
  retry o DLQ. **(RF-16, RF-17)**
- **Contrato HTTP intacto**: no se agregan endpoints ni se modifican `/catalog`,
  publicación, imágenes, Actuator, health checks, métricas o variables de entorno.
  **(RF-20 … RF-23, NFR-4, NFR-5, NFR-7)**

## 3. Estado actual relevante (as-is)

- `pom.xml` ya incluye `spring-boot-starter-amqp`; no hay que agregar dependencias.
- `application.yaml` ya configura `spring.rabbitmq.*` con las variables `RABBITMQ_*`,
  pero no hay consumidores, publicadores, convertidores ni listeners en el código.
- `Product` (`model/Product.java`) tiene `id`, `ownerAccountId`, `name`, `description`,
  `price`, `currency`, `stock` (`Integer`, admite `null`), `stage`, `deletedAt`,
  `version`, `createdAt` y `updatedAt`. No tiene métodos de reserva de stock.
- `ProductRepository` ya tiene `findPublished`, `findPublishedByName` y `findPublicById`,
  pero no un método con bloqueo pesimista para reservar.
- `V1__create_products.sql` define `products` con `stock INTEGER NULL CHECK
  (stock >= 0)`; `V2__add_product_description.sql` agrega `description`. No existe
  ninguna tabla de reservas ni de eventos procesados.
- No existe el paquete `messaging/`; `config/` contiene configuración HTTP, CORS,
  almacenamiento y rutas, y `exception/` los errores HTTP.
- `ProductStage` solo tiene `DRAFT` y `PUBLISHED`; la visibilidad pública exige además
  `deletedAt IS NULL` y dueño no nulo. Para reservar solo se exige publicado y no
  eliminado, sin exigir dueño. **(RF-28)**

## 4. Decisiones técnicas (motivo y RF)

| # | Decisión | Motivo | RF |
|---|---|---|---|
| D1 | Nueva tabla `stock_reservations` con `order_id` como clave primaria y una fila por pedido. | La idempotencia se ancla al pedido; la clave primaria impide dos resultados para el mismo `orderId`. | RF-3, RF-4 |
| D2 | La tabla guarda `event_id`, `status`, `reason`, `reserved_at` y `rejected_at`; el resultado se reconstruye desde ella. | Permite republicar exactamente el resultado almacenado, incluso con otro `eventId`. | RF-3, RF-4, RF-6, RF-7 |
| D3 | Estado `RESERVED` / `REJECTED` y motivos `OUT_OF_STOCK`, `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_AVAILABLE`, `INVALID_QUANTITY`, `INVALID_ORDER` como enums persistidos en mayúsculas. | Fija el vocabulario compartido con `fes-order-api` y evita strings sueltos. | RF-19 |
| D4 | La reserva se valida y ejecuta en una única transacción; no se descuenta stock hasta validar todos los productos. | Garantiza todo o nada y evita reservas parciales que luego haya que liberar. | RF-9, RF-10, RF-11 |
| D5 | Los productos se bloquean con `PESSIMISTIC_WRITE` en orden ascendente de `productId`. | Serializa pedidos concurrentes por producto, evita sobreventa y reduce deadlocks. | RF-12, RF-13 |
| D6 | `Product` incorpora `isReservable()`, `canCover(quantity)` y `reserve(quantity)`. | La entidad dueña del stock concentra la regla de disponibilidad y descuento. | RF-5, RF-7, RF-12, RF-13, RF-15 |
| D7 | Stock nulo: `canCover` siempre verdadero y `reserve` no modifica el valor. | Coincide con la visibilidad actual de stock ilimitado. | RF-15 |
| D8 | El `OrderPlacedMessageReader` extrae el `orderId` del JSON y mapea ítems de forma tolerante; si no hay `orderId` utilizable devuelve vacío. | Permite distinguir un evento sin `orderId` (NACK/DLQ) de un pedido con ítems inválidos (rechazo y ACK). | RF-18, RF-26 |
| D9 | Los ítems repetidos se agrupan por `productId` en un `TreeMap` y se suman con control de desbordamiento. | Aplica la cantidad total correcta y bloquea productos en orden estable. | RF-14, RF-27 |
| D10 | Un pedido con `orderId` válido e ítems inválidos se persiste como `REJECTED` con `INVALID_ORDER` o `INVALID_QUANTITY` según corresponda. | Deja un resultado idempotente y consultable para el pedido, sin tocar stock. | RF-14, RF-26 |
| D11 | El `reason` se decide con precedencia fija: estructura del pedido, cantidad, existencia, disponibilidad y stock. | Hace determinista el motivo cuando hay varios problemas y usa el más específico. | RF-19 |
| D12 | Un evento sin `orderId` utilizable no se persiste ni se publica; se rechaza con NACK sin reencolar y, agotados los 5 intentos, se publica en `stock.reserve.dlq`. | No hay pedido al que asociar resultado; el retry diferido y la DLQ pertenecen a la topología de `fes-infra`. | RF-18 |
| D13 | El listener usa ACK/NACK manuales; publica el resultado después del commit y antes del ACK. | Evita confirmar trabajo no persistido y permite republicar tras una falla de publicación. | RF-16, RF-17 |
| D14 | `StockResultPublisher` publica con `RabbitTemplate` y publisher confirms; una confirmación fallida impide el ACK. | Asegura que el resultado llegue al exchange antes de confirmar la recepción. | RF-6, RF-7, RF-16, RF-17 |
| D15 | No se declaran colas, exchanges, bindings, TTL ni DLQ; solo se usan sus nombres. | La topología pertenece a `fes-infra`. | NFR-3 |
| D16 | No se agregan endpoints HTTP ni dependencias; el contrato existente no cambia. | Mantiene `/catalog`, Actuator y el resto del servicio intactos. | RF-20 … RF-23, NFR-2, NFR-4, NFR-5 |
| D17 | El listener cuenta los ciclos con `x-death`: menos de 5 intentos hace NACK sin reencolar; al quinto publica en `stock.reserve.dlq` y hace ACK. | Cierra el contrato de reintentos máximo 5 declarado por `fes-infra` y evita reintentos infinitos. | RF-16, RF-17, RF-18 |

## 5. Estructura objetivo (Layered)

```text
src/main/java/com/friendlyeshop/catalog
├── controller/                              # sin cambios                     RF-20,21
├── service/
│   ├── ProductService                       # sin cambios                     RF-21
│   ├── PublicationService                   # sin cambios                     RF-21
│   └── StockReservationService              # NUEVO: reserva y resultado       RF-2..17,26..28
├── repository/
│   ├── ProductRepository                    # + findByIdForUpdate              RF-12,13,28
│   └── StockReservationRepository           # NUEVO                            RF-3,4,5,16
├── model/
│   ├── Product                              # + isReservable/canCover/reserve  RF-5,7,15,28
│   ├── StockReservation                     # NUEVO @Entity                    RF-3,4,5,7
│   └── enums/
│       ├── ReservationStatus                # NUEVO RESERVED/REJECTED          RF-3,19
│       └── RejectionReason                  # NUEVO vocabulario de reason      RF-19
├── messaging/rabbit/                        # NUEVO                            RF-1,2,6,18
│   ├── listener/
│   │   └── OrderPlacedListener              # consumidor con ACK/NACK manual    RF-1,3,16,17,18
│   ├── reader/
│   │   └── OrderPlacedMessageReader         # parseo tolerante del JSON         RF-18,26
│   ├── model/
│   │   ├── OrderPlacedMessage               # eventId, orderId, items           RF-2
│   │   ├── OrderPlacedItem                  # productId, quantity               RF-2,14,27
│   │   ├── StockReservedMessage             # eventId, orderId, reservedAt      RF-6
│   │   └── StockRejectedMessage             # eventId, orderId, reason, rejectedAt RF-7,19
│   ├── publisher/
│   │   ├── StockResultPublisher             # publica reserved/rejected         RF-6,7
│   │   └── StockReserveDeadLetterPublisher  # publica en stock.reserve.dlq      RF-16,17,18
│   └── RetryAttempts                        # cuenta x-death                    RF-16,17,18
├── config/
│   └── RabbitMessagingConfig                # convertidor JSON y confirms       RF-6,16
├── exception/                               # sin cambios HTTP                  RF-20
└── util/                                    # sin cambios                       RF-21
src/main/resources/
├── application.yaml                         # + ACK manual y publisher confirms RF-16,17
└── db/migration/
    ├── V1__create_products.sql              # sin cambios
    ├── V2__add_product_description.sql      # sin cambios
    └── V3__create_stock_reservations.sql    # NUEVA                             RF-3,4,5
```

Decisiones de ubicación según `/spring-boot-layered-template`:

- `messaging/rabbit/` agrupa al consumidor, al publicador y a los payloads del broker;
  se subdivide por responsabilidad en `listener/`, `reader/`, `model/` y `publisher/`.
  `RetryAttempts` queda en la raíz porque es un helper transversal a los listeners. Son
  detalles de protocolo y no eventos de dominio; no se mezclan con `model/`.
- `StockReservationService` vive en `service/` porque coordina el caso de uso, los
  bloqueos, las validaciones y la persistencia del resultado.
- `StockReservation` es una entidad de persistencia en `model/`; los enums de estado y
  motivo viven en `model/enums/`.
- `RabbitMessagingConfig` vive en `config/` porque configura infraestructura del broker.
- No se crean `client/`, `http/`, `auth/`, `security/`, `mapper/`, `validation/` ni
  `util/` nuevos: no hay integración de negocio externa ni conversiones repetidas.

## 6. Migración Flyway — RF-3, RF-4, RF-5, RF-7, RF-19

Archivo nuevo `src/main/resources/db/migration/V3__create_stock_reservations.sql`:

```sql
CREATE TABLE stock_reservations (
    order_id UUID PRIMARY KEY,
    event_id UUID NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'REJECTED')),
    reason VARCHAR(30) NULL CHECK (reason IN (
        'OUT_OF_STOCK',
        'PRODUCT_NOT_FOUND',
        'PRODUCT_NOT_AVAILABLE',
        'INVALID_QUANTITY',
        'INVALID_ORDER'
    )),
    reserved_at TIMESTAMPTZ NULL,
    rejected_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT stock_reservations_result_check CHECK (
        (status = 'RESERVED' AND reason IS NULL
            AND reserved_at IS NOT NULL AND rejected_at IS NULL)
        OR
        (status = 'REJECTED' AND reason IS NOT NULL
            AND rejected_at IS NOT NULL AND reserved_at IS NULL)
    )
);

CREATE INDEX stock_reservations_event_id_idx
    ON stock_reservations (event_id);
```

- No se edita `V1` ni `V2`. **(AGENTS.md: no editar migraciones aplicadas)**
- Hibernate corre con `ddl-auto: validate`; la entidad `StockReservation` debe coincidir
  exactamente con la tabla.
- La clave primaria `order_id` es la garantía física de idempotencia por pedido.
- No se agrega ninguna tabla de ítems reservados: el resultado por pedido y el stock de
  `products` son suficientes para este corte; el detalle de ítems pertenece a
  `fes-order-api`. **(NFR-1)**

## 7. Modelo de dominio y persistencia

### 7.1 `Product` — RF-5, RF-7, RF-12, RF-13, RF-15, RF-28

Agregar tres métodos de negocio, sin setters públicos:

```java
public boolean isReservable() {
    return stage == ProductStage.PUBLISHED && deletedAt == null;
}

public boolean canCover(int quantity) {
    return stock == null || stock >= quantity;
}

public void reserve(int quantity) {
    if (stock == null) {
        return;
    }
    if (stock < quantity) {
        throw new IllegalStateException("Insufficient stock");
    }
    stock = stock - quantity;
}
```

- `isReservable` no exige dueño: la spec solo pide publicado y no eliminado. **(RF-28)**
- `canCover` y `reserve` tratan `stock == null` como ilimitado. **(RF-15)**
- El `CHECK (stock >= 0)` de `V1` sigue siendo la última barrera contra negativos.
  **(RF-13)**
- No se agrega `release`: al validar todos los productos antes de descontar, un fallo no
  produce descuentos parciales; la transacción revierte cualquier error inesperado.
  **(RF-9, RF-11)**

### 7.2 `StockReservation` — RF-3, RF-4, RF-5, RF-7

Entidad en `model/`:

```java
@Entity
@Table(name = "stock_reservations")
@Getter
public class StockReservation {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 30)
    private RejectionReason reason;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockReservation() {
    }

    public static StockReservation reserved(UUID eventId, UUID orderId, Instant now) { ... }

    public static StockReservation rejected(
            UUID eventId, UUID orderId, RejectionReason reason, Instant now) { ... }

    @PrePersist
    void onCreate() { ... }

    @PreUpdate
    void onUpdate() { ... }
}
```

- `reserved` deja `reason` nulo y `reservedAt = now`; `rejected` deja `reservedAt` nulo y
  `rejectedAt = now`.
- No hay setters: el resultado es inmutable una vez persistido. Para republicar se leen
  sus getters.
- `ReservationStatus` = `RESERVED`, `REJECTED`. `RejectionReason` = `OUT_OF_STOCK`,
  `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_AVAILABLE`, `INVALID_QUANTITY`, `INVALID_ORDER`.
  **(RF-19)**

### 7.3 Repositorios

`StockReservationRepository`:

```java
public interface StockReservationRepository
        extends JpaRepository<StockReservation, UUID> {
}
```

`ProductRepository` agrega:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Product p where p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") UUID id);
```

- El bloqueo pesimista se mantiene hasta el commit de la transacción de reserva.
  **(RF-12)**
- Se consulta producto por producto en orden ascendente de `productId` (ver D5).
- No se modifica ninguna consulta existente. **(RF-21)**

## 8. Mensajería RabbitMQ

### 8.1 Entrada `order.placed` — RF-1, RF-2, RF-18, RF-26, RF-27

`OrderPlacedListener` declara:

```java
@RabbitListener(queues = "stock.reserve")
public void onOrderPlaced(Message message, Channel channel) throws IOException { ... }
```

- Consume la cola existente `stock.reserve`; no la declara. **(NFR-3)**
- El cuerpo se lee con `OrderPlacedMessageReader.read(message.getBody())`.
- El reader tolera campos extra (`eventType`, `occurredAt`, `tenantId`, `unitPrice`,
  `currency`, `total`) y devuelve un `OrderPlacedMessage` con:
  - `eventId`: UUID o `null` si falta o no es válido.
  - `orderId`: UUID obligatorio; si falta, está en blanco o no es UUID, el reader
    devuelve `Optional.empty()`.
  - `items`: lista (posiblemente vacía) de `OrderPlacedItem(productId, quantity)`, con
    `productId` nulo si el ítem no lo trae y `quantity` nula si no es un entero válido.
- `OrderPlacedItem` usa `Integer` para poder representar cantidad ausente o no numérica
  como inválida, en vez de romper el parseo de todo el evento. **(RF-14, RF-26)**

### 8.2 Salida `stock.reserved` / `stock.rejected` — RF-6, RF-7, RF-19

`StockResultPublisher` usa `RabbitTemplate`:

```java
rabbitTemplate.convertAndSend("stock", "stock.reserved", StockReservedMessage.from(reservation));
rabbitTemplate.convertAndSend("stock", "stock.rejected", StockRejectedMessage.from(reservation));
```

Payloads:

```json
{
  "eventId": "uuid-o-null",
  "orderId": "uuid",
  "reservedAt": "2026-11-25T12:00:00Z"
}
```

```json
{
  "eventId": "uuid-o-null",
  "orderId": "uuid",
  "reason": "OUT_OF_STOCK",
  "rejectedAt": "2026-11-25T12:00:00Z"
}
```

- `eventId` es el **registrado** en la reserva, no necesariamente el de la última
  reentrega. **(RF-3, RF-4)**
- `reason` es uno de los cinco valores del vocabulario. **(RF-19)**
- No se declara el exchange `stock`; se publica por nombre en el exchange existente.
  **(NFR-3)**
- Publisher confirms: el publisher envía con `CorrelationData` y espera la confirmación
  del broker antes de dejar que el listener haga ACK. **(RF-16)**

### 8.3 Configuración — RF-16, RF-17

`RabbitMessagingConfig` define un `Jackson2JsonMessageConverter` con el `ObjectMapper`
de Spring para serializar los payloads de salida. En `application.yaml` se agrega:

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        default-requeue-rejected: false
```

- No se agregan variables de entorno ni secretos: se reutilizan `RABBITMQ_*`.
  **(NFR-5)**
- No se crean `Queue`, `Exchange`, `Binding`, `Declarables`, TTL ni DLX. **(NFR-3)**

## 9. `StockReservationService` — algoritmo

### 9.1 API

```java
@Service
public class StockReservationService {

    @Transactional
    public StockReservation reserve(OrderPlacedMessage message) { ... }

    @Transactional(readOnly = true)
    public Optional<StockReservation> find(UUID orderId) { ... }
}
```

### 9.2 Flujo de `reserve`

1. **Idempotencia**: `stockReservationRepository.findById(message.orderId())`. Si existe,
   devolver el resultado almacenado sin tocar stock ni bloquear productos. **(RF-3,
   RF-4)**
2. **Estructura**: si `items` es nulo o vacío, o si ningún ítem tiene `productId`, crear
   y guardar `StockReservation.rejected(eventId, orderId, INVALID_ORDER, now)` y
   devolver. **(RF-26)**
3. **Cantidad**: si algún ítem tiene `quantity` nula o menor o igual a cero, guardar
   `REJECTED` con `INVALID_QUANTITY` y devolver sin modificar stock. **(RF-14)**
4. **Agregación**: agrupar por `productId` en un `TreeMap<UUID, Integer>`; sumar con
   control de desbordamiento; si la suma excede `Integer.MAX_VALUE`, guardar `REJECTED`
   con `INVALID_QUANTITY`. **(RF-27)**
5. **Bloqueo**: recorrer los `productId` ordenados y obtener cada producto con
   `findByIdForUpdate`. Guardar el mapa `productId → Product`.
6. **Existencia**: si falta algún producto, guardar `REJECTED` con `PRODUCT_NOT_FOUND` y
   devolver. **(RF-8)**
7. **Disponibilidad**: si algún producto no cumple `isReservable()`, guardar `REJECTED`
   con `PRODUCT_NOT_AVAILABLE` y devolver. **(RF-28)**
8. **Stock**: si algún producto no cumple `canCover(quantity)`, guardar `REJECTED` con
   `OUT_OF_STOCK` y devolver. **(RF-7)**
9. **Reserva**: recién ahora recorrer los productos y llamar `reserve(quantity)`. El
   stock nulo no cambia. **(RF-5, RF-15)**
10. **Registro**: guardar `StockReservation.reserved(eventId, orderId, now)` y devolverlo.
    **(RF-5, RF-6)**

Los pasos 6, 7 y 8 se evalúan como barridos completos —existe alguno, alguno no
disponible, alguno sin stock— para que el motivo no dependa del orden de los ítems. La
precedencia global es estructura → cantidad → existencia → disponibilidad → stock.
**(RF-19)**

### 9.3 Todo o nada y compensación

- Ningún `reserve(quantity)` se ejecuta hasta que todos los productos pasaron los pasos
  6, 7 y 8. Si hay rechazo, no hay descuento parcial que liberar. **(RF-9, RF-11)**
- Si un error inesperado ocurre después de algún descuento, la transacción revierte y el
  stock vuelve a su estado previo antes de cualquier publicación. **(RF-9, RF-10)**
- El resultado rechazado se guarda en la misma transacción, sin cambios de stock.
  **(RF-16)**

### 9.4 Concurrencia — RF-12, RF-13

- Dos pedidos por el mismo producto se serializan por el bloqueo pesimista de la fila.
  El segundo lee el stock ya descontado por el primero.
- `reserve` valida `stock >= quantity` con el lock tomado, por lo que el stock nunca
  baja de cero.
- Dos entregas concurrentes del **mismo** `orderId`: ambas pueden pasar el paso 1 antes
  de que exista la fila. La clave primaria `order_id` hace que una inserta y la otra
  falle con conflicto de unicidad. La que falla revierte sus descuentos y el listener
  relee el resultado almacenado para republicarlo. **(RF-3, RF-4, RF-13)**

## 10. `OrderPlacedListener` — ACK/NACK y errores

```java
long tag = message.getMessageProperties().getDeliveryTag();
Optional<OrderPlacedMessage> parsed = reader.read(message.getBody());

if (parsed.isEmpty()) {
    rejectOrDeadLetter(channel, tag, message);
    return;
}

try {
    StockReservation reservation = reservationService.reserve(parsed.get());
    publisher.publish(reservation);
    channel.basicAck(tag, false);
} catch (DataIntegrityViolationException conflict) {
    Optional<StockReservation> stored = reservationService.find(parsed.get().orderId());
    if (stored.isEmpty()) {
        rejectOrDeadLetter(channel, tag, message);
        return;
    }
    publisher.publish(stored.get());
    channel.basicAck(tag, false);
} catch (Exception failure) {
    rejectOrDeadLetter(channel, tag, message);
}

private void rejectOrDeadLetter(Channel channel, long tag, Message message) throws IOException {
    if (RetryAttempts.of(message) >= 5) {
        deadLetterPublisher.publish(message);
        channel.basicAck(tag, false);
    } else {
        channel.basicNack(tag, false, false);
    }
}
```

`RetryAttempts.of(message)` cuenta las entradas de `x-death` de la cola
`stock.reserve.retry` y suma 1: así el primer fallo cuenta como intento 1 y el quinto
fallo publica en la DLQ en lugar de volver a la cola de reintento.

Matriz de decisión:

| Situación | Persistencia | Publicación | ACK/NACK |
|---|---|---|---|
| Sin `orderId` utilizable | Ninguna | Ninguna | NACK sin reencolar mientras el intento sea menor a 5; al quinto, `stock.reserve.dlq` + ACK |
| `orderId` repetido con resultado | Ninguna | Resultado almacenado | ACK |
| Ítems inválidos con `orderId` válido | `REJECTED` | `stock.rejected` | ACK |
| Reserva exitosa | Stock descontado + `RESERVED` | `stock.reserved` | ACK |
| Rechazo por stock/producto | Sin cambio de stock + `REJECTED` | `stock.rejected` | ACK |
| Falla de BD o de publicación | Según el caso | Ninguna o incierta | NACK sin reencolar mientras el intento sea menor a 5; al quinto, `stock.reserve.dlq` + ACK |
| Conflicto de `orderId` concurrente | La transacción perdedora revierte | Resultado almacenado | ACK |

- El listener no publica nada antes de que `reserve` haya confirmado el resultado.
  **(RF-16)**
- Si la publicación falla después del commit, no hay ACK; la reentrega lee el resultado
  almacenado y lo republica sin volver a reservar. **(RF-3, RF-17)**
- NACK usa `requeue = false`; el retry diferido con TTL y la DLQ son responsabilidad de
  `fes-infra`, mientras que `catalog-api` implementa el conteo de intentos y el envío
  final a `stock.reserve.dlq`. **(NFR-3)**

## 11. Contratos de eventos

### 11.1 Entrada `order.placed` (consumida desde `stock.reserve`)

```json
{
  "eventId": "8f3c2b1a-0000-4000-8000-000000000000",
  "eventType": "order.placed",
  "occurredAt": "2026-11-25T12:00:00Z",
  "orderId": "1a2b3c4d-0000-4000-8000-000000000000",
  "tenantId": "uuid",
  "items": [
    { "productId": "uuid", "quantity": 2, "unitPrice": 10.00, "currency": "ARS" }
  ],
  "total": 20.00,
  "currency": "ARS"
}
```

- Solo se usan `eventId`, `orderId` e `items[].productId`, `items[].quantity`; el resto
  se ignora. **(RF-2)**
- `items` repetidos se suman por `productId`. **(RF-27)**

### 11.2 Salidas (publicadas en `stock`)

| Routing key | Payload | Cuándo |
|---|---|---|
| `stock.reserved` | `{eventId, orderId, reservedAt}` | Reserva confirmada |
| `stock.rejected` | `{eventId, orderId, reason, rejectedAt}` | Rechazo |

`reason` ∈ `OUT_OF_STOCK`, `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_AVAILABLE`,
`INVALID_QUANTITY`, `INVALID_ORDER`. **(RF-19)**

## 12. Conservación de contratos existentes — RF-20 … RF-25, NFR-4, NFR-5, NFR-7

- No se agregan controladores ni rutas; `/catalog`, publicación e imágenes no cambian.
  **(RF-20, RF-21, NFR-4)**
- Actuator, health checks y métricas no se tocan. **(RF-22)**
- Se conservan `DATABASE_*`, `RABBITMQ_*`, `S3_*`, `PUBLIC_API_BASE_URL`,
  `BROWSER_ORIGINS` y `SERVER_PORT`; no se agregan secretos. **(RF-23, NFR-5)**
- `PublicationService`, `ProductService`, `ProductResponseMapper` y los DTOs HTTP no se
  modifican. La reserva escribe únicamente `products.stock` y `stock_reservations`.
  **(RF-24, RF-25, NFR-1, NFR-7)**
- No se agregan dependencias ni se cambia `spring-boot-starter-amqp`. **(NFR-2)**

## 13. Pruebas

| Área | Qué verifica | RF |
|---|---|---|
| `ProductReservationTest` (unitario) | `isReservable` con publicado/borrador/eliminado; `canCover` con stock nulo, igual, mayor y menor; `reserve` descuenta y no toca el stock nulo | RF-5, RF-7, RF-13, RF-15, RF-28 |
| `StockReservationTest` (unitario) | Fábricas `reserved`/`rejected` dejan `reason`, `reservedAt`, `rejectedAt` y estados coherentes | RF-3, RF-5, RF-7, RF-19 |
| `OrderPlacedMessageReaderTest` (unitario) | Lee el contrato mínimo e ignora extras; `orderId` ausente/inválido devuelve vacío; `eventId` ausente queda nulo; ítems con `productId`/`quantity` inválidos se marcan, no rompen el parseo | RF-2, RF-14, RF-18, RF-26, RF-27 |
| `StockReservationServiceTest` (unitario) | Idempotencia por `orderId` (reservado y rechazado) sin llamar a productos; distintos `eventId` reutilizan el resultado; suma de repetidos; `INVALID_ORDER`, `INVALID_QUANTITY`, `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_AVAILABLE`, `OUT_OF_STOCK`; stock nulo ilimitado; éxito descuenta y guarda `RESERVED`; un rechazo no llama a `reserve` | RF-3 … RF-9, RF-14, RF-15, RF-19, RF-26 … RF-28 |
| `StockReservationServiceIntegrationTest` (Testcontainers PostgreSQL) | `V3` crea la tabla; reserva persistida; dos pedidos concurrentes por el mismo producto no sobreevenden; dos entregas concurrentes del mismo `orderId` dejan una fila y un solo descuento | RF-3, RF-5, RF-7, RF-12, RF-13 |
| `StockResultPublisherTest` (unitario) | Publica en `stock` con `stock.reserved`/`stock.rejected`, payload exacto y `eventId` almacenado; una confirmación fallida lanza excepción | RF-6, RF-7, RF-16, RF-19 |
| `OrderPlacedListenerTest` (unitario) | ACK tras reserva y publicación; ACK tras rechazo; NACK sin reencolar sin `orderId`; republica y ACK con resultado existente; NACK ante falla de publicación o BD; conflicto de unicidad republica el resultado almacenado | RF-1, RF-3, RF-16, RF-17, RF-18, RF-26 |
| `ProductRepositoryIntegrationTest` (existente, ampliado) | `findByIdForUpdate` bloquea y devuelve el producto; las consultas existentes siguen igual; `V3` no altera `products` | RF-12, RF-21, RF-24 |
| `PublicationControllerWebTest`, `ProductControllerWebTest`, `ActuatorConfigurationTest` (existentes) | `/catalog`, publicación, imágenes, Actuator y health siguen verdes | RF-20 … RF-23, NFR-4, NFR-7 |

- Unitarios con Mockito para servicios, listener y publisher; el reader con Jackson real.
- Integración con `@DataJpaTest` y PostgreSQL vía Testcontainers, aplicando `V1` + `V2`
  + `V3`.
- La prueba de concurrencia usa dos transacciones/threads con `CountDownLatch` y verifica
  el stock final y la cantidad de filas en `stock_reservations`.
- `./mvnw verify` (Checkstyle + tests, cobertura mínima 90 % de líneas) al cerrar.

## 14. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Declarar la cola `stock.reserve` o el exchange `stock` desde el servicio | Choca con la topología de `fes-infra` | No crear beans `Queue`/`Exchange`/`Binding`; consumir/publicar solo por nombre |
| Dos entregas concurrentes del mismo pedido reservan dos veces | Stock descuadrado | Clave primaria `order_id`; la transacción perdedora revierte y republica el resultado almacenado |
| Deadlock entre pedidos con varios productos en orden distinto | Reintentos y latencia | Agrupar en `TreeMap` y bloquear en orden ascendente de `productId` |
| Publicar el resultado y fallar antes del ACK | Resultado duplicado en `fes-order-api` | La reentrega republica el resultado almacenado; `fes-order-api` debe ser idempotente por `orderId` |
| NACK sin reencolar sin DLX configurado | Pérdida del mensaje | La topología de `fes-infra` define retry con TTL y DLQ; se documenta como dependencia cross-repo |
| `stock` nulo interpretado como cero | Rechazos indebidos | `canCover`/`reserve` tratan `null` como ilimitado y hay tests específicos |
| `reason` distinto al esperado por `fes-order-api` | El pedido no se actualiza bien | Enum cerrado y pruebas del payload con los cinco valores |
| Evento sin `orderId` tratado como rechazo | No hay pedido al que asociar el resultado | El reader devuelve vacío y el listener hace NACK sin reencolar |
| Suma de repetidos desborda `Integer` | Stock incorrecto | Suma controlada; desbordamiento ⇒ `INVALID_QUANTITY` |

## 15. Trazabilidad RF → entregable

| RF | Entregable principal |
|---|---|
| RF-1 | `OrderPlacedListener` con `@RabbitListener(queues = "stock.reserve")` |
| RF-2 | `OrderPlacedMessageReader` + `StockReservationService.reserve` |
| RF-3 | `stock_reservations.order_id` PK + replay del resultado almacenado + ACK |
| RF-4 | Columna `event_id` y payloads que reutilizan el `eventId` registrado |
| RF-5 | `StockReservation.reserved` + `Product.reserve` |
| RF-6 | `StockResultPublisher` publica `stock.reserved` |
| RF-7 | `canCover`/`reserve` + `OUT_OF_STOCK` + `stock.rejected` |
| RF-8 | `PRODUCT_NOT_FOUND` cuando falta el producto bloqueado |
| RF-9 | Validación completa antes de descontar; transacción única |
| RF-10 | El rechazo se persiste y publica solo sin reservas parciales |
| RF-11 | Sin descuentos parciales; rollback ante error inesperado |
| RF-12 | `findByIdForUpdate` con `PESSIMISTIC_WRITE` |
| RF-13 | `reserve` valida con lock + `CHECK (stock >= 0)` |
| RF-14 | Validación de cantidad + `INVALID_QUANTITY` |
| RF-15 | `canCover`/`reserve` con `stock == null` ilimitado |
| RF-16 | Commit del resultado, publicación y luego `basicAck` |
| RF-17 | `basicNack(requeue=false)` ante falla |
| RF-18 | Reader devuelve vacío sin `orderId`; listener NACK sin reencolar |
| RF-19 | Enum `RejectionReason` + precedencia fija |
| RF-20 | Sin controladores nuevos |
| RF-21 | `controller/`, `ProductService`, `PublicationService` sin cambios |
| RF-22 | `application.yaml` de Actuator sin cambios de endpoints |
| RF-23 | Variables de entorno existentes; sin secretos nuevos |
| RF-24 | Solo se leen/escriben `products` y `stock_reservations` |
| RF-25 | Un rechazo nunca publica `stock.reserved` |
| RF-26 | `INVALID_ORDER` persistido y publicado con ACK |
| RF-27 | Agregación por `productId` en `TreeMap` |
| RF-28 | `Product.isReservable` + `PRODUCT_NOT_AVAILABLE` |

## 16. Trazabilidad NFR → entregable

| NFR | Entregable |
|---|---|
| NFR-1 | `V3` y entidad `StockReservation` en la base `catalog`; sin tablas ajenas |
| NFR-2 | `spring-boot-starter-amqp` ya presente; sin Kafka |
| NFR-3 | Sin beans de topología; solo nombres `stock.reserve`, `stock`, routing keys |
| NFR-4 | Sin cambios en `/catalog`, publicación, imágenes ni DTOs HTTP |
| NFR-5 | Se reutilizan `RABBITMQ_*`; no se agregan secretos |
| NFR-6 | Clases, tablas y eventos en inglés; documentación del plan en español |
| NFR-7 | El único efecto público es el stock resultante de reservas confirmadas |

## 17. Orden de implementación

1. Migración `V3__create_stock_reservations.sql`. **(RF-3, RF-4, RF-5, RF-7, RF-19)**
2. Enums `ReservationStatus` y `RejectionReason`. **(RF-19)**
3. Entidad `StockReservation` y `StockReservationRepository`. **(RF-3, RF-4, RF-5)**
4. Métodos de reserva en `Product` y `ProductRepository.findByIdForUpdate`.
   **(RF-5, RF-7, RF-12, RF-13, RF-15, RF-28)**
5. Payloads y reader de `order.placed`. **(RF-2, RF-14, RF-18, RF-26, RF-27)**
6. Payloads de resultado, `StockResultPublisher` y configuración Rabbit.
   **(RF-6, RF-7, RF-16, RF-19)**
7. `StockReservationService` con validaciones, idempotencia y bloqueo.
   **(RF-2 … RF-17, RF-26 … RF-28)**
8. `OrderPlacedListener` con ACK/NACK, replay y conflicto de unicidad.
   **(RF-1, RF-3, RF-16, RF-17, RF-18)**
9. Tests unitarios, de integración y de regresión. **(RF-1 … RF-28, NFR-1 … NFR-7)**
10. `./mvnw verify` y demo manual con la topología de `fes-infra`. **(RF-1 … RF-28)**

## 18. Fuera de alcance (no implementado aquí)

- La creación, persistencia y actualización de pedidos (`fes-order-api`).
- Pagos, intentos de pago, notificación al vendedor y feed del vendedor.
- La topología RabbitMQ, TTL, retry y DLQ (`fes-infra`).
- Kafka, workflows diferidos y nuevas fases del proceso de venta.
- Nuevos endpoints HTTP, storefront, detalle de producto o tienda.
- La liberación de reservas por vencimiento, cancelación o fallo de pago.
- Tablas de `orders`, `payments` o `panel`.
- Cambios en `/catalog`, publicación, imágenes, Actuator, health checks o métricas.
