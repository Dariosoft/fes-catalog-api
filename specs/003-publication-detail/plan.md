# Plan 003 — Detalle público de publicación con descripción y referencia al vendedor

Diseño técnico de `specs/003-publication-detail/spec.md` para `catalog-api`. Respeta
`AGENTS.md` y las skills que menciona: `/spring-boot-project-creator` en su **opción
Layered** (sin regenerar el proyecto con Spring Initializr),
`/spring-boot-layered-template` para ubicar clases que no encajan en las carpetas
básicas y `/clean-code-guard` como guardia de calidad. Cubre los RF-1 … RF-24 y los
NFR-1 … NFR-7 de la spec. Agrega una única migración Flyway (`V2`) y ningún cambio de
dependencias ni de tablas ajenas.

## 1. Objetivo técnico

Habilitar el **detalle público de una publicación** (`GET /catalog/publication/{id}`)
de solo lectura, sin sesión ni credenciales, que devuelva nombre, precio, moneda,
stock, una descripción opcional, las imágenes con su URL pública ya existente y el
identificador público de la cuenta dueña como referencia al vendedor. Además, dotar
al producto de una **descripción opcional de hasta 1000 caracteres** que se persiste,
se conserva al publicar y despublicar, y se normaliza a ausente cuando llega vacía o
compuesta solo por espacios. La grilla pública paginada (`GET /catalog/publication`)
y sus `PublicationResponse` **no cambian** de forma: el listado no incorpora la
descripción. **(RF-1 … RF-24, NFR-1 … NFR-7)**

## 2. Alcance y principios

- Capacidad dueña: **catálogo** (`com.friendlyeshop.catalog`). El detalle se resuelve
  solo con la base `catalog`; no se leen ni escriben tablas de otros dominios.
  **(NFR-2, NFR-3)**
- **Layered Architecture**: se usan los paquetes existentes `controller/`, `service/`,
  `repository/`, `model/`, `model/dto/`, `mapper/`, `client/`, `util/`, `config/` y
  `exception/`. No se crean paquetes nuevos.
- Visibilidad idéntica a la grilla: etapa `published`, `deletedAt IS NULL`,
  `ownerAccountId IS NOT NULL` y `stock IS NULL OR stock > 0`. Una publicación con
  stock `0` o negativo, borrada, en borrador, despublicada o sin dueño responde como
  **no encontrada**. **(RF-17, RF-18, RF-19)**
- El detalle es de solo lectura: no modifica etapa, stock ni ninguna otra propiedad.
  **(RF-21, NFR-5)**
- `sellerAccountId` es el `ownerAccountId` expuesto **solo como referencia pública**
  para que el storefront consulte la tienda. Nunca se exponen email, credenciales,
  etapa ni otros datos internos del vendedor. **(RF-14, RF-15, RF-16)**
- El **contrato técnico obligatorio cross-repo** agrega `description` a la
  `ProductForm` (multipart del panel) y a `ProductResponse` (respuesta del panel). Es
  un cambio **aditivo**: no se alteran rutas, operaciones, visibilidad ni el resto del
  shape existente del panel, de modo que los consumidores actuales siguen
  funcionando. La grilla pública y `PublicationResponse` quedan sin `description`.
  **(RF-1, RF-2, RF-22, RF-23)**
- La nueva ruta pública vive bajo `PublicationController` (`/publication`); el
  prefijo `/catalog` lo aporta `ApiPathConfig`. No se toca Actuator, health checks ni
  métricas. **(RF-9, RF-24, NFR-7)**

## 3. Estado actual relevante (as-is)

- `Product` (`model/Product.java`) tiene `id`, `ownerAccountId`, `name`, `price`,
  `currency`, `stock`, `stage`, `deletedAt`, `version`, `createdAt` y `updatedAt`,
  pero **no** `description`. `openNew(ownerAccountId, name, price, currency, stock)`
  y `update(name, price, currency, stock)` no reciben descripción.
- `ProductForm` (`model/dto/ProductForm.java`) es el formulario multipart del panel:
  `(name, price, currency, stock, images)` con `@NotBlank`, `@NotNull`,
  `@DecimalMin("0")` y `@Min(0)`.
- `ProductResponse` (`model/dto/ProductResponse.java`) expone al panel `id`,
  `ownerAccountId`, `name`, `price`, `currency`, `stock`, `stage`, `images`,
  `createdAt`, `updatedAt`.
- `ProductService` valida el formulario con `validateForm`, guarda mediante
  `ProductRepository` y mapea con `ProductResponseMapper.toResponse`.
- `PublicationService` ya expone `list`, `publish`, `unpublish` y `publishCatalog`;
  `publishCatalog` crea productos con `Product.openNew(...)` a partir de
  `Publication`, que no tiene descripción.
- `ProductRepository` ya tiene `findPublished` y `findPublishedByName` con las cuatro
  condiciones de visibilidad, pero **no** existe una consulta por `id` para el
  detalle público.
- `ProductResponseMapper` ya compone las URLs públicas de imagen con
  `UrlUtils.join(publicBaseUrl, "/catalog/images/{imageId}")`.
- `PublicationController` ya agrupa `GET /catalog/publication` y las operaciones de
  publicación; **no** existe `GET /catalog/publication/{id}`.
- `GlobalExceptionHandler` ya traduce `ProductNotFoundException` a `404` con código
  `producto_no_encontrado`, por lo que el detalle no necesita nuevos handlers.
- `V1__create_products.sql` es la única migración; la tabla `products` no tiene
  `description`.

## 4. Decisiones técnicas (motivo y RF)

| # | Decisión | Motivo | RF |
|---|---|---|---|
| D1 | `description` es `VARCHAR(1000) NULL` en una migración **nueva** `V2`. | No editar migraciones aplicadas; la columna debe ser opcional y acotada por esquema. | RF-1, RF-5, NFR-4 |
| D2 | La descripción se normaliza en la entidad: `null` o `isBlank()` → `null`; en otro caso `strip()`. | Cumplir RF-3/RF-4 sin texto por defecto y sin ruido de espacios externos; el contenido interno y los acentos se conservan. | RF-3, RF-4, RF-7, RF-8 |
| D3 | `openNew` y `update` reciben `description` como **último parámetro**. | Mantiene el orden previo de los parámetros y hace explícita la responsabilidad de la entidad sobre la normalización. | RF-2, RF-3 |
| D4 | `ProductForm` agrega `@Size(max = 1000) String description` opcional. | La validación HTTP rechaza > 1000 con 400 vía `BindException` antes de persistir; el límite vive junto al contrato del formulario. | RF-5, RF-6 |
| D5 | `ProductResponse` (panel) agrega `description` de forma **aditiva**. | Contrato cross-repo obligatorio; al no quitar ni renombrar campos, el panel y sus operaciones siguen compatibles. | RF-1, RF-2, RF-23 |
| D6 | `PublicationResponse` y `PublicationPageResponse` **no** cambian. | El listado público no debe ampliar su contrato ni cargar descripciones de toda la página. | RF-22, NFR-5 |
| D7 | Nuevo DTO `PublicationDetailResponse` con `sellerAccountId`, sin `stage`. | El detalle necesita la referencia pública del vendedor y no debe revelar datos internos. | RF-14, RF-15, RF-16 |
| D8 | Nueva consulta JPQL `findPublicById` con las cuatro condiciones de visibilidad. | Reusar el criterio exacto de la grilla en una sola consulta, sin lógica duplicada en el servicio. | RF-17, RF-18, RF-19 |
| D9 | El detalle se resuelve en `PublicationService#detail`. | Es el caso de uso público del catálogo y ya es el dueño de la lectura pública y del mapper. | RF-9, RF-10, RF-21 |
| D10 | No encontrada y no visible comparten la misma excepción y mensaje. | Evitar revelar la existencia de publicaciones ocultas; ambas responden 404 idéntico. | RF-18, RF-19 |
| D11 | El endpoint devuelve `PublicationDetailResponse` con `@GetMapping("/{id}")`. | Mantiene el detalle bajo `/catalog/publication` y reutiliza el prefijo existente. | RF-9, RF-20 |

## 5. Estructura objetivo (Layered)

```text
src/main/java/com/friendlyeshop/catalog
├── controller/
│   ├── ProductController                    # /products: panel (sin cambios de rutas)   RF-2,23
│   └── PublicationController                # /publication: + GET /{id}                  RF-9,20
├── service/
│   ├── ProductService                        # propaga description en create/update       RF-2,5,6
│   ├── PublicationService                    # + detail(id)                               RF-9,10,21
│   └── ProductLookup                         # sin cambios
├── repository/
│   ├── ProductRepository                     # + findPublicById                          RF-17,18,19
│   └── ProductImageRepository                # sin cambios (findByProductId)             RF-11,13
├── model/
│   ├── Product                               # + description y normalización              RF-1,2,3,4
│   └── dto/
│       ├── ProductForm                       # + description opcional (@Size)             RF-5,6
│       ├── ProductResponse                   # + description (panel, aditivo)             RF-1,2,23
│       ├── PublicationResponse               # sin cambios                                RF-22
│       ├── PublicationPageResponse           # sin cambios                                RF-22
│       └── PublicationDetailResponse         # NUEVO detalle público                      RF-10..16
├── mapper/
│   └── ProductResponseMapper                 # + toPublicationDetail                     RF-10..16
├── client/storage/ · util/ · config/ · exception/   # sin cambios
src/main/resources/db/migration/
├── V1__create_products.sql                  # sin cambios
└── V2__add_product_description.sql          # NUEVA                                       RF-1
```

Decisiones de ubicación según `/spring-boot-layered-template`:

- `PublicationDetailResponse` va a `model/dto/` junto al resto de contratos públicos;
  no se reutiliza `PublicationResponse` porque el detalle agrega `description` y
  `sellerAccountId`, y no se reutiliza `ProductResponse` porque expone etapa y fechas.
- `ProductResponseMapper.toPublicationDetail` se agrega al mapper existente, que ya es
  dueño de la conversión entidad → respuesta y del armado de la URL pública de imagen;
  así se evita duplicar `toImageResponse` y `UrlUtils.join`.
- `detail` vive en `PublicationService` porque es el caso de uso público y ya coordina
  `ProductRepository`, `ProductImageRepository` y el mapper.
- No se crean `validation/`, `http/` ni `client/` nuevos: la validación de longitud es
  una restricción del DTO y no hay integración externa en este corte. **(NFR-1, NFR-3)**

## 6. Migración Flyway — RF-1, RF-5

Archivo nuevo `src/main/resources/db/migration/V2__add_product_description.sql`:

```sql
ALTER TABLE products ADD COLUMN description VARCHAR(1000) NULL;
```

- No se edita `V1__create_products.sql`. **(AGENTS.md: no editar migraciones aplicadas)**
- Hibernate corre con `ddl-auto: validate`; la columna declarada en la entidad debe
  coincidir exactamente con `description VARCHAR(1000) NULL`.
- Los tests de integración con Testcontainers aplican `V1` + `V2` en una base limpia.

## 7. Cambios por capa

### 7.1 Entidad `model/Product`

- Nuevo campo:

```java
@Column(name = "description", length = 1000)
private String description;
```

- `openNew` y `update` reciben `String description` como último parámetro y asignan
  `this.description = normalizeDescription(description)`.
- Normalización privada y estática:

```java
private static String normalizeDescription(String description) {
    if (description == null || description.isBlank()) {
        return null;
    }
    return description.strip();
}
```

- `publish()`, `unpublish()` y `deleteLogically(Instant)` **no** tocan la descripción:
  se conserva al publicar, despublicar y borrar lógicamente. **(RF-7, RF-8)**
- No se aplica texto por defecto en ningún camino. **(RF-4)**

### 7.2 DTOs `model/dto`

- `ProductForm`:

```java
public record ProductForm(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin("0") BigDecimal price,
        @NotNull Currency currency,
        @Min(0) Integer stock,
        List<MultipartFile> images) {
}
```

  agregando `@Size(max = 1000)` sobre `description`. El binding multipart es por
  nombre de campo, por lo que el orden del record no afecta al contrato HTTP.
  **(RF-5, RF-6)**
- `ProductResponse` (panel): agregar `String description` (aditivo). **(RF-1, RF-2)**
- `PublicationDetailResponse` (nuevo):

```java
public record PublicationDetailResponse(
        UUID id,
        String name,
        BigDecimal price,
        Currency currency,
        Integer stock,
        String description,
        UUID sellerAccountId,
        List<ProductImageResponse> images) {
}
```

  `description` puede ser `null`; `sellerAccountId` es la referencia pública; no
  incluye `stage`, `ownerAccountId`, `createdAt`, `updatedAt`, email ni credenciales.
  **(RF-10, RF-11, RF-13, RF-14, RF-15, RF-16)**
- `PublicationResponse` y `PublicationPageResponse` no se modifican. **(RF-22)**

### 7.3 Mapper `mapper/ProductResponseMapper`

- `toResponse(Product, List<ProductImage>)` pasa a incluir `product.getDescription()`.
- Nuevo método:

```java
public PublicationDetailResponse toPublicationDetail(Product product, List<ProductImage> images) {
    return new PublicationDetailResponse(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getCurrency(),
            product.getStock(),
            product.getDescription(),
            product.getOwnerAccountId(),
            toImageResponses(images));
}
```

  Reutiliza `toImageResponse`/`UrlUtils.join` para `images[].url`, de modo que la URL
  es la misma que ya sirve la grilla y `GET /catalog/images/{imageId}` sin
  credenciales. Sin imágenes, `List.of()`. **(RF-11, RF-12, RF-13, RF-14, NFR-6)**

### 7.4 Repositorio `repository/ProductRepository`

Nueva consulta JPQL con las cuatro condiciones de visibilidad, consistente con
`findPublished`:

```java
@Query("""
        select p from Product p
        where p.id = :id
          and p.stage = :stage
          and p.deletedAt is null
          and p.ownerAccountId is not null
          and (p.stock is null or p.stock > :minimumStock)
        """)
Optional<Product> findPublicById(
        @Param("id") UUID id,
        @Param("stage") ProductStage stage,
        @Param("minimumStock") int minimumStock);
```

`ProductImageRepository.findByProductId(UUID)` ya existe y cubre el detalle; no se
modifica. **(RF-11, RF-13, RF-17, RF-18, RF-19, RF-20)**

### 7.5 Servicios

`ProductService`:

- `create` pasa `form.description()` a `Product.openNew(...)`.
- `update` pasa `form.description()` a `product.update(...)`.
- No se duplica la validación de longitud en el servicio: la restricción
  `@Size(max = 1000)` del DTO es la fuente única y produce `400` antes de persistir.
  **(RF-2, RF-5, RF-6)**

`PublicationService`:

- Nuevo método de solo lectura:

```java
@Transactional(readOnly = true)
public PublicationDetailResponse detail(UUID id) {
    Product product = productRepository
            .findPublicById(id, ProductStage.PUBLISHED, MINIMUM_STOCK)
            .orElseThrow(() -> new ProductNotFoundException("La publicación no existe"));
    List<ProductImage> images = productImageRepository.findByProductId(product.getId());
    return responseMapper.toPublicationDetail(product, images);
}
```

- `MINIMUM_STOCK` ya vale `0`; la condición `stock > 0` deja fuera el stock `0`.
- El mismo mensaje y la misma excepción cubren inexistente y no visible. **(RF-18,
  RF-19)**
- `publishCatalog` sigue creando con `Product.openNew(..., null)` porque
  `Publication` no tiene descripción; el detalle de esos productos devuelve
  `description = null`. **(RF-4, RF-10, RF-23)**
- No hay `save`, `delete` ni cambio de estado en `detail`. **(RF-21, NFR-5)**

### 7.6 Controller `controller/PublicationController`

```java
@GetMapping("/{id}")
public PublicationDetailResponse detail(@PathVariable UUID id) {
    return publicationService.detail(id);
}
```

- Ruta efectiva: `GET /catalog/publication/{id}`, sin sesión ni token. **(RF-9,
  NFR-1)**
- Controlador delgado: solo delega; no importa `repository/` ni entidades.
- `ProductNotFoundException` se traduce a `404` en `GlobalExceptionHandler` sin
  cambios. **(RF-18, RF-19)**

## 8. Contrato HTTP/JSON del detalle — RF-9 … RF-16, RF-20

`GET /catalog/publication/{id}` — sin sesión, sin token, sin credenciales de
almacenamiento. **(RF-9, NFR-1)**

| Aspecto | Definición | RF |
|---|---|---|
| Ruta | `GET /catalog/publication/{id}` (`id` = UUID de la publicación) | RF-9, RF-20 |
| Autenticación | Ninguna | RF-9, NFR-1 |
| Cuerpo 200 | `PublicationDetailResponse` | RF-10, RF-11, RF-14 |
| No existe | `404` con `{"error":"producto_no_encontrado", ...}` | RF-18 |
| No visible | `404` idéntico al de no existente | RF-19 |
| Efectos secundarios | Ninguno | RF-21, NFR-5 |

Respuesta 200:

```json
{
  "id": "8f3c2b1a-0000-4000-8000-000000000000",
  "name": "Café molido 500 g",
  "price": 1234.5,
  "currency": "ARS",
  "stock": 7,
  "description": "Café de tueste medio, molido para filtro.",
  "sellerAccountId": "1a2b3c4d-0000-4000-8000-000000000000",
  "images": [
    { "id": "uuid", "url": "https://api.example.com/catalog/images/uuid" }
  ]
}
```

Sin descripción (`description` ausente) se serializa como `null`:

```json
{
  "id": "uuid",
  "name": "Zapatillas",
  "price": 10.00,
  "currency": "USD",
  "stock": null,
  "description": null,
  "sellerAccountId": "uuid",
  "images": []
}
```

- `images` es una lista vacía cuando la publicación no tiene imágenes, nunca `null`.
  **(RF-13)**
- Cada `images[].url` es la misma URL pública de la grilla. **(RF-12, NFR-6)**
- `sellerAccountId` viaja siempre que la publicación sea visible, aunque el vendedor
  no tenga tienda configurada; la ausencia de tienda la resuelve el storefront.
  **(RF-14, NFR-3)**
- La respuesta no contiene `stage`, `ownerAccountId`, email, credenciales, `createdAt`
  ni `updatedAt`. **(RF-15, RF-16)**

## 9. Visibilidad — RF-17, RF-18, RF-19

El detalle aplica exactamente las mismas cuatro condiciones que la grilla:

| Condición | Efecto | RF |
|---|---|---|
| `stage = published` | Excluye borradores y despublicados | RF-17 |
| `deletedAt IS NULL` | Excluye borrados lógicos | RF-17 |
| `ownerAccountId IS NOT NULL` | Excluye publicaciones sin dueño | RF-17 |
| `stock IS NULL OR stock > 0` | Incluye stock no informado; excluye `0` y negativos | RF-17 |

- Publicación inexistente → `404`. **(RF-18)**
- Publicación existente pero no visible → `404` con el mismo cuerpo, sin revelar que
  existe. **(RF-19)**
- Una publicación que se despublica o se agota entre la grilla y el detalle pasa a
  responder `404`. **(RF-17, RF-19, NFR-5)**

## 10. Conservación de contratos existentes — RF-21 … RF-24, NFR-5, NFR-7

- `GET /catalog/publication` (paginado), `PublicationResponse` y
  `PublicationPageResponse` no cambian. **(RF-22)**
- Las rutas y operaciones del panel (`ProductController`, `publish`, `unpublish`,
  `publish-catalog`) conservan comportamiento; `ProductResponse` solo suma
  `description`. **(RF-23)**
- Actuator, health checks y métricas no se tocan. **(RF-24, NFR-7)**
- El detalle es `@Transactional(readOnly = true)`, no escribe ni publica eventos.
  **(RF-21, NFR-5)**
- Las variables de entorno, el prefijo `/catalog` y la configuración de CORS no
  cambian. **(NFR-7)**

## 11. Manejo de errores

- Se reutiliza `ProductNotFoundException` del paquete `exception/`; su handler ya
  responde `404` con código `producto_no_encontrado`.
- No se agregan códigos ni handlers nuevos: inexistente y no visible comparten
  respuesta. **(RF-18, RF-19)**
- La descripción de 1001 caracteres se rechaza con `400` (`datos_invalidos`) por
  `BindException` en el alta y la edición multipart. **(RF-6)**
- Los mensajes visibles siguen en español y el contrato/documentación técnica en
  inglés. **(NFR-4)**

## 12. Pruebas

| Área | Qué verifica | RF |
|---|---|---|
| `ProductTest` (unitario) | `openNew`/`update` normalizan `null`, `""` y solo espacios a `null`; conservan contenido y acentos; publicar/despublicar no altera la descripción | RF-1 … RF-4, RF-7, RF-8 |
| `ProductFormValidationTest` (unitario) | Acepta 1000 caracteres inclusive; rechaza 1001; acepta `null`, `""` y solo espacios; el resto de las restricciones siguen vigentes | RF-5, RF-6 |
| `ProductResponseMapperTest` (unitario) | `toResponse` incluye `description`; `toPublicationDetail` arma el shape público, `sellerAccountId = ownerAccountId`, `images[].url` pública, lista vacía sin imágenes y `description` nula | RF-10 … RF-16, NFR-6 |
| `PublicationServiceTest` (unitario) | `detail` devuelve el DTO visible; lanza `ProductNotFoundException` si no existe o no es visible; pasa `PUBLISHED` y stock mínimo 0; carga imágenes; no invoca escrituras | RF-9 … RF-21 |
| `PublicationControllerWebTest` (MockMvc) | `GET /catalog/publication/{id}` responde 200 con todos los campos y 404 para inexistente/no visible; sin `stage`, sin email/credenciales; `description: null` se serializa como nula | RF-9, RF-10, RF-13 … RF-19 |
| `ProductControllerWebTest` (MockMvc) | El alta y la edición multipart aceptan `description`, la propagan al `ProductForm` y rechazan 1001 caracteres con 400; la respuesta del panel incluye `description` | RF-2, RF-5, RF-6, RF-23 |
| `ProductRepositoryIntegrationTest` (Testcontainers PostgreSQL) | `V2` crea `description VARCHAR(1000) NULL`; `findPublicById` devuelve solo publicados, no borrados, con dueño y stock nulo o positivo, y vacía para inexistente, borrador, despublicado, borrado, sin dueño, stock 0 y negativo | RF-1, RF-17 … RF-20 |
| `PublicationControllerWebTest` y `ActuatorConfigurationTest` (existentes) | La grilla pública y Actuator siguen verdes | RF-22, RF-24, NFR-5, NFR-7 |

- Unitarios: entidad, DTO de validación, mapper y servicio con repositorios mock.
- Web: `@WebMvcTest` con `MockMvc`.
- Integración: `@DataJpaTest` con PostgreSQL vía Testcontainers, aplicando `V1` + `V2`.
- `./mvnw verify` (Checkstyle + tests, cobertura mínima 90 % de líneas) al cerrar.

## 13. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Agregar `description` a `ProductResponse` se percibe como cambio del contrato del panel | Consumidores del panel | Es aditivo: no se quitan ni renombran campos; coordinar con `fes-panel-api` |
| `ddl-auto: validate` falla si `V2` no coincide con la entidad | Arranque y tests | Declarar `@Column(length = 1000)` y validar en Testcontainers con `V1` + `V2` |
| Diferencia entre validación `@Size` y normalización `strip()` | Descripciones con espacios externos | El límite se evalúa sobre el valor crudo; el `strip()` no permite superar el esquema |
| Exposición accidental de datos internos del vendedor | Privacidad | El DTO solo incluye `sellerAccountId`; el mapper no copia etapa, email, credenciales ni fechas |
| `GET /catalog/publication/{id}` con un `id` no UUID (p. ej. otra ruta) | Confusión de rutas | `@PathVariable UUID`; un valor no UUID produce 400, no afecta rutas existentes |
| Stock `0` visible por error | Inconsistencia con la grilla | Condición `stock is null or stock > 0` idéntica a `findPublished`, cubierta por test de integración |
| Fuga de existencia al distinguir 404 | Seguridad | Misma excepción y mensaje para inexistente y no visible |

## 14. Trazabilidad RF → entregable

| RF | Entregable principal |
|---|---|
| RF-1 | Columna `description` (`V2`) + campo en `Product` + `description` en `ProductForm`/`ProductResponse` |
| RF-2 | `ProductService.create/update` propagan `form.description()` a la entidad |
| RF-3 | `Product.normalizeDescription` convierte vacío/solo espacios en `null` |
| RF-4 | Normalización a `null` sin texto por defecto; `publishCatalog` usa `null` |
| RF-5 | `@Size(max = 1000)` en `ProductForm` y longitud `1000` en la entidad/columna |
| RF-6 | `@Size` rechaza 1001 con 400 antes de persistir |
| RF-7 | `publish()` no altera `description` |
| RF-8 | `unpublish()` no altera `description` |
| RF-9 | `PublicationController#detail` en `GET /catalog/publication/{id}` sin credenciales |
| RF-10 | `PublicationDetailResponse` + `toPublicationDetail` devuelven los campos pedidos |
| RF-11 | Imágenes cargadas con `ProductImageRepository.findByProductId` |
| RF-12 | Reutilización de `toImageResponse`/`UrlUtils.join` (misma URL pública) |
| RF-13 | `images` vacío cuando no hay imágenes |
| RF-14 | `sellerAccountId = product.getOwnerAccountId()` |
| RF-15 | DTO sin email, credenciales ni datos internos |
| RF-16 | DTO sin `stage` |
| RF-17 | `findPublicById` con las cuatro condiciones de visibilidad |
| RF-18 | `ProductNotFoundException` → 404 en inexistente |
| RF-19 | Mismo 404 para no visible, sin revelar existencia |
| RF-20 | `id` UUID estable como clave de la ruta |
| RF-21 | `detail` con `@Transactional(readOnly = true)` y sin escrituras |
| RF-22 | `PublicationResponse`/`PublicationPageResponse` sin cambios |
| RF-23 | Rutas/operaciones del panel sin cambios; `description` solo aditiva |
| RF-24 | Actuator, health y métricas sin cambios |

## 15. Trazabilidad NFR → entregable

| NFR | Entregable |
|---|---|
| NFR-1 | Endpoint público sin sesión/token/credenciales |
| NFR-2 | Detalle resuelto solo con `catalog` (`products`, `product_images`) |
| NFR-3 | No se persiste ni gestiona la tienda; solo `sellerAccountId` |
| NFR-4 | Mensajes en español; contrato/código en inglés; docs del plan en español |
| NFR-5 | Detalle de solo lectura; grilla y panel sin cambios funcionales |
| NFR-6 | Misma URL de imagen que la grilla vía `ProductResponseMapper` + `UrlUtils` |
| NFR-7 | Rutas, health checks y métricas intactos |

## 16. Orden de implementación

1. Migración `V2__add_product_description.sql`. **(RF-1, RF-5)**
2. Entidad `Product` con `description` y normalización; adaptación de llamadas.
   **(RF-1 … RF-4, RF-7, RF-8)**
3. `ProductForm` con `@Size(max = 1000)` y propagación desde `ProductService`.
   **(RF-2, RF-5, RF-6)**
4. `ProductResponse` + `toResponse` con `description`. **(RF-1, RF-2, RF-23)**
5. `PublicationDetailResponse` y `ProductRepository.findPublicById`.
   **(RF-9, RF-10, RF-17 … RF-20)**
6. `ProductResponseMapper.toPublicationDetail` y `PublicationService.detail`.
   **(RF-10 … RF-16, RF-21)**
7. `PublicationController#detail`. **(RF-9, RF-20)**
8. Tests unitarios, web e integración. **(RF-1 … RF-24, NFR-1 … NFR-7)**
9. Regresión de grilla, panel y Actuator + `./mvnw verify`. **(RF-22 … RF-24)**

## 17. Fuera de alcance (no implementado aquí)

- UI del storefront (maquetado, URL propia, compartir, recarga y vuelta al catálogo).
- Gestión de la tienda del vendedor en `account-api` y su contrato de lectura.
- Compra, reserva de stock, pedidos, pagos y checkout.
- Búsqueda por descripción, categorías, facetas, reseñas y valoraciones.
- Alta, transformación, redimensionado o miniaturas de imágenes.
- Autenticación, login y validación de sesión.
- Eventos RabbitMQ, caché/CDN y agregados de catálogo.
- Cambios en el contrato paginado de la grilla y en Actuator.
