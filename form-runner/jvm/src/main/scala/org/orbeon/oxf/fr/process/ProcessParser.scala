package org.orbeon.oxf.fr.process

import org.apache.commons.lang3.StringEscapeUtils
import org.parboiled.errors.{ErrorUtils, ParsingException}
import org.parboiled.scala._

object ProcessParser extends Parser {

  // Combinators
  sealed abstract class Combinator(val name: String)
  case object ThenCombinator    extends Combinator("then")
  case object RecoverCombinator extends Combinator("recover")

  val CombinatorsByName = Seq(ThenCombinator, RecoverCombinator) map (c => c.name -> c) toMap

  private def quote(s: String) =
    "\"" + StringEscapeUtils.escapeJava(s) + "\""

  // AST
  sealed trait ExprNode { def serialize: String }

  case class ActionNode(name: String, params: Map[Option[String], String]) extends ExprNode {

    private def serializeParams =
      if (params.isEmpty) "" else params map {
        case (Some(name), value) => name + " = " + quote(value)
        case (None, value)       => quote(value)
      } mkString ("(", ", ", ")")

    def serialize = name + serializeParams
  }

  case class GroupNode(expr: ExprNode, rest: List[(Combinator, ExprNode)]) extends ExprNode {

    private def serializeRest =
      if (rest.isEmpty) "" else " " + (rest flatMap { case (combinator, expr) => List(combinator.name, expr.serialize) } mkString " ")

    def serialize = "(" + expr.serialize + serializeRest + ")"
  }

  case class ConditionNode(xpath: String, thenBranch: ExprNode, elseBranch: Option[ExprNode]) extends ExprNode {

    private def serializeElse =
      elseBranch map (" else " + _.serialize) getOrElse ""

    def serialize = "if (" + quote(xpath) + ") then " + thenBranch.serialize + serializeElse
  }

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
    Expr ~ zeroOrMore(CombinatorActionPair) ~~> GroupNode
  }

  def CombinatorActionPair: Rule1[(Combinator, ExprNode)] = rule {
    WhiteSpace ~ Combinator ~ WhiteSpace ~ Expr ~~> (_ -> _)
  }

  def Action: Rule1[ActionNode] = rule {
    Name ~ optional("(" ~ OptWhiteSpace ~ zeroOrMore(Param, ParamSeparator) ~ OptWhiteSpace ~ ")") ~~>
      ((name, params) => ActionNode(name, params.getOrElse(Nil).toMap))
  }

  def Condition: Rule1[ConditionNode] = rule {
    "if" ~ OptWhiteSpace ~ "(" ~ OptWhiteSpace ~ XPath  ~ OptWhiteSpace ~ ")" ~ ThenBranch ~ optional(ElseBranch) ~~> ConditionNode
  }

  def ThenBranch = rule { WhiteSpace ~ "then" ~ WhiteSpace ~ Expr }
  def ElseBranch = rule { WhiteSpace ~ "else" ~ WhiteSpace ~ Expr }

  def XPath = ValueString

  def Name: Rule1[String] = rule { group(NameStart ~ zeroOrMore(NameAfter)) ~> identity }
  def NameStart: Rule0    = rule { "a" - "z" | "A" - "Z" | "-" | "_" }
  def NameAfter: Rule0    = rule { NameStart | "0" - "9" | ":" }

  def Param: Rule1[(Option[String], String)] = rule {
    optional(OptWhiteSpace ~ Name ~ OptWhiteSpace ~ "=") ~ OptWhiteSpace ~ ValueString ~~> (_ -> _)
  }

  def ParamSeparator = OptWhiteSpace ~ "," ~ OptWhiteSpace

  def ValueString = ValueString1 | ValueString2

  def ValueString1: Rule1[String] = rule {
    "\"" ~ zeroOrMore(Character | "'") ~> identity ~ "\""
  }

  def ValueString2: Rule1[String] = rule {
    "'" ~ zeroOrMore(Character | "\"") ~> identity ~ "'"
  }

  def Combinator: Rule1[Combinator] = rule {
    (CombinatorsByName.keys map toRule reduceLeft (_ | _)) ~> CombinatorsByName
  }

  def Character     = rule { EscapedChar | NormalChar }
  def EscapedChar   = rule { "\\" ~ (anyOf("\"\\/bfnrt") | Unicode) }
  def NormalChar    = rule { ! anyOf("\"'\\") ~ ANY }
  def Unicode       = rule { "u" ~ HexDigit ~ HexDigit ~ HexDigit ~ HexDigit }
  def HexDigit      = rule { "0" - "9" | "a" - "f" | "A" - "Z" }

  def WhiteSpace    = rule { oneOrMore(anyOf(" \n\r\t\f")) }
  def OptWhiteSpace = rule { zeroOrMore(anyOf(" \n\r\t\f")) }

  def parse(process: String): GroupNode = {
    val parsingResult = ReportingParseRunner(Process).run(process)
    parsingResult.result match {
      case Some(astRoot) => astRoot
      case None          => throw new ParsingException("Invalid source:\n" + ErrorUtils.printParseErrors(parsingResult))
    }
  }
}