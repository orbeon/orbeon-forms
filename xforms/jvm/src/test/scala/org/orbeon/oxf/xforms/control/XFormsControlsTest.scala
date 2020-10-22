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
package org.orbeon.oxf.xforms.control

import org.mockito.{Matchers, Mockito}
import org.orbeon.dom.QName
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, PartAnalysis}
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.scalatest.funspec.AnyFunSpecLike
import org.xml.sax.helpers.AttributesImpl

class XFormsControlsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  // Mock just what's needed to make XFormsInputControl as used below happy
  private def getContainingDocument(id: String): XFormsContainingDocument = {

    val doc = Mockito.mock(classOf[XFormsContainingDocument])
    Mockito.when(doc.getContainingDocument).thenReturn(doc)

    val elementAnalysis = Mockito.mock(classOf[ElementAnalysis])
    Mockito.when(elementAnalysis.staticId).thenReturn(id)
    Mockito.when(elementAnalysis.prefixedId).thenReturn(id)

    val partAnalysis = Mockito.mock(classOf[PartAnalysis])
    Mockito.when(partAnalysis.getControlAnalysis(Matchers.anyString)).thenReturn(elementAnalysis)
    Mockito.when(doc.getPartAnalysis).thenReturn(partAnalysis)

    doc
  }

  describe("Diff custom MIPs changes ") {
    val attributes = new AttributesImpl
    val control1 = new XFormsInputControl(getContainingDocument("input-1"), null, null, "input-1") {
      override val customMIPs = Map(
        "name1" -> "value1",
        "name2" -> "value2",
        "name3" -> "value3",
        "name4" -> "value4"
      )
    }
    val control2 = new XFormsInputControl(getContainingDocument("input-2"), null, null, "input-2") {
      override val customMIPs = Map(
        // leave as is
        "name1" -> "value1",
        // remove name2
        // change value
        "name3" -> "newvalue3",
        // leave as is
        "name4" -> "value4"
      )
    }
    XFormsSingleNodeControl.addAjaxCustomMIPs(attributes, Some(control1), control2)
    it ("must report the correct attribute changes") {
      assert("-name2-value2 -name3-value3 +name3-newvalue3" === attributes.getValue("class"))
    }
  }

  describe("Diff new custom MIPs") {
    val attributes = new AttributesImpl
    val control2 = new XFormsInputControl(getContainingDocument("input-1"), null, null, "input-1") {
      override val customMIPs = Map(
        "name1" -> "value1",
        "name2" -> "value2",
        "name3" -> "value3",
        "name4" -> "value4"
      )
    }
    XFormsSingleNodeControl.addAjaxCustomMIPs(attributes, None, control2)
    it ("must report the correct attribute changes") {
      assert("+name1-value1 +name2-value2 +name3-value3 +name4-value4" === attributes.getValue("class"))
    }
  }

  describe("Diff class AVT changes") {
    val attributes = new AttributesImpl
    val control1 = new XFormsInputControl(getContainingDocument("input-1"), null, null, "input-1") {
      override def extensionAttributeValue(attributeName: QName) = Some("foo bar gaga")
    }
    val control2 = new XFormsInputControl(getContainingDocument("input-2"), null, null, "input-2") {
      override def extensionAttributeValue(attributeName: QName) = Some("bar toto")
    }
    ControlAjaxSupport.addAjaxClasses(attributes, Some(control1), control2)
    it ("must report the correct attribute changes") {
      assert("-foo -gaga +toto" === attributes.getValue("class"))
    }
  }

  describe("Diff new class AVT") {
    val attributes = new AttributesImpl
    val control2 = new XFormsInputControl(getContainingDocument("input-1"), null, null, "input-1") {
      override def extensionAttributeValue(attributeName: QName) = Some("foo bar")
    }
    ControlAjaxSupport.addAjaxClasses(attributes, None, control2)
    it ("must report the correct attribute changes") {
      assert("foo bar" === attributes.getValue("class"))
    }
  }

  // NOTE: started writing this test, but just using an XFormsOutputControl without the context of an XFormsContainingDocument seems a dead-end!
//    @Test
//    public void testOutputControlRewrite() {
//
//        final Document document = Dom4jUtils.readFromURL("oxf:/org/orbeon/oxf/xforms/processor/test-form.xml", false, false);
//        final DocumentWrapper documentWrapper = new DocumentWrapper(document, null, new Configuration());
//        final Element outputElement = (Element) ((NodeWrapper) XPathCache.evaluateSingle(new PipelineContext(), documentWrapper, "(//xh:body//xf:output)[1]", XFormsDocumentAnnotatorContentHandlerTest.BASIC_NAMESPACE_MAPPINGS, null, null, null, null, null)).getUnderlyingNode();
//
//        final PipelineContext pipelineContext = new PipelineContext();
//
//        final XBLContainer container = new XBLContainer("", null) {};
//        final XFormsOutputControl control1 = new XFormsOutputControl(container, null, outputElement, "output", "output-1");
//        control1.setBindingContext(pipelineContext, new XFormsContextStack.BindingContext(null, null, Collections.singletonList(documentWrapper.wrap(outputElement)), 1, "output-1", true, outputElement, null, false, null));
//
//        control1.evaluateIfNeeded(pipelineContext);
//
//        assertEquals("", control1.getExternalValue(pipelineContext));
//    }
}