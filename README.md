# Catalog API

Owns products, prices and initial stock for Friendly E-Shop.

```bash
./mvnw test
docker build -t friendly-e-shop/catalog-api:dev .
```

Runtime dependencies are PostgreSQL, RabbitMQ and an OTLP endpoint. Kubernetes supplies their configuration.
