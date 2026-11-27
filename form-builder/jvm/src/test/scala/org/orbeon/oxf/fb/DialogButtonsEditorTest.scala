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

import org.orbeon.oxf.fr.ButtonsEditorUI
import org.orbeon.oxf.fr.process.ProcessEditorUI
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class DialogButtonsEditorTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  describe("ButtonsEditorUI metadata XML conversion") {

    it("must omit <detail-buttons> when nothing is overridden and round-trip overrides losslessly") {
      val emptyMeta = ProcessEditorUI.xmlStringToMutableNodeInfoRootElem("<metadata/>")
      val initialEditorXml =
        ButtonsEditorUI.metadataToEditorDetailButtonsXml(
          emptyMeta,
          ButtonsEditorUI.DefaultEditButtons,
          ButtonsEditorUI.DefaultViewButtons,
          ButtonsEditorUI.DefaultInnerButtons
        )

      assert(ButtonsEditorUI.editorDetailButtonsToMetadataXml(initialEditorXml) eq null)

      val customMeta =
        ProcessEditorUI.xmlStringToMutableNodeInfoRootElem(
          """<metadata>
            |  <detail-buttons close-uri="/custom/done">
            |    <bar name="edit">clear save-final approve</bar>
            |    <bar name="inner"/>
            |    <button name="save-final" loading-indicator=""/>
            |    <button name="approve" visible="$amount gt 100" enabled="fr:is-wizard-last-page()" loading-indicator="modal"/>
            |  </detail-buttons>
            |</metadata>""".stripMargin
        )

      val hydrated =
        ButtonsEditorUI.metadataToEditorDetailButtonsXml(
          customMeta,
          ButtonsEditorUI.DefaultEditButtons,
          ButtonsEditorUI.DefaultViewButtons,
          "wizard-prev wizard-next"
        )

      assert(hydrated.attValue("close-uri-override") == "true")
      assert(hydrated.attValue("close-uri") == "/custom/done")

      val editBar  = (hydrated / "bar").find(_.attValue("name") == "edit").get
      val viewBar  = (hydrated / "bar").find(_.attValue("name") == "view").get
      val innerBar = (hydrated / "bar").find(_.attValue("name") == "inner").get
      assert(editBar.attValue("override") == "true" && editBar.attValue("value") == "clear save-final approve")
      assert(viewBar.attValue("override") == "false" && viewBar.attValue("value") == "edit pdf")
      assert(innerBar.attValue("override") == "true" && innerBar.attValue("value") == "")

      val serialized = ButtonsEditorUI.editorDetailButtonsToMetadataXml(hydrated)
      assert(serialized ne null)
      assert(serialized.attValue("close-uri") == "/custom/done")
      assert((serialized / "bar").map(b => b.attValue("name") -> b.stringValue).toList == List(
        "edit"  -> "clear save-final approve",
        "inner" -> ""
      ))

      val saveRule    = (serialized / "button").find(_.attValue("name") == "save-final").get
      val approveRule = (serialized / "button").find(_.attValue("name") == "approve").get
      assert(saveRule.attValueOpt("loading-indicator").contains(""))
      assert(saveRule.attValueOpt("visible").isEmpty)
      assert(approveRule.attValueOpt("visible").contains("$amount gt 100"))
      assert(approveRule.attValueOpt("enabled").contains("fr:is-wizard-last-page()"))
      assert(approveRule.attValueOpt("loading-indicator").contains("modal"))
    }
  }

  describe("<fb:dialog-buttons-editor> XForms integration") {

    it("must stage, configure, validate, and atomically commit buttons, labels, and processes to fr-form-metadata while preserving custom messages") {
      withTestExternalContext { implicit ec =>
        val hostDoc =
          NodeConversions.elemToOrbeonDom(
            <xh:html
              xml:lang="en"
              xmlns:xh="http://www.w3.org/1999/xhtml"
              xmlns:xf="http://www.w3.org/2002/xforms"
              xmlns:xs="http://www.w3.org/2001/XMLSchema"
              xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
              xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
              xmlns:fb="http://orbeon.org/oxf/xml/form-builder">
              <xh:head>
                <xf:model
                  id="fr-form-model"
                  xxf:expose-xpath-types="true"
                  xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">

                  <xf:instance id="fb-form-instance" xxf:index="id">
                    <xh:html>
                      <xh:head>
                        <xf:model id="fr-form-model">
                          <xf:instance id="fr-form-instance">
                            <form>
                              <section-1>
                                <control-1/>
                              </section-1>
                            </form>
                          </xf:instance>
                          <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                            <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                              <xf:bind id="control-1-bind" name="control-1" ref="control-1"/>
                            </xf:bind>
                          </xf:bind>
                          <xf:instance id="fr-form-metadata" xxf:readonly="true" xxf:exclude-result-prefixes="#all">
                            <metadata>
                              <application-name>acme</application-name>
                              <form-name>claim</form-name>
                              <title xml:lang="en">Claim Form</title>
                              <resources>
                                <resource xml:lang="en">
                                  <messages>
                                    <message name="save-success" html="false">Claim saved!</message>
                                  </messages>
                                </resource>
                              </resources>
                            </metadata>
                          </xf:instance>
                          <xf:instance id="fr-form-resources" xxf:readonly="false">
                            <resources>
                              <resource xml:lang="en">
                                <section-1><label>Section 1</label></section-1>
                                <control-1><label>Control 1</label><hint/></control-1>
                              </resource>
                              <resource xml:lang="fr">
                                <section-1><label>Section 1</label></section-1>
                                <control-1><label>Contrôle 1</label><hint/></control-1>
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
                                  <xf:input id="control-1-control" bind="control-1-bind">
                                    <xf:label ref="$form-resources/control-1/label"/>
                                    <xf:hint ref="$form-resources/control-1/hint"/>
                                  </xf:input>
                                </fr:c>
                              </fr:grid>
                            </fr:section>
                          </fr:body>
                        </fr:view>
                      </xh:body>
                    </xh:html>
                  </xf:instance>

                  <xf:instance id="fr-parameters-instance">
                    <parameters>
                      <app>orbeon</app>
                      <form>builder</form>
                      <form-version>1</form-version>
                      <document>test-doc</document>
                      <mode>edit</mode>
                    </parameters>
                  </xf:instance>

                  <xf:instance id="fr-language-instance"><language>en</language></xf:instance>
                  <xf:instance id="fr-languages-instance">
                    <languages>
                      <language code="en" native-name="English"/>
                      <language code="fr" native-name="Français"/>
                    </languages>
                  </xf:instance>

                  <xf:instance id="fr-form-resources" src="oxf:/forms/orbeon/builder/form/resources.xml"
                               xxf:readonly="true" xxf:cache="true" xxf:xinclude="true"/>
                  <xf:instance id="fr-fr-resources" src="oxf:/apps/fr/i18n/resources.xml"
                               xxf:readonly="true" xxf:cache="true" xxf:xinclude="true"/>
                </xf:model>
              </xh:head>
              <xh:body>
                <fb:dialog-buttons-editor
                  id="dialog-buttons-editor"
                  resources-ref="instance('fr-form-resources')/resource[@xml:lang = 'en']"
                  fr-resources-ref="instance('fr-fr-resources')/resource[@xml:lang = 'en']"/>
              </xh:body>
            </xh:html>
          )

        val doc = setupDocument(hostDoc)
        withActionAndDoc(doc) {
          def findByStaticId(staticId: String): Option[XFormsControl] =
            ControlsIterator(doc.controls.getCurrentControlTree)
              .find(_.staticControl.staticId == staticId)

          // 1. Open Form Builder's Buttons dialog
          doc.withOutermostActionHandler {
            dispatch(name = "fb-show-dialog", effectiveId = "dialog-buttons-editor")
          }

          val buttonsEditorCtrl = findByStaticId("fb-buttons-editor").get

          // 2. Configure & add custom button `approve` on the Edit bar
          doc.withOutermostActionHandler {
            dispatch(
              name        = "fr-configure-button",
              effectiveId = buttonsEditorCtrl.effectiveId,
              properties  = Map(
                "bar"        -> Some("edit"),
                "name"       -> Some("approve"),
                "add-to-bar" -> Some("true")
              )
            )
          }

          val labelModeSelect = findByStaticId("fr-be-label-mode-select").get.asInstanceOf[XFormsSelect1Control]
          val singleItem      = labelModeSelect.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left("single")).get._1
          setControlValueWithEventSearchNested(labelModeSelect.effectiveId, singleItem.externalValue(labelModeSelect.mustEncodeValues))

          val singleLabelInput = findByStaticId("fr-be-single-label-input").get.asInstanceOf[XFormsValueControl]
          setControlValueWithEventSearchNested(singleLabelInput.effectiveId, "Approve Claim")

          val visModeSelect = findByStaticId("fr-be-visible-mode-select").get.asInstanceOf[XFormsSelect1Control]
          val xpathItem     = visModeSelect.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).find(_._2 == Left("xpath")).get._1
          setControlValueWithEventSearchNested(visModeSelect.effectiveId, xpathItem.externalValue(visModeSelect.mustEncodeValues))

          val visXPathInput = findByStaticId("fr-be-visible-xpath-input").get.asInstanceOf[XFormsValueControl]
          setControlValueWithEventSearchNested(visXPathInput.effectiveId, "$control-1 = 'yes' and not(fr:is-wizard-first-page())")
          assert(visXPathInput.isValid)

          activateControlWithEvent(findByStaticId("fr-be-apply-dialog-btn").get.effectiveId)

          // 3. Simulate saving the associated process via the embedded Process Editor
          val procEditorCtrl = findByStaticId("fb-be-process-editor-dialog").get
          val procXml        = ProcessEditorUI.xmlStringToMutableNodeInfoRootElem("""<processes><process name="approve">validate then save</process></processes>""")
          doc.withOutermostActionHandler {
            dispatch(
              name        = "fr-update",
              effectiveId = procEditorCtrl.effectiveId,
              properties  = Map("config" -> Some(procXml))
            )
          }

          // 4. Click Apply on <fb:dialog-buttons-editor>
          activateControlWithEvent(findByStaticId("fb-save-trigger").get.effectiveId)

          // 5. Inspect saved fr-form-metadata inside fb-form-instance
          val fbFormRoot = instance("fb-form-instance").get.rootElement
          val metadata   = (fbFormRoot / "*:head" / "*:model" / "*:instance").find(_.id == "fr-form-metadata").get / "metadata"

          val detailButtons = (metadata / "detail-buttons").head
          assert((detailButtons / "bar").exists(b => b.attValue("name") == "edit" && b.stringValue.endsWith("approve")))
          val approveBtn = (detailButtons / "button").find(_.attValue("name") == "approve").get
          assert(approveBtn.attValueOpt("visible").contains("$control-1 = 'yes' and not(fr:is-wizard-first-page())"))

          // Button label written under resources/resource[@xml:lang = '*']/buttons/button while preserving English <messages>!
          val wildButtons = (metadata / "resources" / "resource").filter(_.attValue("*:lang") == "*") / "buttons" / "button"
          assert(wildButtons.exists(b => b.attValue("name") == "approve" && b.stringValue == "Approve Claim"))

          val enMessages = (metadata / "resources" / "resource").filter(_.attValue("*:lang") == "en") / "messages" / "message"
          assert(enMessages.exists(m => m.attValue("name") == "save-success" && m.stringValue == "Claim saved!"))

          // Process written under <processes>
          val savedProc = metadata / "processes" / "process"
          assert(savedProc.exists(p => p.attValue("name") == "approve" && p.stringValue == "validate then save"))

          // Resulting form definition passes FormDefinitionValidator with zero issues
          assert(FormDefinitionValidator.validateFormDefinition(fbFormRoot).isEmpty)
        }
      }
    }
  }
}
