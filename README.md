# EcoEats API

Backend de comida colombiana saludable para clientes Android, iOS, Flutter y web.
Incluye registro/login JWT, recuperación de contraseña con OTP, catálogo y CRUD ADMIN,
e imágenes en un bucket privado S3-compatible.

**Stack:** Kotlin 2.4 · Java 25 · Spring Boot 4.1 · Spring MVC con virtual threads · JPA ·
PostgreSQL 17 · Flyway · Spring Security · Jackson 3 · springdoc · AWS SDK v2 · JUnit/MockK/Testcontainers.

## Ejecutar local

Requiere Java 25 y Docker activo. Desde la raíz del repo:

```sh
cp .env.example .env
# Ajustar las variables; solo contiene valores locales de ejemplo.
set -a
source .env
set +a
docker compose up -d --build
./gradlew bootRun
```

PostgreSQL usa 5432; MinIO S3 9000 y consola 9001. minio-init crea ecoeats-local.
MinIO/mc se construyen desde versiones oficiales fijadas: la primera ejecución descarga
y compila sus fuentes. El perfil local conserva el entorno Compose ya iniciado.
Spring no lee .env automáticamente: deben exportarse sus variables.

## Configuración

| Variables | Uso |
|---|---|
| SPRING_PROFILES_ACTIVE, PORT | local/prod; puerto 8080 por defecto |
| PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD | PostgreSQL |
| JWT_SECRET, JWT_EXPIRATION | Secret mínimo 32 bytes; vigencia 24h por defecto |
| RESEND_API_KEY, RESEND_FROM_EMAIL, RESEND_FROM_NAME | Email; API key vacía en local usa LoggingEmailSender |
| ALLOWED_ORIGINS | Orígenes web permitidos, separados por comas |
| SEED_ADMIN_PASSWORD | Crea admin@ecoeats.com solo si no existe; 123456 es exclusivamente local |
| BUCKET, ENDPOINT, REGION, ACCESS_KEY_ID, SECRET_ACCESS_KEY | Credentials del bucket Railway o MinIO |
| S3_FORCE_PATH_STYLE, IMAGE_URL_TTL | false/24h por defecto; local usa path-style=true |
| PUBLIC_URL, SPRINGDOC_ENABLED | Servidor de producción en OpenAPI; docs habilitados por defecto |

El perfil prod desactiva Compose. JPA valida el esquema; Flyway aplica V1–V4 y los 12 platos.
No subas .env, credenciales ni reglas personales a Git. Un fallo de limpieza de imágenes
después del commit se registra y requiere limpieza posterior; no revierte la transacción confirmada.

## Documentación y pruebas

- [Swagger UI local](http://localhost:8080/docs): Authorize y Try it out.
- [OpenAPI servido](http://localhost:8080/v3/api-docs).
- [Guía para móvil y web](docs/API.md).
- [Contrato OpenAPI versionado](docs/openapi.yaml).
- [Colección Postman](postman/EcoEats-API.postman_collection.json) y [entorno local](postman/EcoEats-local.postman_environment.json).
- [Salud](http://localhost:8080/health).

```sh
./gradlew build             # Incluye PostgreSQL/MinIO y comprobación del contrato
./gradlew generateOpenApi   # Regenerar YAML únicamente tras revisar un cambio intencional
```

En Postman importa ambos archivos, selecciona EcoEats-local y ejecuta Auth y Platos en orden.
Los scripts generan datos, guardan token y seleccionan un plato automáticamente. Recuperación
prueba el OTP inválido por defecto; la guía explica cómo probar el código real. Imágenes
requiere seleccionar un archivo. Desactiva Swagger/OpenAPI con SPRINGDOC_ENABLED=false.

Calidad: `./gradlew check` ejecuta tests, ktlint y JaCoCo (mínimo 80% de líneas en cada
paquete application/domain). Reporte HTML: `build/reports/jacoco/test/html/index.html`.
Los límites por IP viven en memoria; desplegar una sola instancia o migrarlos a Redis.

Deploy: [Railway](docs/DEPLOY-RAILWAY.md), Dockerfile multi-stage y CI en GitHub Actions.
