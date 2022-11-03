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
package org.orbeon.xforms

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.dom


sealed trait RelevanceHandling extends EnumEntry

object RelevanceHandling extends Enum[RelevanceHandling] {

  val values = findValues

  case object Keep   extends RelevanceHandling
  case object Remove extends RelevanceHandling
  case object Empty  extends RelevanceHandling
}


sealed trait  UrlType extends EnumEntry

object UrlType extends Enum[UrlType] {

  val values = findValues

  case object Action   extends UrlType
  case object Render   extends UrlType
  case object Resource extends UrlType
}

sealed trait XXBLScope extends EnumEntry with Lowercase
object XXBLScope extends Enum[XXBLScope] {

  val values = findValues

  case object Inner extends  XXBLScope
  case object Outer extends  XXBLScope
}

sealed trait DeploymentType extends EnumEntry with Lowercase
object DeploymentType extends Enum[DeploymentType] {

  val values = findValues

  case object Separate    extends  DeploymentType
  case object Integrated  extends  DeploymentType
  case object Standalone  extends  DeploymentType
}

// Lifecycle of an XForms document from the point of view of requests/responses
trait XFormsDocumentLifecycle[Response] {
  def afterInitialResponse(): Unit
  def beforeExternalEvents(response: Response, isAjaxRequest: Boolean): Unit
  def afterExternalEvents(isAjaxRequest: Boolean): Unit
  def afterUpdateResponse(): Unit

  final def withExternalEvents[T](response: Response, isAjaxRequest: Boolean)(block: => T): T = {
    beforeExternalEvents(response, isAjaxRequest)
    val result = block
    afterExternalEvents(isAjaxRequest)
    result
  }
}

case class ErrorInfo(
  element : dom.Element,
  message : String
)

case class Message(
  message : String,
  level   : String
)

case class Load(
  resource       : String,
  target         : Option[String],
  urlType        : UrlType,
  isReplace      : Boolean,
  isShowProgress : Boolean
) {
  def isJavaScript: Boolean = resource.trim.startsWith("javascript:")
}

case class DelayedEvent(
  eventName              : String,
  targetEffectiveId      : String,
  bubbles                : Boolean,
  cancelable             : Boolean,
  time                   : Option[Long],
  showProgress           : Boolean,        // whether to show the progress indicator when submitting the event
  browserTarget          : Option[String], // optional browser target for submit events
  isResponseResourceType : Boolean
)
