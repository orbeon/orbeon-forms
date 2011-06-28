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
import org.orbeon.oxf.xforms.event.events.XXFormsValueChangeWithFocusChangeEvent
import org.orbeon.oxf.xforms.control._
import controls.XFormsSelect1Control
import org.dom4j.{Element, Document => JDocument}
import xml.{XML, Elem}
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsEventTarget}
import org.orbeon.oxf.xforms.{XFormsStaticStateImpl, XFormsContainingDocument}

abstract class DocumentTestBase extends ResourceManagerTestBase {

    private var document: XFormsContainingDocument = _

    @After def disposeDocument() {
        if (document ne null) {
            document.afterExternalEvents()
            document.afterUpdateResponse()

            document = null
        }
    }

    def setupDocument(documentURL: String): Unit =
        setupDocument(ProcessorUtils.createDocumentFromURL(documentURL, null))

    def getDocument = document

    def setupDocument(xhtml: JDocument) {
        ResourceManagerTestBase.staticSetup()

        val staticState = XFormsStaticStateImpl.create(xhtml)
        this.document = new XFormsContainingDocument(staticState, null, null, null)

        document.afterInitialResponse()
        document.beforeExternalEvents(null)
    }

    def getControlValue(controlId: String) = getValueControl(controlId).getValue
    def getControlExternalValue(controlId: String) = getValueControl(controlId).getExternalValue

    def setControlValue(controlId: String, value: String) {
        // This stores the value without testing for readonly
        document.startOutermostActionHandler()
        getValueControl(controlId).storeExternalValue(value, null)
        document.endOutermostActionHandler()
    }

    def setControlValueWithEvent(controlId: String, value: String): Unit =
        ClientEvents.processEvent(document, new XXFormsValueChangeWithFocusChangeEvent(document, getObject(controlId).asInstanceOf[XFormsEventTarget], null, value))

    def isRelevant(controlId: String) = getObject(controlId).asInstanceOf[XFormsControl].isRelevant
    def isRequired(controlId: String) = getSingleNodeControl(controlId).isRequired
    def isReadonly(controlId: String) = getSingleNodeControl(controlId).isReadonly
    def isValid(controlId: String) = getSingleNodeControl(controlId).isValid
    def getType(controlId: String) = getSingleNodeControl(controlId).getType

    def getItemset(controlId: String) =
        getObject(controlId).asInstanceOf[XFormsSelect1Control].getItemset.getJSONTreeInfo(null, false, null)

    // Automatically convert between Scala Elem andDom4j Document/Element
    implicit def elemToDocument(e: Elem) = Dom4jUtils.readDom4j(e.toString)
    implicit def elemToElement(e: Elem) = Dom4jUtils.readDom4j(e.toString).getRootElement
    implicit def elementToElem(e: Element) = XML.loadString(Dom4jUtils.domToString(e))

//    // TODO: There is probably a better way to write these conversions
    implicit def scalaElemSeqToDom4jElementSeq(seq: Traversable[Elem]) = seq map (elemToElement(_)) toList
    implicit def dom4jElementSeqToScalaElemSeq(seq: Traversable[Element]) = seq map (elementToElem(_)) toList

    protected def getControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsControl]
    protected def getSingleNodeControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsSingleNodeControl]
    protected def getValueControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsValueControl]
    protected def getObject(controlId: String) = document.getObjectByEffectiveId(controlId)
}