# UML 002 — Lectura pública paginada de productos publicados

Diagramas del desarrollo descrito en `spec.md`, `plan.md` y `tasks.md` de este
directorio, alineados con las convenciones de `catalog-api` (`AGENTS.md`: Java 25 /
Spring Boot 4.1, paquete `com.friendlyeshop.catalog`, arquitectura **Layered** con
extensiones `mapper`, `util` y `client/storage`). Prosa en español; diagramas en
Mermaid con los nombres previstos de clases, rutas y variables. Los controladores
dependen solo de servicios y nunca importan `repository/` ni `client/`.

## 1. Contexto

`catalog-api` es la fuente de verdad de productos, precios, stock e imágenes. Hasta
la spec 001 solo ofrecía lecturas acotadas al dueño para el panel. Este corte agrega
una lectura pública de solo lectura, `GET /catalog/publication`, que el
navegador del Storefront (`fes-client-web`) consume sin sesión ni credenciales. La
respuesta se limita a productos `published`, no borrados, con dueño y con
`stock` no informado o mayor a 0, de todos los vendedores, con filtro opcional por nombre y paginación de
24 por defecto. El acceso desde el navegador se habilita con CORS restringido a
`/catalog/**` y a los orígenes de la variable `BROWSER_ORIGINS`. El contrato del
panel y los endpoints de Actuator no cambian.

## 2. Diagrama de componentes

```mermaid
flowchart TB
  subgraph storefront [fes-client-web]
    Browser["Navegador del visitante<br/>Origin: valor de BROWSER_ORIGINS"]
  end

  subgraph catalogApi [catalog-api · com.friendlyeshop.catalog]
    subgraph configPkg [config]
      CorsProps["CorsProperties<br/>fes.cors.browser-origins<br/>(BROWSER_ORIGINS)"]
      CorsCfg["CorsConfig<br/>WebMvcConfigurer<br/>/catalog/** · GET · sin credenciales"]
      CatalogProps[CatalogProperties<br/>fes.catalog.public-base-url]
      Actuator["Actuator<br/>/actuator/health · info · prometheus<br/>(sin CORS)"]
    end

    subgraph controllers [controller]
      PubCtrl["PublicationController<br/>/publication: lectura y publicación"]
      PanelCtrl["ProductController<br/>GET /catalog/products?ownerAccountId=<br/>(sin cambios)"]
    end

    subgraph services [service]
      PubSvc["PublicationService<br/>list / publish / unpublish / publishCatalog"]
      PanelSvc["ProductService<br/>(sin cambios)"]
    end

    subgraph repos [repository]
      ProdRepo["ProductRepository<br/>+ findPublished<br/>+ findPublishedByName"]
      ImgRepo["ProductImageRepository<br/>+ findByProductIdIn"]
    end

    subgraph modelPkg [model]
      Product[Product<br/>@Entity]
      ProductImage[ProductImage<br/>@Entity]
      PageDto["PublicationPageResponse<br/>model/dto"]
      ItemDto["PublicationResponse<br/>model/dto"]
      ImgDto["ProductImageResponse<br/>model/dto"]
    end

    Mapper["ProductResponseMapper<br/>+ toPublication<br/>+ toPublicationPage"]
    Url["UrlUtils.join(baseUrl, path)"]
    Errors["GlobalExceptionHandler<br/>(sin cambios)"]
  end

  subgraph data [Infraestructura]
    DB[(PostgreSQL<br/>base catalog · tabla products)]
    Minio[(MinIO / S3<br/>imágenes)]
  end

  Browser -->|"GET /catalog/publication?name=&page=&size="| CorsCfg
  CorsCfg --> PubCtrl
  PanelCtrl --> PanelSvc
  PubCtrl --> PubSvc
  PubSvc --> ProdRepo
  PubSvc --> ImgRepo
  PubSvc --> Mapper
  ProdRepo --> Product
  ImgRepo --> ProductImage
  ProdRepo --> DB
  ImgRepo --> DB
  Mapper --> ItemDto
  Mapper --> ImgDto
  Mapper --> Url
  Mapper --> CatalogProps
  Mapper --> PageDto
  CorsCfg --> CorsProps
  Browser -.->|"GET /catalog/images/{imageId} (URL en images[].url)"| Minio
```

Notas:

- `CorsConfig` es el único punto que habilita el navegador; el mapping cubre solo
  `/catalog/**`, por lo que `/actuator/**` y el resto de las rutas quedan fuera.
- `PublicationService` es el único dueño de los casos de publicación: la lectura
  pública (`list`), la publicación/despublicación de un producto y la publicación
  del catálogo. No toca imágenes binarias ni MinIO: solo arma URLs públicas con
  `ProductResponseMapper` + `UrlUtils`.
- El panel conserva su camino (`ProductController` → `ProductService`) sin
  modificaciones.

Variables de entorno relevantes de este corte: `BROWSER_ORIGINS` (orígenes CORS) y,
heredada de la spec 001, `PUBLIC_API_BASE_URL` (base de `images[].url`). El valor de
`BROWSER_ORIGINS` por entorno pertenece a `fes-infra`.

## 3. Diagrama de clases (Layered)

Se muestran relaciones arquitectónicas y contratos de frontera; no se reproduce cada
import ni variable local.

```mermaid
classDiagram
  direction TB

  class PublicationController {
    -PublicationService publicationService
    +list(name String, page String, size String) PublicationPageResponse
    +publishCatalog(ownerAccountId UUID, request PublishCatalogRequest) PublishCatalogResponse
    +publish(id UUID, ownerAccountId UUID) ProductResponse
    +unpublish(id UUID, ownerAccountId UUID) ProductResponse
  }

  class PublicationService {
    -ProductRepository productRepository
    -ProductImageRepository productImageRepository
    -ProductResponseMapper responseMapper
    -ProductLookup productLookup
    -int DEFAULT_PAGE = 1
    -int DEFAULT_SIZE = 24
    -int MAX_SIZE = 100
    -Sort SORT
    +list(name String, page String, size String) PublicationPageResponse
    +publish(ownerAccountId UUID, id UUID) ProductResponse
    +unpublish(ownerAccountId UUID, id UUID) ProductResponse
    +publishCatalog(ownerAccountId UUID, request PublishCatalogRequest) PublishCatalogResponse
    -normalizePage(raw String) int
    -normalizeSize(raw String) int
    -normalizeName(raw String) String
    -findPage(name String, page int, size int) Page~Product~
    -imagesByProductId(List~Product~ products) Map
    -publishOwnerlessProducts(ownerAccountId UUID, request PublishCatalogRequest) int
    -publishAccountDrafts(ownerAccountId UUID) int
  }

  class ProductRepository {
    <<interface>>
    +findPublished(stage ProductStage, minimumStock int, pageable Pageable) Page~Product~
    +findPublishedByName(stage ProductStage, minimumStock int, name String, pageable Pageable) Page~Product~
    +findByOwnerAccountIdAndDeletedAtIsNull(ownerAccountId UUID, pageable Pageable) Page~Product~
    +findByIdAndOwnerAccountIdAndDeletedAtIsNull(id UUID, ownerAccountId UUID) Optional~Product~
  }

  class ProductImageRepository {
    <<interface>>
    +findByProductIdIn(productIds Collection~UUID~) List~ProductImage~
    +findByProductId(productId UUID) List~ProductImage~
  }

  class ProductResponseMapper {
    -CatalogProperties properties
    +toResponse(product Product, images List~ProductImage~) ProductResponse
    +toPublication(product Product, images List~ProductImage~) PublicationResponse
    +toPublicationPage(products List~Product~, imagesByProductId Map, page int, size int, totalItems long, totalPages int) PublicationPageResponse
    -toImageResponse(image ProductImage) ProductImageResponse
    -publicUrl(imageId UUID) String
  }

  class PublicationResponse {
    <<record>>
    +UUID id
    +String name
    +BigDecimal price
    +Currency currency
    +Integer stock
    +List~ProductImageResponse~ images
  }

  class PublicationPageResponse {
    <<record>>
    +List~PublicationResponse~ items
    +int page
    +int size
    +long totalItems
    +int totalPages
  }

  class ProductImageResponse {
    <<record>>
    +UUID id
    +String url
  }

  class Product {
    <<entity>>
    +UUID id
    +UUID ownerAccountId
    +String name
    +BigDecimal price
    +Currency currency
    +Integer stock
    +ProductStage stage
    +Instant deletedAt
    +Instant createdAt
  }

  class ProductImage {
    <<entity>>
    +UUID id
    +UUID productId
    +String objectKey
    +String contentType
  }

  class CorsConfig {
    -CorsProperties properties
    +addCorsMappings(registry CorsRegistry) void
  }

  class CorsProperties {
    <<record>>
    +List~String~ browserOrigins
  }

  class CatalogProperties {
    <<record>>
    +String publicBaseUrl
  }

  class UrlUtils {
    <<utility>>
    +join(baseUrl String, path String) String
  }

  PublicationController --> PublicationService
  PublicationService --> ProductRepository
  PublicationService --> ProductImageRepository
  PublicationService --> ProductResponseMapper
  PublicationService --> ProductLookup
  ProductResponseMapper --> PublicationPageResponse
  ProductResponseMapper --> PublicationResponse
  ProductResponseMapper --> ProductImageResponse
  ProductResponseMapper --> UrlUtils
  ProductResponseMapper --> CatalogProperties
  ProductRepository ..> Product
  ProductImageRepository ..> ProductImage
  ProductResponseMapper ..> Product
  ProductResponseMapper ..> ProductImage
  CorsConfig --> CorsProperties
```

Contrato JSON de la lectura pública:

```json
{
  "items": [
    {
      "id": "uuid",
      "name": "Café molido 500 g",
      "price": 1234.5,
      "currency": "ARS",
      "stock": 7,
      "images": [{ "id": "uuid", "url": "https://host/catalog/images/uuid" }]
    }
  ],
  "page": 1,
  "size": 24,
  "totalItems": 57,
  "totalPages": 3
}
```

## 4. Secuencia — lectura pública con filtro y página fuera de rango

Flujo principal: el Storefront pide la página 9 de un catálogo que solo tiene 2
páginas; el servicio la ajusta a la última disponible y devuelve los totales reales.

```mermaid
sequenceDiagram
  autonumber
  participant B as Navegador (Storefront)
  participant CFG as CorsConfig
  participant C as PublicationController
  participant S as PublicationService
  participant R as ProductRepository
  participant IR as ProductImageRepository
  participant M as ProductResponseMapper
  participant DB as PostgreSQL (catalog)

  B->>CFG: GET /catalog/publication?name=cafe&page=9&size=24<br/>Origin: https://tienda.example
  CFG->>CFG: origen ∈ fes.cors.browser-origins
  CFG->>C: acceso permitido
  C->>S: list("cafe", "9", "24")
  S->>S: size=24, page=9, name="cafe"
  S->>R: findPublishedByName(PUBLISHED, 0, "cafe", PageRequest(8, 24, createdAt desc, id asc))
  R->>DB: select ... where stage='published' and deleted_at is null<br/>and owner_account_id is not null<br/>and (stock is null or stock > 0)<br/>and lower(name) like '%cafe%' order by created_at desc, id asc limit 24 offset 192
  DB-->>R: 0 filas · totalElements 30 · totalPages 2
  R-->>S: Page (totalPages = 2)
  S->>S: page 9 supera totalPages 2 → reconsultar en 1
  S->>R: findPublishedByName(PUBLISHED, 0, "cafe", PageRequest(1, 24, ...))
  R->>DB: ... limit 24 offset 24
  DB-->>R: 6 filas · totalElements 30 · totalPages 2
  R-->>S: Page (6 productos)
  S->>IR: findByProductIdIn(ids de los 6 productos)
  IR->>DB: select ... from product_images where product_id in (...)
  DB-->>IR: imágenes
  IR-->>S: List<ProductImage>
  S->>M: toPublicationPage(productos, imágenesPorProducto, 2, 24, 30, 2)
  M-->>S: PublicationPageResponse
  S-->>C: respuesta con page=2
  C-->>B: 200 application/json<br/>Access-Control-Allow-Origin: https://tienda.example
```

Casos cubiertos por el mismo flujo:

- `name` ausente o en blanco → `findPublished` en lugar de `findPublishedByName`.
- `totalItems` 0 → `items` vacío, `totalItems` 0, `totalPages` 0, sin reconsultar.
- `size` 150 → se aplica 100; `page` `abc` o `0` → se aplica 1.
- Producto publicado sin dueño, borrado, en borrador o con stock cero/negativo nunca
  aparece: queda filtrado en la consulta SQL. El stock no informado sí se muestra.

## 5. Secuencia — verificación previa CORS y origen no permitido

```mermaid
sequenceDiagram
  autonumber
  participant B as Navegador (Storefront)
  participant CFG as CorsConfig (/catalog/**)
  participant C as PublicationController
  participant A as Actuator (/actuator/**)

  rect rgb(232, 244, 233)
    B->>CFG: OPTIONS /catalog/publication<br/>Origin: https://tienda.example<br/>Access-Control-Request-Method: GET
    CFG-->>B: 200 · Access-Control-Allow-Origin: https://tienda.example<br/>Access-Control-Allow-Methods: GET<br/>(sin Access-Control-Allow-Credentials)
    B->>CFG: GET /catalog/publication
    CFG->>C: solicitud autorizada
    C-->>B: 200 JSON · Access-Control-Allow-Origin: https://tienda.example
  end

  rect rgb(250, 235, 235)
    B->>CFG: GET /catalog/publication<br/>Origin: https://sitio-ajeno.example
    CFG->>C: solicitud procesada
    C-->>B: 200 JSON · sin cabeceras CORS
    Note over B: el navegador bloquea la lectura<br/>y no es un error del servidor
  end

  rect rgb(240, 240, 248)
    B->>A: GET /actuator/health
    A-->>B: 200 JSON · sin cabeceras CORS
  end
```

Con `BROWSER_ORIGINS` vacía o ausente, `CorsProperties.browserOrigins` queda en lista
vacía, el mapping no autoriza ningún origen y el servicio arranca sin fallar; las
respuestas de datos son idénticas pero sin cabeceras CORS.

## 6. Flujo — normalización de `page` y `size`

```mermaid
flowchart TD
  A["Llega page / size como String<br/>(puede faltar o no ser numérico)"] --> B{"¿size nulo,<br/>no numérico o menor a 1?"}
  B -->|sí| C["size = 24 (DEFAULT_SIZE)"]
  B -->|no| D{"¿size mayor a 100?"}
  D -->|sí| E["size = 100 (MAX_SIZE)"]
  D -->|no| F["size = valor recibido"]
  C --> G
  E --> G
  F --> G
  G{"¿page nulo,<br/>no numérico o menor a 1?"} -->|sí| H["page = 1 (DEFAULT_PAGE)"]
  G -->|no| I["page = valor recibido"]
  H --> J["Consultar con PageRequest.of(page - 1, size,<br/>createdAt desc, id asc)"]
  I --> J
  J --> K{"¿totalItems = 0?"}
  K -->|sí| L["items vacío · totalItems 0 · totalPages 0<br/>page y size aplicados"]
  K -->|no| M{"¿page mayor a totalPages?"}
  M -->|sí| N["Reconsultar en totalPages - 1<br/>page = totalPages · totales reales"]
  M -->|no| O["Responder la página solicitada<br/>con totales reales"]
```

El parseo usa `String` en el controlador justamente para que un valor no numérico no
produzca 400, y `NumberFormatException` se captura solo en la conversión, sin ocultar
otros errores.
