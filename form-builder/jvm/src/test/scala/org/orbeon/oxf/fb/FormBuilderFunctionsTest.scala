/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.orbeon.builder.rpc.FormBuilderRpcApiImpl
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fb.ToolboxOps._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.{FormRunner, Names, NodeInfoCell}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xml.{NamespaceMapping, TransformerUtils}
import org.orbeon.saxon.om._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.FunSpecLike

import scala.collection.mutable

// These functions run on a simplified "Form Builder" which loads a source form and goes through annotation.
class FormBuilderFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormBuilderSupport {

  val SectionsRepeatsDoc      = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"
  val SectionsGridsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids-repeats.xhtml"
  val SectionTemplatesDoc     = "oxf:/org/orbeon/oxf/fb/template-with-section-templates.xhtml"

  val Control1 = "control-1"
  val Control2 = "control-2"
  val Control3 = "control-3"
  val Section1 = "section-1"
  val Section2 = "section-2"

  describe("Model instance body elements") {
    withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

      val doc = ctx.formDefinitionRootElem

      it("must find the model") {
        assert(getModelElem(doc).getDisplayName === "xf:model")
        assert(getModelElem(doc).hasIdValue(Names.FormModel))
      }

      it("must find the instance") {
        assert((ctx.dataRootElem parent * head).name === "xf:instance")
      }

      it("must find the body group") {
        assert(ctx.bodyElem.uriQualifiedName === URIQualifiedName(XF, "group"))
      }
    }
  }

  describe("Name and id") {
    withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

      val doc = ctx.formDefinitionRootElem

      it("must return the control names") {
        assert(controlNameFromId(controlId(Control1)) === Control1)
        assert(controlNameFromId(bindId(Control1))    === Control1)
      }

      it("must find the control element") {
        assert(findControlByName(doc, Control1).get.uriQualifiedName === URIQualifiedName(XF, "input"))
        assert(findControlByName(doc, Control1).get.hasIdValue(controlId(Control1)))
      }
    }
  }

  describe("Control elements") {
    withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

      val doc = ctx.formDefinitionRootElem

      it("must find the bind element") {
        assert(findBindByName(doc, Control1).get.uriQualifiedName === URIQualifiedName(XF, "bind"))
        assert(findBindByName(doc, Control1).get.hasIdValue(bindId(Control1)))
      }

      it("must check the content of the value holder") {
        assert(findDataHolders(Control1).length == 1)
        assert(findDataHolders(Control1).head.getStringValue === "")
      }

      // TODO
      // controlResourceHolders
    }
  }

  describe("Section name") {
    withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

      val doc = ctx.formDefinitionRootElem

      it("must find the section name") {
        assert(findSectionName(doc, Control1).get === Section1)
        assert(getControlNameOpt(doc descendant "*:section" head).get === Section1)
      }
    }
  }

  describe("New binds") {
    it("must find the newly-created binds") {
      withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

        val doc = ctx.formDefinitionRootElem

        ensureBinds(List(Section1, Control2))

        assert(findBindByName(doc, Control2).get.uriQualifiedName === URIQualifiedName(XF, "bind"))
        assert(findBindByName(doc, Control2).get.hasIdValue(bindId(Control2)))

        ensureBinds(List(Section2, "grid-1", Control3))

        assert(findBindByName(doc, Control3).get.uriQualifiedName === URIQualifiedName(XF, "bind"))
        assert(findBindByName(doc, Control3).get.hasIdValue(bindId(Control3)))
      }
    }
  }

  describe("Find the next id") {
    it("must find ids without collisions") {
      withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒
        assert(nextId("control") === "control-2-control")
        assert(nextId("section") === "section-2-section")
      }
      // TODO: test more collisions
    }
  }

  describe("Containers") {
    withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

      val firstTd = ctx.bodyElem descendant NodeInfoCell.GridTest descendant NodeInfoCell.CellTest head

      val containers = findAncestorContainersLeafToRoot(firstTd)

      it("must find the containers") {
        assert(containers(0).localname === "grid")
        assert(containers(1).localname === "section")

        assert(findContainerNamesForModel(firstTd) === List("section-1"))
      }
    }
  }

  // Select the first grid cell (assume there is one)
  def selectFirstCell()(implicit ctx: FormBuilderDocContext): Unit =
    selectCell(ctx.bodyElem descendant NodeInfoCell.GridTest descendant NodeInfoCell.CellTest head)

  describe("Insert `xf:input` control") {
    it("must insert all elements in the right places") {
      withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

        val doc = ctx.formDefinitionRootElem

        // Insert a new control into the next empty td
        selectFirstCell()
        val newControlNameOption = insertNewControl(doc, <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>)

        // Check the control's name
        assert(newControlNameOption === Some("control-2"))
        val newControlName = newControlNameOption.get

        // Test result
        assert(findControlByName(doc, newControlName).get.hasIdValue(controlId(newControlName)))

        val newlySelectedCell = findSelectedCell
        assert(newlySelectedCell.isDefined)
        assert(newlySelectedCell.get / * /@ "id" === controlId(newControlName))

        val containerNames = findContainerNamesForModel(newlySelectedCell.get)
        assert(containerNames == List("section-1"))

        // NOTE: We should maybe just compare the XML for holders, binds, and resources
        val dataHolder = assertDataHolder(newControlName)
        assert((dataHolder.head precedingSibling * head).name === "control-1")

        val controlBind = findBindByName(doc, newControlName).get
        assert(controlBind.hasIdValue(bindId(newControlName)))
        assert((controlBind precedingSibling * att "id") === bindId("control-1"))

        assert(allResources(ctx.resourcesRootElem) / newControlName nonEmpty)

      }
    }
  }

  describe("Insert `fr:explanation` control") {
    it("must insert all elements in the right places") {
      withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

        val doc = ctx.formDefinitionRootElem

        // Insert explanation control
        val frExplanation = {
          val selectionControls = TransformerUtils.urlToTinyTree("oxf:/xbl/orbeon/explanation/explanation.xbl")
          val explanationBinding = selectionControls.rootElement.child("binding").head
          ToolboxOps.insertNewControl(doc, explanationBinding)
          doc.descendant("*:explanation").head
        }

        // Check resource holder just contains <text>, taken from the XBL metadata
        locally {
          val explanationResourceHolder = FormBuilder.resourcesRoot.child("resource").child(*).last
          val actual   = <holder> { explanationResourceHolder.child(*) map nodeInfoToElem } </holder>
          val expected = <holder><text/></holder>
          assertXMLDocumentsIgnoreNamespacesInScope(actual, expected)
        }

        // Check that the <fr:text ref=""> points to the corresponding <text> resource
        locally {
          val controlName = FormRunner.controlNameFromId(frExplanation.id)
          val actualRef = frExplanation.child("*:text").head.attValue("ref")
          val expectedRef = "$form-resources/" ++ controlName ++ "/text"
          assert(actualRef === expectedRef)
        }
      }
    }
  }

  describe("Insert repeat") {
    it("must insert all elements in the right places") {
      withActionAndFBDoc(TemplateDoc) { implicit ctx ⇒

        val doc = ctx.formDefinitionRootElem

        // Insert a new repeated grid after the current grid
        selectFirstCell()
        val newRepeatNameOption = insertNewRepeatedGrid(doc)

        assert(newRepeatNameOption === Some("grid-2"))
        val newRepeatName          = newRepeatNameOption.get
        val newRepeatIterationName = defaultIterationName(newRepeatName)

        locally {

          val newlySelectedCell = findSelectedCell
          assert(newlySelectedCell.isDefined)
          assert((newlySelectedCell flatMap (_ parent * headOption) head) /@ "id" === gridId(newRepeatName))

          val containerNames = findContainerNamesForModel(newlySelectedCell.get)
          assert(containerNames === List("section-1", newRepeatName, newRepeatIterationName))

          // NOTE: We should maybe just compare the XML for holders, binds, and resources
          val dataHolder = assertDataHolder(containerNames.init.last)
          assert((dataHolder.head precedingSibling * head).name === "control-1")

          val controlBind = findBindByName(doc, newRepeatName).get
          assert(controlBind.hasIdValue(bindId(newRepeatName)))
          assert((controlBind precedingSibling * att "id") === bindId("control-1"))

          assert(getModelElem(doc) / XFInstanceTest exists (_.hasIdValue("grid-2-template")))
        }

        // Insert a new control
        val newControlNameOption = insertNewControl(doc, <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>)

        assert(newControlNameOption === Some("control-2"))
        val newControlName = newControlNameOption.get

        // Test result
        locally {

          val newlySelectedCell = findSelectedCell
          assert(newlySelectedCell.isDefined)
          assert(newlySelectedCell.get / * /@ "id" === controlId(newControlName))

          val containerNames = findContainerNamesForModel(newlySelectedCell.get)
          assert(containerNames === List("section-1", newRepeatName, newRepeatIterationName))

          assert(findControlByName(doc, newControlName).get.hasIdValue(controlId(newControlName)))

          // NOTE: We should maybe just compare the XML for holders, binds, and resources
          val dataHolder = assertDataHolder(newControlName)
          assert(dataHolder.head precedingSibling * isEmpty)
          assert((dataHolder.head parent * head).name === newRepeatIterationName)

          val controlBind = findBindByName(doc, newControlName).get
          assert(controlBind.hasIdValue(bindId(newControlName)))
          assert((controlBind parent * head).hasIdValue(bindId(newRepeatIterationName)))

          assert(allResources(ctx.resourcesRootElem) / newControlName nonEmpty)

          val templateHolder = findTemplateRoot(newRepeatName).get / newControlName headOption

          assert(templateHolder.isDefined)
          assert(templateHolder.get precedingSibling * isEmpty)
          assert((templateHolder.get parent * head).name === newRepeatIterationName)
        }
      }
    }
  }

  describe("Allowed binding expression") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ ⇒
        val doc = this setupDocument
          <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
               xmlns:xh="http://www.w3.org/1999/xhtml"
               xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <xh:head>
              <xf:model xxf:xpath-analysis="true">

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
              </xf:model>
            </xh:head>
            <xh:body>
              <xf:group id="section-1-section" bind="section-1-bind">
                <xf:input id="control-1-control" bind="control-1-bind"/>
              </xf:group>
            </xh:body>
          </xh:html>

        withContainingDocument(doc) {
          val section1 = doc.getControlByEffectiveId("section-1-section")
          val control1 = doc.getControlByEffectiveId("control-1-control")

          assert(true  === DataModel.isAllowedBindingExpression(section1, "section-1")) // existing node
          assert(false === DataModel.isAllowedBindingExpression(section1, "foo/bar"))   // non-existing node
          assert(false === DataModel.isAllowedBindingExpression(section1, "("))         // invalid expression
          assert(true  === DataModel.isAllowedBindingExpression(section1, "/"))         // root node
          assert(true  === DataModel.isAllowedBindingExpression(section1, ".."))        // complex content

          assert(true  === DataModel.isAllowedBindingExpression(control1, "control-1")) // existing node
          assert(false === DataModel.isAllowedBindingExpression(control1, "foo/bar"))   // non-existing node
          assert(false === DataModel.isAllowedBindingExpression(control1, "("))         // invalid expression
          assert(false === DataModel.isAllowedBindingExpression(control1, "/"))         // root node
          assert(false === DataModel.isAllowedBindingExpression(control1, ".."))        // complex content
        }
      }
    }
  }

  describe("Control effective id") {
    it("must return the expected statics ids") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx ⇒

        val doc = ctx.formDefinitionRootElem

        val expected = Map(
          "|fb≡section-1-section≡grid-1-grid≡control-1-control|"                     → "control-1-control",
          "|fb≡section-1-section≡grid-4-grid≡control-5-control⊙1|"                   → "control-5-control",
          "|fb≡section-1-section≡section-3-section≡grid-2-grid≡control-6-control|"   → "control-6-control",
          "|fb≡section-1-section≡section-3-section≡grid-7-grid≡control-8-control⊙1|" → "control-8-control"
        )

        for ((expected, id) ← expected)
          assert(expected === buildFormBuilderControlAbsoluteIdOrEmpty(id))
      }
    }
  }

  describe("Analyze known constraint") {

    import CommonConstraint.analyzeKnownConstraint
    import org.orbeon.oxf.xforms.XFormsConstants._

    val Library = XFormsFunctionLibrary

    val Mapping =
      NamespaceMapping(
        Map(
          XFORMS_PREFIX        → XFORMS_NAMESPACE_URI,
          XFORMS_SHORT_PREFIX  → XFORMS_NAMESPACE_URI,
          XXFORMS_PREFIX       → XXFORMS_NAMESPACE_URI,
          XXFORMS_SHORT_PREFIX → XXFORMS_NAMESPACE_URI
        )
      )

    val Logger  = new IndentedLogger(LoggerFactory.createLogger(classOf[FormBuilderFunctionsTest]), true)

    it("must pass all common constraints") {
      assert(Some("max-length"        → Some("5"))                          === analyzeKnownConstraint("xxf:max-length(5)",                                   Mapping, Library)(Logger))
      assert(Some("min-length"        → Some("5"))                          === analyzeKnownConstraint("xxf:min-length(5)",                                   Mapping, Library)(Logger))
      assert(Some("min-length"        → Some("5"))                          === analyzeKnownConstraint("xxf:min-length('5')",                                 Mapping, Library)(Logger))
      assert(Some("min-length"        → Some("5"))                          === analyzeKnownConstraint("(xxf:min-length(5))",                                 Mapping, Library)(Logger))
      assert(Some("non-negative"      → None)                               === analyzeKnownConstraint("(xxf:non-negative())",                                Mapping, Library)(Logger))
      assert(Some("negative"          → None)                               === analyzeKnownConstraint("(xxf:negative())",                                    Mapping, Library)(Logger))
      assert(Some("non-positive"      → None)                               === analyzeKnownConstraint("(xxf:non-positive())",                                Mapping, Library)(Logger))
      assert(Some("positive"          → None)                               === analyzeKnownConstraint("(xxf:positive())",                                    Mapping, Library)(Logger))
      assert(Some("upload-max-size"   → Some("3221225472"))                 === analyzeKnownConstraint("xxf:upload-max-size(3221225472)",                     Mapping, Library)(Logger))
      assert(Some("upload-mediatypes" → Some("image/jpeg application/pdf")) === analyzeKnownConstraint("xxf:upload-mediatypes('image/jpeg application/pdf')", Mapping, Library)(Logger))
      assert(None                                                           === analyzeKnownConstraint("xxf:min-length(foo)",                                 Mapping, Library)(Logger))
      assert(None                                                           === analyzeKnownConstraint("xxf:foobar(5)",                                       Mapping, Library)(Logger))
    }
  }

  def assertDataHolder(holderName: String)(implicit ctx: FormBuilderDocContext): List[NodeInfo] = {
    val dataHolder = findDataHolders(holderName)
    assert(dataHolder.length == 1)
    dataHolder
  }

  describe("Clipboard cut and paste") {

    it("Simple cut/paste must remove and restore the control, bind, holders, and resources") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx ⇒

        val doc = ctx.formDefinitionRootElem

        val selectedCell = FormBuilder.findSelectedCell.get

        def assertPresent() = {
          assert(Control1 === FormRunner.getControlName(selectedCell / * head))
          assert(FormRunner.findControlByName(doc, Control1).nonEmpty)
          assert(FormRunner.findBindByName(doc, Control1).nonEmpty)
          assert(FormBuilder.findDataHolders(Control1).nonEmpty)
          assert(FormBuilder.findCurrentResourceHolder(Control1).nonEmpty)
        }

        assertPresent()

        ToolboxOps.cutToClipboard(selectedCell)

        // Selected cell hasn't changed
        assert(FormBuilder.findSelectedCell contains selectedCell)

        assert(FormRunner.findControlByName(doc, Control1).isEmpty)
        assert(FormRunner.findBindByName(doc, Control1).isEmpty)
        assert(FormBuilder.findDataHolders(Control1).isEmpty)
        assert(FormBuilder.findCurrentResourceHolder(Control1).isEmpty)

        ToolboxOps.pasteFromClipboard()

        assertPresent()
      }
    }

    val containerIds =
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx ⇒
        findNestedContainers(ctx.bodyElem).to[List] map (_.id)
      }

    containerIds foreach { containerId ⇒
      it(s"Must be able to cut and paste container `$containerId`") {
        withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx ⇒

          val doc = ctx.formDefinitionRootElem

          def countContainers = FormBuilderXPathApi.countAllContainers(doc)

          ToolboxOps.selectFirstCellInContainer(containerById(containerId))

          val nestedControlName =
            FormBuilder.findContainerById(containerId).toList flatMap findNestedControls flatMap getControlNameOpt head

          val initialContainerCount = countContainers

          // Before and after cut
          locally {
            assert(FormBuilder.findContainerById(containerId).nonEmpty)
            assert(FormRunner.findControlByName(doc, nestedControlName).nonEmpty)

            FormBuilderRpcApiImpl.containerCut(containerId)

            assert(FormBuilder.findContainerById(containerId).isEmpty)
            assert(FormRunner.findControlByName(doc, nestedControlName).isEmpty)

            assert(initialContainerCount > countContainers)
          }

          val cutContainersCount = initialContainerCount - countContainers

          // Paste without prefix/suffix (shouldn't conflict)
          locally {
            ToolboxOps.pasteFromClipboard()
            assert(FormBuilder.findContainerById(containerId).nonEmpty)
            assert(FormRunner.findControlByName(doc, nestedControlName).nonEmpty)
            assert(initialContainerCount === countContainers)
          }

          // Paste with prefix
          locally {
            val Prefix = "my-"
            FormBuilderXPathApi.pasteSectionGridFromClipboard(Prefix, "")
            assert(FormBuilder.findContainerById(Prefix + containerId).nonEmpty)
            assert(FormRunner.findControlByName(doc, Prefix + nestedControlName).nonEmpty)
            assert(initialContainerCount + cutContainersCount === countContainers)
          }

          // Paste with suffix
          locally {
            val Suffix = "-nice"
            FormBuilderXPathApi.pasteSectionGridFromClipboard("", Suffix)
            assert(findControlByName(ctx.formDefinitionRootElem, controlNameFromId(containerId) + Suffix).nonEmpty)
            assert(FormRunner.findControlByName(doc, nestedControlName + Suffix).nonEmpty)
            assert(initialContainerCount + cutContainersCount * 2 === countContainers)
          }

          // Paste without prefix/suffix (should create automatic ids and not crash)
          locally {
            FormBuilderXPathApi.pasteSectionGridFromClipboard("", "")
            assert(initialContainerCount + cutContainersCount * 3 === countContainers)
          }
        }
      }
    }
  }

  describe("Section template merging") {

    val SectionsNamesAndControls = List(
      "section-1" → 1,
      "section-2" → 3,
      "section-3" → 0,
      "section-4" → 0,
      "section-5" → 2
    )

    val SectionNames = SectionsNamesAndControls map (_._1)
    val SectionIds   = SectionNames map (_ + "-section")

    def assertSectionsKeepName()(implicit ctx: FormBuilderDocContext) =
      for (sectionName ← SectionNames)
        assert(findControlByName(ctx.formDefinitionRootElem, sectionName).isDefined, s"for $sectionName")

    def assertUniqueIds()(implicit ctx: FormBuilderDocContext) = {

      val visited = mutable.Set[String]()

      idsIterator(Right(ctx.formDefinitionRootElem)) foreach { id ⇒
        assert(! visited(id), s"duplicate id `$id`")
        visited += id
      }
    }

    it("Must merge all section templates without errors") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx ⇒

        assertSectionsKeepName()
        assertUniqueIds()

        for (sectionId ← SectionIds)
          ToolboxOps.containerMerge(sectionId, "", "")

        assertSectionsKeepName()
        assertUniqueIds()
      }
    }

    it("Must merge all section templates using a prefix") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx ⇒

        assertSectionsKeepName()
        assertUniqueIds()

        // First 2 sections will have `my-` prefixes, but other 2 sections not as they are the same
        // section templates and using `my-` would cause conflicts.
        for (sectionId ← SectionIds)
          ToolboxOps.containerMerge(sectionId, "my-", "")

        assertSectionsKeepName()
        assertUniqueIds()

        for ((sectionName, expectedCount) ← SectionsNamesAndControls) {

          val nestedControlsStartingWithMyCount =
            findNestedControls(findControlByName(ctx.formDefinitionRootElem, sectionName).get) flatMap
            getControlNameOpt count
            (_ startsWith "my-")

          assert(expectedCount === nestedControlsStartingWithMyCount, s"for `$sectionName`")
        }
      }
    }

  }

}