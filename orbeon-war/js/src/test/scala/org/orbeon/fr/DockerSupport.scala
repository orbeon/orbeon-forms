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

import org.orbeon.node
import org.orbeon.node.OS
import org.orbeon.oxf.util.FutureUtils.eventually
import org.orbeon.oxf.util.StringUtils._

import scala.async.Async.{async, await}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.JSStringOps._
import scala.util.Success

object DockerSupport {

  // We pass parameters to the test from sbt using the `BuildInfo` plugin.
  val BaseDirectory: String = TestParametersFromSbt.baseDirectory

  val OrbeonDockerNetwork = "orbeon_test_nw"

  private val ImageWaitDelay   = 100.millis
  private val ImageWaitTimeout = 180.seconds

  implicit def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def replacePaths(s: String): String =
    s.replaceAllLiterally("$BASE_DIRECTORY", BaseDirectory).replaceAllLiterally("$HOME", OS.homedir())

  def withInfo[T](message: => String)(body: => T): T =
    try {
      println(s"start $message")
      body
    } finally {
      println(s"end $message")
    }

  def runProcessSync(cmd: String, params: String): String = {

    val replacedCmd    = replacePaths(cmd)
    val replacedParams = replacePaths(params).trim.replaceAllLiterally("\n", " ")

    withInfo(s"trying to run execFileSync: $replacedCmd $replacedParams") {
      (node.ChildProcess.execFileSync(replacedCmd, replacedParams.jsSplit(" ")): Any) match {
        case v: String      => v
        case v: node.Buffer => v.toString()
        case _              => throw new IllegalStateException
      }
    }
  }

  def runProcessSyncF(cmd: String, params: String): Future[String] = {
    try {
      Future.successful(runProcessSync(cmd, params))
    } catch {
      case t: Throwable =>
        Future.failed(t)
    }
  }

  def waitUntilImageAvailable(image: String): Future[String] = {
    withInfo(s"wait for image `$image` to be available") {
      eventually(interval = ImageWaitDelay, timeout = ImageWaitTimeout) {
        runProcessSyncF("docker", s"images -q $image") map (_.trim) filter (_.nonEmpty)
      }
    }
  }

  // Return existing container ids or new container id
  def runContainer(image: String, params: String, checkImageRunning: Boolean): Future[Success[List[String]]] = async {

    await(waitUntilImageAvailable(image))
    val existingContainerIdsOpt =
      if (! checkImageRunning)
        None
      else
        await(runProcessSyncF("docker", s"ps -q --filter ancestor=$image")).trimAllToOpt

    Success(
      existingContainerIdsOpt match {
        case Some(containerIds) => containerIds.splitTo[List]()
        case None               => List(await(runProcessSyncF("docker", s"run -d ${params.trim} $image")))
      }
    )
  }

  def removeContainerByImage(image: String): Future[Unit] = async {
    await(runProcessSyncF("docker", s"ps -q --filter ancestor=$image")).trimAllToOpt match {
      case Some(containerIds) => await(runProcessSyncF("docker", s"rm -f $containerIds"))
      case None               => None
    }
  }

  def waitForContainerStopById(containerId: String) =
    eventually(interval = ImageWaitDelay, timeout = ImageWaitTimeout) {
      runProcessSyncF("docker", s"ps -q --filter id=$containerId") filter (_.trimAllToOpt.isEmpty)
    }

  def removeContainerById(containerId: String): Future[Unit] = async {
    await(runProcessSyncF("docker", s"ps -q --filter id=$containerId")).trimAllToOpt match {
      case Some(containerIds) => await(runProcessSyncF("docker", s"rm -f $containerId"))
      case None               => None
    }
  }

  def removeContainerByIdAndWait(containerId: String): Future[Unit] = async {
    await(removeContainerById(containerId))
    await(waitForContainerStopById(containerId))
  }

  def findNetworkIdF: Future[Option[String]] =
    runProcessSyncF("docker", s"network ls -q --filter name=$OrbeonDockerNetwork") map (_.trimAllToOpt)

  def createNetworkIfNeeded(): Future[Any] = async {
    await(findNetworkIdF) match {
      case Some(networkId) => networkId
      case None            => await(runProcessSyncF("docker", s"network create --driver bridge $OrbeonDockerNetwork"))
    }
  }

  def removeNetworkIfNeeded(): Future[Unit] =
    findNetworkIdF map { _ => runProcessSyncF("docker", s"network remove $OrbeonDockerNetwork") }
}