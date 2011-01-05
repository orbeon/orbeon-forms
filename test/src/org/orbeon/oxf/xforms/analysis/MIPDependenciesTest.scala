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
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.junit._
import org.scalatest.junit._
//import runner.RunWith
//import org.scalatest.{BeforeAndAfterEach, FlatSpec}


//@RunWith(classOf[JUnitRunner])
//class MIPDependenciesTest extends ResourceManagerTestBase with FlatSpec with AssertionsForJUnit with BeforeAndAfterEach {
class MIPDependenciesTest extends ResourceManagerTestBase with AssertionsForJUnit {


    private var pipelineContext: PipelineContext = _
    private var document: XFormsContainingDocument = _

//    override protected def beforeEach() = setupDocument()
//    override protected def afterEach() = disposeDocument()

    @Before def setupDocument(): Unit = setupDocument("oxf:/org/orbeon/oxf/xforms/analysis/mips.xhtml")

    def setupDocument(documentURL: String)  {

        ResourceManagerTestBase.staticSetup()

        this.pipelineContext = createPipelineContextWithExternalContext()

        val staticState= XFormsStaticStateTest.getStaticState(documentURL)
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
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

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

    @Test def testRelevance {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

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

    @Test def testTypeConstraintInvalid {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

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

    @Test def testSimpleRequired {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

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

    @Test def testNormalizeSpaceContextConstraint {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        // NOTE: The value of @required has no dependencies in this sample, so this is a weak test

        assert(isValid("name·1"))

        setControlValue("name·1", "    ") // series of spaces
        assert(!isValid("name·1"))

        setControlValue("name·1", "100")
        assert(isValid("name·1"))
    }

    // See: [ #315733 ] Incorrect MIPs when more than two binds point to the same node
    //      http://forge.ow2.org/tracker/index.php?func=detail&aid=315733&group_id=168&atid=350207
    @Test def testMultipleBindsOnSameNode {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        setupDocument("oxf:/org/orbeon/oxf/xforms/analysis/mips-multiple.xhtml")

        // Test all combinations of MIPs
        for {
            required <- Seq(false, true)
            valid <- Seq(false, true)
            readonly <- Seq(false, true)
            value = Seq(required, valid, readonly) map (if (_) "1" else "0") mkString
        } yield {
            // Value looks like "000" to "111"
            setControlValue("input", value)

            // Check all MIPs
            assert(isReadonly("input") == readonly)
            assert(isValid("input") == valid)
            assert(isRequired("input") == required)
        }
    }

// Experiment with BDD style
//    "The first name control" must "be valid" in {
//       assert(isValid("name·1"))
//    }
//
//    it must "be invalid when set to a non-empty, blank string" in {
//        setControlValue("name·1", "    ") // series of spaces
//        assert(!isValid("name·1"))
//    }
//
//    it must "be valid again when set to a positive integer" in {
//        setControlValue("name·1", "100")
//        assert(isValid("name·1"))
//    }

    // TODO: more tests

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

    private def getSingleNodeControl(controlId: String) = getObject(controlId).asInstanceOf[XFormsSingleNodeControl]
    private def getObject(controlId: String) = document.getObjectByEffectiveId(controlId)
}
