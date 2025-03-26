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

import cats.syntax.option.*
import org.orbeon.datatypes.Direction
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fb.ToolboxOps.*
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XMLSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.saxon.om.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, *}
import org.orbeon.xforms.Namespaces
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.mutable


// These functions run on a simplified "Form Builder" which loads a source form and goes through annotation.
class FormBuilderFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport
     with XMLSupport {

  val SectionsRepeatsDoc  = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"
  val SectionTemplatesDoc = "oxf:/org/orbeon/oxf/fb/template-with-section-templates.xhtml"
  val FormulasDoc         = "oxf:/org/orbeon/oxf/fb/template-with-controls-to-rename.xhtml"

  val Control1 = "control-1"
  val Control2 = "control-2"
  val Control3 = "control-3"
  val Section1 = "section-1"
  val Section2 = "section-2"

  describe("Model instance body elements") {
    withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

      val doc = ctx.formDefinitionRootElem

      it("must find the model") {
        assert(getModelElem(doc).getDisplayName == "xf:model")
        assert(getModelElem(doc).hasIdValue(Names.FormModel))
      }

      it("must find the instance") {
        assert((ctx.dataRootElem parent * head).name == "xf:instance")
      }

      it("must find the body group") {
        assert(ctx.bodyElem.uriQualifiedName == URIQualifiedName(XF, "group"))
      }
    }
  }

  describe("Name and id") {
    withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

      it("must return the control names") {
        assert(controlNameFromId(controlId(Control1)) == Control1)
        assert(controlNameFromId(bindId(Control1))    == Control1)
      }

      it("must find the control element") {
        assert(findControlByName(Control1).get.uriQualifiedName == URIQualifiedName(XF, "input"))
        assert(findControlByName(Control1).get.hasIdValue(controlId(Control1)))
      }
    }
  }

  describe("Control elements") {
    withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

      it("must find the bind element") {
        assert(findBindByName(Control1).get.uriQualifiedName == URIQualifiedName(XF, "bind"))
        assert(findBindByName(Control1).get.hasIdValue(bindId(Control1)))
      }

      it("must check the content of the value holder") {
        assert(findDataHolders(Control1).length == 1)
        assert(findDataHolders(Control1).head.getStringValue == "")
      }

      // TODO
      // controlResourceHolders
    }
  }

  describe("Section name") {
    withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

      val doc = ctx.formDefinitionRootElem

      it("must find the section name") {
        assert(findSectionName(Control1).get == Section1)
        assert(getControlNameOpt(doc descendant "*:section" head).get == Section1)
      }
    }
  }

  describe("New binds") {
    it("must find the newly-created binds") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

        ensureBinds(List(Section1, Control2))

        assert(findBindByName(Control2).get.uriQualifiedName == URIQualifiedName(XF, "bind"))
        assert(findBindByName(Control2).get.hasIdValue(bindId(Control2)))

        ensureBinds(List(Section2, "grid-1", Control3))

        assert(findBindByName(Control3).get.uriQualifiedName == URIQualifiedName(XF, "bind"))
        assert(findBindByName(Control3).get.hasIdValue(bindId(Control3)))
      }
    }
  }

  describe("Find the next id") {
    it("must find ids without collisions") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>
        assert(nextId("control") == "control-2-control")
        assert(nextId("section") == "section-2-section")
      }
      // TODO: test more collisions
    }
  }

  describe("Containers") {
    withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

      val firstTd = ctx.bodyElem descendant NodeInfoCell.GridTest descendant NodeInfoCell.CellTest head

      val containers = findAncestorContainersLeafToRoot(firstTd)

      it("must find the containers") {
        assert(containers(0).localname == "grid")
        assert(containers(1).localname == "section")

        assert(findContainerNamesForModel(firstTd) == List("section-1", "grid-1"))
      }
    }
  }

  // Select the first grid cell (assume there is one)
  def selectFirstCell()(implicit ctx: FormBuilderDocContext): Unit =
    selectCell(ctx.bodyElem descendant NodeInfoCell.GridTest descendant NodeInfoCell.CellTest head)

  describe("Insert `xf:input` control") {
    it("must insert all elements in the right places") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

        // Insert a new control into the next empty td
        selectFirstCell()
        val newControlNameOption = insertNewControl(<binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>)

        // Check the control's name
        assert(newControlNameOption.contains("control-2"))
        val newControlName = newControlNameOption.get

        // Test result
        assert(findControlByName(newControlName).get.hasIdValue(controlId(newControlName)))

        val newlySelectedCell = findSelectedCell
        assert(newlySelectedCell.isDefined)
        assert((newlySelectedCell.get / *).ids == List(controlId(newControlName)))

        val containerNames = findContainerNamesForModel(newlySelectedCell.get)
        assert(containerNames == List("section-1", "grid-1"))

        // NOTE: We should maybe just compare the XML for holders, binds, and resources
        val dataHolder = assertDataHolder(newControlName)
        assert((dataHolder.head precedingSibling * head).name == "control-1")

        val controlBind = findBindByName(newControlName).get
        assert(controlBind.hasIdValue(bindId(newControlName)))
        assert((controlBind precedingSibling *).ids == List(bindId("control-1")))

        assert(allResources(ctx.resourcesRootElem) / newControlName nonEmpty)

      }
    }
  }

  describe("Insert `fr:explanation` control") {
    it("must insert all elements in the right places") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        // Insert explanation control
        val frExplanation = {
          val selectionControls = TransformerUtils.urlToTinyTree("oxf:/xbl/orbeon/explanation/explanation.xbl")
          val explanationBinding = selectionControls.rootElement.child("binding").head
          ToolboxOps.insertNewControl(explanationBinding)
          doc.descendant("*:explanation").head
        }

        // Check resource holder just contains <text>, taken from the XBL metadata
        locally {
          val explanationResourceHolder = FormBuilder.resourcesRoot.child("resource").child(*).last
          val actual   = <holder> { explanationResourceHolder.child(*) map nodeInfoToElem } </holder>
          val expected = <holder><text/></holder>
          assertXMLDocumentsIgnoreNamespacesInScope(expected.toDocument, actual.toDocument)
        }

        // Check that the <fr:text ref=""> points to the corresponding <text> resource
        locally {
          val controlName = FormRunner.controlNameFromId(frExplanation.id)
          val actualRef = frExplanation.child("*:text").head.attValue("ref")
          val expectedRef = "$form-resources/" ++ controlName ++ "/text"
          assert(actualRef == expectedRef)
        }
      }
    }
  }

  describe("Insert repeat") {
    it("must insert all elements in the right places") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        // Insert a new repeated grid after the current grid
        selectFirstCell()
        val newRepeatNameOption = insertNewGrid(repeated = true)

        assert(newRepeatNameOption.contains("grid-2"))
        val newRepeatName          = newRepeatNameOption.get
        val newRepeatIterationName = defaultIterationName(newRepeatName)

        locally {

          val newlySelectedCell = findSelectedCell
          assert(newlySelectedCell.isDefined)
          assert((newlySelectedCell flatMap (_ parent * headOption) head).id == gridId(newRepeatName))

          val containerNames = findContainerNamesForModel(newlySelectedCell.get)
          assert(containerNames == List("section-1", newRepeatName, newRepeatIterationName))

          // NOTE: We should maybe just compare the XML for holders, binds, and resources
          val dataHolder = assertDataHolder(containerNames.init.last)
          assert((dataHolder.head precedingSibling * head).name == "grid-1")

          val controlBind = findBindByName(newRepeatName).get
          assert(controlBind.hasIdValue(bindId(newRepeatName)))
          assert((controlBind precedingSibling *).ids == List(bindId("grid-1")))

          assert(getModelElem(doc) / XFInstanceTest exists (_.hasIdValue("grid-2-template")))
        }

        // Insert a new control
        val newControlNameOption = insertNewControl(<binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>)

        assert(newControlNameOption.contains("control-2"))
        val newControlName = newControlNameOption.get

        // Test result
        locally {

          val newlySelectedCell = findSelectedCell
          assert(newlySelectedCell.isDefined)
          assert((newlySelectedCell.get / *).ids == List(controlId(newControlName)))

          val containerNames = findContainerNamesForModel(newlySelectedCell.get)
          assert(containerNames == List("section-1", newRepeatName, newRepeatIterationName))

          assert(findControlByName(newControlName).get.hasIdValue(controlId(newControlName)))

          // NOTE: We should maybe just compare the XML for holders, binds, and resources
          val dataHolder = assertDataHolder(newControlName)
          assert(dataHolder.head precedingSibling * isEmpty)
          assert((dataHolder.head parent * head).name == newRepeatIterationName)

          val controlBind = findBindByName(newControlName).get
          assert(controlBind.hasIdValue(bindId(newControlName)))
          assert((controlBind parent * head).hasIdValue(bindId(newRepeatIterationName)))

          assert(allResources(ctx.resourcesRootElem) / newControlName nonEmpty)

          val templateHolder = findTemplateRoot(newRepeatName).get / newControlName headOption

          assert(templateHolder.isDefined)
          assert(templateHolder.get precedingSibling * isEmpty)
          assert((templateHolder.get parent * head).name == newRepeatIterationName)
        }
      }
    }
  }

  describe("Allowed binding expression") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ =>
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
          </xh:html>.toDocument

        withContainingDocument(doc) {
          val section1 = doc.getControlByEffectiveId("section-1-section")
          val control1 = doc.getControlByEffectiveId("control-1-control")

          assert(  DataModel.isAllowedBindingExpression(section1, "section-1")) // existing node
          assert(! DataModel.isAllowedBindingExpression(section1, "foo/bar"))   // non-existing node
          assert(! DataModel.isAllowedBindingExpression(section1, "("))         // invalid expression
          assert(  DataModel.isAllowedBindingExpression(section1, "/"))         // root node
          assert(  DataModel.isAllowedBindingExpression(section1, ".."))        // complex content

          assert(  DataModel.isAllowedBindingExpression(control1, "control-1")) // existing node
          assert(! DataModel.isAllowedBindingExpression(control1, "foo/bar"))   // non-existing node
          assert(! DataModel.isAllowedBindingExpression(control1, "("))         // invalid expression
          assert(! DataModel.isAllowedBindingExpression(control1, "/"))         // root node
          assert(! DataModel.isAllowedBindingExpression(control1, ".."))        // complex content
        }
      }
    }
  }

  describe("Control effective id") {
    it("must return the expected statics ids") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx =>

        val expected = Map(
          "|fb≡section-1-section≡grid-1-grid≡control-1-control|"                     -> "control-1-control",
          "|fb≡section-1-section≡grid-4-grid≡control-5-control⊙1|"                   -> "control-5-control",
          "|fb≡section-1-section≡section-3-section≡grid-2-grid≡control-6-control|"   -> "control-6-control",
          "|fb≡section-1-section≡section-3-section≡grid-7-grid≡control-8-control⊙1|" -> "control-8-control"
        )

        for ((expected, id) <- expected)
          assert(expected == buildFormBuilderControlAbsoluteIdOrEmpty(id))
      }
    }
  }

  def assertDataHolder(holderName: String)(implicit ctx: FormBuilderDocContext): List[NodeInfo] = {
    val dataHolder = findDataHolders(holderName)
    assert(dataHolder.length == 1)
    dataHolder
  }

  describe("Section template merging") {

    val SectionsNamesAndControls = List(
      "section-1" -> 1,
      "section-2" -> 3,
      "section-3" -> 0,
      "section-4" -> 0,
      "section-5" -> 2
    )

    val SectionNames = SectionsNamesAndControls map (_._1)
    val SectionIds   = SectionNames map (_ + "-section")

    def assertSectionsKeepName(sectionNames: List[String])(implicit ctx: FormBuilderDocContext): Unit =
      for (sectionName <- sectionNames)
        assert(findControlByName(sectionName).isDefined, s"for $sectionName")

    def assertUniqueIds()(implicit ctx: FormBuilderDocContext): Unit = {

      val visited = mutable.Set[String]()

      idsIterator(Right(ctx.formDefinitionRootElem)) foreach { id =>
        assert(! visited(id), s"duplicate id `$id`")
        visited += id
      }
    }

    it("Must merge all section templates without errors") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>

        assertSectionsKeepName(SectionNames)
        assertUniqueIds()

        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1, None, None, "new")

        for (sectionId <- SectionIds)
          ToolboxOps.containerMerge(sectionId, "", "")

        assertSectionsKeepName(SectionNames)
        assertUniqueIds()
      }
    }

    it("Must merge all section templates using a prefix") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>

        assertSectionsKeepName(SectionNames)
        assertUniqueIds()

        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1, None, None, "new")

        // First 2 sections will have `my-` prefixes, but other 2 sections not as they are the same
        // section templates and using `my-` would cause conflicts.
        for (sectionId <- SectionIds)
          ToolboxOps.containerMerge(sectionId, "my-", "")

        assertSectionsKeepName(SectionNames)
        assertUniqueIds()

        for ((sectionName, expectedCount) <- SectionsNamesAndControls) {

          val nestedControlsStartingWithMyCount =
            findNestedControls(findControlByName(sectionName).get) flatMap
            getControlNameOpt count
            (_ startsWith "my-")

          assert(expectedCount == nestedControlsStartingWithMyCount, s"for `$sectionName`")
        }
      }
    }

    it("Must merge a section template that is the only section") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>

        implicit val formRunnerParams = FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1, None, None, "new")

        // Remove all but one section
        for (i <- 0 to 4)
          FormBuilder.deleteSectionByIdIfPossible(s"section-$i-section")

        assert(FormBuilderXPathApi.countAllContainers(ctx.formDefinitionRootElem) == 1)

        ToolboxOps.containerMerge("section-5-section", "", "")

        // Check that all section template controls are present
        assert(findControlByName("section-5").isDefined)
        assert(findControlByName("grid-5").isDefined)
        assert(findControlByName("control-1").isDefined)
        assert(findControlByName("control-2").isDefined)

        assert(FormBuilderXPathApi.countAllContainers(ctx.formDefinitionRootElem) == 2)
        assert(FormBuilderXPathApi.countAllNonContainers(ctx.formDefinitionRootElem) == 2)

        assertUniqueIds()
      }
    }

    it("Must merge a section template nested in a section") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>

        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1, None, None, "new")

        // Remove all but two sections
        for (i <- 2 to 5)
          FormBuilder.deleteSectionByIdIfPossible(s"section-$i-section")

        assert(FormBuilderXPathApi.countAllContainers(ctx.formDefinitionRootElem) == 3)

        // Move the second one into the first one
        moveSectionRight(findControlByName("section-1").get)
        assert(FormBuilderXPathApi.countAllContainers(ctx.formDefinitionRootElem) == 3)

        ToolboxOps.containerMerge("section-1-section", "", "")

        // Check resulting structure
        assert(findControlByName("section-1").isDefined)
        assert(findControlByName("grid-1").isDefined)
        assert(findControlByName("control-11").isDefined)

        assert(FormBuilderXPathApi.countAllContainers(ctx.formDefinitionRootElem) == 4)
        assert(FormBuilderXPathApi.countAllNonContainers(ctx.formDefinitionRootElem) == 3)

        assertUniqueIds()
      }
    }
  }

  describe("Renaming of variable references in formulas") {

    import org.orbeon.oxf.fb.XMLNames.*
    import org.orbeon.oxf.fr.XMLNames.*
    import org.orbeon.xforms.XFormsNames.*

    describe("Variable and LHHA references") {
      withActionAndFBDoc(FormulasDoc) { implicit ctx =>

        val RenamedBinds: NodeInfo =
          <xf:bind
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
            id="formulas-bind" ref="formulas" name="formulas">
            <xf:bind id="formulas-grid-bind" ref="formulas-grid" name="formulas-grid">
              <xf:bind id="calculated-bind" ref="calculated" name="calculated" fb:calculate="concat($qux, $baz, $toto, $gaga)"
                       fb:readonly="false()"/>
              <xf:bind id="initial-bind" ref="initial" name="initial" fb:default="concat($qux, $baz, $toto, $gaga)"/>
              <xf:bind id="readonly-bind" ref="readonly" name="readonly" xxf:whitespace="trim" fb:readonly="$qux = $baz or $qux = $toto"/>
              <xf:bind id="visibility-bind" ref="visibility" name="visibility" xxf:whitespace="trim"
                       fb:relevant="$gaga = $toto or $baz = $toto"/>
              <xf:bind id="control-3-bind" ref="control-3" name="control-3" xxf:whitespace="trim">
                <fb:constraint id="validation-2-validation" level="warning" value="string-length($qux) lt string-length($baz)"/>
              </xf:bind>
              <xf:bind id="control-2-bind" ref="control-2" name="control-2" xxf:whitespace="trim">
                <fb:constraint id="validation-1-validation" value="string-length($qux) lt string-length($baz)"/>
              </xf:bind>
              <xf:bind id="control-1-bind" ref="control-1" name="control-1" xxf:whitespace="trim"
                       fb:constraint="string-length($qux) lt string-length($baz)"/>
              <xf:bind id="required-bind" ref="required" name="required" xxf:whitespace="trim" required="xxf:is-blank($baz)"/>
              <xf:bind id="multiple-bind" ref="multiple" name="multiple" xxf:whitespace="trim" required="xxf:non-blank($qux)"
                       fb:default="$baz" fb:calculate="$qux" fb:relevant="$qux = '42'" fb:readonly="$toto = 'a' and $gaga = 'b'">
                <fb:constraint id="validation-5-validation" value="string-length(concat($baz, $gaga)) lt 10"/>
                <fb:constraint id="validation-6-validation" value="$toto != $gaga"/>
              </xf:bind>
              <xf:bind id="calculated-from-section-template-bind" ref="calculated-from-section-template" name="calculated-from-section-template"
                       fb:calculate="fr:control-string-value('my-control-in-library-section', false(), 'my-section-containing-template-renamed'), fr:control-typed-value('my-control-in-library-section', false(), 'my-section-containing-template-renamed')"/>
              <xf:bind id="calculated-using-direct-function-call-bind" ref="calculated-using-direct-function-call" name="calculated-using-direct-function-call"
                       fb:calculate="concat(fr:control-string-value('qux', false()), fr:control-string-value('baz', false()), fr:control-string-value('toto', false()), fr:control-string-value('gaga', false()))"
                       fb:readonly="false()"/>
            </xf:bind>
          </xf:bind>

        val RenamedControlNameParams: NodeInfo =
          <xf:label
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            ref="$form-resources/label-with-control-ref/label">
            <fr:param type="ControlValueParam">
              <fr:name>my-foo</fr:name>
              <fr:controlName>qux</fr:controlName>
            </fr:param>
            <fr:param type="ControlValueParam">
              <fr:name>my-bar</fr:name>
              <fr:controlName>baz</fr:controlName>
            </fr:param>
          </xf:label>

        val RenamedExprParams: NodeInfo =
          <xf:label
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            ref="$form-resources/label-with-formula/label">
            <fr:param type="ExpressionParam">
              <fr:name>my-formula</fr:name>
              <fr:expr>string-join(($qux, $baz), ' - ')</fr:expr>
            </fr:param>
          </xf:label>

        val RenamedEmail: NodeInfo =
          <email>
            <templates>
              <template name="default" xml:lang="en">
                <headers/>
                <enable-if-true>$qux = 'value'</enable-if-true>
                <subject>Subject: {{$my-foo}} with {{$my-bar}}</subject>
                <body mediatype="text/html">&lt;div&gt;Body: {{$her-foo}} with {{$her-bar}}&lt;/div&gt;</body>
                <exclude-from-all-control-values/>
              </template>
            </templates>
            <parameters>
              <param type="ControlValueParam">
                <name>my-foo</name>
                <controlName>qux</controlName>
              </param>
              <param type="ControlValueParam">
                <name>my-bar</name>
                <controlName>baz</controlName>
              </param>
              <param type="ControlValueParam">
                <name>her-foo</name>
                <controlName>qux</controlName>
              </param>
              <param type="ControlValueParam">
                <name>her-bar</name>
                <controlName>baz</controlName>
              </param>
            </parameters>
        </email>

        FormBuilder.renameControlReferences("foo", "qux", "foo-control".some)
        FormBuilder.renameControlReferences("bar", "baz", "bar-control".some)
        FormBuilder.renameControlReferences("my-section-containing-template", "my-section-containing-template-renamed")

        it("must rename references from variables") {
          assertXMLElementsIgnoreNamespacesInScope(
            left  = RenamedBinds,
            right = ctx.topLevelBindElem.toList child XFBindTest filter (_.id == "formulas-bind") head
          )
        }

        it("must rename control references from LHHA") {
          assertXMLElementsIgnoreNamespacesInScope(
            left  = RenamedControlNameParams,
            right = ctx.bodyElem descendant * filter (_.id == "label-with-control-ref-control") child LABEL_QNAME head
          )
        }

        it("must rename expression references from LHHA") {
          assertXMLElementsIgnoreNamespacesInScope(
            left  = RenamedExprParams,
            right = ctx.bodyElem descendant * filter (_.id == "label-with-formula-control") child LABEL_QNAME head
          )
        }

        it("must rename references from email templates") {
          assertXMLElementsIgnoreNamespacesInScope(
            left  = RenamedEmail,
            right = ctx.metadataRootElem descendant "email" head
          )
        }
      }
    }

    describe("Legacy actions") {
      withActionAndFBDoc(FormulasDoc) { implicit ctx =>

        val RenamedLegacyAction: NodeInfo =
          <fb:action
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:ev="http://www.w3.org/2001/xml-events"
            xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
            id="my-action-binding">
            <xf:action fb:event="xforms-value-changed" ev:observer="qux-control" if="true()">
              <xf:send submission="echo-submission"/>
            </xf:action>
            <xf:action fb:event="xforms-submit" ev:observer="echo-submission">
              <xf:var name="request-instance-name" value="'echo-instance'"/>
              <xf:action>
                <xf:action class="fr-set-service-value-action">
                  <xf:var name="control-name" value="'qux'"/>
                  <xf:var name="path" value="/*/foo"/>
                </xf:action>
                <xf:action class="fr-set-service-value-action">
                  <xf:var name="control-name" value="'baz'"/>
                  <xf:var name="path" value="/*/bar"/>
                </xf:action>
              </xf:action>
            </xf:action>
            <xf:action fb:event="xforms-submit-done" ev:observer="echo-submission">
              <xf:action class="fr-set-control-value-action">
                <xf:var name="control-name" value="'xyzzy'"/>
                <xf:var name="control-value" value="/*/toto"/>
              </xf:action>
              <xf:action class="fr-set-control-value-action">
                <xf:var name="control-name" value="'gaga'"/>
                <xf:var name="control-value" value="/*/gaga"/>
              </xf:action>
              <xf:action class="fr-itemset-action">
                <xf:var name="control-name" value="'her-dropdown'"/>
                <xf:var name="response-items" value="/*/values"/>
                <xf:var name="item-label" value="label"/>
                <xf:var name="item-value" value="value"/>
              </xf:action>
            </xf:action>
          </fb:action>

        FormBuilder.renameControlReferences("foo",         "qux",          "foo-control".some)
        FormBuilder.renameControlReferences("bar",         "baz",          "bar-control".some)
        FormBuilder.renameControlReferences("toto",        "xyzzy",        "toto-control".some)
        FormBuilder.renameControlReferences("my-dropdown", "her-dropdown", "my-dropdown-control".some)

        it("must rename references from actions") {
          assertXMLElementsIgnoreNamespacesInScope(
            left  = RenamedLegacyAction,
            right = ctx.modelElem child FBActionTest head
          )
        }
      }
    }

    describe("New actions") {
      withActionAndFBDoc(FormulasDoc) { implicit ctx =>

        val RenamedListener: NodeInfo =
          <fr:listener
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            version="2018.2"

            modes="new"
            events="enabled value-changed activated"
            controls="control-qux control-gaga"

            actions="my-action"/>

        val RenamedAction: NodeInfo =
          <fr:action
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            name="my-action"
            version="2018.2">

            <fr:control-setvalue value="'before'" control="control-before"/>

            <fr:service-call service="my-service-gaga">
              <fr:value control="control1" ref="/request/control1"/>
              <fr:value control="control2" ref="/request/control2"/>
              <fr:url-param control="control3" name="param3"/>
            </fr:service-call>

            <fr:control-setvalue value="/response/foo" control="control-qux"/>
            <fr:control-setvalue value="/response/bar" control="control-baz"/>

            <fr:service-call service="my-service-toto">
              <fr:value control="control4" ref="/request/control4"/>
              <fr:url-param control="control5" name="param5"/>
              <fr:sql-param control="control6" index="2"/>
              <fr:value value="current-date()" ref="/request/value7"/>
            </fr:service-call>

            <fr:control-setvalue value="'after'" control="control-after"/>
            <fr:dataset-write name="my-dataset"/>

            <fr:if condition="true()">
              <fr:control-setvalue value="'in-if'" control="control-in-if"/>
            </fr:if>

            <fr:repeat-clear repeat="her-grid"/>

            <fr:data-iterate ref="/response/row">
              <fr:repeat-add-iteration repeat="her-grid" at="end"/>
              <fr:control-setvalue value="name" control="my-name" at="end"/>
              <fr:control-setitems items="item" label="@label" value="@value" control="her-dropdown" at="end"/>

              <fr:control-setattachment control="my-attachment" at="end"/>
              <fr:control-setfilename control="my-attachment" at="end" value="'My Image.png'"/>
              <fr:control-setmediatype control="my-attachment" at="end" value="'image/png'"/>
            </fr:data-iterate>

            <fr:repeat-remove-iteration repeat="her-grid" at="start"/>

            <fr:data-iterate ref="/response/row[exists(foo)]">

              <fr:process-call scope="oxf.fr.detail.process" name="acme-process"/>

              <fr:service-call service="my-service-get-attachment">
                <fr:url-param name="my-attachment-id" value="foo"/>
              </fr:service-call>

              <fr:repeat-add-iteration repeat="her-grid" at="end"/>
              <fr:control-setattachment control="my-attachment" at="end"/>
            </fr:data-iterate>

            <fr:process-call scope="oxf.fr.detail.process" name="summary"/>
            <fr:navigate location="https://www.bbc.com/news"/>
            <fr:navigate location="https://www.bbc.com/news" target="_blank"/>

          </fr:action>

        FormBuilder.renameControlReferences("control-foo", "control-qux",  None)
        FormBuilder.renameControlReferences("control-bar", "control-baz",  None)
        FormBuilder.renameControlReferences("my-grid",     "her-grid",     None)
        FormBuilder.renameControlReferences("my-dropdown", "her-dropdown", None)

        it("must rename references from listeners") {
          assertXMLElementsIgnoreNamespacesInScope(
            left  = RenamedListener,
            right = ctx.modelElem child FRListenerTest head
          )
        }

        it("must rename references from actions") {
          assertXMLElementsIgnoreNamespacesInScope(
            left  = RenamedAction,
            right = ctx.modelElem child FRActionTest head
          )
        }
      }
    }

    describe("Selection controls") {
      withActionAndFBDoc(FormulasDoc) { implicit ctx =>

        val RenamedDataboundSelect1: NodeInfo =
          <fr:databound-select1
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            xmlns:xf="http://www.w3.org/2002/xforms"
            id="dynamic-drodpown-control"
            resource="/fr/service/custom/orbeon/controls/countries?all=true&amp;my-param={$qux}"
            bind="dynamic-drodpown-bind">
            <xf:label ref="$form-resources/dynamic-drodpown/label"/>
            <xf:hint ref="$form-resources/dynamic-drodpown/hint"/>
            <xf:alert ref="$fr-resources/detail/labels/alert"/>
            <xf:itemset ref="//*[name() = $qux]">
              <xf:label ref="*[name() = $baz]"/>
              <xf:value ref="*[name() = $qux]"/>
              <xf:hint  ref="*[name() = $baz]"/>
            </xf:itemset>
          </fr:databound-select1>

        FormBuilder.renameControlReferences("foo", "qux", "foo-control".some)
        FormBuilder.renameControlReferences("bar", "baz", "bar-control".some)

        it("must rename references from `resource` attribute") {
          assertXMLElementsIgnoreNamespacesInScope(
            left  = RenamedDataboundSelect1,
            right = ctx.bodyElem descendant * filter (_.id == "dynamic-drodpown-control") head
          )
        }
      }
    }

    describe("Submissions") {
      withActionAndFBDoc(FormulasDoc) { implicit ctx =>

        val RenamedSubmission: NodeInfo =
            <fb:submission
              xmlns:xf={Namespaces.XF}
              xmlns:fb={FB}
              id="echo-submission"
              class="fr-service"
              resource="/fr/service/custom/orbeon/echo?ping={$read-write-control}"
              method="get"
              serialization="none"
              mediatype=""/>

        FormBuilder.renameControlReferences("readonly-control", "read-write-control", None)

        it("must rename references from `resource` attribute") {
          assertXMLElementsIgnoreNamespacesInScope(
            left = RenamedSubmission,
            right = ctx.modelElem descendant * filter (_.id == "echo-submission") head
          )
        }
      }
    }
  }

  describe("Cursor up/down for cell selection") {

    def assertOne(
      direction: Direction,
      valueOpt : Option[String]
    )(implicit
      ctx      : FormBuilderDocContext
    ): Unit = {
      FormBuilder.findCellsByDirection(
        currentCellElem  = FormBuilder.findSelectedCell.get,
        startCellElemOpt = FormBuilder.findStartingCell,
        direction        = direction,
        allowEmptyCell   = true
      ).headOption.foreach { td =>
        selectCell(td)
      }
      assert(FormBuilder.findSelectedCell.flatMap(_.firstChildOpt(*)).map(_.id).map(FormRunner.controlNameFromId) == valueOpt)
    }

    def testSeries(
      expected: List[(String, Direction, List[Option[String]])]
    )(implicit
      ctx     : FormBuilderDocContext
    ): Unit =
      for ((startControlName, direction, values) <- expected) {
        it(s"must pass starting with `$startControlName` going $direction") {

          FormBuilder.selectCellForControlName(startControlName)
          FormBuilder.selectStartCellForControlName(startControlName)

          for (value <- values)
            assertOne(direction, value)
        }
        it(s"must pass starting with `$startControlName` going $direction in reverse") {
          for (value <- values.reverse.tail ::: Some(startControlName) :: Nil)
            assertOne(Direction.mirror(direction), value)
        }
      }

    describe("using 12-column grids only") {
      withActionAndFBDoc("oxf:/forms/issue/6852-1/form/form.xhtml") { implicit ctx =>

        // Using the "Orbeon Demo: Building Permit Application" form
        testSeries(
          List(
            // Go down the leftmost column
            (
              "project-type",
              Direction.Down,
              List(
                Some("project-scope"),
                Some("property-owner-name"),
                Some("property-management-tenant-name"),
                Some("type-of-contractor"),
                Some("contractor-state-license-number"),
                Some("company-owner-builders-name"),
                Some("email-address"),
                Some("applicant-authorized-agents-name"),
                Some("type-1"),
                Some("name-1"),
                Some("license-number-1"),
                Some("certification-explanation"),
                Some("licensed-contractor-authorized-agent-name"),
                None,
                Some("agreement-explanation"),
                Some("licensed-contractor-authorized-agent-name-2"),
                Some("declarations-explanation"),
                Some("licensed-contractor-authorized-agent-name-3"),
                Some("wc-explanation"),
                Some("wc-radio-1"),
                Some("wc-radio-2"),
                Some("wc-radio-3"),
                Some("licensed-contractor-authorized-agent-name-4"),
                Some("warning-explanation"),
                Some("notes"),
              )
            ),
            // Go up from a rightmost cell of size 3 (no narrower cells)
            (
              "property-owner-phone",
              Direction.Up,
              List(
                Some("project-scope"),
                Some("project-job-valuation"),
              )
            ),
            // Go down from a rightmost cell of size 3 (only one narrower cells, "expires-2")
            (
              "property-owner-phone",
              Direction.Down,
              List(
                None,
                None,
                Some("expires"),
                None,
                None,
                Some("phone-1"),
                None,
                Some("company-name"),
                Some("phone-2"),
                Some("certification-explanation"),
                Some("signature-1"),
                Some("agreement-explanation"),
                Some("signature-2"),
                Some("declarations-explanation"),
                Some("signature-3"),
                Some("wc-explanation"),
                Some("wc-explanation-1"),
                None,
                Some("wc-explanation-2"),
                Some("policy-number-2"),
                Some("wc-explanation-3"),
                Some("signature-4"),
                Some("warning-explanation"),
                Some("notes"),
              )
            ),
          )
        )
      }
    }

    describe("using a mix of 12-column and 24-column grids") {
      withActionAndFBDoc("oxf:/forms/issue/6852-2/form/form.xhtml") { implicit ctx =>
        testSeries(
          List(
            (
              "row24-12",
              Direction.Down,
              List(
                Some("row12-6"),
              )
            ),
            (
              "row24-6",
              Direction.Down,
              List(
                Some("row12-3"),
              )
            ),
            (
              "row24-3",
              Direction.Down,
              List(
                Some("row12-2"),
              )
            ),
            (
              "row24-2",
              Direction.Down,
              List(
                Some("row12-2"),
              )
            ),
            (
              "row24-1",
              Direction.Down,
              List(
                Some("row12-1"),
              )
            ),
          )
        )
      }
    }
  }

  describe("#6880: rename legacy HTTP service references") {
    withActionAndFBDoc("oxf:/forms/issue/6880/form/form.xhtml") { implicit ctx =>

      def findValuesContaining(s: String): LazyList[String] =
        findLegacyActions.descendant(*).att(@*).filter(_.stringValue.contains(s)).map(_.stringValue)

      it("must contain non-renamed values") {
        assert(
          findValuesContaining("echo") ==
            List("echo-submission", "echo-submission", "'echo-instance'", "echo-submission")
        )
      }

      it("must contain renamed attributes") {
        FormBuilder.renameServiceReferences("echo", "gaga")
         assert(
          findValuesContaining("gaga") ==
            List("gaga-submission", "gaga-submission", "'gaga-instance'", "gaga-submission")
        )
      }
    }
  }
}