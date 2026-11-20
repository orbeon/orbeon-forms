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
package org.orbeon.xbl

import org.scalatest.funspec.AnyFunSpec


class MarkdownSupportTest extends AnyFunSpec {

  describe("MarkdownSupportJava.markdownToSanitizedHtml") {

    it("returns empty string for null or blank input") {
      assert(MarkdownSupportJava.markdownToSanitizedHtml(null) == "")
      assert(MarkdownSupportJava.markdownToSanitizedHtml("") == "")
      assert(MarkdownSupportJava.markdownToSanitizedHtml("   \n\t  ") == "")
    }

    it("renders standard CommonMark elements") {
      val md =
        """## Restoration Notes
          |
          |This **Hermes Baby** has a *Swiss-French* keyboard.
          |
          |- 2019-06: Initial inspection
          |- 2021-08: Cleaned and adjusted
          |
          |> Types cleanly now.
          |
          |Use `QWERTZ` layout.
          |
          |---
          |""".stripMargin

      val html = MarkdownSupportJava.markdownToSanitizedHtml(md)
      assert(html.contains("<h2>Restoration Notes</h2>"))
      assert(html.contains("<strong>Hermes Baby</strong>"))
      assert(html.contains("<em>Swiss-French</em>"))
      assert(html.contains("<ul>"))
      assert(html.contains("<li>2019-06: Initial inspection</li>"))
      assert(html.contains("<blockquote>"))
      assert(html.contains("<code>QWERTZ</code>"))
      assert(html.contains("<hr>"))
    }

    it("renders GFM tables, strikethrough, autolinks, and task lists") {
      val md =
        """| Year | Model |
          || --- | --- |
          || 1961 | ~~Baby 2~~ Baby |
          |
          |See https://typewriterdatabase.com/1961-hermes-baby.12502.typewriter
          |
          |- [x] Replace feet
          |- [ ] Oil escapement
          |""".stripMargin

      val html = MarkdownSupportJava.markdownToSanitizedHtml(md)
      assert(html.contains("<table>"))
      assert(html.contains("<th>Year</th>"))
      assert(html.contains("<del>Baby 2</del>"))
      assert(html.contains("""href="https://typewriterdatabase.com/1961-hermes-baby.12502.typewriter""""))
      assert(html.contains("""target="_blank""""))
      assert(html.contains("""rel="noopener noreferrer""""))
      assert(html.contains("fr-markdown-task-checked"))
      assert(html.contains("\u2611"))
      assert(html.contains("\u2610"))
    }

    it("escapes raw HTML and neutralizes javascript: links") {
      val md =
        """<script>alert('xss')</script>
          |
          |<img src="x" onerror="alert(1)">
          |
          |[Click me](javascript:alert(1))
          |
          |[Relative](/posts/hermes-baby-1961/)
          |""".stripMargin

      val html = MarkdownSupportJava.markdownToSanitizedHtml(md)
      assert(! html.contains("<script"))
      assert(! html.contains("<img"))
      assert(! html.contains("javascript:"))
      assert(html.contains("&lt;script&gt;"))
      assert(html.contains("&lt;img "))
      assert(html.contains("""href="/posts/hermes-baby-1961/""""))
      assert(! html.contains("""href="/posts/hermes-baby-1961/" target="_blank""""))
    }
  }
}
