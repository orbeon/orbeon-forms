/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.css

import org.orbeon.css.CSSSelectorParser._
import org.scalatest.funspec.AnyFunSpec


class CSSSelectorParserTest extends AnyFunSpec {

  describe("Complex selector") {
    it("must match") {
      assert(
        List(
          Selector(
            ElementWithFiltersSelector(
              Some(TypeSelector(Some(Some("xf")), "input")),
              List(
                AttributeFilter(None, "appearance", AttributePredicate.Equal("minimal")),
                FunctionalPseudoClassFilter("xxf-type", List(StringExpr("xs:decimal")))
              )
            ),
          Nil)
        ) == CSSSelectorParser.parseSelectors("xf|input[appearance = 'minimal']:xxf-type('xs:decimal')"))
    }
  }

  describe("Selectors") {

    val Expected = List(
      "*"                                                 -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(None)),Nil),Nil),
      "E"                                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),Nil),Nil),
      "E[foo]"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(AttributeFilter(None,"foo",AttributePredicate.Exist))),Nil),
      "E[foo=\"bar\"]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(AttributeFilter(None,"foo",AttributePredicate.Equal("bar")))),Nil),
      "E[foo~=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(AttributeFilter(None,"foo",AttributePredicate.Token("bar")))),Nil),
      "E[foo^=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(AttributeFilter(None,"foo",AttributePredicate.Start("bar")))),Nil),
      "E[foo$=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(AttributeFilter(None,"foo",AttributePredicate.End("bar")))),Nil),
      "E[foo*=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(AttributeFilter(None,"foo",AttributePredicate.Contains("bar")))),Nil),
      "E[foo|=\"en\"]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(AttributeFilter(None,"foo",AttributePredicate.Lang("en")))),Nil),
      "E:root"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(SimplePseudoClassFilter("root"))),Nil),
      "E:nth-child(n)"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("n"))))),Nil),
      "E:first-child"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(SimplePseudoClassFilter("first-child"))),Nil),
      "E::first-line"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(SimplePseudoClassFilter("first-line"))),Nil),
      "E.warning"                                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(ClassFilter("warning"))),Nil),
      "E#myid"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(IdFilter("myid"))),Nil),
      "E:not(s)"                                          -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),List(NegationFilter(TypeSelector(None,"s")))),Nil),
      "E F"                                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),Nil),List((DescendantCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"F")),Nil)))),
      "E > F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"F")),Nil)))),
      "E + F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),Nil),List((ImmediatelyFollowingCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"F")),Nil)))),
      "E ~ F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"E")),Nil),List((FollowingCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"F")),Nil)))),
      "ns|E"                                              -> Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some("ns")),"E")),Nil),Nil),
      "*|E"                                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some("*")),"E")),Nil),Nil),
      "|E"                                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(None),"E")),Nil),Nil),
      "*[hreflang|=en]"                                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(None)),List(AttributeFilter(None,"hreflang",AttributePredicate.Lang("en")))),Nil),
      "[hreflang|=en]"                                    -> Selector(ElementWithFiltersSelector(None,List(AttributeFilter(None,"hreflang",AttributePredicate.Lang("en")))),Nil),
      "*.warning"                                         -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(None)),List(ClassFilter("warning"))),Nil),// FIXME: should be reduced to same as next line
      ".warning"                                          -> Selector(ElementWithFiltersSelector(None,List(ClassFilter("warning"))),Nil),
      "*#myid"                                            -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(None)),List(IdFilter("myid"))),Nil),
      "#myid"                                             -> Selector(ElementWithFiltersSelector(None,List(IdFilter("myid"))),Nil),// FIXME: should be reduced to same as next line
      "ns|*"                                              -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(Some(Some("ns")))),Nil),Nil),
      "*|*"                                               -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(Some(Some("*")))),Nil),Nil),
      "|*"                                                -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(Some(None))),Nil),Nil),
      "h1[title]"                                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"h1")),List(AttributeFilter(None,"title",AttributePredicate.Exist))),Nil),
      "span[class=\"example\"]"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"span")),List(AttributeFilter(None,"class",AttributePredicate.Equal("example")))),Nil),
      "span[hello=\"Cleveland\"][goodbye=\"Columbus\"]"   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"span")),List(AttributeFilter(None,"hello",AttributePredicate.Equal("Cleveland")), AttributeFilter(None,"goodbye",AttributePredicate.Equal("Columbus")))),Nil),
      "a[rel~=\"copyright\"]"                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"a")),List(AttributeFilter(None,"rel",AttributePredicate.Token("copyright")))),Nil),
      "a[href=\"http://www.w3.org/\"]"                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"a")),List(AttributeFilter(None,"href",AttributePredicate.Equal("http://www.w3.org/")))),Nil),
      "a[hreflang=fr]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"a")),List(AttributeFilter(None,"hreflang",AttributePredicate.Equal("fr")))),Nil),
      "a[hreflang|=\"en\"]"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"a")),List(AttributeFilter(None,"hreflang",AttributePredicate.Lang("en")))),Nil),
//            "a[foo|bar|=\"en\"]" -> , // FIXME: unable to parse this yet
      "DIALOGUE[character=romeo]"                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"DIALOGUE")),List(AttributeFilter(None,"character",AttributePredicate.Equal("romeo")))),Nil),
      "DIALOGUE[character=juliet]"                        -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"DIALOGUE")),List(AttributeFilter(None,"character",AttributePredicate.Equal("juliet")))),Nil),
      "object[type^=\"image/\"]"                          -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"object")),List(AttributeFilter(None,"type",AttributePredicate.Start("image/")))),Nil),
      "a[href$=\".html\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"a")),List(AttributeFilter(None,"href",AttributePredicate.End(".html")))),Nil),
      "p[title*=\"hello\"]"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"p")),List(AttributeFilter(None,"title",AttributePredicate.Contains("hello")))),Nil),
      "p.note:target"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"p")),List(ClassFilter("note"), SimplePseudoClassFilter("target"))),Nil),
      "*:target::before"                                  -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(None)),List(SimplePseudoClassFilter("target"), SimplePseudoClassFilter("before"))),Nil),
      "html:lang(fr-be)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"html")),List(FunctionalPseudoClassFilter("lang",List(IdentExpr("fr-be"))))),Nil),
      "html:lang(de)"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"html")),List(FunctionalPseudoClassFilter("lang",List(IdentExpr("de"))))),Nil),
      ":lang(fr-be) > q"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("lang",List(IdentExpr("fr-be"))))),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"q")),Nil)))),
      ":lang(de) > q"                                     -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("lang",List(IdentExpr("de"))))),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"q")),Nil)))),
      "tr:nth-child(2n+1)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("2","n"), PlusExpr, NumberExpr("1"))))),Nil),
      "tr:nth-child(odd)"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("odd"))))),Nil),
      "tr:nth-child(2n+0)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("2","n"), PlusExpr, NumberExpr("0"))))),Nil),
      "tr:nth-child(even)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("even"))))),Nil),
      "p:nth-child(4n+1)"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"p")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("4","n"), PlusExpr, NumberExpr("1"))))),Nil),
      ":nth-child(10n-1)"                                 -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("10","n-1"))))),Nil),
      ":nth-child(10n+9)"                                 -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("10","n"), PlusExpr, NumberExpr("9"))))),Nil),
//            ":nth-child(10n+-1)" -> , // FIXME: should be invalid!
      "foo:nth-child(0n+5)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"foo")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("0","n"), PlusExpr, NumberExpr("5"))))),Nil),
      "foo:nth-child(5)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"foo")),List(FunctionalPseudoClassFilter("nth-child",List(NumberExpr("5"))))),Nil),
      "bar:nth-child(1n+0)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("1","n"), PlusExpr, NumberExpr("0"))))),Nil),
      "bar:nth-child(n+0)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("n"), PlusExpr, NumberExpr("0"))))),Nil),
      "bar:nth-child(n)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("n"))))),Nil),
      "tr:nth-child(2n)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("2","n"))))),Nil),
      ":nth-child( 3n + 1 )"                              -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("3","n"), PlusExpr, NumberExpr("1"))))),Nil),
      ":nth-child( +3n - 2 )"                             -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(PlusExpr, DimensionExpr("3","n"), MinusExpr, NumberExpr("2"))))),Nil),
      ":nth-child( -n+ 6)"                                -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(MinusExpr, IdentExpr("n"), PlusExpr, NumberExpr("6"))))),Nil),
      ":nth-child( +6 )"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(PlusExpr, NumberExpr("6"))))),Nil),
      ":nth-child(3 n)"                                   -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(NumberExpr("3"), IdentExpr("n"))))),Nil),
      ":nth-child(+ 2n)"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(PlusExpr, DimensionExpr("2","n"))))),Nil),
      ":nth-child(+ 2)"                                   -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(PlusExpr, NumberExpr("2"))))),Nil),
      "html|tr:nth-child(-n+6)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some("html")),"tr")),List(FunctionalPseudoClassFilter("nth-child",List(MinusExpr, IdentExpr("n"), PlusExpr, NumberExpr("6"))))),Nil),
      "tr:nth-last-child(-n+2)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"tr")),List(FunctionalPseudoClassFilter("nth-last-child",List(MinusExpr, IdentExpr("n"), PlusExpr, NumberExpr("2"))))),Nil),
      "foo:nth-last-child(odd)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"foo")),List(FunctionalPseudoClassFilter("nth-last-child",List(IdentExpr("odd"))))),Nil),
      "img:nth-of-type(2n+1)"                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"img")),List(FunctionalPseudoClassFilter("nth-of-type",List(DimensionExpr("2","n"), PlusExpr, NumberExpr("1"))))),Nil),
      "img:nth-of-type(2n)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"img")),List(FunctionalPseudoClassFilter("nth-of-type",List(DimensionExpr("2","n"))))),Nil),
      "body > h2:nth-of-type(n+2):nth-last-of-type(n+2)"  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"body")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"h2")),List(FunctionalPseudoClassFilter("nth-of-type",List(IdentExpr("n"), PlusExpr, NumberExpr("2"))), FunctionalPseudoClassFilter("nth-last-of-type",List(IdentExpr("n"), PlusExpr, NumberExpr("2")))))))),
      "body > h2:not(:first-of-type):not(:last-of-type)"  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"body")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"h2")),List(NegationFilter(SimplePseudoClassFilter("first-of-type")), NegationFilter(SimplePseudoClassFilter("last-of-type"))))))),
      "div > p:first-child"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"div")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"p")),List(SimplePseudoClassFilter("first-child")))))),
      "* > a:first-child"                                 -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(None)),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"a")),List(SimplePseudoClassFilter("first-child")))))),
      "ol > li:last-child"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"ol")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(None,"li")),List(SimplePseudoClassFilter("last-child")))))),
      "button:not([DISABLED])"                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(None,"button")),List(NegationFilter(AttributeFilter(None,"DISABLED",AttributePredicate.Exist)))),Nil),
      "*:not(FOO)"                                        -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(None)),List(NegationFilter(TypeSelector(None,"FOO")))),Nil),
      "html|*:not(:link):not(:visited)"                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(Some(Some("html")))),List(NegationFilter(SimplePseudoClassFilter("link")), NegationFilter(SimplePseudoClassFilter("visited")))),Nil),
      "*|*:not(*)"                                        -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(Some(Some("*")))),List(NegationFilter(UniversalSelector(None)))),Nil),
      "*|*:not(:hover)"                                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(Some(Some("*")))),List(NegationFilter(SimplePseudoClassFilter("hover")))),Nil)
    )

    for ((selector, expected) <- Expected)
      it(s"must match for `$selector`") {
        assert(List(expected) == CSSSelectorParser.parseSelectors(selector))
      }
  }
}
