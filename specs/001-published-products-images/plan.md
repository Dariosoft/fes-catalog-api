# Plan 001 — Productos con etapa y dueño, e imágenes en MinIO

Desglose técnico de `specs/001-published-products-images/spec.md` para
`catalog-api`. Respeta `AGENTS.md` y las skills que menciona:
`/spring-boot-project-creator` en su **opción Layered** (sin regenerar el
proyecto con Spring Initializr), `/spring-boot-layered-template` para ubicar
clases que no encajan en las carpetas básicas y `/clean-code-guard` como guardia
de calidad al cerrar cada cambio. Cubre los RF-1 … RF-24 de la spec.

## 1. Alcance y principios

- Capacidad dueña: **catálogo** (`com.friendlyeshop.catalog`), fuente de verdad de
  productos, precios, stock e imágenes. **(RF-1 … RF-24)**
- **Layered Architecture**: paquetes base `controller/`, `service/`, `repository/`,
  `model/`, `model/dto/`, `config/` y `exception/`, con dos extensiones concretas
  del `/spring-boot-layered-template`:
  - `client/storage/` para la integración con MinIO (protocolo S3).
  - `mapper/` para la conversión repetida entidad → respuesta.
- El dueño es `owner_account_id` (UUID de `Account.id` de `account-api`),
  **nullable**; no se persiste columna booleana de propiedad. El `tenant_id` de la
  migración previa **se descarta**. **(RF-3, RF-5)**
- Las imágenes viajan en el alta y la edición (`multipart/form-data` en
  `POST`/`PUT /catalog/products`); no hay endpoint de imágenes aparte. Se guardan
  en MinIO y se leen por una URL pública servida por `catalog-api`. **(RF-17,
  RF-18, RF-19)**
- El borrado es lógico (`deleted_at`); los objetos de MinIO se conservan. La purga
  física queda fuera de alcance. **(RF-9, RF-10)**
- No se tocan tablas de otros dominios; solo la base `catalog`. **(NFR-1, NFR-2)**

## 2. Estado actual relevante

- Spring Boot 4.1 / Java 25, paquete `com.friendlyeshop.catalog`.
- `CatalogController` expone `GET /catalog` con estado del servicio; se conserva.
  **(RF-24)**
- `V1__create_products.sql` define `products` con `tenant_id UUID NOT NULL` y
  `stock INTEGER NOT NULL`, sin etapa ni dueño real; **no se usa** y se corrige
  (ver §4).
- `application.yaml`: datasource `catalog`, Hibernate `ddl-auto: validate`,
  RabbitMQ, Actuator/prometheus, puerto 8080. Se conserva. **(NFR-5, RF-24)**
- Dependencias: web, actuator, prometheus, data-jpa, amqp, flyway, postgresql,
  lombok, devtools, starter-test. No hay cliente S3/MinIO todavía.
- Prueba existente: unitaria de `CatalogController`.

## 3. Ubicación del código (Layered + extensiones)

```text
src/main/java/com/friendlyeshop/catalog
├── CatalogApiApplication
├── controller/
│   ├── CatalogController            # GET /catalog (conservado)          RF-24
│   ├── ProductController            # CRUD + publish/unpublish/publish   RF-2,4-9,11-16
│   └── ProductImageController       # GET /catalog/images/{id} público    RF-18,19
├── service/
│   ├── ProductService               # alta, edición, listado, baja lógica RF-2,4-9,17,19-23
│   └── ProductPublicationService    # publicar, despublicar, publicar cat. RF-11-16
├── repository/
│   ├── ProductRepository            # Spring Data JpaRepository
│   └── ProductImageRepository       # Spring Data JpaRepository
├── model/
│   ├── Product                      # @Entity
│   ├── ProductImage                 # @Entity
│   ├── ProductStage                 # enum draft | published
│   ├── Currency                     # enum ARS | USD
│   └── dto/
│       ├── ProductForm              # entrada multipart POST/PUT
│       ├── ProductResponse          # salida de producto
│       ├── ProductImageResponse     # {id, url}
│       ├── PublishCatalogRequest    # cuerpo de POST /catalog/publish
│       ├── PublishCatalogResponse   # {published}
│       └── ErrorResponse            # {error, message}
├── client/storage/
│   ├── ImageStorage                 # interfaz (fake en tests)
│   ├── MinioImageStorage            # implementación MinIO/S3
│   ├── StoredImage                  # record {objectKey, contentType}
│   └── ImageContent                 # record {resource, contentType}
├── mapper/
│   └── ProductResponseMapper        # entidad → ProductResponse (URL pública)
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

- `client/storage/` porque MinIO es integración con un sistema/protocolo externo
  (S3); no contiene reglas de negocio ni acceso a repositorios propios.
- `mapper/` porque la conversión entidad → respuesta se repite en listado,
  creación, edición y publicación.
- `exception/` para errores propios y el traductor HTTP; controladores delgados.

## 4. Persistencia (Flyway) — RF-1, RF-2, RF-3, RF-9, RF-10, RF-20, RF-23

La migración `V1__create_products.sql` no se usa (`tenant_id`) y **no está aplicada
en ningún entorno**: el servicio es nuevo y solo tiene el esquema inicial. Por eso
se **reescribe V1** en lugar de encadenar una V2, dejando el esquema real desde el
origen. Excepción documentada a la regla «no editar migraciones aplicadas» de
`AGENTS.md`, motivada por la resolución 10 de la spec. Si alguna base ya tuviera V1
aplicada, se creará una `V2` equivalente y no se tocará V1.

Nuevo contenido propuesto para `V1__create_products.sql`:

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
- `deleted_at` para borrado lógico; índices parciales excluyen borrados. **(RF-9)**
- `currency` obligatoria y acotada a `ARS`/`USD`. **(RF-20, RF-21)**
- `stock` nullable e `images` en tabla aparte: publicar no los exige. **(RF-13)**
- `version` para bloqueo optimista. (caso límite de concurrencia)
- `product_images.object_key` referencia el objeto en MinIO; no guarda credenciales.

## 5. Modelo (entidades JPA) — RF-1 … RF-5, RF-9, RF-11 … RF-14, RF-20

### 5.1 `Product` (`model/Product`)

- Campos: `UUID id`, `UUID ownerAccountId` (nullable), `String name`,
  `BigDecimal price`, `Currency currency`, `Integer stock` (nullable),
  `ProductStage stage`, `Instant deletedAt` (nullable), `long version` (`@Version`),
  `Instant createdAt`, `Instant updatedAt`.
- Reglas de dominio:
  - `openNew(...)` nace en `ProductStage.DRAFT`. **(RF-2)**
  - `update(...)` aplica cambios sin cambiar dueño ni etapa. **(RF-8)**
  - `publish()` exige dueño, nombre y precio; pasa a `PUBLISHED`. **(RF-11, RF-12)**
  - `unpublish()` pasa a `DRAFT` **sin** tocar `ownerAccountId`. **(RF-14)**
  - `takeOwnership(accountId)` asigna dueño al publicar en bloque. **(RF-13, RF-15)**
  - `deleteLogically(now)` marca `deletedAt`. **(RF-9)**
  - `isDeleted()` / `belongsTo(accountId)` para consultas y control de cuenta.
    **(RF-23)**
- Sin lógica de MinIO ni HTTP en la entidad.

### 5.2 `ProductImage` (`model/ProductImage`)

- Campos: `UUID id`, `UUID productId`, `String objectKey`, `String contentType`,
  `Instant createdAt`. Asocia cada objeto de MinIO a su producto. **(RF-17)**

### 5.3 Enumeraciones

- `ProductStage { DRAFT, PUBLISHED }` con valor en minúsculas en persistencia.
- `Currency { ARS, USD }`; cualquier otro valor no se puede construir. **(RF-20,
  RF-21)**

## 6. Repositorios (Spring Data) — RF-6, RF-7, RF-8, RF-9, RF-11, RF-14, RF-15, RF-23

- `ProductRepository extends JpaRepository<Product, UUID>`:
  - `findByOwnerAccountIdAndDeletedAtIsNull(UUID owner, Pageable)` — listado de la
    cuenta, cualquier etapa. **(RF-6)**
  - `findByIdAndOwnerAccountIdAndDeletedAtIsNull(UUID id, UUID owner)` — detalle
    acotado a la cuenta; una cuenta distinta no lo ve. **(RF-8, RF-23)**
  - `findByOwnerAccountIdAndStageAndDeletedAtIsNull(UUID owner, ProductStage stage)`
    — drafts de la cuenta para `publish` en bloque. **(RF-15)**
  - Las consultas siempre excluyen `deleted_at`. **(RF-9)**
- `ProductImageRepository extends JpaRepository<ProductImage, UUID>`:
  - `findByProductId(UUID productId)` para componer `images`.
  - `findByIdAndProductId(...)` no es necesaria; la carga pública de imagen usa el
    `objectKey` guardado.
- `ownerAccountId` es parámetro obligatorio en las consultas; no existe una
  consulta global sin dueño. **(RF-7)**

## 7. Almacenamiento de imágenes (`client/storage`) — RF-10, RF-17, RF-18, RF-19

- `ImageStorage` (interfaz):
  - `StoredImage store(UUID productId, MultipartFile file)`.
  - `ImageContent load(UUID imageId)`.
  - Sin método `delete`: el borrado lógico conserva objetos. **(RF-10)**
- `MinioImageStorage` implementa con el SDK de MinIO/S3:
  - `store`: sube el objeto a `{bucket}/products/{productId}/{imageId}` y devuelve
    `objectKey` + `contentType`.
  - `load`: descarga el objeto y devuelve su contenido y `contentType`.
  - Fallos del almacenamiento → `StorageUnavailableException`.
- `ImageContent` y `StoredImage` como records de transporte interno.
- El consumidor nunca recibe `S3_ACCESS_KEY`/`S3_SECRET_KEY`; solo URLs públicas.
  **(NFR-6, RF-19)**

## 8. Servicios (casos de uso) — RF-2, RF-4 … RF-23

### 8.1 `ProductService`

- `list(ownerAccountId)`: devuelve los productos de la cuenta en cualquier etapa.
  **(RF-6, RF-7)**
- `create(ownerAccountId, form)`: valida y persiste un `Product` en `draft`; si hay
  imágenes, las guarda en MinIO y crea `ProductImage`. Devuelve la respuesta con
  URLs públicas. **(RF-2, RF-4, RF-5, RF-17, RF-19, RF-20, RF-21)**
- `update(ownerAccountId, id, form)`: aplica cambios al producto de la cuenta;
  procesa imágenes nuevas. **(RF-8, RF-17, RF-23)**
- `deleteLogically(ownerAccountId, id)`: marca `deletedAt`; no borra objetos.
  **(RF-9, RF-10, RF-23)**
- Valida `name` (no vacío), `price` (≥ 0) y `currency` (`ARS`/`USD`); `stock`
  opcional ≥ 0. No aplica default de moneda. **(RF-20, RF-21, RF-22)**
- Converciones a `ProductResponse` delegadas en `ProductResponseMapper`.

### 8.2 `ProductPublicationService`

- `publish(ownerAccountId, id)`: carga el producto de la cuenta y llama
  `publish()`; rechaza sin dueño/nombre/precio sin cambiar etapa. **(RF-11, RF-12,
  RF-13, RF-23)**
- `unpublish(ownerAccountId, id)`: pasa a `draft` conservando `ownerAccountId`.
  **(RF-14)**
- `publishCatalog(ownerAccountId, request)`: en una transacción, para cada producto
  sin dueño del cuerpo crea el `Product` con ese dueño y lo publica (RF-15), y
  publica todos los `draft` ya asociados a la cuenta; devuelve el conteo. Si no hay
  productos, responde `published = 0` sin error. **(RF-15, RF-16)**
- Idempotente ante repeticiones: publicar un `published` o despublicar un `draft`
  no cambia la etapa ni falla. (caso límite)

### 8.3 `ProductResponseMapper` (`mapper/`)

- Convierte `Product` + `ProductImage` a `ProductResponse` e incluye, por imagen,
  `ProductImageResponse(id, url)`.
- La `url` es `{fes.catalog.public-base-url}/catalog/images/{imageId}`, servida por
  `catalog-api`. **(RF-19)**

## 9. API HTTP (capa `controller`) — RF-7, RF-9, RF-11, RF-14, RF-15, RF-16, RF-18, RF-19, RF-24

Base `/catalog`. El `ownerAccountId` viaja como **query param autoritativo** fijado
por la frontera; un valor en el cuerpo se ignora. **(RF-3, RF-4, RF-23)**

| Método y ruta | Entrada / salida | RF |
|---|---|---|
| `GET /catalog` | Estado del servicio (conservado) | RF-24 |
| `GET /catalog/products?ownerAccountId=` | Lista de productos de la cuenta, cualquier etapa | RF-6, RF-7 |
| `POST /catalog/products?ownerAccountId=` | `multipart/form-data` (campos + partes `images`); crea draft | RF-2, RF-4, RF-5, RF-17, RF-20, RF-21, RF-22 |
| `PUT /catalog/products/{id}?ownerAccountId=` | `multipart/form-data`; actualiza el producto de la cuenta | RF-8, RF-17 |
| `DELETE /catalog/products/{id}?ownerAccountId=` | Baja lógica, sin respuesta de cuerpo | RF-9, RF-10 |
| `POST /catalog/products/{id}/publish?ownerAccountId=` | Publica un draft | RF-11, RF-12, RF-13 |
| `POST /catalog/products/{id}/unpublish?ownerAccountId=` | Despublica conservando dueño | RF-14 |
| `POST /catalog/publish?ownerAccountId=` | JSON con los productos sin dueño; publica la sesión | RF-15, RF-16 |
| `GET /catalog/images/{imageId}` | Stream público de la imagen con su `Content-Type` | RF-18, RF-19 |

- `ProductImageController` es el único punto que sirve bytes; no valida cuenta
  porque la URL es pública y el `imageId` es un UUID opaco. **(RF-19)**
- `ProductController` delgado: HTTP → `ProductService` /
  `ProductPublicationService`; sin reglas de negocio.
- No existe endpoint de imágenes de escritura. **(RF-18)**

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
- `PublishCatalogRequest`: `products` = lista de borradores sin dueño
  (`name`, `price`, `currency`, `stock` opcional). **(RF-15)**
- `PublishCatalogResponse`: `{ "published": n }`. **(RF-16)**
- `ErrorResponse`: `{ "error": "...", "message": "..." }` en español. **(NFR-3)**
- No se exponen entidades JPA en HTTP.

## 11. Manejo de errores — RF-7, RF-12, RF-21, RF-23

`GlobalExceptionHandler` (`@RestControllerAdvice`) traduce y responde
`ErrorResponse` en español:

| Situación | Estado | `error` | RF |
|---|---|---|---|
| Falta `ownerAccountId` | 400 | `datos_invalidos` | RF-7 |
| Campos o moneda inválidos | 400 | `datos_invalidos` | RF-20, RF-21 |
| Producto inexistente o de otra cuenta | 404 | `producto_no_encontrado` | RF-23 |
| Publicar sin dueño/nombre/precio | 409 | `conflicto_de_publicacion` | RF-12 |
| Publicación concurrente (optimista) | 409 | `conflicto_de_publicacion` | caso límite |
| Fallo de MinIO | 503 | `almacenamiento_no_disponible` | caso límite |

## 12. Configuración por entorno — NFR-5

`@ConfigurationProperties` en `config/` (sin secretos en código):

| Variable | Propiedad | Uso | RF/NFR |
|---|---|---|---|
| `S3_ENDPOINT` | `fes.storage.endpoint` | Endpoint S3 de MinIO | RF-17, NFR-5 |
| `S3_BUCKET` | `fes.storage.bucket` | Bucket `product-images` | RF-17, NFR-5 |
| `S3_ACCESS_KEY` | `fes.storage.access-key` | Credencial de escritura | RF-17, NFR-5 |
| `S3_SECRET_KEY` | `fes.storage.secret-key` | Credencial de escritura | RF-17, NFR-5 |
| `PUBLIC_API_BASE_URL` | `fes.catalog.public-base-url` | Base de la URL pública de imágenes | RF-19 |

- `StorageConfig` construye el `MinioClient` con las propiedades S3 (path-style).
- En `application.yaml`: `spring.servlet.multipart.max-file-size` y
  `max-request-size` para admitir hasta 10 imágenes de 2 MB por alta (coherente con
  `panel-web`).
- `PUBLIC_API_BASE_URL` es nueva: coordinar con `infra` que la URL sea alcanzable
  por el navegador del panel (los manifiestos y secretos pertenecen a `infra`).

## 13. Pruebas (criterios de finalización)

| Área | Qué verificar | RF |
|---|---|---|
| Migración/esquema | `owner_account_id` nullable, `stage`, `currency`, `deleted_at`, `product_images`; sin `tenant_id` | RF-1, RF-3, RF-9, RF-20 |
| `Product` | Nace draft; `publish` exige dueño/nombre/precio; `unpublish` conserva dueño; baja lógica | RF-2, RF-11, RF-12, RF-14 |
| `ProductService` | Alta/edición con imágenes; listado solo de la cuenta; sin dueño → 400; moneda inválida → 400 | RF-4, RF-6, RF-7, RF-8, RF-17, RF-20, RF-21, RF-23 |
| `ProductPublicationService` | Publicar, despublicar, publicar catálogo (sin dueño + drafts), conteo 0 sin error | RF-11 … RF-16 |
| `MinioImageStorage` | `store`/`load` con `MinioClient` mock; fallo → `StorageUnavailableException` | RF-17, RF-18 |
| Mapper/URL | `images[].url` apunta a `/catalog/images/{id}` con la base pública | RF-19 |
| `ProductController` (MockMvc) | Multipart, validaciones, publish/unpublish, publish catálogo, 404 de otra cuenta | RF-7 … RF-16, RF-23 |
| `ProductImageController` | Devuelve bytes y `Content-Type` correctos | RF-18, RF-19 |
| Conservación | `GET /catalog` y Actuator siguen respondiendo | RF-24 |

- Unitarios: entidad, servicios (con repositorios mock y `ImageStorage` fake),
  `MinioImageStorage` (cliente mock) y mapper.
- Web: `MockMvc` para todos los endpoints y errores.
- Integración: `@DataJpaTest` con PostgreSQL vía Testcontainers (perfil de test)
  para migración y consultas por dueño/etapa/deleted.
- `./mvnw verify` (Checkstyle + tests) al cerrar. **(criterios de finalización)**

## 14. Dependencias y coordinación

- Añadir el SDK `io.minio:minio` (versión fijada) para el almacenamiento S3. Se
  consulta/coordina antes de incorporarlo, según `AGENTS.md`. Alternativa
  equivalente: AWS SDK S3 v2 con `pathStyle(true)`.
- Añadir Testcontainers PostgreSQL (alcance `test`, versión fijada) para las
  pruebas de integración.
- Bucket `product-images`, credenciales y ruta local ya definidos en
  `infra/specs/002-product-image-storage`; no se inventan valores.
- `PUBLIC_API_BASE_URL` y la exposición pública de `GET /catalog/images/...` se
  coordinan con `infra` (ingress/ruta). No se hardcodea.

## 15. Mapa resumen RF → entregable

| RF | Entregable principal |
|---|---|
| RF-1 | Columna `stage` + `ProductStage` |
| RF-2 | `Product.openNew` / alta en `draft` |
| RF-3 | `owner_account_id` nullable; sin booleano |
| RF-4 | Asignación de dueño desde `ownerAccountId` |
| RF-5 | Alta sin dueño permitida |
| RF-6 | `ProductService.list(owner)` |
| RF-7 | `ownerAccountId` obligatorio; 400 si falta |
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
| RF-18 | Sin endpoint de imágenes de escritura |
| RF-19 | `ImageUrlBuilder`/mapper + `ProductImageController` |
| RF-20 | `Currency` enum |
| RF-21 | Validación de moneda |
| RF-22 | Sin default de moneda en el servicio |
| RF-23 | Consultas por `ownerAccountId`; 404 si no coincide |
| RF-24 | `CatalogController` y Actuator conservados |

## 16. Orden de implementación sugerido

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
6. `ProductController` y `ProductImageController`; conservar `GET /catalog`
   (**RF-6, RF-7, RF-8, RF-9, RF-11, RF-14, RF-15, RF-16, RF-18, RF-19, RF-24**).
7. Matriz de tests y `./mvnw verify` (**RF-1 … RF-24**, criterios de finalización).

## 17. Fuera de alcance (no implementar aquí)

- Listado global de publicados / lectura de market (`GET /catalog` como catálogo
  público). **(RF-24 solo conserva el estado del servicio)**
- Purga física de productos y borrado de objetos en MinIO.
- Frontera `/panel/catalog`, login, sesión, borrador local del navegador y UI.
- Provisión de bucket/credenciales/ruta local e ingress (pertenece a `infra`).
- Transformación o edición individual de imágenes y eventos RabbitMQ.
