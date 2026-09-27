---
name: spring-boot-layered-template
description: Use when adding, moving, or reviewing Java classes in layered Spring Boot projects when the basic controller, service, repository, model, dto, config, and exception folders are not enough to place a responsibility clearly.
---

# Spring Boot Layered Template

This skill complements `spring-boot-project-creator` in its Layered option.

The base skill defines the minimum structure for a layered Spring Boot project. This skill adds criteria for deciding where to place classes that appear during implementation and do not fit cleanly into the most basic folders.

# Core Idea

Not every class that uses Spring belongs in `config`.
Not every class used by a service belongs in `service`.
Not every static class belongs in `util`.
Not every class that participates in a business flow contains business logic.

The folder should express the class's main responsibility.

# Base Layered Structure

Use the basic folders for obvious cases:

- `controller/`: HTTP endpoints, request-to-use-case conversion, response building.
- `service/`: use cases, business orchestration, coordination between repositories, models, validations, and external clients.
- `repository/`: persistence access.
- `model/`: entities, value objects, and domain or application concepts.
- `model/dto/`: transport objects that enter or leave through APIs, events, or external contracts.
- `config/`: properties, beans, framework configuration, CORS, security, serializers, clients configured as infrastructure.
- `exception/`: custom exceptions and error types.

# Allowed Extensions

When the base structure falls short, infrastructure or protocol folders can be added, as long as they have a concrete and stable name.

Avoid generic folders such as:

- `util/`
- `utils/`
- `helper/`
- `helpers/`
- `common/`
- `shared/`

Use them only when there is no more precise responsibility.

# `client/`

Use `client/` for code that belongs to the integration boundary with external systems, external APIs, internal APIs from other services, SDKs, or external protocols.

`client` does not only mean "the class that performs HTTP". It can include mechanisms required to communicate correctly with that API or protocol.

Examples:

- `client/oauth/GoogleOAuthClient`
- `client/oauth/OAuthStateCodec`
- `client/payment/PaymentGatewayClient`
- `client/email/EmailProviderClient`
- `client/catalog/CatalogApiClient`

Use subfolders by provider, protocol, or bounded integration:

- `client/oauth/`
- `client/google/`
- `client/stripe/`
- `client/email/`
- `client/catalog/`

Do not put in `client/`:

- creation or update logic for owned entities
- access to owned repositories
- domain business rules
- HTTP response building for your controllers

# `http/`

Use `http/` for reusable HTTP transport pieces that are not controllers.

Examples:

- `http/cookie/SessionCookieWriter`
- `http/header/CorrelationIdHeader`
- `http/filter/RequestLoggingFilter`
- `http/problem/ProblemDetailsFactory`

Use subfolders by artifact or mechanism:

- `http/cookie/`
- `http/header/`
- `http/filter/`
- `http/problem/`

Do not put in `http/`:

- use cases
- business logic
- database access
- domain DTOs that do not depend on HTTP

# `auth/` or `security/`

Use `auth/` or `security/` when the class represents cross-cutting authentication, authorization, token, claim, password, or security policy mechanisms.

Examples:

- `auth/jwt/JwtTokenFactory`
- `auth/password/PasswordPolicy`
- `auth/session/SessionPrincipal`
- `security/role/RoleEvaluator`

Prefer `auth/` when the concept is more closely tied to identity, login, sessions, or tokens.
Prefer `security/` when the concept is more closely tied to authorization, filters, access rules, or Spring Security integration.

# `messaging/` or `event/`

Use `messaging/` for broker infrastructure, publishers, consumers, serializers, or messaging adapters.

Use `event/` for domain/application events when they are concepts owned by the system and not broker details.

Examples:

- `messaging/rabbit/AccountEventPublisher`
- `messaging/rabbit/AccountCreatedMessage`
- `event/AccountCreatedEvent`

Do not mix broker payloads with domain events if they have different responsibilities.

# `mapper/`

Use `mapper/` when there are repeated conversions between models, DTOs, entities, or external payloads.

Examples:

- `mapper/AccountResponseMapper`
- `mapper/GoogleProfileMapper`

Do not create `mapper/` for a trivial conversion used only once. In that case, keeping the code close to its usage is usually simpler.

# `validation/`

Use `validation/` for reusable validators that are not a complete use case.

Examples:

- `validation/ReturnUrlValidator`
- `validation/PasswordStrengthValidator`

If the validation is a central part of a use case and is not reused, it can stay in `service/`.

# Distinguishing Similar Responsibilities

A class that reads properties does not necessarily belong in `config`.

Example: a cookie writer can depend on properties, but its responsibility is to build cookies. It can go in `http/cookie/`.

A class used by a service does not necessarily belong in `service`.

Example: an OAuth client can be used by a login service, but its responsibility is external integration. It can go in `client/oauth/`.

A static class does not necessarily belong in `util`.

Example: an OAuth `state` codec can have static methods, but its responsibility is encoding a specific protocol payload. It can go in `client/oauth/`.

# Recommended Names

Use names that express role and responsibility:

- `Client`: integration with an API, SDK, external service, or external protocol.
- `Codec`: encodes and decodes a specific payload.
- `Writer`: serializes information into an external or transport artifact.
- `Reader`: extracts information from an external or transport artifact.
- `Factory`: builds objects when construction has rules or variants.
- `Mapper`: converts between two representations.
- `Validator`: validates a reusable rule.
- `Publisher`: publishes messages or events.
- `Consumer` or `Listener`: consumes messages or events.

Avoid ambiguous names:

- `Utils`
- `Helper`
- `Manager`
- `Processor`
- `Handler` when there is no clear framework or pattern behind it

# Class Boundaries

Do not hide meaningful responsibilities as inner classes inside a larger class.

Use a separate top-level class when the collaborator:

- has its own concrete responsibility
- performs external I/O or protocol-specific work
- is worth naming in tests or documentation
- can be replaced with a fake, stub, or alternate implementation
- makes the parent class know too much about low-level details

Example: keep `GoogleOAuthClient`, `TokenExchange`, and `HttpTokenExchange` as separate files instead of nesting the HTTP token exchange implementation inside the client facade.

Inner classes are acceptable only for tiny private implementation details that have no independent responsibility and are not useful to test, name, or document separately.

# UML Relationship Hygiene

Class diagrams should communicate architecture, not reproduce every import or local variable type.

Prefer architecturally meaningful relationships over exhaustive compile-time dependency graphs.

Avoid duplicating transitive relationships when a clearer owner relationship already explains the dependency.

Example: if `GoogleLoginService` depends on `GoogleOAuthClient`, and `GoogleOAuthClient` returns or maps `GoogleProfile`, show `GoogleLoginService --> GoogleOAuthClient` and `GoogleOAuthClient ..> GoogleProfile`; omit `GoogleLoginService ..> GoogleProfile` unless the service owns important rules around that profile type.

For UML diagrams:

- show fields, constructor collaborators, interfaces, adapters, repositories, clients, and framework-facing boundaries
- show DTOs or records when they are public contracts or key outputs
- omit transient local-variable types when their relationship is already explained by a repository, client, adapter, or returned contract
- omit duplicated edges from a service to entities when the repository relationship already communicates ownership/access clearly
- keep the diagram readable even if that means it is not a complete import graph

# Decision Checklist

Before creating or moving a class, answer:

- Does it expose an HTTP endpoint? `controller/`.
- Does it coordinate a use case or business rules? `service/`.
- Does it access persistence? `repository/`.
- Does it configure Spring, beans, properties, or framework behavior? `config/`.
- Does it talk to an API, SDK, provider, or external protocol? `client/<provider-or-protocol>/`.
- Does it build or parse HTTP artifacts such as cookies, headers, filters, or problem details? `http/<artifact>/`.
- Does it represent authentication, authorization, tokens, password, or a principal? `auth/` or `security/`.
- Does it publish or consume messages/events from a broker? `messaging/`.
- Does it represent an application-owned event? `event/`.
- Does it repeatedly convert between representations? `mapper/`.
- Does it validate a reusable rule? `validation/`.
- Is it an input/output DTO? `model/dto/`.
- Is it a domain/application concept? `model/`.

# Minimum Change Rule

Do not add a new folder for a single class if the benefit is not clear.

Add a new folder when:

- it avoids polluting `service/`, `config/`, or `controller/`
- it expresses a stable responsibility
- it will likely group more classes of the same kind in the future
- it reduces ambiguity for future changes
