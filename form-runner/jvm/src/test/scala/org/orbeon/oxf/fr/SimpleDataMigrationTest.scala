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

import cats.syntax.option._
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.SimpleDataMigration.DataMigrationBehavior
import org.orbeon.oxf.fr.SimpleDataMigration.FormDiff
import org.orbeon.oxf.fr.importexport.FormDefinitionOps
import org.orbeon.oxf.http.StreamedContent
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XMLSupport}
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike


class SimpleDataMigrationTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with XMLSupport {

  def runFormRunnerAndAssertData(
    incomingData         : (NodeInfo, DataFormatVersion),
    expectedDataOpt      : Option[NodeInfo],
    formName             : String,
    dataMigrationBehavior: DataMigrationBehavior
  ): Unit = {

    def contentToPost(data: NodeInfo): StreamedContent =
      StreamedContent.fromBytes(
        TransformerUtils.tinyTreeToString(data).getBytes(ExternalContext.StandardCharacterEncoding),
        Some(ContentTypes.XmlContentType)
      )

    val (processorService, docOpt, _) =
      runFormRunner(
        app        = "tests",
        form       = formName,
        mode       = "new",
        document   = "",
        query      = List(
                       FormRunnerPersistence.DataFormatVersionName     -> incomingData._2.entryName,
                       FormRunnerPersistence.DataMigrationBehaviorName -> dataMigrationBehavior.entryName,
                     ),
        initialize = true,
        content    = contentToPost(incomingData._1).some
      )

    expectedDataOpt match {
      case None =>
        assert(docOpt.isEmpty)
      case Some(expectedData) =>
        assert(docOpt.nonEmpty)
        val doc = docOpt.get
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assertXMLDocumentsIgnoreNamespacesInScope(
              left  = expectedData.root,
              right = doc.findObjectByEffectiveId(Names.FormInstance).get.asInstanceOf[XFormsInstance].root
            )
          }
        }
    }
  }

  ignore("Simple data migration") {

    val IncomingFormData: NodeInfo =
      <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.8.0">
        <section-2>
          <s1input1/>
        </section-2>
        <!-- The entire section must be removed -->
        <section-1>
          <section-1-iteration>
            <s2field1/>
            <grid-2>
              <grid-2-iteration>
                <s2number/>
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
              </grid-2-iteration>
              <grid-2-iteration>
                <s2number/>
              </grid-2-iteration>
            </grid-2>
          </section-1-iteration>
        </section-1>
        <!-- Section templates are also missing fields -->
        <section-4>
          <st1field1/>
          <st1num1/>
          <st1grid2>
            <st1grid2-iteration>
            </st1grid2-iteration>
          </st1grid2>
        </section-4>
        <section-3>
          <st1field1/>
          <st1num1/>
          <st1grid2>
            <st1grid2-iteration>
            </st1grid2-iteration>
          </st1grid2>
        </section-3>
        <!-- Here this should contain `st1image1`, but it matches a different element template from
               the section template which as attributes. We need to pick the correct template. -->
        <section-5/>
        <fr:metadata xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>
      </form>

    val ExpectedFormData: NodeInfo =
      <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.8.0">
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
          <fr:metadata xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>
      </form>

    val IncomingFormDataWithExtra: NodeInfo =
      <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.8.0">
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
          <fr:metadata xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>
      </form>

    val IncomingFormDataWithMoves: NodeInfo =
      <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
        <s1>
          <s1g1c1>s1g1c1value</s1g1c1>
          <s1g1c2>s1g1c2value</s1g1c2>
        </s1>
        <s2>
          <s2g1c1>s2g1c1value</s2g1c1>
        </s2>
        <s3>
          <s3g1>
            <s3g1c1>s3g1c1value1</s3g1c1>
          </s3g1>
          <s3g1>
            <s3g1c1>s3g1c1value2</s3g1c1>
          </s3g1>
        </s3>
        <s4>
          <s4-iteration>
            <s4g1c1>s4g1c1value1</s4g1c1>
            <s4g1c2>s4g1c2value1</s4g1c2>
          </s4-iteration>
          <s4-iteration>
            <s4g1c1>s4g1c1value2</s4g1c1>
            <s4g1c2>s4g1c2value2</s4g1c2>
          </s4-iteration>
        </s4>
      </form>

    val ExpectedFormDataWithMoves: NodeInfo =
      <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="2019.1.0">
        <s1>
          <s1g1/>
          <s1g2>
            <s1g1c1>s1g1c1value</s1g1c1>
          </s1g2>
        </s1>
        <s2>
          <s2g1>
            <s2g1c1>s2g1c1value</s2g1c1>
            <s1g1c2>s1g1c2value</s1g1c2>
          </s2g1>
        </s2>
        <s3>
          <s3g1>
            <s3g1-iteration>
              <s3g1c1>s3g1c1value1</s3g1c1>
            </s3g1-iteration>
            <s3g1-iteration>
              <s3g1c1>s3g1c1value2</s3g1c1>
            </s3g1-iteration>
          </s3g1>
        </s3>
        <s4>
          <s4-iteration>
            <s4g1>
              <s4g1c2>s4g1c2value1</s4g1c2>
            </s4g1>
            <s4g2>
              <s4g1c1>s4g1c1value1</s4g1c1>
            </s4g2>
          </s4-iteration>
          <s4-iteration>
            <s4g1>
              <s4g1c2>s4g1c2value2</s4g1c2>
            </s4g1>
            <s4g2>
              <s4g1c1>s4g1c1value2</s4g1c1>
            </s4g2>
          </s4-iteration>
        </s4>
      </form>

    val Expected = List(
      ("migration of elements in main form and section templates with holes", "data-migration",          IncomingFormData          -> DataFormatVersion.V480, DataMigrationBehavior.HolesOnly, ExpectedFormData.some),
      ("migration disallowed when extra elements are present"               , "data-migration",          IncomingFormDataWithExtra -> DataFormatVersion.V480, DataMigrationBehavior.HolesOnly, None), // https://github.com/orbeon/orbeon-forms/issues/5217
      ("migration with moves including in repeats"                          , "data-migration-improved", IncomingFormDataWithMoves -> DataFormatVersion.V400, DataMigrationBehavior.Enabled,   ExpectedFormDataWithMoves.some),
    )

    for ((desc, formName, incomingData, migrationBehavior, expectedDataOpt) <- Expected)
      it(desc) {
        runFormRunnerAndAssertData(
          incomingData,
          expectedDataOpt,
          formName,
          migrationBehavior
        )
      }
  }

  describe("Find form definition format") {

    val Expected = List(
      (Nil,                              None),
      (List("2018.2.2.201903012338 PE"), Some(DataFormatVersion.V480)),
      (List("2019.1.0.201910220207 PE"), Some(DataFormatVersion.V20191)),
      (List("2020.1.202012300129 PE"),   Some(DataFormatVersion.V20191)),
      (
        List(
          "2018.2.2.201903012338 PE",
          "2019.1.0.201910220207 PE",
          "2020.1.202012300129 PE"
        ),
        Some(DataFormatVersion.V20191)
      ),
    )

    for ((versions, expected) <- Expected)
      it(s"must pass for $versions") {
        assert(FormRunnerPersistence.findFormDefinitionFormatFromStringVersions(versions) == expected)
      }
  }

  val FormDefinition: NodeInfo =
    <xh:html
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xf="http://www.w3.org/2002/xforms">
        <xh:head>
            <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">
                <xf:instance id="fr-form-instance" xxf:exclude-result-prefixes="#all" xxf:index="id">
                    <form>
                        <my-section-1>
                            <s1input1/>
                        </my-section-1>
                        <my-section-2>
                            <my-section-2-iteration>
                                <s2field1/>
                                <my-repeated-grid>
                                    <my-repeated-grid-iteration>
                                        <s2attachment filename="" mediatype="" size=""/>
                                        <s2number>42</s2number>
                                    </my-repeated-grid-iteration>
                                    <my-repeated-grid-iteration>
                                        <s2attachment filename="" mediatype="" size=""/>
                                        <s2number/>
                                    </my-repeated-grid-iteration>
                                </my-repeated-grid>
                            </my-section-2-iteration>
                        </my-section-2>
                    </form>
                </xf:instance>
                <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                    <xf:bind id="my-section-1-bind" ref="my-section-1" name="my-section-1">
                        <xf:bind id="s1input1-bind" ref="s1input1" name="s1input1" xxf:whitespace="trim"/>
                    </xf:bind>
                    <xf:bind id="my-section-2-bind" name="my-section-2" ref="my-section-2">
                        <xf:bind id="my-section-2-iteration-bind" ref="my-section-2-iteration" name="my-section-2-iteration">
                            <xf:bind id="s2field1-bind" ref="s2field1" name="s2field1" xxf:whitespace="trim"/>
                            <xf:bind id="my-repeated-grid-bind" ref="my-repeated-grid" name="my-repeated-grid">
                                <xf:bind id="my-repeated-grid-iteration-bind" ref="my-repeated-grid-iteration" name="my-repeated-grid-iteration">
                                    <xf:bind id="s2attachment-bind" ref="s2attachment" name="s2attachment" type="xf:anyURI"/>
                                    <xf:bind id="s2number-bind" ref="s2number" name="s2number" type="xf:decimal"/>
                                </xf:bind>
                            </xf:bind>
                        </xf:bind>
                    </xf:bind>
                </xf:bind>
                <xf:instance xxf:readonly="true" id="fr-form-metadata" xxf:exclude-result-prefixes="#all">
                    <metadata>
                        <application-name>orbeon</application-name>
                        <form-name>issue1523</form-name>
                        <title xml:lang="en">Versioning: support basic data migration #1523</title>
                        <description xml:lang="en"/>
                        <wizard>false</wizard>
                        <data-migration>enabled</data-migration>
                        <migration version="4.8.0">[{{ "path": "my-section-2/my-section-2-iteration/my-repeated-grid", "iteration-name":
                            "my-repeated-grid-iteration" }},{{ "path": "section-4/st1grid2", "iteration-name": "st1grid2-iteration" }},{{
                            "path": "section-3/st1grid2", "iteration-name": "st1grid2-iteration" }}]
                        </migration>
                    </metadata>
                </xf:instance>
                <xf:instance xxf:readonly="true" xxf:exclude-result-prefixes="#all" id="my-repeated-grid-template">
                    <my-repeated-grid-iteration>
                        <s2attachment filename="" mediatype="" size=""/>
                        <s2number/>
                    </my-repeated-grid-iteration>
                </xf:instance>
                <xf:instance xxf:readonly="true" xxf:exclude-result-prefixes="#all" id="my-section-2-template">
                    <my-section-2-iteration>
                        <s2field1/>
                        <my-repeated-grid>
                            <my-repeated-grid-iteration>
                                <s2attachment filename="" mediatype="" size=""/>
                                <s2number>43</s2number>
                            </my-repeated-grid-iteration>
                            <my-repeated-grid-iteration>
                                <s2attachment filename="" mediatype="" size=""/>
                                <s2number/>
                            </my-repeated-grid-iteration>
                        </my-repeated-grid>
                    </my-section-2-iteration>
                </xf:instance>
            </xf:model>
        </xh:head>
        <xh:body>
            <fr:view>
                <fr:body/>
            </fr:view>
        </xh:body>
    </xh:html>

  describe("XML merge") {

    val NewFormData: NodeInfo =
      <form>
          <my-section-1>
              <s1input1/>
          </my-section-1>
          <my-section-2>
              <my-section-2-iteration>
                  <s2field1/>
                  <my-repeated-grid>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number></s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>44</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>45</s2number>
                      </my-repeated-grid-iteration>
                  </my-repeated-grid>
              </my-section-2-iteration>
              <my-section-2-iteration>
                  <s2field1/>
                  <my-repeated-grid>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number/>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>47</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>48</s2number>
                      </my-repeated-grid-iteration>
                    <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>49</s2number>
                      </my-repeated-grid-iteration>
                  </my-repeated-grid>
              </my-section-2-iteration>
          </my-section-2>
      </form>

    val ExpectedFormData: NodeInfo =
      <form>
          <my-section-1>
              <s1input1/>
          </my-section-1>
          <my-section-2>
              <my-section-2-iteration>
                  <s2field1/>
                  <my-repeated-grid>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>42</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>44</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>45</s2number>
                      </my-repeated-grid-iteration>
                  </my-repeated-grid>
              </my-section-2-iteration>
              <my-section-2-iteration>
                  <s2field1/>
                  <my-repeated-grid>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number/>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>47</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>48</s2number>
                      </my-repeated-grid-iteration>
                    <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>49</s2number>
                      </my-repeated-grid-iteration>
                  </my-repeated-grid>
              </my-section-2-iteration>
          </my-section-2>
      </form>

    val formOps = new FormDefinitionOps(FormDefinition)

    val ctx = new InDocFormRunnerDocContext(FormDefinition.rootElement)

    val result = TransformerUtils.extractAsMutableDocument(ctx.dataRootElem)

    val counts =
      SimpleDataMigration.mergeXmlFromBindSchema(
        srcDocRootElem           = NewFormData.rootElement,
        dstDocRootElem           = result.rootElement,
        isElementReadonly        = _ => false,
        ignoreBlankData          = true,
        allowMissingElemInSource = false)(
        formOps                  = formOps
      )

    it("must pass migration of elements in main form and section templates") {

      assert((17, 12) == counts)

      assertXMLDocumentsIgnoreNamespacesInScope(
        left  = ExpectedFormData.root,
        right = result
      )
    }
  }

  describe("XML diff") {

    val OldFormData: NodeInfo =
      <form>
          <my-section-1>
              <s1input1/>
          </my-section-1>
          <my-section-2>
              <my-section-2-iteration>
                  <s2field1/>
                  <my-repeated-grid>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>11</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>12</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>13</s2number>
                      </my-repeated-grid-iteration>
                  </my-repeated-grid>
              </my-section-2-iteration>
              <my-section-2-iteration>
                  <s2field1/>
                  <my-repeated-grid>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>21</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>22</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>23</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>24</s2number>
                      </my-repeated-grid-iteration>
                  </my-repeated-grid>
              </my-section-2-iteration>
          </my-section-2>
      </form>

    val NewFormData: NodeInfo =
      <form>
          <my-section-1>
              <s1input1>New s1input1 value </s1input1>
          </my-section-1>
          <my-section-2>
              <my-section-2-iteration>
                  <s2field1/>
                  <my-repeated-grid>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number></s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>12</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>13</s2number>
                      </my-repeated-grid-iteration>
                      <my-repeated-grid-iteration>
                          <s2attachment filename="" mediatype="" size=""/>
                          <s2number>14</s2number>
                      </my-repeated-grid-iteration>
                  </my-repeated-grid>
              </my-section-2-iteration>
          </my-section-2>
      </form>

    it("must list the correct diffs") {

      val Expected =
        List(
          FormDiff.ValueChanged    ("s1input1"),
          FormDiff.IterationRemoved("my-section-2",     1),
          FormDiff.IterationAdded  ("my-repeated-grid", 1),
          FormDiff.ValueChanged    ("s2number")
        )

      val formOps = new FormDefinitionOps(FormDefinition)

      val diffs =
        SimpleDataMigration.diffSimilarXmlData(
          srcDocRootElem    = OldFormData.rootElement,
          dstDocRootElem    = NewFormData.rootElement,
          isElementReadonly = _ => false)(
          formOps           = formOps)(
          mapBind           = formOps.bindNameOpt(_).getOrElse("[unknown]")
        )

      assert(Expected == diffs)
    }
  }
}
