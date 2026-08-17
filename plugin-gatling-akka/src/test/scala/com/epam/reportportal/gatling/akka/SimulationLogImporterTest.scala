package com.epam.reportportal.gatling.akka

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimulationLogImporterTest {

  @Test
  def parseRequest_withoutGroups(): Unit = {
    val parsed = SimulationLogImporter.parseRequest("REQUEST\tHome\t1000\t1250\tOK\t ")

    assertTrue(parsed.isDefined)
    val sample = parsed.get
    assertEquals("Home", sample.name)
    assertEquals(1000L, sample.startTimestamp)
    assertEquals(1250L, sample.endTimestamp)
    assertEquals("OK", sample.status.name)
    assertTrue(sample.groups.isEmpty)
  }

  @Test
  def parseRequest_withGroupsAndFailure(): Unit = {
    val parsed = SimulationLogImporter.parseRequest("REQUEST\tAPI,Cart\tAdd item\t10\t90\tKO\tstatus 500")

    assertTrue(parsed.isDefined)
    val sample = parsed.get
    assertEquals("Add item", sample.name)
    assertEquals(List("API", "Cart"), sample.groups)
    assertEquals("KO", sample.status.name)
    assertEquals(Some("status 500"), sample.message)
  }

  @Test
  def parseRequest_ignoresNonRequestLines(): Unit = {
    assertTrue(SimulationLogImporter.parseRequest("USER\tscenario\tSTART\t1").isEmpty)
    assertTrue(SimulationLogImporter.parseRequest(null).isEmpty)
  }
}
