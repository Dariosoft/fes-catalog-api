# Tasks 001 — Productos con etapa y dueño, e imágenes en MinIO

Tareas de implementación para `spec.md` y `plan.md` de este directorio. Cada
tarea dura ~20–30 minutos, van en orden de dependencia y usan la arquitectura
Layered de `AGENTS.md` (`/spring-boot-project-creator` opción Layered,
`/spring-boot-layered-template`, `/clean-code-guard`). Estado inicial: pendiente.

## Migración, modelo y repositorios

- [ ] **T1. Reescribir `V1__create_products.sql`**
  - Cubre: RF-1, RF-3, RF-9, RF-10, RF-20
  - Reemplazar la migración por `products` con `owner_account_id` nullable,
    `stage`, `currency` (`ARS`/`USD`), `stock` nullable, `deleted_at`, `version` y
    `product_images`; eliminar `tenant_id`. Índices por dueño (parcial) y por
    producto.
  - Done when: la app arranca con `ddl-auto: validate`, Flyway aplica V1 en una base
    limpia y no existe la columna `tenant_id`.

- [ ] **T2. Enums `ProductStage` y `Currency`**
  - Cubre: RF-1, RF-20
  - `model/ProductStage` (`DRAFT`, `PUBLISHED`) y `model/Currency` (`ARS`, `USD`)
    con persistencia en minúsculas para la etapa.
  - Done when: se puede crear el enum desde `"draft"`/`"ARS"` y un valor inválido
    no construye instancia.

- [ ] **T3. Entidad JPA `Product`**
  - Cubre: RF-2, RF-3, RF-8, RF-9, RF-11, RF-12, RF-13, RF-14
  - Campos del plan §5.1 y métodos `openNew`, `update`, `publish`, `unpublish`,
    `takeOwnership`, `deleteLogically`, `isDeleted`, `belongsTo`; `@Version`.
  - Done when: tests unitarios cubren que nace `draft`, publicar exige
    dueño/nombre/precio, despublicar conserva dueño y la baja marca `deletedAt`.

- [ ] **T4. Entidad JPA `ProductImage`**
  - Cubre: RF-17
  - Campos `id`, `productId`, `objectKey`, `contentType`, `createdAt`.
  - Done when: un test de persistencia guarda una imagen asociada a un producto y la
    recupera por `productId`.

- [ ] **T5. Repositorios `ProductRepository` y `ProductImageRepository`**
  - Cubre: RF-6, RF-7, RF-8, RF-9, RF-15, RF-23
  - Consultas por `ownerAccountId` + `deletedAtIsNull`, por id + dueño, y por dueño
    + etapa; `findByProductId`. Ninguna consulta sin dueño.
  - Done when: los tests verifican que un producto borrado o de otra cuenta no
    aparece en las consultas de la cuenta.

## Configuración de almacenamiento e imágenes

- [ ] **T6. `StorageProperties` y `CatalogProperties`**
  - Cubre: NFR-5, RF-19
  - `@ConfigurationProperties` `fes.storage` (S3_ENDPOINT, S3_BUCKET,
    S3_ACCESS_KEY, S3_SECRET_KEY) y `fes.catalog` (PUBLIC_API_BASE_URL). Sin
    secretos en código ni en logs.
  - Done when: con env de test las propiedades se enlazan y el arranque falla de
    forma explícita si falta una propiedad obligatoria.

- [ ] **T7. `ImageStorage`, `MinioImageStorage`, records y `StorageConfig`**
  - Cubre: RF-17, RF-18
  - Interfaz `store`/`load`; implementación MinIO que sube a
    `products/{productId}/{imageId}` y descarga por `imageId`; fallos →
    `StorageUnavailableException`; bean `MinioClient`. Sin método `delete`.
  - Done when: tests con `MinioClient` mock verifican subida, descarga, tipo de
    contenido y traducción de fallo a `StorageUnavailableException`.

- [ ] **T8. Límites de multipart en `application.yaml`**
  - Cubre: NFR-5
  - Configurar `spring.servlet.multipart.max-file-size` y `max-request-size` para
    hasta 10 imágenes de 2 MB.
  - Done when: una petición multipart dentro del límite se procesa y una que lo
    excede falla de forma explícita.

## DTOs, mapper y errores

- [ ] **T9. DTOs de entrada y salida**
  - Cubre: RF-15, RF-16, RF-19, RF-20, RF-21
  - `model/dto/`: `ProductForm` (con validaciones y `List<MultipartFile> images`),
    `ProductResponse`, `ProductImageResponse`, `PublishCatalogRequest`,
    `PublishCatalogResponse`, `ErrorResponse`.
  - Done when: los records compilan y las validaciones marcan nombre, precio y
    moneda inválidos.

- [ ] **T10. Excepciones y `GlobalExceptionHandler`**
  - Cubre: RF-7, RF-12, RF-21, RF-23
  - `ProductNotFoundException`, `InvalidProductException`,
    `StorageUnavailableException`; handler `@RestControllerAdvice` con los estados
    y shapes del plan §11, mensajes en español.
  - Done when: tests WebMvc confirman 400 por falta de dueño/moneda, 404 por
    producto ajeno/inexistente, 409 por publicación inválida y 503 por almacenamiento.

- [ ] **T11. `ProductResponseMapper` con URL pública**
  - Cubre: RF-19
  - Convierte `Product` + `ProductImage` a `ProductResponse`; cada imagen expone
    `url = {PUBLIC_API_BASE_URL}/catalog/images/{imageId}`.
  - Done when: test unitario verifica el shape y la URL pública construida.

## Servicios

- [ ] **T12. `ProductService.create`**
  - Cubre: RF-2, RF-4, RF-5, RF-17, RF-20, RF-21, RF-22
  - Valida campos, crea `Product` en `draft` con el dueño (o sin dueño), guarda
    imágenes en MinIO y devuelve la respuesta. No aplica moneda por defecto.
  - Done when: tests con repositorio mock e `ImageStorage` fake cubren alta con y
    sin imágenes, sin dueño, y rechazo por moneda ausente/inválida.

- [ ] **T13. `ProductService.update`**
  - Cubre: RF-8, RF-17, RF-23
  - Aplica cambios al producto de la cuenta y procesa imágenes nuevas; un producto
    de otra cuenta o inexistente produce `ProductNotFoundException`.
  - Done when: tests verifican la actualización y el 404 lógico por cuenta ajena.

- [ ] **T14. `ProductService.list`**
  - Cubre: RF-6, RF-7
  - Devuelve los productos de la cuenta en cualquier etapa, excluyendo borrados;
    sin `ownerAccountId` la capa HTTP rechaza la consulta.
  - Done when: test verifica que solo se devuelven los productos de la cuenta y que
    los borrados no aparecen.

- [ ] **T15. `ProductService.deleteLogically`**
  - Cubre: RF-9, RF-10, RF-23
  - Marca `deletedAt` del producto de la cuenta; no borra objetos de MinIO.
  - Done when: test verifica que el producto deja de listarse y que no se invoca
    ninguna operación de borrado en `ImageStorage`.

- [ ] **T16. `ProductPublicationService.publish` y `unpublish`**
  - Cubre: RF-11, RF-12, RF-13, RF-14, RF-23
  - `publish` exige dueño/nombre/precio; `unpublish` pasa a `draft` conservando
    dueño. Repeticiones no cambian la etapa ni fallan. Errores de cuenta ajena →
    404.
  - Done when: tests cubren publicar un draft válido, rechazo sin dueño/nombre/
    precio, despublicar conservando `ownerAccountId` e idempotencia.

- [ ] **T17. `ProductPublicationService.publishCatalog`**
  - Cubre: RF-15, RF-16
  - En una transacción, crea y publica los productos sin dueño del cuerpo con el
    dueño asignado, publica los `draft` de la cuenta y devuelve el conteo; sin
    productos responde 0 sin error.
  - Done when: tests cubren ambos grupos (sin dueño + drafts) y el conteo 0.

## API HTTP

- [ ] **T18. `ProductController` CRUD multipart**
  - Cubre: RF-2, RF-4, RF-5, RF-6, RF-7, RF-8, RF-9, RF-17
  - `GET/POST/PUT/DELETE /catalog/products` con `ownerAccountId` como query param
    autoritativo y `multipart/form-data` en alta/edición; controlador delgado.
  - Done when: tests MockMvc cubren listado, alta con imágenes, edición, baja lógica
    y el rechazo por falta de `ownerAccountId`.

- [ ] **T19. `ProductController` publicar, despublicar y publicar catálogo**
  - Cubre: RF-11, RF-14, RF-15, RF-16
  - `POST /catalog/products/{id}/publish`, `.../unpublish` y `POST
    /catalog/publish` (JSON con productos sin dueño); respuestas con `published`.
  - Done when: tests MockMvc verifican las tres rutas, el conteo 0 y los errores de
    publicación.

- [ ] **T20. `ProductImageController` de lectura pública**
  - Cubre: RF-18, RF-19
  - `GET /catalog/images/{imageId}` devuelve el stream con su `Content-Type`, sin
    exigir credenciales de MinIO.
  - Done when: test MockMvc confirma bytes, `Content-Type` y que no se requiere
    sesión ni credenciales.

- [ ] **T21. Conservar `CatalogController` y las rutas**
  - Cubre: RF-24
  - Mantener `GET /catalog` y verificar que Actuator (`health`, `info`,
    `prometheus`) sigue expuesto.
  - Done when: `GET /catalog` y los endpoints de Actuator responden tras el cambio.

## Pruebas y cierre

- [ ] **T22. Tests unitarios de entidad, servicios, storage y mapper**
  - Cubre: RF-2, RF-8 … RF-14, RF-17, RF-19, RF-20, RF-21, RF-22, RF-23
  - Cubrir `Product`, `ProductService`, `ProductPublicationService`,
    `MinioImageStorage` (con cliente mock) y `ProductResponseMapper`.
  - Done when: la suite unitaria pasa y no depende de red ni de base real.

- [ ] **T23. Tests web MockMvc de todos los endpoints**
  - Cubre: RF-6, RF-7, RF-9, RF-11, RF-12, RF-13, RF-14, RF-15, RF-16, RF-18,
    RF-19, RF-23, RF-24
  - Cubrir operaciones, validaciones, errores del plan §11 y conservación de
    `GET /catalog`.
  - Done when: cada endpoint tiene al menos un test con estado y cuerpo esperados.

- [ ] **T24. Tests de integración JPA con Testcontainers**
  - Cubre: RF-1, RF-3, RF-6, RF-9, RF-15, RF-20
  - Verificar la migración, el esquema y las consultas por dueño/etapa/deleted con
    PostgreSQL real.
  - Done when: la suite de integración aplica V1 en limpio y valida las consultas.

- [ ] **T25. Cerrar con `./mvnw verify` y demo manual**
  - Cubre: RF-1 … RF-24 (verificación global)
  - Ejecutar `./mvnw verify` (Checkstyle + tests) y la demo manual: crear con
    imagen, listar acotado a la cuenta, publicar, despublicar y baja lógica.
  - Done when: `./mvnw verify` en verde y la demo cumple los criterios de
    finalización de la spec.

## Cobertura RF

| RF | Tareas |
|---|---|
| RF-1 | T1, T2, T24, T25 |
| RF-2 | T3, T12, T18, T22, T25 |
| RF-3 | T1, T3, T24, T25 |
| RF-4 | T12, T18, T23, T25 |
| RF-5 | T12, T18, T25 |
| RF-6 | T5, T14, T18, T23, T24, T25 |
| RF-7 | T5, T10, T14, T18, T23, T25 |
| RF-8 | T3, T5, T13, T18, T22, T25 |
| RF-9 | T1, T3, T5, T15, T18, T23, T24, T25 |
| RF-10 | T1, T15, T25 |
| RF-11 | T3, T16, T19, T22, T23, T25 |
| RF-12 | T3, T10, T16, T19, T22, T23, T25 |
| RF-13 | T3, T16, T22, T25 |
| RF-14 | T3, T16, T19, T22, T23, T25 |
| RF-15 | T5, T9, T17, T19, T23, T24, T25 |
| RF-16 | T9, T17, T19, T23, T25 |
| RF-17 | T4, T7, T12, T13, T18, T22, T25 |
| RF-18 | T7, T20, T23, T25 |
| RF-19 | T6, T9, T11, T20, T22, T23, T25 |
| RF-20 | T1, T2, T9, T12, T22, T24, T25 |
| RF-21 | T9, T10, T12, T22, T25 |
| RF-22 | T12, T22, T25 |
| RF-23 | T5, T10, T13, T15, T16, T23, T25 |
| RF-24 | T21, T23, T25 |
