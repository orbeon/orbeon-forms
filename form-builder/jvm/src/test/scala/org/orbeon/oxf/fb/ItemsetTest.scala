/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike

import scala.xml.Elem

class ItemsetTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  val ItemsetsDoc = "oxf:/org/orbeon/oxf/fr/form-with-itemsets.xhtml"

  describe("Read and write control items") {
    it("must read and write itemsets") {
      withActionAndFBDoc(ItemsetsDoc) { implicit ctx =>

        def assertControl(controlName: String, expectedItems: Seq[Elem]): Unit = {

          val actualItems =
            FormBuilder.getControlItemsGroupedByValue(controlName) map TransformerUtils.tinyTreeToDom4j

          for ((expected, actual) <- expectedItems.zipAll(actualItems, null, null))
            assertXMLDocumentsIgnoreNamespacesInScope(expected.toDocument, actual)
        }

        // Read itemsets
        locally {
          val expectedDropdownItems = List(
            <item>
              <label lang="en">First choice</label>
              <label lang="fr">Premier choix</label>
              <value>one</value>
            </item>,
            <item>
              <label lang="en">Second choice</label>
              <label lang="fr">Second choix</label>
              <value>two</value>
            </item>,
            <item>
              <label lang="en">Third choice</label>
              <label lang="fr">Troisième choix</label>
              <value>three</value>
            </item>
          )

          val expectedRadioItems = List(
            <item>
              <label lang="en">First choice</label>
              <hint  lang="en">Hint 1</hint>
              <label lang="fr">Premier choix</label>
              <hint  lang="fr">Suggestion 1</hint>
              <value>one</value>
            </item>,
            <item>
              <label lang="en">Second choice</label>
              <hint  lang="en">Hint 2</hint>
              <label lang="fr">Second choix</label>
              <hint  lang="fr">Suggestion 2</hint>
              <value>two</value>
            </item>,
            <item>
              <label lang="en">Third choice</label>
              <hint  lang="en">Hint 3</hint>
              <label lang="fr">Troisième choix</label>
              <hint  lang="fr">Suggestion 3</hint>
              <value>three</value>
            </item>
          )

          val controlNameExpected = List(
            "dropdown" -> expectedDropdownItems,
            "radios"   -> expectedRadioItems
          )

          for ((controlName, expected) <- controlNameExpected)
            assertControl(controlName, expected)
        }

        // Update itemsets
        locally {
          val newDropdownItems =
            <items>
              <item>
                <label lang="en">Third choice</label>
                <label lang="fr">Troisième choix</label>
                <value>three</value>
              </item>
              <item>
                <label lang="en">First choice</label>
                <label lang="fr">Premier choix</label>
                <value>one</value>
              </item>
              <item>
                <label lang="en">Fourth choice</label>
                <label lang="fr">Quatrième choix</label>
                <value>four</value>
              </item>
            </items>

          val newRadioItems =
            <items>
              <item>
                <label lang="en">Third choice</label>
                <hint  lang="en">Hint 3</hint>
                <label lang="fr">Troisième choix</label>
                <hint  lang="fr">Suggestion 3</hint>
                <value>three</value>
              </item>
              <item>
                <label lang="en">First choice</label>
                <hint  lang="en">Hint 1</hint>
                <label lang="fr">Premier choix</label>
                <hint  lang="fr">Suggestion 1</hint>
                <value>one</value>
              </item>
              <item>
                <label lang="en">Fourth choice</label>
                <hint  lang="en">Hint 4</hint>
                <label lang="fr">Quatrième choix</label>
                <hint  lang="fr">Suggestion 4</hint>
                <value>four</value>
              </item>
            </items>

          val controlNameExpected = List(
            "dropdown" -> newDropdownItems,
            "radios"   -> newRadioItems
          )

          for ((controlName, expected) <- controlNameExpected) {
            FormBuilder.setControlItems(controlName, expected)
            assertControl(controlName, expected \ "item" collect { case e: scala.xml.Elem => e })
          }
        }
      }
    }
  }

  describe("Insert with multiple languages") {
    it(s"must insert itemset resources depending on language") {

      val itemTemplates = {
        val fbResourcesURL = "oxf:/forms/orbeon/builder/form/resources.xml"
        val fbResources = TransformerUtils.urlToTinyTree(fbResourcesURL).rootElement.child("resource")
        fbResources.map { resource =>
          val lang = resource.attValue("lang")
          val items = resource.child("template").child("items").child(*)
          lang -> items
        }.toMap
      }

      withActionAndFBDoc(TemplateDoc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        // Add a new radio control, and return the resources that were created for that control
        def resourceForNewControl(): Elem = {

          // Add new control to the form
          val addedControl = {
            val selectionControls = TransformerUtils.urlToTinyTree("oxf:/forms/orbeon/builder/xbl/selection-select1-full.xbl")
            val selectionBindings = selectionControls.rootElement.child("binding")
            val radioBinding = selectionBindings.find(_.id == "fb-input-select1-full").get
            ToolboxOps.insertNewControl(radioBinding)
            doc.descendant("select1").last
          }

          // Extract from the resource the part just about this control
          val controlName = FormRunner.controlNameFromId(addedControl.id)
          <resources>{
            FormBuilder.resourcesRoot.child(*) map (resourceForLang =>
              <resource lang={resourceForLang.attValue("lang")}>{
                resourceForLang.child(controlName).child(*).map(nodeInfoToElem)
              }</resource>
            )
          }</resources>
        }

        def assertNewControlResources(expected: Seq[(String, Seq[NodeInfo])]): Unit = {
          val expectedResources =
            <resources>{
              expected map { case (lang, items) =>
                <resource lang={lang}>
                  <label/>
                  <hint/>
                  { items.map(nodeInfoToElem) }
                </resource>
              }
            }</resources>
          val actualResources = resourceForNewControl()
          assertXMLDocumentsIgnoreNamespacesInScope(actualResources.toDocument, expectedResources.toDocument)
        }

        // Editing a form in English; English placeholders are added
        XFormsAPI.setvalue(FormBuilder.currentResources /@ "lang", "en")
        assertNewControlResources(Seq("en" -> itemTemplates("en")))

        // Switch language to Japanese; since we don't have FB resources, English is used
        XFormsAPI.setvalue(FormBuilder.currentResources /@ "lang", "jp")
        assertNewControlResources(Seq("jp" -> itemTemplates("en")))

        // Switch language to French; French placeholders are added
        XFormsAPI.setvalue(FormBuilder.currentResources /@ "lang", "fr")
        assertNewControlResources(Seq("fr" -> itemTemplates("fr")))

        // Add English, in addition to French
        XFormsAPI.insert(
          into = FormBuilder.currentResources.parentUnsafe,
         origin = FormBuilder.currentResources
        )
        XFormsAPI.setvalue(FormBuilder.currentResources /@ "lang", "en")
        assertNewControlResources(Seq("fr" -> itemTemplates("fr"), "en" -> itemTemplates("en")))
      }
    }
  }
}
