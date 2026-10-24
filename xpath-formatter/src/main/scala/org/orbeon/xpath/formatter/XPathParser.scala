package org.orbeon.xpath.formatter

import scala.collection.mutable.ListBuffer

class XPathParser(tokens: List[XPathToken]) {

  import XPathAst.*
  import XPathToken.{
    VarRef => TokVarRef,
    StringLiteral => TokStringLiteral,
    IntegerLiteral => TokIntegerLiteral,
    DecimalLiteral => TokDecimalLiteral,
    DoubleLiteral => TokDoubleLiteral,
    _
  }

  private var pos = 0

  private def current: XPathToken =
    if (pos < tokens.length) tokens(pos) else EOF

  private def lookAhead(offset: Int = 1): XPathToken =
    if (pos + offset < tokens.length) tokens(pos + offset) else EOF

  private def advance(): XPathToken = {
    val tok = current
    if (pos < tokens.length) pos += 1
    tok
  }

  private def consume(expected: XPathToken): Unit =
    if (current == expected) {
      advance()
    } else {
      throw new IllegalArgumentException(s"Expected token $expected but got $current at position $pos")
    }

  private def matchKeyword(kw: String): Boolean = current match {
    case Keyword(text) if text == kw => true
    case Name(text) if text == kw    => true
    case _                           => false
  }

  private def consumeKeyword(kw: String): Unit =
    if (matchKeyword(kw)) {
      advance()
    } else {
      throw new IllegalArgumentException(s"Expected keyword '$kw' but got $current at position $pos")
    }

  private val kindTestNames = Set(
    "node", "text", "comment", "processing-instruction",
    "element", "attribute", "schema-element", "schema-attribute",
    "document-node", "namespace-node"
  )

  def parse(): Expr = {
    val expr = parseExpr()
    expr
  }

  // Expr ::= ExprSingle ("," ExprSingle)*
  def parseExpr(): Expr = {
    val first = parseExprSingle()
    if (current == Comma) {
      val items = ListBuffer[Expr](first)
      while (current == Comma) {
        advance() // consume comma
        items += parseExprSingle()
      }
      SequenceExpr(items.toList)
    } else {
      first
    }
  }

  // ExprSingle ::= FLWORExpr | QuantifiedExpr | IfExpr | OrExpr
  def parseExprSingle(): Expr = {
    if (matchKeyword("for") || matchKeyword("let")) {
      parseFlwor()
    } else if (matchKeyword("some") || matchKeyword("every")) {
      parseQuantified()
    } else if (matchKeyword("if") && lookAhead() == LParen) {
      parseIf()
    } else {
      parseOr()
    }
  }

  // FLWORExpr
  private def parseFlwor(): Expr = {
    val clauses = ListBuffer[FlworClause]()
    var parsingClauses = true

    while (parsingClauses) {
      if (matchKeyword("for")) {
        advance()
        val bindings = ListBuffer[ForBinding]()
        var hasMore = true
        while (hasMore) {
          val varRef = current match {
            case TokVarRef(name) => advance(); name
            case other => throw new IllegalArgumentException(s"Expected variable reference in 'for' clause, got $other")
          }
          consumeKeyword("in")
          val expr = parseExprSingle()
          bindings += ForBinding(varRef, expr)
          if (current == Comma && lookAhead().isInstanceOf[TokVarRef]) {
            advance() // consume comma
          } else {
            hasMore = false
          }
        }
        clauses += ForClause(bindings.toList)
      } else if (matchKeyword("let")) {
        advance()
        val bindings = ListBuffer[LetBinding]()
        var hasMore = true
        while (hasMore) {
          val varRef = current match {
            case TokVarRef(name) => advance(); name
            case other => throw new IllegalArgumentException(s"Expected variable reference in 'let' clause, got $other")
          }
          current match {
            case Assign => advance()
            case Eq => advance() // accommodate '=' as well
            case other => throw new IllegalArgumentException(s"Expected ':=' in 'let' clause, got $other")
          }
          val expr = parseExprSingle()
          bindings += LetBinding(varRef, expr)
          if (current == Comma && lookAhead().isInstanceOf[TokVarRef]) {
            advance() // consume comma
          } else {
            hasMore = false
          }
        }
        clauses += LetClause(bindings.toList)
      } else if (matchKeyword("where")) {
        advance()
        val condition = parseExprSingle()
        clauses += WhereClause(condition)
      } else if (matchKeyword("order") && (lookAhead() match {
        case Keyword("by") | Name("by") => true
        case _                          => false
      })) {
        advance() // order
        advance() // by
        val specs = ListBuffer[OrderSpec]()
        var hasMore = true
        while (hasMore) {
          val expr = parseExprSingle()
          var ascending = true
          var emptyGreatest: Option[Boolean] = None
          var collation: Option[String] = None

          if (matchKeyword("ascending")) {
            advance()
            ascending = true
          } else if (matchKeyword("descending")) {
            advance()
            ascending = false
          }

          if (matchKeyword("empty")) {
            advance()
            if (matchKeyword("greatest")) {
              advance()
              emptyGreatest = Some(true)
            } else if (matchKeyword("least")) {
              advance()
              emptyGreatest = Some(false)
            }
          }

          if (matchKeyword("collation")) {
            advance()
            current match {
              case TokStringLiteral(_, value, _) =>
                collation = Some(value)
                advance()
              case other =>
                throw new IllegalArgumentException(s"Expected string literal for collation, got $other")
            }
          }

          specs += OrderSpec(expr, ascending, emptyGreatest, collation)
          if (current == Comma) {
            advance()
          } else {
            hasMore = false
          }
        }
        clauses += OrderByClause(specs.toList)
      } else {
        parsingClauses = false
      }
    }

    consumeKeyword("return")
    val returnExpr = parseExprSingle()
    FlworExpr(clauses.toList, returnExpr)
  }

  // QuantifiedExpr
  private def parseQuantified(): Expr = {
    val isEvery = matchKeyword("every")
    advance() // some or every
    val bindings = ListBuffer[ForBinding]()
    var hasMore = true
    while (hasMore) {
      val varRef = current match {
        case TokVarRef(name) => advance(); name
        case other => throw new IllegalArgumentException(s"Expected variable reference in quantified expr, got $other")
      }
      consumeKeyword("in")
      val expr = parseExprSingle()
      bindings += ForBinding(varRef, expr)
      if (current == Comma) {
        advance()
      } else {
        hasMore = false
      }
    }
    consumeKeyword("satisfies")
    val satisfies = parseExprSingle()
    QuantifiedExpr(isEvery, bindings.toList, satisfies)
  }

  // IfExpr
  private def parseIf(): Expr = {
    consumeKeyword("if")
    consume(LParen)
    val cond = parseExpr()
    consume(RParen)
    consumeKeyword("then")
    val thenExpr = parseExprSingle()
    consumeKeyword("else")
    val elseExpr = parseExprSingle()
    IfExpr(cond, thenExpr, elseExpr)
  }

  // OrExpr
  private def parseOr(): Expr = {
    var lhs = parseAnd()
    while (matchKeyword("or")) {
      advance()
      val rhs = parseAnd()
      lhs = BinaryExpr(lhs, "or", rhs)
    }
    lhs
  }

  // AndExpr
  private def parseAnd(): Expr = {
    var lhs = parseComparison()
    while (matchKeyword("and")) {
      advance()
      val rhs = parseComparison()
      lhs = BinaryExpr(lhs, "and", rhs)
    }
    lhs
  }

  // ComparisonExpr
  private def parseComparison(): Expr = {
    val lhs = parseStringConcat()
    val compOp = current match {
      case Eq => Some("=")
      case Ne => Some("!=")
      case Lt => Some("<")
      case Le => Some("<=")
      case Gt => Some(">")
      case Ge => Some(">=")
      case Precedes => Some("<<")
      case Follows => Some(">>")
      case Keyword(op) if Set("eq", "ne", "lt", "le", "gt", "ge", "is").contains(op) => Some(op)
      case Name(op) if Set("eq", "ne", "lt", "le", "gt", "ge", "is").contains(op) => Some(op)
      case _ => None
    }
    compOp match {
      case Some(op) =>
        advance()
        val rhs = parseStringConcat()
        BinaryExpr(lhs, op, rhs)
      case None =>
        lhs
    }
  }

  // StringConcatExpr: RangeExpr ("||" RangeExpr)*
  private def parseStringConcat(): Expr = {
    var lhs = parseRange()
    while (current == PipePipe) {
      advance()
      val rhs = parseRange()
      lhs = BinaryExpr(lhs, "||", rhs)
    }
    lhs
  }

  // RangeExpr: AdditiveExpr ("to" AdditiveExpr)?
  private def parseRange(): Expr = {
    val lhs = parseAdditive()
    if (matchKeyword("to")) {
      advance()
      val rhs = parseAdditive()
      BinaryExpr(lhs, "to", rhs)
    } else {
      lhs
    }
  }

  // AdditiveExpr: MultiplicativeExpr (("+" | "-") MultiplicativeExpr)*
  private def parseAdditive(): Expr = {
    var lhs = parseMultiplicative()
    var continue = true
    while (continue) {
      current match {
        case Plus =>
          advance()
          lhs = BinaryExpr(lhs, "+", parseMultiplicative())
        case Minus =>
          advance()
          lhs = BinaryExpr(lhs, "-", parseMultiplicative())
        case _ =>
          continue = false
      }
    }
    lhs
  }

  // MultiplicativeExpr: UnionExpr (("*" | "div" | "idiv" | "mod") UnionExpr)*
  private def parseMultiplicative(): Expr = {
    var lhs = parseUnion()
    var continue = true
    while (continue) {
      current match {
        case Star =>
          advance()
          lhs = BinaryExpr(lhs, "*", parseUnion())
        case Keyword(op) if Set("div", "idiv", "mod").contains(op) =>
          advance()
          lhs = BinaryExpr(lhs, op, parseUnion())
        case Name(op) if Set("div", "idiv", "mod").contains(op) =>
          advance()
          lhs = BinaryExpr(lhs, op, parseUnion())
        case _ =>
          continue = false
      }
    }
    lhs
  }

  // UnionExpr: IntersectExceptExpr (("union" | "|") IntersectExceptExpr)*
  private def parseUnion(): Expr = {
    var lhs = parseIntersectExcept()
    var continue = true
    while (continue) {
      if (current == Pipe) {
        advance()
        lhs = BinaryExpr(lhs, "|", parseIntersectExcept())
      } else if (matchKeyword("union")) {
        advance()
        lhs = BinaryExpr(lhs, "union", parseIntersectExcept())
      } else {
        continue = false
      }
    }
    lhs
  }

  // IntersectExceptExpr: TypeExpr (("intersect" | "except") TypeExpr)*
  private def parseIntersectExcept(): Expr = {
    var lhs = parseTypeExpr()
    var continue = true
    while (continue) {
      if (matchKeyword("intersect")) {
        advance()
        lhs = BinaryExpr(lhs, "intersect", parseTypeExpr())
      } else if (matchKeyword("except")) {
        advance()
        lhs = BinaryExpr(lhs, "except", parseTypeExpr())
      } else {
        continue = false
      }
    }
    lhs
  }

  // TypeExpr: handles "instance of", "treat as", "castable as", "cast as"
  private def parseTypeExpr(): Expr = {
    val lhs = parseArrow()
    val typeOp = if (matchKeyword("instance") && (lookAhead() match {
      case Keyword("of") | Name("of") => true
      case _ => false
    })) {
      advance(); advance()
      Some("instance of")
    } else if (matchKeyword("treat") && (lookAhead() match {
      case Keyword("as") | Name("as") => true
      case _ => false
    })) {
      advance(); advance()
      Some("treat as")
    } else if (matchKeyword("castable") && (lookAhead() match {
      case Keyword("as") | Name("as") => true
      case _ => false
    })) {
      advance(); advance()
      Some("castable as")
    } else if (matchKeyword("cast") && (lookAhead() match {
      case Keyword("as") | Name("as") => true
      case _ => false
    })) {
      advance(); advance()
      Some("cast as")
    } else {
      None
    }

    typeOp match {
      case Some(op) =>
        val typeName = readTypeName()
        BinaryExpr(lhs, op, VarRef(typeName)) // VarRef or Name as simple node
      case None =>
        lhs
    }
  }

  private def readTypeName(): String = {
    val buf = new java.lang.StringBuilder()
    var continue = true
    while (continue && current != EOF && current != RParen && current != RBracket && current != RBrace && current != Comma) {
      current match {
        case Name(n) => buf.append(n); advance()
        case Keyword(k) => buf.append(k); advance()
        case Question => buf.append("?"); advance()
        case Plus => buf.append("+"); advance()
        case Star => buf.append("*"); advance()
        case LParen => buf.append("("); advance()
        case RParen => buf.append(")"); advance()
        case _ => continue = false
      }
    }
    buf.toString
  }

  // ArrowExpr: UnaryExpr ("=>" ArrowFunctionCall)*
  private def parseArrow(): Expr = {
    val target = parseUnary()
    if (current == Arrow) {
      val steps = ListBuffer[ArrowStep]()
      while (current == Arrow) {
        advance() // consume =>
        val funcName = current match {
          case Name(n) => advance(); n
          case Keyword(k) => advance(); k
          case other => throw new IllegalArgumentException(s"Expected function name after '=>', got $other")
        }
        consume(LParen)
        val args = parseArgumentList()
        consume(RParen)
        steps += ArrowStep(funcName, args)
      }
      ArrowExpr(target, steps.toList)
    } else {
      target
    }
  }

  // UnaryExpr: ("-" | "+")* ValueExpr
  private def parseUnary(): Expr = {
    if (current == Minus) {
      advance()
      UnaryExpr("-", parseUnary())
    } else if (current == Plus) {
      advance()
      UnaryExpr("+", parseUnary())
    } else {
      parseSimpleMap()
    }
  }

  // SimpleMapExpr: PathExpr ("!" PathExpr)*
  private def parseSimpleMap(): Expr = {
    val first = parsePath()
    if (current == Bang) {
      val steps = ListBuffer[Expr](first)
      while (current == Bang) {
        advance() // consume !
        steps += parsePath()
      }
      SimpleMapExpr(steps.toList)
    } else {
      first
    }
  }

  // PathExpr
  private def parsePath(): Expr = {
    if (current == Slash) {
      advance()
      if (isStepStart(current)) {
        val firstStep = PathStep("/", parseStep())
        val steps = parseRemainingPathSteps(ListBuffer(firstStep))
        PathExpr(isAbsolute = true, steps)
      } else {
        // standalone root node "/"
        PathExpr(isAbsolute = true, Nil)
      }
    } else if (current == SlashSlash) {
      advance()
      val firstStep = PathStep("//", parseStep())
      val steps = parseRemainingPathSteps(ListBuffer(firstStep))
      PathExpr(isAbsolute = true, steps)
    } else {
      val firstStep = parseStep()
      if (current == Slash || current == SlashSlash) {
        val initial = PathStep("", firstStep)
        val steps = parseRemainingPathSteps(ListBuffer(initial))
        PathExpr(isAbsolute = false, steps)
      } else {
        firstStep match {
          case PostfixStep(expr) => expr
          case _                 => PathExpr(isAbsolute = false, List(PathStep("", firstStep)))
        }
      }
    }
  }

  private def parseRemainingPathSteps(steps: ListBuffer[PathStep]): List[PathStep] = {
    while (current == Slash || current == SlashSlash) {
      val sep = if (current == Slash) "/" else "//"
      advance()
      steps += PathStep(sep, parseStep())
    }
    steps.toList
  }

  private def isStepStart(tok: XPathToken): Boolean = tok match {
    case At | Dot | DotDot | Star => true
    case Name(_) | Keyword(_) => true
    case LParen | LBracket | TokVarRef(_) => true
    case Question => true
    case _ => false
  }

  private def parseStep(): Step = {
    if (current == DotDot) {
      advance()
      ParentStep
    } else if (current == Dot) {
      advance()
      val preds = parsePredicates()
      if (preds.isEmpty) ContextStep else PostfixStep(FilterExpr(ContextItem, preds))
    } else if (current == At) {
      advance()
      val nodeTest = current match {
        case Star => advance(); "*"
        case Name(n) => advance(); n
        case Keyword(k) => advance(); k
        case other => throw new IllegalArgumentException(s"Expected attribute name after '@', got $other")
      }
      val preds = parsePredicates()
      AxisStep(Some("attribute"), nodeTest, preds)
    } else if (isAxisSpecifier(current, lookAhead())) {
      val axisName = current match {
        case Name(n) => advance(); n
        case Keyword(k) => advance(); k
        case _ => ""
      }
      advance() // consume ::
      val nodeTest = parseNodeTest()
      val preds = parsePredicates()
      AxisStep(Some(axisName), nodeTest, preds)
    } else if (isNodeTestStart(current)) {
      val nodeTest = parseNodeTest()
      val preds = parsePredicates()
      AxisStep(None, nodeTest, preds)
    } else {
      val primary = parsePrimary()
      val postfix = parsePostfix(primary)
      PostfixStep(postfix)
    }
  }

  private def isAxisSpecifier(tok1: XPathToken, tok2: XPathToken): Boolean = {
    (tok1.isInstanceOf[Name] || tok1.isInstanceOf[Keyword]) && tok2 == ColonColon
  }

  private def isNodeTestStart(tok: XPathToken): Boolean = tok match {
    case Star => true
    case Name(n) =>
      if (n == "map" && lookAhead() == LBrace) false
      else if (n == "array" && lookAhead() == LBrace) false
      else if (n == "function" && lookAhead() == LParen) false
      else if (kindTestNames.contains(n) && lookAhead() == LParen) true
      else lookAhead() != LParen // not a function call
    case Keyword(k) =>
      if (k == "map" && lookAhead() == LBrace) false
      else if (k == "array" && lookAhead() == LBrace) false
      else if (k == "function" && lookAhead() == LParen) false
      else lookAhead() != LParen
    case _ => false
  }

  private def isNodeTestName(name: String): Boolean =
    kindTestNames.contains(name) || name.contains(":") || name == "*"

  private def parseNodeTest(): String = {
    if (current == Star) {
      advance()
      "*"
    } else {
      val name = current match {
        case Name(n) => advance(); n
        case Keyword(k) => advance(); k
        case other => throw new IllegalArgumentException(s"Expected node test, got $other")
      }
      if (kindTestNames.contains(name) && current == LParen) {
        advance() // (
        val testArg = if (current != RParen) {
          val argText = current.text
          advance()
          argText
        } else ""
        consume(RParen)
        s"$name($testArg)"
      } else {
        name
      }
    }
  }

  private def parsePredicates(): List[Expr] = {
    val preds = ListBuffer[Expr]()
    while (current == LBracket) {
      advance() // [
      preds += parseExpr()
      consume(RBracket)
    }
    preds.toList
  }

  // PostfixExpr ::= PrimaryExpr (Predicate | ArgumentList | Lookup)*
  private def parsePostfix(primary: Expr): Expr = {
    var expr = primary
    var continue = true
    while (continue) {
      if (current == LBracket) {
        val preds = parsePredicates()
        expr = FilterExpr(expr, preds)
      } else if (current == Question && lookAhead() != EOF && isKeySpecifierStart(lookAhead())) {
        advance() // consume ?
        val key = parseKeySpecifier()
        expr = PostfixLookup(expr, key)
      } else {
        continue = false
      }
    }
    expr
  }

  private def isKeySpecifierStart(tok: XPathToken): Boolean = tok match {
    case Name(_) | Keyword(_) | Star | TokIntegerLiteral(_) | LParen => true
    case _ => false
  }

  private def parseKeySpecifier(): KeySpecifier = {
    current match {
      case Star => advance(); WildcardKey
      case TokIntegerLiteral(raw) => advance(); IntegerKey(raw)
      case Name(n) => advance(); NameKey(n)
      case Keyword(k) => advance(); NameKey(k)
      case LParen =>
        advance()
        val expr = parseExpr()
        consume(RParen)
        ParenthesizedKey(expr)
      case other =>
        throw new IllegalArgumentException(s"Expected key specifier after '?', got $other")
    }
  }

  // PrimaryExpr
  private def parsePrimary(): Expr = {
    current match {
      case TokStringLiteral(_, value, delim) =>
        advance()
        StringLiteral(value, currentRaw(value, delim), delim)

      case TokIntegerLiteral(raw) =>
        advance()
        IntegerLiteral(raw)

      case TokDecimalLiteral(raw) =>
        advance()
        DecimalLiteral(raw)

      case TokDoubleLiteral(raw) =>
        advance()
        DoubleLiteral(raw)

      case TokVarRef(name) =>
        advance()
        VarRef(name)

      case Dot =>
        advance()
        ContextItem

      case Question =>
        // Unary lookup: ?key
        advance()
        val key = parseKeySpecifier()
        UnaryLookup(key)

      case LParen =>
        advance()
        if (current == RParen) {
          advance()
          SequenceExpr(Nil)
        } else {
          val expr = parseExpr()
          consume(RParen)
          expr match {
            case s: SequenceExpr => s
            case other => SequenceExpr(List(other))
          }
        }

      case LBracket =>
        // Square array: [1, 2, 3]
        advance()
        val items = if (current == RBracket) Nil else parseArgumentList()
        consume(RBracket)
        ArrayConstructor(items, isSquare = true)

      case Keyword("map") if lookAhead() == LBrace =>
        parseMapConstructor()

      case Name("map") if lookAhead() == LBrace =>
        parseMapConstructor()

      case Keyword("array") if lookAhead() == LBrace =>
        parseArrayConstructor()

      case Name("array") if lookAhead() == LBrace =>
        parseArrayConstructor()

      case Keyword("function") if lookAhead() == LParen =>
        parseInlineFunction()

      case Name("function") if lookAhead() == LParen =>
        parseInlineFunction()

      case Name(n) if lookAhead() == LParen =>
        advance() // consume name
        consume(LParen)
        val args = parseArgumentList()
        consume(RParen)
        FunctionCall(n, args)

      case Keyword(k) if lookAhead() == LParen && !kindTestNames.contains(k) =>
        advance() // consume name
        consume(LParen)
        val args = parseArgumentList()
        consume(RParen)
        FunctionCall(k, args)

      case Name(n) =>
        advance()
        VarRef(n) // fallback or raw name

      case other =>
        throw new IllegalArgumentException(s"Unexpected token in primary expression: $other at position $pos")
    }
  }

  private def currentRaw(value: String, delim: Char): String =
    s"$delim${value.replace(delim.toString, delim.toString + delim.toString)}$delim"

  private def parseArgumentList(): List[Expr] = {
    val args = ListBuffer[Expr]()
    if (current != RParen && current != RBracket) {
      args += parseArgument()
      while (current == Comma) {
        advance() // consume comma
        args += parseArgument()
      }
    }
    args.toList
  }

  private def parseArgument(): Expr = {
    if (current == Question) {
      advance()
      ArgumentPlaceholder()
    } else {
      parseExprSingle()
    }
  }

  private def parseMapConstructor(): Expr = {
    advance() // map
    consume(LBrace)
    val entries = ListBuffer[MapEntry]()
    if (current != RBrace) {
      var hasMore = true
      while (hasMore) {
        val key = parseExprSingle()
        consume(Colon)
        val value = parseExprSingle()
        entries += MapEntry(key, value)
        if (current == Comma) {
          advance()
        } else {
          hasMore = false
        }
      }
    }
    consume(RBrace)
    MapConstructor(entries.toList)
  }

  private def parseArrayConstructor(): Expr = {
    advance() // array
    consume(LBrace)
    val items = if (current != RBrace) {
      val expr = parseExpr()
      expr match {
        case SequenceExpr(list) => list
        case single => List(single)
      }
    } else Nil
    consume(RBrace)
    ArrayConstructor(items, isSquare = false)
  }

  private def parseInlineFunction(): Expr = {
    advance() // function
    consume(LParen)
    val params = ListBuffer[FunctionParam]()
    if (current != RParen) {
      var hasMore = true
      while (hasMore) {
        val paramName = current match {
          case TokVarRef(name) => advance(); name
          case other => throw new IllegalArgumentException(s"Expected parameter name in inline function, got $other")
        }
        val typeDecl = if (matchKeyword("as")) {
          advance()
          Some(readTypeName())
        } else None
        params += FunctionParam(paramName, typeDecl)
        if (current == Comma) {
          advance()
        } else {
          hasMore = false
        }
      }
    }
    consume(RParen)
    val returnType = if (matchKeyword("as")) {
      advance()
      Some(readTypeName())
    } else None
    consume(LBrace)
    val body = parseExpr()
    consume(RBrace)
    InlineFunction(params.toList, returnType, body)
  }
}
