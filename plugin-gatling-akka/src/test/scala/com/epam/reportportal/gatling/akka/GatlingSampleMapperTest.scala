package com.epam.reportportal.gatling.akka

import io.gatling.commons.stats.KO
import io.gatling.commons.stats.OK
import io.gatling.core.stats.writer.ResponseMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GatlingSampleMapperTest {

  @Test
  def toSample_mapsSuccessfulRequestWithGroups(): Unit = {
    val message = ResponseMessage(
      "Checkout",
      List("API", "Cart"),
      "Add item",
      1_000L,
      1_250L,
      OK,
      Some("200"),
      None
    )

    val sample = GatlingSampleMapper.toSample(message)

    assertEquals("API / Cart / Add item", sample.getLabel)
    assertEquals("Checkout", sample.getScenarioName)
    assertEquals(250L, sample.getDurationMs)
    assertTrue(sample.isSuccess)
    assertEquals("200", sample.getResponseCode)
  }

  @Test
  def toSample_mapsFailedRequestWithoutGroups(): Unit = {
    val message = ResponseMessage(
      "Browse",
      Nil,
      "Home",
      10L,
      40L,
      KO,
      Some("500"),
      Some("status 500")
    )

    val sample = GatlingSampleMapper.toSample(message)

    assertEquals("Home", sample.getLabel)
    assertFalse(sample.isSuccess)
    assertEquals("status 500", sample.getResponseMessage)
    assertEquals(30L, sample.getDurationMs)
  }
}
