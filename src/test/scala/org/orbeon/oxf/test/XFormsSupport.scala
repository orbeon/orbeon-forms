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

import org.dom4j.{Element ⇒ JElement}
import org.mockito.Mockito
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.{XFormsValueControl, XFormsSingleNodeControl, XFormsControl}
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsEventTarget, XFormsCustomEvent, Dispatch}
import org.orbeon.oxf.xforms.processor.XFormsServer
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsInstance}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.scalatest.mock.MockitoSugar
import scala.xml.{XML, Elem}


trait XFormsSupport extends MockitoSugar {

    self: DocumentTestBase ⇒

    def withActionAndDoc[T](url: String)(body: ⇒ T): T =
        withActionAndDoc(setupDocument(url))(body)

    def withActionAndDoc[T](doc: XFormsContainingDocument)(body: ⇒ T): T =
        withScalaAction(mockActionInterpreter(doc)) {
            withContainingDocument(doc) {
                body
            }
        }

    private def mockActionInterpreter(doc: XFormsContainingDocument) = {
        val actionInterpreter = mock[XFormsActionInterpreter]
        Mockito when actionInterpreter.containingDocument thenReturn doc
        Mockito when actionInterpreter.container thenReturn doc
        Mockito when actionInterpreter.indentedLogger thenReturn new IndentedLogger(XFormsServer.logger, "action")

        actionInterpreter
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

    // Get a top-level instance
    def instance(instanceStaticId: String) =
        document.findInstance(instanceStaticId)

    // Convert an instance to a string
    def instanceToString(instance: XFormsInstance) =
        TransformerUtils.tinyTreeToString(instance.documentInfo)

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
    protected def getControl(controlId: String)           = getObject(controlId).asInstanceOf[XFormsControl]
    protected def getSingleNodeControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsSingleNodeControl]
    protected def getValueControl(controlId: String)      = getObject(controlId).asInstanceOf[XFormsValueControl]
    protected def getObject(controlId: String)            = document.getObjectByEffectiveId(controlId)
}
