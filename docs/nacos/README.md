# Nacos config templates

This folder holds **example configurations** that you can copy-paste into a
Nacos server. The gateway is wired to pull these dataIds at startup.

## File map

| File | dataId in Nacos | Group | Purpose |
|------|-----------------|-------|---------|
| `opengradle.yaml` | `opengradle.yaml` | `DEFAULT_GROUP` | Per-service runtime config (routes, whitelist, auth TTL) |
| `global-variables.yaml` | `global-variables.yaml` | `DEFAULT_GROUP` | Cross-service shared config (optional) |

## Why two files?

`opengradle.yaml` is loaded automatically because Spring Cloud Alibaba builds
the dataId from `spring.application.name` + `.${file-extension}` (see
`bootstrap.yml`). Anything in here applies **only** to this service.

`global-variables.yaml` is loaded only if you uncomment the `shared-configs`
section in `bootstrap.yml`. It's the place to put environment-wide values
shared across multiple services.

## Setup walkthrough (local Nacos)

1. **Start Nacos** (standalone, in Docker):

   ```bash
   docker run -d --name nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:v2.1.0
   ```

   Wait ~30s, then verify: open <http://localhost:8848/nacos>
   (default credentials: `nacos` / `nacos`).

2. **Create the dataId**:

   - In the Nacos console: **Configuration Management → Configurations → "+"**
   - **dataId**: `opengradle.yaml`
   - **Group**: `DEFAULT_GROUP`
   - **Format**: `YAML`
   - **Content**: paste the contents of [`opengradle.yaml`](opengradle.yaml)
   - Click **Publish**

3. **Start the gateway**:

   ```bash
   cd <project root>
   ./gradlew bootRun
   ```

   You should see something like:

   ```
   INFO o.s.c.b.c.PropertySourceBootstrapConfiguration : Located property source: ...
   INFO c.a.n.client.config.impl.ClientWorker : add listener: opengradle.yaml ...
   ```

4. **Verify hot reload**: change `auth.token-ttl-seconds` in Nacos from `7200`
   to `300`, publish, and the next `POST /auth/login` will return
   `expiresIn: 300`. No restart required.

## Property override order

Spring Cloud Nacos sources override the local `application.yml`. Effective
precedence (highest first):

1. Command-line args / environment variables
2. `${spring.application.name}-${profile}.yaml` in Nacos
3. **`${spring.application.name}.yaml`** in Nacos ← `opengradle.yaml`
4. `${spring.application.name}` (no extension) in Nacos
5. Shared configs from `bootstrap.yml`
6. `application.yml` packaged in the jar

So everything you set in `opengradle.yaml` overrides the in-jar defaults but
not env-specific overrides — keep production secrets out of the in-jar file
and put them in env vars or a private Nacos namespace.

## Production hardening

For non-local environments, set in `bootstrap-<profile>.yml`:

```yaml
spring:
  cloud:
    nacos:
      config:
        # Crash on startup if Nacos is unreachable — better than silent drift.
        fail-fast: true
        namespace: <private-namespace-uuid>
        username: <nacos-user>
        password: <nacos-password>
```
