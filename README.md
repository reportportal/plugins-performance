# ReportPortal Performance Plugins

ReportPortal integration for JMeter and Gatling.

Repo: `reportportal/plugins-performance`

| Module | Maven artifact | Role |
|---|---|---|
| `commons-performance` | `com.epam.reportportal:commons-performance` | shared library |
| `plugin-jmeter` | `com.epam.reportportal:plugin-jmeter` | JMeter Backend Listener |
| `plugin-gatling-akka` | `com.epam.reportportal:plugin-gatling-akka` | Gatling 3.6–3.9 |
| `plugin-gatling-pekko` | `com.epam.reportportal:plugin-gatling-pekko` | Gatling 3.10+ (stub) |

Java 11+.

## Build

```bash
./mvnw clean package
./mvnw -Pmaven-central package   # sources + javadoc + flattened POM
```

Drop-in plugins: `*-shaded.jar` in each module `target/`.
Maven artifacts: thin `commons-performance` / `plugin-gatling-akka` jars (no shade).

```bash
./mvnw -Pmaven-central install -pl commons-performance,plugin-gatling-akka -am
```

## JMeter

Drop `plugin-jmeter-*-shaded.jar` into `$JMETER_HOME/lib/ext/`.

Backend Listener class: `com.epam.reportportal.jmeter.PerformanceReporterClient`

Params (draft):

- `ReportPortal_URL`, `Project_Name`, `API_Token`, `Launch_Name`
- `Attach_HTML_Dashboard`
- `SLA_P95_MS`, `SLA_P99_MS`, `SLA_ERROR_RATE_PCT`
- `Sample_Include_Regex`, `Sample_Exclude_Regex`
- `Attribute_1` … `Attribute_5` (optional, `key:value` or tag)

## Gatling

Config via `gatling.reportportal.*` or `reportportal.properties`. See `reference.conf`.

TODO: usage example, install notes.

## Notes

- SLA empty = disabled
- Failed samples + summary metrics go to RP

## Release

Drop-in JARs for JMeter/Gatling users are attached to a GitHub Release:

- JMeter: tag `jmeter-v1.0.0`, or Actions → Release → plugin `jmeter`
- Gatling: tag `gatling-v1.0.0`, or plugin `gatling` (Akka JAR; Pekko is still a stub)
- Both: tag `v1.0.0`, or plugin `all`

## Publishing to Maven Central

Only `commons-performance` and `plugin-gatling-akka` are published as Maven
dependencies; the JMeter plugin stays a drop-in JAR.

1. Release version `1.0.0` (tag `gatling-v1.0.0` or `v1.0.0`). Besides the
   GitHub Release, the `packages` job deploys GPG-signed jar, sources, javadoc
   and flattened POM to GitHub Packages.
2. Actions → Publish to Maven Central, version `1.0.0`. The shared ReportPortal
   workflow bundles those files from GitHub Packages and uploads them to Sonatype.

Organization secrets: `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` for step 1,
`SONATYPE_USER` / `SONATYPE_PASSWORD` for step 2.
