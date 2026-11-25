# Tasks 003 — Detalle público de publicación con descripción y referencia al vendedor

Tareas de implementación para `spec.md` y `plan.md` de este directorio, en orden de
dependencia y pensadas para ~20–30 minutos cada una. Usan la arquitectura Layered de
`AGENTS.md` (`/spring-boot-project-creator` opción Layered, sin regenerar el
proyecto, `/spring-boot-layered-template` y `/clean-code-guard`). Estado inicial:
ninguna tarea hecha (`[ ]`). Se agrega una migración (`V2`) y ninguna dependencia
nueva.

## Migración y modelo

- [x] **T1. Migración `V2__add_product_description.sql`**
  - Cubre: RF-1, RF-5
  - Crear `src/main/resources/db/migration/V2__add_product_description.sql` con
    `ALTER TABLE products ADD COLUMN description VARCHAR(1000) NULL;`. No editar
    `V1__create_products.sql`.
  - Done when: Flyway aplica `V1` + `V2` en una base limpia y la columna
    `description` aparece en `information_schema.columns` con longitud 1000 y
    nulabilidad habilitada.

- [x] **T2. Entidad `Product` con `description` y normalización**
  - Cubre: RF-1, RF-3, RF-4, RF-7, RF-8
  - Agregar `@Column(name = "description", length = 1000) private String description;`
    y un método privado estático que convierta `null`/`isBlank()` en `null` y, en
    otro caso, aplique `strip()`. `publish()`, `unpublish()` y `deleteLogically()`
    no deben tocar la descripción.
  - Done when: la entidad compila con `ddl-auto: validate` contra `V2` y un test
    unitario confirma que `null`, `""` y `"   "` quedan en `null`, que un texto con
    acentos se conserva y que publicar/despublicar no modifican la descripción.

- [x] **T3. `openNew` y `update` reciben `description` y se adaptan las llamadas**
  - Cubre: RF-1, RF-2, RF-3
  - Agregar `String description` como último parámetro de `Product.openNew(...)` y
    `Product.update(...)`, asignando el valor normalizado. Actualizar las llamadas
    de `ProductService`, `PublicationService` (`publishCatalog` con `null`) y los
    tests existentes para que compilen.
  - Done when: `./mvnw -q -DskipTests compile` pasa y no queda ninguna llamada con
    la firma anterior.

- [x] **T4. `ProductForm` con `description` opcional y `@Size(max = 1000)`**
  - Cubre: RF-5, RF-6
  - Agregar `@Size(max = 1000) String description` al record `ProductForm` y ajustar
    las construcciones de test afectadas.
  - Done when: los tests de validación aceptan `null`, `""`, solo espacios y 1000
    caracteres, y rechazan 1001 caracteres con una violación en `description`.

- [x] **T5. `ProductService` propaga la descripción en alta y edición**
  - Cubre: RF-2, RF-5
  - Pasar `form.description()` a `Product.openNew(...)` en `create` y a
    `product.update(...)` en `update`, sin duplicar la validación de longitud (la
    aporta `@Size` en `ProductForm`).
  - Done when: un test unitario verifica que la descripción del formulario llega a
    la entidad en alta y en edición.

- [x] **T6. `ProductResponse` y `toResponse` incluyen `description` (panel, aditivo)**
  - Cubre: RF-1, RF-2, RF-23
  - Agregar `String description` a `ProductResponse` y pasarlo desde
    `ProductResponseMapper.toResponse`. No cambiar rutas ni operaciones del panel.
  - Done when: un test del mapper confirma que `toResponse` devuelve la descripción
    (o `null`) sin alterar los campos existentes y `ProductControllerWebTest` sigue
    compilando con el constructor actualizado.

## Detalle público

- [x] **T7. DTO `PublicationDetailResponse`**
  - Cubre: RF-10, RF-11, RF-13, RF-14, RF-15, RF-16
  - Crear el record `PublicationDetailResponse(UUID id, String name, BigDecimal price,
    Currency currency, Integer stock, String description, UUID sellerAccountId,
    List<ProductImageResponse> images)`.
  - Done when: el record compila, expone exactamente esos campos y no contiene
    `stage`, `ownerAccountId`, `createdAt`, `updatedAt`, email ni credenciales.

- [x] **T8. `ProductRepository.findPublicById`**
  - Cubre: RF-17, RF-18, RF-19, RF-20
  - Agregar la consulta JPQL `Optional<Product> findPublicById(UUID id, ProductStage
    stage, int minimumStock)` con `id = :id`, `stage = :stage`,
    `deletedAt is null`, `ownerAccountId is not null` y
    `(stock is null or stock > :minimumStock)`.
  - Done when: la consulta compila, arranca con `ddl-auto: validate` y un test de
    integración devuelve la publicación solo cuando cumple las cuatro condiciones.

- [x] **T9. `ProductResponseMapper.toPublicationDetail`**
  - Cubre: RF-10, RF-11, RF-12, RF-13, RF-14, RF-15, RF-16, NFR-6
  - Agregar el método que arma `PublicationDetailResponse`, reutiliza
    `toImageResponses`/`UrlUtils.join` para `images[].url`, usa
    `product.getOwnerAccountId()` como `sellerAccountId` y devuelve `List.of()`
    cuando no hay imágenes.
  - Done when: un test del mapper verifica el shape público, la URL pública de
    imagen, la lista vacía, `description` nula y que no aparecen etapa ni datos
    internos.

- [x] **T10. `PublicationService.detail`**
  - Cubre: RF-9, RF-10, RF-11, RF-13, RF-14, RF-17, RF-18, RF-19, RF-21
  - Implementar `detail(UUID id)` con `@Transactional(readOnly = true)`:
    `findPublicById(id, ProductStage.PUBLISHED, 0)`, `orElseThrow` con
    `ProductNotFoundException("La publicación no existe")`, carga de imágenes con
    `findByProductId` y delegación en `toPublicationDetail`.
  - Done when: un test unitario confirma el DTO para una publicación visible, el
    mismo 404 para inexistente y no visible, y que no se invoca ningún método de
    escritura.

- [x] **T11. Endpoint `GET /catalog/publication/{id}`**
  - Cubre: RF-9, RF-20, NFR-1
  - Agregar en `PublicationController` un `@GetMapping("/{id}")` que reciba
    `@PathVariable UUID id` y delegue en `publicationService.detail(id)`.
  - Done when: `GET /catalog/publication/{id}` responde 200 sin credenciales y 404
    cuando el servicio lanza `ProductNotFoundException`, sin tocar las rutas
    existentes.

## Pruebas

- [x] **T12. Tests de límite de descripción en `ProductForm`**
  - Cubre: RF-5, RF-6
  - Ampliar `ProductFormValidationTest` con 1000 caracteres (aceptado), 1001
    (rechazado), `null`, `""` y solo espacios (aceptados por tamaño).
  - Done when: cada caso tiene una aserción sobre las violaciones y la suite pasa
    sin base ni red.

- [x] **T13. Tests de normalización en `Product`**
  - Cubre: RF-1, RF-3, RF-4, RF-7, RF-8
  - Ampliar `ProductTest`: `openNew` y `update` normalizan vacío/solo espacios a
    `null`, conservan acentos y contenido, y publicar/despublicar no alteran la
    descripción.
  - Done when: los tests pasan y cubren los cinco casos de normalización y
    conservación.

- [x] **T14. Tests de mapper (panel y detalle)**
  - Cubre: RF-10, RF-11, RF-12, RF-13, RF-14, RF-15, RF-16, NFR-6
  - Ampliar `ProductResponseMapperTest` con `description` en `toResponse` y con
    `toPublicationDetail`: campos exactos, `sellerAccountId`, URL pública, lista
    vacía y ausencia de `stage`/datos internos.
  - Done when: los tests del mapper pasan y verifican ambos métodos.

- [x] **T15. Tests unitarios del servicio de detalle**
  - Cubre: RF-9, RF-10, RF-11, RF-13, RF-14, RF-17, RF-18, RF-19, RF-20, RF-21
  - Ampliar `PublicationServiceTest` con `detail` visible, inexistente, no visible
    (borrador, borrado, stock 0, sin dueño), verificación de argumentos
    (`PUBLISHED`, stock mínimo 0) y `verifyNoMoreInteractions` sobre escrituras.
  - Done when: los tests pasan con repositorios mock y cubren 200 lógico y 404.

- [x] **T16. Tests web del detalle público**
  - Cubre: RF-9, RF-10, RF-11, RF-13, RF-14, RF-15, RF-16, RF-18, RF-19
  - Ampliar `PublicationControllerWebTest` con `GET /catalog/publication/{id}`:
    200 con `id/name/price/currency/stock/description/sellerAccountId/images`,
    `description: null` serializada como nula, `images` vacío, ausencia de `stage`
    y de email/credenciales, y 404 para inexistente/no visible.
  - Done when: cada caso tiene estado y cuerpo esperados en `MockMvc`.

- [x] **T17. Tests web de descripción en alta y edición del panel**
  - Cubre: RF-2, RF-5, RF-6
  - Ampliar `ProductControllerWebTest`: el multipart de alta y de edición envía
    `description`, el `ProductForm` capturado la contiene, un valor de 1001
    caracteres responde 400 (`datos_invalidos`) y la respuesta incluye `description`.
  - Done when: los tests pasan y verifican propagación y rechazo sin persistencia.

- [x] **T18. Tests de integración del repositorio y la migración**
  - Cubre: RF-1, RF-17, RF-18, RF-19, RF-20
  - Ampliar `ProductRepositoryIntegrationTest`: `V2` agrega `description`; se
    persiste y recupera; `findPublicById` devuelve solo publicados, no borrados, con
    dueño y stock nulo o positivo, y vacía para inexistente, borrador, despublicado,
    borrado, sin dueño, stock 0 y negativo.
  - Done when: la suite de integración aplica `V1` + `V2` en limpio y valida todas
    las condiciones con PostgreSQL real.

- [x] **T19. Regresión de grilla, panel y Actuator**
  - Cubre: RF-21, RF-22, RF-23, RF-24, NFR-5, NFR-7
  - Ejecutar y ajustar (sin cambiar comportamiento) `PublicationControllerWebTest`,
    `ProductControllerWebTest`, `ProductResponseMapperTest` y
    `ActuatorConfigurationTest`; confirmar que la grilla no incorpora `description`
    y que Actuator/health/metrics siguen iguales.
  - Done when: las suites de grilla, panel y Actuator pasan en verde.

- [x] **T20. `./mvnw verify`, Checkstyle y demo manual**
  - Cubre: RF-1 … RF-24, NFR-1 … NFR-7 (verificación global)
  - Ejecutar `./mvnw verify` (incluye Checkstyle y cobertura mínima 90 %) y hacer la
    demo: crear con descripción, publicar y consultar el detalle; producto sin
    descripción y con vacío/solo espacios; 1000 vs 1001 caracteres; visible vs
    borrador/despublicado/borrado/stock 0/inexistente; grilla y Actuator sin cambios.
  - Done when: `./mvnw verify` en verde y la demo cumple los criterios de
    finalización de la spec.

## Cobertura RF

| RF | Tareas |
|---|---|
| RF-1 | T1, T2, T3, T6, T13, T18, T20 |
| RF-2 | T3, T5, T6, T17, T20 |
| RF-3 | T2, T3, T13, T20 |
| RF-4 | T2, T13, T20 |
| RF-5 | T1, T4, T5, T12, T17, T20 |
| RF-6 | T4, T12, T17, T20 |
| RF-7 | T2, T13, T20 |
| RF-8 | T2, T13, T20 |
| RF-9 | T10, T11, T15, T16, T20 |
| RF-10 | T7, T9, T10, T14, T15, T16, T20 |
| RF-11 | T7, T9, T10, T14, T15, T16, T20 |
| RF-12 | T9, T14, T20 |
| RF-13 | T7, T9, T10, T14, T15, T16, T20 |
| RF-14 | T7, T9, T10, T14, T15, T16, T20 |
| RF-15 | T7, T9, T14, T16, T20 |
| RF-16 | T7, T9, T14, T16, T20 |
| RF-17 | T8, T10, T15, T18, T20 |
| RF-18 | T8, T10, T15, T16, T18, T20 |
| RF-19 | T8, T10, T15, T16, T18, T20 |
| RF-20 | T8, T10, T11, T15, T18, T20 |
| RF-21 | T10, T15, T19, T20 |
| RF-22 | T6, T19, T20 |
| RF-23 | T6, T19, T20 |
| RF-24 | T19, T20 |

## Cobertura NFR

| NFR | Tareas |
|---|---|
| NFR-1 | T11, T16, T20 |
| NFR-2 | T8, T10, T18 |
| NFR-3 | T9, T10 |
| NFR-4 | T1, T4, T7, T12, T16 |
| NFR-5 | T10, T19, T20 |
| NFR-6 | T9, T14 |
| NFR-7 | T19, T20 |
