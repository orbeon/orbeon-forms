/**
 *  Copyright (C) 2012 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.state

import org.junit.{Assume, Test}
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xforms.state.XFormsOperations._
import org.orbeon.oxf.xforms.state.XFormsProtocols._
import org.orbeon.oxf.xforms.{XFormsContainingDocumentBuilder, XFormsStaticStateImpl}
import org.orbeon.oxf.xml.SAXStoreBinaryFormat._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatestplus.junit.AssertionsForJUnit
import org.xml.sax.Attributes

import scala.jdk.CollectionConverters._


class SerializationTest extends DocumentTestBase with AssertionsForJUnit {

  // NOTE: For #1890, place and use lang AVTs, see https://github.com/orbeon/orbeon-forms/issues/1890
  val simpleDoc =
      <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
           xmlns:xh="http://www.w3.org/1999/xhtml"
           xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
           lang="{xxf:instance('fr-language-instance')}"
           xml:lang="{xxf:instance('fr-language-instance')}"
           xxf:xpath-analysis="true">
        <xh:head>
          <xh:title><xf:output value="xxf:lang()"/></xh:title>
          <xf:model>
            <xf:instance id="instance">
              <value>0</value>
            </xf:instance>
            <xf:instance id="fr-language-instance">
              <value>en</value>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:input id="input" ref="instance()"/>
          <xf:trigger id="trigger"><xf:label/></xf:trigger>
        </xh:body>
      </xh:html>.toDocument

  @Test def saxStore(): Unit = {

    // Serialize then deserialize
    val saxStore = TransformerUtils.dom4jToSAXStore(simpleDoc, false)
    val serializedBytes = toByteSeq(saxStore)
    val deserializedSAXStore = fromByteSeq[SAXStore](serializedBytes)
    val deserializedDoc = TransformerUtils.saxStoreToDom4jDocument(deserializedSAXStore)

    // Compare
    assertXMLDocumentsIgnoreNamespacesInScope(simpleDoc, deserializedDoc)
  }

  @Test def saxStoreWithMarks(): Unit = {

    // Transform to SAXStore while collecting marks
    val saxStore = new SAXStore
    TransformerUtils.writeDom4j(simpleDoc, new ForwardingXMLReceiver(saxStore) {
      override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
        // Mark all elements with an id
        Option(attributes.getValue("id")) foreach
          saxStore.createAndRememberMark

        super.startElement(uri, localname, qName, attributes)
      }
    })

    val serializedBytes = toByteSeq(saxStore)
    val deserializedSAXStore = fromByteSeq[SAXStore](serializedBytes)

    // All expected documents
    val expectedDocs =
      List(
        <xf:instance id="instance" xmlns:xf="http://www.w3.org/2002/xforms">
          <value>0</value>
        </xf:instance>.toDocument,
        <xf:instance id="fr-language-instance" xmlns:xf="http://www.w3.org/2002/xforms">
          <value>en</value>
        </xf:instance>.toDocument,
        <xf:input id="input" ref="instance()" xmlns:xf="http://www.w3.org/2002/xforms"/>.toDocument,
        <xf:trigger id="trigger" xmlns:xf="http://www.w3.org/2002/xforms"><xf:label/></xf:trigger>.toDocument
      )

    // All actual documents
    val actualDocs =
      deserializedSAXStore.getMarks.asScala map
        TransformerUtils.saxStoreMarkToDom4jDocument

    // Compare all
    assert(actualDocs.size === expectedDocs.size)
    for ((expected, actual) <- expectedDocs zip actualDocs)
      assertXMLDocumentsIgnoreNamespacesInScope(expected, actual)
  }

  @Test def dynamicState(): Unit = {
    val originalDoc = this setupDocument simpleDoc

    val serialized = DynamicState(originalDoc)
    val serializedBytes = toByteSeq(serialized)

    val deserialized = fromByteSeq[DynamicState](serializedBytes)
    assert(serialized === deserialized)
  }

  @Test def template(): Unit = {

    Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

    // Template not stored
    locally {
      val doc = this setupDocument
        <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
             xmlns:xh="http://www.w3.org/1999/xhtml"
             xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <xh:head>
            <xf:model>
              <xf:instance id="instance">
                <value/>
              </xf:instance>
            </xf:model>
          </xh:head>
        </xh:html>.toDocument

      assert(doc.staticState.template.isEmpty)
    }

    // Template stored in the static state because of full updates
    locally {
      val doc = this setupDocument
        <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
             xmlns:xh="http://www.w3.org/1999/xhtml"
             xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <xh:head>
            <xf:model>
              <xf:instance id="instance">
                <value/>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:group xxf:update="full"/>
          </xh:body>
        </xh:html>.toDocument

      assert(doc.staticState.template.isDefined)
    }
  }

  @Test def staticState(): Unit = {

    Assume.assumeTrue(Version.isPE)

    val doc = this setupDocument simpleDoc
    val staticStateXML = doc.staticState.asInstanceOf[XFormsStaticStateImpl].staticStateDocumentForTests.xmlDocument

    // Serialize/deserialize
    val serialized = doc.staticState.encodedState
    val restored = XFormsContainingDocumentBuilder.restoreStaticState(None, serialized, forceEncryption = false)
    val restoredXML = restored.staticStateDocumentForTests.xmlDocument

    // Compare expected/actual XML representation of the static state
    assertXMLDocumentsIgnoreNamespacesInScope(staticStateXML, restoredXML)

    // Check other aspects of the restored state
    val part = restored.topLevelPart
    assert(part.hasControls)

    val models = part.getModelsForScope(part.startScope)
    assert(models.size === 1)
    assert(models.head.instances.head._2.staticId === "instance")

    assert(part.getControlAnalysis("input") ne null)
    assert(part.getControlAnalysis("trigger") ne null)
  }
}
