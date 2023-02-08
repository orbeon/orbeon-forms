package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.IndexedControl
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.scaxon.NodeConversions._
import org.scalatest.funspec.AnyFunSpecLike

class IndexTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("The `findIndexedControls` function") {

    val formElem48 =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
               xmlns:xf="http://www.w3.org/2002/xforms"
               xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
               xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
               xmlns:fb="http://orbeon.org/oxf/xml/form-builder">
        <xh:head>
          <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">

            <xf:instance id="fr-form-instance" xxf:exclude-result-prefixes="#all" xxf:index="id">
              <form>
                <my-section>
                  <in-summary/>
                  <in-search/>
                  <in-both/>
                  <in-none/>
                  <date/>
                  <repeat>
                    <repeat-iteration>
                      <in-repeat/>
                    </repeat-iteration>
                  </repeat>
                </my-section>
              </form>
            </xf:instance>

            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
              <xf:bind id="my-section-bind" name="my-section" ref="my-section">
                <xf:bind id="in-summary-bind" name="in-summary" ref="in-summary" xxf:whitespace="trim"/>
                <xf:bind id="in-search-bind" ref="in-search" name="in-search" xxf:whitespace="trim"/>
                <xf:bind id="in-both-bind" ref="in-both" name="in-both" xxf:whitespace="trim"/>
                <xf:bind id="in-none-bind" ref="in-none" name="in-none" xxf:whitespace="trim"/>
                <xf:bind id="date-bind" ref="date" name="date" xxf:whitespace="trim" type="xf:date"/>
                <xf:bind id="repeat-bind" ref="repeat" name="repeat">
                  <xf:bind id="repeat-iteration-bind" ref="repeat-iteration" name="repeat-iteration">
                    <xf:bind id="in-repeat-bind" ref="in-repeat" name="in-repeat" xxf:whitespace="trim"/>
                  </xf:bind>
                </xf:bind>
              </xf:bind>
            </xf:bind>

            <xf:instance id="fr-form-metadata" xxf:readonly="true" xxf:exclude-result-prefixes="#all">
              <metadata>
                <application-name>test</application-name>
                <form-name>index</form-name>
                <title xml:lang="en">Untitled Form</title>
                <description xml:lang="en"/>
                <created-with-version>2018.2.3.201905172253 PE</created-with-version>
                <migration version="4.8.0">
                  [
                    {{
                      "path": "my-section/repeat",
                      "iteration-name": "repeat-iteration"
                    }}
                  ]
                </migration>
              </metadata>
            </xf:instance>

            <xf:instance id="fr-form-attachments" xxf:exclude-result-prefixes="#all">
              <attachments/>
            </xf:instance>

            <xf:instance xxf:readonly="true" id="fr-form-resources" xxf:exclude-result-prefixes="#all">
              <resources>
                <resource xml:lang="en">
                </resource>
              </resources>
            </xf:instance>
            <xf:instance xxf:readonly="true" id="repeat-template" xxf:exclude-result-prefixes="#all">
              <repeat-iteration>
                <in-repeat/>
              </repeat-iteration>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <fr:view>
            <fr:body>
              <fr:section id="my-section-section" bind="my-section-bind">
                <xf:label ref="$form-resources/my-section/label"/>
                <fr:grid id="my-grid-grid">
                  <fr:c y="1" x="1" w="6">
                    <xf:input id="in-summary-control" bind="in-summary-bind" class="fr-summary">
                      <xf:label ref="$form-resources/in-summary/label" mediatype="text/html"/>
                      <xf:hint ref="$form-resources/in-summary/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c y="1" x="7" w="6">
                    <xf:input id="in-search-control" bind="in-search-bind" class="fr-search">
                      <xf:label ref="$form-resources/in-search/label"/>
                      <xf:hint ref="$form-resources/in-search/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="2" w="6">
                    <xf:input id="in-both-control" bind="in-both-bind" class="fr-summary fr-search">
                      <xf:label ref="$form-resources/in-both/label"/>
                      <xf:hint ref="$form-resources/in-both/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="7" y="2" w="6">
                    <xf:input id="in-none-control" bind="in-none-bind">
                      <xf:label ref="$form-resources/in-none/label"/>
                      <xf:hint ref="$form-resources/in-none/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="3" w="6">
                    <fr:date id="date-control" bind="date-bind" class="fr-summary fr-search">
                      <xf:label ref="$form-resources/date/label"/>
                      <xf:hint ref="$form-resources/date/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </fr:date>
                  </fr:c>
                  <fr:c x="7" y="3" w="6"/>
                </fr:grid>
                <fr:grid id="repeat-grid" bind="repeat-bind" repeat="content" min="1"
                         template="instance('repeat-template')"
                         apply-defaults="true"
                         fb:initial-iterations="first">
                  <fr:c x="1" y="1" w="6">
                    <xf:input id="in-repeat-control" bind="in-repeat-bind" class="fr-summary fr-search">
                      <xf:label ref="$form-resources/in-repeat/label"/>
                      <xf:hint ref="$form-resources/in-repeat/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="7" y="1" w="6"/>
                </fr:grid>
              </fr:section>
            </fr:body>
          </fr:view>
        </xh:body>
      </xh:html>

    val formElem20191=
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
               xmlns:xf="http://www.w3.org/2002/xforms"
               xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
               xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
               xmlns:fb="http://orbeon.org/oxf/xml/form-builder">
        <xh:head>
          <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">

            <xf:instance id="fr-form-instance" xxf:exclude-result-prefixes="#all" xxf:index="id">
              <form>
                <my-section>
                  <my-grid>
                    <in-summary/>
                    <in-search/>
                    <in-both/>
                    <in-none/>
                    <date/>
                  </my-grid>
                  <repeat>
                    <repeat-iteration>
                      <in-repeat/>
                    </repeat-iteration>
                  </repeat>
                </my-section>
              </form>
            </xf:instance>

            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
              <xf:bind id="my-section-bind" name="my-section" ref="my-section">
                <xf:bind id="my-grid-bind" ref="my-grid" name="my-grid">
                  <xf:bind id="in-summary-bind" name="in-summary" ref="in-summary" xxf:whitespace="trim"/>
                  <xf:bind id="in-search-bind" ref="in-search" name="in-search" xxf:whitespace="trim"/>
                  <xf:bind id="in-both-bind" ref="in-both" name="in-both" xxf:whitespace="trim"/>
                  <xf:bind id="in-none-bind" ref="in-none" name="in-none" xxf:whitespace="trim"/>
                  <xf:bind id="date-bind" ref="date" name="date" xxf:whitespace="trim" type="xf:date"/>
                </xf:bind>
                <xf:bind id="repeat-bind" ref="repeat" name="repeat">
                  <xf:bind id="repeat-iteration-bind" ref="repeat-iteration" name="repeat-iteration">
                    <xf:bind id="in-repeat-bind" ref="in-repeat" name="in-repeat" xxf:whitespace="trim"/>
                  </xf:bind>
                </xf:bind>
              </xf:bind>
            </xf:bind>

            <xf:instance id="fr-form-metadata" xxf:readonly="true" xxf:exclude-result-prefixes="#all">
              <metadata>
                <application-name>test</application-name>
                <form-name>index</form-name>
                <title xml:lang="en">Untitled Form</title>
                <description xml:lang="en"/>
                <created-with-version>2018.2.3.201905172253 PE</created-with-version>
                <migration version="4.8.0">
                  [
                    {{
                    "path": "my-section/repeat",
                    "iteration-name": "repeat-iteration"
                    }}
                  ]
                </migration>
                <migration version="2019.1.0">
                  {{
                    "migrations": [
                      {{
                        "containerPath": [
                          {{
                            "value": "my-section"
                          }}
                        ],
                        "newGridElem": {{
                          "value": "my-grid"
                        }},
                        "afterElem": null,
                        "content": [
                          {{
                            "value": "in-summary"
                          }},
                          {{
                            "value": "in-search"
                          }},
                          {{
                            "value": "in-both"
                          }},
                          {{
                            "value": "in-none"
                          }},
                          {{
                            "value": "date"
                          }}
                        ],
                        "topLevel" : true
                      }}
                    ]
                  }}
                </migration>
              </metadata>
            </xf:instance>

            <xf:instance id="fr-form-attachments" xxf:exclude-result-prefixes="#all">
              <attachments/>
            </xf:instance>

            <xf:instance xxf:readonly="true" id="fr-form-resources" xxf:exclude-result-prefixes="#all">
              <resources>
                <resource xml:lang="en">
                </resource>
              </resources>
            </xf:instance>
            <xf:instance xxf:readonly="true" xxf:exclude-result-prefixes="#all" id="repeat-template">
              <repeat-iteration>
                <in-repeat/>
              </repeat-iteration>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <fr:view>
            <fr:body>
              <fr:section id="my-section-section" bind="my-section-bind">
                <xf:label ref="$form-resources/my-section/label"/>
                <fr:grid id="my-grid-grid" bind="my-grid-bind">
                  <fr:c y="1" x="1" w="6">
                    <xf:input id="in-summary-control" bind="in-summary-bind" class="fr-summary">
                      <xf:label ref="$form-resources/in-summary/label" mediatype="text/html"/>
                      <xf:hint ref="$form-resources/in-summary/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c y="1" x="7" w="6">
                    <xf:input id="in-search-control" bind="in-search-bind" class="fr-search">
                      <xf:label ref="$form-resources/in-search/label"/>
                      <xf:hint ref="$form-resources/in-search/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="2" w="6">
                    <xf:input id="in-both-control" bind="in-both-bind" class="fr-summary fr-search">
                      <xf:label ref="$form-resources/in-both/label"/>
                      <xf:hint ref="$form-resources/in-both/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="7" y="2" w="6">
                    <xf:input id="in-none-control" bind="in-none-bind">
                      <xf:label ref="$form-resources/in-none/label"/>
                      <xf:hint ref="$form-resources/in-none/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="3" w="6">
                    <fr:date id="date-control" bind="date-bind" class="fr-summary fr-search">
                      <xf:label ref="$form-resources/date/label"/>
                      <xf:hint ref="$form-resources/date/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </fr:date>
                  </fr:c>
                  <fr:c x="7" y="3" w="6"/>
                </fr:grid>
                <fr:grid id="repeat-grid" bind="repeat-bind" repeat="content" min="1"
                         template="instance('repeat-template')"
                         apply-defaults="true"
                         fb:initial-iterations="first">
                  <fr:c x="1" y="1" w="6">
                    <xf:input id="in-repeat-control" bind="in-repeat-bind" class="fr-summary fr-search">
                      <xf:label ref="$form-resources/in-repeat/label"/>
                      <xf:hint ref="$form-resources/in-repeat/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="7" y="1" w="6"/>
                </fr:grid>
              </fr:section>
            </fr:body>
          </fr:view>
        </xh:body>
      </xh:html>

    val expected40 =
      List(
        IndexedControl("in-summary", inSearch = false, inSummary = true,  "my-section/in-summary",                        "xs:string", "input", htmlLabel = true,  resources = Nil),
        IndexedControl("in-search",  inSearch = true,  inSummary = false, "my-section/in-search",                         "xs:string", "input", htmlLabel = false, resources = Nil),
        IndexedControl("in-both",    inSearch = true,  inSummary = true,  "my-section/in-both",                           "xs:string", "input", htmlLabel = false, resources = Nil),
        IndexedControl("date",       inSearch = true,  inSummary = true,  "my-section/date",                              "xf:date"  , "date",  htmlLabel = false, resources = Nil),
        IndexedControl("in-repeat",  inSearch = true,  inSummary = true,  "my-section/repeat/in-repeat",                  "xs:string", "input", htmlLabel = false, resources = Nil)
      )

    val expected48 =
      List(
        IndexedControl("in-summary", inSearch = false, inSummary = true,  "my-section/in-summary",                        "xs:string", "input", htmlLabel = true,  resources = Nil),
        IndexedControl("in-search",  inSearch = true,  inSummary = false, "my-section/in-search",                         "xs:string", "input", htmlLabel = false, resources = Nil),
        IndexedControl("in-both",    inSearch = true,  inSummary = true,  "my-section/in-both",                           "xs:string", "input", htmlLabel = false, resources = Nil),
        IndexedControl("date",       inSearch = true,  inSummary = true,  "my-section/date",                              "xf:date"  , "date",  htmlLabel = false, resources = Nil),
        IndexedControl("in-repeat",  inSearch = true,  inSummary = true,  "my-section/repeat/repeat-iteration/in-repeat", "xs:string", "input", htmlLabel = false, resources = Nil)
      )

    val expected20191 =
      List(
        IndexedControl("in-summary", inSearch = false, inSummary = true,  "my-section/my-grid/in-summary",                "xs:string", "input", htmlLabel = true,  resources = Nil),
        IndexedControl("in-search",  inSearch = true,  inSummary = false, "my-section/my-grid/in-search",                 "xs:string", "input", htmlLabel = false, resources = Nil),
        IndexedControl("in-both",    inSearch = true,  inSummary = true,  "my-section/my-grid/in-both",                   "xs:string", "input", htmlLabel = false, resources = Nil),
        IndexedControl("date",       inSearch = true,  inSummary = true,  "my-section/my-grid/date",                      "xf:date"  , "date",  htmlLabel = false, resources = Nil),
        IndexedControl("in-repeat",  inSearch = true,  inSummary = true,  "my-section/repeat/repeat-iteration/in-repeat", "xs:string", "input", htmlLabel = false, resources = Nil)
      )

    for {
      (srcVersion, elem)     <- List(DataFormatVersion.V480 -> formElem48, DataFormatVersion.V20191 -> formElem20191)
      (dstVersion, expected) <- List(DataFormatVersion.V400 -> expected40, DataFormatVersion.V480 -> expected48, DataFormatVersion.V20191 -> expected20191)
      if ! MigrationSupport.isMigrateUp(srcVersion, dstVersion)
    } locally {
      it(s"must find the expected indexed controls for source ${srcVersion.entryName} and destination ${dstVersion.entryName}") {
        assert(
          expected == Index.findIndexedControls(
            elemToDocumentInfo(elem),
            dstVersion
          )
        )
      }
    }
  }
}
