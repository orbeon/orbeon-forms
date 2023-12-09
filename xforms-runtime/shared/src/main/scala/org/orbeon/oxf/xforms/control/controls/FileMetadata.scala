/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, FileUtils, IndentedLogger, UploadProgress}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.SingleItemBinding
import org.orbeon.oxf.xforms.analysis.controls.WithFileMetadata
import org.orbeon.oxf.xforms.control.XFormsControl.{ControlProperty, ImmutableControlProperty, MutableControlProperty}
import org.orbeon.oxf.xforms.control.controls.FileMetadata._
import org.orbeon.oxf.xforms.control.{ControlAjaxSupport, XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.XXFormsBindingErrorEvent
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable.typeableOps

import scala.util.control.NonFatal


// This trait is used by controls that support nested file metadata such as "filename"
trait FileMetadata extends XFormsValueControl {

  self: XFormsControl =>

  private def staticFileMetadata: Option[WithFileMetadata] =
    self.staticControl.cast[WithFileMetadata]

  private class FileMetadataProperty(evaluator: Evaluator) extends MutableControlProperty[String] {

    protected def evaluateValue(collector: ErrorEventCollector): String = evaluator.evaluate(self, collector)
    override protected def nonRelevantValue: String = evaluator.default

    protected def isRelevant: Boolean = self.isRelevant
    protected def wasRelevant: Boolean = self.wasRelevant

    // No dependencies yet
    protected def requireUpdate = true
    protected def notifyCompute(): Unit = ()
    protected def notifyOptimized(): Unit = ()
  }

  // Supported file metadata properties
  private var props: Map[String, ControlProperty[String]] =
    supportedFileMetadata.map(name => name -> new FileMetadataProperty(Evaluators(name))).toMap

  // Properties to support
  def supportedFileMetadata: Seq[String]

  // Evaluate all properties
  def evaluateFileMetadata(relevant: Boolean, collector: ErrorEventCollector): Unit =
    props.values foreach (_.value(collector))

  // Mark all properties dirty
  def markFileMetadataDirty(): Unit =
    props.values foreach (_.handleMarkDirty())

  // Getters
  def state        (collector: ErrorEventCollector): String         = props("state")    .value(collector)
  def fileMediatype(collector: ErrorEventCollector): Option[String] = props("mediatype").value(collector).trimAllToOpt
  def filename     (collector: ErrorEventCollector): Option[String] = props("filename") .value(collector).trimAllToOpt
  def fileSize     (collector: ErrorEventCollector): Option[String] = props("size")     .value(collector).trimAllToOpt

  def iterateProperties(collector: ErrorEventCollector): Iterator[(String, Option[String])] = props.iterator map {
    case (k, v) => k -> Option(v.value(collector))
  }

  def humanReadableFileSize(collector: ErrorEventCollector): Option[String] =
    fileSize(collector) filter (_.nonAllBlank) map humanReadableBytes

  // "Instant" evaluators which go straight to the bound nodes if possible
  def boundFileMediatype(collector: ErrorEventCollector): String = Evaluators("mediatype").evaluate(self, collector)
  def boundFilename(collector: ErrorEventCollector): String = Evaluators("filename").evaluate(self, collector)

  // Setters for `XFormsUploadControl`
  def setFileMediatype(mediatype: String, collector: ErrorEventCollector): Unit =
    staticFileMetadata.flatMap(_.mediatypeBinding).foreach(setMetadataValue(self, _, mediatype, collector))

  def setFilename(filename: String, collector: ErrorEventCollector): Unit =
    staticFileMetadata.flatMap(_.filenameBinding).foreach(setMetadataValue(self, _, filename, collector))

  def setFileSize(size: String, collector: ErrorEventCollector): Unit =
    staticFileMetadata.flatMap(_.sizeBinding).foreach(setMetadataValue(self, _, size, collector))

  def addFileMetadataAttributes(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[FileMetadata],
    collector         : ErrorEventCollector
  ): Boolean = {

    val uploadControl2 = self

    var added: Boolean = false

    def addAtt(name: String, getValue: FileMetadata => String): Unit = {
      val value1 = previousControlOpt.map(getValue).orNull
      val value2 = getValue(uploadControl2)

      if (value1 != value2) {
        val attributeValue = if (value2 eq null) "" else value2
        added |= ControlAjaxSupport.addAttributeIfNeeded(attributesImpl, name, attributeValue, previousControlOpt.isEmpty, attributeValue == "")
      }
    }

    // Add attributes for each property with a different value
    props foreach {
      case (name @ "size", _) => addAtt(name, _.humanReadableFileSize(collector).orNull) // special case size so we can format
      case (name, _)          => addAtt(name, _.props(name).value(collector))
    }

    added
  }

  // True if all metadata is the same (NOTE: the names must match)
  def compareFileMetadata(other: FileMetadata, collector: ErrorEventCollector): Boolean =
    props.size == other.props.size && (props forall { case (name, prop) => prop.value(collector) == other.props(name).value(collector) })

  // Update other with an immutable version of the metadata
  def updateFileMetadataCopy(other: FileMetadata, collector: ErrorEventCollector): Unit =
    other.props = props map { case (name, prop) => name -> new ImmutableControlProperty(prop.value(collector)) }
}

object FileMetadata {

  case class Evaluator(evaluate: (FileMetadata, ErrorEventCollector) => String, default: String)

  // How to evaluate each property and default values used when control is non-relevant
  private val Evaluators = Map[String, Evaluator](
    "state"             -> Evaluator((m, c) => if (m.getValue(c).isAllBlank) "empty" else "file", "empty"),

    "mediatype"         -> Evaluator((m, c) => m.staticFileMetadata.flatMap(_.mediatypeBinding).map(getMetadataValue(m, _, c)).orNull, null),
    "filename"          -> Evaluator((m, c) => m.staticFileMetadata.flatMap(_.filenameBinding) .map(getMetadataValue(m, _, c)).orNull, null),
    "size"              -> Evaluator((m, c) => m.staticFileMetadata.flatMap(_.sizeBinding)     .map(getMetadataValue(m, _, c)).orNull, null),

    "progress-state"    -> Evaluator((m, _) => progress(m).map    (_.state.name)                  .orNull, null),
    "progress-received" -> Evaluator((m, _) => progress(m).map    (_.receivedSize.toString)       .orNull, null),
    "progress-expected" -> Evaluator((m, _) => progress(m).flatMap(_.expectedSize).map(_.toString).orNull, null)
  )

  // All possible property names
  val AllMetadataNames: Seq[String] = Evaluators.keys.toList

  // Find the progress information in the session
  def progress(metadata: FileMetadata): Option[UploadProgress[CoreCrossPlatformSupport.FileItemType]] =
    XFormsCrossPlatformSupport.getUploadProgress(
      XFormsCrossPlatformSupport.externalContext.getRequest,
      metadata.containingDocument.uuid,
      metadata.getEffectiveId
    ) filter
      (_.fieldName == metadata.getEffectiveId)

  private def getMetadataValue(
    m                : FileMetadata,
    singleItemBinding: SingleItemBinding,
    collector        : ErrorEventCollector
  ): String = {
    val contextStack = m.getContextStack
    contextStack.setBinding(m.bindingContext)
    contextStack.pushBinding(singleItemBinding, m.getEffectiveId, m, collector)
    DataModel.getValue(contextStack.getCurrentBindingContext.getSingleItemOrNull)
  }

  private def setMetadataValue(
    m                : FileMetadata,
    singleItemBinding: SingleItemBinding,
    value            : String,
    collector        : ErrorEventCollector
  )(implicit
    containingDocument: XFormsContainingDocument,
    logger            : IndentedLogger
  ): Unit = {

    val contextStack = m.getContextStack
    contextStack.setBinding(m.bindingContext)
    contextStack.pushBinding(singleItemBinding, m.getEffectiveId, m, collector)

    contextStack.getCurrentBindingContext.singleNodeOpt foreach { currentSingleNode =>
      DataModel.setValueIfChanged(
        nodeInfo  = currentSingleNode,
        newValue  = value,
        onSuccess = oldValue => DataModel.logAndNotifyValueChange(
          source             = "file metadata",
          nodeInfo           = currentSingleNode,
          oldValue           = oldValue,
          newValue           = value,
          isCalculate        = false,
          collector          = (event: XFormsEvent) => Dispatch.dispatchEvent(event, collector)
        ),
        reason => Dispatch.dispatchEvent(new XXFormsBindingErrorEvent(m, Option(m.getLocationData), reason), collector)
      )
    }
  }

  // Format a string containing a number of bytes to a human-readable string
  // If the input string doesn't represent a `Long`, return the string unchanged
  def humanReadableBytes(size: String): String =
    try FileUtils.byteCountToDisplaySize(size.toLong)
    catch { case NonFatal(_) => size }
}