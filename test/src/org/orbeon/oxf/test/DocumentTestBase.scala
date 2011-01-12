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

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.xforms.analysis.XFormsStaticStateTest
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.junit.After
import org.orbeon.oxf.xforms.event.events.XXFormsValueChangeWithFocusChangeEvent
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.dom4j.{Element, Document => JDocument}
import xml.{XML, Elem}

abstract class DocumentTestBase extends ResourceManagerTestBase {

    private var pipelineContext: PipelineContext = _
    private var document: XFormsContainingDocument = _

    @After def disposeDocument() {
        if (document ne null) {
            document.afterExternalEvents(pipelineContext)
            document.afterUpdateResponse()

            document = null
        }
        pipelineContext = null
    }

    def setupDocument(documentURL: String): Unit = setupDocument(ProcessorUtils.createDocumentFromURL(documentURL, null))

    def getDocument = document
    def getPipelineContext = pipelineContext

    def setupDocument(xhtml: JDocument) {
        ResourceManagerTestBase.staticSetup()

        this.pipelineContext = createPipelineContextWithExternalContext()

        val staticState = XFormsStaticStateTest.getStaticState(xhtml)
        this.document = new XFormsContainingDocument(pipelineContext, staticState, null, null, null)

        document.afterInitialResponse()
        document.beforeExternalEvents(pipelineContext, null)
    }

    def getControlValue(controlId: String) =
        getObject(controlId).asInstanceOf[XFormsValueControl].getValue(pipelineContext)

    def setControlValue(controlId: String, value: String) {
        // This stores the value without testing for readonly
        document.startOutermostActionHandler()
        getObject(controlId).asInstanceOf[XFormsValueControl].storeExternalValue(pipelineContext, value, null)
        document.endOutermostActionHandler(pipelineContext)
    }

    def setControlValueWithEvent(controlId: String, value: String): Unit =
        document.handleExternalEvent(pipelineContext, new XXFormsValueChangeWithFocusChangeEvent(document, getObject(controlId).asInstanceOf[XFormsEventTarget], null, value))

    def isRelevant(controlId: String) = getObject(controlId).asInstanceOf[XFormsControl].isRelevant
    def isRequired(controlId: String) = getSingleNodeControl(controlId).isRequired
    def isReadonly(controlId: String) = getSingleNodeControl(controlId).isReadonly
    def isValid(controlId: String) = getSingleNodeControl(controlId).isValid
    def getType(controlId: String) = getSingleNodeControl(controlId).getType

    // Automatically convert between Scala Elem andDom4j Document/Element
    implicit def scalaElemToDom4jDocument(element: Elem) = Dom4jUtils.readDom4j(element.toString)
    implicit def scalaElemToDom4jElement(element: Elem) = Dom4jUtils.readDom4j(element.toString).getRootElement
    implicit def dom4jElementToScalaElem(element: Element) = XML.loadString(Dom4jUtils.domToString(element))

    // TODO: There is probably a better way to write these conversions
    implicit def scalaElemSeqToDom4jElementSeq(seq: Traversable[Elem]) = seq map (scalaElemToDom4jElement(_)) toList
    implicit def dom4jElementSeqToScalaElemSeq(seq: Traversable[Element]) = seq map (dom4jElementToScalaElem(_)) toList

    private def getSingleNodeControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsSingleNodeControl]
    private def getObject(controlId: String) = document.getObjectByEffectiveId(controlId)
}