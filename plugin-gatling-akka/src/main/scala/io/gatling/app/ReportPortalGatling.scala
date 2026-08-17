package io.gatling.app

import java.nio.file.FileSystems
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import io.gatling.app.cli.ArgsParser
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.scenario.Simulation
import io.gatling.netty.util.Transports
import org.slf4j.LoggerFactory

/**
 * Copy of Gatling 3.6 `io.gatling.app.Gatling.start` that uses [[ReportPortalRunner]].
 * Must live in this package because ArgsParser / Runner / RunResultProcessor are package-private.
 */
object ReportPortalGatling extends StrictLogging {

  def main(args: Array[String]): Unit = sys.exit(fromArgs(args, None))

  def fromMap(overrides: collection.mutable.Map[String, _]): Int = start(overrides, None)

  def fromArgs(args: Array[String], selectedSimulationClass: Option[Class[Simulation]]): Int =
    new ArgsParser(args).parseArguments match {
      case Left(overrides)   => start(overrides, selectedSimulationClass)
      case Right(statusCode) => statusCode.code
    }

  private def terminateActorSystem(system: ActorSystem, timeout: FiniteDuration): Unit =
    try {
      Await.result(system.terminate(), timeout)
    } catch {
      case NonFatal(e) =>
        logger.debug("Could not terminate ActorSystem", e)
    }

  private def start(
      overrides: collection.mutable.Map[String, _],
      selectedSimulationClass: Option[Class[Simulation]]
  ): Int =
    try {
      logger.trace("Starting")
      FileSystems.getDefault
      val configuration = GatlingConfiguration.load(overrides)
      logger.trace("Configuration loaded")
      val runResult =
        configuration.core.directory.reportsOnly match {
          case Some(runId) => new RunResult(runId, hasAssertions = true)
          case _ =>
            val system = ActorSystem("GatlingSystem", GatlingConfiguration.loadActorSystemConfiguration())
            val eventLoopGroup = Transports.newEventLoopGroup(configuration.netty.useNativeTransport, 0, "gatling")
            try {
              val runner = ReportPortalRunner(system, eventLoopGroup, configuration)
              logger.trace("Runner instantiated")
              runner.run(selectedSimulationClass)
            } catch {
              case e: Throwable =>
                logger.error("Run crashed", e)
                throw e
            } finally {
              eventLoopGroup.shutdownGracefully(0, configuration.core.shutdownTimeout, TimeUnit.MILLISECONDS)
              terminateActorSystem(system, configuration.core.shutdownTimeout.milliseconds)
            }
        }
      new RunResultProcessor(configuration).processRunResult(runResult).code
    } finally {
      val factory = LoggerFactory.getILoggerFactory
      try {
        factory.getClass.getMethod("stop").invoke(factory)
      } catch {
        case _: NoSuchMethodException =>
        case NonFatal(ex)             => logger.warn("Logback failed to shutdown.", ex)
      }
    }
}
