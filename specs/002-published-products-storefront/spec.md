# Spec 002 — Lectura pública paginada de productos publicados para el Storefront

## Contexto y objetivo

La spec 001 dejó `catalog-api` como fuente de verdad de productos, precios, stock e
imágenes, con una lectura acotada al dueño (`GET /catalog/products?ownerAccountId=…`)
para el panel y sin ninguna lectura global. El Storefront público necesita mostrar
todos los productos que los vendedores publicaron, en una sola grilla paginada, con
un buscador por nombre siempre visible en el header. Este corte agrega esa lectura
pública: devuelve únicamente productos en etapa publicada, no borrados, con dueño y
con stock mayor a cero o sin stock informado, de todos los dueños y sin agrupar, con
filtro opcional por
nombre y paginación, y habilita al navegador del Storefront a consumirla mediante
CORS. El objetivo es que el catálogo sea visible para cualquier visitante sin
sesión, sin exponer datos internos del vendedor (dueño ni etapa) y sin tocar el
contrato que ya usa el panel.

## Usuarios / actores

- **Visitante de la tienda**: persona sin sesión que recorre la grilla de productos
  publicados y busca por nombre desde el header.
- **Storefront (`fes-client-web`)**: aplicación de navegador que consume la lectura
  pública, pagina de 24 en 24 y refleja el término y la página en la URL.
- **Vendedor**: dueño de los productos publicados que se muestran; no opera nada en
  este corte.
- **Operación/Infra**: entrega el origen del Storefront en la variable
  `BROWSER_ORIGINS`; no gestiona el contenido del catálogo.

## Historias de usuario

- H1: Como visitante quiero ver en una grilla todos los productos publicados de
  todos los vendedores para descubrir qué se ofrece en la tienda.
- H2: Como visitante quiero buscar productos por nombre para encontrar rápido lo
  que me interesa sin recorrer toda la grilla.
- H3: Como visitante quiero recorrer los resultados por páginas de 24 productos para
  navegar catálogos grandes sin cargas enormes.
- H4: Como visitante quiero que la grilla no muestre productos agotados ni retirados
  para no intentar comprar algo que no está disponible.
- H5: Como responsable del Storefront quiero que el navegador pueda consultar el
  catálogo desde el origen de la tienda sin exponer credenciales ni datos internos
  del vendedor.

## Requisitos funcionales (criterios de aceptación en EARS)

- RF-1: EL SISTEMA expondrá la lectura pública de productos publicados en
  `GET /catalog/publish`, accesible sin sesión ni credenciales.
- RF-2: CUANDO se consulte `GET /catalog/publish` sin filtro por nombre,
  EL SISTEMA devolverá los productos de todos los dueños, sin agrupar por dueño.
- RF-3: EL SISTEMA incluirá en la lectura pública únicamente productos en etapa
  publicada.
- RF-4: EL SISTEMA excluirá de la lectura pública los productos borrados aunque
  estén en etapa publicada.
- RF-5: EL SISTEMA incluirá en la lectura pública los productos cuyo stock no esté
  informado o sea mayor a cero.
- RF-6: CUANDO se consulte `GET /catalog/publish` con el parámetro
  `name`, EL SISTEMA devolverá únicamente los productos cuyo nombre contenga ese
  texto, sin distinguir mayúsculas de minúsculas.
- RF-7: EL SISTEMA tratará como significativos los acentos y demás signos
  diacríticos del filtro `name`.
- RF-8: CUANDO `name` llegue vacío o compuesto solo por espacios, EL SISTEMA no
  aplicará el filtro por nombre.
- RF-9: CUANDO la consulta no indique `page`, EL SISTEMA usará la página 1.
- RF-10: EL SISTEMA interpretará `page` como un número de página que empieza en 1.
- RF-11: CUANDO la consulta no indique `size`, EL SISTEMA usará 24 productos por
  página.
- RF-12: SI se solicita un `size` mayor a 100, ENTONCES EL SISTEMA ajustará el
  `size` a 100 sin rechazar la consulta.
- RF-13: SI `page` o `size` llegan con un valor menor a 1 o no numérico, ENTONCES EL
  SISTEMA aplicará el valor por defecto de cada parámetro sin rechazar la consulta.
- RF-14: CUANDO la consulta se resuelva, EL SISTEMA devolverá una respuesta con los
  campos `items`, `page`, `size`, `totalItems` y `totalPages`.
- RF-15: EL SISTEMA devolverá cada elemento de `items` con los campos `id`, `name`,
  `price`, `currency`, `stock` e `images`.
- RF-16: EL SISTEMA devolverá cada elemento de `images` con los campos `id` y `url`.
- RF-17: EL SISTEMA no expondrá en la respuesta el identificador del dueño ni la
  etapa del producto.
- RF-18: EL SISTEMA entregará en `url` de cada imagen la misma URL pública de imagen
  que ya sirve el servicio, sin exigir credenciales de almacenamiento al consumidor.
- RF-19: CUANDO un producto no tenga imágenes, EL SISTEMA devolverá su lista
  `images` vacía.
- RF-20: EL SISTEMA calculará `totalItems` como la cantidad total de productos que
  cumplen el filtro por nombre y las condiciones de publicación, independientemente
  de la página solicitada.
- RF-21: EL SISTEMA calculará `totalPages` como la cantidad de páginas necesarias
  para recorrer `totalItems` con el `size` aplicado.
- RF-22: CUANDO no existan productos que cumplan las condiciones, EL SISTEMA
  devolverá `items` vacío, `totalItems` igual a 0 y `totalPages` igual a 0.
- RF-23: EL SISTEMA devolverá los productos ordenados por fecha de creación
  descendente, del más reciente al más antiguo, con un desempate determinista que
  mantenga el orden estable entre páginas consecutivas.
- RF-24: CUANDO el navegador del Storefront envíe un `GET` a cualquier ruta bajo
  `/catalog/**` desde un origen incluido en `BROWSER_ORIGINS`, EL SISTEMA responderá
  permitiendo ese origen.
- RF-25: CUANDO el navegador del Storefront envíe un `OPTIONS` a cualquier ruta bajo
  `/catalog/**` desde un origen incluido en `BROWSER_ORIGINS`, EL SISTEMA responderá
  la verificación previa permitiendo ese origen y el método `GET`.
- RF-26: EL SISTEMA no permitirá el envío de credenciales en las solicitudes CORS de
  la tienda.
- RF-27: SI el origen de una solicitud no está incluido en `BROWSER_ORIGINS`,
  ENTONCES EL SISTEMA no lo permitirá en la respuesta CORS.
- RF-28: EL SISTEMA aplicará CORS únicamente sobre `/catalog/**` y no sobre las
  rutas de Actuator ni ninguna otra.
- RF-29: SI `BROWSER_ORIGINS` está vacía o ausente, ENTONCES EL SISTEMA arrancará
  sin permitir ningún origen de navegador y sin fallar el arranque.
- RF-30: EL SISTEMA conservará sin cambios el contrato de administración
  `GET /catalog/products?ownerAccountId=…`, el alta, la edición y el borrado de
  productos.
- RF-35: EL SISTEMA expondrá la publicación y despublicación de un producto en
  `POST /catalog/publish/products/{id}` y `POST /catalog/publish/products/{id}/unpublish`,
  y la publicación de catálogo en `POST /catalog/publish`.
- RF-36: EL SISTEMA publicará todo controller bajo el prefijo `/catalog`, de modo que
  cada controller declare solo el segmento propio de su recurso.
- RF-37: EL SISTEMA mantendrá los endpoints de Actuator fuera del prefijo `/catalog`.
- RF-31: EL SISTEMA no modificará el estado de ningún producto a causa de la
  lectura pública.
- RF-32: EL SISTEMA conservará los endpoints de Actuator, health checks y métricas
  usados por Kubernetes.
- RF-33: SI un producto en etapa publicada no tiene dueño, ENTONCES EL SISTEMA lo
  excluirá de la lectura pública.
- RF-34: SI `page` supera `totalPages` y existen productos que cumplen el filtro,
  ENTONCES EL SISTEMA devolverá la última página disponible junto con los
  `totalItems` y `totalPages` reales.

## Requisitos no funcionales

- NFR-1: La lectura pública no requerirá sesión, token ni credenciales de
  almacenamiento.
- NFR-2: El único cambio de configuración de entorno será la nueva variable
  `BROWSER_ORIGINS`; las variables existentes conservan su significado.
- NFR-3: EL SISTEMA resolverá la lectura pública únicamente con datos de la base
  `catalog` y no leerá ni escribirá tablas de otros dominios.
- NFR-4: Los mensajes visibles al usuario estarán en español y el contrato, los
  nombres y la documentación técnica en inglés.
- NFR-5: EL SISTEMA expondrá el nuevo contrato bajo `/catalog` y mantendrá las rutas,
  health checks y métricas que usa Kubernetes.
- NFR-6: El formato de la URL pública de imagen será el mismo que ya entrega el
  servicio en sus lecturas actuales, para que el Storefront no necesite lógica
  distinta según el endpoint que consume.
- NFR-7: La lectura pública será de solo lectura y no alterará la disponibilidad ni
  el comportamiento del contrato del panel.

## Casos límite

- Catálogo sin productos publicados: `items` vacío, `totalItems` 0 y `totalPages` 0,
  sin error.
- Filtro `name` sin coincidencias: `items` vacío, `totalItems` 0, sin error.
- Filtro `name` con distinto uso de mayúsculas: devuelve las mismas coincidencias.
- Filtro `name` con acentos distintos del nombre almacenado: no coincide.
- Filtro `name` vacío o solo con espacios: se comporta como consulta sin filtro.
- Producto publicado con stock no informado (nulo): incluido en la lectura pública
  (RF-5).
- Producto publicado con stock igual a cero: excluido de la lectura pública.
- Producto publicado con stock menor a cero: excluido de la lectura pública.
- Producto borrado lógicamente: excluido aunque conserve la etapa publicada.
- Producto en borrador o despublicado: excluido de la lectura pública.
- Producto publicado sin imágenes: se devuelve con `images` vacío.
- Producto publicado sin dueño: se excluye de la lectura pública.
- Página solicitada más allá de la última disponible: se ajusta a la última página
  disponible y se devuelven los totales reales.
- `size` mayor a 100: se ajusta a 100.
- `page` o `size` menores a 1 o no numéricos: se aplica el valor por defecto de cada
  uno.
- Productos con la misma fecha de creación: el desempate mantiene un orden estable
  entre páginas.
- `BROWSER_ORIGINS` vacía o ausente: arranca sin orígenes de navegador permitidos.
- Origen no incluido en `BROWSER_ORIGINS`: el servidor no lo autoriza en la respuesta
  CORS y el navegador bloquea la lectura; no es un error del servidor.
- Solicitud de verificación previa (`OPTIONS`) del Storefront: respondida sin exigir
  credenciales.
- Cambios de publicación entre dos páginas consecutivas (un producto se publica,
  despublica o agota stock en el medio): el recorrido puede repetir u omitir ese
  producto y no es un error.
- Consulta desde una herramienta sin origen de navegador: la respuesta de datos es la
  misma, sin cabeceras CORS de tienda.

## Fuera de alcance

- La grilla, el buscador del header, la paginación visual y los estados de carga,
  vacío y error del Storefront (`fes-client-web`).
- La entrega y el valor de `BROWSER_ORIGINS` en los overlays de Minikube y Hostinger
  (`fes-infra`).
- La administración de productos y el contrato que consume el panel
  (`GET /catalog/products?ownerAccountId=…`, alta, edición, publicación,
  despublicación y borrado).
- La vista de detalle de un producto.
- El ordenamiento elegido por el visitante, las categorías, las facetas, los filtros
  por precio o vendedor y la búsqueda difusa o por acentos.
- El carrito, el checkout y la compra.
- Las migraciones de base, los cambios de esquema y los datos de ejemplo.
- La autenticación, el login y la validación de sesión.
- La publicación de eventos de catálogo por RabbitMQ.
- El almacenamiento en caché, CDN o agregados de catálogo.
- La transformación, el redimensionado o las miniaturas de imágenes.
- La definición del esquema físico, las tablas, las clases, los paquetes y todo
  detalle técnico (pertenece al plan).

## Criterios de finalización

- Todos los RF con test automatizado en verde, incluidos el filtrado, la paginación,
  los totales, el orden y las respuestas CORS.
- `./mvnw verify` en verde, incluido Checkstyle.
- Demo manual: consultar la lectura pública con y sin `name`, recorrer al menos dos
  páginas de 24 y verificar que solo aparecen productos publicados, con dueño, no
  borrados y con stock mayor a cero o sin stock informado.
- Demo manual de paginación: una consulta con `page` mayor a `totalPages` devuelve la
  última página disponible y una consulta con `size` mayor a 100 devuelve como
  máximo 100 elementos.
- Demo manual de CORS: una solicitud con el origen del Storefront recibe la
  autorización y una solicitud con un origen ajeno no la recibe.
- El contrato del panel y los endpoints de Actuator responden igual que antes del
  cambio.
