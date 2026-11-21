/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.state

import org.orbeon.dom.tree.CompactElement
import org.orbeon.oxf.cache.MemorySizeEstimator
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.analysis.model.{Instance, InstanceMetadata}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.xbl.GlobalBindingIndex
import org.orbeon.oxf.xforms.{XFormsContainingDocumentBuilder, XFormsGlobalProperties, XFormsStaticStateImpl}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.{SAXStore, SAXStoreBinaryFormat, TransformerUtils}
import org.scalatest.funspec.AnyFunSpecLike
import org.xml.sax.Locator
import org.xml.sax.helpers.AttributesImpl


class StaticStateMemoryFootprintTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("SAXStore buffer trimming and binary deserialization string deduplication") {

    it("must trim backing arrays on endDocument, deduplicate strings on binary read, and allow safe resize") {
      val store = new SAXStore

      var currentLine = 1
      store.setDocumentLocator(new Locator {
        def getPublicId    : String = null
        // Allocate a fresh String each time to verify contiguous systemId deduplication
        def getSystemId    : String = new String("oxf:/forms/acme/order/form.xhtml")
        def getLineNumber  : Int    = currentLine
        def getColumnNumber: Int    = 1
      })

      store.startDocument()
      val rootAtts = new AttributesImpl
      store.startElement("http://www.w3.org/1999/xhtml", "div", "xh:div", rootAtts)

      for (i <- 1 to 150) {
        currentLine = i + 1
        val atts = new AttributesImpl
        atts.addAttribute(new String(""), new String("class"), new String("class"), new String("CDATA"), new String("fr-grid-cell"))
        atts.addAttribute(new String(""), new String("id"), new String("id"), new String("CDATA"), s"cell-$i")
        store.startElement(new String("http://www.w3.org/1999/xhtml"), new String("span"), new String("xh:span"), atts)
        val chars = s"Label $i".toCharArray
        store.characters(chars, 0, chars.length)
        store.endElement(new String("http://www.w3.org/1999/xhtml"), new String("span"), new String("xh:span"))
      }

      store.endElement("http://www.w3.org/1999/xhtml", "div", "xh:div")
      store.endDocument()

      // 1. All 6 backing arrays are trimmed to exact recorded positions
      assert(store.eventBuffer.length          == store.eventBufferPosition)
      assert(store.charBuffer.length           == store.charBufferPosition)
      assert(store.intBuffer.length            == store.intBufferPosition)
      assert(store.lineBuffer.length           == store.lineBufferPosition)
      assert(store.systemIdBuffer.length       == store.systemIdBufferPosition)
      assert(store.attributeCountBuffer.length == store.attributeCountBufferPosition)

      // 2. Contiguous systemId entries share a single String instance
      val distinctSystemIdRefs = (0 until store.systemIdBufferPosition).map(store.systemIdBuffer(_)).map(System.identityHashCode).toSet
      assert(distinctSystemIdRefs.size == 1)

      // 3. Binary round-trip deduplicates repeated element names, attribute names, URIs, "CDATA", and systemIds
      val bytes        = SAXStoreBinaryFormat.serialize(store)
      val deserialized = SAXStoreBinaryFormat.deserialize(bytes)

      val deserializedSysIdRefs =
        (0 until deserialized.systemIdBufferPosition).map(deserialized.systemIdBuffer(_)).map(System.identityHashCode).toSet
      assert(deserializedSysIdRefs.size == 1)

      val cdataRefs =
        (0 until deserialized.stringBuilder.size)
          .map(deserialized.stringBuilder.get)
          .filter(_ == "CDATA")
          .map(System.identityHashCode)
          .toSet
      assert(cdataRefs.size == 1)

      val xhtmlUriRefs =
        (0 until deserialized.stringBuilder.size)
          .map(deserialized.stringBuilder.get)
          .filter(_ == "http://www.w3.org/1999/xhtml")
          .map(System.identityHashCode)
          .toSet
      assert(xhtmlUriRefs.size == 1)

      assertXMLDocumentsIgnoreNamespacesInScope(
        TransformerUtils.saxStoreToOrbeonDomDocument(store),
        TransformerUtils.saxStoreToOrbeonDomDocument(deserialized)
      )

      // 4. Appending to a trimmed SAXStore resizes cleanly without ArrayIndexOutOfBoundsException
      val emptyTrimmed = new SAXStore
      emptyTrimmed.trimToSize()
      assert(emptyTrimmed.eventBuffer.length == 0)
      emptyTrimmed.startDocument()
      emptyTrimmed.startElement("", "root", "root", new AttributesImpl)
      emptyTrimmed.characters("ok".toCharArray, 0, 2)
      emptyTrimmed.endElement("", "root", "root")
      emptyTrimmed.endDocument()
      assert(TransformerUtils.saxStoreToOrbeonDomDocument(emptyTrimmed).getRootElement.getText == "ok")
    }
  }

  describe("XFormsStaticStateImpl memory footprint optimizations (#7252)") {

    it("must detach dead static DOM subtrees, clear transient XPath analysis, and preserve runtime behavior") {
      assume(Version.isPE)

      // Ensure `Instance` does not retain a duplicate immutable `InstanceMetadata` field alongside `_instanceMetadata`
      val metadataFields = classOf[Instance].getDeclaredFields.filter(_.getType == classOf[InstanceMetadata])
      assert(metadataFields.length == 1)

      implicit val indentedLogger: IndentedLogger = XFormsStateManager.newIndentedLogger

      val resourceItems =
        for {
          lang <- List("en", "fr", "de", "es", "it")
          idx  <- 1 to 120
        } yield
          <item id={s"field-$idx"} lang={lang}>
            <label>{s"Field label number $idx in language $lang with descriptive text"}</label>
            <hint>{s"Helpful hint text for field $idx ($lang)"}</hint>
            <help>{s"Extended help content for field $idx ($lang)"}</help>
          </item>

      val formDoc =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xbl="http://www.w3.org/ns/xbl"
          xmlns:acme="http://www.acme.com/xbl"
          xxf:xpath-analysis="true">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <name>Alice</name>
                  </section-1>
                </form>
              </xf:instance>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <application-name>acme</application-name>
                  <form-name>order</form-name>
                  <title xml:lang="en">Order Form</title>
                </metadata>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="true">
                <resources>{resourceItems}</resources>
              </xf:instance>
            </xf:model>
            <xbl:xbl>
              <xbl:binding id="acme-badge" element="acme|badge">
                <xbl:template>
                  <xh:span class="badge"><xf:output id="badge-out" value="'OK'"/></xh:span>
                </xbl:template>
              </xbl:binding>
              <xbl:binding id="acme-unused" element="acme|unused">
                <xbl:template>
                  <xh:div class="unused-large">{(1 to 50).map(i => <xh:span>{s"Unused $i"}</xh:span>)}</xh:div>
                </xbl:template>
              </xbl:binding>
            </xbl:xbl>
          </xh:head>
          <xh:body>
            <xf:group id="full-group" xxf:update="full" context="instance('fr-form-instance')" ref="section-1">
              <xf:input id="name-input" ref="name">
                <xf:label value="instance('fr-form-resources')/item[@id = 'field-1' and @lang = 'en']/label"/>
              </xf:input>
              <acme:badge id="my-badge"/>
            </xf:group>
          </xh:body>
        </xh:html>.toDocument

      val (_, rawStaticState) = PartAnalysisBuilder.createFromDocument(formDoc)
      val staticState = rawStaticState.asInstanceOf[XFormsStaticStateImpl]

      // 1. `<template>`, `<properties>`, and `<xbl:xbl>` are already detached from the retained root control Document
      val rootElem       = staticState.topLevelPart.getTopLevelControls.head.element
      val staticStateDoc = rootElem.getDocument
      assert(staticStateDoc.getRootElement.element("template") eq null)
      assert(rootElem.elements.forall(_.getName != "xbl"))

      // 2. Transient `contextAnalysis` is cleared across all controls after XPath analysis
      assert(staticState.topLevelPart.controlAnalysisMap.values.forall(_.contextAnalysis.isEmpty))
      assert(staticState.template.isDefined)

      // 3. Before `trimAfterServerCompilation()`, `inlineRootElemOpt` is still present for offline compiler compatibility
      val resourcesInst = staticState.topLevelPart.findModel("fr-form-model").get.instances("fr-form-resources")
      assert(resourcesInst.inlineRootElemOpt.isDefined)

      val domBytesBeforeServerTrim = staticState.staticDomSizeBytes
      val sharedRoots = Seq[AnyRef](GlobalBindingIndex.currentIndex, XFormsGlobalProperties)
      val jolBeforeOpt = MemorySizeEstimator.measureExclusiveBytesWithJol(staticState, sharedRoots).map(_._1)

      // 4. Trim server static state: materializes `constantContent` (`TinyTree`), detaches mutable inline instances,
      // and replaces every `ElementAnalysis.element` with a detached `CompactElement`
      staticState.trimAfterServerCompilation()

      assert(resourcesInst.inlineRootElemOpt.isEmpty)
      assert(resourcesInst.element.elements.isEmpty)
      assert(resourcesInst.constantContent.isDefined)

      val formInst = staticState.topLevelPart.findModel("fr-form-model").get.instances("fr-form-instance")
      assert(formInst.inlineRootElemOpt.isDefined)
      assert(formInst.inlineRootElemOpt.get.getDocument eq null)
      assert(formInst.inlineRootElemOpt.get.getParent eq null)
      assert(formInst.inlineRootElemOpt.get.namespaceForPrefix("acme").contains("http://www.acme.com/xbl"))

      assert(staticState.topLevelPart.controlAnalysisMap.values.forall(_.element.isInstanceOf[CompactElement]))
      assert(staticState.topLevelPart.controlAnalysisMap.values.forall(_.element.getDocument eq null))

      val domBytesAfterServerTrim = staticState.staticDomSizeBytes
      assert(
        domBytesAfterServerTrim < domBytesBeforeServerTrim / 10,
        s"Expected >90% DOM reduction after CompactElement + readonly TinyTree trim, got before=$domBytesBeforeServerTrim after=$domBytesAfterServerTrim"
      )

      jolBeforeOpt.foreach { jolBefore =>
        MemorySizeEstimator.measureExclusiveBytesWithJol(staticState, sharedRoots).foreach { case (jolAfter, _) =>
          assert(
            jolAfter < jolBefore,
            s"Expected JOL exclusive footprint to decrease after trimAfterServerCompilation, got before=$jolBefore after=$jolAfter"
          )
        }
      }

      // 5. `formRunnerMetadataOpt` works both directly and after round-tripping through compressed server bytes
      assert(staticState.formRunnerMetadataOpt.contains(("acme", "order", Some("Order Form"))))

      val restored =
        XFormsContainingDocumentBuilder.restoreStaticStateFromBytes(
          Some(staticState.digest),
          staticState.serverEncodedStateBytes
        )
      assert(restored.formRunnerMetadataOpt.contains(("acme", "order", Some("Order Form"))))
      assert(restored.topLevelPart.findModel("fr-form-model").get.instances("fr-form-resources").inlineRootElemOpt.isEmpty)

      // 6. Live document initializes and evaluates readonly `TinyTree` + read-write instances + XBL component cleanly
      withXFormsDocument(formDoc) { _ =>
        assert(getControlValue("name-input") == "Alice")
        assert(getControl("name-input").getLabel(EventCollector.Throw).contains("Field label number 1 in language en with descriptive text"))
        assert(getControlValue("my-badge≡badge-out") == "OK")
      }
    }

    it("must preserve `_xblIndex` on mutable `xxf:dynamic` parts so incremental XBL shadow-tree updates bind nested components") {
      val hostDoc =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xbl="http://www.w3.org/ns/xbl"
          xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:acme="http://acme.org/xbl">
          <xh:head>
            <xf:model id="outer-model">
              <xf:instance id="dyn-form">
                <xh:html>
                  <xh:head>
                    <xf:model id="inner-model">
                      <xf:instance id="inner-data"><data/></xf:instance>
                    </xf:model>
                    <xbl:xbl>
                      <xbl:binding id="acme-box" element="acme|box">
                        <xbl:template xxbl:transform="oxf:unsafe-xslt">
                          <xh:div class="acme-box" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xsl:version="2.0">
                            <xsl:copy-of select="/*/*"/>
                          </xh:div>
                        </xbl:template>
                      </xbl:binding>
                      <xbl:binding id="acme-cell" element="acme|cell">
                        <xbl:template>
                          <xh:div class="acme-cell"><xf:output id="cell-out" value="'BOUND'"/></xh:div>
                        </xbl:template>
                      </xbl:binding>
                    </xbl:xbl>
                  </xh:head>
                  <xh:body>
                    <acme:box id="box-1"/>
                  </xh:body>
                </xh:html>
              </xf:instance>
              <xf:instance id="cell-template">
                <acme:cell id="cell-1"/>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xxf:dynamic id="dyn" ref="instance('dyn-form')"/>
            <xf:trigger id="insert-cell">
              <xf:label>Insert Cell</xf:label>
              <xf:insert
                event="DOMActivate"
                context="instance('dyn-form')/xh:body/acme:box"
                origin="instance('cell-template')"/>
            </xf:trigger>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { implicit ec =>
        withXFormsDocument(hostDoc) { implicit doc =>
          // Mutating `<acme:box>` in `dyn-form` triggers `XXFormsDynamicControl.processXBLUpdates` ->
          // `createOrUpdateStaticShadowTree` after initial `freeTransientState()` has already run.
          activateControlWithEvent("insert-cell")
          assert(getControlValue("dyn≡box-1≡cell-1≡cell-out") == "BOUND")
        }
      }
    }

    it("must preserve ancestor xml:base, literal xf:property text, xxf:control-element, and inline xs:schema after CompactElement trim") {
      val docDom =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xml:base="http://example.org/app/">
          <xh:head>
            <xf:model id="main-model">
              <xs:schema targetNamespace="">
                <xs:simpleType name="code3">
                  <xs:restriction base="xs:string">
                    <xs:length value="3"/>
                  </xs:restriction>
                </xs:simpleType>
              </xs:schema>
              <xf:instance id="main-instance">
                <data>
                  <code>ABCD</code>
                  <prop-out/>
                  <ctrl-att/>
                </data>
              </xf:instance>
              <xf:bind ref="code" type="code3"/>
              <xf:action event="my-custom-event">
                <xf:setvalue ref="prop-out" value="event('greeting')"/>
                <xf:setvalue ref="ctrl-att" value="xxf:control-element('code-input')/@class"/>
              </xf:action>
            </xf:model>
          </xh:head>
          <xh:body xml:base="forms/v1/">
            <xf:input id="code-input" ref="code" class="code-field"/>
            <xf:output id="prop-out-ctrl" ref="prop-out"/>
            <xf:output id="ctrl-att-ctrl" ref="ctrl-att"/>
            <xf:trigger id="fire-btn">
              <xf:label>Fire</xf:label>
              <xf:dispatch event="DOMActivate" name="my-custom-event" targetid="main-model">
                <xf:property name="greeting">Hello from CompactElement</xf:property>
              </xf:dispatch>
            </xf:trigger>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { implicit ec =>
        withXFormsDocument(docDom) { implicit doc =>
          // 1. Model with inline `<xs:schema>` retains schema validation (`"ABCD"` fails length=3)
          assert(! isValid("code-input"))

          setControlValueWithEventSearchNested("code-input", "XYZ")
          assert(isValid("code-input"))

          // 2. Ancestor `xml:base` (`http://example.org/app/` + `forms/v1/`) was pre-resolved onto `CompactElement`
          val codeElem = doc.staticOps.findControlAnalysis("code-input").get.element
          assert(codeElem.isInstanceOf[CompactElement])
          assert(doc.resolveXMLBase(codeElem, "lookup.xml").toString == "http://example.org/app/forms/v1/lookup.xml")

          // 3. Literal `<xf:property>` text and `xxf:control-element` work on `CompactElement`
          activateControlWithEvent("fire-btn")
          assert(getControlValue("prop-out-ctrl") == "Hello from CompactElement")
          assert(getControlValue("ctrl-att-ctrl") == "code-field")
        }
      }
    }
  }
}
