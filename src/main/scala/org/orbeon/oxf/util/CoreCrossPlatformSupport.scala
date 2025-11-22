/**
 * Copyright (C) 2020 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import cats.effect.unsafe.{IORuntime, IORuntimeBuilder, IORuntimeConfig}
import org.apache.commons.fileupload.disk.DiskFileItem
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.properties.{PropertyLoader, PropertyStore}

import java.util.concurrent.ExecutorService
import javax.naming.InitialContext
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


object CoreCrossPlatformSupport extends CoreCrossPlatformSupportTrait {

  type FileItemType = DiskFileItem

  implicit def executionContext: ExecutionContext = runtime.compute

  private val DefaultJndiName = "java:comp/DefaultManagedExecutorService"
  private val PropertyName    = "oxf.managed-executor-service.jndi-name"

  // If we don't make this lazy, things don't work down the line for some reason!
  private lazy val _runtime: IORuntime = {

    val buffer = mutable.ListBuffer.empty[String]

    buffer += "IORuntime initialization:"

    def logTry[U](t: Try[U])(m: String): Try[U] = {
      t match {
        case Success(b) => buffer += s"- found $m: $b"
        case Failure(t) => buffer += s"- did not find $m: ${t.getMessage}"
      }
      t
    }

    def fromJndi: Try[IORuntime] =
      for {
        jndiName                       <- logTry(Try(CoreCrossPlatformSupport.properties.getString(PropertyName, DefaultJndiName)))("JNDI name")
        managedExecutorService         <- logTry(Try((new InitialContext).lookup(jndiName).asInstanceOf[ExecutorService]))("`ManagedExecutorService`")
        executionContext               = ExecutionContext.fromExecutorService(managedExecutorService)
        (scheduler, schedulerShutdown) = IORuntime.createDefaultScheduler()
      } yield
        IORuntime(
          executionContext,
          executionContext, // Q: unclear if we should pass different `ExecutionContext`s to `compute` and `blocking`
          scheduler,
          () => schedulerShutdown(),
          IORuntimeConfig()
        )

    fromJndi match {
      case Success(runtime) =>
        buffer += s"Using IORuntime from JNDI."
        logger.info(buffer.mkString("\n"))
        runtime
      case Failure(_) =>
        buffer += s"Using default IORuntime."
        logger.info(buffer.mkString("\n"))
        IORuntimeBuilder().build() // https://github.com/orbeon/orbeon-forms/issues/6089
    }
  }

  implicit def runtime: IORuntime = _runtime

  def logger: org.log4s.Logger = PropertyLoader.logger
  def isPE: Boolean = Version.isPE
  def isJsEnv: Boolean = false
  def randomHexId: String = SecureUtils.randomHexId
  def getApplicationResourceVersion: Option[String] = URLRewriterUtils.getApplicationResourceVersion
  def propertyStore: PropertyStore = PropertyLoader.getPropertyStore(requestOpt)

  def setExternalContext(ec: ExternalContext): Unit = externalContextDyn.value = ec
  def clearExternalContext()                 : Unit = externalContextDyn.clear()
}
