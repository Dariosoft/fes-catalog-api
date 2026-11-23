# Tasks 002 — Lectura pública paginada de productos publicados

Tareas de implementación para `spec.md` y `plan.md` de este directorio, en orden de
dependencia y pensadas para ~20–30 minutos cada una. Usan la arquitectura Layered de
`AGENTS.md` (`/spring-boot-project-creator` opción Layered, sin regenerar el
proyecto, `/spring-boot-layered-template` y `/clean-code-guard`). Estado inicial:
ninguna tarea hecha (`[ ]`). No hay migraciones ni dependencias nuevas.

## Contrato y datos base

- [x] **T1. DTOs de lectura pública en `model/dto/`**
  - Cubre: RF-14, RF-15, RF-16, RF-17
  - Crear `PublicationResponse(UUID id, String name, BigDecimal price,
    Currency currency, Integer stock, List<ProductImageResponse> images)` y
    `PublicationPageResponse(List<PublicationResponse> items, int page,
    int size, long totalItems, int totalPages)`. Reutilizar `ProductImageResponse`;
    no incluir dueño, etapa ni fechas.
  - Done when: los records compilan, no exponen `ownerAccountId`/`stage` y un test de
    serialización confirma las claves `items`, `page`, `size`, `totalItems`,
    `totalPages` e `images[].id`/`images[].url`.

- [x] **T2. `ProductRepository.findPublished`**
  - Cubre: RF-2, RF-3, RF-4, RF-5, RF-23, RF-33
  - Agregar la consulta JPQL con `@Query` que filtra `stage = :stage`,
    `deletedAt is null`, `ownerAccountId is not null` y
    `stock is null or stock > :minimumStock`,
    devolviendo `Page<Product>`. Documentar que el orden llega por `Pageable`.
  - Done when: la consulta compila, arranca con `ddl-auto: validate` y un test de
    integración devuelve solo publicados, no borrados, con dueño y con stock positivo o
    no informado.

- [x] **T3. `ProductRepository.findPublishedByName`**
  - Cubre: RF-6, RF-7
  - Agregar la variante con `lower(p.name) like lower(concat('%', :name, '%'))`
    manteniendo el resto de las condiciones de T2.
  - Done when: un test de integración muestra que mayúsculas distintas coinciden y
    que un nombre con acentos distintos no coincide.

- [x] **T4. Consulta batch de imágenes `findByProductIdIn`**
  - Cubre: RF-16, RF-19
  - Agregar `List<ProductImage> findByProductIdIn(Collection<UUID> productIds)` a
    `ProductImageRepository` para cargar las imágenes de una página en una sola
    consulta.
  - Done when: un test recupera las imágenes de varios productos con una sola
    llamada y devuelve lista vacía cuando no hay coincidencias.

## Mapeo público

- [x] **T5. `ProductResponseMapper.toPublication`**
  - Cubre: RF-15, RF-16, RF-17, RF-18, RF-19, NFR-6
  - Agregar el método que arma `PublicationResponse` y reutiliza
    `toImageResponse`/`UrlUtils.join` para `images[].url`; sin imágenes devolver
    `List.of()`.
  - Done when: el test del mapper verifica el shape público, la URL pública con la
    base normalizada y la lista vacía cuando no hay imágenes.

- [x] **T6. `ProductResponseMapper.toPublicationPage`**
  - Cubre: RF-14, RF-20, RF-21, RF-22, RF-23
  - Agregar el método que compone `PublicationPageResponse` a partir de la lista
    de productos, el `Map<UUID, List<ProductImage>>` y los totales, conservando el
    orden recibido.
  - Done when: el test del mapper verifica totales, orden preservado y página vacía
    con `totalItems` 0 y `totalPages` 0.

## Servicio

- [x] **T7. Normalización de `page` y `size`**
  - Cubre: RF-9, RF-10, RF-11, RF-12, RF-13
  - En `PublicationService`, parsear ambos parámetros desde `String` con
    `try/catch` de `NumberFormatException`; valores nulos, vacíos, no numéricos o
    menores a 1 caen en `DEFAULT_PAGE = 1` / `DEFAULT_SIZE = 24`; `size` mayor a
    `MAX_SIZE = 100` se recorta a 100.
  - Done when: los tests unitarios cubren `null`, `""`, `"abc"`, `"0"`, `"-3"`,
    `"101"` y valores válidos sin lanzar excepción.

- [x] **T8. Consulta base, orden y totales**
  - Cubre: RF-2, RF-3, RF-4, RF-5, RF-14, RF-20, RF-21, RF-23, RF-31, RF-33
  - Implementar `list(name, page, size)` con `@Transactional(readOnly = true)`:
    `PageRequest.of(page - 1, size, Sort.by(Order.desc("createdAt"),
    Order.asc("id")))`, llamada a `findPublished` con `ProductStage.PUBLISHED` y
    stock mínimo 0, carga batch de imágenes de la página y delegación en el mapper.
  - Done when: el test unitario verifica el `PageRequest` recibido (página, tamaño y
    orden), los totales devueltos y que no se invoca ningún método de escritura.

- [x] **T9. Ajuste de página fuera de rango y resultado vacío**
  - Cubre: RF-22, RF-34
  - Si `totalPages > 0` y `page > totalPages`, repetir la consulta con
    `PageRequest.of(totalPages - 1, size, SORT)` y responder con
    `page = totalPages`; si `totalItems == 0`, responder `items` vacío, totales 0,
    página normalizada y tamaño aplicado.
  - Done when: los tests cubren página 5 con 2 páginas reales (devuelve la 2 y los
    totales reales) y catálogo vacío (0/0 sin excepción).

- [x] **T10. Filtro por nombre**
  - Cubre: RF-6, RF-7, RF-8
  - Recortar `name`, convertirlo en `null` cuando queda vacío y elegir
    `findPublishedByName` o `findPublished` según corresponda; sin normalizar
    acentos.
  - Done when: los tests verifican que un `name` en blanco usa la consulta sin filtro
    y que un `name` válido llega a la consulta filtrada con el valor recortado.

## API HTTP pública

- [x] **T11. `PublicationController`**
  - Cubre: RF-1, RF-14, NFR-1
  - Crear `controller/PublicationController` con `@RequestMapping("/publication")`
    (el prefijo `/catalog` lo aporta `ApiPathConfig`) y un `@GetMapping` raíz que recibe
    `name`, `page` y `size` como `String` opcionales y delega en el servicio.
  - Done when: `GET /catalog/publication` responde 200 sin credenciales y
    parámetros no numéricos no producen 400.

## CORS y configuración

- [x] **T12. `CorsProperties` y `application.yaml`**
  - Cubre: RF-29, NFR-2
  - Crear el record `@ConfigurationProperties(prefix = "fes.cors")` con
    `List<String> browserOrigins` normalizado (recorte, descarte de blancos, vacío
    por defecto) registrado por `@ConfigurationPropertiesScan`; agregar
    `fes.cors.browser-origins: ${BROWSER_ORIGINS:}` al `application.yaml`.
  - Done when: con `BROWSER_ORIGINS` ausente, vacía, con espacios y con varios
    orígenes el binding resuelve y la app arranca.

- [x] **T13. `CorsConfig` sobre `/catalog/**`**
  - Cubre: RF-24, RF-25, RF-26, RF-27, RF-28
  - Implementar `WebMvcConfigurer#addCorsMappings` con mapping `/catalog/**`,
    `allowedOrigins` desde `CorsProperties`, `allowedMethods("GET")`,
    `allowedHeaders("*")` y `allowCredentials(false)`.
  - Done when: un `GET` y un `OPTIONS` preflight desde un origen permitido reciben
    las cabeceras CORS, un origen ajeno no las recibe y `/actuator/health` queda sin
    CORS.

- [x] **T14. Documentar `BROWSER_ORIGINS` en `.env.sample`**
  - Cubre: NFR-2
  - Agregar la variable con un origen local de ejemplo y una nota de que vacío
    significa ningún origen permitido.
  - Done when: `.env.sample` lista `BROWSER_ORIGINS` junto a las variables existentes
    sin cambiar las demás.

## Pruebas y cierre

- [x] **T15. Tests unitarios de paginación y totales**
  - Cubre: RF-9, RF-10, RF-11, RF-12, RF-13, RF-14, RF-20, RF-21, RF-22, RF-34
  - Cubrir `PublicationService` con repositorios mock: defaults, recorte de
    `size`, página fuera de rango, resultado vacío, totales y orden del `PageRequest`.
  - Done when: la suite unitaria pasa y no depende de base ni de red.

- [x] **T16. Tests unitarios de filtro, mapper e imágenes**
  - Cubre: RF-6, RF-7, RF-8, RF-15, RF-16, RF-17, RF-18, RF-19, NFR-6
  - Cubrir la elección de consulta con y sin `name`, el shape público del mapper y la
    URL de imagen con la base normalizada.
  - Done when: los tests de `PublicationService` y `ProductResponseMapper`
    pasan en verde.

- [x] **T17. Tests web del endpoint público**
  - Cubre: RF-1, RF-6, RF-9, RF-10, RF-11, RF-12, RF-13, RF-14, RF-15, RF-16,
    RF-17, RF-22, RF-34
  - `@WebMvcTest` con `MockMvc`: JSON con `items/page/size/totalItems/totalPages`,
    ausencia de `ownerAccountId`/`stage`, paso de parámetros, `page=abc` y `size=0`
    sin 400, arreglo `images` vacío.
  - Done when: cada caso del endpoint tiene un test con estado y cuerpo esperados.

- [x] **T18. Tests de CORS y propiedades**
  - Cubre: RF-24, RF-25, RF-26, RF-27, RF-28, RF-29, NFR-2
  - Verificar `Access-Control-Allow-Origin` con origen permitido, preflight
    `OPTIONS` con `GET`, ausencia de cabeceras con origen ajeno o lista vacía,
    ausencia de `Allow-Credentials` y ausencia de CORS en `/actuator/health`.
  - Done when: los tests de `CorsConfig` y `CorsProperties` pasan y cubren la lista
    vacía sin fallar el arranque.

- [x] **T19. Tests de integración JPA**
  - Cubre: RF-2, RF-3, RF-4, RF-5, RF-6, RF-7, RF-23, RF-33
  - Ampliar `ProductRepositoryIntegrationTest` con `@DataJpaTest` y Testcontainers:
    publicados/no borrados/con dueño/stock positivo o no informado, exclusión de
    cero/negativo,
    borrador, despublicado y sin dueño, filtro case-insensitive, acentos
    significativos y orden `createdAt` desc + `id` asc estable entre páginas.
  - Done when: la suite de integración aplica V1 en limpio y valida todas las
    consultas con PostgreSQL real.

- [ ] **T20. Regresión, `./mvnw verify` y demo manual**
  - Cubre: RF-1 … RF-34, RF-30, RF-31, RF-32 (verificación global), NFR-5, NFR-7
  - Ejecutar la suite completa, confirmar que `ProductControllerWebTest` y
    `ActuatorConfigurationTest` siguen verdes y hacer la demo: consulta sin filtro,
    con `name`, dos páginas de 24, `page` fuera de rango, `size` 150, origen
    permitido y origen ajeno.
  - Done when: `./mvnw verify` en verde con Checkstyle y la demo cumple los criterios
    de finalización de la spec.

## Cobertura RF

| RF | Tareas |
|---|---|
| RF-1 | T11, T17, T20 |
| RF-2 | T2, T8, T19, T20 |
| RF-3 | T2, T8, T19, T20 |
| RF-4 | T2, T8, T19, T20 |
| RF-5 | T2, T8, T19, T20 |
| RF-6 | T3, T10, T16, T17, T19, T20 |
| RF-7 | T3, T10, T16, T19, T20 |
| RF-8 | T10, T16, T20 |
| RF-9 | T7, T15, T17, T20 |
| RF-10 | T7, T15, T17, T20 |
| RF-11 | T7, T15, T17, T20 |
| RF-12 | T7, T15, T17, T20 |
| RF-13 | T7, T15, T17, T20 |
| RF-14 | T1, T6, T8, T11, T15, T17, T20 |
| RF-15 | T1, T5, T16, T17, T20 |
| RF-16 | T1, T4, T5, T16, T17, T20 |
| RF-17 | T1, T5, T16, T17, T20 |
| RF-18 | T5, T16, T20 |
| RF-19 | T4, T5, T16, T17, T20 |
| RF-20 | T6, T8, T15, T20 |
| RF-21 | T6, T8, T15, T20 |
| RF-22 | T6, T9, T15, T17, T20 |
| RF-23 | T2, T6, T8, T19, T20 |
| RF-24 | T13, T18, T20 |
| RF-25 | T13, T18, T20 |
| RF-26 | T13, T18, T20 |
| RF-27 | T13, T18, T20 |
| RF-28 | T13, T18, T20 |
| RF-29 | T12, T18, T20 |
| RF-30 | T20 |
| RF-31 | T8, T20 |
| RF-32 | T13, T20 |
| RF-33 | T2, T8, T19, T20 |
| RF-34 | T9, T15, T17, T20 |

## Cobertura NFR

| NFR | Tareas |
|---|---|
| NFR-1 | T11, T20 |
| NFR-2 | T12, T14, T18 |
| NFR-3 | T2, T8, T19 |
| NFR-4 | T1, T11, T17 |
| NFR-5 | T13, T20 |
| NFR-6 | T5, T16 |
| NFR-7 | T8, T20 |
