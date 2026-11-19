---
icon: lucide/container
---

# Kubernetes and rollback

The platform repository owns the aggregate Compose/Kubernetes topology. This
page documents the contract the Product image expects so an operator can attach
it without changing the service.

## Runtime contract

| Setting | Value |
| --- | --- |
| Image | `ghcr.io/microsserv52/product@sha256:<digest>` or `ghcr.io/microsserv52/product:sha-<full-commit-sha>` |
| Port | `8080` |
| User | `10001:10001` |
| Readiness | `GET /actuator/health/readiness` |
| Liveness | `GET /actuator/health/liveness` |
| Startup grace | At least 30 seconds for Flyway and JVM startup. |
| Required env | `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_*`, `JWT_SECRET`, `PRODUCT_IMAGE_MODE=s3`, `PRODUCT_IMAGE_S3_BUCKET`, `PRODUCT_IMAGE_S3_REGION` |
| Optional env | `CACHE_REDIS_URL`, `CACHE_ENABLED`, `CACHE_TTL_SECONDS`, `LOG_LEVEL`, `PRODUCT_IMAGE_S3_MEDIA_ORIGIN`, `PRODUCT_IMAGE_UPLOAD_EXPIRY` |

## Example deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: product
spec:
  replicas: 3
  selector:
    matchLabels:
      app: product
  template:
    metadata:
      labels:
        app: product
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
      containers:
        - name: product
          image: ghcr.io/microsserv52/product@sha256:REPLACE_WITH_DIGEST
          ports:
            - name: http
              containerPort: 8080
          envFrom:
            - secretRef:
                name: product-secrets
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            periodSeconds: 5
            failureThreshold: 6
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            initialDelaySeconds: 30
            periodSeconds: 10
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
```

`product-secrets` must provide `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` and `JWT_SECRET`.
Set `CACHE_REDIS_URL` from the platform Redis service when caching is enabled.

## Probes and scaling

- Readiness deliberately turns `503` when PostgreSQL is unavailable, removing
  the pod from service endpoints while liveness stays `200`.
- Do not use readiness as a liveness probe; a database outage should not restart
  healthy pods.
- HPA can scale on CPU or `http.server.requests`; keep the database pool
  (`DB_POOL_MAX`) multiplied by the maximum replica count within PostgreSQL
  connection limits.
- Use a PodDisruptionBudget and graceful shutdown (`server.shutdown=graceful`).

## Migrations

Flyway runs at startup under a PostgreSQL advisory lock. For large or risky
changes, run a one-shot Job with the same image and datasource secrets, wait for
completion, then roll the Deployment. Never run destructive DDL during a rolling
deploy without an expand/contract plan.

## Rollback

1. Identify the previous immutable image digest from the release record.
2. Set the Deployment image back to that digest.
3. If the previous release expects the current schema, roll back normally.
4. If the new release applied a forward-only migration that is not
   backward-compatible, do **not** point the old image at the new schema.
   Restore the database from a verified snapshot or ship a forward fix using an
   expand/contract migration.
5. Verify readiness, `GET /products`, an authenticated create/delete in a
   non-production environment, and the Prometheus scrape.
6. Record the rollback image digest and the reason in the release notes.

## Package access

GHCR packages are private by default. Grant cluster pull access with an
image-pull secret scoped to `read:packages` for the `microsserv52` organization.
Retain at least the last 30 immutable `sha-` images and all tagged releases.
