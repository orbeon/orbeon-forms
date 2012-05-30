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
import org.apache.commons.lang.StringUtils
import org.orbeon.oxf.util.{NetUtils, Multipart}
import org.dom4j.Element
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.xforms.event.events.XXFormsBindingErrorEvent
import org.orbeon.oxf.xforms.control.XFormsControl.{ImmutableControlProperty, MutableControlProperty, ControlProperty}
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xforms.control.{AjaxSupport, XFormsControl, XFormsValueControl}

// This trait is used by controls that support nested file metadata such as "filename"
trait FileMetadata extends XFormsValueControl {

    self: XFormsControl ⇒

    // Children elements
    private val mediatypeElement = Option(self.element.element(XFORMS_MEDIATYPE_QNAME))
    private val filenameElement  = Option(self.element.element(XFORMS_FILENAME_QNAME))
    private val sizeElement      = Option(self.element.element(XXFORMS_SIZE_QNAME))

    private class FileMetadataProperty(evaluate: FileMetadata ⇒ String) extends MutableControlProperty[String] {

        protected def evaluateValue() = evaluate(self)

        protected def isRelevant = self.isRelevant
        protected def wasRelevant = self.wasRelevant

        // No dependencies yet
        protected def requireUpdate = true
        protected def notifyCompute() = ()
        protected def notifyOptimized() = ()
    }

    // Supported file metadata properties
    private var props: Map[String, ControlProperty[String]] =
        supportedFileMetadata map (name ⇒ name → new FileMetadataProperty(FileMetadata.Evaluators(name))) toMap

    // Properties to support
    def supportedFileMetadata: Seq[String]

    // Evaluate all properties
    def evaluateFileMetadata() =
        props.values foreach (_.value)

    // Mark all properties dirty
    def markFileMetadataDirty() =
        props.values foreach (_.handleMarkDirty())

    // Getters
    def fileMediatype        = props("mediatype").value
    def filename             = props("filename") .value
    def fileSize             = props("size")     .value

    // Setters
    def setFileMediatype(mediatype: String): Unit =
        setInfoValue(mediatypeElement, mediatype)

    def setFilename(filename: String): Unit = {

        // Depending on web browsers, the filename may contain a path or not. Normalize and just keep the file name.
        val normalized = filename.replaceAllLiterally("""\""", "/")
        val index = normalized.lastIndexOf('/')
        val justFileName = if (index == -1) normalized else normalized.substring(index + 1)

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
        props foreach
            { case (name, prop) ⇒ addAtt(name, _.props(name).value) }

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
                contextStack.setBinding(self.getBindingContext)
                contextStack.pushBinding(e, self.getEffectiveId, self.getChildElementScope(e))

                contextStack.getCurrentSingleItem match {
                    case currentSingleItem: NodeInfo ⇒
                        DataModel.setValueIfChanged(
                            currentSingleItem,
                            value,
                            oldValue ⇒ DataModel.logAndNotifyValueChange(self.container.getContainingDocument, self.getIndentedLogger, "file metadata", currentSingleItem, oldValue, value, isCalculate = false),
                            reason ⇒ self.container.getContainingDocument.dispatchEvent(new XXFormsBindingErrorEvent(self.container.getContainingDocument, self, self.getLocationData, reason))
                        )
                    case _ ⇒
                }
            }
}

object FileMetadata {

    // How to evaluate each property
    private val Evaluators = Map[String, FileMetadata ⇒ String](
        "state"              → (m ⇒ if (StringUtils.isBlank(m.getValue)) "empty" else "file"),
        "mediatype"          → (m ⇒ m.mediatypeElement map (childMetadataValue(m, _)) orNull),
        "filename"           → (m ⇒ m.filenameElement  map (childMetadataValue(m, _)) orNull),
        "size"               → (m ⇒ m.sizeElement      map (childMetadataValue(m, _)) orNull),
        "progress-state"     → (m ⇒ progress(m) map (_.state.toString.toLowerCase) orNull),
        "progress-received"  → (m ⇒ progress(m) map (_.receivedSize.toString) orNull),
        "progress-expected"  → (m ⇒ progress(m) flatMap (_.expectedSize) map (_.toString) orNull)
    )

    // All possible property names
    val EvaluatorNames: Seq[String] = Evaluators.keys.toList

    private def progress(metadata: FileMetadata) = {
        val option = Multipart.getUploadProgress(NetUtils.getExternalContext.getRequest, metadata.containingDocument.getUUID, metadata.getEffectiveId)
        option filter (_.fieldName == metadata.getEffectiveId)
    }

    private def childMetadataValue(m: FileMetadata, element: Element) = {
        val contextStack = m.getContextStack
        contextStack.setBinding(m.getBindingContext)
        contextStack.pushBinding(element, m.getEffectiveId, m.getChildElementScope(element))
        DataModel.getValue(contextStack.getCurrentSingleItem)
    }
}