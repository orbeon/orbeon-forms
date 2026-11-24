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
package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.orbeon.io.CharsetNames
import org.orbeon.io.IOUtils
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.xforms.Constants
import org.orbeon.xforms.route.XFormsAssetServerRoute
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.mutable.ListBuffer


class StyleVariablesTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  private val DocumentId = "7641a1b2c3d4e5f60718293a4b5c6d7e"

  private def layeredCss(lightDeclarations: String): String =
    s"${StyleVariables.LayerOrder} @layer orbeon-properties { .orbeon { $lightDeclarations } }"

  private def topLevelBlocks(css: String): List[(String, String)] = {
    // Header and content of the top-level rules and at-rules of a stylesheet without comments
    val blocks = ListBuffer[(String, String)]()
    var depth  = 0
    var start  = 0
    var open   = 0
    for ((c, i) <- css.zipWithIndex)
      c match {
        case '{' =>
          if (depth == 0) open = i
          depth += 1
        case '}' =>
          depth -= 1
          if (depth == 0) {
            blocks += css.substring(start, open).trim -> css.substring(open + 1, i)
            start = i + 1
          }
        case ';' if depth == 0 =>
          start = i + 1
        case _ =>
      }
    blocks.toList
  }

  private def themeCss(theme: String): String =
    IOUtils.readStreamAsStringAndClose(ResourceManagerWrapper.instance.getContentAsStream(s"/apps/fr/themes/$theme/theme.css"), CharsetNames.Utf8.some)
      .replaceAll("""(?s)/\*.*?\*/""", "")

  private def variableProperty(name: String, appForm: String, value: String): PropertyParams =
    stringProperty(s"${StyleVariables.propertyName(name)}.$appForm", value)

  private def stringProperty(name: String, value: String): PropertyParams =
    PropertyParams(Map.empty, name, XMLConstants.XS_STRING_QNAME, value)

  private def fontSizes(pdf: Array[Byte], text: String): List[Float] = {
    // Font size, in points, of the glyphs of the first occurrence of the text
    val all   = pdfTextPositions(pdf).filter(_.getUnicode.length == 1)
    val index = all.map(_.getUnicode).mkString.indexOf(text)
    if (index == -1) Nil else all.slice(index, index + text.length).map(_.getFontSizeInPt)
  }

  private def styleVariablesStylesheets(form: String, mode: String, properties: PropertyParams*): List[String] = {

    val (_, content, _) =
      runFormRunnerReturnContent(
        app        = "issue",
        form       = form,
        mode       = mode,
        documentId = if (mode == "new") None else DocumentId.some,
        attributes = propertiesOverride(properties*)
      )
    val html = new String(content.body, CharsetNames.Utf8)
    Constants.FormStyleResourcesRegex.findAllMatchIn(html)
      .map(m => XFormsAssetServerRoute.findInlineStylesheet(m.group(1)).getOrElse(fail(s"no stylesheet for `${m.group(1)}`")))
      .filter(css => "--orbeon-[a-z0-9-]+:".r.findFirstIn(css).isDefined)
      .toList
  }

  describe("Names of style variables") {

    val Valid   = List("accent-color", "base-font-size", "h1-font-size", "stripe-color-2")
    val Invalid = List("", "*", "-accent-color", "accent-color-", "accent--color", "Accent-Color", "accent_color", "accent color", "--orbeon-accent-color")

    for (name <- Valid)
      it(s"must accept `$name`") {
        assert(StyleVariables.isValidName(name))
      }

    for (name <- Invalid)
      it(s"must reject `$name`") {
        assert(! StyleVariables.isValidName(name))
      }
  }

  describe("Values of style variables") {

    val Valid = List(
      "#b3003b",
      "white",
      "15px",
      "1.14em",
      "6px 10px",
      "-1px",
      "1px solid #ccc",
      "'Source Sans 3', Arial, sans-serif",
      "\"Helvetica Neue\", sans-serif",
      "var(--orbeon-accent-color)",
      "calc(max(20px, 1em) + 14px)",
      "calc((1em + 2px) * 2)",
      "(1px)",
      "rgb(16 67 168 / 50%)",
      "RGB(16, 67, 168)",
      "oklch(42% 0.17 262)",
      "color-mix(in oklab, var(--orbeon-accent-color) 5%, #f3f5f8)",
      "light-dark(#1043a8, #3368d0)",
      "linear-gradient(to right, #1043a8, #3368d0)",
      "invert(1) grayscale(100%) brightness(200%)",
      "'Café Sans', sans-serif",
      "'a; b } (c'",
      "url(image.png)",
      "red !important"
    )

    val Invalid = List(
      "",
      "red; color: blue",
      "red } .a { color: blue",
      "calc(1px + 2px",
      "calc(1px + 2px))",
      "[a",
      "'Source Sans 3",
      "\"Source Sans 3'",
      "red /* comment",
      "red \\3b",
      "'</style>'",
      "red\nblue"
    )

    for (value <- Valid)
      it(s"must accept `$value`") {
        assert(StyleVariables.isValidValue(value))
      }

    for (value <- Invalid)
      it(s"must reject `$value`") {
        assert(! StyleVariables.isValidValue(value))
      }
  }

  describe("Style variables from properties") {

    implicit val propertySet: PropertySet =
      PropertySet.forTests(
        List(
          variableProperty("accent-color",     "*.*",        "#111111"),
          variableProperty("accent-color",     "issue.*",    "#222222"),
          variableProperty("accent-color",     "issue.7641", "#333333"),
          variableProperty("base-font-size",   "*.*",        "15px"),
          variableProperty("base-font-size",   "issue.7641", ""),
          variableProperty("base-font-family", "*.*",        "  'Source Sans 3',\n  sans-serif  "),
          variableProperty("link-color",       "issue.7640", "#444444"),
          variableProperty("Bad_Name",         "*.*",        "#555555")
        )
      )

    it("must use the most specific property, and ignore a blank value") {
      assert(
        StyleVariables.fromProperties("issue", "7641", None) ==
          List("accent-color" -> "#333333", "base-font-family" -> "'Source Sans 3', sans-serif")
      )
      assert(
        StyleVariables.fromProperties("issue", "7640", None) ==
          List("accent-color" -> "#222222", "base-font-family" -> "'Source Sans 3', sans-serif", "base-font-size" -> "15px", "link-color" -> "#444444")
      )
    }

    it("must use the wildcard properties for the pages without an app and form") {
      assert(
        StyleVariables.fromProperties("*", "*", None) ==
          List("accent-color" -> "#111111", "base-font-family" -> "'Source Sans 3', sans-serif", "base-font-size" -> "15px")
      )
    }

    it("must give the name of the property used for a variable as written, with its wildcards") {
      assert(StyleVariables.declaredPropertyName("accent-color", "issue", "7641", None) == "oxf.fr.style.variable.accent-color.issue.7641")
      assert(StyleVariables.declaredPropertyName("accent-color", "issue", "7640", None) == "oxf.fr.style.variable.accent-color.issue.*")
      assert(StyleVariables.declaredPropertyName("accent-color", "other", "form", None) == "oxf.fr.style.variable.accent-color.*.*")
    }

    for (theme <- List("2025", "2026"))
      it(s"must put the light values of the $theme theme in a layer, and its dark and print blocks in a later layer") {

        val css    = themeCss(theme)
        val blocks = topLevelBlocks(css)

        assert(css.trim.startsWith(StyleVariables.LayerOrder))
        assert(blocks.map(_._1) == List("@layer orbeon-theme", "@layer orbeon-theme-variants"))

        val lightContent  = blocks.head._2
        val variantBlocks = topLevelBlocks(blocks(1)._2)

        assert("""(?s)^\s*\.orbeon \{[^}]*--orbeon-accent-color:""".r.findFirstIn(lightContent).isDefined)
        assert(variantBlocks.forall { case (header, _) => header.startsWith("@media ") }, variantBlocks.map(_._1))
        assert(variantBlocks.exists { case (header, content) => header == "@media screen" && content.trim.startsWith(".orbeon.fr-color-scheme-dark,") })

        val printBlocks = variantBlocks.collect { case (header, content) if header == "@media print" => content }
        assert(printBlocks.exists(content => """(?s)^\s*\.orbeon \{[^}]*--orbeon-base-font-size:""".r.findFirstIn(content).isDefined))
        assert(printBlocks.forall(content => ! """\.fr-pdf-color-mode-[a-z-]+\s*[,{]""".r.findFirstIn(content).isDefined))
      }

    it("must produce CSS without a media query for the light values, and nothing without variables") {
      assert(
        StyleVariables.toCss(List("accent-color" -> "#333333", "base-font-size" -> "15px")) ==
          layeredCss("--orbeon-accent-color: #333333; --orbeon-base-font-size: 15px;").some
      )
      assert(StyleVariables.toCss(Nil).isEmpty)
    }
  }

  // The head of the page is kept with the compiled form, so each test uses its own form or mode
  describe("Style variables in Form Runner pages") {

    it("must not produce a stylesheet without properties") {
      assert(styleVariablesStylesheets("8055", "new").isEmpty)
    }

    it("must produce a stylesheet with the variables of the form, without the invalid values") {
      assert(
        styleVariablesStylesheets(
          "7641",
          "new",
          variableProperty("accent-color",   "*.*",        "#111111"),
          variableProperty("accent-color",   "issue.7641", "#333333"),
          variableProperty("base-font-size", "issue.*",    "15px"),
          variableProperty("link-color",     "issue.7640", "#444444"),
          variableProperty("base-color",     "*.*",        "black; } .orbeon { display: none")
        ) ==
          List(layeredCss("--orbeon-accent-color: #333333; --orbeon-base-font-size: 15px;"))
      )
    }
  }

  describe("Style variables and PDF") {

    // The theme isn't available to the PDF renderer here, so the test form sets the font sizes in its own style element
    it("must apply the light value to the PDF") {
      val (_, content, _) =
        runFormRunnerReturnContent(
          app        = "issue",
          form       = "7641",
          mode       = "pdf",
          documentId = DocumentId.some,
          attributes = propertiesOverride(variableProperty("label-font-size", "*.*", "30px"))
        )

      val labelSizes = fontSizes(content.body, "Label7641")
      val valueSizes = fontSizes(content.body, "Value7641")
      val sizes      = s"label: $labelSizes, value: $valueSizes"

      // 30px for the label, compared to 10px for the value
      assert(labelSizes.size == 9 && labelSizes.distinct.size == 1, sizes)
      assert(valueSizes.size == 9 && valueSizes.distinct.size == 1, sizes)
      assert(math.abs(labelSizes.head / valueSizes.head - 3f) < 0.05f, sizes)
    }
  }
}
