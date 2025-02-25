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
                Filter.Attribute(TypeSelector(NsType.Default, "appearance"), AttributePredicate.Equal("minimal")),
                FunctionalPseudoClassFilter("xxf-type", List(Expr.Str("xs:decimal")))
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

      "E[foo]"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Exist))),Nil),
      "E[foo=\"bar\"]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Equal("bar")))),Nil),
      "E[foo~=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Token("bar")))),Nil),
      "E[foo^=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Start("bar")))),Nil),
      "E[foo$=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.End("bar")))),Nil),
      "E[foo*=\"bar\"]"                                   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Contains("bar")))),Nil),
      "E[foo|=\"en\"]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Lang("en")))),Nil),

      // Without quotes
      "E[foo=bar]"                                        -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Equal("bar")))),Nil),
      "E[foo~=bar]"                                       -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Token("bar")))),Nil),
      "E[foo^=bar]"                                       -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Start("bar")))),Nil),
      "E[foo$=bar]"                                       -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.End("bar")))),Nil),
      "E[foo*=bar]"                                       -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Contains("bar")))),Nil),
      "E[foo|=en]"                                        -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Lang("en")))),Nil),

      // With spaces
      "E[foo = bar]"                                      -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Equal("bar")))),Nil),
      "E[foo ~= bar]"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Token("bar")))),Nil),
      "E[foo ^= bar]"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Start("bar")))),Nil),
      "E[foo $= bar]"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.End("bar")))),Nil),
      "E[foo *= bar]"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Contains("bar")))),Nil),
      "E[foo |= en]"                                      -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Default,"foo"),AttributePredicate.Lang("en")))),Nil),

      "E[ns|foo]"                                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Exist))),Nil),
      "E[ns|foo=\"bar\"]"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Equal("bar")))),Nil),
      "E[ns|foo~=\"bar\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Token("bar")))),Nil),
      "E[ns|foo^=\"bar\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Start("bar")))),Nil),
      "E[ns|foo$=\"bar\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.End("bar")))),Nil),
      "E[ns|foo*=\"bar\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Contains("bar")))),Nil),
      "E[ns|foo|=\"en\"]"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Attribute(TypeSelector(NsType.Specific("ns"),"foo"),AttributePredicate.Lang("en")))),Nil),

      "E:root"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(SimplePseudoClassFilter("root"))),Nil),
      "E:nth-child(n)"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Ident("n"))))),Nil),
      "E:first-child"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(SimplePseudoClassFilter("first-child"))),Nil),
      "E::first-line"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(SimplePseudoClassFilter("first-line"))),Nil),
      "E.warning"                                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Class("warning"))),Nil),
      "E#myid"                                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Id("myid"))),Nil),
      "E:not(s)"                                          -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),List(Filter.Negation(TypeSelector(NsType.Default,"s")))),Nil),
      "E F"                                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),List((Combinator.Descendant,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"F")),Nil)))),
      "E > F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),List((Combinator.Child,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"F")),Nil)))),
      "E + F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),List((Combinator.ImmediatelyFollowing,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"F")),Nil)))),
      "E ~ F"                                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"E")),Nil),List((Combinator.Following,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"F")),Nil)))),
      "ns|E"                                              -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Specific("ns"),"E")),Nil),Nil),
      "*|E"                                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Any,"E")),Nil),Nil),
      "|E"                                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.None,"E")),Nil),Nil),
      "*[hreflang|=en]"                                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(Filter.Attribute(TypeSelector(NsType.Default,"hreflang"),AttributePredicate.Lang("en")))),Nil),
      "[hreflang|=en]"                                    -> Selector(ElementWithFiltersSelector(None,List(Filter.Attribute(TypeSelector(NsType.Default,"hreflang"),AttributePredicate.Lang("en")))),Nil),
      "*.warning"                                         -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(Filter.Class("warning"))),Nil),// FIXME: should be reduced to same as next line
      ".warning"                                          -> Selector(ElementWithFiltersSelector(None,List(Filter.Class("warning"))),Nil),
      "*#myid"                                            -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(Filter.Id("myid"))),Nil),
      "#myid"                                             -> Selector(ElementWithFiltersSelector(None,List(Filter.Id("myid"))),Nil),// FIXME: should be reduced to same as next line
      "ns|*"                                              -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Specific("ns"))),Nil),Nil),
      "*|*"                                               -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Any)),Nil),Nil),
      "|*"                                                -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.None)),Nil),Nil),
      "h1[title]"                                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"h1")),List(Filter.Attribute(TypeSelector(NsType.Default,"title"),AttributePredicate.Exist))),Nil),
      "span[class=\"example\"]"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"span")),List(Filter.Attribute(TypeSelector(NsType.Default,"class"),AttributePredicate.Equal("example")))),Nil),
      "span[hello=\"Cleveland\"][goodbye=\"Columbus\"]"   -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"span")),List(Filter.Attribute(TypeSelector(NsType.Default,"hello"),AttributePredicate.Equal("Cleveland")), Filter.Attribute(TypeSelector(NsType.Default,"goodbye"),AttributePredicate.Equal("Columbus")))),Nil),
      "a[rel~=\"copyright\"]"                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(Filter.Attribute(TypeSelector(NsType.Default,"rel"),AttributePredicate.Token("copyright")))),Nil),
      "a[href=\"http://www.w3.org/\"]"                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(Filter.Attribute(TypeSelector(NsType.Default,"href"),AttributePredicate.Equal("http://www.w3.org/")))),Nil),
      "a[hreflang=fr]"                                    -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(Filter.Attribute(TypeSelector(NsType.Default,"hreflang"),AttributePredicate.Equal("fr")))),Nil),
      "a[hreflang|=\"en\"]"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(Filter.Attribute(TypeSelector(NsType.Default,"hreflang"),AttributePredicate.Lang("en")))),Nil),
      "DIALOGUE[character=romeo]"                         -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"DIALOGUE")),List(Filter.Attribute(TypeSelector(NsType.Default,"character"),AttributePredicate.Equal("romeo")))),Nil),
      "DIALOGUE[character=juliet]"                        -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"DIALOGUE")),List(Filter.Attribute(TypeSelector(NsType.Default,"character"),AttributePredicate.Equal("juliet")))),Nil),
      "object[type^=\"image/\"]"                          -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"object")),List(Filter.Attribute(TypeSelector(NsType.Default,"type"),AttributePredicate.Start("image/")))),Nil),
      "a[href$=\".html\"]"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(Filter.Attribute(TypeSelector(NsType.Default,"href"),AttributePredicate.End(".html")))),Nil),
      "p[title*=\"hello\"]"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"p")),List(Filter.Attribute(TypeSelector(NsType.Default,"title"),AttributePredicate.Contains("hello")))),Nil),
      "p.note:target"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"p")),List(Filter.Class("note"), SimplePseudoClassFilter("target"))),Nil),
      "*:target::before"                                  -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(SimplePseudoClassFilter("target"), SimplePseudoClassFilter("before"))),Nil),
      "html:lang(fr-be)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"html")),List(FunctionalPseudoClassFilter("lang",List(Expr.Ident("fr-be"))))),Nil),
      "html:lang(de)"                                     -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"html")),List(FunctionalPseudoClassFilter("lang",List(Expr.Ident("de"))))),Nil),
      ":lang(fr-be) > q"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("lang",List(Expr.Ident("fr-be"))))),List((Combinator.Child,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"q")),Nil)))),
      ":lang(de) > q"                                     -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("lang",List(Expr.Ident("de"))))),List((Combinator.Child,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"q")),Nil)))),
      "tr:nth-child(2n+1)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("2","n"), Expr.Plus, Expr.Num("1"))))),Nil),
      "tr:nth-child(odd)"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Ident("odd"))))),Nil),
      "tr:nth-child(2n+0)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("2","n"), Expr.Plus, Expr.Num("0"))))),Nil),
      "tr:nth-child(even)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Ident("even"))))),Nil),
      "p:nth-child(4n+1)"                                 -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"p")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("4","n"), Expr.Plus, Expr.Num("1"))))),Nil),
      ":nth-child(10n-1)"                                 -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("10","n-1"))))),Nil),
      ":nth-child(10n+9)"                                 -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("10","n"), Expr.Plus, Expr.Num("9"))))),Nil),
//            ":nth-child(10n+-1)" -> , // FIXME: should be invalid!
      "foo:nth-child(0n+5)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"foo")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("0","n"), Expr.Plus, Expr.Num("5"))))),Nil),
      "foo:nth-child(5)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"foo")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Num("5"))))),Nil),
      "bar:nth-child(1n+0)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("1","n"), Expr.Plus, Expr.Num("0"))))),Nil),
      "bar:nth-child(n+0)"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Ident("n"), Expr.Plus, Expr.Num("0"))))),Nil),
      "bar:nth-child(n)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"bar")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Ident("n"))))),Nil),
      "tr:nth-child(2n)"                                  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("2","n"))))),Nil),
      ":nth-child( 3n + 1 )"                              -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Dimension("3","n"), Expr.Plus, Expr.Num("1"))))),Nil),
      ":nth-child( +3n - 2 )"                             -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Plus, Expr.Dimension("3","n"), Expr.Minus, Expr.Num("2"))))),Nil),
      ":nth-child( -n+ 6)"                                -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Minus, Expr.Ident("n"), Expr.Plus, Expr.Num("6"))))),Nil),
      ":nth-child( +6 )"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Plus, Expr.Num("6"))))),Nil),
      ":nth-child(3 n)"                                   -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Num("3"), Expr.Ident("n"))))),Nil),
      ":nth-child(+ 2n)"                                  -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Plus, Expr.Dimension("2","n"))))),Nil),
      ":nth-child(+ 2)"                                   -> Selector(ElementWithFiltersSelector(None,List(FunctionalPseudoClassFilter("nth-child",List(Expr.Plus, Expr.Num("2"))))),Nil),
      "html|tr:nth-child(-n+6)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Specific("html"),"tr")),List(FunctionalPseudoClassFilter("nth-child",List(Expr.Minus, Expr.Ident("n"), Expr.Plus, Expr.Num("6"))))),Nil),
      "tr:nth-last-child(-n+2)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"tr")),List(FunctionalPseudoClassFilter("nth-last-child",List(Expr.Minus, Expr.Ident("n"), Expr.Plus, Expr.Num("2"))))),Nil),
      "foo:nth-last-child(odd)"                           -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"foo")),List(FunctionalPseudoClassFilter("nth-last-child",List(Expr.Ident("odd"))))),Nil),
      "img:nth-of-type(2n+1)"                             -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"img")),List(FunctionalPseudoClassFilter("nth-of-type",List(Expr.Dimension("2","n"), Expr.Plus, Expr.Num("1"))))),Nil),
      "img:nth-of-type(2n)"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"img")),List(FunctionalPseudoClassFilter("nth-of-type",List(Expr.Dimension("2","n"))))),Nil),
      "body > h2:nth-of-type(n+2):nth-last-of-type(n+2)"  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"body")),Nil),List((Combinator.Child,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"h2")),List(FunctionalPseudoClassFilter("nth-of-type",List(Expr.Ident("n"), Expr.Plus, Expr.Num("2"))), FunctionalPseudoClassFilter("nth-last-of-type",List(Expr.Ident("n"), Expr.Plus, Expr.Num("2")))))))),
      "body > h2:not(:first-of-type):not(:last-of-type)"  -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"body")),Nil),List((Combinator.Child,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"h2")),List(Filter.Negation(SimplePseudoClassFilter("first-of-type")), Filter.Negation(SimplePseudoClassFilter("last-of-type"))))))),
      "div > p:first-child"                               -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"div")),Nil),List((Combinator.Child,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"p")),List(SimplePseudoClassFilter("first-child")))))),
      "* > a:first-child"                                 -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),Nil),List((Combinator.Child,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"a")),List(SimplePseudoClassFilter("first-child")))))),
      "ol > li:last-child"                                -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"ol")),Nil),List((Combinator.Child,ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"li")),List(SimplePseudoClassFilter("last-child")))))),
      "button:not([DISABLED])"                            -> Selector(ElementWithFiltersSelector(Some(TypeSelector(NsType.Default,"button")),List(Filter.Negation(Filter.Attribute(TypeSelector(NsType.Default,"DISABLED"),AttributePredicate.Exist)))),Nil),
      "*:not(FOO)"                                        -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Default)),List(Filter.Negation(TypeSelector(NsType.Default,"FOO")))),Nil),
      "html|*:not(:link):not(:visited)"                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Specific("html"))),List(Filter.Negation(SimplePseudoClassFilter("link")), Filter.Negation(SimplePseudoClassFilter("visited")))),Nil),
      "*|*:not(*)"                                        -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Any)),List(Filter.Negation(UniversalSelector(NsType.Default)))),Nil),
      "*|*:not(:hover)"                                   -> Selector(ElementWithFiltersSelector(Some(UniversalSelector(NsType.Any)),List(Filter.Negation(SimplePseudoClassFilter("hover")))),Nil)
    )

    for ((selector, expected) <- Expected)
      it(s"must match for `$selector`") {
        assert(List(expected) == CSSSelectorParser.parseSelectors(selector))
      }
  }
}
