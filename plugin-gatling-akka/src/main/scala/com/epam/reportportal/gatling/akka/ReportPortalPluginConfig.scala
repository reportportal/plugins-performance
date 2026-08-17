package com.epam.reportportal.gatling.akka

import com.epam.reportportal.common.CustomLaunchAttributes
import com.epam.reportportal.common.SampleFilter
import com.epam.reportportal.common.SlaConfig
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.util.{Collections, List => JList}
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * ReportPortal settings for the Gatling plugin.
 *
 * Resolution order (first non-blank wins): system property, environment variable,
 * `gatling.reportportal.*` in Typesafe Config, then `reportportal.properties`.
 */
final case class ReportPortalPluginConfig(
    endpoint: String,
    apiToken: String,
    project: String,
    launchName: String,
    slaConfig: SlaConfig,
    sampleFilter: SampleFilter,
    customAttributes: JList[ItemAttributesRQ]
) {

  /** Overridden because the generated case class `toString` would print the API token. */
  override def toString: String =
    s"ReportPortalPluginConfig(endpoint=$endpoint, " +
      s"apiToken=${ReportPortalPluginConfig.RedactedToken}, " +
      s"project=$project, launchName=$launchName)"
}

object ReportPortalPluginConfig {
  val DefaultLaunchName = "Gatling Performance Metrics"
  val RedactedToken = "***"

  def load(): ReportPortalPluginConfig = load(ConfigFactory.load())

  def load(config: Config): ReportPortalPluginConfig =
    load(config, k => Option(System.getProperty(k)), k => Option(System.getenv(k)))

  def load(
      config: Config,
      sysProp: String => Option[String],
      env: String => Option[String]
  ): ReportPortalPluginConfig = {
    val rpFile = Try(ConfigFactory.parseResources("reportportal.properties")).getOrElse(ConfigFactory.empty())

    def first(values: Option[String]*): Option[String] =
      values.flatten.map(_.trim).find(_.nonEmpty)

    def gatling(path: String): Option[String] = {
      val full = s"gatling.reportportal.$path"
      if (config.hasPath(full)) Option(config.getString(full)) else None
    }

    def rp(key: String): Option[String] =
      if (rpFile.hasPath(key)) Option(rpFile.getString(key)) else None

    val endpoint = first(
      sysProp("rp.endpoint"),
      env("RP_ENDPOINT"),
      gatling("endpoint"),
      rp("rp.endpoint")
    ).getOrElse {
      throw new IllegalStateException(
        "ReportPortal endpoint is required (rp.endpoint / RP_ENDPOINT / gatling.reportportal.endpoint)"
      )
    }

    val apiToken = first(
      sysProp("rp.api.key"),
      env("RP_API_KEY"),
      env("RP_TOKEN"),
      gatling("apiKey"),
      gatling("api-key"),
      rp("rp.api.key")
    ).getOrElse {
      throw new IllegalStateException(
        "ReportPortal API token is required (rp.api.key / RP_API_KEY / gatling.reportportal.apiKey)"
      )
    }

    val project = first(
      sysProp("rp.project"),
      env("RP_PROJECT"),
      gatling("project"),
      rp("rp.project")
    ).getOrElse {
      throw new IllegalStateException(
        "ReportPortal project is required (rp.project / RP_PROJECT / gatling.reportportal.project)"
      )
    }

    val launchName = first(
      sysProp("rp.launch"),
      env("RP_LAUNCH"),
      gatling("launch"),
      rp("rp.launch")
    ).getOrElse(DefaultLaunchName)

    val slaConfig = SlaConfig.fromParameters(
      first(sysProp("rp.sla.p95.ms"), env("RP_SLA_P95_MS"), gatling("sla.p95Ms"), gatling("sla.p95-ms")).orNull,
      first(sysProp("rp.sla.p99.ms"), env("RP_SLA_P99_MS"), gatling("sla.p99Ms"), gatling("sla.p99-ms")).orNull,
      first(
        sysProp("rp.sla.error.rate.pct"),
        env("RP_SLA_ERROR_RATE_PCT"),
        gatling("sla.errorRatePct"),
        gatling("sla.error-rate-pct")
      ).orNull
    )

    val sampleFilter = SampleFilter.fromParameters(
      first(
        sysProp("rp.sample.include.regex"),
        env("RP_SAMPLE_INCLUDE_REGEX"),
        gatling("sample.includeRegex"),
        gatling("sample.include-regex")
      ).orNull,
      first(
        sysProp("rp.sample.exclude.regex"),
        env("RP_SAMPLE_EXCLUDE_REGEX"),
        gatling("sample.excludeRegex"),
        gatling("sample.exclude-regex")
      ).orNull
    )

    val attributeValues = (1 to CustomLaunchAttributes.MAX).map { i =>
      first(
        sysProp(s"rp.attribute.$i"),
        env(s"RP_ATTRIBUTE_$i"),
        gatling(s"attribute.$i"),
        gatling(s"attributes.$i")
      ).orNull
    }

    val fromList: Seq[String] =
      if (config.hasPath("gatling.reportportal.attributes")
        && config.getValue("gatling.reportportal.attributes").valueType().name() == "LIST") {
        config.getStringList("gatling.reportportal.attributes").asScala.toSeq
      } else {
        Seq.empty
      }

    val mergedAttributes =
      if (attributeValues.exists(_ != null)) attributeValues.toArray
      else fromList.toArray

    val customAttributes =
      if (mergedAttributes.isEmpty) Collections.emptyList[ItemAttributesRQ]()
      else CustomLaunchAttributes.fromParameters(mergedAttributes: _*)

    ReportPortalPluginConfig(
      endpoint = endpoint,
      apiToken = apiToken,
      project = project,
      launchName = launchName,
      slaConfig = slaConfig,
      sampleFilter = sampleFilter,
      customAttributes = customAttributes
    )
  }
}
