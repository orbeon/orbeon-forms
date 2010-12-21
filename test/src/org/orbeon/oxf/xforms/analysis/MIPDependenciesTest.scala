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
package org.orbeon.oxf.xforms.analysis

import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.events.XXFormsValueChangeWithFocusChangeEvent
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.common.Version;

class MIPDependenciesTest extends ResourceManagerTestBase with AssertionsForJUnit {

    @Test
    def testMIPChanges {
        if (Version.isPE) { // only test this feature if we are the PE version
            // Create document
            val pipelineContext = createPipelineContextWithExternalContext()

            val staticState= XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/mips.xml")
            val document = new XFormsContainingDocument(pipelineContext, staticState, null, null, null)
            document.afterInitialResponse()

            assert("150" === getControlValue("line-total·1"))
            assert("2150" === getControlValue("subtotal"))

            // Test updates on document
            document.beforeExternalEvents(pipelineContext, null)

            // Change first item price to foo
            setControlValue("price·1", "foo")
            assert(!isValid("price·1"))
            assert(!isValid("line-total·1"))
            assert("-" === getControlValue("line-total·1"))
            assert("2000" === getControlValue("subtotal"))

            // Change first item price to 100
            setControlValue("price·1", "100")
            assert(isValid("price·1"))
            assert(isValid("line-total·1"))
            assert("300" === getControlValue("line-total·1"))
            assert("2300" === getControlValue("subtotal"))

            document.afterExternalEvents(pipelineContext)

            document.afterUpdateResponse()

            def setControlValue(controlId: String, value: String): Unit =
                document.handleExternalEvent(pipelineContext, new XXFormsValueChangeWithFocusChangeEvent(document, document.getObjectByEffectiveId(controlId).asInstanceOf[XFormsEventTarget], null, value))

            def isRequired(controlId: String) =
                (document.getObjectByEffectiveId(controlId).asInstanceOf[XFormsSingleNodeControl]).isRequired

            def getType(controlId: String) =
                (document.getObjectByEffectiveId(controlId).asInstanceOf[XFormsSingleNodeControl]).getType

            def isValid(controlId: String) =
                (document.getObjectByEffectiveId(controlId).asInstanceOf[XFormsSingleNodeControl]).isValid

            def isRelevant(controlId: String) =
                (document.getObjectByEffectiveId(controlId).asInstanceOf[XFormsControl]).isRelevant

            def getControlValue(controlId: String) =
                (document.getObjectByEffectiveId(controlId).asInstanceOf[XFormsValueControl]).getValue(pipelineContext)

            def isReadonly(controlId: String) =
                (document.getObjectByEffectiveId(controlId).asInstanceOf[XFormsSingleNodeControl]).isReadonly
        }
    }
}
