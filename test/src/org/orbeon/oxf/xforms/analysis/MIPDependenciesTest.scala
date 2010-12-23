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

import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.events.XXFormsValueChangeWithFocusChangeEvent
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.junit._
;

class MIPDependenciesTest extends ResourceManagerTestBase with AssertionsForJUnit {

    private var pipelineContext: PipelineContext = _
    private var document: XFormsContainingDocument = _

    @Before def setupDocument()  {
        this.pipelineContext = createPipelineContextWithExternalContext()

        val staticState= XFormsStaticStateTest.getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/mips.xml")
        this.document = new XFormsContainingDocument(pipelineContext, staticState, null, null, null)

        document.afterInitialResponse()
        document.beforeExternalEvents(pipelineContext, null)
    }

    @After def disposeDocument() {
        document.afterExternalEvents(pipelineContext)
        document.afterUpdateResponse()

        document = null
        pipelineContext = null
    }


    @Test def testTypeInvalid {
        if (Version.isPE) { // only test this feature if we are the PE version

            // Initial state
            assert("150" === getControlValue("line-total·1"))
            assert("2150" === getControlValue("subtotal"))

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
        }
    }

    @Test def testRelevance {
        if (Version.isPE) { // only test this feature if we are the PE version

            val units = getControlValue("units·1")

            // Change first item units to foo
            setControlValue("units·1", "foo")

            assert(!isValid("units·1"))
            assert(!isRelevant("line-total·1"))
            assert("2000" === getControlValue("subtotal"))

            // Change back item  units
            setControlValue("units·1", units)

            assert(isValid("units·1"))
            assert(isRelevant("line-total·1"))
            assert(isValid("line-total·1"))
            assert("150" === getControlValue("line-total·1"))
            assert("2150" === getControlValue("subtotal"))
        }
    }

    @Test def testTypeConstraintInvalid {
        if (Version.isPE) { // only test this feature if we are the PE version

            val units = getControlValue("units·1")

            // Change first item units to a constraint-invalid value
            setControlValue("units·1", "0")

            assert(!isValid("units·1"))
            assert(!isRelevant("line-total·1"))
            assert("2000" === getControlValue("subtotal"))

            // Then change to type-invalid too
            setControlValue("units·1", "foo")

            assert(!isValid("units·1"))
            assert(!isRelevant("line-total·1"))
            assert("2000" === getControlValue("subtotal"))

            // Change back item  units
            setControlValue("units·1", units)

            assert(isValid("units·1"))
            assert(isRelevant("line-total·1"))
            assert(isValid("line-total·1"))
            assert("150" === getControlValue("line-total·1"))
            assert("2150" === getControlValue("subtotal"))
        }
    }

    @Test def testSimpleRequired {
        if (Version.isPE) { // only test this feature if we are the PE version

            // NOTE: The value of @required has no dependencies in this sample, so this is a weak test 

            assert(isRequired("name·1"))
            assert(isValid("name·1"))

            setControlValue("name·1", "")
            assert(isRequired("name·1"))
            assert(!isValid("name·1"))

            setControlValue("name·1", "100")
            assert(isRequired("name·1"))
            assert(isValid("name·1"))
        }
    }

    @Test def testNormalizeSpaceContextConstraint {
        if (Version.isPE) { // only test this feature if we are the PE version

            // NOTE: The value of @required has no dependencies in this sample, so this is a weak test

            assert(isValid("name·1"))

            setControlValue("name·1", "    ") // series of spaces
            assert(!isValid("name·1"))

            setControlValue("name·1", "100")
            assert(isValid("name·1"))
        }
    }

    // TODO: more tests

    def getControlValue(controlId: String) =
        getObject(controlId).asInstanceOf[XFormsValueControl].getValue(pipelineContext)

    def setControlValue(controlId: String, value: String): Unit =
        document.handleExternalEvent(pipelineContext, new XXFormsValueChangeWithFocusChangeEvent(document, getObject(controlId).asInstanceOf[XFormsEventTarget], null, value))

    def isRelevant(controlId: String) = getObject(controlId).asInstanceOf[XFormsControl].isRelevant
    def isRequired(controlId: String) = getSingleNodeControl(controlId).isRequired
    def isReadonly(controlId: String) = getSingleNodeControl(controlId).isReadonly
    def isValid(controlId: String) = getSingleNodeControl(controlId).isValid
    def getType(controlId: String) = getSingleNodeControl(controlId).getType

    private def getSingleNodeControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsSingleNodeControl]
    private def getObject(controlId: String) = document.getObjectByEffectiveId(controlId)
}
