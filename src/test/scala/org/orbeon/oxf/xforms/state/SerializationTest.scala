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

import org.scalatest.junit.AssertionsForJUnit
import org.dom4j.Document
import org.orbeon.oxf.test.DocumentTestBase
import XFormsOperations._
import XFormsProtocols._
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes
import collection.JavaConverters._
import org.junit.{Assume, Test}
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.xforms.XFormsStaticStateImpl
import org.orbeon.oxf.xml.Dom4j.elemToDocument

class SerializationTest extends DocumentTestBase with AssertionsForJUnit {

    val simpleDoc: Document =
        <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                 xmlns:xh="http://www.w3.org/1999/xhtml"
                 xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <xh:head>
                <xf:model>
                    <xf:instance id="instance">
                        <value>0</value>
                    </xf:instance>
                </xf:model>
            </xh:head>
            <xh:body>
                <xf:input id="input" ref="instance()"/>
                <xf:trigger id="trigger"><xf:label/></xf:trigger>
            </xh:body>
        </xh:html>

    @Test def saxStore() {

        // Serialize then deserialize
        val saxStore = TransformerUtils.dom4jToSAXStore(simpleDoc, false)
        val serializedBytes = toByteSeq(saxStore)
        val deserializedSAXStore = fromByteSeq[SAXStore](serializedBytes)
        val deserializedDoc = TransformerUtils.saxStoreToDom4jDocument(deserializedSAXStore)

        // Compare
        assert(Dom4j.compareDocumentsIgnoreNamespacesInScope(simpleDoc, deserializedDoc))
    }

    @Test def saxStoreWithMarks() {

        // Transform to SAXStore while collecting marks
        val saxStore = new SAXStore
        TransformerUtils.writeDom4j(simpleDoc, new ForwardingXMLReceiver(saxStore) {
            override def startElement(uri: String, localname: String, qName: String, attributes: Attributes) {
                // Mark all elements with an id
                Option(attributes.getValue("id")) foreach
                    (saxStore.getMark(_))

                super.startElement(uri, localname, qName, attributes)
            }
        })

        val serializedBytes = toByteSeq(saxStore)
        val deserializedSAXStore = fromByteSeq[SAXStore](serializedBytes)

        // All expected documents
        val expectedDocs = Seq[Document](
            <xf:instance id="instance" xmlns:xf="http://www.w3.org/2002/xforms">
                <value>0</value>
            </xf:instance>,
            <xf:input id="input" ref="instance()" xmlns:xf="http://www.w3.org/2002/xforms"/>,
            <xf:trigger id="trigger" xmlns:xf="http://www.w3.org/2002/xforms"><xf:label/></xf:trigger>
        )

        // All actual documents
        val actualDocs = deserializedSAXStore.getMarks.asScala map
            (TransformerUtils.saxStoreMarkToDom4jDocument(_))

        // Compare all
        assert(actualDocs.size === expectedDocs.size)
        for ((expected, actual) ← expectedDocs zip actualDocs)
            assert(Dom4j.compareDocumentsIgnoreNamespacesInScope(expected, actual))
    }

    @Test def dynamicState() {
        val originalDoc = this setupDocument simpleDoc

        val serialized = DynamicState(originalDoc)
        val serializedBytes = toByteSeq(serialized)

        val deserialized = fromByteSeq[DynamicState](serializedBytes)
        assert(serialized === deserialized)
    }

    @Test def template() {

        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        // Template not stored
        locally {
            val doc = this setupDocument
                <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                         xmlns:xh="http://www.w3.org/1999/xhtml"
                         xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                    <xh:head>
                        <xf:model xxf:noscript="false">
                            <xf:instance id="instance">
                                <value/>
                            </xf:instance>
                        </xf:model>
                    </xh:head>
                </xh:html>


            assert(! doc.getStaticState.template.isDefined)
            assert(doc.getTemplate eq null)
        }

        // Template stored in the static state because of noscript mode
        locally {
            val doc = this setupDocument
                <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                         xmlns:xh="http://www.w3.org/1999/xhtml"
                         xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                    <xh:head>
                        <xf:model xxf:noscript="true">
                            <xf:instance id="instance">
                                <value/>
                            </xf:instance>
                        </xf:model>
                    </xh:head>
                </xh:html>


            assert(doc.getStaticState.template.isDefined)
            assert(doc.getTemplate eq null)
        }

        // Template stored in the dynamic state because of noscript mode
        locally {
            val doc = this setupDocument
                <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                         xmlns:xh="http://www.w3.org/1999/xhtml"
                         xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                    <xh:head>
                        <xf:model xxf:noscript="true" xxf:noscript-template="dynamic">
                            <xf:instance id="instance">
                                <value/>
                            </xf:instance>
                        </xf:model>
                    </xh:head>
                </xh:html>

            assert(! doc.getStaticState.template.isDefined)
            assert(doc.getTemplate ne null)
        }

        // Template stored in the static state because of full updates
        locally {
            val doc = this setupDocument
                <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                         xmlns:xh="http://www.w3.org/1999/xhtml"
                         xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                    <xh:head>
                        <xf:model xxf:noscript="false">
                            <xf:instance id="instance">
                                <value/>
                            </xf:instance>
                        </xf:model>
                    </xh:head>
                    <xh:body>
                        <xf:group xxf:update="full"/>
                    </xh:body>
                </xh:html>

            assert(doc.getStaticState.template.isDefined)
            assert(doc.getTemplate eq null)
        }
    }

    @Test def staticState() {
        val doc = this setupDocument simpleDoc
        val staticStateXML = doc.getStaticState.asInstanceOf[XFormsStaticStateImpl].staticStateDocument.xmlDocument

        // Serialize/deserialize
        val serialized = doc.getStaticState.encodedState
        val restored = XFormsStaticStateImpl.restore(None, serialized)
        val restoredXML = restored.staticStateDocument.xmlDocument

        // Compare expected/actual XML representation of the static state
        assert(Dom4j.compareDocumentsIgnoreNamespacesInScope(staticStateXML, restoredXML))

        // Check other aspects of the restored state
        val part = restored.topLevelPart
        assert(part.hasControls)

        val models = part.getModelsForScope(part.startScope)
        assert(models.size === 1)
        assert(models(0).instances.head._2.staticId === "instance")

        assert(part.getControlAnalysis("input") ne null)
        assert(part.getControlAnalysis("trigger") ne null)
    }
}
