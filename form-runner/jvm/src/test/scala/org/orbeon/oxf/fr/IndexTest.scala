package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.index.Index.{IndexedControl, SummarySettings}
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
        IndexedControl("in-summary", "my-section/in-summary",       "xs:string", "input", SummarySettings(search = false, show = true,  edit = false), staticallyRequired = false, htmlLabel = true,  resources = Nil),
        IndexedControl("in-search",  "my-section/in-search",        "xs:string", "input", SummarySettings(search = true,  show = false, edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("in-both",    "my-section/in-both",          "xs:string", "input", SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("date",       "my-section/date",             "xf:date"  , "date",  SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("in-repeat",  "my-section/repeat/in-repeat", "xs:string", "input", SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil)
      )

    val expected48 =
      List(
        IndexedControl("in-summary", "my-section/in-summary",                        "xs:string", "input", SummarySettings(search = false, show = true,  edit = false), staticallyRequired = false, htmlLabel = true,  resources = Nil),
        IndexedControl("in-search",  "my-section/in-search",                         "xs:string", "input", SummarySettings(search = true,  show = false, edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("in-both",    "my-section/in-both",                           "xs:string", "input", SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("date",       "my-section/date",                              "xf:date"  , "date",  SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("in-repeat",  "my-section/repeat/repeat-iteration/in-repeat", "xs:string", "input", SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil)
      )

    val expected20191 =
      List(
        IndexedControl("in-summary", "my-section/my-grid/in-summary",                "xs:string", "input", SummarySettings(search = false, show = true,  edit = false), staticallyRequired = false, htmlLabel = true,  resources = Nil),
        IndexedControl("in-search",  "my-section/my-grid/in-search",                 "xs:string", "input", SummarySettings(search = true,  show = false, edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("in-both",    "my-section/my-grid/in-both",                   "xs:string", "input", SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("date",       "my-section/my-grid/date",                      "xf:date"  , "date",  SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("in-repeat",  "my-section/repeat/repeat-iteration/in-repeat", "xs:string", "input", SummarySettings(search = true,  show = true,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil)
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
            dstVersion,
            forUserRoles = None
          )
        )
      }
    }

    val formWithSummarySettings =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms"
               xmlns:xs="http://www.w3.org/2001/XMLSchema"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xmlns:ev="http://www.w3.org/2001/xml-events"
               xmlns:xi="http://www.w3.org/2001/XInclude"
               xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
               xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
               xmlns:map="http://www.w3.org/2005/xpath-functions/map"
               xmlns:array="http://www.w3.org/2005/xpath-functions/array"
               xmlns:math="http://www.w3.org/2005/xpath-functions/math"
               xmlns:exf="http://www.exforms.org/exf/1-0"
               xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
               xmlns:saxon="http://saxon.sf.net/"
               xmlns:sql="http://orbeon.org/oxf/xml/sql"
               xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:fb="http://orbeon.org/oxf/xml/form-builder">
        <xh:head>
          <xh:title>Untitled Form</xh:title>
          <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">
            <xf:instance id="fr-form-instance" xxf:exclude-result-prefixes="#all" xxf:index="id">
              <form>
                <section-1>
                  <grid-1>
                    <control-1/>
                    <control-2/>
                    <control-3/>
                    <control-4/>
                    <control-5/>
                    <control-6/>
                    <control-7/>
                  </grid-1>
                </section-1>
              </form>
            </xf:instance>
            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
              <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                <xf:bind id="grid-1-bind" ref="grid-1" name="grid-1">
                  <xf:bind id="control-1-bind" name="control-1" ref="control-1" xxf:whitespace="trim"/>
                  <xf:bind id="control-2-bind" ref="control-2" name="control-2" xxf:whitespace="trim"/>
                  <xf:bind id="control-3-bind" ref="control-3" name="control-3" xxf:whitespace="trim"/>
                  <xf:bind id="control-4-bind" ref="control-4" name="control-4" xxf:whitespace="trim"/>
                  <xf:bind id="control-5-bind" ref="control-5" name="control-5" xxf:whitespace="trim"/>
                  <xf:bind id="control-6-bind" ref="control-6" name="control-6" xxf:whitespace="trim"/>
                  <xf:bind id="control-7-bind" ref="control-7" name="control-7" xxf:whitespace="trim"/>
                </xf:bind>
              </xf:bind>
            </xf:bind>
            <xf:instance id="fr-form-metadata" xxf:readonly="true" xxf:exclude-result-prefixes="#all">
              <metadata>
                <application-name>test</application-name>
                <form-name>index</form-name>
                <title xml:lang="en">Untitled Form</title>
                <description xml:lang="en"/>
                <created-with-version>2022.1-SNAPSHOT PE</created-with-version>
                <email>
                  <templates>
                    <template name="default">
                      <form-fields/>
                    </template>
                  </templates>
                  <parameters/>
                </email>
                <grid-tab-order>default</grid-tab-order>
                <library-versions>
                  <orbeon>1</orbeon>
                </library-versions>
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
          </xf:model>
        </xh:head>
        <xh:body>
          <fr:view>
            <fr:body xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:p="http://www.orbeon.com/oxf/pipeline"
                     xmlns:oxf="http://www.orbeon.com/oxf/processors">
              <fr:section id="section-1-section" bind="section-1-bind">
                <xf:label ref="$form-resources/section-1/label"/>
                <fr:grid id="grid-1-grid" bind="grid-1-bind">
                  <fr:c y="1" x="1" w="6">
                    <xf:input id="control-1-control" bind="control-1-bind">
                      <fr:index/>
                      <xf:label ref="$form-resources/control-1/label"/>
                      <xf:hint ref="$form-resources/control-1/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c y="1" x="7" w="6">
                    <xf:input id="control-2-control" bind="control-2-bind">
                      <fr:index>
                        <fr:summary-show/>
                      </fr:index>
                      <xf:label ref="$form-resources/control-2/label"/>
                      <xf:hint ref="$form-resources/control-2/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="2" w="6">
                    <xf:input id="control-3-control" bind="control-3-bind">
                      <fr:index>
                        <fr:summary-search/>
                      </fr:index>
                      <xf:label ref="$form-resources/control-3/label"/>
                      <xf:hint ref="$form-resources/control-3/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="7" y="2" w="6">
                    <xf:input id="control-4-control" bind="control-4-bind">
                      <fr:index>
                        <fr:summary-edit/>
                      </fr:index>
                      <xf:label ref="$form-resources/control-4/label"/>
                      <xf:hint ref="$form-resources/control-4/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="3" w="6">
                    <xf:input id="control-5-control" bind="control-5-bind">
                      <fr:index>
                        <fr:summary-edit/>
                        <fr:summary-search/>
                        <fr:summary-show/>
                      </fr:index>
                      <xf:label ref="$form-resources/control-5/label"/>
                      <xf:hint ref="$form-resources/control-5/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="7" y="3" w="6">
                    <xf:input id="control-6-control" bind="control-6-bind">
                      <fr:encrypt/>
                      <xf:label ref="$form-resources/control-6/label"/>
                      <xf:hint ref="$form-resources/control-6/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="4" w="6">
                    <xf:input id="control-7-control" bind="control-7-bind">
                      <xf:label ref="$form-resources/control-7/label"/>
                      <xf:hint ref="$form-resources/control-7/hint"/>
                      <xf:alert ref="$fr-resources/detail/labels/alert"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="7" y="4" w="6"/>
                </fr:grid>
              </fr:section>
            </fr:body>
          </fr:view>
        </xh:body>
      </xh:html>

    val expectedSummarySettings =
      List(
        IndexedControl("control-1", "section-1/grid-1/control-1", "xs:string", "input", SummarySettings(show = false, search = false,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("control-2", "section-1/grid-1/control-2", "xs:string", "input", SummarySettings(show = true,  search = false,  edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("control-3", "section-1/grid-1/control-3", "xs:string", "input", SummarySettings(show = false, search = true,   edit = false), staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("control-4", "section-1/grid-1/control-4", "xs:string", "input", SummarySettings(show = false, search = false,  edit = true),  staticallyRequired = false, htmlLabel = false, resources = Nil),
        IndexedControl("control-5", "section-1/grid-1/control-5", "xs:string", "input", SummarySettings(show = true,  search = true,   edit = true),  staticallyRequired = false, htmlLabel = false, resources = Nil)
      )

    it("must find the expected indexed controls when using sub-elements instead of classes") {
      assert(
        expectedSummarySettings == Index.findIndexedControls(
          elemToDocumentInfo(formWithSummarySettings),
          FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm("test", "index")),
          forUserRoles = None
        )
      )
    }
  }
}
