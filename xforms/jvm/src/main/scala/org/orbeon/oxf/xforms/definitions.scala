/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}
import org.orbeon.dom
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.xforms.submission.UrlType

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

trait XFormsObject {
  def getEffectiveId: String
  def containingDocument: XFormsContainingDocument
}

// Lifecycle of an XForms document from the point of view of requests/responses
trait XFormsDocumentLifecycle {
  def afterInitialResponse(): Unit
  def beforeExternalEvents(response: ExternalContext.Response, isAjaxRequest: Boolean): Unit
  def afterExternalEvents(isAjaxRequest: Boolean): Unit
  def afterUpdateResponse(): Unit
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
  eventName         : String,
  targetEffectiveId : String,
  bubbles           : Boolean,
  cancelable        : Boolean,
  time              : Option[Long],
  showProgress      : Boolean,       // whether to show the progress indicator when submitting the event
  browserTarget     : Option[String] // optional browser target for submit events
)