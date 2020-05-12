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

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import org.orbeon.dom
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.submission.UrlType
import org.orbeon.oxf.xml.{XMLReceiver, XMLReceiverSupport}
import org.orbeon.xforms.rpc

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
  def beforeExternalEvents(response: ExternalContext.Response): Unit
  def afterExternalEvents(): Unit
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
  time              : Long,
  discardable       : Boolean, // whether the client can discard the event past the delay (see AjaxServer.js)
  showProgress      : Boolean  // whether to show the progress indicator when submitting the event
) {

  private def asEncodedDocument: String = {

    import org.orbeon.oxf.xml.Dom4j._

    XFormsUtils.encodeXML(
      <xxf:events xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
        <xxf:event
          name={eventName}
          source-control-id={targetEffectiveId}
          bubbles={bubbles.toString}
          cancelable={cancelable.toString}/>
      </xxf:events>,
      false
    )
  }

  def writeAsSAX(currentTime: Long)(implicit receiver: XMLReceiver): Unit = {

    import XMLReceiverSupport._

    element(
      localName = XXFORMS_SERVER_EVENTS_QNAME.localName,
      prefix    = XXFORMS_SHORT_PREFIX,
      uri       = XXFORMS_NAMESPACE_URI,
      atts      = List(
        "delay"         -> (time - currentTime).toString,
        "discardable"   -> discardable.toString,
        "show-progress" -> showProgress.toString
      ),
      text      = asEncodedDocument
    )
  }

  def toServerEvent(currentTime: Long): rpc.ServerEvent =
    rpc.ServerEvent(
      delay        = time - currentTime,
      discardable  = discardable,
      showProgress = showProgress,
      encodedEvent = asEncodedDocument
    )
}
