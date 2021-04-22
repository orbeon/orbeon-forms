/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event.events

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.control.controls.{FileMetadata, XFormsUploadControl}
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.model.DataModel.Reason
import org.orbeon.xforms.EventNames
import shapeless.syntax.typeable._

import scala.collection.compat._

class XXFormsStateRestoredEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_STATE_RESTORED, target, properties, bubbles = false, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsBindingErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_BINDING_ERROR, target, properties, bubbles = true, cancelable = false) {

  def this(target: XFormsEventTarget, locationData: LocationData, reason: Reason) = {
    this(target, Map("message" -> Option(reason.message)))
    _locationData = Option(locationData)
    _reason = Option(reason)
  }

  private var _locationData: Option[LocationData] = None
  override def locationData = _locationData.orNull

  private var _reason: Option[Reason] = None
  def reason = _reason.orNull
}

class XXFormsXPathErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_XPATH_ERROR, target, properties, bubbles = true, cancelable = true) {

  def this(target: XFormsEventTarget, message: String, throwable: Throwable) = {
    // MAYBE: "throwable" -> OrbeonFormatter.format(throwable)
    this(target, Map("message" -> Option(message)))
    _throwable = Option(throwable)
  }

  private var _throwable: Option[Throwable] = None
  def throwable = _throwable.orNull
}

trait LinkEvent extends XFormsEvent {
  def resourceURI = property[String]("resource-uri").get
  def throwable: Throwable
}

class XFormsLinkErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_LINK_ERROR, target, properties, bubbles = true, cancelable = false)
  with LinkEvent {

  def this(target: XFormsEventTarget, url: String, throwable: Throwable) = {
    // MAYBE: "throwable" -> OrbeonFormatter.format(throwable)
    this(target, Map("resource-uri" -> Option(url)))
    _throwable = Option(throwable)
  }

  private var _throwable: Option[Throwable] = None
  def throwable = _throwable.orNull
}

class XFormsLinkExceptionEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_LINK_EXCEPTION, target, properties, bubbles = true, cancelable = false)
  with LinkEvent {

  def this(target: XFormsEventTarget, url: String, throwable: Throwable) = {
    // MAYBE: "throwable" -> OrbeonFormatter.format(throwable)
    this(target, Map("resource-uri" -> Option(url)))
    _throwable = Option(throwable)
  }

  private var _throwable: Option[Throwable] = None
  def throwable = _throwable.orNull
}

class XXFormsValueEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(EventNames.XXFormsValue, target, properties, bubbles = false, cancelable = false) {

  def this(target: XFormsEventTarget, value: String) =
    this(target, Map("value" -> Option(value)))

  def value = property[String]("value").get
}

class XXFormsLoadEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_LOAD, target, properties, bubbles = false, cancelable = false) {

  def this(target: XFormsEventTarget, resource: String) =
    this(target, Map("resource" -> Option(resource)))

  def resource = property[String]("resource").get
}

object XXFormsLoadEvent {
  val StandardProperties = Map(XXFORMS_LOAD -> List("resource"))
}

// NOTE: Event default behavior done at target so event is left cancelable.
class XXFormsUploadStartEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(EventNames.XXFormsUploadStart, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

// NOTE: Event default behavior done at target so event is left cancelable.
class XXFormsUploadProgressEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(EventNames.XXFormsUploadProgress, target, properties, bubbles = false, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

// NOTE: Event default behavior done at target so event is left cancelable.
class XXFormsUploadCancelEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(EventNames.XXFormsUploadCancel, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

// NOTE: Event default behavior done at target so event is left cancelable.
class XXFormsUploadDoneEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(EventNames.XXFormsUploadDone, target, properties, bubbles = true, cancelable = true) {

  // These properties come from the client
  def file          = property[String]("file").get
  def filename      = property[String]("filename").get
  def contentType   = property[String](Headers.ContentTypeLower).get
  def contentLength = property[String](Headers.ContentLengthLower).get // comes as String from the client
}

object XXFormsUploadDoneEvent {
  val StandardProperties = Map(
    EventNames.XXFormsUploadDone -> List("file", "filename", Headers.ContentTypeLower, Headers.ContentLengthLower)
  )
}

// NOTE: Event default behavior done at target so event is left cancelable.
class XXFormsUploadErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(
    name         = EventNames.XXFormsUploadError,
    targetObject = target,
    properties   = XXFormsUploadErrorEvent.reasonToProperties(target).toMap orElse properties,
    bubbles      = true,
    cancelable   = true
  )

object XXFormsUploadErrorEvent {
  // Attempt to retrieve a reason if any
  def reasonToProperties(target: XFormsEventTarget): List[(String, Option[Any])] =
    target.cast[XFormsUploadControl].to(List) flatMap
      FileMetadata.progress                   flatMap {
      case UploadProgress(_, _, _, UploadState.Interrupted(Some(Reason.SizeReason(permitted, actual)))) =>
        List(
          "error-type" -> Some("size-error"),
          "permitted"  -> Some(permitted),
          "actual"     -> Some(actual)
        )
      case UploadProgress(_, _, _, UploadState.Interrupted(Some(Reason.MediatypeReason(permitted, actual)))) =>
        List(
          "error-type" -> Some("mediatype-error"),
          "permitted"  -> Some(permitted.to(List) map (_.toString)),
          "actual"     -> (actual map (_.toString))
        )
      case UploadProgress(_, _, _, UploadState.Interrupted(Some(Reason.FileScanReason(message)))) =>
        List(
          "error-type" -> Some("file-scan-error"),
          "message"    -> Some(message)
        )
      case _ =>
        List("error-type" -> Some("upload-error"))
    }
}

class XXFormsInstanceInvalidate(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_INSTANCE_INVALIDATE, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsModelConstructDoneEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_MODEL_CONSTRUCT_DONE, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsModelConstructEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_MODEL_CONSTRUCT, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget, rrr: Boolean) =
    this(target, Map("rrr" -> Option(rrr)))

  def rrr = propertyOrDefault[Boolean]("rrr", default = true)
}

class XXFormsInstancesReadyEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_INSTANCES_READY, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsModelDestructEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_MODEL_DESTRUCT, target, properties, bubbles = false, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsReadyEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_READY, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsRebuildEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_REBUILD, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsRefreshEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_REFRESH, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsResetEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_RESET, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsRevalidateEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_REVALIDATE, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsRecalculateEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_RECALCULATE, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsScrollFirstEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_SCROLL_FIRST, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsScrollLastEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_SCROLL_LAST, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsSubmitEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_SUBMIT, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsDialogCloseEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_DIALOG_CLOSE, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsInvalidEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_INVALID, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsReadyEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_READY, target, properties, bubbles = false, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsValidEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_VALID, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsIterationMovedEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_ITERATION_MOVED, target, properties, bubbles = false, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsRepeatActivateEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_REPEAT_ACTIVATE, target, properties, bubbles = false, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsRefreshDoneEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_REFRESH_DONE, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}