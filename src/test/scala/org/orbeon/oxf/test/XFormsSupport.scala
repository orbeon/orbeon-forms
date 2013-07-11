/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.test

import org.dom4j.{Element ⇒ JElement, Document ⇒ JDocument}
import org.orbeon.oxf.xforms.XFormsInstance
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.{XFormsValueControl, XFormsSingleNodeControl, XFormsControl}
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsEventTarget, XFormsCustomEvent, Dispatch}
import org.orbeon.oxf.xml.{Dom4j, TransformerUtils}
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.xml.{XML, Elem}


trait XFormsSupport {

    self: DocumentTestBase ⇒

    // Dispatch a custom event to the object with the given prefixed id
    def dispatch(name: String, prefixedId: String) =
        Dispatch.dispatchEvent(
            new XFormsCustomEvent(
                name,
                document.getObjectByEffectiveId(prefixedId).asInstanceOf[XFormsEventTarget],
                Map(),
                bubbles = true,
                cancelable = true)
        )

    // Get an instance value as a string
    def instanceAsString(instance: XFormsInstance) = TransformerUtils.tinyTreeToString(instance.documentInfo)

    def getControlValue(controlId: String) = getValueControl(controlId).getValue
    def getControlExternalValue(controlId: String) = getValueControl(controlId).getExternalValue

    def setControlValue(controlId: String, value: String) {
        // This stores the value without testing for readonly
        document.startOutermostActionHandler()
        getValueControl(controlId).storeExternalValue(value)
        document.endOutermostActionHandler()
    }

    def setControlValueWithEvent(controlId: String, value: String): Unit =
        ClientEvents.processEvent(document, new XXFormsValueEvent(getObject(controlId).asInstanceOf[XFormsEventTarget], value))

    def isRelevant(controlId: String) = getObject(controlId).asInstanceOf[XFormsControl].isRelevant
    def isRequired(controlId: String) = getSingleNodeControl(controlId).isRequired
    def isReadonly(controlId: String) = getSingleNodeControl(controlId).isReadonly
    def isValid(controlId: String)    = getSingleNodeControl(controlId).isValid
    def getType(controlId: String)    = getSingleNodeControl(controlId).valueType

    def getItemset(controlId: String) =
        getObject(controlId).asInstanceOf[XFormsSelect1Control].getItemset.getJSONTreeInfo(null, null)

    // Automatically convert between Scala Elem andDom4j Document/Element
    implicit def elemToDocument(e: Elem)    = Dom4jUtils.readDom4j(e.toString)
    implicit def elemToElement(e: Elem)     = Dom4jUtils.readDom4j(e.toString).getRootElement
    implicit def elementToElem(e: JElement) = XML.loadString(Dom4jUtils.domToString(e))

//    implicit def elemToDocumentWrapper(e: Elem) = new DocumentWrapper(elemToDocument(e), null, XPathCache.getGlobalConfiguration)

//    // TODO: There is probably a better way to write these conversions
    implicit def scalaElemSeqToDom4jElementSeq(seq: Traversable[Elem]): Seq[JElement] = seq map elemToElement toList
    implicit def dom4jElementSeqToScalaElemSeq(seq: Traversable[JElement]): Seq[Elem]  = seq map elementToElem toList

    protected def getControl(controlId: String)           = getObject(controlId).asInstanceOf[XFormsControl]
    protected def getSingleNodeControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsSingleNodeControl]
    protected def getValueControl(controlId: String)      = getObject(controlId).asInstanceOf[XFormsValueControl]
    protected def getObject(controlId: String)            = document.getObjectByEffectiveId(controlId)
}
