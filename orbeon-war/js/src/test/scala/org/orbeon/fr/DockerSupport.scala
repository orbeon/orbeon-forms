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

import cats.implicits.toFunctorOps
import org.orbeon.node
import org.orbeon.node.OS
import org.orbeon.oxf.util.FutureUtils.eventually
import org.orbeon.oxf.util.StringUtils.*

import scala.async.Async.{async, await}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.JSStringOps.*
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
    s.replace("$BASE_DIRECTORY", BaseDirectory).replace("$HOME", OS.homedir())

  private def withInfo[T](message: => String)(body: => Future[T]): Future[T] = {
    def timestamp: String = new scala.scalajs.js.Date().toLocaleString()
    println(s"$timestamp - Start $message")
    body.transform { result =>
      println(s"$timestamp - End $message")
      result
    }
  }

  private def runProcessSync(cmd: String, params: String): String = {

    val replacedCmd    = replacePaths(cmd)
    val replacedParams = replacePaths(params).trim.replace("\n", " ")

    (node.ChildProcess.execFileSync(replacedCmd, replacedParams.jsSplit(" ")): Any) match {
      case v: String      => v
      case v: node.Buffer => v.toString()
      case _              => throw new IllegalStateException
    }
  }

  private def runProcessSyncF(cmd: String, params: String): Future[String] = {
    try {
      Future.successful(runProcessSync(cmd, params))
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        Future.failed(t)
    }
  }

  private def waitUntilImageAvailable(image: String): Future[String] = {
    withInfo(s"waiting for image `$image` to be available") {
      eventually(interval = ImageWaitDelay, timeout = ImageWaitTimeout) {
        runProcessSyncF("docker", s"images -q $image") map (_.trim) filter (_.nonEmpty)
      }
    }
  }

  // Return existing container ids or new container id
  def runContainer(image: String, containerName: String, params: String, checkImageRunning: Boolean): Future[Success[List[String]]] = {
    def getContainerIds: Future[Option[String]] = runProcessSyncF("docker", s"ps -q --filter ancestor=$image").map(_.trimAllToOpt)
    def runNewContainer: Future[List[String]]   = runProcessSyncF("docker", s"run -d --name $containerName ${params.trim} $image").map(_.trim).map(List(_))

    withInfo(s"running container image `$image` name `$containerName`") {
      for {
        _                       <- waitUntilImageAvailable(image)
        existingContainerIdsOpt <- if (!checkImageRunning) Future.successful(None) else getContainerIds
        containerIds            <- existingContainerIdsOpt
                                     .map(ids => Future.successful(ids.splitTo[List]()))
                                     .getOrElse(runNewContainer)
      } yield Success(containerIds)
    }
  }

  def removeContainerByImage(image: String): Future[Unit] = {
    def removeContainers(containerIds: String): Future[Unit] =
      runProcessSyncF("docker", s"rm -f $containerIds").void

    withInfo(s"removing container by image name `$image`") {
      for {
        containerIdsOpt <- runProcessSyncF("docker", s"ps -q --filter ancestor=$image").map(_.trimAllToOpt)
        _               <- containerIdsOpt.fold(Future.successful(()))(removeContainers)
      } yield ()
    }
  }

  private def waitForContainerStopById(containerId: String): Future[String] =
    eventually(interval = ImageWaitDelay, timeout = ImageWaitTimeout) {
      runProcessSyncF("docker", s"ps -q --filter id=$containerId") filter (_.trimAllToOpt.isEmpty)
    }

  private def removeContainerById(containerId: String): Future[Unit] = {
    for {
      containerIdsOpt <- runProcessSyncF("docker", s"ps -q --filter id=$containerId").map(_.trimAllToOpt)
      _               <- containerIdsOpt.fold(Future.successful(()))(
                           ids => runProcessSyncF("docker", s"rm -f $containerId").void
                         )
    } yield ()
  }

  def removeContainerByIdAndWait(containerId: String): Future[Unit] = {
    withInfo(s"removing container by id `$containerId`") {
      for {
        _ <- removeContainerById(containerId)
        _ <- waitForContainerStopById(containerId)
      } yield ()
    }
  }

  private def findNetworkIdF: Future[Option[String]] =
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