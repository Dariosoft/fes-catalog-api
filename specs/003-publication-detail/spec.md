# Spec 003 — Detalle público de publicación con descripción y tienda del vendedor

## Contexto y objetivo

La spec 002 dejó una grilla pública paginada con los productos publicados, pero el
visitante solo puede ver el resumen de cada producto y no puede abrir una
publicación concreta. Ahora cada producto puede llevar una descripción opcional de
hasta 1000 caracteres, y el visitante anónimo necesita consultar el detalle de una
publicación —nombre, precio, moneda, stock, descripción, imágenes y una referencia
al vendedor— desde una página propia, compartible y recargable. El detalle debe
respetar la misma noción de visibilidad que la grilla: solo se exponen publicaciones
publicadas, no eliminadas y con stock disponible o sin límite; si la publicación no
existe o no es visible, se informa como no encontrada. La tienda del vendedor
pertenece a otro servicio y esta spec solo consume su referencia pública. El objetivo
es que el visitante conozca mejor lo que se ofrece sin habilitar todavía ninguna
compra.

## Usuarios / actores

- **Visitante de la tienda**: persona sin sesión que abre el detalle de una
  publicación concreta desde la grilla o desde un enlace compartido.
- **Storefront (`fes-client-web`)**: aplicación de navegador que consume el detalle y
  construye la página, su URL propia, el compartir y la vuelta al catálogo
  conservando búsqueda, página y tamaño.
- **Vendedor**: dueño de la publicación; define su descripción y gestiona su tienda,
  que vive en otro servicio.
- **Servicio de cuentas (`account-api`)**: origen de la tienda del vendedor; no se
  implementa ni se persiste aquí.
- **Operación/Infra**: mantiene las rutas, health checks y métricas; no gestiona el
  contenido del catálogo.

## Historias de usuario

- H1: Como vendedor quiero agregar una descripción opcional a mis productos para dar
  más información al visitante.
- H2: Como visitante quiero abrir el detalle de una publicación para ver su
  descripción, precio, moneda, stock, imágenes y vendedor antes de decidir.
- H3: Como visitante quiero que el detalle tenga una dirección propia y estable para
  poder compartirla y recargarla.
- H4: Como visitante quiero que solo existan detalles de publicaciones visibles para
  no perder tiempo en productos retirados o no disponibles.
- H5: Como responsable del storefront quiero que el detalle exponga información
  pública sin revelar datos internos del vendedor.

## Requisitos funcionales (criterios de aceptación en EARS)

- RF-1: EL SISTEMA permitirá que cada producto tenga una descripción opcional.
- RF-2: CUANDO se cree o actualice un producto con una descripción, EL SISTEMA la
  almacenará asociada a ese producto.
- RF-3: CUANDO se cree o actualice un producto sin descripción o con una descripción
  vacía o compuesta solo por espacios, EL SISTEMA la tratará como ausente y lo
  almacenará sin descripción.
- RF-4: EL SISTEMA no aplicará una descripción ni un texto por defecto a los productos
  que no tengan descripción.
- RF-5: EL SISTEMA aceptará descripciones de hasta 1000 caracteres inclusive.
- RF-6: SI la descripción supera los 1000 caracteres, ENTONCES EL SISTEMA rechazará la
  operación sin persistir cambios.
- RF-7: CUANDO se publique un producto, EL SISTEMA conservará su descripción.
- RF-8: CUANDO se despublique un producto, EL SISTEMA conservará su descripción.
- RF-9: EL SISTEMA expondrá una consulta pública del detalle de una publicación
  concreta, accesible sin sesión ni credenciales.
- RF-10: CUANDO se consulte el detalle de una publicación visible, EL SISTEMA
  devolverá su identificador, nombre, precio, moneda, stock y descripción; si la
  descripción está ausente, la devolverá como ausente sin texto por defecto.
- RF-11: CUANDO se consulte el detalle de una publicación visible, EL SISTEMA
  devolverá sus imágenes.
- RF-12: EL SISTEMA entregará en el detalle la misma URL pública de imagen que ya
  sirve en la grilla, sin exigir credenciales de almacenamiento al consumidor.
- RF-13: CUANDO una publicación no tenga imágenes, EL SISTEMA devolverá su lista de
  imágenes vacía.
- RF-14: EL SISTEMA incluirá en el detalle el identificador público de la cuenta del
  vendedor como referencia pública, para que el storefront consulte su tienda; la
  referencia viaja siempre que la publicación sea visible, aunque el vendedor no haya
  configurado su tienda.
- RF-15: EL SISTEMA no expondrá en el detalle email, credenciales ni otros datos
  internos del vendedor.
- RF-16: EL SISTEMA no expondrá en el detalle la etapa del producto.
- RF-17: EL SISTEMA considerará visible una publicación que esté publicada, no
  eliminada y tenga stock mayor a cero o no tenga stock informado, consistente con la
  grilla actual; una publicación con stock igual a cero no será visible.
- RF-18: SI la publicación solicitada no existe, ENTONCES EL SISTEMA informará que no
  fue encontrada.
- RF-19: SI la publicación solicitada existe pero no es visible, ENTONCES EL SISTEMA
  informará que no fue encontrada sin revelar que existe.
- RF-20: EL SISTEMA identificará cada publicación de forma estable para que su
  detalle pueda consultarse de nuevo.
- RF-21: EL SISTEMA no modificará el estado de ninguna publicación a causa de la
  consulta de detalle.
- RF-22: EL SISTEMA conservará sin cambios la lectura pública paginada de
  publicaciones.
- RF-23: EL SISTEMA conservará sin cambios el contrato del panel y las operaciones de
  publicación y despublicación de productos.
- RF-24: EL SISTEMA conservará los endpoints de Actuator, health checks y métricas
  usados por Kubernetes.

## Requisitos no funcionales

- NFR-1: La consulta de detalle no requerirá sesión, token ni credenciales de
  almacenamiento.
- NFR-2: EL SISTEMA resolverá el detalle únicamente con datos de la base `catalog` y
  no leerá ni escribirá tablas de otros dominios.
- NFR-3: EL SISTEMA no gestionará ni persistirá la tienda del vendedor; esa
  información pertenece a otro servicio.
- NFR-4: Los mensajes visibles al usuario estarán en español y el contrato, los
  nombres y la documentación técnica en inglés.
- NFR-5: El detalle será de solo lectura y no alterará la disponibilidad ni el
  comportamiento de la grilla ni del panel.
- NFR-6: El formato de la URL pública de imagen será el mismo que ya entrega el
  servicio en la grilla, para que el storefront no necesite lógica distinta.
- NFR-7: EL SISTEMA conservará las rutas, health checks y métricas que usa
  Kubernetes.

## Casos límite

- Producto creado sin descripción: se permite, se publica y se consulta en el detalle;
  la descripción se devuelve como ausente, sin texto por defecto, y el texto
  alternativo lo muestra el storefront.
- Descripción de exactamente 1000 caracteres: aceptada.
- Descripción de 1001 caracteres: rechazada sin persistir cambios.
- Descripción vacía o compuesta solo por espacios: se trata como ausente; no se
  almacena texto, no se aplica un texto por defecto y el detalle la devuelve como
  ausente.
- Publicación visible sin imágenes: el detalle devuelve la lista de imágenes vacía.
- Publicación con stock no informado (nulo): visible, con el stock sin informar.
- Publicación con stock igual a cero: no visible, se informa como no encontrada,
  consistente con la grilla.
- Publicación con stock menor a cero: no visible, se informa como no encontrada.
- Publicación borrada lógicamente: no encontrada aunque conserve la etapa publicada.
- Publicación en borrador o despublicada: no encontrada.
- Publicación publicada sin dueño: no encontrada, consistente con la grilla.
- Detalle de una publicación inexistente: se informa como no encontrada.
- Detalle de una publicación no visible: se informa como no encontrada, sin revelar
  que existe.
- Publicación que se despublica o se agota entre la grilla y el detalle: el detalle
  pasa a informarse como no encontrado.
- Vendedor que no configuró su tienda: el detalle se sirve igualmente y siempre incluye
  el identificador público del vendedor; la ausencia de tienda la resuelve el
  storefront.
- Reintento o recarga del mismo detalle: mismo resultado, sin efectos secundarios.
- Descripción con acentos o caracteres especiales: se conserva y se devuelve tal cual
  se almacenó.

## Fuera de alcance

- La compra, la reserva de stock, los pedidos, los pagos y el checkout.
- La búsqueda por descripción.
- La edición de la publicación o de su descripción desde el storefront.
- La vista de detalle del storefront: su maquetado, su URL propia, el compartir, la
  recarga y la vuelta al catálogo conservando búsqueda, página y tamaño
  (`fes-client-web`); esta spec solo garantiza que el detalle sea consultable por un
  identificador estable.
- La gestión, creación y edición de la tienda del vendedor (`account-api`) y su
  contrato de lectura.
- La interfaz del panel, sus formularios y sus mensajes de error.
- Las categorías, las facetas, las reseñas y las valoraciones.
- El alta de imágenes, su transformación, redimensionado o miniaturas.
- La autenticación, el login y la validación de sesión.
- La publicación de eventos de catálogo por RabbitMQ.
- El almacenamiento en caché, CDN o agregados de catálogo.
- La definición del esquema físico, las migraciones, las tablas, las clases y todo
  detalle técnico (pertenece al plan).

## Criterios de finalización

- Todos los RF con test automatizado en verde, incluidos la descripción opcional, el
  límite de 1000 caracteres, el detalle de una publicación visible, la lista de
  imágenes, la referencia al vendedor y las respuestas de no encontrado.
- `./mvnw verify` en verde, incluido Checkstyle.
- Demo manual: crear un producto con descripción, publicarlo y consultar su detalle
  verificando nombre, precio, moneda, stock, descripción, imágenes y referencia al
  vendedor, incluida la referencia cuando el vendedor no configuró su tienda.
- Demo manual de descripción: un producto sin descripción, uno con descripción vacía o
  solo espacios y uno con 1000 caracteres se consultan correctamente; una descripción
  de 1001 caracteres se rechaza.
- Demo manual de visibilidad: una publicación en borrador, despublicada, borrada
  lógicamente o con stock igual a cero responde como no encontrada tanto como
  inexistente.
- Demo manual de no encontrado: una publicación inexistente y una no visible no se
  distinguen en la respuesta.
- La grilla pública, el contrato del panel y los endpoints de Actuator responden
  igual que antes del cambio.

## Decisiones resueltas

- ~~¿Una publicación con stock igual a cero debe tratarse como no encontrada, igual
  que en la grilla, o mostrarse en el detalle como visible sin stock?~~ Se trata como
  no encontrada: una publicación con stock igual a cero no es visible y el detalle
  responde como no encontrada, consistente con la grilla.
- ~~¿Cómo se representa la referencia pública al vendedor y qué ocurre cuando el
  vendedor no configuró su tienda?~~ Se expone el identificador público de la cuenta
  del vendedor como referencia pública para que el storefront consulte su tienda; el
  identificador viaja siempre que la publicación sea visible, aunque el vendedor no
  haya configurado su tienda, y la ausencia de tienda la resuelve el storefront. Nunca
  se exponen email, credenciales ni la etapa del producto.
- ~~Cuando la descripción está ausente o vacía, ¿el detalle debe devolverla vacía,
  omitirla o mostrar un texto alternativo?~~ Una descripción vacía o compuesta solo
  por espacios se trata como ausente: no se almacena texto por defecto y el detalle la
  devuelve como ausente; el texto alternativo lo muestra el storefront.
