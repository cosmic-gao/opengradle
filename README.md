# opengradle

A minimal Spring Cloud Gateway template with built-in token-based auth, based on the dependency stack of `mspbots-gateway`.

## Stack

- Java 11
- Spring Boot 2.5.3
- Spring Cloud 2020.0.x (Gateway / LoadBalancer)
- Spring Cloud Alibaba 2021.1 (Nacos config + discovery)
- Redis (Jedis, no Lettuce)
- Caffeine local cache
- Hutool utility kit
- Lombok
- Logback (file + console)

## Project layout

```
src/main/java/com/opengradle/
├── WebApplication.java                         # main class
├── auth/                                       # authentication subsystem
│   ├── AuthController.java                     # /auth/login, /auth/logout, /auth/me
│   ├── AuthService.java                        # login / logout / getUserContext
│   ├── UserContext.java                        # cached identity (userId, tenant, ...)
│   └── dto/
│       ├── LoginRequest.java
│       └── LoginResponse.java
├── config/
│   ├── AppConfiguration.java                   # WebClient + Jackson + converters
│   ├── AuthConfiguration.java                  # @RefreshScope auth.* settings
│   ├── GatewayContext.java                     # per-request context bag
│   ├── RedisConfiguration.java                 # RedisTemplate w/ Jackson serializer
│   └── WhiteListConfiguration.java             # @RefreshScope gateway.whitelist
├── constant/
│   ├── Constant.java                           # header names, etc.
│   └── RedisConstant.java                      # redis key prefixes
├── exception/
│   ├── ErrorCode.java                          # standard error codes
│   └── GlobalExceptionHandler.java             # ErrorWebExceptionHandler
├── filter/
│   ├── RequestCoverFilter.java                 # caches request body to GatewayContext
│   └── AuthFilter.java                         # whitelist + token check + identity injection
└── utils/
    ├── RedisUtils.java                         # thin wrapper over RedisTemplate
    └── Result.java                             # unified response envelope

src/main/resources/
├── bootstrap.yml                               # Nacos enable + global Nacos config
├── bootstrap-local.yml                         # local Nacos addr (per-profile)
├── application.yml                             # in-jar defaults (overridden by Nacos)
└── logback-spring.xml                          # logging

docs/nacos/                                     # Nacos config templates
├── README.md                                   # how to upload, hot-reload demo
├── opengradle.yaml                             # main dataId → routes / whitelist / auth
└── global-variables.yaml                       # optional shared-configs dataId
```

## Filter chain

| Order | Filter | Responsibility |
|------:|--------|----------------|
| HIGHEST_PRECEDENCE | `RequestCoverFilter` | Caches JSON / form body into `GatewayContext` |
| 10 | `AuthFilter` | Whitelist bypass &rarr; token resolve &rarr; identity header injection |

## Authentication

### Flow

```
                  ┌──────────────────────────────┐
client ── POST ──▶│  /auth/login                 │
{ user, pwd }     │  AuthService.login()         │
                  │   1. lookup in user store     │
                  │   2. sha256(password) match   │
                  │   3. mint UUID token          │
                  │   4. set TOKENS:<token>       │
                  │      = UserContext, TTL=7200s │
                  └──────┬───────────────────────┘
                         │
                         ▼
          { token, expiresIn, user }
                         │
                         ▼
client ── any req w/ header `token: <uuid>` ───────────▶ AuthFilter
                                                            │
                                                            ▼
                                            AuthService.getUserContext(token)
                                                            │
                              ┌─────────────────────────────┴─────────────────┐
                              ▼                                                ▼
                       found → inject identity headers                   not found → 401
                              │                                                ▲
                              ▼                                                │
                       chain.filter() ──▶ downstream service           response.setStatusCode(401)
```

### Identity headers injected downstream

| Header | Source | Example |
|--------|--------|---------|
| `x-user-id` | `UserContext.userId` | `1001` |
| `x-username` | `UserContext.username` | `alice` |
| `x-tenant-code` | `UserContext.tenantCode` | `1000` |
| `x-super-admin` | `UserContext.superAdmin` | `false` |
| `x-forwarded-for` | resolved client IP (respects upstream XFF) | `10.0.0.42` |

Downstream services trust these headers and **must not re-validate the token** — the gateway is the single source of truth.

### Demo users

Seeded in-memory at startup by `AuthService.seedDemoUsers()`:

| username | password | userId | tenantCode | superAdmin |
|----------|----------|-------:|-----------:|:----------:|
| admin    | admin123 | 1      | 1000       | ✅ |
| alice    | alice123 | 1001   | 1000       | – |
| bob      | bob123   | 1002   | 1000       | – |

Replace `AuthService.seedDemoUsers()` with a real `UserRepository` lookup before going beyond local demos. Replace `SecureUtil.sha256()` with BCrypt or Argon2 before storing any real passwords.

## Run locally

Prerequisites:
- JDK 11
- A reachable Redis (default `127.0.0.1:6379`)
- A reachable Nacos (default `127.0.0.1:8848`) — `fail-fast: false` is set in `bootstrap.yml`, so the gateway still starts with `application.yml` defaults if Nacos is down

### One-shot setup with Docker

```bash
# Redis
docker run -d --name redis -p 6379:6379 redis:7

# Nacos (standalone)
docker run -d --name nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:v2.1.0
```

### Push the gateway config to Nacos

The template lives in `docs/nacos/opengradle.yaml`. Either:

- **Manually**: Open <http://localhost:8848/nacos> (default `nacos / nacos`),
  Configuration Management → "+", `dataId=opengradle.yaml`, `Group=DEFAULT_GROUP`,
  format `YAML`, paste the file content, click **Publish**.
- **CLI**:
  ```bash
  curl -X POST "http://localhost:8848/nacos/v1/cs/configs" \
    --data-urlencode "dataId=opengradle.yaml" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content=$(cat docs/nacos/opengradle.yaml)"
  ```

See [`docs/nacos/README.md`](docs/nacos/README.md) for the full walkthrough and hot-reload demo.

### Start the gateway

```bash
./gradlew bootRun
```

Or build and run the jar:

```bash
./gradlew bootJar
java -jar build/libs/opengradle-1.0-SNAPSHOT.jar
```

### What Nacos drives

| Property in `opengradle.yaml` | Bound to | Hot reload? |
|-------------------------------|----------|:-----------:|
| `spring.cloud.gateway.routes` | Gateway route table | ✅ |
| `gateway.whitelist` | `WhiteListConfiguration` (`@RefreshScope`) | ✅ |
| `auth.token-ttl-seconds` | `AuthConfiguration` (`@RefreshScope`) | ✅ |

Whitelist/auth changes take effect for the **next** request after `RefreshEvent`
fires (~1–2s after publishing in Nacos). Route changes auto-trigger a
`RefreshRoutesEvent`.

## End-to-end test

```bash
# 1. Login — /auth/login is whitelisted, no token needed
curl -s -X POST http://localhost:9999/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}'
# → {"code":0,"msg":"ok","data":{"token":"<uuid>","expiresIn":7200,"user":{...}}}

TOKEN=<paste-the-token-from-above>

# 2. Hit a protected endpoint without a token — expect 401
curl -i http://localhost:9999/example/anything
# → HTTP/1.1 401 Unauthorized
#   {"code":401,"msg":"token is missing.","data":null}

# 3. Hit the same endpoint WITH the token — gateway will forward to lb://example-service
curl -i -H "token: $TOKEN" http://localhost:9999/example/anything
# Downstream service will see headers: x-user-id, x-username, x-tenant-code, x-super-admin

# 4. Verify the current user
curl -s -H "token: $TOKEN" http://localhost:9999/auth/me
# → {"code":0,"msg":"ok","data":{"userId":1001,"username":"alice","tenantCode":1000,"superAdmin":false}}

# 5. Logout — token is removed from Redis
curl -i -X POST -H "token: $TOKEN" http://localhost:9999/auth/logout
# → {"code":0,"msg":"ok","data":true}

# 6. /auth/me with the same token now returns 401
curl -i -H "token: $TOKEN" http://localhost:9999/auth/me
```

## Extending

- **Add a route**: edit `application.yml` `spring.cloud.gateway.routes`, or push it to the `opengradle.yaml` config in Nacos.
- **Add a filter**: implement `GlobalFilter` + `@Order(N)`. Lower N runs first; `HIGHEST_PRECEDENCE` runs before everything else.
- **Add whitelist URI**: add a regex line under `gateway.whitelist` (matches the path with the leading `/` stripped).
- **Change auth scheme**: keep `AuthFilter` as-is and replace the body of `AuthService` (e.g., JWT verification, OAuth introspection, LDAP). The filter only depends on `AuthService.getUserContext(token)`.
- **Change identity propagation**: edit `Constant.HEADER_*` and `AuthFilter#withIdentityHeaders`.
