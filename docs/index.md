---
icon: lucide/package
---

# Product service

Product owns the catalogue used by Order and the storefront: product identity,
display name, price and sale unit. It is a Java 25 / Spring Boot service backed
by PostgreSQL and an optional Redis-compatible read cache.

The [canonical HTTP contract](https://github.com/Microsserv52/product-interface)
is published separately so consumers can pin a version without cloning this
repository.

## Start here

- [Architecture](architecture.md)
- [API and errors](api.md)
- [Configuration](configuration.md)
- [Cache](cache.md)
- [Database](database.md)
- [Observability](observability.md)
- [Security](security.md)
- [Kubernetes and rollback](kubernetes.md)
- [Development and validation](development.md)
- [Changelog](changelog.md)

## Links

- [Microsserv52 organization](https://github.com/Microsserv52)
- [Platform repository](https://github.com/Microsserv52/platform)
- [Published platform documentation](https://microsserv52.github.io/platform/)
- [Product service repository](https://github.com/Microsserv52/product)
- [Product interface repository](https://github.com/Microsserv52/product-interface)
- [Product interface documentation](https://microsserv52.github.io/product-interface/)
