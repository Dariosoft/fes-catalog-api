# AGENTS.md - catalog-api

## Proyecto
API de catálogo de Friendly E-Shop. Es un servicio independiente en Java 25 y Spring Boot que posee productos, precios y stock inicial; expone su contrato HTTP bajo `/catalog`.
Persiste únicamente en la base `catalog`, usa Flyway para el esquema, RabbitMQ para futuros eventos y Actuator/OpenTelemetry para observabilidad.

## Comandos
- Ejecutar: `./mvnw spring-boot:run`
- Tests: `./mvnw test`
- Compilar y verificar: `./mvnw verify`
- Lint: `./mvnw checkstyle:check`; también se ejecuta automáticamente en la fase `validate`.

## Estilo y convenciones
- Usa Java 25, Spring Boot 4.1 y el paquete `com.friendlyeshop.catalog`.
- Nombres, código y documentación técnica en inglés; mensajes visibles al usuario en español.
- Respeta `checkstyle.xml`: 4 espacios, sin tabs, líneas de hasta 120 caracteres e imports explícitos.
- Mantén controladores HTTP delgados y la lógica de catálogo fuera de otros servicios.
- Crea nuevas migraciones Flyway; no edites migraciones ya aplicadas. Hibernate solo valida el esquema.

## Reglas
- Lee la skill `/java-springboot` y la spec activa, si existe, antes de tocar código.
- Usa `/clean-architecture` al diseñar o modificar capas, límites, dependencias, casos de uso o adaptadores.
- Este servicio es la fuente de verdad de productos, precios actuales y stock inicial.
- Nunca leas ni escribas tablas de `orders`, `payments` o `panel`; integra mediante contratos HTTP o eventos.
- Conserva `/catalog`, las variables de entorno, los health checks y las métricas usadas por Kubernetes.
- RabbitMQ es la mensajería prevista; no añadas Kafka ni workflows de negocio diferidos sin una spec.
- Mantén versiones fijadas y consulta antes de añadir dependencias o cambiar contratos compartidos.
- No omitas ni desactives reglas de Checkstyle para evitar corregir una violación.
- Los manifiestos y secretos pertenecen a `infra`; coordina allí cualquier cambio de puerto, ruta o configuración.

## Al terminar cualquier tarea
- Tras cambios no triviales de código de producción, aplica `/clean-code-guard` antes de finalizar.
- Ejecuta `./mvnw verify`; incluye Checkstyle y los tests.
- Añade o actualiza tests y migraciones cuando cambie comportamiento o esquema.
- Comprueba que no se hayan roto `/catalog` ni los endpoints de Actuator.
