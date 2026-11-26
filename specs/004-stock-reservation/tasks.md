# Tasks 004 — Reserva de stock desde `order.placed` con resultado y compensación

Tareas de implementación para `spec.md` y `plan.md` de este directorio, en orden de
dependencia y pensadas para ~20–30 minutos cada una. Usan la arquitectura Layered de
`AGENTS.md` (`/spring-boot-project-creator` opción Layered, sin regenerar el proyecto,
`/spring-boot-layered-template` y `/clean-code-guard`). Estado inicial: ninguna tarea
hecha (`[ ]`). Se agrega una migración (`V3`), un paquete `messaging/rabbit/` y ninguna
dependencia nueva.

## Migración, enums y persistencia

- [x] **T1. Migración `V3__create_stock_reservations.sql`**
  - Cubre: RF-3, RF-4, RF-5, RF-7, RF-19
  - Crear `src/main/resources/db/migration/V3__create_stock_reservations.sql` con la
    tabla `stock_reservations` del plan: `order_id` como clave primaria, `event_id`,
    `status`, `reason`, `reserved_at`, `rejected_at`, `created_at`, `updated_at`, los
    `CHECK` de estado/motivo/coherencia y el índice por `event_id`. No editar `V1` ni
    `V2`.
  - Done when: Flyway aplica `V1` + `V2` + `V3` en una base limpia y
    `information_schema.columns` muestra las columnas con sus nulabilidades y el `CHECK`
    rechaza una fila `RESERVED` con `reason` o una `REJECTED` sin `reason`.

- [x] **T2. Enums `ReservationStatus` y `RejectionReason`**
  - Cubre: RF-19
  - Crear en `model/enums/` los enums `ReservationStatus { RESERVED, REJECTED }` y
    `RejectionReason { OUT_OF_STOCK, PRODUCT_NOT_FOUND, PRODUCT_NOT_AVAILABLE,
    INVALID_QUANTITY, INVALID_ORDER }`, sin textos visibles al usuario.
  - Done when: los enums compilan con exactamente esos cinco motivos y dos estados, y
    coinciden con los valores permitidos por el `CHECK` de `V3`.

- [x] **T3. Entidad `StockReservation`**
  - Cubre: RF-3, RF-4, RF-5, RF-7
  - Crear `model/StockReservation` con `@Entity`, `@Table(name = "stock_reservations")`,
    `order_id` como `@Id`, `event_id`, `status`, `reason`, `reserved_at`,
    `rejected_at`, `created_at`, `updated_at`, getters de Lombok, constructor protegido
    y las fábricas `reserved(eventId, orderId, now)` y
    `rejected(eventId, orderId, reason, now)`, más `@PrePersist`/`@PreUpdate`.
  - Done when: la entidad arranca con `ddl-auto: validate` contra `V3`, `reserved` deja
    `reason` nulo y `reservedAt` no nulo, y `rejected` deja `rejectedAt` no nulo y
    `reservedAt` nulo.

- [x] **T4. Repositorio `StockReservationRepository`**
  - Cubre: RF-3, RF-5, RF-16
  - Crear `repository/StockReservationRepository extends JpaRepository<StockReservation,
    UUID>` sin consultas adicionales; la clave primaria es el `orderId`.
  - Done when: el repositorio compila, `findById(orderId)` recupera la reserva y
    `saveAndFlush` de un `orderId` duplicado lanza conflicto de unicidad.

- [x] **T5. Métodos de reserva en `Product`**
  - Cubre: RF-5, RF-7, RF-13, RF-15, RF-28
  - Agregar `isReservable()` (`stage == PUBLISHED && deletedAt == null`),
    `canCover(int quantity)` (`stock == null || stock >= quantity`) y
    `reserve(int quantity)` (no toca `null`, descuenta si alcanza y lanza si no alcanza),
    sin setters públicos ni cambios a `openNew`/`update`/`publish`.
  - Done when: un test rápido compila y confirma que `stock = null` es ilimitado, que
    `stock = 5` cubre 5 pero no 6 y que un producto en borrador o eliminado no es
    reservable.

- [x] **T6. `ProductRepository.findByIdForUpdate`**
  - Cubre: RF-12, RF-13, RF-28
  - Agregar la consulta con `@Lock(LockModeType.PESSIMISTIC_WRITE)` y
    `@Query("select p from Product p where p.id = :id")` para obtener un producto
    bloqueado. No modificar las consultas existentes.
  - Done when: el método compila, devuelve el producto en una transacción y un test de
    integración observa que dos transacciones sobre el mismo `id` se serializan.

## Mensajería

- [x] **T7. Payloads de entrada y `OrderPlacedMessageReader`**
  - Cubre: RF-2, RF-14, RF-18, RF-26, RF-27
  - Crear en `messaging/rabbit/model/` los records `OrderPlacedMessage(UUID eventId, UUID
    orderId, List<OrderPlacedItem> items)` y `OrderPlacedItem(UUID productId, Integer
    quantity)`, y `messaging/rabbit/reader/OrderPlacedMessageReader` que lea el JSON de
    forma tolerante:
    `orderId` obligatorio y UUID; `eventId` opcional; `items` ausente o no lista se
    convierte en lista vacía; `productId` inválido se deja nulo; `quantity` no numérica o
    fuera de rango se deja nula; los campos extra se ignoran.
  - Done when: el reader devuelve `Optional.empty()` sin `orderId` utilizable, procesa un
    JSON con `orderId` válido e ítems inválidos sin lanzar excepción y conserva los
    ítems repetidos para que el servicio los sume.

- [x] **T8. Payloads de resultado**
  - Cubre: RF-6, RF-7, RF-19
  - Crear en `messaging/rabbit/model/` `StockReservedMessage(UUID eventId, UUID orderId,
    Instant reservedAt)` y `StockRejectedMessage(UUID eventId, UUID orderId,
    RejectionReason reason, Instant rejectedAt)`, cada uno con una fábrica
    `from(StockReservation)`.
  - Done when: las fábricas producen exactamente los campos del contrato, `reason` se
    serializa como el nombre del enum y `eventId` puede ser nulo.

- [x] **T9. `StockResultPublisher`**
  - Cubre: RF-6, RF-7, RF-16, RF-19
  - Crear `messaging/rabbit/publisher/StockResultPublisher` con `RabbitTemplate`; publicar
    `stock.reserved` o `stock.rejected` en el exchange `stock` según el estado, usando
    `CorrelationData` y esperando la confirmación del broker con un timeout.
  - Done when: un test con `RabbitTemplate` mock verifica exchange, routing key y payload
    para ambos estados, y una confirmación fallida o un timeout lanza excepción.

- [x] **T10. Configuración Rabbit de entrada/salida**
  - Cubre: RF-6, RF-16, RF-17, NFR-3, NFR-5
  - Crear `config/RabbitMessagingConfig` con un `Jackson2JsonMessageConverter` basado en
    el `ObjectMapper` de Spring. En `application.yaml` configurar
    `spring.rabbitmq.listener.simple.acknowledge-mode: manual`,
    `default-requeue-rejected: false`, `publisher-confirm-type: correlated` y
    `publisher-returns: true`. No crear `Queue`, `Exchange`, `Binding`, TTL ni DLX.
  - Done when: el contexto carga sin declarar topología, el convertidor serializa los
    records de salida y no se agregan variables de entorno ni secretos.

- [x] **T11. `StockReservationService`: idempotencia y persistencia del resultado**
  - Cubre: RF-3, RF-4, RF-5, RF-16
  - Crear `service/StockReservationService` con `reserve(OrderPlacedMessage)` y
    `find(UUID orderId)`. Antes de cualquier trabajo, buscar por `orderId` y devolver el
    resultado almacenado si existe. Agregar un método privado que persista y devuelva
    `StockReservation.rejected(...)` sin tocar stock.
  - Done when: un test confirma que con una reserva existente no se consultan productos
    ni se guarda nada nuevo, y que un rechazo se persiste con su `eventId`, `reason` y
    `rejectedAt`.

- [x] **T12. `StockReservationService`: estructura, cantidad y agregación**
  - Cubre: RF-14, RF-19, RF-26, RF-27
  - Validar `items` nulo/vacío o sin ningún `productId` ⇒ `INVALID_ORDER`; `quantity`
    nula o ≤ 0 ⇒ `INVALID_QUANTITY`; agrupar por `productId` en un `TreeMap` y sumar con
    control de desbordamiento ⇒ `INVALID_QUANTITY` si excede `Integer.MAX_VALUE`. No
    tocar stock en estos rechazos.
  - Done when: tests unitarios cubren cada motivo y confirman que varios ítems del mismo
    producto producen una sola cantidad total.

- [x] **T13. `StockReservationService`: bloqueo, existencia, disponibilidad y stock**
  - Cubre: RF-7, RF-8, RF-12, RF-13, RF-28
  - Recorrer los `productId` ordenados, obtener cada producto con `findByIdForUpdate`,
    guardarlos en un mapa y evaluar en barridos completos: falta alguno ⇒
    `PRODUCT_NOT_FOUND`; alguno no reservable ⇒ `PRODUCT_NOT_AVAILABLE`; alguno sin
    stock suficiente ⇒ `OUT_OF_STOCK`. No llamar a `reserve` si existe cualquier rechazo.
  - Done when: tests unitarios verifican los tres motivos, que el motivo no depende del
    orden de los ítems y que `Product.reserve` no se invoca en ningún rechazo.

- [x] **T14. `StockReservationService`: éxito y stock nulo ilimitado**
  - Cubre: RF-5, RF-6, RF-15
  - Cuando todos los productos son reservables y cubren la cantidad, llamar
    `reserve(quantity)` a cada uno, guardar `StockReservation.reserved(...)` y devolverla.
    El stock nulo no debe cambiar.
  - Done when: un test unitario verifica el descuento de cada producto, la fila
    `RESERVED` con `reservedAt`, y que un producto con stock nulo se reserva sin
    modificar su stock.

## Listener

- [x] **T15. `OrderPlacedListener`: lectura y NACK sin `orderId`**
  - Cubre: RF-1, RF-18
  - Crear `messaging/rabbit/listener/OrderPlacedListener` con
    `@RabbitListener(queues = "stock.reserve")` y firma `(Message, Channel)`. Leer el
    cuerpo con el reader; si devuelve vacío, `basicNack(deliveryTag, false, false)` y
    terminar sin publicar ni persistir.
  - Done when: un test invoca el listener con un JSON sin `orderId` y verifica NACK con
    `requeue = false`, ninguna llamada al servicio y ninguna publicación; con `x-death` de
    4 o más entradas publica el mensaje original en `stock.reserve.dlq` y hace ACK.

- [x] **T16. `OrderPlacedListener`: reserva, publicación y ACK**
  - Cubre: RF-3, RF-6, RF-16, RF-17
  - Con un mensaje parseado, llamar `reserve`, publicar el resultado y recién entonces
    `basicAck(deliveryTag, false)`. Si `reserve` o `publish` lanzan excepción,
    `basicNack(deliveryTag, false, false)`.
  - Done when: tests confirman ACK tras reserva y tras rechazo, NACK ante falla del
    servicio y NACK ante falla del publisher mientras el intento sea menor a 5, y al
    quinto fallo la publicación en `stock.reserve.dlq` seguida de ACK.

- [x] **T17. `OrderPlacedListener`: replay por conflicto de unicidad**
  - Cubre: RF-3, RF-4, RF-17
  - Capturar `DataIntegrityViolationException` de una entrega concurrente del mismo
    `orderId`, releer con `find(orderId)` el resultado almacenado por la transacción
    ganadora, publicarlo y hacer ACK. Si no aparece, NACK sin reencolar.
  - Done when: un test simula el conflicto, verifica que se relee el resultado, se
    publica el `eventId` almacenado y se hace ACK sin una segunda reserva.

## Pruebas

- [x] **T18. Tests de dominio de `Product`**
  - Cubre: RF-5, RF-7, RF-13, RF-15, RF-28
  - Ampliar `ProductTest` con `isReservable` para publicado, borrador y eliminado;
    `canCover` con stock nulo, igual, mayor y menor; `reserve` con descuento, stock nulo
    y cantidad no cubierta.
  - Done when: los tests pasan y cubren los tres métodos sin base ni red.

- [x] **T19. Tests de `OrderPlacedMessageReader`**
  - Cubre: RF-2, RF-14, RF-18, RF-26, RF-27
  - Cubrir contrato mínimo, campos extra ignorados, `orderId` ausente/inválido,
    `eventId` ausente, `items` ausente/no lista, `productId` inválido, `quantity`
    no numérica y ítems repetidos.
  - Done when: cada caso tiene aserción sobre el `Optional`, los campos y la lista de
    ítems, sin excepciones no controladas.

- [x] **T20. Tests unitarios de idempotencia y replay del servicio**
  - Cubre: RF-3, RF-4, RF-5, RF-16
  - Con `StockReservationRepository` y `ProductRepository` mock, verificar que un
    `orderId` con resultado reservado o rechazado se devuelve tal cual, no se consultan
    productos, no se guarda de nuevo y se conserva el `eventId` original.
  - Done when: los tests pasan y usan `verifyNoMoreInteractions` sobre los productos.

- [x] **T21. Tests unitarios de validación y motivos del servicio**
  - Cubre: RF-8, RF-14, RF-19, RF-26, RF-28
  - Cubrir `INVALID_ORDER`, `INVALID_QUANTITY`, `PRODUCT_NOT_FOUND`,
    `PRODUCT_NOT_AVAILABLE` y `OUT_OF_STOCK`, incluida la precedencia cuando hay varios
    problemas y el desbordamiento de la suma de repetidos.
  - Done when: cada motivo queda persistido con el enum correcto y no se invoca
    `Product.reserve` en ningún rechazo.

- [x] **T22. Tests unitarios de éxito, stock nulo y agregación**
  - Cubre: RF-7, RF-12, RF-15, RF-27
  - Verificar descuento de varios productos, suma de ítems repetidos, stock nulo
    ilimitado, stock justo en cero y cantidad mayor al stock.
  - Done when: los tests confirman el stock final, la fila `RESERVED` y que los productos
    se bloquean en orden ascendente de `productId`.

- [x] **T23. Test de integración de migración, entidad y bloqueo**
  - Cubre: RF-12, RF-21, RF-24
  - Ampliar `ProductRepositoryIntegrationTest` con `V3`, persistencia de
    `StockReservation` y `findByIdForUpdate`; confirmar que las consultas públicas
    siguen devolviendo lo mismo y que no se crean tablas ajenas.
  - Done when: la suite aplica `V1` + `V2` + `V3` en limpio, valida columnas/constraints y
    el bloqueo pesimista con PostgreSQL real.

- [x] **T24. Test de integración de concurrencia**
  - Cubre: RF-3, RF-5, RF-7, RF-12, RF-13
  - Con dos threads y `CountDownLatch`, ejecutar dos pedidos por el mismo producto y
    verificar que el stock final no es negativo y que la suma reservada no supera el
    disponible. Repetir con dos entregas concurrentes del mismo `orderId` y verificar una
    sola fila y un solo descuento.
  - Done when: la suite de integración pasa de forma repetible y no quedan reservas
    parciales ni stock negativo.

- [x] **T25. Tests de entidad de reserva y publisher**
  - Cubre: RF-6, RF-7, RF-19
  - Crear `StockReservationTest` para las fábricas y `StockResultPublisherTest` con
    `RabbitTemplate` mock para `stock.reserved`/`stock.rejected`, el payload exacto y la
    confirmación fallida.
  - Done when: los tests pasan y verifican los cinco motivos posibles en el contrato de
    rechazo.

- [x] **T26. Tests del listener**
  - Cubre: RF-1, RF-3, RF-16, RF-17, RF-18, RF-26
  - Crear `OrderPlacedListenerTest` con `Channel`, servicio, publisher, DLQ publisher y
    reader mock: ACK tras reserva, ACK tras rechazo, NACK sin `orderId`, NACK por falla del
    servicio o del publisher, replay de resultado existente, conflicto de unicidad, e
    intentos 1–4 con NACK sin reencolar frente al quinto con `stock.reserve.dlq` + ACK.
  - Done when: cada caso verifica `basicAck` o `basicNack` con los parámetros exactos, el
    conteo de `x-death` y las publicaciones esperadas.

- [ ] **T27. Regresión HTTP, `./mvnw verify` y demo manual**
  - Cubre: RF-1 … RF-28, NFR-1 … NFR-7 (verificación global)
  - Ejecutar `PublicationControllerWebTest`, `ProductControllerWebTest`,
    `ProductImageControllerWebTest`, `ActuatorConfigurationTest`, `Cors*` y
    `./mvnw verify` (Checkstyle + tests + cobertura mínima 90 %). Hacer la demo con la
    topología de `fes-infra`: reserva exitosa, stock nulo, rechazo por stock, producto
    inexistente, producto no disponible, cantidad inválida, pedido inválido, reentrega
    del mismo `orderId` con otro `eventId`, evento sin `orderId` y dos pedidos
    concurrentes.
  - Done when: `./mvnw verify` está en verde, `/catalog` y Actuator responden igual que
    antes y la demo cumple los criterios de finalización de la spec.

## Cobertura RF

| RF | Tareas |
|---|---|
| RF-1 | T15, T26, T27 |
| RF-2 | T7, T19, T21, T27 |
| RF-3 | T1, T3, T4, T11, T16, T17, T20, T24, T26, T27 |
| RF-4 | T1, T3, T11, T17, T20, T27 |
| RF-5 | T1, T3, T5, T11, T14, T18, T20, T24, T27 |
| RF-6 | T8, T9, T10, T14, T16, T25, T27 |
| RF-7 | T1, T3, T5, T8, T9, T13, T18, T22, T24, T25, T27 |
| RF-8 | T13, T21, T27 |
| RF-9 | T13, T21, T27 |
| RF-10 | T13, T21, T27 |
| RF-11 | T13, T21, T27 |
| RF-12 | T6, T13, T22, T23, T24, T27 |
| RF-13 | T5, T6, T13, T18, T24, T27 |
| RF-14 | T7, T12, T19, T21, T27 |
| RF-15 | T5, T14, T18, T22, T27 |
| RF-16 | T4, T9, T10, T11, T16, T20, T26, T27 |
| RF-17 | T10, T16, T17, T26, T27 |
| RF-18 | T7, T15, T19, T26, T27 |
| RF-19 | T1, T2, T8, T9, T12, T21, T25, T27 |
| RF-20 | T27 |
| RF-21 | T23, T27 |
| RF-22 | T27 |
| RF-23 | T10, T27 |
| RF-24 | T23, T27 |
| RF-25 | T16, T27 |
| RF-26 | T7, T12, T19, T21, T26, T27 |
| RF-27 | T7, T12, T19, T22, T27 |
| RF-28 | T5, T6, T13, T18, T21, T27 |

## Cobertura NFR

| NFR | Tareas |
|---|---|
| NFR-1 | T1, T3, T11, T13, T23, T24, T27 |
| NFR-2 | T10, T27 |
| NFR-3 | T9, T10, T15, T27 |
| NFR-4 | T23, T27 |
| NFR-5 | T10, T27 |
| NFR-6 | T1, T2, T3, T7, T8, T27 |
| NFR-7 | T27 |
