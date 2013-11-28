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

import org.orbeon.oxf.xforms.XFormsConstants._
import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.util.{NetUtils, Multipart}
import org.orbeon.oxf.util.ScalaUtils._
import org.dom4j.Element
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.xforms.event.events.XXFormsBindingErrorEvent
import org.orbeon.oxf.xforms.control.XFormsControl.{ImmutableControlProperty, MutableControlProperty, ControlProperty}
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xforms.control.{XFormsValueControl, AjaxSupport, XFormsControl}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.control.controls.FileMetadata._
import org.apache.commons.io.FileUtils
import scala.util.control.NonFatal

// This trait is used by controls that support nested file metadata such as "filename"
trait FileMetadata extends XFormsValueControl {

    self: XFormsControl ⇒

    // Children elements
    // TODO: Don't deal with elements here, this should be part of the ElementAnalysis
    private val mediatypeElement = Option(self.element.element(XFORMS_MEDIATYPE_QNAME))
    private val filenameElement  = Option(self.element.element(XFORMS_FILENAME_QNAME))
    private val sizeElement      = Option(self.element.element(XXFORMS_SIZE_QNAME))

    private class FileMetadataProperty(evaluator: Evaluator) extends MutableControlProperty[String] {

        protected def evaluateValue() = evaluator.evaluate(self)
        override protected def nonRelevantValue = evaluator.default

        protected def isRelevant = self.isRelevant
        protected def wasRelevant = self.wasRelevant

        // No dependencies yet
        protected def requireUpdate = true
        protected def notifyCompute() = ()
        protected def notifyOptimized() = ()
    }

    // Supported file metadata properties
    private var props: Map[String, ControlProperty[String]] =
        supportedFileMetadata map (name ⇒ name → new FileMetadataProperty(Evaluators(name))) toMap

    // Properties to support
    def supportedFileMetadata: Seq[String]

    // Evaluate all properties
    def evaluateFileMetadata(relevant: Boolean) =
        props.values foreach (_.value)

    // Mark all properties dirty
    def markFileMetadataDirty() =
        props.values foreach (_.handleMarkDirty())

    // Getters
    def state                 = props("state")           .value
    def fileMediatype         = Option(props("mediatype").value)
    def filename              = Option(props("filename") .value)
    def fileSize              = Option(props("size")     .value)

    def humanReadableFileSize = fileSize filter StringUtils.isNotBlank map humanReadableBytes

    // "Instant" evaluators which go straight to the bound nodes if possible
    def boundFileMediatype  = Evaluators("mediatype").evaluate(self)
    def boundFilename       = Evaluators("filename").evaluate(self)

    // Setters
    def setFileMediatype(mediatype: String): Unit =
        setInfoValue(mediatypeElement, mediatype)

    def setFilename(filename: String): Unit = {

        // Depending on web browsers, the filename may contain a path or not (sending the path is fairly insecure and a
        // bad idea but some browsers do it. For consistency and security we just keep the filename.
        val justFileName = split(filename, """\/""").lastOption getOrElse ""
        setInfoValue(filenameElement, justFileName)
    }

    def setFileSize(size: String): Unit =
        setInfoValue(sizeElement, size)
    
    def addFileMetadataAttributes(attributesImpl: AttributesImpl, isNewRepeatIteration: Boolean, other: FileMetadata): Boolean = {
        val uploadControl1 = other
        val uploadControl2 = self

        var added: Boolean = false

        def addAtt(name: String, getValue: FileMetadata ⇒ String): Unit = {
            val value1 = Option(uploadControl1) map getValue orNull
            val value2 = getValue(uploadControl2)

            if (value1 != value2) {
                val attributeValue = Option(value2) getOrElse ""
                added |= AjaxSupport.addAttributeIfNeeded(attributesImpl, name, attributeValue, isNewRepeatIteration, attributeValue == "")
            }
        }

        // Add attributes for each property with a different value
        props foreach {
            case (name @ "size", _) ⇒ addAtt(name, _.humanReadableFileSize.orNull) // special case size so we can format
            case (name, _)          ⇒ addAtt(name, _.props(name).value)
        }

        added
    }

    // True if all metadata is the same (NOTE: the names must match)
    def compareFileMetadata(other: FileMetadata) =
        props.size == other.props.size && (props forall { case (name, prop) ⇒ prop.value == other.props(name).value })

    // Update other with an immutable version of the metadata
    def updateFileMetadataCopy(other: FileMetadata) =
        other.props = props map { case (name, prop) ⇒ name → new ImmutableControlProperty(prop.value) }

    private def setInfoValue(element: Option[Element], value: String) =
        if (value ne null)
            element foreach { e ⇒
                val contextStack = self.getContextStack
                contextStack.setBinding(self.bindingContext)
                contextStack.pushBinding(e, self.getEffectiveId, self.getChildElementScope(e))

                contextStack.getCurrentBindingContext.getSingleItem match {
                    case currentSingleItem: NodeInfo ⇒
                        DataModel.setValueIfChanged(
                            currentSingleItem,
                            value,
                            oldValue ⇒ DataModel.logAndNotifyValueChange(self.container.getContainingDocument, "file metadata", currentSingleItem, oldValue, value, isCalculate = false),
                            reason ⇒ Dispatch.dispatchEvent(new XXFormsBindingErrorEvent(self, self.getLocationData, reason))
                        )
                    case _ ⇒
                }
            }
}

object FileMetadata {

    case class Evaluator(evaluate: FileMetadata ⇒ String, default: String)

    // How to evaluate each property and default values used when control is non-relevant
    private val Evaluators = Map[String, Evaluator](
        "state"              → Evaluator(m ⇒ if (StringUtils.isBlank(m.getValue)) "empty" else "file", "empty"),
        "mediatype"          → Evaluator(m ⇒ m.mediatypeElement map (childMetadataValue(m, _)) orNull, null),
        "filename"           → Evaluator(m ⇒ m.filenameElement  map (childMetadataValue(m, _)) orNull, null),
        "size"               → Evaluator(m ⇒ m.sizeElement      map (childMetadataValue(m, _)) orNull, null),
        "progress-state"     → Evaluator(m ⇒ progress(m) map (_.state.toString.toLowerCase) orNull, null),
        "progress-received"  → Evaluator(m ⇒ progress(m) map (_.receivedSize.toString) orNull, null),
        "progress-expected"  → Evaluator(m ⇒ progress(m) flatMap (_.expectedSize) map (_.toString) orNull, null)
    )

    // All possible property names
    val AllMetadataNames: Seq[String] = Evaluators.keys.toList

    def progress(metadata: FileMetadata) = {
        val option = Multipart.getUploadProgress(NetUtils.getExternalContext.getRequest, metadata.containingDocument.getUUID, metadata.getEffectiveId)
        option filter (_.fieldName == metadata.getEffectiveId)
    }

    private def childMetadataValue(m: FileMetadata, element: Element) = {
        val contextStack = m.getContextStack
        contextStack.setBinding(m.bindingContext)
        contextStack.pushBinding(element, m.getEffectiveId, m.getChildElementScope(element))
        DataModel.getValue(contextStack.getCurrentBindingContext.getSingleItem)
    }

    // Format a string containing a number of bytes to a human-readable string
    // If the input string doesn't represent a Long, return the string unchanged
    def humanReadableBytes(size: String) =
        try FileUtils.byteCountToDisplaySize(size.toLong)
        catch { case NonFatal(_) ⇒ size }
}