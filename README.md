# Node Zero

> Year 2187. You are the Systems Commander (SRE) of orbital server station NODE-01.

Node Zero is an educational sci-fi **tower defense + server design/operations** game.
Threats come from outside (traffic, attacks) and inside (memory, threads, disk), and you
fight them with real-world techniques: Nginx, Redis, PostgreSQL, HikariCP, Tomcat,
Prometheus, Grafana, Kafka and more.

**Educational purpose only.** Technology names are used to teach real concepts. No logos
are used, and the project is not affiliated with or endorsed by any of the named projects.

## Modules

| Module | Description |
|---|---|
| `sim-core` | Pure, deterministic Java 21 simulation engine (no Spring) |

## Build

```bash
./mvnw verify
```

## Docs

- [Game Design Document](docs/GDD.md)
- [Architecture Decision Records](docs/adr/)
