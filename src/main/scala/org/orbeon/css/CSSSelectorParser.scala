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

import org.orbeon.dom.QName
import org.orbeon.xml.NamespaceMapping
import org.parboiled2.*

import scala.util.{Failure, Success}


// CSS 3 selector parser based on the grammar specified at: http://www.w3.org/TR/css3-selectors/
//
// Bugs and RFEs:
//
// - `:nth-child(10n+-1)` should be invalid
// - `not` should not be accepted as the name of a functional pseudo-class
// - tests escapes in identifiers and strings
// - tests newlines in identifiers and strings
object CSSSelectorParser {

  // AST
  sealed trait SelectorNode

  case class Selector(head: ElementWithFiltersSelector, tail: Seq[(Combinator, ElementWithFiltersSelector)]) extends SelectorNode

  object Selector {
    def applySeq(head: ElementWithFiltersSelector, tail: Seq[(Combinator, ElementWithFiltersSelector)]): Selector =
      Selector(head, tail.toList)
  }

  trait Combinator extends SelectorNode
  object Combinator {
    case object ImmediatelyFollowing extends Combinator
    case object Child                extends Combinator
    case object Following            extends Combinator
    case object Descendant           extends Combinator

    def apply(s: String): Combinator = s match {
      case "+" => ImmediatelyFollowing
      case ">" => Child
      case "~" => Following
      case _   => Descendant
    }
  }

  trait NsType
  object NsType {
    case class  Specific(prefix: String) extends NsType // `ns|E`
    case object Any                      extends NsType // `*|E`
    case object None                     extends NsType // `|E`
    case object Default                  extends NsType // `E`
  }

  trait SimpleElementSelector extends SelectorNode

  case class TypeSelector(prefix: NsType, localName: String) extends SimpleElementSelector {
    def toQName(ns: NamespaceMapping): QName =
      prefix match {
        case NsType.Specific(p) => QName(localName, p, ns.mapping(p))
        case NsType.Any         => throw new IllegalArgumentException("Cannot create a QName for a universal selector")
        case NsType.None        => QName(localName)
        case NsType.Default     => QName(localName) // assume we live in "none" as the default namespace
      }
  }

  case class UniversalSelector(prefix: NsType) extends SimpleElementSelector

  object TypeSelector {
    def applyOpt(prefix: Option[Option[String]], localName: String): TypeSelector =
      TypeSelector(
        prefix match {
          case None            => NsType.Default
          case Some(None)      => NsType.None
          case Some(Some("*")) => NsType.Any
          case Some(Some(p))   => NsType.Specific(p)
        },
        localName
      )
  }

  object UniversalSelector {
    def applyOpt(prefix: Option[Option[String]]): UniversalSelector =
      UniversalSelector(
        prefix match {
          case None            => NsType.Default
          case Some(None)      => NsType.None
          case Some(Some("*")) => NsType.Any
          case Some(Some(p))   => NsType.Specific(p)
        }
      )
  }

  sealed trait AttributePredicate
  object AttributePredicate {

    case object Exist                   extends AttributePredicate
    case class  Equal   (value: String) extends AttributePredicate
    case class  Token   (value: String) extends AttributePredicate
    case class  Lang    (value: String) extends AttributePredicate
    case class  Start   (value: String) extends AttributePredicate
    case class  End     (value: String) extends AttributePredicate
    case class  Contains(value: String) extends AttributePredicate

    def apply(op: String, value: String): AttributePredicate =
      op match {
        case "="   => Equal   (value)
        case "~="  => Token   (value)
        case "|="  => Lang    (value)
        case "^="  => Start   (value)
        case "$="  => End     (value)
        case "*="  => Contains(value)
        case other => throw new IllegalArgumentException(other)
      }
  }

  trait Filter extends SelectorNode

  object Filter {
    case class Id(id: String)                                               extends Filter
    case class Class(className: String)                                     extends Filter
    case class Attribute(name: TypeSelector, predicate: AttributePredicate) extends Filter
    case class Negation(selector: SelectorNode)                             extends Filter
  }

  trait Expr extends SelectorNode

  object Expr {
    case object Plus                                  extends Expr
    case object Minus                                 extends Expr
    case class  Dimension(num: String, ident: String) extends Expr
    case class  Num(s: String)                        extends Expr
    case class  Str(s: String)                        extends Expr
    case class  Ident(s: String)                      extends Expr
  }

  trait PseudoClassFilter extends Filter
  case class SimplePseudoClassFilter(classname: String) extends PseudoClassFilter
  case class FunctionalPseudoClassFilter(function: String, exprs: List[Expr]) extends PseudoClassFilter
  object FunctionalPseudoClassFilter {
    def applySeq(function: String, exprs: Seq[Expr]): FunctionalPseudoClassFilter =
      FunctionalPseudoClassFilter(function, exprs.toList)
  }

  case class ElementWithFiltersSelector(element: Option[SimpleElementSelector], filters: List[Filter]) extends SelectorNode

  object ElementWithFiltersSelector {
    def applyFilters(filters: Seq[Filter]): ElementWithFiltersSelector = ElementWithFiltersSelector(None, filters.toList)
    def applySelectorAndFilters(selector: SimpleElementSelector, filters: Seq[Filter]): ElementWithFiltersSelector = ElementWithFiltersSelector(Some(selector), filters.toList)
  }

  def parseSelectors(process: String): List[Selector] =
     new CSSSelectorParser(process).selectorsGroup.run() match {
      case Success(astRoot) => astRoot.toList
      case Failure(t)       => throw t
    }
}

class CSSSelectorParser(val input: ParserInput)  extends Parser {

  import CSSSelectorParser.*

  // Rules (for naming rules, see http://users.parboiled.org/Scala-performance-td4024217.html)
  def selectorsGroup: Rule1[Seq[Selector]] = rule {
    OptWhiteSpace ~ zeroOrMore(selector).separatedBy(OptWhiteSpace ~ "," ~ OptWhiteSpace) ~ EOI
  }

  def selector: Rule1[Selector] = rule {
    (simpleSelectorSequence ~ zeroOrMore(selectorTuple)) ~> Selector.applySeq
  }

  def selectorTuple: Rule1[(Combinator, ElementWithFiltersSelector)] = rule {
    combinator ~ simpleSelectorSequence ~> Tuple2[Combinator, ElementWithFiltersSelector].apply
  }

  def combinator: Rule1[Combinator] = rule {
    (OptWhiteSpace ~ capture(anyOf("+>~")) ~> Combinator.apply | capture(WhiteSpace) ~> Combinator.apply) ~ OptWhiteSpace
  }

  def simpleSelectorSequence: Rule1[ElementWithFiltersSelector] = rule {
    ((typeSelector | universal) ~ zeroOrMore(filters) ~> ElementWithFiltersSelector.applySelectorAndFilters _) |
      (oneOrMore(filters) ~> ElementWithFiltersSelector.applyFilters)
  }

  def filters: Rule1[Filter] = rule {
    hash | className | attribute | negation | pseudo
  }

  def typeSelector: Rule1[TypeSelector] = rule {
    optional(namespacePrefix) ~ elementName ~> TypeSelector.applyOpt
  }

  def typeSelectorNoNs: Rule1[TypeSelector] = rule {
    push[Option[Option[String]]](None) ~ elementName ~> TypeSelector.applyOpt
  }

  // By spec, `|E` means "element with name E without a namespace".
  // `E` can mean "element with name E in the default namespace" or "element with name E in any namespace", for
  // elements.
  // Here we don't make a distinction between `|E` and `E` for now. We don't handle default namespaces for elements or
  // attributes in our selectors, so this is not a problem at the moment.
  def namespacePrefix: Rule1[Option[String]] = rule {
    optional(ident | capture("*")) ~ "|"
  }

  def elementName: Rule1[String] = ident

  def universal: Rule1[UniversalSelector] = rule {
    optional(namespacePrefix) ~> UniversalSelector.applyOpt ~ "*"
  }

  def className: Rule1[Filter.Class] = rule { "." ~ (ident ~> Filter.Class.apply) }

  def attribute: Rule1[Filter.Attribute] = rule {
    "[" ~
      OptWhiteSpace ~
      (typeSelector | typeSelectorNoNs) ~ // so that `E[ns|foo|="en"]` is parsed correctly
      OptWhiteSpace ~
      (
        (
          capture("^=" | "$=" | "*=" | "=" | "~=" | "|=") ~
          OptWhiteSpace ~
          (string | ident) ~
          OptWhiteSpace ~> AttributePredicate.apply
        ) |
          push(AttributePredicate.Exist)
      ) ~> Filter.Attribute.apply ~
    "]"
  }

  def pseudo: Rule1[PseudoClassFilter] = rule { ":" ~ optional(":") ~ (functionalPseudo | ident ~> SimplePseudoClassFilter.apply) }

  // FIXME: ident must not be "not" as that's handled by the negation
  def functionalPseudo: Rule1[FunctionalPseudoClassFilter] = rule {
    ident ~ "(" ~ OptWhiteSpace ~ expression ~ ")" ~> (FunctionalPseudoClassFilter.applySeq _)
  }

  def expression: Rule1[Seq[Expr]] = rule {
    oneOrMore((plus | minus | dimension | number ~> Expr.Num.apply | string ~> Expr.Str.apply | ident ~> Expr.Ident.apply) ~ OptWhiteSpace)
  }

  def negation: Rule1[Filter.Negation] = rule {
    ":not(" ~ OptWhiteSpace ~ negationArg ~> Filter.Negation.apply ~ OptWhiteSpace ~ ")"
  }

  def negationArg: Rule1[SelectorNode] = rule {
    typeSelector | universal | hash | className | attribute | pseudo
  }

  def hash     : Rule1[Filter.Id] = rule { "#" ~ (capture(oneOrMore(nmchar)) ~> Filter.Id.apply) }

  def plus     : Rule1[Expr]   = rule { OptWhiteSpace ~ "+" ~ push(Expr.Plus) }
  def minus    : Rule1[Expr]   = rule { "-" ~ push(Expr.Minus) }
  def dimension: Rule1[Expr]   = rule { number ~ ident ~> Expr.Dimension.apply }
  def number   : Rule1[String] = rule { capture(oneOrMore("0" - "9")) | capture(zeroOrMore("0" - "9") ~ "." ~ oneOrMore("0" - "9")) }

  def string   : Rule1[String] = rule { string1 | string2 }
  def string1  : Rule1[String] = rule { "\"" ~ capture(zeroOrMore(! anyOf("\"\n\r\f\\") ~ ANY | NewLine | nonascii | escape)) ~ "\"" }
  def string2  : Rule1[String] = rule { "'"  ~ capture(zeroOrMore(! anyOf("'\n\r\f\\")  ~ ANY | NewLine | nonascii | escape)) ~ "'"  }

  def ident        : Rule1[String] = rule { capture(optional("-") ~ nmstart ~ zeroOrMore(nmchar)) }

  def nmstart      : Rule0 = rule { "_" | "a" - "z" | "A" - "Z" | nonascii | escape }
  def nmchar       : Rule0 = rule { ("_" | "a" - "z" | "A" - "Z" | "0" - "9" | "-") | nonascii | escape }

  def nonascii     : Rule0 = rule { ! ("\u0000" - "\u007f") ~ ANY }

  def escape       : Rule0 = rule { unicode | "\\" ~ ! ("\n" | "\r" | "\f" | "0" - "9" | "a" - "f") ~ ANY }
  def unicode      : Rule0 = rule { "\\" ~ oneOrMore("0" - "9" | "a" - "f") ~ optional("\r\n" | WhiteSpace) }

  def NewLine      : Rule0 = rule { "\n" | "\r\n" | "\r" | "\f" }
  def WhiteSpace   : Rule0 = rule { oneOrMore(anyOf(" \n\r\t\f")) }
  def OptWhiteSpace: Rule0 = rule { zeroOrMore(anyOf(" \n\r\t\f")) }
}