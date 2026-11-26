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
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.mutable.ListBuffer


class StyleVariablesTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  private val LayerOrder = "@layer orbeon-theme, orbeon-properties, orbeon-theme-variants, orbeon-properties-variants;"

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

  describe("Style variables from properties") {

    for (theme <- List("2025", "2026"))
      it(s"must put the light values of the $theme theme in a layer, and its dark and print blocks in a later layer") {

        val css    = themeCss(theme)
        val blocks = topLevelBlocks(css)

        assert(css.trim.startsWith(LayerOrder))
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
  }
}
