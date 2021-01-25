package org.orbeon.oxf.fr.process

import org.orbeon.oxf.util.MarkupUtils._
import org.parboiled2._

import scala.util.{Failure, Success}


class ProcessParser(val input: ParserInput) extends Parser {

  import ProcessParser._

  // Root rule
  def Process: Rule1[GroupNode] = rule {
    OptWhiteSpace ~ GroupContent ~ OptWhiteSpace ~ EOI
  }

  def Expr: Rule1[ExprNode] = rule {
    Condition | Action | ParenthesizedGroup
  }

  def ParenthesizedGroup: Rule1[GroupNode] = rule {
    "(" ~ OptWhiteSpace ~ GroupContent ~ OptWhiteSpace ~ ")"
  }

  def GroupContent: Rule1[GroupNode] = rule {
    Expr ~ zeroOrMore(CombinatorActionPair) ~> ((e: ExprNode, p: Seq[(Combinator, ExprNode)]) => GroupNode(e, p))
  }

  def CombinatorActionPair: Rule1[(Combinator, ExprNode)] = rule {
    WhiteSpace ~ Combinator ~ WhiteSpace ~ Expr ~> ((_: Combinator) -> (_: ExprNode))
  }

  def Action: Rule1[ActionNode] = rule {
    Name ~ optional("(" ~ OptWhiteSpace ~ zeroOrMore(Param).separatedBy(ParamSeparator) ~ OptWhiteSpace ~ ")") ~>
      ((name: String, params: Option[Seq[(Option[String], String)]]) => ActionNode(name, params.getOrElse(Nil).toMap))
  }

  def Condition: Rule1[ConditionNode] = rule {
    "if" ~ OptWhiteSpace ~ "(" ~ OptWhiteSpace ~ XPath  ~ OptWhiteSpace ~ ")" ~ ThenBranch ~ optional(ElseBranch) ~> ConditionNode
  }

  def ThenBranch = rule { WhiteSpace ~ "then" ~ WhiteSpace ~ Expr }
  def ElseBranch = rule { WhiteSpace ~ "else" ~ WhiteSpace ~ Expr }

  def XPath = ValueString

  def Name: Rule1[String] = rule { capture(NameStart ~ zeroOrMore(NameAfter)) }
  def NameStart: Rule0    = rule { "a" - "z" | "A" - "Z" | "-" | "_" }
  def NameAfter: Rule0    = rule { NameStart | "0" - "9" | ":" }

  def Param: Rule1[(Option[String], String)] = rule {
    optional(OptWhiteSpace ~ Name ~ OptWhiteSpace ~ "=") ~ OptWhiteSpace ~ ValueString ~> ((_: Option[String]) -> (_: String))
  }

  def ParamSeparator = rule { OptWhiteSpace ~ "," ~ OptWhiteSpace }

  def ValueString =  rule { ValueString1 | ValueString2 }

  def ValueString1: Rule1[String] = rule {
    "\"" ~ capture(zeroOrMore(Character | "'")) ~ "\"" ~> ((s: String) => s)
  }

  def ValueString2: Rule1[String] = rule {
    "'" ~ capture(zeroOrMore(Character | "\"")) ~ "'" ~> ((s: String) => s)
  }

  def Combinator: Rule1[Combinator] = rule {
    capture(ThenCombinator.name | RecoverCombinator.name) ~> CombinatorsByName
  }

  def Character     = rule { EscapedChar | NormalChar }
  def EscapedChar   = rule { "\\" ~ (anyOf("\"\\/bfnrt") | Unicode) }
  def NormalChar    = rule { ! anyOf("\"'\\") ~ ANY }
  def Unicode       = rule { "u" ~ HexDigit ~ HexDigit ~ HexDigit ~ HexDigit }
  def HexDigit      = rule { "0" - "9" | "a" - "f" | "A" - "Z" }

  def WhiteSpace    = rule { oneOrMore(anyOf(" \n\r\t\f")) }
  def OptWhiteSpace = rule { zeroOrMore(anyOf(" \n\r\t\f")) }
}

object ProcessParser {

  // Combinators
  sealed abstract class Combinator(val name: String)
  case object ThenCombinator    extends Combinator("then")
  case object RecoverCombinator extends Combinator("recover")

  val CombinatorsByName: Map[String, Combinator] =
    Seq(ThenCombinator, RecoverCombinator) map (c => c.name -> c) toMap

  private def quote(s: String) =
    "\"" + escapeJava(s) + "\""

  // XXX TODO
  def escapeJava(s: String): String =
    s.escapeJavaScript

  // AST
  sealed trait ExprNode { def serialize: String }

  case class ActionNode(name: String, params: Map[Option[String], String]) extends ExprNode {

    private def serializeParams =
      if (params.isEmpty) "" else params map {
        case (Some(name), value) => name + " = " + quote(value)
        case (None, value)       => quote(value)
      } mkString ("(", ", ", ")")

    def serialize: String = name + serializeParams
  }

  case class GroupNode(expr: ExprNode, rest: List[(Combinator, ExprNode)]) extends ExprNode {

    private def serializeRest =
      if (rest.isEmpty) "" else " " + (rest flatMap { case (combinator, expr) => List(combinator.name, expr.serialize) } mkString " ")

    def serialize: String = "(" + expr.serialize + serializeRest + ")"
  }

  object GroupNode {
    def apply(expr: ExprNode, rest: Seq[(Combinator, ExprNode)]): GroupNode =
      GroupNode(expr, rest.toList)
  }

  case class ConditionNode(xpath: String, thenBranch: ExprNode, elseBranch: Option[ExprNode]) extends ExprNode {

    private def serializeElse =
      elseBranch map (" else " + _.serialize) getOrElse ""

    def serialize: String = "if (" + quote(xpath) + ") then " + thenBranch.serialize + serializeElse
  }

  def parse(process: String): GroupNode = {
    val parsingResult = new ProcessParser(process).Process.run()
    parsingResult match {
      case Success(astRoot) => astRoot
      case Failure(t)       => throw t
    }
  }
}