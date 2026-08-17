# ReportPortal Performance Plugins

[![Java](https://img.shields.io/badge/Java-11%2B-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![ReportPortal](https://img.shields.io/badge/ReportPortal-5.x-39c2d7)](https://reportportal.io/)
[![JMeter](https://img.shields.io/badge/JMeter-5.5-d22128?logo=apache&logoColor=white)](https://jmeter.apache.org/)
[![Gatling](https://img.shields.io/badge/Gatling-3.6–3.9-ff9e2a)](https://gatling.io/)
[![Maven Central](https://img.shields.io/maven-central/v/com.epam.reportportal/plugin-gatling-akka?label=Maven%20Central)](https://central.sonatype.com/artifact/com.epam.reportportal/plugin-gatling-akka)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Made with love](https://img.shields.io/badge/Made%20with-%E2%9D%A4%EF%B8%8F-ff69b4)](https://github.com/reportportal/plugins-performance)

Official [ReportPortal](https://reportportal.io) plugins for **JMeter** and **Gatling**.
They report aggregated latency percentiles, error rates, an SLA quality gate, and the
details of failed requests into a ReportPortal launch.

Repository: [reportportal/plugins-performance](https://github.com/reportportal/plugins-performance)

## Table of contents

- [Modules](#modules)
- [ReportPortal setup](#reportportal-setup)
  - [Base URL](#base-url)
  - [Project name](#project-name)
  - [API token](#api-token)
- [What ends up in ReportPortal](#what-ends-up-in-reportportal)
- [JMeter plugin](#jmeter-plugin)
  - [Install](#install)
  - [Add and configure the Backend Listener](#add-and-configure-the-backend-listener)
  - [Parameters](#parameters)
  - [Verify the run](#verify-the-run)
  - [HTML dashboard attachment](#html-dashboard-attachment)
  - [Official vs community plugin](#official-vs-community-plugin)
- [Gatling plugin](#gatling-plugin)
- [Limitations](#limitations)
- [Planned improvements](#planned-improvements)
- [Build from source](#build-from-source)
- [Release](#release)
- [Publishing to Maven Central](#publishing-to-maven-central)
- [License](#license)

## Modules

| Module | Artifact | Role |
|---|---|---|
| `commons-performance` | `com.epam.reportportal:commons-performance` | shared client, metrics, SLA (Maven Central) |
| `plugin-jmeter` | drop-in `plugin-jmeter-*-shaded.jar` | JMeter Backend Listener (GitHub Releases only) |
| `plugin-gatling-akka` | `com.epam.reportportal:plugin-gatling-akka` | Gatling 3.6–3.9 Scala / Akka (Maven Central) |
| `plugin-gatling-pekko` | stub | Gatling 3.10+ Pekko — not implemented yet |

Requirements: **Java 11+**, **ReportPortal 5.x**, JMeter **5.5** (compile target) or Gatling **3.6–3.9**.

## ReportPortal setup

The plugins need three values: **base URL**, **project name**, and **API token**.
They are the same for JMeter and Gatling; only the parameter names differ.

### Base URL

Use the root URL of your ReportPortal instance — the same host you open in the browser.

| Correct | Incorrect |
|---|---|
| `https://demo.reportportal.io` | `https://demo.reportportal.io/ui/` |
| `https://rp.example.com` | `https://rp.example.com/api/v1` |
| `https://rp.example.com` | `https://rp.example.com/ui/#default_personal` |

Rules:

- Prefer **`https://`**. Plain `http://` works but the API token is sent unencrypted; the
  plugin logs a warning.
- Do **not** append `/ui`, `/api`, `/api/v1`, or a project path. The client adds the API
  path itself.
- No trailing slash required (`https://rp.example.com` and `https://rp.example.com/` are
  both fine).

### Project name

This is the **project name** (slug), **not** the numeric database id.

1. Open ReportPortal in the browser and select the project.
2. Look at the address bar. The project name is the segment after `#`, for example:

   ```text
   https://demo.reportportal.io/ui/#my_project/dashboard
                              ^^^^^^^^^^
   ```

3. Personal projects often look like `default_personal` or `john_smith_personal`.
4. Put exactly that string into `Project_Name` (JMeter) or `rp.project` / `RP_PROJECT`
   (Gatling).

Wrong: `12345`, `My Project` (display title with spaces), or a UUID.
Right: `my_project`, `default_personal`.

### API token

1. Sign in to ReportPortal.
2. Open your **user avatar** → **Profile** (or **User profile**).
3. Find **API keys** / **Access token** and generate or copy a token.
4. Paste it into `API_Token` (JMeter) or `rp.api.key` / `RP_API_KEY` (Gatling).

Treat the token like a password. Do not commit it to Git; prefer environment variables
or a local config that stays out of the repository.

The token must belong to a user who can create launches in the target project
(typically PROJECT_MANAGER or MEMBER with reporting rights — exact role names depend on
your ReportPortal version).

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

## JMeter plugin

Implementation class (must match exactly):

```text
com.epam.reportportal.jmeter.PerformanceReporterClient
```

### Install

**From GitHub Release (recommended)**

1. Download `plugin-jmeter-1.0.0-shaded.jar` from
   [JMeter 1.0.0 release](https://github.com/reportportal/plugins-performance/releases/tag/jmeter-v1.0.0).
2. Copy it into `$JMETER_HOME/lib/ext/`.
3. Restart JMeter (GUI or stop/start the CLI process).

**From source**

```bash
./mvnw -pl plugin-jmeter -am package
cp plugin-jmeter/target/plugin-jmeter-*-shaded.jar "$JMETER_HOME/lib/ext/"
```

Only the **`-shaded.jar`** belongs in `lib/ext`. The thin JAR without classifier is not
self-contained.

### Add and configure the Backend Listener

1. Open your test plan in JMeter.
2. Right-click the **Test Plan** (or a Thread Group) → **Add** → **Listener** →
   **Backend Listener**.
3. In **Backend Listener implementation**, select or type:

   ```text
   com.epam.reportportal.jmeter.PerformanceReporterClient
   ```

   If the class is missing from the dropdown, the shaded JAR is not on the classpath —
   check `lib/ext` and restart JMeter.
4. Fill the parameters table (see below). At minimum set:

   | Parameter | Example |
   |---|---|
   | `ReportPortal_URL` | `https://rp.example.com` |
   | `Project_Name` | `my_project` |
   | `API_Token` | *(your token)* |
   | `Launch_Name` | `Checkout load test` |

5. Keep the Backend Listener **enabled**. Place it where it can see the samples you care
   about (usually under the Test Plan so all Thread Groups are included).
6. Run the plan from the GUI (**Start**) or non-GUI:

   ```bash
   jmeter -n -t my-plan.jmx -l results.jtl
   ```

### Parameters

| Parameter | Default | Meaning |
|---|---|---|
| `ReportPortal_URL` | `http://localhost:8080` | ReportPortal base URL — see [Base URL](#base-url) |
| `Project_Name` | `default_personal` | Project **name** (slug), not numeric id — see [Project name](#project-name) |
| `API_Token` | `your_api_token_here` | User API token — see [API token](#api-token) |
| `Launch_Name` | `JMeter Performance Metrics` | Name of the launch created in ReportPortal |
| `Attach_HTML_Dashboard` | `true` | Generate the native JMeter HTML dashboard and attach it as a ZIP |
| `SLA_P95_MS` | *(empty)* | Global p95 threshold in ms; empty disables the check |
| `SLA_P99_MS` | *(empty)* | Global p99 threshold in ms; empty disables the check |
| `SLA_ERROR_RATE_PCT` | *(empty)* | Global max error rate in percent; empty disables the check |
| `Sample_Include_Regex` | *(empty)* | Only report labels matching this Java regex |
| `Sample_Exclude_Regex` | *(empty)* | Drop labels matching this Java regex (applied first) |
| `Attribute_1` … `Attribute_5` | *(not listed)* | Launch attributes: `key:value`, `key=value`, or a bare tag |

`Attribute_1` … `Attribute_5` are **not** created by default. Add rows manually in the
Backend Listener parameters table when you need custom launch attributes.

Examples:

```text
Attribute_1 = env:staging
Attribute_2 = release=24.3
Attribute_3 = nightly
```

### Verify the run

After the test finishes:

1. Open ReportPortal → your project → **Launches**.
2. Find the launch named as in `Launch_Name` (status PASSED or FAILED depending on SLA /
   failures).
3. Open **Performance Summary Report** — Markdown tables with global and per-request
   metrics, plus the SLA block if thresholds were set.
4. Under **Scenario: …** open a request that failed — you should see up to 20 failed
   sample steps with response details.

If nothing appears in ReportPortal, check the JMeter log (`jmeter.log`) for ReportPortal
HTTP errors: wrong URL, wrong project name, or invalid token are the usual causes.

### HTML dashboard attachment

With `Attach_HTML_Dashboard=true` the plugin writes a temporary JTL during the run,
builds the standard JMeter HTML report at teardown, and uploads
`jmeter-html-report.zip` under the summary item.

Download the ZIP from the launch log, extract it, and open `index.html` in a browser.
ReportPortal cannot render that dashboard inline. For large tests the ZIP can be heavy —
set `Attach_HTML_Dashboard` to `false` if you do not need it.

### Official vs community plugin

There is a separate community Backend Listener in the Plugins Manager under id
`jmeter.backendlistener.reportportal` (different vendor and class).

| | Official (this repo) | Community |
|---|---|---|
| Class | `com.epam.reportportal.jmeter.PerformanceReporterClient` | `io.github.prasantmohanty…ReportPortalBackendClient` |
| Vendor | ReportPortal | Prasanta Mohanty |
| Source | [plugins-performance](https://github.com/reportportal/plugins-performance) | separate GitHub project |

Use only **one** of them in a given test plan.

## Gatling plugin

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

ReportPortal credentials for Gatling use the same values as above
([ReportPortal setup](#reportportal-setup)), exposed as `rp.endpoint` / `RP_ENDPOINT`,
`rp.project` / `RP_PROJECT`, and `rp.api.key` / `RP_API_KEY` (see the config table below).

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
- The Gatling drop-in shaded JAR still bundles dependencies without relocating them, so a
  host Gatling runtime shipping a different version of the same library can clash. The
  JMeter shaded JAR relocates jackson, okhttp, kotlin and related packages under
  `com.epam.reportportal.jmeter.shaded.*`.

## Planned improvements

- Gatling 3.10+ (Pekko) support in `plugin-gatling-pekko`.
- Per-request SLA thresholds, plus throughput and error-type breakdowns.
- Relocated packages in the Gatling shaded JAR to remove classpath conflicts.
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
