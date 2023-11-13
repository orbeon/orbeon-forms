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
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XXFormsBindingErrorEvent
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable.typeableOps

import scala.util.control.NonFatal


// This trait is used by controls that support nested file metadata such as "filename"
trait FileMetadata extends XFormsValueControl {

  self: XFormsControl =>

  def staticFileMetadata: Option[WithFileMetadata] =
    self.staticControl.cast[WithFileMetadata]

  private class FileMetadataProperty(evaluator: Evaluator) extends MutableControlProperty[String] {

    protected def evaluateValue(): String = evaluator.evaluate(self)
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
    supportedFileMetadata map (name => name -> new FileMetadataProperty(Evaluators(name))) toMap

  // Properties to support
  def supportedFileMetadata: Seq[String]

  // Evaluate all properties
  def evaluateFileMetadata(relevant: Boolean): Unit =
    props.values foreach (_.value)

  // Mark all properties dirty
  def markFileMetadataDirty(): Unit =
    props.values foreach (_.handleMarkDirty())

  // Getters
  def state        : String         = props("state")    .value
  def fileMediatype: Option[String] = props("mediatype").value.trimAllToOpt
  def filename     : Option[String] = props("filename") .value.trimAllToOpt
  def fileSize     : Option[String] = props("size")     .value.trimAllToOpt

  def iterateProperties: Iterator[(String, Option[String])] = props.iterator map {
    case (k, v) => k -> Option(v.value)
  }

  def humanReadableFileSize: Option[String] =
    fileSize filter (_.nonAllBlank) map humanReadableBytes

  // "Instant" evaluators which go straight to the bound nodes if possible
  def boundFileMediatype: String = Evaluators("mediatype").evaluate(self)
  def boundFilename: String = Evaluators("filename").evaluate(self)

  // Setters
  def setFileMediatype(mediatype: String): Unit =
    staticFileMetadata.flatMap(_.mediatypeBinding).foreach(setMetadataValue(self, _, mediatype))

  def setFilename(filename: String): Unit =
    staticFileMetadata.flatMap(_.filenameBinding).foreach(setMetadataValue(self, _, filename))

  def setFileSize(size: String): Unit =
    staticFileMetadata.flatMap(_.sizeBinding).foreach(setMetadataValue(self, _, size))

  def addFileMetadataAttributes(attributesImpl: AttributesImpl, previousControlOpt: Option[FileMetadata]): Boolean = {

    val uploadControl2 = self

    var added: Boolean = false

    def addAtt(name: String, getValue: FileMetadata => String): Unit = {
      val value1 = previousControlOpt map getValue orNull
      val value2 = getValue(uploadControl2)

      if (value1 != value2) {
        val attributeValue = if (value2 eq null) "" else value2
        added |= ControlAjaxSupport.addAttributeIfNeeded(attributesImpl, name, attributeValue, previousControlOpt.isEmpty, attributeValue == "")
      }
    }

    // Add attributes for each property with a different value
    props foreach {
      case (name @ "size", _) => addAtt(name, _.humanReadableFileSize.orNull) // special case size so we can format
      case (name, _)          => addAtt(name, _.props(name).value)
    }

    added
  }

  // True if all metadata is the same (NOTE: the names must match)
  def compareFileMetadata(other: FileMetadata): Boolean =
    props.size == other.props.size && (props forall { case (name, prop) => prop.value == other.props(name).value })

  // Update other with an immutable version of the metadata
  def updateFileMetadataCopy(other: FileMetadata): Unit =
    other.props = props map { case (name, prop) => name -> new ImmutableControlProperty(prop.value) }
}

object FileMetadata {

  case class Evaluator(evaluate: FileMetadata => String, default: String)

  // How to evaluate each property and default values used when control is non-relevant
  private val Evaluators = Map[String, Evaluator](
    "state"             -> Evaluator(m => if (m.getValue.isAllBlank) "empty" else "file", "empty"),

    "mediatype"         -> Evaluator(m => m.staticFileMetadata.flatMap(_.mediatypeBinding).map(getMetadataValue(m, _)).orNull, null),
    "filename"          -> Evaluator(m => m.staticFileMetadata.flatMap(_.filenameBinding) .map(getMetadataValue(m, _)).orNull, null),
    "size"              -> Evaluator(m => m.staticFileMetadata.flatMap(_.sizeBinding)     .map(getMetadataValue(m, _)).orNull, null),

    "progress-state"    -> Evaluator(m => progress(m)        map     (_.state.name)                    orNull, null),
    "progress-received" -> Evaluator(m => progress(m)        map     (_.receivedSize.toString)         orNull, null),
    "progress-expected" -> Evaluator(m => progress(m)        flatMap (_.expectedSize) map (_.toString) orNull, null)
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
    singleItemBinding: SingleItemBinding
  ): String = {
    val contextStack = m.getContextStack
    contextStack.setBinding(m.bindingContext)
    contextStack.pushBinding(singleItemBinding, m.getEffectiveId)
    DataModel.getValue(contextStack.getCurrentBindingContext.getSingleItemOrNull)
  }

  private def setMetadataValue(
    m                : FileMetadata,
    singleItemBinding: SingleItemBinding,
    value            : String
  )(implicit
    containingDocument: XFormsContainingDocument,
    logger            : IndentedLogger
  ): Unit = {

    println(s"xxx setInfoValue: $singleItemBinding, $value")

    val contextStack = m.getContextStack
    contextStack.setBinding(m.bindingContext)
    contextStack.pushBinding(singleItemBinding, m.getEffectiveId)

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
          collector          = Dispatch.dispatchEvent
        ),
        reason => Dispatch.dispatchEvent(new XXFormsBindingErrorEvent(m, m.getLocationData, reason))
      )
    }
  }

  // Format a string containing a number of bytes to a human-readable string
  // If the input string doesn't represent a `Long`, return the string unchanged
  def humanReadableBytes(size: String): String =
    try FileUtils.byteCountToDisplaySize(size.toLong)
    catch { case NonFatal(_) => size }
}