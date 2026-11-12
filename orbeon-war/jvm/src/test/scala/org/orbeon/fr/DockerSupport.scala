/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.fr

import org.orbeon.oxf.util.StringUtils.*

import java.time.LocalDateTime
import scala.concurrent.duration.*
import scala.sys.process.Process
import scala.util.Try
import scala.util.control.NonFatal

object DockerSupport {

  import Private.*

  // We pass parameters to the test from sbt using the `BuildInfo` plugin.
  val BaseDirectory: String = TestParametersFromSbt.baseDirectory

  val OrbeonDockerNetwork = "orbeon_test_nw"

  def eventually[T](interval: FiniteDuration, timeout: FiniteDuration)(block: => T): T = {
    val deadline = timeout.fromNow
    var lastError: Throwable = null
    while (deadline.hasTimeLeft()) {
      try {
        return block
      } catch {
        case NonFatal(t) =>
          lastError = t
          Thread.sleep(interval.toMillis)
      }
    }
    throw lastError
  }

  def eventuallyAsTry[T](interval: FiniteDuration, timeout: FiniteDuration)(block: => T): Try[T] =
    Try(eventually(interval, timeout)(block))

  def replacePaths(s: String): String =
    s.replace("$BASE_DIRECTORY", BaseDirectory).replace("$HOME", System.getProperty("user.home"))

  // Return existing container ids or new container id
  def runContainer(image: String, containerName: String, params: String, checkImageRunning: Boolean): Try[List[String]] = {
    def getContainerIds: Option[String] = runProcessSync("docker", s"ps -q --filter ancestor=$image").trimAllToOpt
    def runNewContainer: List[String]   = List(runProcessSync("docker", s"run -d --name $containerName ${params.trim} $image").trim)

    withInfo(s"running container image `$image` name `$containerName`") {
      Try {
        waitUntilImageAvailable(image)
        val existingContainerIdsOpt = if (!checkImageRunning) None else getContainerIds
        existingContainerIdsOpt
          .map(_.splitTo[List]())
          .getOrElse(runNewContainer)
      }
    }
  }

  def removeContainerByImage(image: String): Unit = {
    def removeContainers(containerIds: String): Unit =
      runProcessSync("docker", s"rm -f $containerIds")

    withInfo(s"removing container by image name `$image`") {
      runProcessSync("docker", s"ps -q --filter ancestor=$image").trimAllToOpt.foreach(removeContainers)
    }
  }

  def removeContainerByIdAndWait(containerId: String): Unit =
    withInfo(s"removing container by id `$containerId`") {
      removeContainerById(containerId)
      waitForContainerStopById(containerId)
    }

  def createNetworkIfNeeded(): String =
    findNetworkId.getOrElse(runProcessSync("docker", s"network create --driver bridge $OrbeonDockerNetwork").trim)

  def removeNetworkIfNeeded(): Unit =
    findNetworkId.foreach(_ => runProcessSync("docker", s"network remove $OrbeonDockerNetwork"))

  private object Private {

    val ImageWaitDelay   = 100.millis
    val ImageWaitTimeout = 180.seconds

    def withInfo[T](message: => String)(body: => T): T = {
      def timestamp: String = LocalDateTime.now().toString
      println(s"$timestamp - Start $message")
      try {
        body
      } finally {
        println(s"$timestamp - End $message")
      }
    }

    def runProcessSync(cmd: String, params: String): String = {
      val replacedCmd    = replacePaths(cmd)
      val replacedParams = replacePaths(params).trim.replace("\n", " ").splitTo[List]()
      Process(replacedCmd :: replacedParams).!!
    }

    def waitUntilImageAvailable(image: String): String =
      withInfo(s"waiting for image `$image` to be available") {
        eventually(interval = ImageWaitDelay, timeout = ImageWaitTimeout) {
          val res = runProcessSync("docker", s"images -q $image").trim
          assert(res.nonEmpty)
          res
        }
      }

    def waitForContainerStopById(containerId: String): Unit =
      eventually(interval = ImageWaitDelay, timeout = ImageWaitTimeout) {
        assert(runProcessSync("docker", s"ps -q --filter id=$containerId").trimAllToOpt.isEmpty)
      }

    def removeContainerById(containerId: String): Unit =
      runProcessSync("docker", s"ps -q --filter id=$containerId").trimAllToOpt.foreach { _ =>
        runProcessSync("docker", s"rm -f $containerId")
      }

    def findNetworkId: Option[String] =
      runProcessSync("docker", s"network ls -q --filter name=$OrbeonDockerNetwork").trimAllToOpt
  }
}
