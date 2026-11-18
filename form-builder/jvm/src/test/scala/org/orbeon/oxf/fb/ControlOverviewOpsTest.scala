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

import cats.syntax.option.*
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fb.XMLNames.FB
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.XMLNames.{FR, XF}
import org.orbeon.oxf.fr.{AppForm, FormRunner, FormRunnerParams}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.analysis.model.MipName
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.charset.StandardCharsets
import java.util.Base64


class ControlOverviewOpsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  private val SectionsGridsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids-repeats.xhtml"
  private val SectionTemplatesDoc     = "oxf:/org/orbeon/oxf/fb/template-with-section-templates.xhtml"
  private val FormulasDoc             = "oxf:/org/orbeon/oxf/fb/template-with-controls-to-rename.xhtml"

  private def mutableOverview(overviewRo: NodeInfo): NodeInfo =
    TransformerUtils.extractAsMutableDocument(overviewRo).rootElement

  describe("Hierarchy extraction, PDF/Excel field names, and Section Templates") {

    it("must extract sections, grids, repeats, nesting levels, PDF names, and Excel range names in document order") {
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        val overview = FormBuilder.buildControlOverviewXml("en")
        val items    = (overview / "item").toList

        val summary = items.map(i => (i.attValue("kind"), (i / "name").stringValue, i.attValue("level").toInt, i.attValue("is-repeat") == "true"))
        assert(
          summary === List(
            ("section", "section-1",    1, false),
            ("grid",    "grid-11",      2, false),
            ("control", "control-111",  3, false),
            ("grid",    "grid-12",      2, true),
            ("control", "control-121",  3, false),
            ("section", "section-2",    1, false),
            ("grid",    "grid-21",      2, false),
            ("control", "control-211",  3, false),
            ("grid",    "grid-22",      2, true),
            ("control", "control-212",  3, false),
            ("section", "section-3",    1, true),
            ("grid",    "grid-31",      2, false),
            ("control", "control-311",  3, false),
            ("section", "section-33",   2, false),
            ("grid",    "grid-331",     3, false),
            ("control", "control-3311", 4, false),
            ("grid",    "grid-332",     3, true),
            ("control", "control-3312", 4, false)
          )
        )

        def itemByName(n: String): NodeInfo = items.find(i => (i / "name").stringValue == n).get

        // Non-repeated grids do not contribute to PDF field names, whereas sections and repeated grids do
        assert(itemByName("control-111").attValue("effective-pdf-name").startsWith("section-1$control-111"))
        assert(itemByName("control-121").attValue("effective-pdf-name").startsWith("section-1$grid-12$control-121"))
        assert(itemByName("control-3312").attValue("effective-pdf-name").startsWith("section-3$section-33$grid-332$control-3312"))

        // Excel range names replace hyphens with underscores
        assert(itemByName("control-111").attValue("effective-excel-name") === "control_111")
        assert(itemByName("section-1").attValue("effective-excel-name")   === "")

        // Custom iteration names must be reserved in `external-names`
        FormBuilder.renameControlIterationIfNeeded("grid-12", "grid-12", None, Some("custom-line-item"))
        val overviewWithCustomIter = FormBuilder.buildControlOverviewXml("en")
        val externalNames          = overviewWithCustomIter.attValue("external-names").split(" ").toSet
        assert(externalNames.contains("custom-line-item"))
      }
    }

    it("must include Section Template inner items as read-only while keeping the host section editable") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>
        val overview = FormBuilder.buildControlOverviewXml("en")
        val items    = (overview / "item").toList

        // A section template's own `-content` holder belongs to that editable section row and must not self-collide in `external-names`
        val externalNames = overview.attValue("external-names").split(" ").filter(_.nonEmpty).toSet
        assert(! externalNames.contains("section-5-content"))

        val hostSec1 = items.find(i => i.attValue("original-name") == "section-1" && i.attValue("in-section-template") == "false").get
        assert(hostSec1.attValue("is-section-template") === "true")
        assert(hostSec1.attValue("readonly")            === "false")

        val templateInnerItems = items.filter(_.attValue("in-section-template") == "true")
        assert(templateInnerItems.nonEmpty)
        assert(templateInnerItems.forall(_.attValue("readonly") == "true"))

        // Inner controls from section-template-1 / section-template-2 must appear with their section path
        val innerNames = templateInnerItems.map(i => (i / "name").stringValue).toSet
        assert(innerNames.contains("control-11"))
        assert(innerNames.contains("nested-section"))
        assert(innerNames.contains("control-2221"))

        val inner2221 = templateInnerItems.find(i => (i / "name").stringValue == "control-2221").head
        assert(inner2221.attValue("section-path").startsWith("Section 2"))
      }
    }

    it("must count formulas and validations accurately on formula-heavy forms") {
      withActionAndFBDoc(FormulasDoc) { implicit ctx =>
        val overview = FormBuilder.buildControlOverviewXml("en")
        val items    = (overview / "item").toList
        def byName(n: String): NodeInfo = items.find(_.attValue("original-name") == n).get

        val multiple = byName("multiple")
        assert(multiple.attValue("required-mode")    === "formula")
        assert(multiple.attValue("formula-count")    === "5")
        assert(multiple.attValue("validation-count") === "2")
        assert(multiple.attValue("formulas-tooltip").contains("Calculated: $foo"))
        assert(multiple.attValue("validations-tooltip").contains("$toto != $gaga"))
      }
    }
  }

  describe("Name suggestions (single row and bulk)") {

    it("must generate valid, collision-free NCNames from labels") {
      assert(FormBuilder.slugSuggestionFromLabel("First Name!", Set.empty)           === "first-name_")
      assert(FormBuilder.slugSuggestionFromLabel("1st Address", Set.empty)           === "_1st-address")
      assert(FormBuilder.slugSuggestionFromLabel("my iteration", Set.empty)          === "my-iteration_")
      assert(FormBuilder.slugSuggestionFromLabel("Email", Set("email", "email-1"))   === "email-2")
      assert(
        FormBuilder.slugSuggestionFromLabel(
          label             = " billing ",
          usedNames         = Set("billing-content"),
          isSectionTemplate = true
        ) === "billing-1"
      )
    }

    it("must suggest names only for default `control-N` / `section-N` rows and deduplicate bulk suggestions") {
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        // Set identical labels on two controls and a custom name on another
        FormBuilder.setControlResourcesWithLang("control-111", "label", List("en" -> Seq("Email Address")))
        FormBuilder.setControlResourcesWithLang("control-121", "label", List("en" -> Seq("Email Address")))
        FormBuilder.renameControlIfNeeded("control-211", "existing-custom-name")

        val ov = mutableOverview(FormBuilder.buildControlOverviewXml("en"))

        val row111Id = (ov / "item").find(_.attValue("original-name") == "control-111").get.attValue("row-id")
        val row211Id = (ov / "item").find(_.attValue("original-name") == "existing-custom-name").get.attValue("row-id")

        assert(FormBuilder.suggestControlNameForOverviewRow(ov, row111Id, "en").contains("email-address"))
        assert(FormBuilder.suggestControlNameForOverviewRow(ov, row211Id, "en").isEmpty)

        val updatedCount = FormBuilder.suggestAllDefaultControlNames(ov, "en")
        assert(updatedCount > 0)

        val nameAfter111 = ((ov / "item").find(_.attValue("original-name") == "control-111").get / "name").stringValue
        val nameAfter121 = ((ov / "item").find(_.attValue("original-name") == "control-121").get / "name").stringValue
        val nameAfter211 = ((ov / "item").find(_.attValue("original-name") == "existing-custom-name").get / "name").stringValue

        assert(nameAfter111 === "email-address")
        assert(nameAfter121 === "email-address-1")
        assert(nameAfter211 === "existing-custom-name")
      }
    }
  }

  describe("Applying staged changes, batch renames, HTML/Encrypt guards, and single-step Undo/Redo") {

    it("must update multi-language labels/hints, Required, Summary, preserve HTML & encrypted fields, and undo/redo in one step") {
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")

        // Add French resources so the form has two languages (`en` and `fr`)
        val enRes = (ctx.resourcesRootElem / "resource").head
        val frRes = TransformerUtils.extractAsMutableDocument(enRes).rootElement
        setvalue(frRes /@ "*:lang", "fr")
        insert(into = ctx.resourcesRootElem, after = Seq(enRes), origin = frRes)

        // Make `control-211` have an HTML label (`@mediatype="text/html"`) and `fr:encrypt`
        val ctrl211 = FormRunner.findControlByName("control-211").get
        FormBuilder.setControlLhhatMediatype("control-211", "label", isHTML = true)
        FormBuilder.setControlResourcesWithLang("control-211", "label", List("en" -> Seq("<b>Bold Label</b>"), "fr" -> Seq("<b>Gras</b>")))
        insert(into = ctrl211, origin = <fr:encrypt xmlns:fr="http://orbeon.org/oxf/xml/form-runner"/>: NodeInfo)

        // Add a formula referencing `control-111` and `control-121` on `control-311` so we can verify swap rename rewriting
        FormBuilderXPathApi.writeAndNormalizeCalculatedMip("control-311", MipName.Calculate.name, "$control-111 + $control-121")

        val ov = mutableOverview(FormBuilder.buildControlOverviewXml("en"))
        def ovItem(origName: String): NodeInfo = (ov / "item").find(_.attValue("original-name") == origName).get

        // Verify HTML and encrypted guards in built overview XML
        val item211 = ovItem("control-211")
        assert((item211 / "label").head.attValue("isHTML")   === "true")
        assert((item211 / "label").head.attValue("preview")  === "Bold Label")
        assert(item211.attValue("summary-readonly")          === "true")
        assert(item211.attValue("summary-readonly-reason")   === "encrypted")

        // Stage edits:
        // 1. Swap names `control-111` <-> `control-121`!
        setvalue(ovItem("control-111") / "name", "control-121")
        setvalue(ovItem("control-121") / "name", "control-111")

        // 2. Rename repeated grid `grid-12` -> `line-items` (must also rename `grid-12-iteration` -> `line-items-iteration`)
        setvalue(ovItem("grid-12") / "name", "line-items")

        // 3. Edit `en` and `fr` labels & hints, Required=true, Summary=true on original `control-111` (which becomes `control-121`)
        setvalue((ovItem("control-111") / "label" / "value").find(_.attValue("lang") == "en").get, "First Amount")
        setvalue((ovItem("control-111") / "label" / "value").find(_.attValue("lang") == "fr").get, "Premier montant")
        setvalue((ovItem("control-111") / "hint"  / "value").find(_.attValue("lang") == "en").get, "Enter USD")
        setvalue((ovItem("control-111") / "hint"  / "value").find(_.attValue("lang") == "fr").get, "Saisir USD")
        setvalue(ovItem("control-111") / "required", "true")
        setvalue(ovItem("control-111") / "summary",  "true")

        // 4. Update section label in both languages on `section-1`
        setvalue((ovItem("section-1") / "label" / "value").find(_.attValue("lang") == "en").get, "General Info")
        setvalue((ovItem("section-1") / "label" / "value").find(_.attValue("lang") == "fr").get, "Informations générales")

        // 5. Attempt to mutate HTML label and encrypted summary in XML anyway — backend guard must ignore both
        setvalue((item211 / "label" / "value").find(_.attValue("lang") == "en").get, "Hacked Plain")
        setvalue(item211 / "summary", "true")

        val changed = FormBuilder.applyControlOverviewChanges(ov)
        assert(changed >= 4)

        // Verify swapped controls & updated properties
        // Original `control-111` was in `grid-11` (first grid of `section-1`) and is now named `control-121`
        val grid11Ctrl = (FormBuilder.containerById("grid-11-grid") descendant (XF -> "input")).head
        assert(getControlNameOpt(grid11Ctrl).contains("control-121"))

        // Repeated grid `grid-12` is now `line-items` with `line-items-iteration`, containing swapped `control-111`
        assert(FormRunner.findControlByName("line-items").isDefined)
        assert(FormRunner.findBindByName("line-items-iteration").isDefined)
        val lineItemsCtrl = (FormRunner.findControlByName("line-items").get descendant (XF -> "input")).head
        assert(getControlNameOpt(lineItemsCtrl).contains("control-111"))

        // Formula `$control-111 + $control-121` swapped cleanly to `$control-121 + $control-111` without colliding!
        val calcAfterSwap = FormRunner.readDenormalizedCalculatedMip(
          FormRunner.findBindByName("control-311").get,
          MipName.Calculate,
          mipToFBMIPQNames(MipName.Calculate)._1
        )
        assert(calcAfterSwap === "$control-121 + $control-111")

        // Verify labels/hints in both languages, required, and summary on the renamed control (`control-121`)
        val resByLangAfter = FormBuilder.getControlResourcesWithLang("control-121", LHHA.Label.entryName, Seq("en", "fr")).toMap
        assert(resByLangAfter("en").head.getStringValue === "First Amount")
        assert(resByLangAfter("fr").head.getStringValue === "Premier montant")
        val hintByLangAfter = FormBuilder.getControlResourcesWithLang("control-121", LHHA.Hint.entryName, Seq("en", "fr")).toMap
        assert(hintByLangAfter("en").head.getStringValue === "Enter USD")
        assert(hintByLangAfter("fr").head.getStringValue === "Saisir USD")
        assert(FormBuilder.RequiredValidation.fromForm("control-121").required === Left(true))
        assert(FormBuilderXPathApi.isControlSummaryShow("control-121"))

        // Verify section label updated
        val secLabelAfter = FormBuilder.getControlResourcesWithLang("section-1", LHHA.Label.entryName, Seq("en", "fr")).toMap
        assert(secLabelAfter("en").head.getStringValue === "General Info")
        assert(secLabelAfter("fr").head.getStringValue === "Informations générales")

        // Verify HTML label & encrypted summary on `control-211` were preserved untouched
        val htmlLabelAfter = FormBuilder.getControlResourcesWithLang("control-211", LHHA.Label.entryName, Seq("en")).toMap
        assert(htmlLabelAfter("en").head.getStringValue === "<b>Bold Label</b>")
        assert(! FormBuilderXPathApi.isControlSummaryShow("control-211"))

        // Single-step UNDO must revert ALL renames, formula rewrites, labels, hints, required, and summary!
        FormBuilderXPathApi.undoActionImpl()

        assert(FormRunner.findControlByName("grid-12").isDefined)
        assert(FormRunner.findBindByName("grid-12-iteration").isDefined)
        assert(FormRunner.findControlByName("line-items").isEmpty)

        val grid11CtrlUndo = (FormBuilder.containerById("grid-11-grid") descendant (XF -> "input")).head
        assert(getControlNameOpt(grid11CtrlUndo).contains("control-111"))

        val calcAfterUndo = FormRunner.readDenormalizedCalculatedMip(
          FormRunner.findBindByName("control-311").get,
          MipName.Calculate,
          mipToFBMIPQNames(MipName.Calculate)._1
        )
        assert(calcAfterUndo === "$control-111 + $control-121")
        assert(FormBuilder.RequiredValidation.fromForm("control-111").required === Left(false))
        assert(! FormBuilderXPathApi.isControlSummaryShow("control-111"))

        val secLabelUndo = FormBuilder.getControlResourcesWithLang("section-1", LHHA.Label.entryName, Seq("en")).toMap
        assert(secLabelUndo("en").head.getStringValue === "Section 1")

        // Single-step REDO must replay the entire batch!
        FormBuilderXPathApi.redoActionImpl()

        assert(FormRunner.findControlByName("line-items").isDefined)
        assert(FormRunner.findBindByName("line-items-iteration").isDefined)
        val calcAfterRedo = FormRunner.readDenormalizedCalculatedMip(
          FormRunner.findBindByName("control-311").get,
          MipName.Calculate,
          mipToFBMIPQNames(MipName.Calculate)._1
        )
        assert(calcAfterRedo === "$control-121 + $control-111")
        assert(FormBuilder.RequiredValidation.fromForm("control-121").required === Left(true))
        assert(FormBuilderXPathApi.isControlSummaryShow("control-121"))
      }
    }
  }

  describe("CSV export") {

    it("must produce UTF-8 BOM data URI with RFC 4180 escaping, multi-language columns, and live staged PDF/Excel names") {
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        // Add French resources
        val enRes = (ctx.resourcesRootElem / "resource").head
        val frRes = TransformerUtils.extractAsMutableDocument(enRes).rootElement
        setvalue(frRes /@ "*:lang", "fr")
        insert(into = ctx.resourcesRootElem, after = Seq(enRes), origin = frRes)

        val ov = mutableOverview(FormBuilder.buildControlOverviewXml("en"))
        def ovItem(origName: String): NodeInfo = (ov / "item").find(_.attValue("original-name") == origName).get

        // Stage renames on both an ancestor section (`section-1` -> `customer`) and its child control (`control-111` -> `full-name`)
        // plus a label with commas and double quotes
        setvalue(ovItem("section-1")   / "name", "customer")
        setvalue(ovItem("control-111") / "name", "full-name")
        setvalue((ovItem("control-111") / "label" / "value").find(_.attValue("lang") == "en").get, """Name, "Legal"""")
        setvalue((ovItem("control-111") / "label" / "value").find(_.attValue("lang") == "fr").get, "Nom complet")

        val dataUri = FormBuilder.controlOverviewToCsvDownloadUri(ov, "en")
        assert(dataUri.startsWith("data:text/csv;charset=utf-8;base64,"))

        val decoded = new String(
          Base64.getDecoder.decode(dataUri.stripPrefix("data:text/csv;charset=utf-8;base64,")),
          StandardCharsets.UTF_8
        )
        assert(decoded.startsWith("\uFEFF"))

        val lines = decoded.stripPrefix("\uFEFF").split("\r\n").toList
        assert(lines.head.contains("Label (en),Label (fr),Hint (en),Hint (fr)"))
        assert(lines.head.endsWith("PDF Field Name,Excel Range Name,Section Template"))

        val fullNameLine = lines.find(_.contains(",full-name,")).get
        // Must escape quotes & commas per RFC 4180 and compute staged PDF & Excel names (`customer$full-name` and `full_name`)
        assert(fullNameLine.contains(""""Name, ""Legal"""""))
        assert(fullNameLine.contains(",Nom complet,"))
        assert(fullNameLine.contains("customer$full-name"))
        assert(fullNameLine.contains(",full_name,"))
      }
    }
  }
}
