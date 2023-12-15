/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import cats.effect.IO
import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, VirtualNodeType}
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.oxf.xforms.model.XFormsInstance._


/**
 * Cache for shared and immutable XForms instances.
 */
trait XFormsServerSharedInstancesCacheTrait {

  type InstanceLoader = (String, Boolean) => DocumentNodeInfoType

  protected case class InstanceContent(documentInfo: DocumentNodeInfoType) {
    require(! documentInfo.isInstanceOf[VirtualNodeType], "`InstanceContent` must return a `TinyTree`")
  }

  import Private._

  def findContent(
    instanceCaching  : InstanceCaching,
    readonly         : Boolean,
    exposeXPathTypes : Boolean)(implicit
    indentedLogger   : IndentedLogger
  ): Option[DocumentNodeInfoType] =
    find(instanceCaching)
      .map(wrapDocumentInfoIfNeeded(_, readonly, exposeXPathTypes))

  def findContentOrLoad(
    instanceCaching  : InstanceCaching,
    readonly         : Boolean,
    exposeXPathTypes : Boolean,
    loadInstance     : InstanceLoader
  )(implicit
    indentedLogger   : IndentedLogger
  ): DocumentNodeInfoType =
    wrapDocumentInfoIfNeeded(
      find(instanceCaching).getOrElse(loadAndCache(instanceCaching, loadInstance)),
      readonly,
      exposeXPathTypes
    )

  def findContentOrLoadAsync(
    instanceCaching  : InstanceCaching,
    readonly         : Boolean,
    exposeXPathTypes : Boolean,
    loadInstance     : InstanceLoader
  )(implicit
    indentedLogger   : IndentedLogger
  ): IO[DocumentNodeInfoType] =
    find(instanceCaching)
      .map(IO.pure)
      .getOrElse(IO(loadAndCache(instanceCaching, loadInstance)))
      .map(wrapDocumentInfoIfNeeded(_, readonly, exposeXPathTypes))

  protected def add(
    instanceCaching: InstanceCaching,
    instanceContent: InstanceContent,
    timeToLive     : Long
  )(implicit
    indentedLogger : IndentedLogger
  ): Unit

  protected def find(
    instanceCaching: InstanceCaching
  )(implicit
    logger: IndentedLogger
  ): Option[DocumentNodeInfoType]

  def remove(
    instanceSourceURI : String,
    requestBodyHash   : Option[String],
    handleXInclude    : Boolean,
    ignoreQueryString : Boolean
  )(implicit
    indentedLogger    : IndentedLogger
  ): Unit

  def removeAll(implicit indentedLogger: IndentedLogger): Unit

  private object Private {

    def loadAndCache(
      instanceCaching: InstanceCaching,
      loadInstance   : InstanceLoader
    )(implicit
      indentedLogger : IndentedLogger
    ): DocumentNodeInfoType = {
      // Note that this method is not synchronized. Scenario: if the method is synchronized, the resource URI may
      // reach an XForms page which itself needs to load a shared resource. The result would be a deadlock.
      // Without synchronization, what can happen is that two concurrent requests load the same URI at the same
      // time. In the worst case scenario, the results will be different, and the two requesting XForms instances
      // will be different. The instance that is retrieved first will be stored in the cache for a very short
      // amount of time, and the one retrieved last will win and be stored in the cache for a longer time.
      debug("loading instance into cache", instanceCaching.debugPairs)

      loadInstance(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude) |!>
        (documentInfo => add(instanceCaching, InstanceContent(documentInfo), instanceCaching.timeToLive))
    }
  }
}