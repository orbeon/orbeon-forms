package org.orbeon.oxf.fr.process

// XXX FIXME stub parser for Scala.js
object ProcessParser  {

  // Combinators
  sealed abstract class Combinator(val name: String)
  case object ThenCombinator    extends Combinator("then")
  case object RecoverCombinator extends Combinator("recover")

  val CombinatorsByName = Seq(ThenCombinator, RecoverCombinator) map (c => c.name -> c) toMap

  // AST
  sealed trait ExprNode { def serialize: String }

  case class ActionNode(name: String, params: Map[Option[String], String]) extends ExprNode {

    def serialize = "TODO"
  }

  case class GroupNode(expr: ExprNode, rest: List[(Combinator, ExprNode)]) extends ExprNode {

    private def serializeRest =
      if (rest.isEmpty) "" else " " + (rest flatMap { case (combinator, expr) => List(combinator.name, expr.serialize) } mkString " ")

    def serialize = "TODO"
  }

  case class ConditionNode(xpath: String, thenBranch: ExprNode, elseBranch: Option[ExprNode]) extends ExprNode {

    private def serializeElse =
      elseBranch map (" else " + _.serialize) getOrElse ""

    def serialize = "TODO"
  }

  def parse(process: String): GroupNode = {
    // XXX hardcoded save
    GroupNode(ActionNode("save", Map.empty), Nil)
  }
}