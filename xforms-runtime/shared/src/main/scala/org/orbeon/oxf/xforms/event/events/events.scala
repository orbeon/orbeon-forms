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
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.analysis.XPathErrorDetails
import org.orbeon.oxf.xforms.control.controls.{FileMetadata, XFormsUploadControl}
import org.orbeon.oxf.xforms.event.XFormsEvent.*
import org.orbeon.oxf.xforms.event.XFormsEvents.*
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}
import org.orbeon.xforms.{BindingErrorReason, EventNames}
import shapeless.syntax.typeable.*


class XXFormsStateRestoredEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_STATE_RESTORED, target, properties, bubbles = false, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsBindingErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_BINDING_ERROR, target, properties, bubbles = true, cancelable = true) {

  def this(target: XFormsEventTarget, locationDataOpt: Option[LocationData], reason: BindingErrorReason) = {
    this(target, Map("message" -> Option(reason.message)))
    _locationData = locationDataOpt
    _reasonOpt = Option(reason)
  }

  private var _locationData: Option[LocationData] = None
  override def locationData: LocationData = _locationData.orNull

  private var _reasonOpt: Option[BindingErrorReason] = None
  def reasonOpt: Option[BindingErrorReason] = _reasonOpt
}

class XXFormsXPathErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_XPATH_ERROR, target, properties, bubbles = true, cancelable = true) {

  def this(
    target    : XFormsEventTarget,
    expression: String,
    details   : XPathErrorDetails,
    message   : String,
    throwable : Throwable
  ) = {
    // MAYBE: "throwable" -> OrbeonFormatter.format(throwable)
    this(
      target,
      Map(
        "expression" -> Option(expression),
        "message"    -> Option(message)
      )
    )
    _detailsOpt   = Option(details)
    _throwableOpt = Option(throwable)
  }

  // Not stored as properties because properties don't support reading regular Java objects yet
  private var _detailsOpt: Option[XPathErrorDetails] = None
  private var _throwableOpt: Option[Throwable] = None

  def detailsOpt  : Option[XPathErrorDetails] = _detailsOpt
  def throwableOpt: Option[Throwable] = _throwableOpt

  def expressionOpt: Option[String] = property[String]("expression")
  def messageOpt   : Option[String] = property[String]("message")

  def combinedMessage: String = {
    val details    = detailsOpt.map(XPathErrorDetails.message).getOrElse("[unknown]")
    val message    = messageOpt     getOrElse "[unknown]"
    val expression = expressionOpt  getOrElse "[unknown]"
    s"XPath error `$message` while evaluating expression `$expression` ($details)"
  }
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

  def value: String = property[String]("value").get
}

class XXFormsLoadEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_LOAD, target, properties, bubbles = false, cancelable = false) {

  def this(target: XFormsEventTarget, resource: String) =
    this(target, Map("resource" -> Option(resource)))

  def resource: String = property[String]("resource").get
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

  def filename      = property[String]("filename").get
  def contentType   = property[String](Headers.ContentTypeLower).get
  def contentLength = property[String](Headers.ContentLengthLower).get // comes as String from the client
}

class XXFormsUploadStoreEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(EventNames.XXFormsUploadStore, target, properties, bubbles = true, cancelable = true) {

  // These properties come from the client
  def file          = property[String]("file").get
  def filename      = property[String]("filename").get
  def contentType   = property[String](Headers.ContentTypeLower).get
  def contentLength = property[String](Headers.ContentLengthLower).get // comes as String from the client
}

object XXFormsUploadStoreEvent {
  val StandardProperties = Map(
    EventNames.XXFormsUploadStore -> List("file", "filename", Headers.ContentTypeLower, Headers.ContentLengthLower)
  )
}

// NOTE: Event default behavior done at target so event is left cancelable.
class XXFormsUploadErrorEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(
    name         = EventNames.XXFormsUploadError,
    targetObject = target,
    properties   = properties orElse XXFormsUploadErrorEvent.reasonToProperties(target).toMap, // allow overriding standard properties
    bubbles      = true,
    cancelable   = true
  )

object XXFormsUploadErrorEvent {
  // Attempt to retrieve a reason if any
  def reasonToProperties(target: XFormsEventTarget): List[(String, Option[Any])] =
    target.cast[XFormsUploadControl].to(List) flatMap
      FileMetadata.progress                   flatMap {
      case UploadProgress(_, _, _, UploadState.Interrupted(Some(FileRejectionReason.EmptyFile))) =>
        List("error-type" -> Some("empty-file-error"))
      case UploadProgress(_, _, _, UploadState.Interrupted(Some(FileRejectionReason.SizeTooLarge(permitted, actual)))) =>
        List(
          "error-type" -> Some("size-error"),
          "permitted"  -> Some(ByteSizeUtils.byteCountToDisplaySize(permitted)),
          "actual"     -> Some(ByteSizeUtils.byteCountToDisplaySize(actual))
        )
      case UploadProgress(_, _, _, UploadState.Interrupted(Some(FileRejectionReason.DisallowedMediatype(clientFilenameOpt, permitted, actual)))) =>
        List(
          "error-type" -> Some("mediatype-error"),
          "filename"   -> clientFilenameOpt,
          "permitted"  -> Some(permitted.to(List) map (_.toString)),
          "actual"     -> (actual map (_.toString))
        )
      case UploadProgress(_, _, _, UploadState.Interrupted(Some(FileRejectionReason.FailedFileScan(_, message)))) =>
        List(
          "error-type" -> Some("file-scan-error"),
          "message"    -> message
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

class XXFormsRebuildStartedEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_REBUILD_STARTED, target, properties, bubbles = true, cancelable = true) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsRecalculateStartedEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_RECALCULATE_STARTED, target, properties, bubbles = true, cancelable = true) {
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