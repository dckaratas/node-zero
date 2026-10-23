# Node Zero

> Year 2187. You are the Systems Commander (SRE) of orbital server station NODE-01.

Node Zero is an educational sci-fi **tower defense + server design/operations** game.
Threats come from outside (traffic surges) and inside (log storms, slow queries, batch jobs), and you
fight them with real-world techniques: Prometheus, Grafana, Nginx `limit_req`, PostgreSQL indexes,
`statement_timeout`, HikariCP bulkheads, Tomcat tuning and more.

**Educational purpose only.** Technology names are used to teach real concepts. No logos
are used, and the project is not affiliated with or endorsed by any of the named projects.

## What makes it different

| Idea | In the game |
|---|---|
| Server-authoritative simulation | A pure, deterministic Java engine decides every outcome; the browser only renders |
| Real queueing math | CPU latency from Erlang-C (M/M/c), pools from Little's law, disks fill over time |
| Fog of war | Without monitoring you only see the shield and late user complaints; Prometheus and Grafana reveal metrics |
| Error budget as a shield | SLO 99%: failed requests drain the shield, burn rate is shown live |
| Postmortems | Every wave ends with a root-cause question; the codex explains the real concept |

## Status

Chapter 1 "Ignition" is playable end to end: four threat waves (log storm, colony festival traffic surge,
slow endpoint from a growing table, nightly report job), 15 modules, 5 incident actions, alert rules,
crew (toil), postmortems, a 27-entry codex and EN/TR content.

## Modules

| Module | Description |
|---|---|
| `sim-core` | Pure, deterministic Java 21 simulation engine (no Spring) and the YAML content |
| `sim-bench` | JMH benchmarks for the tick budget (< 20 µs per session) |
| `game-server` | Spring Boot 4 server: sessions, 10 Hz tick shards, WebSocket protocol, Prometheus metrics |
| `client` | TypeScript + PixiJS browser client (Vite): hex station map, HUD, contract-tested protocol |

## Run locally

Requirements: JDK 21+, Node.js 24.

```bash
./mvnw -q install -DskipTests
java -jar game-server/target/game-server-0.1.0-SNAPSHOT-exec.jar   # ws://localhost:8080/ws/game
```

In a second terminal:

```bash
cd client && npm ci && npm run dev   # http://localhost:5173
```

## Build and test

```bash
./mvnw verify                                                    # engine, server, benchmarks
cd client && npm ci && npm run lint && npm test && npm run build # browser client
```

Dependency versions are pinned on purpose (exact npm versions, no Maven ranges, Actions by commit SHA);
CI runs `npm audit`, and only security fixes trigger an upgrade.

## Docs (Turkish)

- [Game Design Document](docs/GDD.md)
- [Architecture Decision Records](docs/adr/): determinism, latency model, performance budget, server and client architecture, incident gameplay
- [Learning notes](docs/learning-notes.md)

## License

[MIT](LICENSE)
