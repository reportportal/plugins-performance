package com.epam.reportportal.gatling.akka

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

import io.gatling.commons.stats.KO
import io.gatling.commons.stats.OK
import io.gatling.core.Predef
import io.gatling.core.scenario.Simulation
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.Using

/**
 * Mix this into a Simulation when you cannot change the Gatling main class.
 * After the run it imports `simulation.log` (requires the built-in `file` data writer).
 *
 * Prefer [[Gatling]] as the process main class for live, per-request reporting.
 */
trait ReportPortalSimulation extends Simulation {
  private val logger = LoggerFactory.getLogger(getClass)
  @volatile private var handler: ReportPortalHandler = _

  before {
    val config = ReportPortalPluginConfig.load()
    handler = new ReportPortalHandler(config)
    handler.start(None)
  }

  after {
    try {
      if (handler != null && handler.isStarted) {
        importLatestSimulationLog()
        handler.shutdown()
      }
    } catch {
      case e: Exception =>
        logger.error("Failed to import Gatling results into ReportPortal", e)
        if (handler != null) {
          handler.shutdown()
        }
    }
  }

  private def importLatestSimulationLog(): Unit = {
    val resultsDir = Predef.configuration.core.directory.results
    val logFile = findLatestSimulationLog(resultsDir)
    logFile.foreach { path =>
      logger.info("Importing Gatling simulation.log from {}", path)
      SimulationLogImporter.importFile(path, handler)
    }
  }

  private def findLatestSimulationLog(resultsDir: Path): Option[Path] = {
    if (!Files.isDirectory(resultsDir)) {
      logger.warn("Gatling results directory does not exist: {}", resultsDir)
      return None
    }
    val stream = Files.walk(resultsDir)
    try {
      val logs = stream
        .filter(p => Files.isRegularFile(p) && p.getFileName.toString == "simulation.log")
        .collect(Collectors.toList[Path])
        .asScala
      logs.maxByOption(p => Files.getLastModifiedTime(p).toMillis)
    } finally {
      stream.close()
    }
  }
}

object SimulationLogImporter {
  private val logger = LoggerFactory.getLogger(getClass)
  private val Tab = "\t"

  def importFile(path: Path, handler: ReportPortalHandler): Unit = {
    Using(Files.newBufferedReader(path, StandardCharsets.UTF_8)) { reader =>
      reader.lines().iterator().asScala.foreach { line =>
        parseRequest(line).foreach { sample =>
          handler.onResponse(
            sample.scenario,
            sample.groups,
            sample.name,
            sample.startTimestamp,
            sample.endTimestamp,
            sample.status,
            sample.responseCode,
            sample.message
          )
        }
      }
    }.recover { case e =>
      logger.error("Failed to parse simulation.log {}", path, e)
    }
  }

  private[akka] def parseRequest(line: String): Option[LogRequest] = {
    if (line == null || !line.startsWith("REQUEST\t")) {
      return None
    }
    val parts = line.split(Tab, -1)
    // REQUEST \t [groups] \t name \t start \t end \t status \t message
    // When groups are empty: REQUEST \t name \t start \t end \t status \t message
    Try {
      if (parts.length >= 6 && looksLikeTimestamp(parts(2)) && looksLikeTimestamp(parts(3))) {
        LogRequest(
          scenario = "Gatling",
          groups = Nil,
          name = parts(1),
          startTimestamp = parts(2).toLong,
          endTimestamp = parts(3).toLong,
          status = if (parts(4) == "KO") KO else OK,
          responseCode = None,
          message = emptyToNone(parts.lift(5).getOrElse(""))
        )
      } else if (parts.length >= 7 && looksLikeTimestamp(parts(3)) && looksLikeTimestamp(parts(4))) {
        val groups = Option(parts(1)).filter(_.trim.nonEmpty).map(_.split(',').toList).getOrElse(Nil)
        LogRequest(
          scenario = "Gatling",
          groups = groups,
          name = parts(2),
          startTimestamp = parts(3).toLong,
          endTimestamp = parts(4).toLong,
          status = if (parts(5) == "KO") KO else OK,
          responseCode = None,
          message = emptyToNone(parts.lift(6).getOrElse(""))
        )
      } else {
        throw new IllegalArgumentException(s"Unrecognized REQUEST line: $line")
      }
    }.toOption
  }

  private def looksLikeTimestamp(value: String): Boolean =
    value.nonEmpty && value.forall(_.isDigit)

  private def emptyToNone(value: String): Option[String] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) None else Some(trimmed)
  }

  private[akka] final case class LogRequest(
      scenario: String,
      groups: List[String],
      name: String,
      startTimestamp: Long,
      endTimestamp: Long,
      status: io.gatling.commons.stats.Status,
      responseCode: Option[String],
      message: Option[String]
  )
}
