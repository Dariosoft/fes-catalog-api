# Plan 001 — Productos con etapa y dueño, e imágenes en MinIO

Diseño técnico as-built de `specs/001-published-products-images/spec.md` para
`catalog-api`. Respeta `AGENTS.md` y las skills que menciona:
`/spring-boot-project-creator` en su **opción Layered** (sin regenerar el proyecto
con Spring Initializr), `/spring-boot-layered-template` para ubicar clases que no
encajan en las carpetas básicas y `/clean-code-guard` como guardia de calidad.
Cubre los RF-1 … RF-25 de la spec. Este documento refleja **cómo quedó
implementada** la funcionalidad, no un plan previo.

## 1. Alcance y principios

- Capacidad dueña: **catálogo** (`com.friendlyeshop.catalog`), fuente de verdad de
  productos, precios, stock e imágenes. **(RF-1 … RF-25)**
- **Layered Architecture**: paquetes base `controller/`, `service/`, `repository/`,
  `model/`, `model/dto/`, `config/` y `exception/`, con dos extensiones concretas
  del `/spring-boot-layered-template`:
  - `client/storage/` para la integración con MinIO (protocolo S3).
  - `mapper/` para la conversión repetida entidad → respuesta.
- El dueño es `owner_account_id` (UUID de `Account.id` de `account-api`),
  **nullable**; no se persiste columna booleana de propiedad. La posesión se
  **infiere por la presencia o ausencia** de `owner_account_id`. El `tenant_id` de
  la migración previa **se descarta**. **(RF-3, RF-5)**
- Las imágenes viajan en el alta y la edición (`multipart/form-data` en
  `POST`/`PUT /catalog/products`); no hay endpoint de escritura de imágenes aparte.
  Se guardan en MinIO y se leen por una URL pública servida por `catalog-api` en
  `GET /catalog/images/{imageId}`. **(RF-17, RF-18, RF-19)**
- El borrado es lógico (`deleted_at`); los objetos de MinIO se conservan. La purga
  física queda fuera de alcance. **(RF-9, RF-10)**
- El listado de la cuenta se filtra opcionalmente por nombre (`name`, contains,
  ignorando mayúsculas) usando `Pageable.unpaged()`. **(RF-25)**
- Se conservan los endpoints de Actuator (health, info, prometheus) usados por
  Kubernetes; **no** se expone un endpoint de lectura global del catálogo publicado
  en esta iteración. **(RF-24)**
- No se tocan tablas de otros dominios; solo la base `catalog`. **(NFR-1, NFR-2)**

## 2. Estado as-built relevante

- Spring Boot 4.1 / Java 25, paquete `com.friendlyeshop.catalog`.
- Los endpoints de Actuator (`health`, `info`, `prometheus`) siguen expuestos para
  Kubernetes; no hay controller de lectura global del catálogo. **(RF-24)**
- `V1__create_products.sql` define `products` con `owner_account_id` nullable,
  `stage`, `currency`, `stock` nullable, `deleted_at`, `version`, y
  `product_images`; **no existe `tenant_id`**.
- `application.yaml`: datasource `catalog`, Hibernate `ddl-auto: validate`,
  RabbitMQ, Actuator/prometheus, multipart 2MB/25MB, puerto 8080. **(NFR-5,
  RF-24)**
- Dependencias añadidas: `spring-boot-starter-validation`,
  `io.minio:minio:8.5.17`, `org.projectlombok:lombok` (solo `@Getter` en las
  entidades) y, en test, `spring-boot-webmvc-test`, `spring-boot-data-jpa-test`,
  `spring-boot-testcontainers`, `testcontainers-postgresql` y
  `testcontainers-junit-jupiter`.
- Configuración enlazada por `@ConfigurationPropertiesScan` en
  `CatalogApiApplication` (no con `@Component` sobre los records, que rompía el
  binding de constructor). **(NFR-8)**
- Ajustes de organización as-built: `model/` guarda solo entidades JPA y los
  enums/converters/DTOs se reparten en `model/enums/`, `model/converters/` y
  `model/dto/`; `ProductLookup` centraliza la carga por dueño; `ProductImageService`
  media entre `ProductImageController` y `client/storage/`; y `util/UrlUtils`
  concentra el armado de URLs. Los controladores no tocan repositorios ni
  clientes. **(RF-19, RF-23, NFR-8)**
- Pruebas: unitarias de entidad, enums, DTOs, servicios, storage y mapper; web
  MockMvc de `ProductController` y `ProductImageController`;
  integración `@DataJpaTest` con Testcontainers PostgreSQL; y de configuración
  (storage, multipart, actuator).

## 3. Ubicación del código (Layered + extensiones)

```text
src/main/java/com/friendlyeshop/catalog
├── CatalogApiApplication             # @SpringBootApplication + @ConfigurationPropertiesScan
├── controller/
│   ├── ProductController            # CRUD + publish/unpublish/publish   RF-2,4-9,11-16,25
│   └── ProductImageController       # GET /catalog/images/{id} público    RF-18,19
├── service/
│   ├── ProductService               # alta, edición, listado, baja lógica RF-2,4-9,17,19-23,25
│   ├── ProductPublicationService    # publicar, despublicar, publicar cat. RF-11-16
│   ├── ProductImageService          # lectura pública de bytes de imagen  RF-18,19
│   └── ProductLookup                # colaborador: findOwned(owner, id)   RF-8,23
├── repository/
│   ├── ProductRepository            # Spring Data JpaRepository
│   └── ProductImageRepository       # Spring Data JpaRepository
├── model/                           # solo entidades JPA
│   ├── Product                      # @Entity + Lombok @Getter
│   ├── ProductImage                 # @Entity + Lombok @Getter
│   ├── enums/
│   │   ├── ProductStage             # enum draft | published (@JsonValue minúsculas)
│   │   └── Currency                 # enum ARS | USD
│   ├── converters/
│   │   └── ProductStageConverter    # AttributeConverter ProductStage <-> String
│   └── dto/
│       ├── ProductForm              # entrada multipart POST/PUT
│       ├── ProductResponse          # salida de producto
│       ├── ProductImageResponse     # {id, url}
│       ├── ProductImageContent      # {resource, contentType} para servir bytes
│       ├── PublishCatalogItem       # ítem sin dueño del cuerpo
│       ├── PublishCatalogRequest    # cuerpo de POST /catalog/publish (List<@Valid …>)
│       ├── PublishCatalogResponse   # {published}
│       └── ErrorResponse            # {error, message}
├── client/storage/
│   ├── ImageStorage                 # interfaz (fake en tests)
│   ├── MinioImageStorage            # implementación MinIO/S3
│   └── StoredImage                  # record {objectKey, contentType}
├── mapper/
│   └── ProductResponseMapper        # entidad → ProductResponse (URL pública vía UrlUtils)
├── util/
│   └── UrlUtils                     # join(baseUrl, path) para armar URLs
├── config/
│   ├── StorageProperties            # fes.storage (S3_*)
│   ├── CatalogProperties            # fes.catalog (PUBLIC_API_BASE_URL)
│   └── StorageConfig                # bean MinioClient
└── exception/
    ├── ProductNotFoundException
    ├── InvalidProductException
    ├── StorageUnavailableException
    └── GlobalExceptionHandler       # @RestControllerAdvice
```

Decisiones de ubicación según `/spring-boot-layered-template`:

- `model/` contiene **solo entidades JPA** (`Product`, `ProductImage`); los enums
  viven en `model/enums/`, los `AttributeConverter` en `model/converters/` y los
  DTOs en `model/dto/` (incluido `ProductImageContent`, antes en `client/storage/`).
- `client/storage/` porque MinIO es integración con un sistema/protocolo externo
  (S3); no contiene reglas de negocio ni acceso a repositorios propios.
- `mapper/` porque la conversión entidad → respuesta se repite en listado,
  creación, edición y publicación; delega el armado de la URL en `util/UrlUtils`.
- `util/UrlUtils` concentra el `join(baseUrl, path)` (normaliza barras) para no
  repetir el armado de URLs por todo el código.
- `service/ProductLookup` es un colaborador compartido de `ProductService` y
  `ProductPublicationService`; evita duplicar `findOwned`/404 por cuenta ajena.
- `exception/` para errores propios y el traductor HTTP; **los controladores son
  delgados y no acceden a repositorios ni a clientes de infraestructura** (solo a
  los servicios), y las entidades usan Lombok `@Getter`.

## 4. Persistencia (Flyway) — RF-1, RF-2, RF-3, RF-9, RF-10, RF-20, RF-23

La migración `V1__create_products.sql` fue **reescrita** (no se encadenó una V2)
porque el servicio era nuevo y el esquema previo con `tenant_id` no estaba
aplicado en ningún entorno. Contenido as-built:

```sql
CREATE TABLE products (
    id UUID PRIMARY KEY,
    owner_account_id UUID NULL,
    name VARCHAR(200) NOT NULL,
    price NUMERIC(19, 2) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency IN ('ARS', 'USD')),
    stock INTEGER NULL CHECK (stock >= 0),
    stage VARCHAR(20) NOT NULL DEFAULT 'draft' CHECK (stage IN ('draft', 'published')),
    deleted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX products_owner_account_id_idx
    ON products (owner_account_id) WHERE deleted_at IS NULL;

CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id),
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX product_images_product_id_idx ON product_images (product_id);
```

- `owner_account_id` nullable: dueño inferido por presencia/ausencia. **(RF-3,
  RF-5)**
- `stage` con default `draft`. **(RF-1, RF-2)**
- `deleted_at` para borrado lógico; el índice parcial por dueño excluye borrados.
  **(RF-9)**
- `currency` obligatoria y acotada a `ARS`/`USD`. **(RF-20, RF-21)**
- `stock` nullable y `product_images` en tabla aparte: publicar no los exige.
  **(RF-13)**
- `version` para bloqueo optimista. (caso límite de concurrencia)
- `product_images.object_key` referencia el objeto en MinIO; no guarda
  credenciales.

## 5. Modelo (entidades JPA) — RF-1 … RF-5, RF-9, RF-11 … RF-14, RF-20

### 5.1 `Product` (`model/Product`)

- Entidad con Lombok `@Getter` (sin getters manuales).
- Campos: `UUID id`, `UUID ownerAccountId` (nullable), `String name`,
  `BigDecimal price`, `Currency currency`, `Integer stock` (nullable),
  `ProductStage stage`, `Instant deletedAt` (nullable), `long version` (`@Version`),
  `Instant createdAt`, `Instant updatedAt`.
- `stage` persistida en minúsculas mediante `ProductStageConverter`; `createdAt` y
  `updatedAt` se sellan en `@PrePersist`/`@PreUpdate`.
- Reglas de dominio:
  - `openNew(...)` nace en `ProductStage.DRAFT`. **(RF-2)**
  - `update(...)` aplica cambios sin cambiar dueño ni etapa. **(RF-8)**
  - `publish()` exige dueño, nombre y precio; pasa a `PUBLISHED`. **(RF-11,
    RF-12)**
  - `unpublish()` pasa a `DRAFT` **sin** tocar `ownerAccountId`. **(RF-14)**
  - `takeOwnership(accountId)` asigna dueño al publicar en bloque. **(RF-13,
    RF-15)**
  - `deleteLogically(now)` marca `deletedAt`. **(RF-9)**
  - `isDeleted()` / `belongsTo(accountId)` para consultas y control de cuenta.
    **(RF-23)**
- Sin lógica de MinIO ni HTTP en la entidad.

### 5.2 `ProductImage` (`model/ProductImage`)

- Entidad con Lombok `@Getter` (sin getters manuales).
- Campos: `UUID id`, `UUID productId`, `String objectKey`, `String contentType`,
  `Instant createdAt`; se crea con `ProductImage.of(...)`. Asocia cada objeto de
  MinIO a su producto. **(RF-17)**

### 5.3 Enumeraciones (`model/enums`) y conversor (`model/converters`)

- `ProductStage` (`model/enums/ProductStage`): `{ DRAFT, PUBLISHED }`, con
  `@JsonValue` para serializar `"draft"`/`"published"` en minúsculas y
  `from(String)` para reconstruir.
- `Currency` (`model/enums/Currency`): `{ ARS, USD }`; cualquier otro valor no se
  puede construir. **(RF-20, RF-21)**
- `ProductStageConverter` (`model/converters/ProductStageConverter`,
  `AttributeConverter<ProductStage, String>`) guarda la etapa en minúsculas en la
  columna `stage`.

## 6. Repositorios (Spring Data) — RF-6, RF-7, RF-8, RF-9, RF-11, RF-14, RF-15, RF-23, RF-25

- `ProductRepository extends JpaRepository<Product, UUID>`:
  - `Page<Product> findByOwnerAccountIdAndDeletedAtIsNull(UUID owner, Pageable)` —
    listado de la cuenta, cualquier etapa. **(RF-6)**
  - `Page<Product> findByOwnerAccountIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
    UUID owner, String name, Pageable)` — listado filtrado por nombre, contains e
    ignore-case. **(RF-25)**
  - `Optional<Product> findByIdAndOwnerAccountIdAndDeletedAtIsNull(UUID id, UUID
    owner)` — detalle acotado a la cuenta; una cuenta distinta no lo ve. **(RF-8,
    RF-23)**
  - `List<Product> findByOwnerAccountIdAndStageAndDeletedAtIsNull(UUID owner,
    ProductStage stage)` — drafts de la cuenta para `publish` en bloque. **(RF-15)**
  - Las consultas siempre excluyen `deleted_at`. **(RF-9)**
- `ProductImageRepository extends JpaRepository<ProductImage, UUID>`:
  - `findByProductId(UUID productId)` para componer `images`.
  - `findById(UUID)` (heredado) resuelve la imagen pública por su id opaco.
- `ownerAccountId` es parámetro obligatorio en las consultas de producto; no existe
  una consulta de listado global sin dueño. **(RF-7)**

## 7. Almacenamiento de imágenes (`client/storage`) — RF-10, RF-17, RF-18, RF-19

- `ImageStorage` (interfaz):
  - `StoredImage store(UUID productId, UUID imageId, MultipartFile file)`.
  - `ProductImageContent load(String objectKey)`.
  - Sin método `delete`: el borrado lógico conserva objetos. **(RF-10)**
- `MinioImageStorage` implementa con el SDK de MinIO/S3:
  - `store`: sube el objeto a `{bucket}/products/{productId}/{imageId}` con
    `PutObjectArgs`, resolviendo el `contentType` del archivo (o
    `application/octet-stream` por defecto), y devuelve `objectKey` +
    `contentType`.
  - `load`: resuelve el `contentType` con `StatObjectArgs`, descarga con
    `GetObjectArgs` y devuelve un `InputStreamResource` con ese tipo.
  - Fallos del almacenamiento → `StorageUnavailableException`.
- `ProductImageContent` (`Resource`, `contentType`, en `model/dto/`) y
  `StoredImage` (`objectKey`, `contentType`, en `client/storage/`) como records de
  transporte interno. `ProductImageContent` es un DTO de salida de
  `ProductImageService`, no un tipo de `client/`.
- El consumidor nunca recibe `S3_ACCESS_KEY`/`S3_SECRET_KEY`; solo URLs públicas.
  **(NFR-6, RF-19)**
- `StorageConfig` construye el `MinioClient` con `endpoint`, `accessKey` y
  `secretKey` de `StorageProperties`. **(NFR-5)**

## 8. Servicios (casos de uso) — RF-2, RF-4 … RF-23, RF-25

### 8.1 `ProductService`

- `list(ownerAccountId, name)`: si `name` es nulo o vacío usa
  `findByOwnerAccountIdAndDeletedAtIsNull`; si viene, usa
  `findByOwnerAccountIdAndNameContainingIgnoreCaseAndDeletedAtIsNull`. Compone
  cada respuesta con sus imágenes. Devuelve los productos de la cuenta en
  cualquier etapa, excluyendo borrados. **(RF-6, RF-7, RF-25)**
- `create(ownerAccountId, form)`: valida, persiste un `Product` en `draft` (con o
  sin dueño); si hay imágenes, las guarda en MinIO y crea `ProductImage`.
  Devuelve la respuesta con URLs públicas. **(RF-2, RF-4, RF-5, RF-17, RF-19,
  RF-20, RF-21)**
- `update(ownerAccountId, id, form)`: valida, carga el producto de la cuenta con
  `ProductLookup.findOwned`, aplica cambios y procesa imágenes nuevas (conserva las
  existentes). **(RF-8, RF-17, RF-23)**
- `deleteLogically(ownerAccountId, id)`: carga con `ProductLookup.findOwned` y
  marca `deletedAt`; no borra objetos. **(RF-9, RF-10, RF-23)**
- `validateForm` defensivo: `name` no vacío, `price` ≥ 0, `currency` no nula y
  `stock` opcional ≥ 0; no aplica default de moneda. **(RF-20, RF-21, RF-22)**
- Las imágenes se guardan con un `imageId` aleatorio por archivo y los vacíos se
  ignoran.

### 8.2 `ProductPublicationService`

- `publish(ownerAccountId, id)`: carga el producto con `ProductLookup.findOwned` y
  llama `publish()`; rechaza sin dueño/nombre/precio sin cambiar etapa. **(RF-11,
  RF-12, RF-13, RF-23)**
- `unpublish(ownerAccountId, id)`: carga con `ProductLookup.findOwned`, pasa a
  `draft` conservando `ownerAccountId`. **(RF-14)**
- `publishCatalog(ownerAccountId, request)`: en una transacción, publica los
  productos sin dueño incluidos en el cuerpo (los crea con `openNew(null, …)`,
  aplica `takeOwnership(ownerAccountId)` y `publish()`), y luego publica todos los
  `draft` ya asociados a la cuenta; devuelve el conteo. Sin productos por
  publicar, responde `published = 0` sin error. **(RF-15, RF-16)**
- Idempotente ante repeticiones: publicar un `published` o despublicar un `draft`
  no cambia la etapa ni falla. (caso límite)

### 8.3 `ProductLookup` (`service/`)

- Colaborador compartido por `ProductService` y `ProductPublicationService`.
- `findOwned(ownerAccountId, id)`: busca con
  `findByIdAndOwnerAccountIdAndDeletedAtIsNull` y lanza `ProductNotFoundException`
  si no existe o es de otra cuenta. Elimina el `findOwnedProduct` duplicado.
  **(RF-8, RF-23)**

### 8.4 `ProductImageService` (`service/`)

- `read(imageId)`: resuelve el `ProductImage` por id (si no existe →
  `ProductNotFoundException`), carga el objeto por su `objectKey` con `ImageStorage`
  y devuelve `ProductImageContent`. Es el mediador entre `ProductImageController` y
  `client/storage/`, de modo que el controller no toca repositorios ni clientes.
  **(RF-18, RF-19)**

### 8.5 `ProductResponseMapper` (`mapper/`)

- Convierte `Product` + `List<ProductImage>` a `ProductResponse` e incluye, por
  imagen, `ProductImageResponse(id, url)`.
- La `url` es `{fes.catalog.public-base-url}/catalog/images/{imageId}`, normalizando
  una barra final en la base, servida por `catalog-api`; el armado se delega en
  `UrlUtils.join(baseUrl, path)` (`util/`). **(RF-19)**

## 9. API HTTP (capa `controller`) — RF-7, RF-9, RF-11, RF-14, RF-15, RF-16, RF-18, RF-19, RF-24, RF-25

Base `/catalog`. El `ownerAccountId` viaja como **query param autoritativo** fijado
por la frontera; un valor en el cuerpo se ignora. **(RF-3, RF-4, RF-23)**

| Método y ruta | Entrada / salida | RF |
|---|---|---|
| `GET /catalog/products?ownerAccountId=&name=` | Lista de productos de la cuenta, cualquier etapa, filtrable por nombre | RF-6, RF-7, RF-25 |
| `POST /catalog/products?ownerAccountId=` | `multipart/form-data` (campos + partes `images`); crea draft; `ownerAccountId` opcional | RF-2, RF-4, RF-5, RF-17, RF-20, RF-21, RF-22 |
| `PUT /catalog/products/{id}?ownerAccountId=` | `multipart/form-data`; actualiza el producto de la cuenta | RF-8, RF-17 |
| `DELETE /catalog/products/{id}?ownerAccountId=` | Baja lógica (204, sin cuerpo) | RF-9, RF-10 |
| `POST /catalog/products/{id}/publish?ownerAccountId=` | Publica un draft | RF-11, RF-12, RF-13 |
| `POST /catalog/products/{id}/unpublish?ownerAccountId=` | Despublica conservando dueño | RF-14 |
| `POST /catalog/publish?ownerAccountId=` | JSON con los productos sin dueño; publica la sesión; `request` opcional | RF-15, RF-16 |
| `GET /catalog/images/{imageId}` | Stream público de la imagen con su `Content-Type` y `Cache-Control` | RF-18, RF-19 |

- `ProductImageController` depende de `ProductImageService.read(imageId)`, que
  devuelve `ProductImageContent`; es el único punto que sirve bytes y no valida
  cuenta porque la URL es pública y el `imageId` es un UUID opaco. Responde
  `Cache-Control: public, max-age=86400`. **(RF-19)**
- `ProductController` delgado: HTTP → `ProductService` /
  `ProductPublicationService`; sin reglas de negocio.
- **Regla as-built:** ningún controller importa `repository/` ni `client/`; solo
  conoce los servicios (y los DTOs). La resolución de entidades queda en
  `ProductLookup`/los servicios.
- No existe endpoint de escritura de imágenes. **(RF-18)**
- Los endpoints de Actuator (`health`, `info`, `prometheus`) siguen expuestos; no
  hay endpoint de lectura global del catálogo publicado en esta iteración. **(RF-24)**

## 10. DTOs y contrato JSON — RF-19

`ProductResponse`:

```json
{
  "id": "uuid",
  "ownerAccountId": "uuid|null",
  "name": "string",
  "price": 1234.5,
  "currency": "ARS",
  "stock": 0,
  "stage": "draft",
  "images": [{ "id": "uuid", "url": "https://.../catalog/images/uuid" }],
  "createdAt": "2026-10-01T12:00:00Z",
  "updatedAt": "2026-10-01T12:00:00Z"
}
```

- `ProductForm` (multipart): `name` (`@NotBlank`), `price` (`@NotNull`,
  `@DecimalMin("0")`), `currency` (`@NotNull`, `Currency`), `stock` (`@Min(0)`,
  opcional), `List<MultipartFile> images` (opcional).
- `PublishCatalogItem`: `name` (`@NotBlank`), `price` (`@NotNull`,
  `@DecimalMin("0")`), `currency` (`@NotNull`), `stock` opcional (`@Min(0)`).
  **(RF-15)**
- `ProductImageContent`: `{ resource, contentType }`, DTO de salida de
  `ProductImageService` hacia `ProductImageController` (no se expone como JSON).
  **(RF-18, RF-19)**
- `PublishCatalogRequest`: `products` = `List<@Valid PublishCatalogItem>` (puede
  ser nula); la validación se declara sobre el tipo del elemento, no sobre el
  contenedor, para evitar la deprecación de validación sobre `List`.
- `PublishCatalogResponse`: `{ "published": n }`. **(RF-16)**
- `ErrorResponse`: `{ "error": "...", "message": "..." }` en español. **(NFR-3)**
- No se exponen entidades JPA en HTTP. `ProductStage` se serializa en minúsculas.

## 11. Manejo de errores — RF-7, RF-12, RF-21, RF-23

`GlobalExceptionHandler` (`@RestControllerAdvice`) traduce y responde
`ErrorResponse` en español:

| Situación | Estado | `error` | RF |
|---|---|---|---|
| Falta `ownerAccountId` u otro parámetro obligatorio | 400 | `datos_invalidos` | RF-7 |
| `ProductForm` inválido (nombre/precio/moneda/stock) o JSON ilegible | 400 | `datos_invalidos` | RF-20, RF-21 |
| Producto o imagen inexistente, o de otra cuenta | 404 | `producto_no_encontrado` | RF-23 |
| Publicar sin dueño/nombre/precio | 409 | `conflicto_de_publicacion` | RF-12 |
| Publicación concurrente (optimista) | 409 | `conflicto_de_publicacion` | caso límite |
| Fallo de MinIO | 503 | `almacenamiento_no_disponible` | caso límite |

- La validación declarativa (`@Valid @ModelAttribute` / `@RequestBody`) se traduce
  vía `BindException` → 400; `InvalidProductException` (reglas de dominio y
  `ProductService.validateForm`) → 409.

## 12. Configuración por entorno — NFR-5, NFR-8

`@ConfigurationProperties` en `config/` (sin secretos en código):

| Variable | Propiedad | Uso | RF/NFR |
|---|---|---|---|
| `S3_ENDPOINT` | `fes.storage.endpoint` | Endpoint S3 de MinIO | RF-17, NFR-5 |
| `S3_BUCKET` | `fes.storage.bucket` | Bucket `product-images` | RF-17, NFR-5 |
| `S3_ACCESS_KEY` | `fes.storage.access-key` | Credencial de escritura | RF-17, NFR-5 |
| `S3_SECRET_KEY` | `fes.storage.secret-key` | Credencial de escritura | RF-17, NFR-5 |
| `PUBLIC_API_BASE_URL` | `fes.catalog.public-base-url` | Base de la URL pública de imágenes | RF-19 |

- **As-built clave (NFR-8):** `StorageProperties` y `CatalogProperties` son
  records `@ConfigurationProperties` + `@Validated`, registrados por
  `@ConfigurationPropertiesScan` en `CatalogApiApplication`. Anotarlos con
  `@Component` rompía el binding de constructor; el escaneo lo resuelve y hace
  fallar el arranque si falta una propiedad (`@NotBlank`).
- `StorageConfig` construye el `MinioClient` con endpoint y credenciales.
- En `application.yaml`: `spring.servlet.multipart.max-file-size: 2MB` y
  `max-request-size: 25MB` (hasta 10 imágenes de 2 MB por alta).
- `PUBLIC_API_BASE_URL` es alcanzable por el navegador del panel; los manifiestos,
  secretos e ingress pertenecen a `infra`.
- `.env.sample` documenta las cinco variables para desarrollo local.

## 13. Pruebas (criterios de finalización)

| Área | Qué verifica | RF |
|---|---|---|
| Migración/esquema | `owner_account_id` nullable, `stage`, `currency`, `deleted_at`, `version`, `product_images`; sin `tenant_id` | RF-1, RF-3, RF-9, RF-20 |
| `Product` | Nace draft; `publish` exige dueño/nombre/precio; `unpublish` conserva dueño; baja lógica | RF-2, RF-11, RF-12, RF-14 |
| `ProductStage`/`Currency` | JSON/parseo en minúsculas y rechazo de valores inválidos | RF-1, RF-20, RF-21 |
| `ProductService` | Alta/edición con imágenes; listado solo de la cuenta; filtro por nombre; sin dueño; moneda inválida; cuenta ajena (vía `ProductLookup`) | RF-4, RF-6, RF-7, RF-8, RF-17, RF-20, RF-21, RF-23, RF-25 |
| `ProductPublicationService` | Publicar, despublicar, publicar catálogo (sin dueño + drafts), conteo 0, repeticiones y cuenta ajena (vía `ProductLookup`) | RF-11 … RF-16, RF-23 |
| `ProductImageService` | Resuelve la imagen por id, delega `load` en `ImageStorage` y traduce inexistente a `ProductNotFoundException` | RF-18, RF-19 |
| `MinioImageStorage` | `store`/`load` con `MinioClient` mock; tipo de contenido; fallo → `StorageUnavailableException` | RF-17, RF-18 |
| Mapper/URL | `images[].url` apunta a `/catalog/images/{id}` con la base pública normalizada; `UrlUtils.join` normaliza barras | RF-19 |
| `ProductController` (MockMvc) | Multipart, validaciones, listado con/sin `name`, publish/unpublish, publish catálogo, 404 de otra cuenta | RF-6, RF-7, RF-11 … RF-16, RF-23, RF-25 |
| `ProductImageController` | Devuelve bytes, `Content-Type` y 404 de imagen inexistente | RF-18, RF-19 |
| Configuración | Binding de `fes.storage` (falla si falta una obligatoria); multipart 2MB/25MB; Actuator expone health/info/prometheus | NFR-5, NFR-8 |
| Conservación | Actuator (`health`, `info`, `prometheus`) sigue expuesto | RF-24 |

- Unitarios: entidad, enums, DTOs, servicios (repositorios mock e `ImageStorage`
  fake; incluye `ProductLookup`, `ProductImageService` y `UrlUtils`),
  `MinioImageStorage` (cliente mock) y mapper.
- Web: `MockMvc` para todos los endpoints y errores.
- Integración: `@DataJpaTest` con PostgreSQL vía Testcontainers (perfil de test)
  para migración y consultas por dueño/etapa/deleted.
- `./mvnw verify` (Checkstyle + tests) al cerrar. **(criterios de finalización)**

## 14. Dependencias y coordinación

- `io.minio:minio:8.5.17` (versión fijada) para el almacenamiento S3.
- `spring-boot-starter-validation` para las validaciones declarativas.
- `org.projectlombok:lombok` (versión fijada en el POM) para `@Getter` en las
  entidades JPA.
- Testcontainers PostgreSQL y soporte de test de Spring Boot (`webmvc-test`,
  `data-jpa-test`, `testcontainers`) en alcance `test`, versiones gestionadas por
  el BOM.
- Bucket `product-images`, credenciales y ruta local definidos en
  `infra/specs/002-product-image-storage`; no se inventan valores.
- `PUBLIC_API_BASE_URL` y la exposición pública de `GET /catalog/images/...` se
  coordinan con `infra` (ingress/ruta). No se hardcodea.

## 15. Mapa resumen RF → entregable

| RF | Entregable principal |
|---|---|
| RF-1 | Columna `stage` + `ProductStage`/`ProductStageConverter` |
| RF-2 | `Product.openNew` / alta en `draft` |
| RF-3 | `owner_account_id` nullable; sin booleano |
| RF-4 | Asignación de dueño desde `ownerAccountId` |
| RF-5 | Alta sin dueño permitida |
| RF-6 | `ProductService.list(owner, name)` |
| RF-7 | `ownerAccountId` obligatorio en listado/edición; 400 si falta |
| RF-8 | `ProductService.update` acotado a la cuenta |
| RF-9 | `deleted_at` y filtros |
| RF-10 | Sin borrado de objetos en baja lógica |
| RF-11 | `Product.publish()` |
| RF-12 | Validación de publicabilidad |
| RF-13 | Dueño al publicar; imágenes/stock opcionales |
| RF-14 | `Product.unpublish()` conserva dueño |
| RF-15 | `ProductPublicationService.publishCatalog` |
| RF-16 | Conteo 0 sin error |
| RF-17 | `ImageStorage.store` + `ProductImage` |
| RF-18 | Sin endpoint de escritura de imágenes |
| RF-19 | `ProductResponseMapper` + `ProductImageController` |
| RF-20 | `Currency` enum |
| RF-21 | Validación de moneda |
| RF-22 | Sin default de moneda en el servicio |
| RF-23 | Consultas por `ownerAccountId`; 404 si no coincide |
| RF-24 | Actuator conservado; sin lectura global del catálogo publicado |
| RF-25 | Consulta `…NameContainingIgnoreCase…` + `name` en `ProductController` |

## 16. Orden de implementación seguido

1. Reescribir `V1__create_products.sql` y crear entidades/enums + repositorios
   (**RF-1, RF-2, RF-3, RF-9, RF-10, RF-20**).
2. `StorageProperties`, `CatalogProperties`, `StorageConfig`, `ImageStorage` y
   `MinioImageStorage` (**RF-17, RF-18, RF-19**, NFR-5).
3. DTOs, `ProductResponseMapper`, excepciones y `GlobalExceptionHandler`
   (**RF-7, RF-19, RF-21, RF-23**, NFR-3).
4. `ProductService`: alta, edición, listado, baja lógica e imágenes
   (**RF-2, RF-4 … RF-10, RF-17, RF-19, RF-20, RF-21, RF-22, RF-23**).
5. `ProductPublicationService`: publicar, despublicar y publicar catálogo
   (**RF-11 … RF-16**).
6. `ProductController` y `ProductImageController`; conservar los endpoints de
   Actuator (**RF-6, RF-7, RF-8, RF-9, RF-11, RF-14, RF-15, RF-16, RF-18, RF-19,
   RF-24**).
7. Ajustes surgidos en la implementación: registro de `@ConfigurationProperties`
   por escaneo (**NFR-8**) y filtro por nombre en el listado (**RF-25**).
8. Refactors de organización as-built: `ProductLookup` compartido, mover
   `ImageContent` a `model/dto/ProductImageContent`, `@Valid` en el tipo del
   elemento, Lombok `@Getter`, separar `model/enums`/`model/converters` y extraer
   `UrlUtils.join` (**RF-8, RF-19, RF-23**).
9. Matriz de tests y `./mvnw verify` (**RF-1 … RF-25**, criterios de
   finalización).

## 17. Fuera de alcance (no implementado aquí)

- Exponer un endpoint de lectura global del catálogo publicado para el market, con
  paginación, búsqueda o detalle por producto. **(RF-24 lo excluye en esta iteración)**
- Purga física de productos y borrado de objetos en MinIO.
- Frontera `/panel/catalog`, login, sesión, borrador local del navegador y UI.
- Provisión de bucket/credenciales/ruta local e ingress (pertenece a `infra`).
- Transformación o edición individual de imágenes y eventos RabbitMQ.
