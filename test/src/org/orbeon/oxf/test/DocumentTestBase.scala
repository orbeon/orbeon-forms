/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.junit.After
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.control._
import controls.XFormsSelect1Control
import org.dom4j.{Element, Document ⇒ JDocument}
import xml.{XML, Elem}
import org.orbeon.oxf.xforms.event.{XFormsCustomEvent, Dispatch, ClientEvents, XFormsEventTarget}
import org.orbeon.oxf.xforms.{XFormsInstance, XFormsStaticStateImpl, XFormsContainingDocument}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml.TransformerUtils

abstract class DocumentTestBase extends ResourceManagerTestBase {

    private var _document: XFormsContainingDocument = _
    def document = _document

    @After def disposeDocument() {
        if (_document ne null) {
            _document.afterExternalEvents()
            _document.afterUpdateResponse()

            _document = null
        }
    }

    def setupDocument(documentURL: String): XFormsContainingDocument =
        setupDocument(ProcessorUtils.createDocumentFromURL(documentURL, null))

    def setupDocument(xhtml: JDocument): XFormsContainingDocument = {
        ResourceManagerTestBase.staticSetup()

        val (template, staticState) = XFormsStaticStateImpl.createFromDocument(xhtml)
        this._document = new XFormsContainingDocument(staticState, AnnotatedTemplate(template), null, null)

        _document.afterInitialResponse()
        _document.beforeExternalEvents(null)

        _document
    }

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
        _document.startOutermostActionHandler()
        getValueControl(controlId).storeExternalValue(value)
        _document.endOutermostActionHandler()
    }

    def setControlValueWithEvent(controlId: String, value: String): Unit =
        ClientEvents.processEvent(_document, new XXFormsValueEvent(getObject(controlId).asInstanceOf[XFormsEventTarget], value))

    def isRelevant(controlId: String) = getObject(controlId).asInstanceOf[XFormsControl].isRelevant
    def isRequired(controlId: String) = getSingleNodeControl(controlId).isRequired
    def isReadonly(controlId: String) = getSingleNodeControl(controlId).isReadonly
    def isValid(controlId: String) = getSingleNodeControl(controlId).isValid
    def getType(controlId: String) = getSingleNodeControl(controlId).valueType

    def getItemset(controlId: String) =
        getObject(controlId).asInstanceOf[XFormsSelect1Control].getItemset.getJSONTreeInfo(null, null)

    // Automatically convert between Scala Elem andDom4j Document/Element
    implicit def elemToDocument(e: Elem) = Dom4jUtils.readDom4j(e.toString)
    implicit def elemToElement(e: Elem) = Dom4jUtils.readDom4j(e.toString).getRootElement
    implicit def elementToElem(e: Element) = XML.loadString(Dom4jUtils.domToString(e))

//    implicit def elemToDocumentWrapper(e: Elem) = new DocumentWrapper(elemToDocument(e), null, XPathCache.getGlobalConfiguration)

//    // TODO: There is probably a better way to write these conversions
    implicit def scalaElemSeqToDom4jElementSeq(seq: Traversable[Elem]): Seq[Element] = seq map (elemToElement(_)) toList
    implicit def dom4jElementSeqToScalaElemSeq(seq: Traversable[Element]): Seq[Elem]  = seq map (elementToElem(_)) toList

    protected def getControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsControl]
    protected def getSingleNodeControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsSingleNodeControl]
    protected def getValueControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsValueControl]
    protected def getObject(controlId: String) = _document.getObjectByEffectiveId(controlId)
}