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
El password de seed es opcional. Si se configura `SEED_ADMIN_PASSWORD`, el arranque crea
`admin@ecoeats.com` con rol `ADMIN` y BCrypt strength 10 solo si la cuenta no existe.
El valor `123456` de `.env.example` es exclusivamente para desarrollo local.

Flyway aplica V1–V4 para crear usuarios, tokens de recuperación, platos y los 12 platos iniciales.
JPA utiliza `ddl-auto=validate`. La autenticación utiliza JWT HS256: el secret debe contener
al menos 32 bytes UTF-8. Los tokens duran `JWT_EXPIRATION` (24 horas por defecto).
El registro y login son públicos; los GET de platos requieren token y las escrituras requieren
rol ADMIN. Se cachea la existencia de usuarios durante cinco segundos; después de ese plazo,
un token de un usuario eliminado devuelve 401 `Token inválido`.

Los archivos `.env`, `kotlin_global_rules.md`, metadatos del IDE y salidas de compilación
quedan excluidos de Git. `.env.example` contiene únicamente valores ficticios.

## Verificar persistencia local

Después de iniciar con el perfil local y las variables exportadas:

```sh
docker compose exec postgres psql -U postgres -d ecoeats
```

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT count(*) AS total_platos FROM dishes; -- 12
SELECT name, price, rating FROM dishes ORDER BY name;
SELECT id, email, role, numero_documento FROM users WHERE email = 'admin@ecoeats.com';
```

Las pruebas con Testcontainers verifican migraciones e idempotencia del seed, restricciones
de unicidad, UUID v7, auditoría, repositorios y creación opcional del admin.

## Registro y login

Con la API local iniciada:

```sh
curl -i http://localhost:8080/api/securities/register \
  -H 'Content-Type: application/json' \
  -d '{"numero_documento":"12345678","nombres":"Demo","apellidos":"EcoEats","correo":"demo@ecoeats.com","password":"demo123"}'

curl -i http://localhost:8080/api/securities/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@ecoeats.com","password":"demo123"}'
```

El registro devuelve 201; repetirlo devuelve 409. El login devuelve 200 con `id`, `email`,
`token` y `tokenFirebaseAuth`. Envía el token como `Authorization: Bearer <token>`.
BCrypt admite contraseñas de hasta 72 bytes UTF-8; el registro valida ese límite además
del mínimo de seis caracteres.

Las pruebas de autorización usan un controller de platos únicamente en `src/test`.
Los endpoints reales de platos se implementarán en la fase 5. Las pruebas de esta fase
cubren el contrato JSON, duplicados concurrentes, JWT expirados o con firma alterada,
roles USER/ADMIN, usuarios eliminados y CORS.
