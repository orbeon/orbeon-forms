/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.StreamedContent
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XMLSupport}
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId
import org.scalatest.funspec.AnyFunSpecLike

class SimpleDataMigrationTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with XMLSupport {

  ignore("Simple data migration") {

    val ExpectedFormData: NodeInfo =
      <form>
        <section-2>
            <s1input1/>
        </section-2>
        <section-1>
            <section-1-iteration>
                <s2field1/>
                <grid-2>
                    <grid-2-iteration>
                        <s2attachment filename="" mediatype="" size=""/>
                        <s2number/>
                    </grid-2-iteration>
                    <grid-2-iteration>
                        <s2attachment filename="" mediatype="" size=""/>
                        <s2number/>
                    </grid-2-iteration>
                </grid-2>
            </section-1-iteration>
            <section-1-iteration>
                <s2field1/>
                <grid-2>
                    <grid-2-iteration>
                        <s2attachment filename="" mediatype="" size=""/>
                        <s2number/>
                    </grid-2-iteration>
                    <grid-2-iteration>
                        <s2attachment filename="" mediatype="" size=""/>
                        <s2number/>
                    </grid-2-iteration>
                    <grid-2-iteration>
                        <s2attachment filename="" mediatype="" size=""/>
                        <s2number/>
                    </grid-2-iteration>
                </grid-2>
            </section-1-iteration>
        </section-1>
        <section-4>
            <st1field1/>
            <st1image1 filename="" mediatype="" size=""/>
            <st1num1/>
            <st1grid2>
                <st1grid2-iteration>
                    <st1area1/>
                    <st1currency1/>
                </st1grid2-iteration>
            </st1grid2>
        </section-4>
        <section-3>
            <st1field1/>
            <st1image1 filename="" mediatype="" size=""/>
            <st1num1/>
            <st1grid2>
                <st1grid2-iteration>
                    <st1area1/>
                    <st1currency1/>
                </st1grid2-iteration>
            </st1grid2>
        </section-3>
        <section-5>
          <!-- This needs to be the correct template. -->
          <st1image1/>
        </section-5>
    </form>

    val IncomingFormData: NodeInfo =
      <form>
        <section-2>
            <s1input1/>
            <extra1/>
        </section-2>
        <!-- The entire section must be removed -->
        <extra2>
          <extra2-iteration/>
          <extra2-iteration/>
        </extra2>
        <section-1>
            <section-1-iteration>
                <s2field1/>
                <grid-2>
                    <grid-2-iteration>
                        <s2number/>
                        <extra3/>
                    </grid-2-iteration>
                    <grid-2-iteration>
                        <s2number/>
                    </grid-2-iteration>
                </grid-2>
            </section-1-iteration>
            <section-1-iteration>
                <s2field1/>
                <grid-2>
                    <grid-2-iteration>
                        <s2number/>
                    </grid-2-iteration>
                    <grid-2-iteration>
                        <s2number/>
                        <extra4/>
                    </grid-2-iteration>
                    <grid-2-iteration>
                        <s2number/>
                        <extra4/>
                    </grid-2-iteration>
                </grid-2>
            </section-1-iteration>
        </section-1>
        <!-- Section templates are also missing fields -->
        <section-4>
            <st1field1/>
            <st1num1/>
            <extra5/>
            <st1grid2>
                <st1grid2-iteration>
                </st1grid2-iteration>
            </st1grid2>
        </section-4>
        <section-3>
            <st1field1/>
            <st1num1/>
            <extra5/>
            <st1grid2>
                <st1grid2-iteration>
                    <extra6/>
                </st1grid2-iteration>
            </st1grid2>
        </section-3>
        <!-- Here this should contain `st1image1`, but it matches a different element template from
             the section template which as attributes. We need to pick the correct template. -->
        <section-5/>
    </form>

    val contentToPost =
      StreamedContent.fromBytes(
        TransformerUtils.tinyTreeToString(IncomingFormData).getBytes(ExternalContext.StandardCharacterEncoding),
        Some(ContentTypes.XmlContentType)
      )

    val (processorService, docOpt, _) =
      runFormRunner(
        app        = "tests",
        form       = "data-migration",
        mode       = "new",
        document   = "",
        query      = List(FormRunnerPersistence.DataFormatVersionName -> DataFormatVersion.Edge.entryName),
        initialize = true,
        content    = Some(contentToPost)
      )

    val doc = docOpt.get

    it("must pass migration of elements in main form and section templates") {
      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {
          assertXMLDocumentsIgnoreNamespacesInScope(
            left  = ExpectedFormData.root,
            right = doc.findObjectByEffectiveId(Names.FormInstance).get.asInstanceOf[XFormsInstance].root
          )
        }
      }
    }
  }
}
