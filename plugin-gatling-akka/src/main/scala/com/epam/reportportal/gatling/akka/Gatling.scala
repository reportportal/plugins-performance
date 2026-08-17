package com.epam.reportportal.gatling.akka

import io.gatling.app.ReportPortalGatling
import io.gatling.core.scenario.Simulation

/**
 * Drop-in replacement for `io.gatling.app.Gatling` that reports every request to ReportPortal.
 *
 * `gatling-maven-plugin` and `gatling-sbt` hardcode `io.gatling.app.Gatling`, so the swap has to
 * happen in the launcher: the bundle's `gatling.sh`, an `exec-maven-plugin` execution, or a custom
 * runner. Otherwise mix in [[ReportPortalSimulation]].
 */
object Gatling {

  def main(args: Array[String]): Unit = ReportPortalGatling.main(args)

  def fromMap(overrides: collection.mutable.Map[String, _]): Int =
    ReportPortalGatling.fromMap(overrides)

  def fromArgs(args: Array[String], selectedSimulationClass: Option[Class[Simulation]]): Int =
    ReportPortalGatling.fromArgs(args, selectedSimulationClass)
}
