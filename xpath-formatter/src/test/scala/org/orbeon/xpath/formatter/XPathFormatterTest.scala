package org.orbeon.xpath.formatter

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class XPathFormatterTest extends AnyFunSpec with Matchers {

  describe("FLWOR expressions") {
    it("formats short FLWOR inline when under threshold") {
      val input = "for $x in 1 to 10 return $x * 2"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "for $x in 1 to 10 return $x * 2"
    }

    it("formats multi-variable for and let clauses on separate lines") {
      val input = "for $x in 1 to 10, $y in 1 to 10 let $sum := $x + $y, $prod := $x * $y return $sum + $prod"
      val options = XPathFormatterOptions.default.copy(flworInlineThreshold = 10)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """for $x in 1 to 10,
          |    $y in 1 to 10
          |let $sum  := $x + $y,
          |    $prod := $x * $y
          |return $sum + $prod""".stripMargin
      formatted shouldBe expected
    }

    it("formats FLWOR with where and order by clauses") {
      val input = "for $item in //item where $item/@price > 100 order by $item/@price descending return $item/name"
      val options = XPathFormatterOptions.default.copy(flworInlineThreshold = 10)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """for $item in //item
          |where $item/@price > 100
          |order by $item/@price descending
          |return $item/name""".stripMargin
      formatted shouldBe expected
    }

    it("supports indented return clause") {
      val input = "let $x := 10 return $x * 2"
      val options = XPathFormatterOptions.default.copy(flworInlineThreshold = 10, flworIndentReturn = true)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """let $x := 10
          |  return $x * 2""".stripMargin
      formatted shouldBe expected
    }

    it("formats FLWOR returning multi-line chained conditional expression") {
      val input =
        """for $start in $first-date,
          |    $end in $second-date,
          |    $startW in xs:integer(format-date($start, '[F1]')),
          |    $endW in xs:integer(format-date($end, '[F1]')),
          |    $days in days-from-duration($end - $start),
          |    $daysNoWeekends in $days - (floor($days div 7) * 2)
          |return
          |    if ($days mod 7 != 0 and ($startW = 7 or $endW = 7)) then
          |    $daysNoWeekends - 1
          |  else
          |    if ($endW < $startW) then
          |      $daysNoWeekends - 2
          |    else
          |      $daysNoWeekends""".stripMargin
      val formatted = XPathFormatter.format(input)
      val expected =
        """for $start          in $first-date,
          |    $end            in $second-date,
          |    $startW         in xs:integer(format-date($start, '[F1]')),
          |    $endW           in xs:integer(format-date($end, '[F1]')),
          |    $days           in days-from-duration($end - $start),
          |    $daysNoWeekends in $days - (floor($days div 7) * 2)
          |return
          |  if ($days mod 7 != 0 and ($startW = 7 or $endW = 7)) then
          |    $daysNoWeekends - 1
          |  else if ($endW < $startW) then
          |    $daysNoWeekends - 2
          |  else
          |    $daysNoWeekends""".stripMargin
      formatted shouldBe expected
    }

    it("vertically aligns := in multi-variable let clauses") {
      val input = "let $short := 1, $longerVariable := 2, $mid := 3 return $short + $longerVariable + $mid"
      val options = XPathFormatterOptions.default.copy(flworInlineThreshold = 10)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """let $short          := 1,
          |    $longerVariable := 2,
          |    $mid            := 3
          |return $short + $longerVariable + $mid""".stripMargin
      formatted shouldBe expected
    }

    it("formats FLWOR without aligning bindings when flworAlignBindings is false") {
      val input =
        """for $start in $first-date,
          |    $end in $second-date,
          |    $daysNoWeekends in $days
          |return $daysNoWeekends""".stripMargin
      val options = XPathFormatterOptions.default.copy(flworAlignBindings = false)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """for $start in $first-date,
          |    $end in $second-date,
          |    $daysNoWeekends in $days
          |return $daysNoWeekends""".stripMargin
      formatted shouldBe expected
    }

    it("formats nested FLWOR expressions with correct indentation") {
      val input = "for $x in 1 to 2 return for $y in 1 to 2 return $x * $y"
      val options = XPathFormatterOptions.default.copy(flworInlineThreshold = 10)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """for $x in 1 to 2
          |return
          |  for $y in 1 to 2
          |  return $x * $y""".stripMargin
      formatted shouldBe expected
    }
  }

  describe("Conditional expressions (if-then-else)") {
    it("formats short if-then-else inline when under threshold") {
      val input = "if ($x > 0) then $x else -$x"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "if ($x > 0) then $x else -$x"
    }

    it("formats multi-line if-then-else when exceeding threshold") {
      val input = "if ($count > 100) then concat('High: ', string($count)) else concat('Low: ', string($count))"
      val options = XPathFormatterOptions.default.copy(ifInlineThreshold = 20)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """if ($count > 100) then
          |  concat('High: ', string($count))
          |else
          |  concat('Low: ', string($count))""".stripMargin
      formatted shouldBe expected
    }

    it("formats if-then-else in ClausePerLine style") {
      val input = "if ($valid) then $successMessage else $errorMessage"
      val options = XPathFormatterOptions.default.copy(ifInlineThreshold = 10, ifStyle = IfStyle.ClausePerLine)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """if ($valid)
          |then $successMessage
          |else $errorMessage""".stripMargin
      formatted shouldBe expected
    }

    it("formats chained else-if expressions") {
      val input =
        """if ($x > 0) then
          |  'positive'
          |else
          |  if ($x < 0) then
          |    'negative'
          |  else
          |    'zero'""".stripMargin
      val options = XPathFormatterOptions.default.copy(ifInlineThreshold = 10)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """if ($x > 0) then
          |  'positive'
          |else if ($x < 0) then
          |  'negative'
          |else
          |  'zero'""".stripMargin
      formatted shouldBe expected
    }

    it("formats chained else-if in ClausePerLine style") {
      val input = "if ($x > 0) then 1 else if ($x < 0) then -1 else 0"
      val options = XPathFormatterOptions.default.copy(ifInlineThreshold = 10, ifStyle = IfStyle.ClausePerLine)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """if ($x > 0)
          |then 1
          |else if ($x < 0)
          |then -1
          |else 0""".stripMargin
      formatted shouldBe expected
    }
  }

  describe("Arrow expressions (=>)") {
    it("formats chained arrow expressions on new lines by default") {
      val input = "$text => normalize-space() => upper-case() => substring(1, 10)"
      val formatted = XPathFormatter.format(input)
      val expected =
        """$text
          |  => normalize-space()
          |  => upper-case()
          |  => substring(1, 10)""".stripMargin
      formatted shouldBe expected
    }

    it("formats arrow expressions inline when arrowOnNewline is false") {
      val input = "$text => normalize-space() => upper-case()"
      val options = XPathFormatterOptions.default.copy(arrowOnNewline = false)
      val formatted = XPathFormatter.format(input, options)
      formatted shouldBe "$text => normalize-space() => upper-case()"
    }
  }

  describe("Maps and Arrays (XPath 3.1)") {
    it("formats map constructors on multiple lines when containing multiple entries") {
      val input = "map { 'name': 'John', 'age': 30, 'city': 'New York' }"
      val formatted = XPathFormatter.format(input)
      val expected =
        """map {
          |  'name': 'John',
          |  'age': 30,
          |  'city': 'New York'
          |}""".stripMargin
      formatted shouldBe expected
    }

    it("formats square arrays") {
      val input = "[1, 2, 3, 4, 5]"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "[1, 2, 3, 4, 5]"
    }

    it("formats lookups") {
      val input = "$map?name"
      XPathFormatter.format(input) shouldBe "$map?name"

      val wildcardLookup = "$array?*"
      XPathFormatter.format(wildcardLookup) shouldBe "$array?*"

      val unaryLookup = "?city"
      XPathFormatter.format(unaryLookup) shouldBe "?city"
    }
  }

  describe("Path expressions") {
    it("formats absolute paths") {
      val input = "/root/section/item[@active = 'true']"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "/root/section/item[@active = 'true']"
    }

    it("formats descendant-or-self paths") {
      val input = "//section//p"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "//section//p"
    }

    it("formats path with stepPerLine option") {
      val input = "/root/section/item"
      val options = XPathFormatterOptions.default.copy(stepPerLine = true)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """/root
          |  /section
          |  /item""".stripMargin
      formatted shouldBe expected
    }
  }

  describe("Binary operators and expressions") {
    it("formats arithmetic and comparison expressions with spaces") {
      val input = "$x+$y*$z"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "$x + $y * $z"
    }

    it("formats boolean operators") {
      val input = "$a and $b or $c"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "$a and $b or $c"
    }

    it("formats string concatenation") {
      val input = "$first || ' ' || $last"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "$first || ' ' || $last"
    }

    it("formats range expression") {
      val input = "1 to 10"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "1 to 10"
    }
  }

  describe("Quantified expressions") {
    it("formats some/every expressions") {
      val input = "some $x in 1 to 10 satisfies $x > 5"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "some $x in 1 to 10 satisfies $x > 5"
    }
  }

  describe("String literal quote preference") {
    it("preserves quotes by default") {
      val input = "concat('single', \"double\")"
      val formatted = XPathFormatter.format(input)
      formatted shouldBe "concat('single', \"double\")"
    }

    it("converts to double quotes when requested") {
      val input = "concat('single', \"double\")"
      val options = XPathFormatterOptions.default.copy(quotePreference = QuotePreference.PreferDouble)
      val formatted = XPathFormatter.format(input, options)
      formatted shouldBe "concat(\"single\", \"double\")"
    }

    it("converts to single quotes when requested") {
      val input = "concat('single', \"double\")"
      val options = XPathFormatterOptions.default.copy(quotePreference = QuotePreference.PreferSingle)
      val formatted = XPathFormatter.format(input, options)
      formatted shouldBe "concat('single', 'double')"
    }
  }

  describe("Custom indentation") {
    it("formats with 4-space indentation") {
      val input = "if ($x > 0) then 'positive' else 'non-positive'"
      val options = XPathFormatterOptions.default.copy(indentSize = 4, ifInlineThreshold = 10)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """if ($x > 0) then
          |    'positive'
          |else
          |    'non-positive'""".stripMargin
      formatted shouldBe expected
    }

    it("formats with tab indentation") {
      val input = "if ($x > 0) then 'positive' else 'non-positive'"
      val options = XPathFormatterOptions.default.copy(useTabs = true, ifInlineThreshold = 10)
      val formatted = XPathFormatter.format(input, options)
      val expected =
        """if ($x > 0) then
          |\t'positive'
          |else
          |\t'non-positive'""".stripMargin.replace("\\t", "\t")
      formatted shouldBe expected
    }
  }

  describe("Syntax highlighting") {
    it("highlights raw XPath tokens with XPathHighlighter") {
      val input = "(: comment :) for $x in 1 to 10 return concat('Num: ', $x)"
      val spans = XPathHighlighter.highlight(input)

      spans.map(_.tokenType) shouldBe List(
        XPathTokenType.Comment,
        XPathTokenType.Plain,
        XPathTokenType.Keyword,
        XPathTokenType.Plain,
        XPathTokenType.Variable,
        XPathTokenType.Plain,
        XPathTokenType.Keyword,
        XPathTokenType.Plain,
        XPathTokenType.NumberLiteral,
        XPathTokenType.Plain,
        XPathTokenType.Keyword,
        XPathTokenType.Plain,
        XPathTokenType.NumberLiteral,
        XPathTokenType.Plain,
        XPathTokenType.Keyword,
        XPathTokenType.Plain,
        XPathTokenType.FunctionCall,
        XPathTokenType.Punctuation,
        XPathTokenType.StringLiteral,
        XPathTokenType.Punctuation,
        XPathTokenType.Plain,
        XPathTokenType.Variable,
        XPathTokenType.Punctuation
      )
    }

    it("produces HTML highlighting with CSS classes via XPathHighlighter") {
      val input = "if ($x eq 'hello') then 1 else 2"
      val html = XPathHighlighter.highlightToHtml(input)
      html shouldBe """<span class="xpath-keyword">if</span> <span class="xpath-punctuation">(</span><span class="xpath-variable">$x</span> <span class="xpath-keyword">eq</span> <span class="xpath-string">&#39;hello&#39;</span><span class="xpath-punctuation">)</span> <span class="xpath-keyword">then</span> <span class="xpath-number">1</span> <span class="xpath-keyword">else</span> <span class="xpath-number">2</span>"""
    }

    it("produces ANSI colored highlighting via XPathHighlighter") {
      val input = "$x + 1"
      val ansi = XPathHighlighter.highlightToAnsi(input)
      ansi shouldBe s"${XPathTokenType.Variable.ansiColor}$$x${XPathTokenType.AnsiReset} ${XPathTokenType.Operator.ansiColor}+${XPathTokenType.AnsiReset} ${XPathTokenType.NumberLiteral.ansiColor}1${XPathTokenType.AnsiReset}"
    }

    it("formats and highlights simultaneously via XPathFormatter.formatToHtml") {
      val input = "for $x in 1 to 10 return $x * 2"
      val html = XPathFormatter.formatToHtml(input)
      val expected = """<span class="xpath-keyword">for</span> <span class="xpath-variable">$x</span> <span class="xpath-keyword">in</span> <span class="xpath-number">1</span> <span class="xpath-keyword">to</span> <span class="xpath-number">10</span> <span class="xpath-keyword">return</span> <span class="xpath-variable">$x</span> <span class="xpath-operator">*</span> <span class="xpath-number">2</span>"""
      html shouldBe expected
    }

    it("formats and highlights multi-line FLWOR expressions to HTML") {
      val input = "for $x in 1 to 10, $y in 1 to 10 return $x + $y"
      val options = XPathFormatterOptions.default.copy(flworInlineThreshold = 10)
      val html = XPathFormatter.formatToHtml(input, options)
      html should include("""<span class="xpath-keyword">for</span> <span class="xpath-variable">$x</span> <span class="xpath-keyword">in</span> <span class="xpath-number">1</span> <span class="xpath-keyword">to</span> <span class="xpath-number">10</span><span class="xpath-punctuation">,</span>""")
      html should include("""<span class="xpath-keyword">return</span> <span class="xpath-variable">$x</span> <span class="xpath-operator">+</span> <span class="xpath-variable">$y</span>""")
    }
  }
}
