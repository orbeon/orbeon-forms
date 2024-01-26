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

import org.apache.commons.fileupload.disk.DiskFileItem
import org.orbeon.dom.QName
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.properties.{Properties, PropertySet}

import scala.concurrent.ExecutionContext


object CoreCrossPlatformSupport extends CoreCrossPlatformSupportTrait {

  type FileItemType = DiskFileItem

  implicit def executionContext: ExecutionContext = ExecutionContext.global

  def isPE: Boolean = Version.isPE
  def isJsEnv: Boolean = false
  def randomHexId: String = SecureUtils.randomHexId
  def getApplicationResourceVersion: Option[String] = Option(URLRewriterUtils.getApplicationResourceVersion)
  def properties: PropertySet = Properties.instance.getPropertySet
  def getPropertySet(processorName: QName): PropertySet = Properties.instance.getPropertySet(processorName)
  def externalContext: ExternalContext = NetUtils.getExternalContext

  def withExternalContext[T](ec: ExternalContext)(body: => T): T =
    InitUtils.withPipelineContext { pipelineContext =>
      pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, ec)
      body
    }
}
