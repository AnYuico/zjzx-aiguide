# Deployment Network Boundary

## Public ports

Only the application entry points should be exposed publicly:

- `8500`: customer-facing API gateway
- `8501`: management application, restricted to trusted administrators

Do not expose business service ports `8511-8515` through a cloud security group,
host firewall, Docker `ports` mapping, or public load balancer.

## Service binding

The development profile binds business services to `127.0.0.1` and registers that
address with Nacos by default. This is suitable when the gateway and all services
run on the same machine.

For containers or multiple hosts, set both variables per service:

```text
SERVICE_BIND_ADDRESS=0.0.0.0
SERVICE_DISCOVERY_IP=<private service IP>
```

Keep `8511-8515` private when overriding the bind address. Containers should share
an internal network without publishing these ports on the host.

## Internal API token

User, product, cart, order, and payment services must receive the same non-empty environment
variable:

```text
ZJZX_INTERNAL_API_TOKEN=<random shared secret>
```

The token must not be stored in frontend code or sent through the public gateway.

## Public image bucket

The `zjzx-bucket` bucket may be anonymously readable for product and avatar images,
but its anonymous policy must grant only `s3:GetObject` on bucket objects. Do not
grant anonymous `PutObject`, `DeleteObject`, or `ListBucket` permissions.

Only validated JPEG, PNG, and WebP files may be written through the management
upload API. The MinIO service account should have the minimum bucket-location and
object-write permissions needed by the SDK. Bucket creation is a deployment task;
the upload request no longer creates buckets dynamically.
