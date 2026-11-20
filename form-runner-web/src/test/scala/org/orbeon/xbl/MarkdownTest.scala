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

import org.scalatest.funspec.AnyFunSpecLike


class MarkdownTest extends AnyFunSpecLike {

  private def applyEnter(value: String, selStart: Int, selEnd: Int): Option[(String, Int, Int)] =
    Markdown.computeEnterEdit(value, selStart, selEnd).map { edit =>
      (edit.applyTo(value), edit.selectionStart, edit.selectionEnd)
    }

  private def applyEnterAtEnd(value: String): Option[(String, Int)] =
    applyEnter(value, value.length, value.length).map { case (v, s, _) => (v, s) }

  private def applyPaste(value: String, selStart: Int, selEnd: Int, pasted: String): Option[(String, Int, Int)] =
    Markdown.computePasteEdit(value, selStart, selEnd, pasted).map { edit =>
      (edit.applyTo(value), edit.selectionStart, edit.selectionEnd)
    }

  describe("Markdown.computeEnterEdit") {

    describe("unordered lists") {

      it("continues '-', '*', and '+' list items preserving indentation") {
        assert(applyEnterAtEnd("- First")     == Some(("- First\n- ", 10)))
        assert(applyEnterAtEnd("* First")     == Some(("* First\n* ", 10)))
        assert(applyEnterAtEnd("+ First")     == Some(("+ First\n+ ", 10)))
        assert(applyEnterAtEnd("  - Nested")  == Some(("  - Nested\n  - ", 15)))
        assert(applyEnterAtEnd("\t- Tabbed")  == Some(("\t- Tabbed\n\t- ", 13)))
      }

      it("splits a list item when the cursor is in the middle of the line") {
        val input = "- Hello world"
        val pos   = "- Hello".length
        assert(applyEnter(input, pos, pos) == Some(("- Hello\n-  world", pos + 3, pos + 3)))
      }

      it("replaces a selection inside a list item instead of exiting the list") {
        val input = "- Hello"
        assert(applyEnter(input, 2, 7) == Some(("- \n- ", 5, 5)))
      }

      it("exits the list when pressing Enter on an empty list item") {
        assert(applyEnterAtEnd("- ")               == Some(("", 0)))
        assert(applyEnterAtEnd("  - ")             == Some(("", 0)))
        assert(applyEnterAtEnd("- First\n- ")      == Some(("- First\n", 8)))
        assert(applyEnterAtEnd("- First\n  *   ")  == Some(("- First\n", 8)))
      }

      it("does not intercept plain text, horizontal rules, or cursor inside the marker") {
        assert(applyEnterAtEnd("Plain text").isEmpty)
        assert(applyEnterAtEnd("---").isEmpty)
        assert(applyEnterAtEnd("***").isEmpty)
        assert(applyEnter("- First", 0, 0).isEmpty)
        assert(applyEnter("- First", 1, 1).isEmpty)
      }
    }

    describe("task lists") {

      it("continues checked and unchecked task items as unchecked") {
        assert(applyEnterAtEnd("- [ ] Todo")     == Some(("- [ ] Todo\n- [ ] ", 17)))
        assert(applyEnterAtEnd("- [x] Done")     == Some(("- [x] Done\n- [ ] ", 17)))
        assert(applyEnterAtEnd("* [X] Done")     == Some(("* [X] Done\n* [ ] ", 17)))
        assert(applyEnterAtEnd("  - [x] Nested") == Some(("  - [x] Nested\n  - [ ] ", 23)))
      }

      it("exits the task list when pressing Enter on an empty task item") {
        assert(applyEnterAtEnd("- [ ] ")           == Some(("", 0)))
        assert(applyEnterAtEnd("- [ ] One\n- [ ] ") == Some(("- [ ] One\n", 10)))
      }
    }

    describe("ordered lists") {

      it("continues and increments ordered list items with '.' and ')' delimiters") {
        assert(applyEnterAtEnd("1. First")    == Some(("1. First\n2. ", 12)))
        assert(applyEnterAtEnd("9. Ninth")    == Some(("9. Ninth\n10. ", 13)))
        assert(applyEnterAtEnd("2) Second")   == Some(("2) Second\n3) ", 13)))
        assert(applyEnterAtEnd("  1. Nested") == Some(("  1. Nested\n  2. ", 17)))
      }

      it("exits the ordered list when pressing Enter on an empty item") {
        assert(applyEnterAtEnd("1. ")          == Some(("", 0)))
        assert(applyEnterAtEnd("1. First\n2. ") == Some(("1. First\n", 9)))
      }

      it("renumbers subsequent contiguous sibling items") {
        val input = "1. First\n2. Second\n3. Third"
        val pos   = "1. First".length
        assert(
          applyEnter(input, pos, pos) ==
            Some(("1. First\n2. \n3. Second\n4. Third", pos + 4, pos + 4))
        )
      }

      it("renumbers subsequent sibling items across nested sub-items and stops at non-list lines") {
        val input =
          """1. First
            |   - sub A
            |2. Second
            |   - sub B
            |Paragraph
            |3. Unrelated""".stripMargin
        val pos = "1. First".length
        val expected =
          """1. First
            |2. 
            |   - sub A
            |3. Second
            |   - sub B
            |Paragraph
            |3. Unrelated""".stripMargin
        assert(applyEnter(input, pos, pos) == Some((expected, pos + 4, pos + 4)))
      }

      it("splits an ordered list item in the middle and renumbers subsequent items") {
        val input = "1. First item\n2. Second"
        val pos   = "1. First".length
        assert(
          applyEnter(input, pos, pos) ==
            Some(("1. First\n2.  item\n3. Second", pos + 4, pos + 4))
        )
      }

      it("handles overflow numbers without throwing") {
        assert(applyEnterAtEnd("99999999999999999999999. Huge").isEmpty)
      }
    }
  }

  describe("Markdown.computePasteEdit") {

    it("wraps selected text in a Markdown link when pasting an HTTP(S) URL") {
      val input = "Check out Orbeon Forms here"
      val start = input.indexOf("Orbeon Forms")
      val end   = start + "Orbeon Forms".length
      val url   = "https://www.orbeon.com/"
      val expected = s"Check out [Orbeon Forms]($url) here"
      val expectedCursor = start + s"[Orbeon Forms]($url)".length
      assert(applyPaste(input, start, end, s"  $url \n") == Some((expected, expectedCursor, expectedCursor)))
    }

    it("does not intercept when there is no selection, pasted text is not a URL, or selection is a URL or multiline") {
      assert(applyPaste("Hello", 5, 5, "https://www.orbeon.com/").isEmpty)
      assert(applyPaste("Hello", 0, 5, "not a url").isEmpty)
      assert(applyPaste("Hello", 0, 5, "https://a.com https://b.com").isEmpty)
      assert(applyPaste("https://old.com", 0, 15, "https://new.com").isEmpty)
      assert(applyPaste("Line 1\nLine 2", 0, 13, "https://www.orbeon.com/").isEmpty)
    }
  }
}
