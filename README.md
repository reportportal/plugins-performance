# ReportPortal Performance Plugins

Report JMeter and Gatling load-test results to [ReportPortal](https://reportportal.io):
aggregated latency percentiles, error rates, an SLA quality gate, and the details of
failed requests.

Repo: `reportportal/plugins-performance`

| Module | Maven artifact | Role |
|---|---|---|
| `commons-performance` | `com.epam.reportportal:commons-performance` | shared client, metrics, SLA |
| `plugin-jmeter` | `com.epam.reportportal:plugin-jmeter` | JMeter Backend Listener (drop-in JAR only) |
| `plugin-gatling-akka` | `com.epam.reportportal:plugin-gatling-akka` | Gatling 3.6–3.9 (Scala / Akka) |
| `plugin-gatling-pekko` | `com.epam.reportportal:plugin-gatling-pekko` | Gatling 3.10+ (Pekko) — empty stub |

Requirements: Java 11+, ReportPortal 5.x, JMeter 5.5 (compiled against) or Gatling 3.6–3.9.

## What ends up in ReportPortal

One launch per test run, with this hierarchy:

```
Launch  (attributes: p50, p95, p99, sla=PASS|FAIL + your custom attributes)
├── Performance Summary Report          STEP   -> SLA table, global and per-request metrics (Markdown logs)
└── Scenario: <thread group / scenario>  SUITE
    └── <request label>                  SUITE  -> PASSED, or FAILED if it had any failure
        └── <thread> | <code> | <ms>     STEP   -> failed samples only, with an ERROR log
```

The launch description holds the SLA table, so the quality gate is visible from the
launch list. Response times are aggregated in memory with HdrHistogram — individual
successful samples are never stored or sent.

## JMeter

1. Build or download `plugin-jmeter-<version>-shaded.jar` and drop it into
   `$JMETER_HOME/lib/ext/`.
2. Restart JMeter, add a **Backend Listener** to the test plan and set the
   implementation class to `com.epam.reportportal.jmeter.PerformanceReporterClient`.
3. Fill in the parameters.

| Parameter | Default | Meaning |
|---|---|---|
| `ReportPortal_URL` | `http://localhost:8080` | ReportPortal base URL (use `https://` in real setups) |
| `Project_Name` | `default_personal` | target project |
| `API_Token` | — | API token from your ReportPortal profile |
| `Launch_Name` | `JMeter Performance Metrics` | launch name |
| `Attach_HTML_Dashboard` | `true` | generate the native JMeter dashboard and attach it as a ZIP |
| `SLA_P95_MS`, `SLA_P99_MS` | empty | latency thresholds in ms; empty disables the check |
| `SLA_ERROR_RATE_PCT` | empty | max error rate in percent; empty disables the check |
| `Sample_Include_Regex` | empty | only report labels matching this Java regex |
| `Sample_Exclude_Regex` | empty | drop labels matching this Java regex (applied first) |
| `Attribute_1` … `Attribute_5` | not listed | launch attributes, `key:value`, `key=value`, or a bare tag |

The `Attribute_*` rows are not created by default — add them manually in the Backend
Listener table when you need them.

With `Attach_HTML_Dashboard=true` the plugin writes a temporary JTL file during the run,
renders the standard HTML dashboard at the end, and uploads it as
`jmeter-html-report.zip` under the summary item. Download and unzip it, then open
`index.html`; ReportPortal cannot render the dashboard inline.

## Gatling

Only the Akka-based line (Gatling 3.6–3.9, Scala) is supported. Add the plugin to the
test classpath either as a Maven dependency:

```xml
<dependency>
    <groupId>com.epam.reportportal</groupId>
    <artifactId>plugin-gatling-akka</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

or, for the Gatling bundle, by copying `plugin-gatling-akka-<version>-shaded.jar` into
`$GATLING_HOME/lib/`. The shaded JAR deliberately excludes Gatling, Scala, SLF4J and
Jackson, so it only works inside a Gatling runtime.

### Live mode (recommended)

`com.epam.reportportal.gatling.akka.Gatling` is a drop-in replacement for
`io.gatling.app.Gatling`. It wraps Gatling's `StatsEngine`, so every request is reported
while the simulation runs, with real scenario names and response codes.

`gatling-maven-plugin` and `gatling-sbt` hardcode `io.gatling.app.Gatling`, so the main
class has to be swapped by the launcher:

- **Gatling bundle**: in `bin/gatling.sh` (or `gatling.bat`), replace `io.gatling.app.Gatling`
  with `com.epam.reportportal.gatling.akka.Gatling`.
- **Maven**: compile the simulations as usual (`mvn test-compile`, or one `mvn gatling:test`
  run), then launch them through `exec-maven-plugin`:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.5.0</version>
    <configuration>
        <executable>java</executable>
        <classpathScope>test</classpathScope>
        <arguments>
            <argument>-classpath</argument>
            <classpath/>
            <argument>com.epam.reportportal.gatling.akka.Gatling</argument>
            <argument>-s</argument>
            <argument>com.example.MySimulation</argument>
        </arguments>
    </configuration>
</plugin>
```

```bash
mvn test-compile exec:exec -Drp.endpoint=https://rp.example.com -Drp.api.key=$RP_TOKEN -Drp.project=my_project
```

### Post-run mode (no launcher changes)

When the main class cannot be changed, mix `ReportPortalSimulation` into the simulation.
It starts a launch in `before`, and in `after` it parses the newest `simulation.log` from
the results directory and pushes everything to ReportPortal.

```scala
import com.epam.reportportal.gatling.akka.Predef.ReportPortalSimulation

class MySimulation extends ReportPortalSimulation {
  setUp(scn.inject(atOnceUsers(10))).protocols(httpProtocol)
}
```

This works with plain `mvn gatling:test`, but it needs Gatling's built-in `file` data
writer (enabled by default) and it reports less: no live updates, no response codes, and
all requests land under a single `Scenario: Gatling` node because `simulation.log` does
not carry the scenario name.

### Gatling configuration

Every setting is resolved from the first non-blank source in this order: JVM system
property, environment variable, `gatling.reportportal.*` in `gatling.conf`, then
`reportportal.properties` on the classpath.

| Setting | System property | Environment variable | `gatling.conf` key |
|---|---|---|---|
| Endpoint (required) | `rp.endpoint` | `RP_ENDPOINT` | `endpoint` |
| API token (required) | `rp.api.key` | `RP_API_KEY`, `RP_TOKEN` | `apiKey` / `api-key` |
| Project (required) | `rp.project` | `RP_PROJECT` | `project` |
| Launch name | `rp.launch` | `RP_LAUNCH` | `launch` |
| SLA p95 (ms) | `rp.sla.p95.ms` | `RP_SLA_P95_MS` | `sla.p95Ms` |
| SLA p99 (ms) | `rp.sla.p99.ms` | `RP_SLA_P99_MS` | `sla.p99Ms` |
| SLA error rate (%) | `rp.sla.error.rate.pct` | `RP_SLA_ERROR_RATE_PCT` | `sla.errorRatePct` |
| Include labels (regex) | `rp.sample.include.regex` | `RP_SAMPLE_INCLUDE_REGEX` | `sample.includeRegex` |
| Exclude labels (regex) | `rp.sample.exclude.regex` | `RP_SAMPLE_EXCLUDE_REGEX` | `sample.excludeRegex` |
| Attributes (up to 5) | `rp.attribute.1` … `.5` | `RP_ATTRIBUTE_1` … `_5` | `attribute.1` … `.5`, or the `attributes` list |

```hocon
gatling.reportportal {
  endpoint = "https://rp.example.com"
  apiKey = ${?RP_TOKEN}
  project = "my_project"
  launch = "Checkout load test"
  sla {
    p95Ms = 800
    p99Ms = 1500
    errorRatePct = 1
  }
  attributes = ["env:staging", "release:24.3", "nightly"]
}
```

If the launch name is left at its default, live mode uses the simulation class name
instead. Missing endpoint, token or project fails the run with an `IllegalStateException`.

## Limitations

**Gatling**

- Akka-based Gatling only (3.6–3.9). The plugin hooks into internals that are
  package-private in `io.gatling.app`, and CI builds it against 3.6.1; other 3.x versions
  in that range are expected to work but are not covered by tests.
- `plugin-gatling-pekko` (Gatling 3.10+, Pekko) is a placeholder with no functionality.
- Live mode requires replacing the process main class; there is no way to enable it purely
  through configuration.
- `ReportPortalDataWriter` cannot be activated via `gatling.data.writers`, because Gatling
  OSS only accepts its own built-in writer names.
- Gatling exposes no request or response body, so failure logs contain the error message
  only.

**JMeter**

- Compiled against JMeter 5.5; newer 5.x releases are untested.
- In distributed (remote) runs the Backend Listener executes on every engine, so each
  engine creates its own launch — results are not merged.

**Both**

- SLA thresholds are global (p95, p99, error rate over all requests) and evaluated once at
  the end of the run. There are no per-request thresholds, and no throughput, latency or
  connect-time metrics.
- Percentiles come from an HdrHistogram with 2 significant digits, so they carry up to ~1%
  error, and any response time above 1 hour is clipped to 1 hour.
- At most 1000 distinct request labels per run; everything after that is folded into
  `(other requests)`. Labels that embed dynamic values (ids, timestamps) make per-request
  metrics useless.
- At most 20 failed samples are reported per label; further failures are counted in the
  metrics only, and a warning is logged on the request item. Response bodies are truncated
  at 3000 characters.
- At most 5 custom launch attributes.
- Every run starts a new launch; attaching to an existing launch or a rerun is not
  supported. Other `client-java` options (proxy, `rp.mode`, batching, `rp.launch.uuid`) are
  ignored — only the settings above are read.
- Drop-in shaded JARs bundle their dependencies without relocating them, so a host tool
  shipping a different version of the same library can clash.

## Planned improvements

- Gatling 3.10+ (Pekko) support in `plugin-gatling-pekko`.
- Per-request SLA thresholds, plus throughput and error-type breakdowns.
- Relocated packages in the shaded JARs to remove classpath conflicts.
- Scenario names and response codes in post-run mode by parsing more of `simulation.log`.
- Attaching Grafana panel snapshots for the run's time range next to the summary report.
- Configurable label cap, failed-sample cap and histogram precision.
- Pass-through for the remaining `client-java` settings (proxy, debug mode, batching) and
  reporting into an existing launch.

## Build from source

```bash
./mvnw clean package                # drop-in *-shaded.jar in each module target/
./mvnw -Pmaven-central package      # + sources, javadoc, flattened POM
./mvnw -Pmaven-central install -pl commons-performance,plugin-gatling-akka -am
```

Maven consumers get the thin `commons-performance` and `plugin-gatling-akka` JARs;
JMeter and bundle users get the `*-shaded.jar`.

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

## License

Apache License 2.0 — see [LICENSE](LICENSE).
