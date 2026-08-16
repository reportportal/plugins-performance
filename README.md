# Load Testing ReportPortal Plugins

ReportPortal integration for JMeter and Gatling.

Modules:

- `rp-common` — shared reporting / SLA
- `jmeter-plugin` — Backend Listener
- `gatling-akka` — Gatling 3.6–3.9 (Akka)
- `gatling-pekko` — Gatling 3.10+ (Pekko)

Java 11+.

## Build

```bash
./mvnw clean package
```

JARs land in each module's `target/`.

## JMeter

Drop `plugin-jmeter-reportportal-*.jar` into `$JMETER_HOME/lib/ext/`.

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

- JMeter: tag `jmeter-v1.0.0`, or Actions → Release → plugin `jmeter`
- Gatling: tag `gatling-v1.0.0`, or plugin `gatling` (Akka JAR; Pekko is still a stub)
- Both: tag `v1.0.0`, or plugin `all`
