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
package org.orbeon.oxf.fb

import java.io.{File, FileInputStream}
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.fb.FormDefinitionValidator.*
import org.orbeon.oxf.fb.ValidationIssue.*
import org.orbeon.oxf.fr.permission.Operation
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class FormDefinitionValidatorTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  private def toRootElem(elem: scala.xml.Elem): NodeInfo =
    elemToNodeInfo(elem)

  describe("Circular dependency detection (#3602)") {

    it("must report no circular dependency for a valid DAG") {
      val form = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <a/>
                    <b/>
                    <c/>
                    <d/>
                  </section-1>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a"/>
                  <xf:bind id="b-bind" name="b" ref="b" calculate="$a + 1"/>
                  <xf:bind id="c-bind" name="c" ref="c" calculate="$a + 2"/>
                  <xf:bind id="d-bind" name="d" ref="d" calculate="$b + $c"/>
                </xf:bind>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="3"><xf:input id="a-control" bind="a-bind"/></fr:c>
                    <fr:c x="4" y="1" w="3"><xf:input id="b-control" bind="b-bind"/></fr:c>
                    <fr:c x="7" y="1" w="3"><xf:input id="c-control" bind="c-bind"/></fr:c>
                    <fr:c x="10" y="1" w="3"><xf:input id="d-control" bind="d-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      assert(validateFormDefinition(form).isEmpty)
    }

    it("must detect a direct self-dependency in calculate") {
      val form = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form><section-1><a/></section-1></form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a" calculate="$a + 1"/>
                </xf:bind>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="12"><xf:input id="a-control" bind="a-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val issues = validateFormDefinition(form)
      assert(issues == List(CircularDependency("calculate", List("a"))))
      assert(issues.head.blocking)
    }

    it("must detect a multi-control cycle in calculate and default (including fb: annotated attributes)") {
      val form = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:fb="http://orbeon.org/oxf/xml/form-builder">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <a/>
                    <b/>
                    <c/>
                  </section-1>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a" fb:calculate="$b + 1" fb:default="$c"/>
                  <xf:bind id="b-bind" name="b" ref="b" fb:calculate="$c + 1" fb:default="$a"/>
                  <xf:bind id="c-bind" name="c" ref="c" fb:calculate="$a + 1" fb:default="$b"/>
                </xf:bind>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="4"><xf:input id="a-control" bind="a-bind"/></fr:c>
                    <fr:c x="5" y="1" w="4"><xf:input id="b-control" bind="b-bind"/></fr:c>
                    <fr:c x="9" y="1" w="4"><xf:input id="c-control" bind="c-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val issues = validateFormDefinition(form)
      assert(issues == List(
        CircularDependency("calculate", List("a", "b", "c")),
        CircularDependency("default",   List("a", "b", "c"))
      ))

      assert(hasCircularDependencyForControl(form, "a", "calculate", Some("$b + 1")))
      assert(! hasCircularDependencyForControl(form, "a", "calculate", Some("42")))
    }
  }

  describe("Formula XPath syntax and variable/control reference checks (#5746)") {

    it("must detect invalid XPath syntax, unresolved variables, and unresolved control references") {
      val form = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:var name="my-model-var" value="42"/>
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <a/>
                    <b/>
                  </section-1>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind
                    id="a-bind"
                    name="a"
                    ref="a"
                    calculate="$b + $my-model-var + (let $local := 1 return $local)"
                    relevant="$missing-var = 'yes'"/>
                  <xf:bind
                    id="b-bind"
                    name="b"
                    ref="b"
                    readonly="1 + * 2"
                    calculate="fr:control-string-value('missing-ctrl', false())"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <email>
                    <templates>
                      <template name="default">
                        <headers>
                          <header name="To" type="control-value">missing-email-ctrl</header>
                        </headers>
                        <exclude-from-all-control-values>
                          <control>missing-excluded-ctrl</control>
                        </exclude-from-all-control-values>
                        <attach>
                          <control>missing-attach-ctrl</control>
                        </attach>
                      </template>
                    </templates>
                  </email>
                </metadata>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="false">
                <resources>
                  <resource xml:lang="en">
                    <a class="label">
                      <label>Label A</label>
                      <fr:param type="ControlValueParam">
                        <fr:name>p1</fr:name>
                        <fr:controlName>missing-param-ctrl</fr:controlName>
                      </fr:param>
                      <fr:param type="ExpressionParam">
                        <fr:name>p2</fr:name>
                        <fr:expr>$missing-expr-var</fr:expr>
                      </fr:param>
                    </a>
                  </resource>
                </resources>
              </xf:instance>
              <fr:listener
                version="2018.2"
                events="value-changed"
                controls="a missing-listener-ctrl"
                actions="my-action"/>
              <fr:action name="my-action" version="2018.2">
                <fr:control-setvalue value="$missing-action-var" control="missing-target-ctrl"/>
              </fr:action>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="6"><xf:input id="a-control" bind="a-bind"/></fr:c>
                    <fr:c x="7" y="1" w="6"><xf:input id="b-control" bind="b-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val issues = validateFormDefinition(form)

      assert(issues.contains(UnresolvedVariableReference(Some("a"), "relevant", "missing-var")))
      assert(issues.contains(InvalidXPathExpression(Some("b"), "readonly", "1 + * 2")))
      assert(issues.contains(UnresolvedControlReference(Some("b"), "calculate", "missing-ctrl")))
      assert(issues.contains(UnresolvedControlReference(Some("a"), "param:p1", "missing-param-ctrl")))
      assert(issues.contains(UnresolvedVariableReference(Some("a"), "param:p2", "missing-expr-var")))
      assert(issues.contains(UnresolvedControlReference(None, "email-template:default", "missing-email-ctrl")))
      assert(issues.contains(UnresolvedControlReference(None, "email-template:default", "missing-excluded-ctrl")))
      assert(issues.contains(UnresolvedControlReference(None, "email-template:default", "missing-attach-ctrl")))
      assert(issues.contains(UnresolvedControlReference(None, "listener", "missing-listener-ctrl")))
      assert(issues.contains(UnresolvedControlReference(None, "action:my-action", "missing-target-ctrl")))
      assert(issues.contains(UnresolvedVariableReference(None, "action:my-action", "missing-action-var")))

      // Valid variables ($b, $my-model-var, $local) must not be reported
      assert(! issues.exists {
        case UnresolvedVariableReference(_, _, v) => Set("b", "my-model-var", "local").contains(v)
        case _                                    => false
      })
    }
  }

  describe("Token permission warning checks (#5437)") {

    it("must warn when a link parameter or function uses a token without corresponding anyone-with-token permissions") {
      val formWithoutTokenPermissions = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form><section-1><a/></section-1></form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a" calculate="fr:form-runner-link('edit', true())"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <permissions>
                    <permission operations="create read update">
                      <owner/>
                    </permission>
                  </permissions>
                  <email>
                    <templates>
                      <template name="default">
                        <body>
                          <fr:param type="LinkToViewPageParam">
                            <fr:name>view-link</fr:name>
                            <fr:token>true</fr:token>
                          </fr:param>
                        </body>
                      </template>
                    </templates>
                  </email>
                </metadata>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="12"><xf:input id="a-control" bind="a-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      // Note: `<anyone-with-token/>` is lowercase in Form Runner permissions XML; `<AnyoneWithToken/>` is not recognized, so permissions will be missing.
      val issues1 = validateFormDefinition(formWithoutTokenPermissions)
      assert(issues1.contains(TokenWithoutPermissions(Some("a"), "calculate", "edit", Set(Operation.Read, Operation.Update))))
      assert(issues1.contains(TokenWithoutPermissions(Some("default"), "param:view-link", "view", Set(Operation.Read))))

      // Now with proper `<anyone-with-token/>` permissions for `read` and `update`:
      val formWithTokenPermissions = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form><section-1><a/></section-1></form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a" calculate="fr:form-runner-link('edit', true())"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <permissions>
                    <permission operations="read update">
                      <anyone-with-token/>
                    </permission>
                  </permissions>
                  <email>
                    <templates>
                      <template name="default">
                        <body>
                          <fr:param type="LinkToViewPageParam">
                            <fr:name>view-link</fr:name>
                            <fr:token>true</fr:token>
                          </fr:param>
                        </body>
                      </template>
                    </templates>
                  </email>
                </metadata>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="12"><xf:input id="a-control" bind="a-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val issues2 = validateFormDefinition(formWithTokenPermissions)
      assert(issues2.isEmpty)
    }
  }

  describe("Data compatibility comparison when overwriting a published version (#3985)") {

    val baseForm = toRootElem(
      <xh:html
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <xh:head>
          <xf:model id="fr-form-model">
            <xf:instance id="fr-form-instance">
              <form>
                <section-1>
                  <a/>
                  <b/>
                  <grid-2>
                    <grid-2-iteration>
                      <rep-field/>
                    </grid-2-iteration>
                  </grid-2>
                </section-1>
                <section-2>
                  <c/>
                </section-2>
              </form>
            </xf:instance>
            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
              <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                <xf:bind id="a-bind" name="a" ref="a" type="xf:integer"/>
                <xf:bind id="b-bind" name="b" ref="b" type="xf:date"/>
                <xf:bind id="grid-2-bind" name="grid-2" ref="grid-2">
                  <xf:bind id="grid-2-iteration-bind" name="grid-2-iteration" ref="grid-2-iteration">
                    <xf:bind id="rep-field-bind" name="rep-field" ref="rep-field"/>
                  </xf:bind>
                </xf:bind>
              </xf:bind>
              <xf:bind id="section-2-bind" name="section-2" ref="section-2">
                <xf:bind id="c-bind" name="c" ref="c"/>
              </xf:bind>
            </xf:bind>
            <xf:instance id="grid-2-template">
              <grid-2-iteration>
                <rep-field/>
              </grid-2-iteration>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <fr:view>
            <fr:body>
              <fr:section id="section-1-section" bind="section-1-bind">
                <fr:grid id="grid-1-grid">
                  <fr:c x="1" y="1" w="6"><xf:input id="a-control" bind="a-bind"/></fr:c>
                  <fr:c x="7" y="1" w="6"><xf:input id="b-control" bind="b-bind"/></fr:c>
                </fr:grid>
                <fr:grid id="grid-2-grid" bind="grid-2-bind" repeat="content">
                  <fr:c x="1" y="1" w="12"><xf:input id="rep-field-control" bind="rep-field-bind"/></fr:c>
                </fr:grid>
              </fr:section>
              <fr:section id="section-2-section" bind="section-2-bind">
                <fr:grid id="grid-3-grid">
                  <fr:c x="1" y="1" w="12"><xf:input id="c-control" bind="c-bind"/></fr:c>
                </fr:grid>
              </fr:section>
            </fr:body>
          </fr:view>
        </xh:body>
      </xh:html>
    )

    it("must report Compatible when structure and datatypes are unchanged or widened") {
      val widenedForm = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <a/>
                    <b/>
                    <non-persisted/>
                    <grid-2>
                      <grid-2-iteration>
                        <rep-field/>
                      </grid-2-iteration>
                    </grid-2>
                  </section-1>
                  <section-2>
                    <c/>
                  </section-2>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a" type="xf:decimal"/>
                  <xf:bind id="b-bind" name="b" ref="b" type="xf:string"/>
                  <xf:bind id="non-persisted-bind" name="non-persisted" ref="non-persisted" fr:persist="false()"/>
                  <xf:bind id="grid-2-bind" name="grid-2" ref="grid-2">
                    <xf:bind id="grid-2-iteration-bind" name="grid-2-iteration" ref="grid-2-iteration">
                      <xf:bind id="rep-field-bind" name="rep-field" ref="rep-field"/>
                    </xf:bind>
                  </xf:bind>
                </xf:bind>
                <xf:bind id="section-2-bind" name="section-2" ref="section-2">
                  <xf:bind id="c-bind" name="c" ref="c"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="grid-2-template">
                <grid-2-iteration>
                  <rep-field/>
                </grid-2-iteration>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="4"><xf:input id="a-control" bind="a-bind"/></fr:c>
                    <fr:c x="5" y="1" w="4"><xf:input id="b-control" bind="b-bind"/></fr:c>
                    <fr:c x="9" y="1" w="4"><xf:output id="non-persisted-control" bind="non-persisted-bind"/></fr:c>
                  </fr:grid>
                  <fr:grid id="grid-2-grid" bind="grid-2-bind" repeat="content">
                    <fr:c x="1" y="1" w="12"><xf:input id="rep-field-control" bind="rep-field-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
                <fr:section id="section-2-section" bind="section-2-bind">
                  <fr:grid id="grid-3-grid">
                    <fr:c x="1" y="1" w="12"><xf:input id="c-control" bind="c-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val (status, issues) = compareFormDefinitions(baseForm, widenedForm)
      assert(status == DataCompatibilityStatus.Compatible)
      assert(issues.isEmpty)
    }

    it("must report MigrationRequired when controls are added, removed, or moved within the same repeat level") {
      val migratedForm = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <a/>
                    <new-field/>
                    <grid-2>
                      <grid-2-iteration>
                        <rep-field/>
                      </grid-2-iteration>
                    </grid-2>
                  </section-1>
                  <section-2>
                    <b/>
                  </section-2>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a" type="xf:integer"/>
                  <xf:bind id="new-field-bind" name="new-field" ref="new-field"/>
                  <xf:bind id="grid-2-bind" name="grid-2" ref="grid-2">
                    <xf:bind id="grid-2-iteration-bind" name="grid-2-iteration" ref="grid-2-iteration">
                      <xf:bind id="rep-field-bind" name="rep-field" ref="rep-field"/>
                    </xf:bind>
                  </xf:bind>
                </xf:bind>
                <xf:bind id="section-2-bind" name="section-2" ref="section-2">
                  <xf:bind id="b-bind" name="b" ref="b" type="xf:date"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="grid-2-template">
                <grid-2-iteration>
                  <rep-field/>
                </grid-2-iteration>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="6"><xf:input id="a-control" bind="a-bind"/></fr:c>
                    <fr:c x="7" y="1" w="6"><xf:input id="new-field-control" bind="new-field-bind"/></fr:c>
                  </fr:grid>
                  <fr:grid id="grid-2-grid" bind="grid-2-bind" repeat="content">
                    <fr:c x="1" y="1" w="12"><xf:input id="rep-field-control" bind="rep-field-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
                <fr:section id="section-2-section" bind="section-2-bind">
                  <fr:grid id="grid-3-grid">
                    <fr:c x="1" y="1" w="12"><xf:input id="b-control" bind="b-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val (status, issues) = compareFormDefinitions(baseForm, migratedForm)
      assert(status == DataCompatibilityStatus.MigrationRequired)
      assert(issues == List(DataMigrationRequired(
        addedNames   = List("new-field"),
        removedNames = List("c"),
        movedNames   = List("b")
      )))
    }

    it("must report Incompatible when repeat status, repeat ancestry, or datatype changes incompatibly") {
      val incompatibleForm = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <a/>
                    <grid-2>
                      <grid-2-iteration>
                        <rep-field/>
                        <b/>
                      </grid-2-iteration>
                    </grid-2>
                  </section-1>
                  <section-2>
                    <section-2-iteration>
                      <c/>
                    </section-2-iteration>
                  </section-2>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a" type="xf:boolean"/>
                  <xf:bind id="grid-2-bind" name="grid-2" ref="grid-2">
                    <xf:bind id="grid-2-iteration-bind" name="grid-2-iteration" ref="grid-2-iteration">
                      <xf:bind id="rep-field-bind" name="rep-field" ref="rep-field"/>
                      <xf:bind id="b-bind" name="b" ref="b" type="xf:date"/>
                    </xf:bind>
                  </xf:bind>
                </xf:bind>
                <xf:bind id="section-2-bind" name="section-2" ref="section-2">
                  <xf:bind id="section-2-iteration-bind" name="section-2-iteration" ref="section-2-iteration">
                    <xf:bind id="c-bind" name="c" ref="c"/>
                  </xf:bind>
                </xf:bind>
              </xf:bind>
              <xf:instance id="grid-2-template">
                <grid-2-iteration>
                  <rep-field/>
                  <b/>
                </grid-2-iteration>
              </xf:instance>
              <xf:instance id="section-2-template">
                <section-2-iteration>
                  <c/>
                </section-2-iteration>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="12"><xf:input id="a-control" bind="a-bind"/></fr:c>
                  </fr:grid>
                  <fr:grid id="grid-2-grid" bind="grid-2-bind" repeat="content">
                    <fr:c x="1" y="1" w="6"><xf:input id="rep-field-control" bind="rep-field-bind"/></fr:c>
                    <fr:c x="7" y="1" w="6"><xf:input id="b-control" bind="b-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
                <fr:section id="section-2-section" bind="section-2-bind" repeat="content">
                  <fr:grid id="grid-3-grid">
                    <fr:c x="1" y="1" w="12"><xf:input id="c-control" bind="c-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val (status, issues) = compareFormDefinitions(baseForm, incompatibleForm)
      assert(status == DataCompatibilityStatus.Incompatible)
      assert(issues.contains(IncompatibleDatatypeChange("a", "integer", "boolean")))
      assert(issues.contains(ControlRepeatAncestryChanged("b", Nil, List("grid-2-iteration"))))
      assert(issues.contains(ContainerRepeatStatusChanged("section-2", wasRepeated = false, isRepeated = true)))
    }
  }

  describe("Structural validation for Edit Source (#816)") {

    it("must pass on a structurally valid form definition") {
      val validForm = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <a/>
                  </section-1>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata/>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="false">
                <resources>
                  <resource xml:lang="en">
                    <section-1><label>Section 1</label></section-1>
                    <a><label>Control A</label><hint/></a>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <xf:label ref="$form-resources/section-1/label"/>
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="12">
                      <xf:input id="a-control" bind="a-bind">
                        <xf:label ref="$form-resources/a/label"/>
                        <xf:hint ref="$form-resources/a/hint"/>
                      </xf:input>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      assert(validateFormStructure(validForm).isEmpty)
    }

    it("must detect structural errors in edited form source") {
      val invalidForm = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <a/>
                    <!-- missing <b/> data holder -->
                  </section-1>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="a-bind" name="a" ref="a"/>
                  <xf:bind id="b-bind" name="b" ref="b"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata/>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="false">
                <resources>
                  <resource xml:lang="en">
                    <section-1><label>Section 1</label></section-1>
                    <a><label>Control A</label></a>
                  </resource>
                  <resource xml:lang="fr">
                    <section-1><label>Section 1</label></section-1>
                    <!-- missing <a> in French resources -->
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <p>Unprefixed HTML paragraph in no namespace</p>
                <fr:section id="section-1-section" bind="section-1-bind" repeat="content">
                  <xf:label ref="$form-resources/section-1/label"/>
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="20">
                      <xf:input id="a-control" bind="a-bind">
                        <xf:label ref="$form-resources/a/label"/>
                      </xf:input>
                      <xf:input id="b-control" bind="non-existent-bind">
                        <xf:label ref="$form-resources/missing-res/label"/>
                      </xf:input>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val codes = validateFormStructure(invalidForm).map(_.code).toSet

      assert(codes.contains("inconsistent-resources"))
      assert(codes.contains("element-without-namespace"))
      assert(codes.contains("multiple-controls-in-cell"))
      assert(codes.contains("invalid-cell-coordinates"))
      assert(codes.contains("missing-bind-reference"))
      assert(codes.contains("missing-repeat-template"))
      assert(codes.contains("missing-data-holder"))
      assert(codes.contains("mismatched-resource-name"))
      assert(codes.contains("missing-resource-holder"))
    }

    it("must detect when a control resource reference does not match the control name even if the resource holder exists") {
      val formWithMismatchedResourceName = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <contact-information>
                    <first-name/>
                  </contact-information>
                  <billing-information>
                    <card-number/>
                  </billing-information>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="contact-information-bind" name="contact-information" ref="contact-information">
                  <xf:bind id="first-name-bind" name="first-name" ref="first-name"/>
                </xf:bind>
                <xf:bind id="billing-information-bind" name="billing-information" ref="billing-information">
                  <xf:bind id="card-number-bind" name="card-number" ref="card-number"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata/>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="false">
                <resources>
                  <resource xml:lang="en">
                    <contact-information><label>Contact Information</label></contact-information>
                    <first-name><label>First Name</label></first-name>
                    <billing-information><label>Billing Information</label></billing-information>
                    <card-number><label>Card Number</label></card-number>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="contact-information-section" bind="contact-information-bind">
                  <xf:label ref="$form-resources/contact-information/label"/>
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="12">
                      <xf:input id="first-name-control" bind="first-name-bind">
                        <xf:label ref="$form-resources/first-name/label"/>
                      </xf:input>
                    </fr:c>
                  </fr:grid>
                </fr:section>
                <fr:section id="billing-information-section" bind="billing-information-bind">
                  <xf:label ref="$form-resources/contact-information/label"/>
                  <fr:grid id="grid-2-grid">
                    <fr:c x="1" y="1" w="12">
                      <xf:input id="card-number-control" bind="card-number-bind">
                        <xf:label ref="$form-resources/card-number/label"/>
                      </xf:input>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val structureIssues = validateFormStructure(formWithMismatchedResourceName)
      assert(structureIssues.exists(i => i.code == "mismatched-resource-name" && i.controlName.contains("billing-information")))

      val formIssues = validateFormDefinition(formWithMismatchedResourceName)
      assert(formIssues.exists(i => i.code == "mismatched-resource-name" && i.controlName.contains("billing-information")))
    }

    it("must treat legacy @columns values (other than 24) as 12-column grids and include structure checks in validateFormAsXml") {
      val formWithLegacyColumns = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <details>
                    <details-grid>
                      <title/>
                      <author/>
                    </details-grid>
                    <review-grid>
                      <review/>
                    </review-grid>
                  </details>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="details-bind" name="details" ref="details">
                  <xf:bind id="details-grid-bind" name="details-grid" ref="details-grid">
                    <xf:bind id="title-bind" name="title" ref="title"/>
                    <xf:bind id="author-bind" name="author" ref="author"/>
                  </xf:bind>
                  <xf:bind id="review-grid-bind" name="review-grid" ref="review-grid">
                    <xf:bind id="review-bind" name="review" ref="review"/>
                  </xf:bind>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata/>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="false">
                <resources>
                  <resource xml:lang="en">
                    <details><label>Details</label></details>
                    <title><label>Title</label></title>
                    <author><label>Author</label></author>
                    <review><label>Review</label></review>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="details-section" bind="details-bind">
                  <xf:label ref="$form-resources/details/label"/>
                  <fr:grid columns="3" id="details-grid-grid" bind="details-grid-bind">
                    <fr:c y="1" x="1" w="4">
                      <xf:input id="title-control" bind="title-bind">
                        <xf:label ref="$form-resources/title/label"/>
                      </xf:input>
                    </fr:c>
                    <fr:c y="1" x="5" w="8">
                      <xf:input id="author-control" bind="author-bind">
                        <xf:label ref="$form-resources/author/label"/>
                      </xf:input>
                    </fr:c>
                  </fr:grid>
                  <fr:grid columns="1" id="review-grid-grid" bind="review-grid-bind">
                    <fr:c y="1" x="1" w="12">
                      <xf:textarea id="review-control" bind="review-bind">
                        <xf:label ref="$form-resources/review/label"/>
                      </xf:textarea>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      assert(validateFormStructure(formWithLegacyColumns).isEmpty)
      val xmlResult = validateFormAsXml(formWithLegacyColumns)
      assert(xmlResult.attValue("valid") == "true")
    }

    it("must validate AVT expressions on fr:hyperlink/@href") {
      def hyperlinkForm(hrefAttr: String) = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <details>
                    <order-id/>
                    <order-link/>
                  </details>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="details-bind" name="details" ref="details">
                  <xf:bind id="order-id-bind" name="order-id" ref="order-id"/>
                  <xf:bind id="order-link-bind" name="order-link" ref="order-link" fr:persist="false()"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata/>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="false">
                <resources>
                  <resource xml:lang="en">
                    <details><label>Details</label></details>
                    <order-id><label>Order ID</label></order-id>
                    <order-link><label>Open Order</label></order-link>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="details-section" bind="details-bind">
                  <xf:label ref="$form-resources/details/label"/>
                  <fr:grid id="details-grid-grid">
                    <fr:c y="1" x="1" w="6">
                      <xf:input id="order-id-control" bind="order-id-bind">
                        <xf:label ref="$form-resources/order-id/label"/>
                      </xf:input>
                    </fr:c>
                    <fr:c y="1" x="7" w="6">
                      <fr:hyperlink id="order-link-control" bind="order-link-bind" href={hrefAttr}>
                        <xf:label ref="$form-resources/order-link/label"/>
                      </fr:hyperlink>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val validForm   = hyperlinkForm("https://example.com/orders/{$order-id}")
      val invalidForm = hyperlinkForm("https://example.com/orders/{$missing-control}")

      assert((validateFormStructure(validForm) ++ validateFormDefinition(validForm)).isEmpty)
      val invalidIssues = validateFormDefinition(invalidForm)
      assert(invalidIssues.nonEmpty)
    }

    it("must validate AVT expressions on fr:range start/end/step/prefix/suffix attributes") {
      def rangeForm(endAttr: String) = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">
              <xf:instance id="fr-form-instance">
                <form>
                  <details>
                    <max-score>100</max-score>
                    <score/>
                  </details>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="details-bind" name="details" ref="details">
                  <xf:bind id="max-score-bind" name="max-score" ref="max-score" type="xf:decimal"/>
                  <xf:bind id="score-bind" name="score" ref="score" type="fr:range"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <application-name>acme</application-name>
                  <form-name>slider-test</form-name>
                  <title xml:lang="en">Slider Test</title>
                </metadata>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="true">
                <resources>
                  <resource xml:lang="en">
                    <details><label>Details</label></details>
                    <max-score><label>Max Score</label></max-score>
                    <score><label>Score</label></score>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="details-section" bind="details-bind">
                  <xf:label ref="$form-resources/details/label"/>
                  <fr:grid id="details-grid-grid">
                    <fr:c y="1" x="1" w="6">
                      <xf:input id="max-score-control" bind="max-score-bind">
                        <xf:label ref="$form-resources/max-score/label"/>
                      </xf:input>
                    </fr:c>
                    <fr:c y="1" x="7" w="6">
                      <fr:range id="score-control" bind="score-bind" start="0" end={endAttr} step="5" suffix="pts">
                        <xf:label ref="$form-resources/score/label"/>
                      </fr:range>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val validForm   = rangeForm("{$max-score}")
      val invalidForm = rangeForm("{$non-existent-max}")

      assert((validateFormStructure(validForm) ++ validateFormDefinition(validForm)).isEmpty)
      val invalidIssues = validateFormDefinition(invalidForm)
      assert(invalidIssues.nonEmpty)
    }

    it("must validate AVT expressions on fr:rating and fr:scale attributes") {
      def ratingScaleForm(maxStarsAttr: String, scaleEndAttr: String) = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">
              <xf:instance id="fr-form-instance">
                <form>
                  <details>
                    <max-score>10</max-score>
                    <stars/>
                    <nps/>
                  </details>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="details-bind" name="details" ref="details">
                  <xf:bind id="max-score-bind" name="max-score" ref="max-score" type="xf:integer"/>
                  <xf:bind id="stars-bind" name="stars" ref="stars" type="fr:rating"/>
                  <xf:bind id="nps-bind" name="nps" ref="nps" type="fr:scale"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <application-name>acme</application-name>
                  <form-name>rating-scale-test</form-name>
                  <title xml:lang="en">Rating Scale Test</title>
                </metadata>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="true">
                <resources>
                  <resource xml:lang="en">
                    <details><label>Details</label></details>
                    <max-score><label>Max Score</label></max-score>
                    <stars><label>Stars</label></stars>
                    <nps><label>NPS</label></nps>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="details-section" bind="details-bind">
                  <xf:label ref="$form-resources/details/label"/>
                  <fr:grid id="details-grid-grid">
                    <fr:c y="1" x="1" w="4">
                      <xf:input id="max-score-control" bind="max-score-bind">
                        <xf:label ref="$form-resources/max-score/label"/>
                      </xf:input>
                    </fr:c>
                    <fr:c y="1" x="5" w="4">
                      <fr:rating id="stars-control" bind="stars-bind" max={maxStarsAttr}>
                        <xf:label ref="$form-resources/stars/label"/>
                      </fr:rating>
                    </fr:c>
                    <fr:c y="1" x="9" w="4">
                      <fr:scale id="nps-control" bind="nps-bind" start="0" end={scaleEndAttr} start-label="Low" end-label="High">
                        <xf:label ref="$form-resources/nps/label"/>
                      </fr:scale>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val validForm   = ratingScaleForm("{$max-score}", "{$max-score}")
      val invalidForm = ratingScaleForm("{$unknown-max}", "{$unknown-end}")

      assert((validateFormStructure(validForm) ++ validateFormDefinition(validForm)).isEmpty)
      val invalidIssues = validateFormDefinition(invalidForm)
      assert(invalidIssues.size == 2)
    }

    it("must validate all demo forms under data/orbeon/fr/orbeon/builder/data") {
      val builderDataDir =
        List(
          new File("data/orbeon/fr/orbeon/builder/data"),
          new File("../data/orbeon/fr/orbeon/builder/data")
        ).find(_.isDirectory).getOrElse(fail("Could not find `data/orbeon/fr/orbeon/builder/data` directory"))

      val dataFiles =
        Option(builderDataDir.listFiles)
          .toList
          .flatten
          .filter(_.isDirectory)
          .sortBy(_.getName)
          .map(new File(_, "data.xml"))
          .filter(_.isFile)

      assert(dataFiles.nonEmpty)

      val problems =
        for {
          dataFile <- dataFiles
          doc      = useAndClose(new FileInputStream(dataFile)) { is =>
            TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, dataFile.toURI.toString, false, false)
          }
          rootElem = doc.rootElement
          issues   = (validateFormStructure(rootElem) ++ validateFormDefinition(rootElem)).distinct
          if issues.nonEmpty
        } yield
          s"${dataFile.getParentFile.getName}/data.xml:\n" +
            issues.map(i => s"  - [${i.severity.entryName}/${i.code}] ${formatIssueMessage(i)}").mkString("\n")

      assert(problems.isEmpty, problems.mkString("\n", "\n\n", "\n"))
    }
  }

  describe("Process definition validation (#7871)") {

    def formWithProcesses(processesElem: scala.xml.Elem): NodeInfo =
      toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <first-name/>
                    <amount/>
                  </section-1>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="first-name-bind" name="first-name" ref="first-name"/>
                  <xf:bind id="amount-bind" name="amount" ref="amount"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <application-name>acme</application-name>
                  <form-name>order</form-name>
                  {processesElem}
                </metadata>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="6"><xf:input id="first-name-control" bind="first-name-bind"/></fr:c>
                    <fr:c x="7" y="1" w="6"><xf:input id="amount-control" bind="amount-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

    it("must accept valid processes with XPath, AVTs, sub-processes, section-template setvalue, and custom XForms params") {
      val form = formWithProcesses(
        <processes>
          <process name="helper">
            control-setvalue(control-name = "first-name", value = "upper-case($first-name)")
            then control-setvalue(control-name = "inner-ctrl", value = "'ok'", section-name = "section-1")
            then xf:dispatch(name = "my-custom-event", targetid = "fr-form-model", custom-prop = "{{$amount + 1}}")
          </process>
          <process name="main">
            require-uploads
            then validate("warning")
            then if ("$amount gt 0 and xxf:non-blank($first-name)") then (
              helper
              then process(name = "helper")
              then send(uri = "https://example.org/items/{{$first-name}}", method = "post", content = "xml metadata")
              then sleep(duration = "500ms")
              then save(draft = "false")
            ) else
              error-message(message = "Invalid amount: {{$amount}}")
          </process>
        </processes>
      )

      assert(validateFormDefinition(form).isEmpty)
    }

    it("must report syntax, parameter domain, required-one-of, mutual-exclusion, XPath/AVT, reference, and cycle errors") {
      val form = formWithProcesses(
        <processes>
          <process name="bad-syntax">validate then (save</process>
          <process name="bad-params">
            save(draft = "maybe", bogus = "1")
            then validate(level = "")
            then send(method = "POST")
            then send(uri = "https://example.org/{{1 + }}", content = "xml nope")
            then sleep(duration = "1000")
            then change-mode(mode = "pdf")
            then xf:send(submission = "bad id")
            then control-setvalue(control-name = "missing-ctrl", value = "$missing-var + ")
            then control-setvalue(control-name = "first-name", value = "'x'", at = "0")
            then control-setvalue(control-name = "first-name", value = "'x'", at = "all", section-name = "missing-sec")
            then if ("1 + * 2") then unknown-action else process(name = "bad-params")
          </process>
          <process name="cycle-a">cycle-b</process>
          <process name="cycle-b">process(name = "cycle-a")</process>
        </processes>
      )

      val issues = validateFormDefinition(form)

      assert(issues.contains(InvalidProcess("bad-syntax", "Process syntax is invalid")))
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("does not support parameter \"bogus\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Parameter \"draft\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Parameter \"level\" of action \"validate\" requires a non-empty value"); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Action \"send\" requires at least one of"); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Unsupported value in parameter \"content\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Invalid duration \"1000\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Unsupported mode \"pdf\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Invalid identifier \"bad id\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Invalid \"at\" value \"0\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("cannot specify both \"at\" and \"section-name\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Unknown action or sub-process \"unknown-action\""); case _ => false })
      assert(issues.exists { case InvalidProcess("bad-params", d) => d.contains("Process \"bad-params\" cannot call itself"); case _ => false })

      // XPath & AVT compilation errors + unresolved control/section references
      assert(issues.contains(InvalidXPathExpression(None, "process:bad-params", "https://example.org/{1 + }")))
      assert(issues.contains(InvalidXPathExpression(None, "process:bad-params", "$missing-var +")))
      assert(issues.contains(InvalidXPathExpression(None, "process:bad-params", "1 + * 2")))
      assert(issues.contains(UnresolvedControlReference(None, "process:bad-params", "missing-ctrl")))
      assert(issues.contains(UnresolvedControlReference(None, "process:bad-params", "missing-sec")))

      // Mutual sub-process cycle detection
      assert(issues.contains(InvalidProcess("cycle-a", """Circular sub-process call involving "cycle-a"""")))
      assert(issues.contains(InvalidProcess("cycle-b", """Circular sub-process call involving "cycle-b"""")))
    }
  }

  describe("XML Schema type validation (#816/#5746)") {

    it("must accept valid schema types with and without targetNamespace as well as component predefined types") {
      val withNsForm = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:bk="http://example.org/books">
          <xh:head>
            <xf:model id="fr-form-model">
              <xs:schema targetNamespace="http://example.org/books" xmlns:bk="http://example.org/books">
                <xs:simpleType name="rating">
                  <xs:restriction base="xs:integer">
                    <xs:minInclusive value="1"/>
                    <xs:maxInclusive value="5"/>
                  </xs:restriction>
                </xs:simpleType>
              </xs:schema>
              <xf:instance id="fr-form-instance">
                <form><section-1><book-rating/><iban/></section-1></form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="book-rating-bind" name="book-rating" ref="book-rating" type="bk:rating"/>
                  <xf:bind id="iban-bind" name="iban" ref="iban" type="fr:iban"/>
                </xf:bind>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="6"><xf:input id="book-rating-control" bind="book-rating-bind"/></fr:c>
                    <fr:c x="7" y="1" w="6"><fr:iban id="iban-control" bind="iban-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      assert(validateFormDefinition(withNsForm).isEmpty)
    }

    it("must report UnresolvedSchemaType when schema is missing, type is undefined, or prefix is undeclared") {
      val noSchemaForm = toRootElem(
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:bk="http://example.org/books">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form><section-1><rating-ctrl/><year-ctrl/><nested-ctrl/></section-1></form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="rating-ctrl-bind" name="rating-ctrl" ref="rating-ctrl" type="rating"/>
                  <xf:bind id="year-ctrl-bind" name="year-ctrl" ref="year-ctrl" type="undeclared:year"/>
                  <xf:bind id="nested-ctrl-bind" name="nested-ctrl" ref="nested-ctrl">
                    <xf:type id="v-1">bk:missing-type</xf:type>
                  </xf:bind>
                </xf:bind>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid">
                    <fr:c x="1" y="1" w="4"><xf:input id="rating-ctrl-control" bind="rating-ctrl-bind"/></fr:c>
                    <fr:c x="5" y="1" w="4"><xf:input id="year-ctrl-control" bind="year-ctrl-bind"/></fr:c>
                    <fr:c x="9" y="1" w="4"><xf:input id="nested-ctrl-control" bind="nested-ctrl-bind"/></fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>
      )

      val issues = validateFormDefinition(noSchemaForm)
      assert(issues.contains(UnresolvedSchemaType("rating-ctrl", "rating")))
      assert(issues.contains(UnresolvedSchemaType("year-ctrl", "undeclared:year")))
      assert(issues.contains(UnresolvedSchemaType("nested-ctrl", "bk:missing-type")))

      val validationXml = validateFormAsXml(noSchemaForm, includeStructureCheck = false)
      assert(validationXml.attValue("error-count") == "3")
      val issueTexts = (validationXml / "issue").map(_.stringValue)
      assert(issueTexts.contains("""Control "rating-ctrl" uses unknown XML Schema type "rating""""))
    }
  }
}
