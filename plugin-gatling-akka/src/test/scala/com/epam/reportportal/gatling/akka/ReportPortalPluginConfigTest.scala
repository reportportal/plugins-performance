package com.epam.reportportal.gatling.akka

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReportPortalPluginConfigTest {

  @Test
  def load_readsGatlingConfigBlock(): Unit = {
    val config = ConfigFactory.parseString(
      """
        |gatling.reportportal {
        |  endpoint = "http://rp.example"
        |  apiKey = "token-1"
        |  project = "proj"
        |  launch = "My Launch"
        |  sla.p95Ms = "500"
        |  sla.errorRatePct = "1.5"
        |  sample.includeRegex = "API"
        |  attributes = ["env:staging", "nightly"]
        |}
        |""".stripMargin
    )

    val loaded = ReportPortalPluginConfig.load(config, _ => None, _ => None)

    assertEquals("http://rp.example", loaded.endpoint)
    assertEquals("token-1", loaded.apiToken)
    assertEquals("proj", loaded.project)
    assertEquals("My Launch", loaded.launchName)
    assertEquals(java.lang.Long.valueOf(500L), loaded.slaConfig.getP95Ms)
    assertEquals(java.lang.Double.valueOf(1.5), loaded.slaConfig.getErrorRatePct)
    assertTrue(loaded.sampleFilter.accept("API/login"))
    assertFalse(loaded.sampleFilter.accept("other"))
    assertEquals(2, loaded.customAttributes.size())
  }

  @Test
  def load_prefersSystemPropertiesOverConfig(): Unit = {
    val config = ConfigFactory.parseString(
      """
        |gatling.reportportal {
        |  endpoint = "http://from-file"
        |  apiKey = "file-token"
        |  project = "file-project"
        |}
        |""".stripMargin
    )

    val loaded = ReportPortalPluginConfig.load(
      config,
      Map("rp.endpoint" -> "http://from-props", "rp.api.key" -> "prop-token", "rp.project" -> "prop-project").get,
      _ => None
    )

    assertEquals("http://from-props", loaded.endpoint)
    assertEquals("prop-token", loaded.apiToken)
    assertEquals("prop-project", loaded.project)
  }

  @Test
  def toString_doesNotExposeApiToken(): Unit = {
    val config = ConfigFactory.parseString(
      """
        |gatling.reportportal {
        |  endpoint = "http://rp.example"
        |  apiKey = "super-secret-token"
        |  project = "proj"
        |}
        |""".stripMargin
    )

    val rendered = ReportPortalPluginConfig.load(config, _ => None, _ => None).toString

    assertFalse(rendered.contains("super-secret-token"))
    assertTrue(rendered.contains(ReportPortalPluginConfig.RedactedToken))
  }

  @Test
  def load_failsWhenEndpointMissing(): Unit = {
    assertThrows(
      classOf[IllegalStateException],
      () => ReportPortalPluginConfig.load(ConfigFactory.empty(), _ => None, _ => None)
    )
  }
}
