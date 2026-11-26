# Spec 004 — Reserva de stock desde `order.placed` con resultado y compensación

## Contexto y objetivo

`catalog-api` es la fuente de verdad de los productos y de su stock, y hasta ahora solo
lo exponía por HTTP para el panel y para la tienda pública. En la Fase 1 del proceso de
venta asíncrono, `fes-order-api` da de alta un pedido y publica `order.placed`; este
servicio debe reaccionar reservando el stock de los ítems del pedido y devolviendo un
resultado claro: `stock.reserved` si se pudo reservar todo o `stock.rejected` si no.
La reserva debe ser idempotente por pedido para que una reentrega no descuadre el
stock, debe liberar las reservas parciales cuando un pedido multiítem falla a mitad de
camino y nunca debe permitir sobreventa ni stock negativo bajo pedidos concurrentes.
Todo el contrato HTTP existente (`/catalog`, publicación, imágenes), los health checks,
las métricas y las variables de entorno deben seguir funcionando igual. El objetivo es
que el stock quede apartado de forma confiable para que `fes-order-api` pueda continuar
el proceso de venta sin que `catalog-api` escriba pedidos ni pagos.

## Usuarios / actores

- **`fes-order-api`**: productor de `order.placed` y consumidor del resultado de stock;
  actualiza el pedido según `stock.reserved` o `stock.rejected`. No forma parte de esta
  implementación.
- **Comprador / cliente**: inicia el pedido y espera que no se le venda stock que no
  existe.
- **`fes-catalog-api`**: dueño de productos y stock; reserva el stock y publica el
  resultado.
- **`fes-infra`**: define los exchanges, colas, bindings, TTL y DLQ; no implementa la
  reserva.
- **Operación/Infra**: mantiene health checks, métricas y variables de entorno usadas
  por Kubernetes.

## Historias de usuario

- H1: Como `fes-order-api` quiero que al colocar un pedido se reserve su stock para
  poder continuar el proceso de venta.
- H2: Como comprador quiero que no se venda más stock del disponible para no recibir un
  pedido que no se puede cumplir.
- H3: Como operador quiero que las reentregas del mismo pedido no dupliquen reservas
  para que el stock no se descuadre.
- H4: Como `fes-order-api` quiero recibir `stock.reserved` o `stock.rejected` para
  actualizar el pedido según el resultado.
- H5: Como `fes-order-api` quiero que un pedido que no se puede reservar por completo no
  deje reservas parciales, para no bloquear stock de más.
- H6: Como responsable del catálogo quiero conservar `/catalog`, publicación, imágenes,
  health checks, métricas y variables de entorno.

## Requisitos funcionales (criterios de aceptación en EARS)

- RF-1: EL SISTEMA consumirá el evento `order.placed` que llega por la cola
  `stock.reserve`, bindeada al exchange `orders` con la routing key `order.placed`.
- RF-2: CUANDO reciba un `order.placed` con `orderId` utilizable e ítems utilizables, EL
  SISTEMA intentará reservar el stock de todos los productos del pedido.
- RF-3: CUANDO reciba un `order.placed` cuyo `orderId` ya tiene un resultado almacenado,
  EL SISTEMA no volverá a reservar stock, republicará ese resultado almacenado y
  confirmará la recepción del mensaje.
- RF-4: EL SISTEMA registrará el `eventId` de cada evento procesado para trazabilidad,
  sin usarlo como criterio para reservar de nuevo cuando el `orderId` ya tenga
  resultado.
- RF-5: CUANDO todos los productos de un pedido puedan reservarse, EL SISTEMA registrará
  la reserva del pedido como confirmada.
- RF-6: CUANDO registre una reserva confirmada, EL SISTEMA publicará en el exchange
  `stock` la routing key `stock.reserved` con el `eventId` registrado, el `orderId` y el
  `reservedAt`.
- RF-7: SI la cantidad total requerida de un producto supera su stock disponible,
  ENTONCES EL SISTEMA no confirmará la reserva del pedido y publicará `stock.rejected`
  con el motivo `OUT_OF_STOCK`.
- RF-8: SI algún ítem del pedido referencia un producto que no existe, ENTONCES EL
  SISTEMA no confirmará la reserva del pedido y publicará `stock.rejected` con el motivo
  `PRODUCT_NOT_FOUND`.
- RF-9: SI un pedido multiítem no puede reservar todos sus productos, ENTONCES EL
  SISTEMA no dejará ninguna reserva parcial y devolverá el stock de los productos
  afectados a su estado previo al intento.
- RF-10: EL SISTEMA no publicará `stock.rejected` hasta que no quede ninguna reserva
  parcial del pedido.
- RF-11: CUANDO libere una reserva parcial, EL SISTEMA dejará el stock disponible de ese
  producto como estaba antes del intento de reserva.
- RF-12: MIENTRAS se procesan pedidos concurrentes sobre el mismo producto, EL SISTEMA
  no reservará más unidades que el stock disponible.
- RF-13: EL SISTEMA no reducirá el stock disponible de un producto por debajo de cero.
- RF-14: SI la cantidad solicitada de un ítem no es mayor a cero, ENTONCES EL SISTEMA
  rechazará la reserva del pedido con el motivo `INVALID_QUANTITY` y sin modificar stock.
- RF-15: SI el stock de un producto no está informado (nulo), ENTONCES EL SISTEMA lo
  tratará como ilimitado y confirmará la reserva sin reducir su stock.
- RF-16: EL SISTEMA confirmará la recepción del mensaje solo después de que la reserva y
  su registro hayan quedado confirmados.
- RF-17: SI la reserva o su registro no pueden confirmarse, ENTONCES EL SISTEMA no
  confirmará la recepción del mensaje mientras queden reintentos; agotados los 5 intentos,
  lo enviará a la cola de descarte y confirmará la recepción para no reintentarlo
  indefinidamente.
- RF-18: SI el evento no trae un `orderId` utilizable, ENTONCES EL SISTEMA no confirmará
  la recepción del mensaje mientras queden reintentos, no publicará resultado y, agotados
  los 5 intentos, lo enviará a la cola de descarte y confirmará la recepción.
- RF-19: EL SISTEMA entregará en `stock.rejected` el motivo más específico de entre
  `OUT_OF_STOCK`, `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_AVAILABLE`, `INVALID_QUANTITY` e
  `INVALID_ORDER`.
- RF-20: EL SISTEMA no expondrá nuevos endpoints HTTP.
- RF-21: EL SISTEMA conservará sin cambios `/catalog`, la lectura pública de
  publicaciones y la lectura pública de imágenes.
- RF-22: EL SISTEMA conservará sin cambios los health checks y las métricas usados por
  Kubernetes.
- RF-23: EL SISTEMA conservará las variables de entorno existentes.
- RF-24: EL SISTEMA solo reservará y modificará stock de productos que pertenecen a su
  base de catálogo.
- RF-25: CUANDO un pedido no se pueda reservar, EL SISTEMA no publicará `stock.reserved`
  para ese pedido.
- RF-26: SI el evento trae un `orderId` utilizable pero sus ítems no son utilizables
  (sin ítems, sin `productId` o sin ningún ítem válido), ENTONCES EL SISTEMA publicará
  `stock.rejected` con el motivo `INVALID_ORDER` y confirmará la recepción del mensaje.
- RF-27: EL SISTEMA sumará las cantidades de los ítems repetidos del mismo producto
  antes de verificar la disponibilidad y reservar.
- RF-28: SI un producto existe pero no está publicado o está eliminado lógicamente,
  ENTONCES EL SISTEMA rechazará la reserva del pedido con el motivo
  `PRODUCT_NOT_AVAILABLE`.

## Requisitos no funcionales

- NFR-1: EL SISTEMA persistirá sus datos únicamente en la base `catalog` y no leerá ni
  escribirá tablas de `orders`, `payments` ni `panel`.
- NFR-2: EL SISTEMA usará RabbitMQ como mensajería; no incorporará Kafka en esta
  iteración.
- NFR-3: EL SISTEMA no declarará exchanges, colas, bindings, TTL ni DLQ; esa topología
  pertenece a `fes-infra`.
- NFR-4: EL SISTEMA mantendrá el contrato HTTP existente bajo `/catalog` sin cambios de
  comportamiento para el panel ni para la tienda pública.
- NFR-5: EL SISTEMA mantendrá compatibilidad con las variables de entorno ya usadas por
  Kubernetes, incluidas las `RABBITMQ_*`, y no introducirá secretos nuevos.
- NFR-6: Los nombres y la documentación técnica quedarán en inglés; los mensajes
  visibles al usuario quedarán en español.
- NFR-7: La reserva de stock no alterará el comportamiento de las lecturas públicas más
  allá del stock resultante.

## Casos límite

- Reentrega exacta del mismo `eventId` con reserva ya confirmada: no se reserva de nuevo;
  se republica el resultado almacenado y se confirma la recepción.
- Reentrega de un evento ya rechazado: no se modifica stock; se republica el rechazo
  almacenado con su motivo original y se confirma la recepción.
- Reentrega de un `orderId` con reserva ya confirmada y un `eventId` distinto: no se
  vuelve a reservar; se republica el resultado almacenado y se confirma la recepción.
- Reentrega de un `orderId` con rechazo ya almacenado y un `eventId` distinto: se
  conserva el rechazo original y no se intenta una nueva reserva.
- Evento sin `eventId` pero con `orderId` utilizable: se procesa igualmente; la
  idempotencia sigue anclada al `orderId` y el `eventId` se registra como ausente para
  trazabilidad.
- Stock nulo en un producto del pedido: se trata como ilimitado; la reserva se confirma
  sin reducir stock.
- Stock disponible igual a la cantidad solicitada: la reserva se confirma y el stock
  queda en cero.
- Stock disponible menor que la cantidad solicitada: el pedido se rechaza con
  `OUT_OF_STOCK`.
- Stock igual a cero: cualquier reserva que lo requiera se rechaza con `OUT_OF_STOCK`.
- Pedido multiítem donde el primer producto se reserva y un producto posterior falla: no
  queda ninguna reserva parcial, el stock vuelve a su estado previo y se publica
  `stock.rejected`.
- Dos o más pedidos concurrentes por el mismo producto: no hay sobreventa y el stock no
  queda negativo.
- Ítem con producto inexistente: el pedido se rechaza con `PRODUCT_NOT_FOUND`.
- Ítem con producto en borrador, despublicado o eliminado lógicamente: el pedido se
  rechaza con `PRODUCT_NOT_AVAILABLE`.
- Cantidad de ítem igual a cero, negativa o ausente: el pedido se rechaza con
  `INVALID_QUANTITY` sin modificar stock.
- Pedido sin ítems utilizables o con `productId` ausente: el pedido se rechaza con
  `INVALID_ORDER`, se registra el resultado y se confirma la recepción.
- Evento sin `orderId` utilizable: no se reserva, no se registra resultado ni se publica
  nada; no se confirma la recepción mientras queden reintentos y, agotados los 5 intentos,
  se envía a la cola de descarte y se confirma.
- Pedido con varios problemas a la vez: se informa el motivo de la primera comprobación
  que falle, en el orden estructura del pedido, cantidad, existencia del producto,
  disponibilidad del producto y stock.
- Falla transitoria al registrar la reserva o al publicar el resultado: el mensaje no se
  confirma mientras queden reintentos; agotados los 5 intentos, se envía a la cola de
  descarte y se confirma.
- Reserva confirmada pero publicación del resultado fallida: la reentrega no duplica la
  reserva; republica el resultado almacenado.
- Evento con ítems repetidos del mismo producto: la cantidad total requerida es la suma
  de las cantidades solicitadas antes de verificar el stock.
- Suma de cantidades repetidas que excede el rango de una cantidad válida: el pedido se
  rechaza con `INVALID_QUANTITY` sin modificar stock.

## Fuera de alcance

- La creación, persistencia y actualización de pedidos (`fes-order-api`).
- Los pagos y los intentos de pago (`fes-payment-api`), reservados para la Fase 3.
- La notificación al vendedor y su feed, reservados para la Fase 2.
- La topología RabbitMQ: exchanges, colas, bindings, TTL, reintentos y DLQ
  (`fes-infra`).
- Kafka y cualquier workflow de negocio diferido fuera de esta fase.
- El detalle de producto y de tienda, y cualquier trabajo del storefront.
- Nuevos endpoints HTTP y cambios en `/catalog`, publicación o imágenes.
- La liberación de reservas por motivos distintos del fallo de un pedido multiítem, como
  vencimiento, cancelación del pedido o fallo de pago; quedan para fases posteriores.
- La lectura o escritura de tablas de `orders`, `payments` o `panel`.
- La definición del esquema físico, las migraciones, las clases, los algoritmos y todo
  detalle técnico (pertenece al plan).

## Criterios de finalización

- Todos los RF con test automatizado en verde, incluidos el camino exitoso, el rechazo
  por stock insuficiente, el rechazo por producto inexistente, el rechazo por producto
  no disponible, la idempotencia por `orderId`, la reentrega con `eventId` distinto, la
  compensación de reservas parciales, la suma de ítems repetidos, la ausencia de
  sobreventa bajo concurrencia, el stock nulo ilimitado y el manejo de eventos
  malformados.
- `./mvnw verify` en verde, incluido Checkstyle.
- Demo manual con la topología de `fes-infra`: publicar un `order.placed` y verificar la
  reserva y la publicación de `stock.reserved`; forzar un pedido que no se puede
  reservar y verificar `stock.rejected` con el motivo correcto; reentregar el mismo
  evento y el mismo `orderId` con otro `eventId` y verificar que no se duplica la
  reserva; ejecutar pedidos concurrentes y verificar que no hay sobreventa ni stock
  negativo; enviar un evento sin `orderId` y verificar que no se publica resultado.
- `/catalog`, la lectura pública de publicaciones, la lectura pública de imágenes y los
  endpoints de Actuator responden igual que antes del cambio.

## Decisiones resueltas

- ~~¿La reserva debe tratar el stock nulo como ilimitado, como en la visibilidad actual,
  o debe rechazar la reserva por falta de stock informado?~~ Se trata como ilimitado,
  consistente con la visibilidad actual: la reserva de un producto con stock nulo se
  confirma sin reducir stock.
- ~~Cuando se reentrega un evento ya procesado, ¿el servicio debe volver a publicar el
  resultado ya registrado o solo abstenerse de reservar de nuevo y confirmar la
  recepción?~~ Se republica el resultado almacenado (reservado o rechazado) y se confirma
  la recepción; nunca se reserva dos veces.
- ~~¿La reserva solo puede aplicarse a productos publicados y no eliminados, o también a
  productos en borrador, despublicados o eliminados?~~ Solo son reservables los productos
  publicados y no eliminados lógicamente; un producto en borrador, despublicado o
  eliminado provoca `stock.rejected` con `PRODUCT_NOT_AVAILABLE`.
- ~~¿Un mismo `orderId` con un `eventId` distinto es una nueva entrega idempotente o un
  nuevo intento de reserva?~~ La idempotencia se ancla al `orderId`; el `eventId` se
  registra para trazabilidad. Un `eventId` distinto con el mismo `orderId` republica el
  resultado almacenado y nunca reserva de nuevo.
- ~~¿Un pedido puede repetir un mismo producto en varios ítems y, si es así, las
  cantidades se suman para verificar el stock disponible?~~ Se permite repetir un
  producto y sus cantidades se suman por producto antes de verificar la disponibilidad y
  reservar.
- ~~¿Cómo debe tratar el servicio un pedido sin ítems utilizables o un evento malformado:
  rechazarlo con `stock.rejected`, no confirmarlo para que la topología lo derive a la
  cola de descarte, o descartarlo?~~ Sin un `orderId` utilizable no se confirma la
  recepción mientras queden reintentos; agotados los 5 intentos, se envía a la cola de
  descarte y se confirma. Con un `orderId` válido pero ítems inválidos se publica
  `stock.rejected` con `INVALID_ORDER` y se confirma la recepción; una cantidad inválida
  usa el motivo más específico `INVALID_QUANTITY`.
- ~~¿Qué valores o códigos concretos debe llevar `reason` en `stock.rejected` y cuáles
  espera `fes-order-api`?~~ El vocabulario es `OUT_OF_STOCK`, `PRODUCT_NOT_FOUND`,
  `PRODUCT_NOT_AVAILABLE`, `INVALID_QUANTITY` e `INVALID_ORDER`; se usa el motivo más
  específico aplicable.
- Se mantiene la reserva todo o nada con compensación de reservas parciales: no hay
  sobreventa, el stock nunca queda negativo y un pedido que falla no deja stock apartado.

## Dudas abiertas

- Ninguna. Todas las dudas de esta iteración quedaron resueltas en «Decisiones
  resueltas».
