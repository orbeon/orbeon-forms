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

import org.parboiled.errors.{ErrorUtils, ParsingException}
import org.parboiled.scala._

// CSS 3 selector parser based on the grammar specified at: http://www.w3.org/TR/css3-selectors/
//
// Bugs and RFEs:
//
// - fix parsing of `a[foo|bar|="en"]`, which currently fails
// - `:nth-child(10n+-1)` should be invalid
// - `not` should not be accepted as the name of a functional pseudo-class
// - better representation of namespaced names
// - tests escapes in identifiers and strings
// - tests newlines in identifiers and strings
object CSSSelectorParser extends Parser {

  // AST
  sealed trait SelectorNode
  case class Selector(head: ElementWithFiltersSelector, tail: List[(Combinator, ElementWithFiltersSelector)]) extends SelectorNode

  trait Combinator extends SelectorNode
  case object ImmediatelyFollowingCombinator extends Combinator
  case object ChildCombinator                extends Combinator
  case object FollowingCombinator            extends Combinator
  case object DescendantCombinator           extends Combinator

  trait SimpleElementSelector extends SelectorNode
  case class TypeSelector(prefix: Option[Option[String]], name: String) extends SimpleElementSelector
  case class UniversalSelector(prefix: Option[Option[String]]) extends SimpleElementSelector

  // TODO: Attribute existence.
  case class AttributePredicate(op: String, value: String)

  trait Filter extends SelectorNode
  case class IdFilter(id: String) extends Filter
  case class ClassFilter(className: String) extends Filter
  case class AttributeFilter(prefix: Option[Option[String]], name: String, predicate: Option[AttributePredicate]) extends Filter
  case class NegationFilter(selector: SelectorNode) extends Filter

  trait Expr extends SelectorNode
  case object PlusExpr extends Expr
  case object MinusExpr extends Expr
  case class DimensionExpr(num: String, ident: String) extends Expr
  case class NumberExpr(s: String) extends Expr
  case class StringExpr(s: String) extends Expr
  case class IdentExpr(s: String) extends Expr

  trait PseudoClassFilter extends Filter
  case class SimplePseudoClassFilter(classname: String) extends PseudoClassFilter
  case class FunctionalPseudoClassFilter(function: String, exprs: List[Expr]) extends PseudoClassFilter

  case class ElementWithFiltersSelector(element: Option[SimpleElementSelector], filters: List[Filter]) extends SelectorNode

  object Combinator {
    def apply(s: String) = s match {
      case "+" => ImmediatelyFollowingCombinator
      case ">" => ChildCombinator
      case "~" => FollowingCombinator
      case _   => DescendantCombinator
    }
  }

  object ElementWithFiltersSelector {
    def apply1(filters: List[Filter]): ElementWithFiltersSelector = ElementWithFiltersSelector(None, filters)
    def apply2(selector: SimpleElementSelector, filters: List[Filter]): ElementWithFiltersSelector = ElementWithFiltersSelector(Some(selector), filters)
  }

  // Rules (for naming rules, see http://users.parboiled.org/Scala-performance-td4024217.html)
  def selectorsGroup: Rule1[List[Selector]] = rule {
    OptWhiteSpace ~ zeroOrMore(selector, OptWhiteSpace ~ "," ~ OptWhiteSpace) ~ EOI
  }

  def selector: Rule1[Selector] = rule {
    simpleSelectorSequence ~ zeroOrMore(combinator ~ simpleSelectorSequence) ~~> Selector
  }

  def combinator: Rule1[Combinator] = rule {
    (OptWhiteSpace ~ anyOf("+>~") ~> Combinator.apply | WhiteSpace ~> Combinator.apply) ~ OptWhiteSpace
  }

  def simpleSelectorSequence: Rule1[ElementWithFiltersSelector] = rule {
    ((typeSelector | universal) ~ zeroOrMore(filters) ~~> ElementWithFiltersSelector.apply2 _) | (oneOrMore(filters) ~~> ElementWithFiltersSelector.apply1 _)
  }

  def filters: Rule1[Filter] = rule {
    hash | className | attribute | negation | pseudo
  }

  def typeSelector: Rule1[TypeSelector] = rule {
    optional(namespacePrefix) ~ elementName ~~> TypeSelector
  }

  def namespacePrefix: Rule1[Option[String]] = rule {
    optional(ident | "*" ~> identity) ~ "|"
  }

  def elementName: Rule1[String] = ident

  def universal: Rule1[UniversalSelector] = rule {
    optional(namespacePrefix) ~~> UniversalSelector ~ "*"
  }

  def className = rule { "." ~ (ident ~~> ClassFilter) }

  def attribute: Rule1[AttributeFilter] = rule {
    "[" ~
      OptWhiteSpace ~
      push[Option[Option[String]]](None) ~ //FIXME: optional(namespacePrefix) ~
      ident ~
      OptWhiteSpace ~
      optional(
        ("^=" | "$=" | "*=" | "=" | "~=" | "|=") ~> identity ~
        OptWhiteSpace ~
        (string | ident) ~
        OptWhiteSpace ~~> AttributePredicate
      ) ~~> AttributeFilter ~
    "]"
  }

  def pseudo: Rule1[PseudoClassFilter] = rule { ":" ~ optional(":") ~ (functionalPseudo | ident ~~> SimplePseudoClassFilter) }

  // FIXME: ident must not be "not" as that's handled by the negation
  def functionalPseudo: Rule1[FunctionalPseudoClassFilter] = rule {
    ident ~ "(" ~ OptWhiteSpace ~ expression ~ ")" ~~> FunctionalPseudoClassFilter
  }

  def expression: Rule1[List[Expr]] = rule {
    oneOrMore((plus | minus | dimension | number ~~> NumberExpr | string ~~> StringExpr | ident ~~> IdentExpr) ~ OptWhiteSpace)
  }

  def negation: Rule1[NegationFilter] = rule {
    ":not(" ~ OptWhiteSpace ~ negationArg ~~> NegationFilter ~ OptWhiteSpace ~ ")"
  }

  def negationArg = rule {
    typeSelector | universal | hash | className | attribute | pseudo
  }

  def hash = rule { "#" ~ (oneOrMore(nmchar) ~> IdFilter) }
  def plus = rule { OptWhiteSpace ~ "+" ~ push(PlusExpr) }
  def minus = rule { "-" ~ push(MinusExpr) }
  def dimension = rule { number ~ ident ~~> DimensionExpr }
  def number = rule { oneOrMore("0" - "9") ~> identity | group(zeroOrMore("0" - "9") ~ "." ~ oneOrMore("0" - "9")) ~> identity }

  def string  = rule { string1 | string2 }
  def string1 = rule { "\"" ~ zeroOrMore(! anyOf("\"\n\r\f\\") ~ ANY | NewLine | nonascii | escape) ~> identity ~ "\"" }
  def string2 = rule { "'"  ~ zeroOrMore(! anyOf("'\n\r\f\\")  ~ ANY | NewLine | nonascii | escape) ~> identity ~ "'"  }

  def ident = rule { group(optional("-") ~ nmstart ~ zeroOrMore(nmchar)) ~> identity }
  def nmstart: Rule0 = rule {"_" | "a" - "z" | "A" - "Z" | nonascii | escape }
  def nmchar: Rule0 = rule { ("_" | "a" - "z" | "A" - "Z" | "0" - "9" | "-") | nonascii | escape }

  def nonascii: Rule0 = rule { ! ("\u0000" - "\u007f") ~ ANY }

  def escape: Rule0 = rule { unicode | "\\" ~ ! ("\n" | "\r" | "\f" | "0" - "9" | "a" - "f") ~ ANY }
  def unicode: Rule0 = rule { "\\" ~ oneOrMore("0" - "9" | "a" - "f") ~ optional("\r\n" | WhiteSpace) }

  def NewLine       = rule { "\n" | "\r\n" | "\r" | "\f" }
  def WhiteSpace    = rule { oneOrMore(anyOf(" \n\r\t\f")) }
  def OptWhiteSpace = rule { zeroOrMore(anyOf(" \n\r\t\f")) }

  // Instantiate parser only once as parser creation is costly
  val SelectorsGroupParser = selectorsGroup

  def parseSelectors(selectors: String): List[Selector] = {
    val parsingResult = ReportingParseRunner(SelectorsGroupParser).run(selectors)
    parsingResult.result match {
      case Some(list) => list
      case None       => throw new ParsingException("Invalid source:\n" + ErrorUtils.printParseErrors(parsingResult))
    }
  }
}