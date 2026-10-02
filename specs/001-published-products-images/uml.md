# UML 001 — Productos con etapa y dueño, e imágenes en MinIO

Diagramas alineados con `spec.md`, `plan.md` y `tasks.md`, y con las
convenciones de `catalog-api` (`AGENTS.md`: Java 25 / Spring Boot 4.1, paquete
`com.friendlyeshop.catalog`, arquitectura **Layered** con extensiones
`client/storage` y `mapper`). Prosa en español; diagramas en Mermaid con los
nombres reales previstos.

## 1. Contexto

`catalog-api` es la fuente de verdad de productos, precios, stock e imágenes. El
panel llega a través de la frontera `panel-api`, que valida la sesión y fija el
`ownerAccountId`; este servicio no autentica. Las imágenes se suben dentro del
alta/edición del producto (`multipart/form-data` en `POST`/`PUT
/catalog/products`) y se guardan en MinIO; se leen por una URL pública servida por
este servicio (`GET /catalog/images/{imageId}`), de modo que el consumidor nunca
usa credenciales de MinIO. El borrado es lógico y conserva los objetos.

## 2. Diagrama de componentes

```mermaid
flowchart TB
  subgraph panel [panel-web / panel-api]
    Frontier["Frontera /panel/catalog<br/>fija ownerAccountId"]
  end

  subgraph catalogApi [catalog-api]
    subgraph controllers [controller]
      ProductCtrl[ProductController<br/>/catalog/products]
      ImageCtrl[ProductImageController<br/>/catalog/images/{id}]
      HealthCtrl[CatalogController<br/>GET /catalog]
    end

    subgraph services [service]
      ProductSvc[ProductService]
      PublishSvc[ProductPublicationService]
    end

    subgraph repos [repository]
      ProductRepo[ProductRepository]
      ImageRepo[ProductImageRepository]
    end

    subgraph domain [model]
      Product[Product]
      ProductImage[ProductImage]
      Stage[ProductStage]
      Curr[Currency]
    end

    subgraph storage [client/storage]
      Storage[ImageStorage]
      Minio[MinioImageStorage]
    end

    Mapper[ProductResponseMapper]
    Errors[GlobalExceptionHandler]
    Config["config<br/>StorageProperties · CatalogProperties · StorageConfig"]
  end

  subgraph external [Infraestructura]
    MinioS3[(MinIO / S3<br/>bucket product-images)]
    DB[(PostgreSQL<br/>base catalog)]
  end

  Frontier -->|"POST/PUT multipart + ownerAccountId"| ProductCtrl
  Frontier -->|"publish/unpublish/publish catalog"| ProductCtrl
  Frontier -->|"GET list"| ProductCtrl
  ImageCtrl --> Storage
  ImageCtrl --> ImageRepo
  ProductCtrl --> ProductSvc
  ProductCtrl --> PublishSvc
  ProductSvc --> ProductRepo
  ProductSvc --> ImageRepo
  ProductSvc --> Storage
  ProductSvc --> Mapper
  PublishSvc --> ProductRepo
  PublishSvc --> ImageRepo
  PublishSvc --> Mapper
  Storage -.implementa.-> Minio
  Minio -->|S3_*| MinioS3
  ProductRepo --> DB
  ImageRepo --> DB
  Mapper --> CatalogProps[CatalogProperties<br/>PUBLIC_API_BASE_URL]
  Config --> Minio
```

Variables de entorno relevantes: `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`,
`S3_SECRET_KEY` (almacenamiento) y `PUBLIC_API_BASE_URL` (base de la URL pública
de imágenes). El bucket `product-images` y las credenciales pertenecen a `infra`.

## 3. Diagrama de clases (Layered)

Se muestran relaciones arquitectónicas relevantes; no se reproduce cada import ni
variable local. Los DTOs y errores se citan como contratos de frontera.

```mermaid
classDiagram
  direction TB

  class ProductController {
    -ProductService productService
    -ProductPublicationService publicationService
    +list(ownerAccountId) List~ProductResponse~
    +create(ownerAccountId, form) ProductResponse
    +update(ownerAccountId, id, form) ProductResponse
    +deleteLogically(ownerAccountId, id) void
    +publish(ownerAccountId, id) ProductResponse
    +unpublish(ownerAccountId, id) ProductResponse
    +publishCatalog(ownerAccountId, request) PublishCatalogResponse
  }

  class ProductImageController {
    -ImageStorage imageStorage
    +image(imageId) ResponseEntity~Resource~
  }

  class ProductService {
    -ProductRepository productRepository
    -ProductImageRepository imageRepository
    -ImageStorage imageStorage
    -ProductResponseMapper mapper
    +list(ownerAccountId) List~ProductResponse~
    +create(ownerAccountId, form) ProductResponse
    +update(ownerAccountId, id, form) ProductResponse
    +deleteLogically(ownerAccountId, id) void
  }

  class ProductPublicationService {
    -ProductRepository productRepository
    -ProductImageRepository imageRepository
    -ProductResponseMapper mapper
    +publish(ownerAccountId, id) ProductResponse
    +unpublish(ownerAccountId, id) ProductResponse
    +publishCatalog(ownerAccountId, request) PublishCatalogResponse
  }

  class ProductRepository {
    <<interface>>
    +findByOwnerAccountIdAndDeletedAtIsNull(owner) List~Product~
    +findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, owner) Optional~Product~
    +findByOwnerAccountIdAndStageAndDeletedAtIsNull(owner, stage) List~Product~
  }

  class ProductImageRepository {
    <<interface>>
    +findByProductId(productId) List~ProductImage~
  }

  class Product {
    <<entity>>
    -UUID id
    -UUID ownerAccountId
    -String name
    -BigDecimal price
    -Currency currency
    -Integer stock
    -ProductStage stage
    -Instant deletedAt
    -long version
    +openNew(...)$ Product
    +update(...) void
    +publish() void
    +unpublish() void
    +takeOwnership(accountId) void
    +deleteLogically(now) void
    +belongsTo(accountId) boolean
  }

  class ProductImage {
    <<entity>>
    -UUID id
    -UUID productId
    -String objectKey
    -String contentType
    -Instant createdAt
  }

  class ProductStage {
    <<enum>>
    DRAFT
    PUBLISHED
  }
  class Currency {
    <<enum>>
    ARS
    USD
  }

  class ImageStorage {
    <<interface>>
    +store(productId, file) StoredImage
    +load(imageId) ImageContent
  }
  class MinioImageStorage {
    -MinioClient client
    -StorageProperties properties
    +store(productId, file) StoredImage
    +load(imageId) ImageContent
  }
  class StorageProperties {
    -String endpoint
    -String bucket
    -String accessKey
    -String secretKey
  }
  class CatalogProperties {
    -String publicBaseUrl
  }

  class ProductResponseMapper {
    -CatalogProperties catalogProperties
    +toResponse(product, images) ProductResponse
  }

  class ProductResponse {
    <<record>>
    +UUID id
    +UUID ownerAccountId
    +String name
    +BigDecimal price
    +Currency currency
    +Integer stock
    +ProductStage stage
    +List~ProductImageResponse~ images
  }
  class ProductImageResponse {
    <<record>>
    +UUID id
    +String url
  }
  class ProductForm {
    <<record>>
    +String name
    +BigDecimal price
    +Currency currency
    +Integer stock
    +List~MultipartFile~ images
  }
  class PublishCatalogRequest {
    <<record>>
    +List~ProductSeed~ products
  }
  class PublishCatalogResponse {
    <<record>>
    +int published
  }
  class ErrorResponse {
    <<record>>
    +String error
    +String message
  }

  class GlobalExceptionHandler
  class ProductNotFoundException
  class InvalidProductException
  class StorageUnavailableException

  ProductController --> ProductService
  ProductController --> ProductPublicationService
  ProductImageController --> ImageStorage
  ProductService --> ProductRepository
  ProductService --> ProductImageRepository
  ProductService --> ImageStorage
  ProductService --> ProductResponseMapper
  ProductPublicationService --> ProductRepository
  ProductPublicationService --> ProductImageRepository
  ProductPublicationService --> ProductResponseMapper
  ProductResponseMapper --> CatalogProperties
  ProductResponseMapper ..> ProductResponse
  ProductResponseMapper ..> ProductImageResponse
  ProductRepository ..> Product
  ProductImageRepository ..> ProductImage
  Product --> ProductStage
  Product --> Currency
  MinioImageStorage ..|> ImageStorage
  MinioImageStorage --> StorageProperties
  GlobalExceptionHandler --> ProductNotFoundException
  GlobalExceptionHandler --> InvalidProductException
  GlobalExceptionHandler --> StorageUnavailableException
  GlobalExceptionHandler ..> ErrorResponse
```

## 4. Secuencia — alta con imagen y publicación de un producto

Flujo principal: alta `multipart` con `ownerAccountId`, guardado en MinIO y
publicación. Cubre la validación de moneda y los rechazos de publicación.

```mermaid
sequenceDiagram
  actor Panel as panel-api (frontera)
  participant Ctrl as ProductController
  participant Svc as ProductService
  participant Repo as ProductRepository
  participant ImgRepo as ProductImageRepository
  participant Storage as MinioImageStorage
  participant Pub as ProductPublicationService
  participant Minio as MinIO

  Panel->>Ctrl: POST /catalog/products?ownerAccountId={cuenta}<br/>multipart: name, price, currency, stock?, images[]
  Ctrl->>Svc: create(ownerAccountId, form)
  alt moneda ausente o distinta de ARS/USD
    Svc-->>Ctrl: InvalidProductException
    Ctrl-->>Panel: 400 {"error":"datos_invalidos","message":"..."}
  else datos válidos
    Svc->>Repo: save(Product.openNew(owner, draft))
    loop cada imagen
      Svc->>Storage: store(productId, file)
      Storage->>Minio: PUT products/{productId}/{imageId}
      Minio-->>Storage: objectKey + contentType
      Svc->>ImgRepo: save(ProductImage)
    end
    Svc-->>Ctrl: ProductResponse (stage=draft, images[].url)
    Ctrl-->>Panel: 201 ProductResponse
  end

  Panel->>Ctrl: POST /catalog/products/{id}/publish?ownerAccountId={cuenta}
  Ctrl->>Pub: publish(ownerAccountId, id)
  Pub->>Repo: findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, owner)
  alt sin dueño, sin nombre o sin precio
    Pub-->>Ctrl: InvalidProductException (409)
    Ctrl-->>Panel: 409 {"error":"conflicto_de_publicacion","message":"..."}
  else publicable
    Pub->>Repo: save(product.publish())
    Pub-->>Ctrl: ProductResponse (stage=published)
    Ctrl-->>Panel: 200 ProductResponse
  end

  Panel->>Ctrl: GET /catalog/products?ownerAccountId={cuenta}
  Ctrl->>Svc: list(ownerAccountId)
  Svc->>Repo: findByOwnerAccountIdAndDeletedAtIsNull(owner)
  Svc->>ImgRepo: findByProductId(...)
  Svc-->>Ctrl: List~ProductResponse~ (draft + published, sin borrados)
  Ctrl-->>Panel: 200 [ProductResponse]
```

Notas de contrato:

- `ownerAccountId` viaja como **query param autoritativo**; un valor en el cuerpo se
  ignora. Las consultas y mutaciones siempre están acotadas a esa cuenta.
- Las imágenes se referencian como
  `{PUBLIC_API_BASE_URL}/catalog/images/{imageId}`, servidas por
  `ProductImageController`; el consumidor no recibe credenciales de MinIO.

## 5. Secuencia — `POST /catalog/publish`

Publica los productos de la sesión: los sin dueño incluidos en el cuerpo (creados
y tomados con el dueño de la cuenta) más los `draft` ya asociados a la cuenta. Sin
productos por publicar, responde 0 sin error.

```mermaid
sequenceDiagram
  actor Panel as panel-api (frontera)
  participant Ctrl as ProductController
  participant Pub as ProductPublicationService
  participant Repo as ProductRepository
  participant Minio as MinIO

  Panel->>Ctrl: POST /catalog/publish?ownerAccountId={cuenta}<br/>JSON {"products":[ sinDueño... ]}
  Ctrl->>Pub: publishCatalog(ownerAccountId, request)
  loop productos sin dueño del cuerpo
    Pub->>Repo: save(Product.openNew(...).takeOwnership(owner).publish())
  end
  Pub->>Repo: findByOwnerAccountIdAndStageAndDeletedAtIsNull(owner, DRAFT)
  loop drafts de la cuenta
    Pub->>Repo: save(product.publish())
  end
  alt sin productos por publicar
    Pub-->>Ctrl: PublishCatalogResponse(published=0)
  else hay productos
    Pub-->>Ctrl: PublishCatalogResponse(published=n)
  end
  Ctrl-->>Panel: 200 {"published":n}
```

## 6. Traducción de errores

| Origen | Resultado de `catalog-api` | RF |
|---|---|---|
| Falta `ownerAccountId` o moneda inválida | `400` `{"error":"datos_invalidos","message":"..."}` | RF-7, RF-21 |
| Producto inexistente o de otra cuenta | `404` `{"error":"producto_no_encontrado","message":"..."}` | RF-23 |
| Publicar sin dueño, nombre o precio | `409` `{"error":"conflicto_de_publicacion","message":"..."}` | RF-12 |
| Fallo de MinIO | `503` `{"error":"almacenamiento_no_disponible","message":"..."}` | caso límite |
