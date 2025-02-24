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

import org.orbeon.css.CSSSelectorParser.*
import org.scalatest.funspec.AnyFunSpec


class CSSSelectorParserTest extends AnyFunSpec {

  describe("Complex selector") {
    it("must match") {
      assert(
        List(
          Selector(
            ElementWithFiltersSelector(
              Some(TypeSelector(NsType.Specific("xf"), "input")),
              List(
                AttributeFilter(TypeSelector(NsType.Default, "appearance"), AttributePredicate.Equal("minimal")),
                FunctionalPseudoClassFilter("xxf-type", List(StringExpr("xs:decimal")))
              )
            ),
          Nil)
        ) == CSSSelectorParser.parseSelectors("xf|input[appearance = 'minimal']:xxf-type('xs:decimal')"))
    }
  }

  describe("Selectors") {

    val Expected = List(
      "*"                                                 -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),Nil),Nil),
      "E"                                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),Nil),

      "E[foo]"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Exist))),Nil),
      "E[foo=\"bar\"]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Equal("bar")))),Nil),
      "E[foo~=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Token("bar")))),Nil),
      "E[foo^=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Start("bar")))),Nil),
      "E[foo$=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.End("bar")))),Nil),
      "E[foo*=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Contains("bar")))),Nil),
      "E[foo|=\"en\"]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Lang("en")))),Nil),

      // Without quotes
      "E[foo=bar]"                                        -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Equal("bar")))),Nil),
      "E[foo~=bar]"                                       -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Token("bar")))),Nil),
      "E[foo^=bar]"                                       -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Start("bar")))),Nil),
      "E[foo$=bar]"                                       -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.End("bar")))),Nil),
      "E[foo*=bar]"                                       -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Contains("bar")))),Nil),
      "E[foo|=en]"                                        -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Lang("en")))),Nil),

      // With spaces
      "E[foo = bar]"                                      -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Equal("bar")))),Nil),
      "E[foo ~= bar]"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Token("bar")))),Nil),
      "E[foo ^= bar]"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Start("bar")))),Nil),
      "E[foo $= bar]"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.End("bar")))),Nil),
      "E[foo *= bar]"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Contains("bar")))),Nil),
      "E[foo |= en]"                                      -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Default,"foo"),AttributePredicate.Lang("en")))),Nil),

      "E[ns|foo]"                                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Exist))),Nil),
      "E[ns|foo=\"bar\"]"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Equal("bar")))),Nil),
      "E[ns|foo~=\"bar\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Token("bar")))),Nil),
      "E[ns|foo^=\"bar\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Start("bar")))),Nil),
      "E[ns|foo$=\"bar\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.End("bar")))),Nil),
      "E[ns|foo*=\"bar\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Contains("bar")))),Nil),
      "E[ns|foo|=\"en\"]"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(AttributeFilter(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Lang("en")))),Nil),

      "E:root"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(SimplePseudoClassFilter("root"))),Nil),
      "E:nth-child(n)"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("n"))))),Nil),
      "E:first-child"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(SimplePseudoClassFilter("first-child"))),Nil),
      "E::first-line"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(SimplePseudoClassFilter("first-line"))),Nil),
      "E.warning"                                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(ClassFilter("warning"))),Nil),
      "E#myid"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(IdFilter("myid"))),Nil),
      "E:not(s)"                                          -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(NegationFilter(TypeSelector(NsType.Default,"s")))),Nil),
      "E F"                                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),List((DescendantCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"F")),Nil)))),
      "E > F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"F")),Nil)))),
      "E + F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),List((ImmediatelyFollowingCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"F")),Nil)))),
      "E ~ F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),List((FollowingCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"F")),Nil)))),
      "ns|E"                                              -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Specific("ns"),"E")),Nil),Nil),
      "*|E"                                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Any,"E")),Nil),Nil),
      "|E"                                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.None,"E")),Nil),Nil),
      "*[hreflang|=en]"                                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(AttributeFilter(TypeSelector(NsType.Default,"hreflang"),AttributePredicate.Lang("en")))),Nil),
      "[hreflang|=en]"                                    -> Selector(ElementWithFiltersSelector(None,List(AttributeFilter(TypeSelector(NsType.Default,"hreflang"),AttributePredicate.Lang("en")))),Nil),
      "*.warning"                                         -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(ClassFilter("warning"))),Nil),// FIXME: should be reduced to same as next line
      ".warning"                                          -> Selector(ElementWithFiltersSelector(None,List(ClassFilter("warning"))),Nil),
      "*#myid"                                            -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(IdFilter("myid"))),Nil),
      "#myid"                                             -> Selector(ElementWithFiltersSelector(None,List(IdFilter("myid"))),Nil),// FIXME: should be reduced to same as next line
      "ns|*"                                              -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Specific("ns"))),Nil),Nil),
      "*|*"                                               -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Any)),Nil),Nil),
      "|*"                                                -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.None)),Nil),Nil),
      "h1[title]"                                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"h1")),List(AttributeFilter(TypeSelector(NsType.Default,"title"),AttributePredicate.Exist))),Nil),
      "span[class=\"example\"]"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"span")),List(AttributeFilter(TypeSelector(NsType.Default,"class"),AttributePredicate.Equal("example")))),Nil),
      "span[hello=\"Cleveland\"][goodbye=\"Columbus\"]"   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"span")),List(AttributeFilter(TypeSelector(NsType.Default,"hello"),AttributePredicate.Equal("Cleveland")), AttributeFilter(TypeSelector(NsType.Default,"goodbye"),AttributePredicate.Equal("Columbus")))),Nil),
      "a[rel~=\"copyright\"]"                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(AttributeFilter(TypeSelector(NsType.Default,"rel"),AttributePredicate.Token("copyright")))),Nil),
      "a[href=\"http://www.w3.org/\"]"                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(AttributeFilter(TypeSelector(NsType.Default,"href"),AttributePredicate.Equal("http://www.w3.org/")))),Nil),
      "a[hreflang=fr]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(AttributeFilter(TypeSelector(NsType.Default,"hreflang"),AttributePredicate.Equal("fr")))),Nil),
      "a[hreflang|=\"en\"]"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(AttributeFilter(TypeSelector(NsType.Default,"hreflang"),AttributePredicate.Lang("en")))),Nil),
      "DIALOGUE[character=romeo]"                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"DIALOGUE")),List(AttributeFilter(TypeSelector(NsType.Default,"character"),AttributePredicate.Equal("romeo")))),Nil),
      "DIALOGUE[character=juliet]"                        -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"DIALOGUE")),List(AttributeFilter(TypeSelector(NsType.Default,"character"),AttributePredicate.Equal("juliet")))),Nil),
      "object[type^=\"image/\"]"                          -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"object")),List(AttributeFilter(TypeSelector(NsType.Default,"type"),AttributePredicate.Start("image/")))),Nil),
      "a[href$=\".html\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(AttributeFilter(TypeSelector(NsType.Default,"href"),AttributePredicate.End(".html")))),Nil),
      "p[title*=\"hello\"]"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"p")),List(AttributeFilter(TypeSelector(NsType.Default,"title"),AttributePredicate.Contains("hello")))),Nil),
      "p.note:target"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"p")),List(ClassFilter("note"), SimplePseudoClassFilter("target"))),Nil),
      "*:target::before"                                  -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(SimplePseudoClassFilter("target"), SimplePseudoClassFilter("before"))),Nil),
      "html:lang(fr-be)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"html")),List(FunctionalPseudoClassFilter("lang",List(IdentExpr("fr-be"))))),Nil),
      "html:lang(de)"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"html")),List(FunctionalPseudoClassFilter("lang",List(IdentExpr("de"))))),Nil),
      ":lang(fr-be) > q"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("lang",List(IdentExpr("fr-be"))))),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"q")),Nil)))),
      ":lang(de) > q"                                     -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("lang",List(IdentExpr("de"))))),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"q")),Nil)))),
      "tr:nth-child(2n+1)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("2","n"), PlusExpr, NumberExpr("1"))))),Nil),
      "tr:nth-child(odd)"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("odd"))))),Nil),
      "tr:nth-child(2n+0)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("2","n"), PlusExpr, NumberExpr("0"))))),Nil),
      "tr:nth-child(even)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("even"))))),Nil),
      "p:nth-child(4n+1)"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"p")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("4","n"), PlusExpr, NumberExpr("1"))))),Nil),
      ":nth-child(10n-1)"                                 -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("10","n-1"))))),Nil),
      ":nth-child(10n+9)"                                 -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("10","n"), PlusExpr, NumberExpr("9"))))),Nil),
//            ":nth-child(10n+-1)" -> , // FIXME: should be invalid!
      "foo:nth-child(0n+5)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"foo")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("0","n"), PlusExpr, NumberExpr("5"))))),Nil),
      "foo:nth-child(5)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"foo")),List(FunctionalPseudoClassFilter("nth-child",List(NumberExpr("5"))))),Nil),
      "bar:nth-child(1n+0)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("1","n"), PlusExpr, NumberExpr("0"))))),Nil),
      "bar:nth-child(n+0)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("n"), PlusExpr, NumberExpr("0"))))),Nil),
      "bar:nth-child(n)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(IdentExpr("n"))))),Nil),
      "tr:nth-child(2n)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("2","n"))))),Nil),
      ":nth-child( 3n + 1 )"                              -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(DimensionExpr("3","n"), PlusExpr, NumberExpr("1"))))),Nil),
      ":nth-child( +3n - 2 )"                             -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(PlusExpr, DimensionExpr("3","n"), MinusExpr, NumberExpr("2"))))),Nil),
      ":nth-child( -n+ 6)"                                -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(MinusExpr, IdentExpr("n"), PlusExpr, NumberExpr("6"))))),Nil),
      ":nth-child( +6 )"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(PlusExpr, NumberExpr("6"))))),Nil),
      ":nth-child(3 n)"                                   -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(NumberExpr("3"), IdentExpr("n"))))),Nil),
      ":nth-child(+ 2n)"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(PlusExpr, DimensionExpr("2","n"))))),Nil),
      ":nth-child(+ 2)"                                   -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(PlusExpr, NumberExpr("2"))))),Nil),
      "html|tr:nth-child(-n+6)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Specific("html"),"tr")),List(FunctionalPseudoClassFilter("nth-child",List(MinusExpr, IdentExpr("n"), PlusExpr, NumberExpr("6"))))),Nil),
      "tr:nth-last-child(-n+2)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-last-child",List(MinusExpr, IdentExpr("n"), PlusExpr, NumberExpr("2"))))),Nil),
      "foo:nth-last-child(odd)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"foo")),List(FunctionalPseudoClassFilter("nth-last-child",List(IdentExpr("odd"))))),Nil),
      "img:nth-of-type(2n+1)"                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"img")),List(FunctionalPseudoClassFilter("nth-of-type",List(DimensionExpr("2","n"), PlusExpr, NumberExpr("1"))))),Nil),
      "img:nth-of-type(2n)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"img")),List(FunctionalPseudoClassFilter("nth-of-type",List(DimensionExpr("2","n"))))),Nil),
      "body > h2:nth-of-type(n+2):nth-last-of-type(n+2)"  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"body")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"h2")),List(FunctionalPseudoClassFilter("nth-of-type",List(IdentExpr("n"), PlusExpr, NumberExpr("2"))), FunctionalPseudoClassFilter("nth-last-of-type",List(IdentExpr("n"), PlusExpr, NumberExpr("2")))))))),
      "body > h2:not(:first-of-type):not(:last-of-type)"  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"body")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"h2")),List(NegationFilter(SimplePseudoClassFilter("first-of-type")), NegationFilter(SimplePseudoClassFilter("last-of-type"))))))),
      "div > p:first-child"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"div")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"p")),List(SimplePseudoClassFilter("first-child")))))),
      "* > a:first-child"                                 -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(SimplePseudoClassFilter("first-child")))))),
      "ol > li:last-child"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"ol")),Nil),List((ChildCombinator,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"li")),List(SimplePseudoClassFilter("last-child")))))),
      "button:not([DISABLED])"                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"button")),List(NegationFilter(AttributeFilter(TypeSelector(NsType.Default,"DISABLED"),AttributePredicate.Exist)))),Nil),
      "*:not(FOO)"                                        -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(NegationFilter(TypeSelector(NsType.Default,"FOO")))),Nil),
      "html|*:not(:link):not(:visited)"                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Specific("html"))),List(NegationFilter(SimplePseudoClassFilter("link")), NegationFilter(SimplePseudoClassFilter("visited")))),Nil),
      "*|*:not(*)"                                        -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Any)),List(NegationFilter(UniversalSelector(NsType.Default)))),Nil),
      "*|*:not(:hover)"                                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Any)),List(NegationFilter(SimplePseudoClassFilter("hover")))),Nil)
    )

    for ((selector, expected) <- Expected)
      it(s"must match for `$selector`") {
        assert(List(expected) == CSSSelectorParser.parseSelectors(selector))
      }
  }
}
