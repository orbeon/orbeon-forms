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

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xforms.xbl.XBLBindingBuilder
import org.orbeon.oxf.xforms.{XFormsStaticStateImpl, XFormsUtils}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.VirtualNode
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.xforms.XFormsConstants
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.JavaConverters._

class XFormsAnnotatorTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private val xmlDoc = elemToDom4j(
    <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:foo="http://orbeon.org/oxf/xml/foo"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xbl="http://www.w3.org/ns/xbl"

        id="html"

        lang="{'en'}">

        <xh:head>
            <xh:title><xf:output id="output-in-title" value="'My Title'"/></xh:title>
            <xf:model>
                <xf:instance id="main-instance">
                    <instance id="instance-root">
                        <value id="instance-value"/>
                    </instance>
                </xf:instance>
                <xf:setvalue ev:event="xforms-model-construct-done" ref="value1" value="current-dateTime()"/>
                <xs:schema>
                    <xs:element name="form" id="schema-element">
                    </xs:element>
                </xs:schema>
            </xf:model>
            <xbl:xbl>
                <xbl:binding id="dateTime-component" element="foo|dateTime">
                    <xbl:template>
                        <xf:instance id="instance-in-xbl">
                            <instance id="xbl-instance-root">
                                <value id="xbl-instance-value"/>
                            </instance>
                        </xf:instance>

                        <div id="div-in-xbl"/>

                    </xbl:template>
                </xbl:binding>
            </xbl:xbl>
        </xh:head>
        <xh:body>

            <foo:dateTime id="dateTime1-control" ref="value1"/>
            <foo:dateTime ref="value2"/>

            <xf:input id="value1-control" ref="value1"/>
            <xf:input ref="value2">
                <xf:label>
                    <xf:output id="output-in-label" value="'My Label'"/>
                    <xh:img id="img-in-label" src="{'avt-image.png'}"/>
                </xf:label>
            </xf:input>

            <xh:span id="span" style="{'background-color: red'}"/>

        </xh:body>
    </xh:html>
  )


  describe("Annotator namespace handling") {

    val metadata = Metadata.apply(new IdGenerator(1), isTopLevelPart = true)

    TransformerUtils.writeDom4j(xmlDoc, new XFormsAnnotator(metadata))

    it("provides namespace information for elements") {

      val ids = List(
        "output-in-title",
        "html",
        "main-instance",
        "dateTime-component",
        "dateTime1-control",
        "value1-control",
        "output-in-label",
        "img-in-label",
        "span"
      )

      assert(ids forall (id => metadata.getNamespaceMapping(id).isDefined))
    }

    it("doesn't provide namespace information for elements processed as part of shadow tree processing") {

      val ids = List(
        "instance-in-xbl",
        "div-in-xbl"
      )

      assert(ids forall (id => metadata.getNamespaceMapping(id).isEmpty))
    }

    it("doesn't provide namespace information for elements in instances or schemas") {

      val ids = List(
        "instance-root",
        "instance-value",
        "xbl-instance-root",
        "xbl-instance-value",
        "schema-element"
      )

      assert(ids forall (id => metadata.getNamespaceMapping(id).isEmpty))
    }
  }

  describe("Annotator `xxf:attribute` handling") {

    val metadata     = Metadata.apply(new IdGenerator(1), isTopLevelPart = true)
    val annotatedDoc = XBLBindingBuilder.annotateShadowTree(metadata, xmlDoc, "")
    val docWrapper   = new DocumentWrapper(annotatedDoc, null, XPath.GlobalConfiguration)

    val elemNames = List(
      "html" -> "lang",
      "span" -> "style"
    )

    for ((elemName, attName) <- elemNames)
      it(s"creates an `xxf:attribute` for the `$elemName` element") {

        val result =
          XPathCache.evaluate(
          contextItem        = docWrapper,
          xpathString        = s"//xxf:attribute[@for = '$elemName']",
          namespaceMapping   = XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING,
          variableToValueMap = null,
          functionLibrary    = null,
          functionContext    = null,
          baseURI            = null,
          locationData       = null,
          reporter           = null
        ).asScala

        assert(1 == result.size)

        val resultElement = result collectFirst { case v: VirtualNode => unsafeUnwrapElement(v) }

        assert(resultElement exists (XFormsUtils.getElementId(_).nonAllBlank))
        assert(resultElement exists (_.attributeValue(XFormsConstants.NAME_QNAME) == attName))
      }
  }
}