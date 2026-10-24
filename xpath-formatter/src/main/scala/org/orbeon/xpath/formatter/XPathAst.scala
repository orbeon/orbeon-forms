package org.orbeon.xpath.formatter

sealed trait XPathNode

// Expressions
sealed trait Expr extends XPathNode

object XPathAst {

  // Primary Expressions
  case class StringLiteral(value: String, raw: String, delimiter: Char) extends Expr
  case class IntegerLiteral(raw: String) extends Expr
  case class DecimalLiteral(raw: String) extends Expr
  case class DoubleLiteral(raw: String) extends Expr
  case class VarRef(name: String) extends Expr
  case object ContextItem extends Expr
  case class SequenceExpr(items: List[Expr]) extends Expr // (expr, expr, ...) or ()

  // Function calls and arguments
  case class FunctionCall(name: String, args: List[Expr]) extends Expr
  case class ArgumentPlaceholder() extends Expr // ? in function call like f(?, 2)

  // Arrow expression: expr => f(arg2, arg3) => g()
  case class ArrowExpr(target: Expr, steps: List[ArrowStep]) extends Expr
  case class ArrowStep(functionName: String, args: List[Expr])

  // Simple map operator: expr ! expr
  case class SimpleMapExpr(steps: List[Expr]) extends Expr

  // Filter Expression: primary[pred1][pred2]
  case class FilterExpr(primary: Expr, predicates: List[Expr]) extends Expr

  // Path Expressions
  case class PathExpr(isAbsolute: Boolean, steps: List[PathStep]) extends Expr
  case class PathStep(separator: String, step: Step) extends XPathNode

  sealed trait Step extends XPathNode
  case class AxisStep(axis: Option[String], nodeTest: String, predicates: List[Expr]) extends Step
  case class PostfixStep(expr: Expr) extends Step
  case object ParentStep extends Step // ..
  case object ContextStep extends Step // .

  // Unary and Binary Expressions
  case class UnaryExpr(op: String, expr: Expr) extends Expr
  case class BinaryExpr(lhs: Expr, op: String, rhs: Expr) extends Expr

  // FLWOR Expressions
  case class FlworExpr(clauses: List[FlworClause], returnExpr: Expr) extends Expr

  sealed trait FlworClause extends XPathNode
  case class ForClause(bindings: List[ForBinding]) extends FlworClause
  case class ForBinding(varName: String, expr: Expr)

  case class LetClause(bindings: List[LetBinding]) extends FlworClause
  case class LetBinding(varName: String, expr: Expr)

  case class WhereClause(condition: Expr) extends FlworClause
  case class OrderByClause(specs: List[OrderSpec]) extends FlworClause
  case class OrderSpec(expr: Expr, ascending: Boolean, emptyGreatest: Option[Boolean], collation: Option[String])

  // Conditionals: if (...) then ... else ...
  case class IfExpr(condition: Expr, thenExpr: Expr, elseExpr: Expr) extends Expr

  // Quantified expressions: some/every $x in ... satisfies ...
  case class QuantifiedExpr(isEvery: Boolean, bindings: List[ForBinding], satisfies: Expr) extends Expr

  // Maps and Arrays (XPath 3.1)
  case class MapConstructor(entries: List[MapEntry]) extends Expr
  case class MapEntry(key: Expr, value: Expr)

  case class ArrayConstructor(items: List[Expr], isSquare: Boolean) extends Expr

  // Postfix and Unary Lookups
  case class PostfixLookup(target: Expr, key: KeySpecifier) extends Expr
  case class UnaryLookup(key: KeySpecifier) extends Expr

  sealed trait KeySpecifier extends XPathNode
  case class NameKey(name: String) extends KeySpecifier
  case class IntegerKey(raw: String) extends KeySpecifier
  case object WildcardKey extends KeySpecifier
  case class ParenthesizedKey(expr: Expr) extends KeySpecifier

  // Inline Function
  case class InlineFunction(params: List[FunctionParam], returnType: Option[String], body: Expr) extends Expr
  case class FunctionParam(name: String, typeDeclaration: Option[String])
}
