# UML 004 — Reserva de stock desde `order.placed` con resultado y compensación

Diagramas del desarrollo descrito en `spec.md`, `plan.md` y `tasks.md` de este
directorio, alineados con las convenciones de `catalog-api` (`AGENTS.md`: Java 25 /
Spring Boot 4.1, paquete `com.friendlyeshop.catalog`, arquitectura **Layered** con la
extensión `messaging/rabbit/`). Prosa en español; los diagramas Mermaid conservan los
nombres reales de clases, rutas, colas, exchanges, variables y payloads. No se declara
topología RabbitMQ: `fes-infra` es dueño de `orders`, `stock.reserve`, `stock` y
`order.stock-results`; este servicio solo consume y publica por nombre.

## 1. Contexto

`catalog-api` es la fuente de verdad de productos y stock. En este corte incorpora el
consumidor de la cola `stock.reserve` (exchange `orders`, routing key `order.placed`),
la reserva de stock idempotente por `orderId`, el registro del resultado y la publicación
de `stock.reserved` o `stock.rejected` en el exchange `stock`. El `eventId` se guarda
para trazabilidad y se reutiliza al republicar el resultado almacenado; la idempotencia
se ancla al `orderId`.

Reglas centrales:

- stock nulo = ilimitado; la reserva se confirma sin descontar;
- solo productos publicados y no eliminados son reservables;
- ítems repetidos del mismo producto se suman antes de comprobar disponibilidad;
- todo o nada: no hay reservas parciales, sobreventa ni stock negativo;
- ACK solo después de persistir el resultado y publicarlo; una falla deja el mensaje sin
  confirmar para que `fes-infra` decida retry o DLQ;
- un evento sin `orderId` utilizable no se persiste, no se publica y se hace NACK sin
  reencolar.

## 2. Diagrama de estructura (componentes)

```mermaid
flowchart LR
  subgraph infra [fes-infra · topología RabbitMQ]
    OrdersEx[[exchange orders<br/>topic durable]]
    StockQ[cola stock.reserve]
    StockEx[[exchange stock<br/>topic durable]]
    ResultQ[cola order.stock-results]
    OrdersEx -->|order.placed| StockQ
    StockEx -->|stock.reserved / stock.rejected| ResultQ
  end

  subgraph catalogApi [catalog-api · com.friendlyeshop.catalog]
    subgraph messaging [messaging/rabbit]
      Listener["OrderPlacedListener<br/>@RabbitListener(queues = stock.reserve)"]
      Reader["OrderPlacedMessageReader"]
      InMsg["OrderPlacedMessage / OrderPlacedItem"]
      Publisher["StockResultPublisher"]
      OutMsg["StockReservedMessage / StockRejectedMessage"]
    end

    subgraph services [service]
      ReservationSvc["StockReservationService<br/>reserve(message) / find(orderId)"]
      ExistingServices["ProductService / PublicationService<br/>sin cambios"]
    end

    subgraph repos [repository]
      ProductRepo["ProductRepository<br/>+ findByIdForUpdate"]
      ReservationRepo["StockReservationRepository"]
    end

    subgraph modelPkg [model]
      Product["Product<br/>+ isReservable / canCover / reserve"]
      Reservation["StockReservation<br/>@Entity"]
      Status["ReservationStatus"]
      Reason["RejectionReason"]
    end

    Config["RabbitMessagingConfig<br/>Jackson2JsonMessageConverter"]
    Migration["Flyway V3<br/>stock_reservations"]
  end

  subgraph data [Persistencia]
    DB[(PostgreSQL<br/>base catalog<br/>products + stock_reservations)]
  end

  StockQ --> Listener
  Listener --> Reader
  Reader --> InMsg
  Listener --> ReservationSvc
  ReservationSvc --> ProductRepo
  ReservationSvc --> ReservationRepo
  ReservationSvc --> Product
  ReservationSvc --> Reservation
  Reservation --> Status
  Reservation --> Reason
  ProductRepo --> DB
  ReservationRepo --> DB
  Migration --> DB
  Listener --> Publisher
  Publisher --> OutMsg
  Publisher -->|exchange stock<br/>stock.reserved / stock.rejected| StockEx
  Config -.convertidor JSON.-> Listener
  Config -.convertidor JSON.-> Publisher
  ExistingServices -.sin cambios.-> DB
```

Notas:

- `/catalog`, `ProductService`, `PublicationService`, Actuator y health checks no
  participan del flujo de reserva y quedan sin cambios.
- No hay beans `Queue`, `Exchange`, `Binding`, TTL ni DLX en `catalog-api`.
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` y
  `RABBITMQ_VIRTUAL_HOST` se reutilizan desde `application.yaml`.

## 3. Diagrama de clases (frontera de mensajería, servicio y persistencia)

Se muestran relaciones arquitectónicas y contratos de frontera; no se reproduce cada
import ni variable local.

```mermaid
classDiagram
  direction TB

  class OrderPlacedListener {
    -OrderPlacedMessageReader reader
    -StockReservationService reservationService
    -StockResultPublisher publisher
    +onOrderPlaced(message Message, channel Channel) void
  }

  class OrderPlacedMessageReader {
    -ObjectMapper objectMapper
    +read(body byte[]) Optional~OrderPlacedMessage~
  }

  class OrderPlacedMessage {
    <<record>>
    +UUID eventId
    +UUID orderId
    +List~OrderPlacedItem~ items
  }

  class OrderPlacedItem {
    <<record>>
    +UUID productId
    +Integer quantity
  }

  class StockReservationService {
    -ProductRepository productRepository
    -StockReservationRepository reservationRepository
    +reserve(message OrderPlacedMessage) StockReservation
    +find(orderId UUID) Optional~StockReservation~
    -reject(eventId UUID, orderId UUID, reason RejectionReason) StockReservation
  }

  class StockResultPublisher {
    -RabbitTemplate rabbitTemplate
    +publish(reservation StockReservation) void
  }

  class StockReservedMessage {
    <<record>>
    +UUID eventId
    +UUID orderId
    +Instant reservedAt
    +from(reservation StockReservation)$ StockReservedMessage
  }

  class StockRejectedMessage {
    <<record>>
    +UUID eventId
    +UUID orderId
    +RejectionReason reason
    +Instant rejectedAt
    +from(reservation StockReservation)$ StockRejectedMessage
  }

  class ProductRepository {
    <<interface>>
    +findByIdForUpdate(id UUID) Optional~Product~
    +findPublished(stage ProductStage, minimumStock int, pageable Pageable) Page~Product~
    +findPublicById(id UUID, stage ProductStage, minimumStock int) Optional~Product~
  }

  class StockReservationRepository {
    <<interface>>
    +findById(orderId UUID) Optional~StockReservation~
    +saveAndFlush(reservation StockReservation) StockReservation
  }

  class Product {
    <<entity>>
    +UUID id
    +Integer stock
    +ProductStage stage
    +Instant deletedAt
    +isReservable() boolean
    +canCover(quantity int) boolean
    +reserve(quantity int) void
  }

  class StockReservation {
    <<entity>>
    +UUID orderId
    +UUID eventId
    +ReservationStatus status
    +RejectionReason reason
    +Instant reservedAt
    +Instant rejectedAt
    +reserved(eventId UUID, orderId UUID, now Instant)$ StockReservation
    +rejected(eventId UUID, orderId UUID, reason RejectionReason, now Instant)$ StockReservation
  }

  class ReservationStatus {
    <<enumeration>>
    RESERVED
    REJECTED
  }

  class RejectionReason {
    <<enumeration>>
    OUT_OF_STOCK
    PRODUCT_NOT_FOUND
    PRODUCT_NOT_AVAILABLE
    INVALID_QUANTITY
    INVALID_ORDER
  }

  class RabbitMessagingConfig {
    +jsonMessageConverter(objectMapper ObjectMapper) MessageConverter
  }

  OrderPlacedListener --> OrderPlacedMessageReader
  OrderPlacedListener --> StockReservationService
  OrderPlacedListener --> StockResultPublisher
  OrderPlacedMessageReader ..> OrderPlacedMessage
  OrderPlacedMessage *-- OrderPlacedItem
  StockReservationService --> ProductRepository
  StockReservationService --> StockReservationRepository
  StockReservationService ..> Product
  StockReservationService ..> StockReservation
  StockReservationService ..> RejectionReason
  StockResultPublisher ..> StockReservedMessage
  StockResultPublisher ..> StockRejectedMessage
  StockReservation --> ReservationStatus
  StockReservation --> RejectionReason
  RabbitMessagingConfig ..> StockResultPublisher
```

Relaciones clave:

- `OrderPlacedListener` solo conoce el reader, el servicio y el publisher; no toca
  repositorios ni entidades directamente.
- `StockReservationService` es el dueño del algoritmo de idempotencia, validación,
  bloqueo y resultado; `Product` concentra las reglas de stock.
- `StockResultPublisher` arma el payload desde la entidad `StockReservation`, reutilizando
  el `eventId` almacenado.
- `RabbitMessagingConfig` solo aporta el convertidor JSON; no declara topología.

## 4. Secuencia — consumo, reserva, publicación y ACK

Flujo principal para un `order.placed` con `orderId` utilizable. El `alt` cubre el
resultado almacenado y los rechazos.

```mermaid
sequenceDiagram
  autonumber
  participant B as RabbitMQ (fes-infra)
  participant L as OrderPlacedListener
  participant R as OrderPlacedMessageReader
  participant S as StockReservationService
  participant SR as StockReservationRepository
  participant PR as ProductRepository
  participant P as StockResultPublisher
  participant DB as PostgreSQL (catalog)

  B->>L: deliver order.placed desde stock.reserve
  L->>R: read(message.body)
  alt sin orderId utilizable
    R-->>L: Optional.empty()
    L->>B: basicNack(tag, requeue = false)
  else orderId utilizable
    R-->>L: Optional.of(OrderPlacedMessage)
    L->>S: reserve(message)
    S->>SR: findById(orderId)
    SR->>DB: select from stock_reservations where order_id = ?
    DB-->>SR: fila o vacío
    alt resultado ya almacenado
      SR-->>S: StockReservation existente
      S-->>L: resultado almacenado
    else pedido nuevo
      S->>S: validar estructura y cantidades<br/>agrupar ítems repetidos por productId
      alt ítems inválidos
        S->>SR: saveAndFlush(rejected INVALID_ORDER / INVALID_QUANTITY)
      else ítems válidos
        loop productId ordenados
          S->>PR: findByIdForUpdate(productId)
          PR->>DB: select ... for update
          DB-->>PR: Product o vacío
          PR-->>S: Optional~Product~
        end
        alt producto inexistente
          S->>SR: saveAndFlush(rejected PRODUCT_NOT_FOUND)
        else producto no reservable
          S->>SR: saveAndFlush(rejected PRODUCT_NOT_AVAILABLE)
        else stock insuficiente
          S->>SR: saveAndFlush(rejected OUT_OF_STOCK)
        else todo disponible
          S->>S: Product.reserve(quantity)<br/>stock nulo no cambia
          S->>SR: saveAndFlush(reserved)
        end
      end
      SR->>DB: commit (products.stock y stock_reservations)
      S-->>L: StockReservation
    end
    L->>P: publish(reservation)
    alt status RESERVED
      P->>B: exchange stock<br/>routing key stock.reserved<br/>{eventId, orderId, reservedAt}
    else status REJECTED
      P->>B: exchange stock<br/>routing key stock.rejected<br/>{eventId, orderId, reason, rejectedAt}
    end
    B-->>P: publisher confirm
    P-->>L: confirmado
    L->>B: basicAck(tag)
  end
```

Casos cubiertos por este flujo:

- Reentrega del mismo `orderId`: se salta la reserva y se republica el resultado
  almacenado.
- `eventId` distinto con el mismo `orderId`: se reutiliza el `eventId` guardado.
- Stock nulo: `Product.reserve` no modifica el valor y la reserva se confirma.
- Rechazo: se guarda la fila `REJECTED`, no se descuenta stock y se publica
  `stock.rejected`.
- Falla de BD o de publicación: no se hace ACK; el mensaje queda para retry/DLQ de
  `fes-infra`.

## 5. Flujo de decisión y motivos de rechazo

```mermaid
flowchart TD
  A["Mensaje en stock.reserve"] --> B{"¿orderId utilizable?"}
  B -->|no| N["basicNack(requeue = false)<br/>sin persistir ni publicar"]
  B -->|sí| C{"¿Existe resultado<br/>para el orderId?"}
  C -->|sí| D["Republicar resultado almacenado"]
  D --> ACK["basicAck"]
  C -->|no| E{"¿items utilizables?<br/>lista no vacía y con productId"}
  E -->|no| F["REJECTED INVALID_ORDER"]
  E -->|sí| G{"¿quantity > 0<br/>y suma sin desbordar?"}
  G -->|no| H["REJECTED INVALID_QUANTITY"]
  G -->|sí| I{"¿Todos los productos<br/>existen?"}
  I -->|no| J["REJECTED PRODUCT_NOT_FOUND"]
  I -->|sí| K{"¿Todos publicados<br/>y no eliminados?"}
  K -->|no| L["REJECTED PRODUCT_NOT_AVAILABLE"]
  K -->|sí| M{"¿Todos cubren la<br/>cantidad total?"}
  M -->|no| O["REJECTED OUT_OF_STOCK"]
  M -->|sí| Q["Descontar stock<br/>(null = ilimitado)<br/>y guardar RESERVED"]
  F --> P["Publicar stock.rejected<br/>con el reason"]
  H --> P
  J --> P
  L --> P
  O --> P
  P --> ACK
  Q --> S["Publicar stock.reserved"]
  S --> ACK
```

La precedencia del motivo es estructura del pedido → cantidad → existencia →
disponibilidad → stock, de modo que un pedido con varios problemas informa siempre el
mismo motivo. `INVALID_ORDER` cubre la falta de ítems, la falta de `productId` y la
ausencia de ítems válidos; `INVALID_QUANTITY` cubre cantidad nula, cero, negativa o suma
desbordada.

## 6. Contratos JSON

Entrada consumida desde `stock.reserve` (solo se usan `eventId`, `orderId` e
`items[].productId`, `items[].quantity`; el resto se ignora):

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

Salida `stock.reserved` en el exchange `stock`:

```json
{
  "eventId": "8f3c2b1a-0000-4000-8000-000000000000",
  "orderId": "1a2b3c4d-0000-4000-8000-000000000000",
  "reservedAt": "2026-11-25T12:00:00Z"
}
```

Salida `stock.rejected` en el exchange `stock`:

```json
{
  "eventId": "8f3c2b1a-0000-4000-8000-000000000000",
  "orderId": "1a2b3c4d-0000-4000-8000-000000000000",
  "reason": "OUT_OF_STOCK",
  "rejectedAt": "2026-11-25T12:00:00Z"
}
```

- `eventId` puede ser `null` si el evento original no lo traía; se conserva el valor
  registrado al procesar el pedido.
- `reason` solo puede ser `OUT_OF_STOCK`, `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_AVAILABLE`,
  `INVALID_QUANTITY` o `INVALID_ORDER`.
- No se exponen endpoints HTTP nuevos; `/catalog`, imágenes, publicación y Actuator
  quedan fuera de este flujo.
