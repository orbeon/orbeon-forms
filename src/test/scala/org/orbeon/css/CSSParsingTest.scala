/**
 * Copyright (C) 2025 Orbeon, Inc.
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

import cats.implicits.catsSyntaxOptionId
import org.log4s.Logger
import org.orbeon.css.CSSParsing.CSSCache
import org.orbeon.oxf.util.IndentedLogger
import org.scalatest.funspec.AnyFunSpec

import java.net.URL


class CSSParsingTest extends AnyFunSpec {

  val logger: Logger                          = org.log4s.getLogger(classOf[CSSParsingTest])
  implicit val indentedLogger: IndentedLogger = new IndentedLogger(logger)

  describe("Media query") {
    val all    = MediaQuery("all")
    val other1 = MediaQuery("screen and (min-width: 800px)")
    val other2 = MediaQuery("(prefers-color-scheme: light)")
    val other3 = MediaQuery("(orientation: landscape)")

    it("must combine individual media queries (and operator)") {
      assert(all.and(other1)    == other1)
      assert(other1.and(all)    == other1)
      assert(other1.and(other1) == other1)
      assert(other2.and(other2) == other2)
      assert(all.and(all)       == all)
      assert(other1.and(other2) == MediaQuery("screen and (min-width: 800px) and (prefers-color-scheme: light)"))
      assert(other2.and(other1) == MediaQuery("(prefers-color-scheme: light) and screen and (min-width: 800px)"))
    }

    it("must combine media query lists (and operator)") {
      val expected = List(
        (Nil                 , Nil                 ) -> Nil,
        (List(all)           , Nil                 ) -> Nil,
        (Nil                 , List(all)           ) -> Nil,
        (List(all)           , List(other1)        ) -> List(other1),
        (List(other1)        , List(all)           ) -> List(other1),
        (List(other1)        , List(other1)        ) -> List(other1),
        (List(other2)        , List(other2)        ) -> List(other2),
        (List(other1)        , List(other2)        ) -> List(MediaQuery("screen and (min-width: 800px) and (prefers-color-scheme: light)")),
        (List(other2)        , List(other1)        ) -> List(MediaQuery("(prefers-color-scheme: light) and screen and (min-width: 800px)")),
        (List(other1)        , List(other2, other3)) -> List(
                                                          MediaQuery("screen and (min-width: 800px) and (prefers-color-scheme: light)"),
                                                          MediaQuery("screen and (min-width: 800px) and (orientation: landscape)")
                                                        ),
        (List(other1, other2), List(other3)        ) -> List(
                                                          MediaQuery("screen and (min-width: 800px) and (orientation: landscape)"),
                                                          MediaQuery("(prefers-color-scheme: light) and (orientation: landscape)")
                                                        )
      )

      for (((param1, param2), result) <- expected) {
        assert(MediaQuery.and(param1, param2) == result)
      }
    }

    it("must simplify media query lists (or operator)") {
      val expected = List(
        Nil                       -> Nil,
        List(all)                 -> List(all),
        List(all, other1)         -> List(all),
        List(all, other1, other2) -> List(all),
        List(other1, other2)      -> List(other1, other2),
        List(other2, other1)      -> List(other2, other1)
      )

      for ((param, result) <- expected) {
        assert(MediaQuery.simplified(param) == result)
      }
    }
  }

  describe("Variable definitions") {
    implicit val cssCache: CSSCache = new CSSCache()

    val cssWithVariableDefinitions1 = """|:root {
                                        |  --orbeon-test1: test1;
                                        |  --orbeon-test2: test2
                                        |}
                                        |@media print {
                                        |  .orbeon, .otherclass {
                                        |    --orbeon-test1: test1-print;
                                        |    --orbeon-test3: test3-print;
                                        |  }
                                        |}""".stripMargin

    it("must parse variable definitions from a CSS string") {
      val variableDefinitions1 = CSSParsing.variableDefinitions(
        resource    = Style(cssWithVariableDefinitions1, List(MediaQuery("all"))),
        resolvedURL = _ => new URL("") // Won't be called
      )

      // Only check variable names and values for now
      val actualVariableNamesAndValues = variableDefinitions1.variableDefinitions.map(d => (d.name, d.value))

      val expectedVariableNamesAndValues = List(
        "--orbeon-test1" -> "test1",
        "--orbeon-test2" -> "test2",
        "--orbeon-test1" -> "test1-print",
        "--orbeon-test3" -> "test3-print"
      )

      assert(actualVariableNamesAndValues == expectedVariableNamesAndValues)
    }

    it("must inject a variable value into a single declaration value") {
      val testVariableDefinitions = VariableDefinitions(
        List(
          VariableDefinition(name = "--base-font-size", value = "13px"   , mediaQueries = List(MediaQuery("all")), selectors = List(Selector(".orbeon"))),
          VariableDefinition(name = "--hint-font-size", value = "smaller", mediaQueries = List(MediaQuery("all")), selectors = List(Selector(".orbeon")))
        )
      )

      val expected = List(
        "var(--base-font-size)"                                              -> "13px",
        "var(--hint-font-size)"                                              -> "smaller",
        "var(--undefined)"                                                   -> "var(--undefined)",
        "var(--undefined-with-fallback, 20px)"                               -> "20px",
        "calc(var(--base-font-size) * 2)"                                    -> "calc(13px * 2)",
        "calc(var(--undefined) * 2)"                                         -> "calc(var(--undefined) * 2)",
        "calc(var(--undefined-with-fallback, 10px) * 2)"                     -> "calc(10px * 2)",
        "calc(var(--base-font-size) + var(--undefined-with-fallback, 40px))" -> "calc(13px + 40px)",
      )

      for ((param, result) <- expected) {
        assert(CSSParsing.injectVariablesIntoDeclaration(param, testVariableDefinitions, MediaQuery("print"), Nil) == result)
      }
    }

    it("must inject variables into a CSS stylesheet") {
      val variableDefinitions1 = CSSParsing.variableDefinitions(
        resource    = Style(cssWithVariableDefinitions1, List(MediaQuery("all"))),
        resolvedURL = _ => new URL("") // Won't be called
      )

      val cssWithVariableEvaluations =
        """|.orbeon {
           |  font-size: var(--orbeon-test2);
           |  font-family: var(--orbeon-test3);
           |}""".stripMargin

      val actualModifiedCss = CSSParsing.injectVariablesIntoCss(
        cascadingStyleSheet = CSSParsing.parsedCss(cssWithVariableEvaluations).get,
        variableDefinitions = variableDefinitions1,
        mediaQuery          = MediaQuery("print")
      )

      val expectedModifiedCss =
        """|/*
           | * THIS FILE IS GENERATED - DO NOT EDIT
           | */
           |@charset "UTF-8";
           |.orbeon {
           |  font-size:test2;
           |  font-family:test3-print;
           |}
           |""".stripMargin

      // TODO: ignore comments, whitespace, etc.

      assert(actualModifiedCss == expectedModifiedCss)
    }

    trait ValueProvider {
      def variableValue(variableName: String): Option[String]
    }

    def withDefinitions(cssWithVariableDefinitions: String,
                        mediaQuery                : MediaQuery)(
                        f                         : ValueProvider => Unit
    ): Unit = {
      val variableDefinitions = CSSParsing.variableDefinitions(
        resource    = Style(cssWithVariableDefinitions, List(MediaQuery("all"))),
        resolvedURL = _ => new URL("") // Won't be called
      )

      val variableValues = new ValueProvider {
        def variableValue(variableName: String): Option[String] =
          variableDefinitions.variableValue(
            variableName = variableName,
            mediaQuery   = mediaQuery,
            selectors    = List(Selector(".orbeon"))
          )
      }

      f(variableValues)
    }

    it("must respect simple media queries when retrieving variable values") {

      import MediaQuery.{ScreenMediaQuery, PrintMediaQuery}

      val vars1 = """.orbeon {
                    |  --orbeon1: orbeon1-1st;
                    |  --orbeon2: orbeon2;
                    |}""".stripMargin

      val vars2 = """.orbeon {
                    |  --orbeon1: orbeon1-2nd;
                    |  --orbeon3: orbeon3;
                    |}""".stripMargin

      val expectedValues = List(
        List(
          s"$vars1 $vars2",
          s"@media all { $vars1 } $vars2"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some)
        ),
        List(
          s"$vars1 @media all { $vars2 }",
          s"@media all { $vars1 } @media all { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some)
        ),
        List(
          s"$vars1 @media screen { $vars2 }",
          s"@media all { $vars1 } @media screen { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-1st".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> None)
        ),
        List(
          s"$vars1 @media print { $vars2 }",
          s"@media all { $vars1 } @media print { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-1st".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> None),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some)
        ),
        List(
          s"@media screen { $vars1 } $vars2",
          s"@media screen { $vars1 } @media all { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> None          , "--orbeon3" -> "orbeon3".some)
        ),
        List(
          s"@media screen { $vars1 } @media screen { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some),
          PrintMediaQuery  -> List("--orbeon1" -> None              , "--orbeon2" -> None          , "--orbeon3" -> None)
        ),
        List(
          s"@media screen { $vars1 } @media print { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-1st".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> None),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> None          , "--orbeon3" -> "orbeon3".some)
        ),
        List(
          s"@media print { $vars1 } $vars2",
          s"@media print { $vars1 } @media all { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> None          , "--orbeon3" -> "orbeon3".some),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some)
        ),
        List(
          s"@media print { $vars1 } @media screen { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> None          , "--orbeon3" -> "orbeon3".some),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-1st".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> None)
        ),
        List(
          s"@media print { $vars1 } @media print { $vars2 }"
        ) -> List(
          ScreenMediaQuery -> List("--orbeon1" -> None              , "--orbeon2" -> None          , "--orbeon3" -> None),
          PrintMediaQuery  -> List("--orbeon1" -> "orbeon1-2nd".some, "--orbeon2" -> "orbeon2".some, "--orbeon3" -> "orbeon3".some)
        ),
      )

      for  {
        (cssDefinitions, mediaQueriesAndVariableValues) <- expectedValues
        cssDefinition                                   <- cssDefinitions
        (mediaQuery, variableValues)                    <- mediaQueriesAndVariableValues
      } {
        withDefinitions(cssDefinition, mediaQuery) { valueProvider =>
          for ((variableName, variableValueOpt) <- variableValues) {
            assert(valueProvider.variableValue(variableName) == variableValueOpt)
          }
        }
      }
    }
  }
}
