# Catalog API

Owns products, prices and initial stock for Friendly E-Shop.

## Implementation

Every implementation in this repository uses only these two skills:

- `spring-boot-project-creator`, Layered option. Place code under `com.friendlyeshop.catalog` in `controller/`, `service/`, `repository/`, `model/`, `model/dto/`, `config/`, and `exception/`. Do not use the DDD option of that skill, and do not bootstrap a new project with Spring Initializr when changing this service.
- `clean-code-guard` after every non-trivial production code change, before the work is considered done.

```bash
./mvnw test
docker build -t friendly-e-shop/catalog-api:dev .
```

Runtime dependencies are PostgreSQL, RabbitMQ and an OTLP endpoint. Kubernetes supplies their configuration.
