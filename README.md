# EcoEats API

Base en Kotlin + Spring Boot, sin lógica de negocio todavía. Requiere Java 25 y Docker activo.

## Ejecutar en local

Desde la raíz del repositorio:

```sh
cp .env.example .env
# Editar .env con los valores del entorno.
set -a
source .env
set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

Spring no carga `.env` automáticamente: las variables deben exportarse antes de iniciar.
El perfil local inicia PostgreSQL 17 mediante Docker Compose.

- Salud: http://localhost:8080/health
- Swagger UI: http://localhost:8080/docs
- OpenAPI: http://localhost:8080/v3/api-docs

## Verificar

```sh
./gradlew build
```

El test de contexto usa PostgreSQL 17 con Testcontainers y requiere Docker. Las pruebas web
verifican los envelopes de éxito y error, validación, estados HTTP y timestamps UTC.

## Configuración

Las versiones se centralizan en `gradle/libs.versions.toml`. El perfil `prod` desactiva
Docker Compose; configura las variables PostgreSQL, JWT y Resend en el entorno de despliegue.
El password de seed es opcional y todavía no se utiliza.

`spring.flyway.fail-on-missing-locations=false` es temporal: retirar al agregar la primera
migración. JPA utiliza `ddl-auto=validate`. La seguridad permite todas las rutas temporalmente;
la fase 3 añadirá autenticación y autorización.

Los archivos `.env`, `kotlin_global_rules.md`, metadatos del IDE y salidas de compilación
quedan excluidos de Git. `.env.example` contiene únicamente valores ficticios.
