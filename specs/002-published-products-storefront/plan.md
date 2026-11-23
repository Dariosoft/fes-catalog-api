# Plan 002 — Lectura pública paginada de productos publicados

Diseño técnico de `specs/002-published-products-storefront/spec.md` para
`catalog-api`. Respeta `AGENTS.md` y las skills que menciona:
`/spring-boot-project-creator` en su **opción Layered** (sin regenerar el proyecto
con Spring Initializr), `/spring-boot-layered-template` para ubicar clases que no
encajan en las carpetas básicas y `/clean-code-guard` como guardia de calidad.
Cubre los RF-1 … RF-34 de la spec y los NFR-1 … NFR-7. No agrega migraciones,
dependencias ni tablas: el esquema de la spec 001 ya contiene lo necesario.

## 1. Alcance y principios

- Capacidad dueña: **catálogo** (`com.friendlyeshop.catalog`). La lectura pública se
  resuelve solo con la base `catalog`. **(NFR-3)**
- **Layered Architecture**: se usan los paquetes existentes `controller/`,
  `service/`, `repository/`, `model/`, `model/dto/`, `mapper/`, `util/`, `config/` y
  `exception/`. No se crean paquetes nuevos.
- El prefijo `/catalog` deja de repetirse en cada controller: `ApiPathConfig`
  implementa `WebMvcConfigurer#configurePathMatch` y aplica `addPathPrefix("/catalog")`
  a los controllers del paquete `controller/`. Actuator usa su propio handler mapping y
  queda fuera del prefijo. **(RF-36, RF-37)**
- `PublicationController` (`/publication`) agrupa todo lo relativo a publicación: la lectura
  pública `GET /catalog/publication`, la publicación de catálogo `POST /catalog/publication/publish-catalog`, la
  publicación de un producto `POST /catalog/publication/publish/{id}` y su
  despublicación `POST /catalog/publication/unpublish/{id}`. **(RF-1, RF-31, RF-35)**
- `ProductController` (`/products`) conserva el alta, la edición, el borrado y el
  listado por dueño: `GET /catalog/products?ownerAccountId=…`, con el mismo contrato.
  **(RF-30, NFR-7)**
- El traslado de las rutas de publicación a `/catalog/publication/...` se coordina con
  `fes-panel-api`, que las consume; el listado, el alta, la edición y el borrado del
  panel no cambian. **(RF-30, RF-35)**
- La selección aplica cuatro condiciones simultáneas: etapa `published`,
  `deleted_at IS NULL`, `owner_account_id IS NOT NULL` y `stock IS NULL OR stock > 0`.
  **(RF-3, RF-4, RF-5, RF-33)**
- La respuesta nunca expone `ownerAccountId`, `stage`, `createdAt` ni `updatedAt`.
  **(RF-17)**
- Los parámetros `page` y `size` **se ajustan, nunca rechazan**: no producen 400.
  **(RF-12, RF-13, RF-34)**
- CORS se habilita únicamente sobre `/catalog/**`, con orígenes de
  `BROWSER_ORIGINS` y sin credenciales. **(RF-24 … RF-29)**
- Actuator, health checks y métricas no cambian y quedan fuera de CORS. **(RF-28,
  RF-32, NFR-5)**

## 2. Estado actual relevante (as-is)

- Spring Boot 4.1 / Java 25, paquete `com.friendlyeshop.catalog`, sin cambios de
  dependencias ni de `pom.xml`.
- `ProductRepository` solo conoce consultas acotadas al dueño
  (`findByOwnerAccountIdAndDeletedAtIsNull`, `…NameContainingIgnoreCase…`,
  `findByIdAndOwnerAccountIdAndDeletedAtIsNull`,
  `findByOwnerAccountIdAndStageAndDeletedAtIsNull`); **no existe** ninguna consulta
  global. **(RF-2)**
- `ProductImageRepository` solo expone `findByProductId(UUID)`. **(RF-16)**
- `ProductResponseMapper` compone `ProductResponse` (con dueño y etapa) e
  `ProductImageResponse(id, url)` con
  `{fes.catalog.public-base-url}/catalog/images/{imageId}` vía `UrlUtils.join`.
  **(RF-18, NFR-6)**
- `ProductController` pasa a `@RequestMapping("/products")` y expone el listado, el
  alta, la edición y el borrado; las tres acciones de publicación se mudan a
  `PublicationController`. **(RF-30, RF-35)**
- `application.yaml` no define CORS ni `BROWSER_ORIGINS`; el servicio no tiene
  `WebMvcConfigurer` alguno. **(RF-24 … RF-29)**
- `@ConfigurationPropertiesScan` ya está activo en `CatalogApiApplication`, con los
  records `CatalogProperties` y `StorageProperties` validados. Las propiedades nuevas
  se registran por el mismo mecanismo (anotar el record con `@Component` rompía el
  binding de constructor en la spec 001). **(NFR-2)**
- `V1__create_products.sql` ya tiene `stage`, `deleted_at`, `owner_account_id`
  nullable y `stock` nullable, con índice parcial por dueño excluyendo borrados.
  **(RF-3, RF-4, RF-5, RF-23, RF-33)**

## 3. Ubicación del código (Layered + extensiones)

```text
src/main/java/com/friendlyeshop/catalog
├── controller/
│   ├── ProductController                    # /products: CRUD y listado           RF-30
│   ├── ProductImageController               # /images: lectura pública            RF-18
│   └── PublicationController                # /publication: lectura y publicación  RF-1,35
├── service/
│   ├── ProductService                        # panel (sin cambios)
│   ├── ProductImageService                   # imágenes públicas (sin cambios)
│   ├── ProductLookup                         # panel (sin cambios)
│   └── PublicationService                    # lectura, publicación y despublicación
├── repository/
│   ├── ProductRepository                     # + findPublished / findPublishedByName  RF-2..6,23,33
│   └── ProductImageRepository                # + findByProductIdIn                   RF-16,19
├── model/
│   ├── Product                               # sin cambios
│   ├── ProductImage                          # sin cambios
│   └── dto/
│       ├── ProductResponse / ProductImageResponse        # sin cambios (panel)
│       ├── PublicationResponse          # NUEVO ítem público                    RF-15..17
│       └── PublicationPageResponse      # NUEVO sobre paginado                  RF-14,20..22,34
├── mapper/
│   └── ProductResponseMapper                 # + toPublication / toPublicationPage RF-15..19
├── config/
│   ├── CatalogProperties                     # sin cambios
│   ├── CorsProperties                        # NUEVO: fes.cors.browser-origins       RF-29, NFR-2
│   └── CorsConfig                            # NUEVO: WebMvcConfigurer /catalog/**    RF-24..28
├── util/
│   └── UrlUtils                              # sin cambios
└── exception/
    └── GlobalExceptionHandler                # sin cambios (la lectura pública no agrega errores)
```

Decisiones de ubicación según `/spring-boot-layered-template`:

- `controller/PublicationController` se separa de `ProductController` porque
  expone un contrato público distinto (sin `ownerAccountId`, con paginación y sin
  datos internos). Así el contrato del panel queda literalmente sin tocar.
  **(RF-1, RF-30)**
- `service/PublicationService` es el caso de uso completo de publicación:
  `list` normaliza parámetros, consulta, calcula totales y compone la página;
  `publish`, `unpublish` y `publishCatalog` aplican las transiciones de etapa.
  Un solo servicio cubre todos los casos de publicación. No se crea un
  `validation/` ni un `util/` para la normalización porque no es una regla
  reutilizada fuera de este caso de uso y vive bien como métodos privados con
  constantes con nombre. **(RF-9 … RF-13)**
- Los DTOs públicos van a `model/dto/` junto a los del panel; no se reutiliza
  `ProductResponse` porque expone dueño, etapa y fechas. **(RF-14 … RF-17)**
- El mapeo público se agrega a `ProductResponseMapper` (ya es el dueño de la
  conversión entidad → respuesta y del armado de la URL de imagen) en lugar de
  duplicar `toImageResponse` y `UrlUtils.join` en un mapper nuevo. **(RF-18, NFR-6)**
- `config/CorsProperties` y `config/CorsConfig` siguen el criterio del template:
  `config/` para propiedades y configuración de framework (CORS). **(RF-24 … RF-29)**
- No se agregan paquetes `client/`, `http/`, `security/` ni `messaging/`: no hay
  integración externa ni autenticación en este corte. **(NFR-1)**

## 4. Contrato HTTP público — RF-1, RF-14, RF-30

`GET /catalog/publication?name=&page=&size=` — sin sesión, sin token, sin
credenciales. **(RF-1, NFR-1)**

| Aspecto | Definición | RF |
|---|---|---|
| Ruta | `GET /catalog/publication` | RF-1 |
| `name` | Opcional, texto libre; contiene, sin distinguir mayúsculas | RF-6, RF-8 |
| `page` | Opcional, entero ≥ 1; por defecto 1 | RF-9, RF-10 |
| `size` | Opcional, entero ≥ 1; por defecto 24; máximo efectivo 100 | RF-11, RF-12 |
| Cuerpo de respuesta | `PublicationPageResponse` | RF-14 |
| Errores de parámetros | Ninguno: se ajustan y responden 200 | RF-12, RF-13, RF-34 |

`PublicationController`:

```java
@RestController
@RequestMapping("/publication")
public class PublicationController {

    private final PublicationService publicationService;

    @GetMapping
    public PublicationPageResponse list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return publicationService.list(name, page, size);
    }

    @PostMapping(value = "/publish-catalog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PublishCatalogResponse publishCatalog(
            @RequestParam UUID ownerAccountId,
            @Valid @RequestBody(required = false) PublishCatalogRequest request) {
        return publicationService.publishCatalog(ownerAccountId, request);
    }

    @PostMapping("/publish/{id}")
    public ProductResponse publish(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        return publicationService.publish(ownerAccountId, id);
    }

    @PostMapping("/unpublish/{id}")
    public ProductResponse unpublish(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        return publicationService.unpublish(ownerAccountId, id);
    }
}
```

- `page` y `size` se reciben como `String` a propósito: si se declararan como
  `int`, Spring respondería 400 con `MethodArgumentTypeMismatchException` (ya
  traducido en `GlobalExceptionHandler`), incumpliendo RF-13.
- El controlador es delgado: no importa `repository/` ni conoce entidades.
- No hay cabecera de autenticación ni validación de sesión. **(NFR-1)**
- El panel sigue usando `ProductController` sin cambios. **(RF-30)**

## 5. Normalización de paginación y ajuste de página — RF-9 … RF-13, RF-20 … RF-22, RF-34

`PublicationService#list` aplica esta secuencia:

1. **`size`**: si es nulo, vacío, no numérico o menor a 1 → `24`; si es mayor a
   `100` → `100`. **(RF-11, RF-12, RF-13)**
2. **`page`**: si es nulo, vacío, no numérico o menor a 1 → `1`. **(RF-9, RF-10,
   RF-13)**
3. **`name`**: se recorta; si queda vacío → sin filtro. **(RF-8)**
4. Se consulta con `PageRequest.of(page - 1, size, SORT)`.
5. `totalItems = page.getTotalElements()` y `totalPages = page.getTotalPages()`.
   **(RF-20, RF-21)**
6. Si `totalPages > 0` y `page > totalPages`, se repite la consulta con
   `PageRequest.of(totalPages - 1, size, SORT)` y se responde con `page = totalPages`
   y los totales reales. **(RF-34)**
7. Si `totalItems == 0`, se responde `items` vacío, `totalItems = 0`,
   `totalPages = 0`, `page` = página normalizada solicitada y `size` = tamaño
   aplicado. **(RF-22)**

Constantes con nombre en el servicio: `DEFAULT_PAGE = 1`, `DEFAULT_SIZE = 24`,
`MAX_SIZE = 100`. El parseo se hace con un `try/catch` de
`NumberFormatException` acotado a la conversión, sin tragar otros errores.

`totalPages` se toma del `Page` de Spring Data, que ya calcula
`ceil(totalItems / size)` con el `size` efectivo. **(RF-21)**

## 6. Filtro por nombre — RF-6, RF-7, RF-8

- `name` se aplica como `contains` con `lower(...)` en ambos lados de la comparación:
  ignora mayúsculas y minúsculas. **(RF-6)**
- No se normalizan acentos ni diacríticos: `café` no coincide con `cafe`. Es el
  comportamiento por defecto de PostgreSQL con `lower()` y se verifica en un test.
  **(RF-7)**
- `name` vacío o solo espacios se convierte en `null` antes de consultar, y el
  servicio usa la consulta sin filtro. **(RF-8)**
- El valor se recorta y se escapa del lado de la consulta JPQL usando parámetro
  enlazado y `concat('%', :name, '%')`; nunca se concatena SQL. **(RF-6)**

## 7. Consultas y orden estable — RF-2 … RF-5, RF-23, RF-33

`ProductRepository` gana dos consultas JPQL explícitas (los nombres derivados
resultarían ilegibles por la cantidad de condiciones):

```java
@Query("""
        select p from Product p
        where p.stage = :stage
          and p.deletedAt is null
          and p.ownerAccountId is not null
          and (p.stock is null or p.stock > :minimumStock)
        """)
Page<Product> findPublished(
        @Param("stage") ProductStage stage,
        @Param("minimumStock") int minimumStock,
        Pageable pageable);

@Query("""
        select p from Product p
        where p.stage = :stage
          and p.deletedAt is null
          and p.ownerAccountId is not null
          and (p.stock is null or p.stock > :minimumStock)
          and lower(p.name) like lower(concat('%', :name, '%'))
        """)
Page<Product> findPublishedByName(
        @Param("stage") ProductStage stage,
        @Param("minimumStock") int minimumStock,
        @Param("name") String name,
        Pageable pageable);
```

- `stage = published` incluye solo publicados. **(RF-3)**
- `deletedAt is null` excluye borrados lógicos. **(RF-4)**
- `stock is null or stock > 0` incluye el stock no informado y excluye cero y
  negativos (en SQL, `NULL > 0` no es verdadero). **(RF-5)**
- `ownerAccountId is not null` excluye publicados sin dueño. **(RF-33)**
- Sin filtro por dueño: devuelve todos los vendedores, sin agrupar. **(RF-2)**
- Orden: `Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))`, pasado en el
  `PageRequest`; el `id` ascendente desempata filas con la misma fecha de creación y
  garantiza el mismo orden entre páginas consecutivas. **(RF-23)**
- Las consultas son de lectura; no hay `save`, `delete` ni modificación de estado en
  el flujo. **(RF-31, NFR-7)**

`ProductImageRepository` gana una consulta batch para evitar N+1:

```java
List<ProductImage> findByProductIdIn(Collection<UUID> productIds);
```

## 8. DTOs y contrato JSON — RF-14 … RF-22

`PublicationPageResponse` (`model/dto/`):

```json
{
  "items": [
    {
      "id": "uuid",
      "name": "Café molido 500 g",
      "price": 1234.5,
      "currency": "ARS",
      "stock": 7,
      "images": [
        { "id": "uuid", "url": "https://api.example.com/catalog/images/uuid" }
      ]
    }
  ],
  "page": 1,
  "size": 24,
  "totalItems": 57,
  "totalPages": 3
}
```

```java
public record PublicationPageResponse(
        List<PublicationResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}

public record PublicationResponse(
        UUID id,
        String name,
        BigDecimal price,
        Currency currency,
        Integer stock,
        List<ProductImageResponse> images) {
}
```

- `items`, `page`, `size`, `totalItems` y `totalPages` siempre presentes. **(RF-14)**
- Cada ítem expone solo `id`, `name`, `price`, `currency`, `stock` e `images`.
  **(RF-15)**
- `images` reutiliza `ProductImageResponse(id, url)` del panel, con los campos `id`
  y `url`. **(RF-16, NFR-6)**
- No se incluye `ownerAccountId`, `stage`, `createdAt` ni `updatedAt`. **(RF-17)**
- `images` es una lista vacía cuando el producto no tiene imágenes, nunca `null`.
  **(RF-19)**
- `currency` se serializa como `ARS`/`USD` con el enum existente; `ProductStage` no
  aparece en este contrato, así que su `@JsonValue` no interviene.

## 9. Mapper y URLs públicas de imagen — RF-15 … RF-19, NFR-6

`ProductResponseMapper` gana dos métodos y reutiliza el armado de URL existente:

```java
public PublicationResponse toPublication(Product product, List<ProductImage> images);

public PublicationPageResponse toPublicationPage(List<Product> products,
        Map<UUID, List<ProductImage>> imagesByProductId, int page, int size,
        long totalItems, int totalPages);
```

- `toPublication` reutiliza `toImageResponse` y `UrlUtils.join` tal como están:
  `url = {fes.catalog.public-base-url}/catalog/images/{imageId}`. Es la misma URL que
  ya sirve `GET /catalog/images/{imageId}` sin credenciales. **(RF-18, NFR-6)**
- Sin imágenes → `List.of()`. **(RF-19)**
- `toPublicationPage` conserva el orden recibido de la consulta y no reordena en
  memoria. **(RF-23)**

## 10. Servicio de lectura pública — RF-2 … RF-8, RF-20 … RF-23, RF-31, RF-33, RF-34

`PublicationService` (`service/`, `@Service`):

- `list(String name, String rawPage, String rawSize)` con
  `@Transactional(readOnly = true)`.
- Depende solo de `ProductRepository`, `ProductImageRepository` y
  `ProductResponseMapper`. No usa `ProductLookup` (no hay dueño) ni
  `ImageStorage` (no lee bytes).
- Pasos:
  1. Normalizar `size` y `page` (§5) y el `name` (§6).
  2. Consultar `findPublished(PUBLISHED, 0, pageRequest)` o
     `findPublishedByName(PUBLISHED, 0, name, pageRequest)`.
  3. Reajustar la página si excede `totalPages` (§5, paso 6). **(RF-34)**
  4. Cargar imágenes de la página con `findByProductIdIn(ids)` y agrupar por
     `productId` en un `Map<UUID, List<ProductImage>>`. Si la página está vacía, no
     se consulta imágenes.
  5. Delegar la composición en `ProductResponseMapper.toPublicationPage(...)`.
- No muta entidades ni publica eventos. **(RF-31, NFR-7)**
- Sin resultados → `items` vacío y totales 0 (§5, paso 7). **(RF-22)**

## 11. CORS del Storefront — RF-24 … RF-29

`CorsProperties` (`config/`):

```java
@ConfigurationProperties(prefix = "fes.cors")
public record CorsProperties(List<String> browserOrigins) {

    public CorsProperties {
        browserOrigins = browserOrigins == null
                ? List.of()
                : browserOrigins.stream().map(String::trim).filter(origin -> !origin.isBlank()).toList();
    }
}
```

- Registrado por `@ConfigurationPropertiesScan`; sin `@Component`. **(NFR-2)**
- Lista ausente, vacía o con solo espacios → lista vacía; **no** se valida con
  `@NotBlank`, por lo que el arranque no falla. **(RF-29)**

`CorsConfig` (`config/`):

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/catalog/**")
                .allowedOrigins(properties.browserOrigins().toArray(String[]::new))
                .allowedMethods(HttpMethod.GET.name())
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
```

- Solo `/catalog/**`: Actuator (`/actuator/**`) y cualquier otra ruta quedan sin
  cabeceras CORS. **(RF-28)**
- `GET` desde un origen permitido → `Access-Control-Allow-Origin` con ese origen.
  **(RF-24)**
- `OPTIONS` de verificación previa → `Access-Control-Allow-Origin` del origen
  permitido y `Access-Control-Allow-Methods` con `GET`. **(RF-25)**
- `allowCredentials(false)`: nunca se envía `Access-Control-Allow-Credentials`.
  **(RF-26)**
- Origen no listado → sin cabeceras CORS; el navegador bloquea, el servidor responde
  normalmente. **(RF-27)**
- Lista vacía → `allowedOrigins(...)` se invoca con un arreglo vacío, que reemplaza
  el comodín `*` por defecto de `CorsRegistration`; ningún origen recibe cabeceras y
  el servicio arranca igual. **(RF-29)**
- No se usa `allowedOriginPatterns` ni comodines: solo la lista explícita.

## 12. Configuración por entorno — NFR-2, RF-29

`application.yaml` agrega solo:

```yaml
fes:
  cors:
    browser-origins: ${BROWSER_ORIGINS:}
```

| Variable | Propiedad | Uso | RF/NFR |
|---|---|---|---|
| `BROWSER_ORIGINS` | `fes.cors.browser-origins` | Orígenes de navegador permitidos, separados por coma; vacío o ausente = ninguno | RF-24 … RF-29, NFR-2 |

- `.env.sample` documenta `BROWSER_ORIGINS` con un valor local de ejemplo.
- El valor real de `BROWSER_ORIGINS` en Minikube y Hostinger pertenece a `fes-infra`;
  no se fija aquí. **(fuera de alcance)**
- Las variables existentes (`DATABASE_*`, `S3_*`, `PUBLIC_API_BASE_URL`,
  `RABBITMQ_*`, `SERVER_PORT`) no cambian. **(NFR-2)**

## 13. Conservación de contratos existentes — RF-30 … RF-32, NFR-5, NFR-7

- `ProductController`, `ProductService`, `PublicationService`,
  `ProductResponseMapper.toResponse` y `ProductResponse` no cambian de
  comportamiento. **(RF-30)**
- La lectura pública es `@Transactional(readOnly = true)` y no guarda ni elimina.
  **(RF-31, NFR-7)**
- `management.endpoints.web.exposure` y los grupos de health no se tocan; CORS no
  aplica a `/actuator/**`. **(RF-28, RF-32, NFR-5)**
- No se agregan ni modifican migraciones Flyway. **(fuera de alcance)**

## 14. Manejo de errores

- La lectura pública no agrega códigos de error nuevos: `page`/`size` inválidos se
  ajustan y responden 200. **(RF-12, RF-13)**
- `GlobalExceptionHandler` se mantiene igual; un fallo inesperado de datos sigue
  respondiendo el 500 por defecto de Spring.
- Los mensajes visibles siguen en español y el contrato/documentación en inglés.
  **(NFR-4)**

## 15. Pruebas (criterios de finalización)

| Área | Qué verifica | RF |
|---|---|---|
| `PublicationServiceTest` | Defaults 1/24; no numérico; menor a 1; `size` 101 → 100; `page` más allá de `totalPages` → última página con totales reales; sin resultados; `name` vacío; `name` con espacios; agrupación de imágenes | RF-8 … RF-14, RF-20, RF-21, RF-22, RF-34 |
| `ProductResponseMapperTest` | Shape público sin dueño/etapa; `images` vacío; `url` pública con la base normalizada | RF-15 … RF-19, NFR-6 |
| `PublicationControllerWebTest` (MockMvc) | 200 con `items/page/size/totalItems/totalPages`; paso de `name`, `page` y `size`; parámetros no numéricos no producen 400; sin dueño ni etapa en el JSON | RF-1, RF-6, RF-9 … RF-17, RF-22 |
| `CorsConfigurationTest` (MockMvc) | `GET /catalog/**` con origen permitido recibe `Access-Control-Allow-Origin`; `OPTIONS` preflight con `GET`; origen ajeno sin cabeceras; `/actuator/health` sin CORS; `BROWSER_ORIGINS` vacía arranca sin orígenes; sin `Allow-Credentials` | RF-24 … RF-29 |
| `CorsPropertiesTest` | Binding de lista separada por comas; vacío/ausente → lista vacía; recorte y descarte de blancos | RF-29, NFR-2 |
| `ProductRepositoryIntegrationTest` (Testcontainers PostgreSQL) | Solo publicados, no borrados, con dueño y `stock is null or stock > 0`; incluye stock no informado y excluye cero/negativo, borrado, borrador, despublicado y sin dueño; filtro por nombre con mayúsculas distintas y con acentos distintos; orden `createdAt` desc + `id` asc estable entre páginas | RF-2 … RF-7, RF-23, RF-33 |
| `ProductControllerWebTest` y `ActuatorConfigurationTest` (existentes) | El contrato del panel y Actuator siguen verdes | RF-30, RF-32, NFR-5 |

- Unitarios: servicio y mapper con repositorios mock.
- Web: `@WebMvcTest` con `MockMvc`, incluidas las cabeceras CORS.
- Integración: `@DataJpaTest` con PostgreSQL vía Testcontainers, reutilizando la
  infraestructura de `ProductRepositoryIntegrationTest`.
- `./mvnw verify` (Checkstyle + tests, cobertura mínima 90 % de líneas) al cerrar.

## 16. Dependencias y coordinación

- **Sin dependencias nuevas**: `spring-boot-starter-web` ya aporta
  `WebMvcConfigurer`, `CorsRegistry` y `CorsConfiguration`.
- **Sin migraciones**: el esquema de la spec 001 ya soporta las consultas.
- Coordinación con `fes-infra`: definir el valor de `BROWSER_ORIGINS` por entorno y
  la exposición pública de `GET /catalog/publication` y
  `GET /catalog/images/{imageId}`. No se inventan valores ni ingress aquí.
- Coordinación con `fes-panel-api`: sus llamadas de publicación y despublicación pasan
  a `POST /catalog/publication/publish/{id}` y
  `POST /catalog/publication/unpublish/{id}`; `POST /catalog/publication/publish-catalog` no cambia.
- Coordinación con `fes-client-web`: consume `items/page/size/totalItems/totalPages`
  y usa la misma `images[].url` que ya muestra el panel.

## 17. Mapa resumen RF → entregable

| RF | Entregable principal |
|---|---|
| RF-1 | `PublicationController#list` en `GET /catalog/publication` |
| RF-35 | `PublicationController` con `POST /catalog/publication/publish-catalog`, `POST /catalog/publication/publish/{id}` y `POST /catalog/publication/unpublish/{id}` |
| RF-36 | `ApiPathConfig#configurePathMatch` con `addPathPrefix("/catalog")` |
| RF-37 | Actuator fuera del prefijo; verificado en `CorsConfigurationTest` y en el arranque |
| RF-2 | `findPublished` sin filtro por dueño |
| RF-3 | Condición `p.stage = published` |
| RF-4 | Condición `p.deletedAt is null` |
| RF-5 | Condición `p.stock is null or p.stock > 0` |
| RF-6 | `findPublishedByName` con `lower(...) like lower(...)` |
| RF-7 | Test de acentos significativos (sin normalización) |
| RF-8 | `name` recortado a `null` cuando está en blanco |
| RF-9 | `DEFAULT_PAGE = 1` |
| RF-10 | `page` base 1 → `PageRequest.of(page - 1, …)` |
| RF-11 | `DEFAULT_SIZE = 24` |
| RF-12 | `MAX_SIZE = 100` |
| RF-13 | Parseo tolerante de `page`/`size` como `String` |
| RF-14 | `PublicationPageResponse` |
| RF-15 | `PublicationResponse` |
| RF-16 | `ProductImageResponse(id, url)` reutilizado |
| RF-17 | DTO público sin `ownerAccountId` ni `stage` |
| RF-18 | `ProductResponseMapper.toPublication` + `UrlUtils.join` |
| RF-19 | `images` vacío cuando no hay imágenes |
| RF-20 | `totalItems = page.getTotalElements()` |
| RF-21 | `totalPages = page.getTotalPages()` |
| RF-22 | Página vacía → totales 0 |
| RF-23 | `Sort desc(createdAt) + asc(id)` |
| RF-24 | `CorsConfig` con `/catalog/**` y origen permitido |
| RF-25 | Preflight `OPTIONS` con `GET` |
| RF-26 | `allowCredentials(false)` |
| RF-27 | Origen no listado → sin cabeceras CORS |
| RF-28 | Mapping limitado a `/catalog/**` |
| RF-29 | `CorsProperties` tolerante a lista vacía/ausente |
| RF-30 | `ProductController` y servicios del panel sin cambios |
| RF-31 | Servicio `readOnly = true`, sin escrituras |
| RF-32 | Actuator y health sin cambios ni CORS |
| RF-33 | Condición `p.ownerAccountId is not null` |
| RF-34 | Reconsulta en `totalPages - 1` con totales reales |

## 18. Orden de implementación

1. DTOs públicos `PublicationResponse` y `PublicationPageResponse`
   (**RF-14 … RF-17**).
2. Consultas `findPublished`/`findPublishedByName` y `findByProductIdIn`
   (**RF-2 … RF-6, RF-23, RF-33**).
3. Métodos públicos en `ProductResponseMapper` (**RF-15 … RF-19**, NFR-6).
4. `PublicationService` con normalización, totales, clamp y filtro
   (**RF-6 … RF-14, RF-20 … RF-23, RF-31, RF-34**).
5. `PublicationController` (**RF-1, RF-14**).
6. `CorsProperties`, `CorsConfig`, `application.yaml` y `.env.sample`
   (**RF-24 … RF-29, NFR-2**).
7. Tests unitarios, web, de configuración e integración (**RF-1 … RF-34**).
8. Regresión del panel y Actuator + `./mvnw verify` (**RF-30, RF-32**, criterios de
   finalización).

## 19. Fuera de alcance (no implementado aquí)

- UI del Storefront: grilla, buscador del header, paginación visual y estados de
  carga, vacío y error.
- Valores de `BROWSER_ORIGINS` en los overlays de Minikube y Hostinger, ingress y
  exposición pública (pertenece a `fes-infra`).
- Detalle de producto, ordenamiento elegido por el visitante, categorías, facetas,
  filtros por precio o vendedor y búsqueda difusa o por acentos.
- Carrito, checkout y compra.
- Migraciones, cambios de esquema, datos de ejemplo, caché/CDN y eventos RabbitMQ.
- Autenticación, login y validación de sesión.
- Cambios en el contrato del panel y en las operaciones de escritura.
