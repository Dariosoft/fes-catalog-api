# Spec 001 — Productos con etapa y dueño, e imágenes en MinIO

## Contexto y objetivo

`catalog-api` debe ser la fuente de verdad de los productos del sistema. Hoy solo
existe un `GET /catalog` de comprobación que no persiste nada. Se necesita que
cada producto tenga una etapa (`draft`/`published`) persistida, un dueño
identificado por `owner_account_id` (UUID de la cuenta que fija la frontera) y sus
imágenes almacenadas en MinIO. Las imágenes llegan dentro del alta y la edición
del producto, no por un endpoint aparte, y se leen a través de una URL pública
servida por `catalog-api`, sin que el consumidor maneje credenciales de MinIO.
Con esto el panel podrá dar de alta, publicar y despublicar productos de una
cuenta, y un futuro catálogo de market podrá consumir los publicados. El objetivo
es que el servicio concentre la verdad de productos, precios y stock sin invadir
el borrador local del navegador, el login ni la frontera del panel.

## Usuarios / actores

- **Vendedor**: persona con sesión que, a través del panel, da de alta, edita,
  publica, despublica y elimina productos de su cuenta.
- **`panel-api`**: frontera que valida la sesión, fija el `ownerAccountId` y
  reenvía las operaciones; es el cliente directo de este servicio.
- **Market (futuro)**: consumidor de solo lectura de los productos publicados; su
  listado global es un desarrollo aparte, fuera de esta iteración.
- **Operación/Infra**: provee MinIO y el cableado de entorno; no gestiona el
  esquema de productos ni la publicación.

## Historias de usuario

- H1: Como vendedor quiero dar de alta y editar productos con sus imágenes para
  ofrecerlos en el catálogo.
- H2: Como vendedor quiero publicar un producto para que quede en etapa publicada.
- H3: Como vendedor quiero despublicar un producto para ocultarlo sin perderlo ni
  perder su dueño.
- H4: Como vendedor quiero publicar de una vez todos mis productos pendientes para
  no hacerlo uno por uno.
- H5: Como vendedor quiero eliminar un producto para dejar de ofrecerlo sin perder
  sus imágenes de forma irreversible.
- H6: Como consumidor quiero ver las imágenes de un producto por una URL pública
  para no necesitar credenciales de MinIO.

## Requisitos funcionales (criterios de aceptación en EARS)

- RF-1: EL SISTEMA persistirá cada producto con una etapa, `draft` o `published`.
- RF-2: CUANDO se cree un producto, EL SISTEMA lo almacenará en etapa `draft`.
- RF-3: EL SISTEMA identificará al dueño de un producto por su `owner_account_id`,
  el UUID de la cuenta, que puede ser nulo, sin persistir una columna booleana de
  propiedad.
- RF-4: CUANDO la frontera cree o actualice un producto indicando `ownerAccountId`,
  EL SISTEMA asociará el producto a esa cuenta.
- RF-5: CUANDO llegue un producto sin `ownerAccountId`, EL SISTEMA lo almacenará
  sin dueño.
- RF-6: CUANDO se consulte `GET /catalog/products` con `ownerAccountId`, EL SISTEMA
  devolverá únicamente los productos de esa cuenta en cualquier etapa (respuesta
  con la lista de productos de la cuenta).
- RF-7: SI `GET /catalog/products` llega sin `ownerAccountId`, ENTONCES EL SISTEMA
  rechazará la consulta en lugar de devolver los productos de todas las cuentas
  (respuesta de solicitud inválida).
- RF-8: CUANDO se actualice un producto mediante `PUT /catalog/products/{id}`, EL
  SISTEMA aplicará los cambios recibidos al producto indicado de la cuenta.
- RF-9: CUANDO se invoque `DELETE /catalog/products/{id}`, EL SISTEMA realizará un
  borrado lógico y dejará de exponer ese producto en sus lecturas.
- RF-10: EL SISTEMA conservará en MinIO los objetos de imagen de un producto
  borrado lógicamente.
- RF-11: CUANDO se invoque `POST /catalog/products/{id}/publish` sobre un producto
  `draft` con dueño, nombre y precio, EL SISTEMA cambiará su etapa a `published`.
- RF-12: SI se intenta publicar un producto sin dueño, sin nombre o sin precio,
  ENTONCES EL SISTEMA rechazará la operación sin cambiar la etapa.
- RF-13: EL SISTEMA tomará el dueño al publicar y no exigirá imágenes ni stock para
  publicar.
- RF-14: CUANDO se invoque `POST /catalog/products/{id}/unpublish` sobre un
  producto `published` de la cuenta, EL SISTEMA cambiará su etapa a `draft`
  conservando su `owner_account_id`.
- RF-15: CUANDO se invoque `POST /catalog/publish` con `ownerAccountId`, EL SISTEMA
  publicará los productos de la sesión: los productos sin dueño incluidos en el
  cuerpo, asignándoles ese dueño, y los productos `draft` ya asociados a esa cuenta.
- RF-16: CUANDO `POST /catalog/publish` no tenga productos por publicar, EL SISTEMA
  responderá con un conteo de 0 publicados y sin error.
- RF-17: CUANDO se cree o actualice un producto enviando imágenes dentro del cuerpo
  de `POST`/`PUT /catalog/products`, EL SISTEMA almacenará esas imágenes en MinIO y
  las asociará al producto.
- RF-18: EL SISTEMA no ofrecerá un endpoint de imágenes aparte del alta y la
  edición del producto.
- RF-19: CUANDO se lea un producto, EL SISTEMA expondrá la referencia de cada
  imagen como una URL pública servida por `catalog-api`, sin exigir credenciales de
  MinIO al consumidor.
- RF-20: EL SISTEMA aceptará únicamente `ARS` o `USD` como moneda de un producto.
- RF-21: SI falta la moneda o su valor no es `ARS` ni `USD`, ENTONCES EL SISTEMA
  rechazará la operación (respuesta de solicitud inválida, sin persistir cambios).
- RF-22: EL SISTEMA no aplicará una moneda por defecto; el valor por defecto `ARS`
  reside en el panel.
- RF-23: CUANDO una operación sobre un producto reciba un `ownerAccountId` distinto
  del dueño del producto, EL SISTEMA tratará el producto como inexistente.
- RF-24: EL SISTEMA conservará el endpoint `GET /catalog` y los endpoints de
  Actuator, health checks y métricas usados por Kubernetes.

## Requisitos no funcionales

- NFR-1: EL SISTEMA persistirá sus datos únicamente en la base `catalog`.
- NFR-2: EL SISTEMA no leerá ni escribirá tablas de `orders`, `payments` ni `panel`.
- NFR-3: Los mensajes visibles al usuario estarán en español.
- NFR-4: Las imágenes almacenadas permanecerán disponibles entre reinicios del
  servicio.
- NFR-5: EL SISTEMA mantendrá compatibilidad con las variables de entorno ya usadas
  por Kubernetes y recibirá las de almacenamiento definidas por infraestructura
  (`S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`).
- NFR-6: El consumidor nunca necesitará credenciales de MinIO para leer las
  imágenes de un producto.
- NFR-7: El contrato del servicio seguirá expuesto bajo `/catalog`, con nombres y
  documentación técnica en inglés.

## Casos límite

- Publicar o despublicar un producto que ya está en la etapa destino (repetición):
  no es error y la etapa no cambia.
- Publicar un producto sin imágenes: permitido.
- Publicar un producto sin stock: permitido.
- Publicar un producto sin nombre o sin precio: rechazado sin cambiar la etapa.
- Moneda ausente o distinta de `ARS`/`USD`: operación rechazada.
- Producto en etapa `published` sin dueño: combinación no alcanzable por el flujo
  normal.
- Fallo de MinIO al almacenar una imagen: la operación falla de forma explícita y no
  deja el producto a medias.
- `GET /catalog/products` sin `ownerAccountId`: consulta rechazada.
- Consulta con un `ownerAccountId` sin productos: lista vacía, no error.
- Dos publicaciones simultáneas sobre el mismo producto: una prevalece y la otra no
  corrompe el estado.
- Borrado lógico de un producto con imágenes: el producto deja de exponerse y los
  objetos permanecen en MinIO.
- `POST /catalog/publish` sin productos por publicar: responde con 0 publicados.
- Operación sobre un producto cuyo `ownerAccountId` no coincide: se trata como
  producto inexistente.

## Fuera de alcance

- El listado global de productos publicados y su lectura para el market (`GET
  /catalog` como catálogo público): es un desarrollo futuro aparte.
- La purga física de productos y la eliminación definitiva de sus objetos en MinIO.
- El borrador local del navegador y la gestión de sesión (`panel-web`).
- El login y la validación de `fes_session` (`account-api`, `panel-api`).
- La frontera de contrato `/panel/catalog` (`panel-api`).
- La interfaz de panel, sus botones, tooltips y modales de confirmación.
- La interfaz del market y la búsqueda o filtrado de productos publicados.
- La provisión del bucket, la ruta local de imágenes y el cableado de
  infraestructura (`infra`).
- La lectura o escritura de tablas de `orders`, `payments` o `panel`.
- El modelo de tienda/tenant: el `tenant_id` de la migración previa se descarta y
  no se usa.
- La transformación, el redimensionado, las miniaturas o la edición individual de
  imágenes.
- La publicación de eventos de catálogo por RabbitMQ.
- La definición del esquema físico, las migraciones, los nombres de clases y todo
  detalle técnico (pertenece al plan).

## Criterios de finalización

- Todos los RF con test automatizado en verde.
- `./mvnw verify` en verde, incluido Checkstyle.
- Demo manual del flujo principal: crear un producto con imagen, listar acotado a la
  cuenta, publicarlo, despublicarlo y verificar el borrado lógico.
- El esquema persistido queda alineado con lo descrito (ver plan) y Hibernate valida
  el arranque sin errores.

## Dudas abiertas

Ninguna.
