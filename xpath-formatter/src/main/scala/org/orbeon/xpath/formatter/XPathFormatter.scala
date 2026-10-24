package org.orbeon.xpath.formatter

import XPathAst.*
import Span.*
import scala.collection.mutable.ListBuffer

class XPathFormatter(val options: XPathFormatterOptions = XPathFormatterOptions.default) {

  private val nl = options.newline

  def format(expr: Expr): String =
    formatSpans(expr).toPlainText

  def formatSpans(expr: Expr): List[Span] =
    formatExprSpans(expr, 0)

  private def indent(level: Int): String = options.indentString(level)

  private def span(text: String, tokenType: XPathTokenType = XPathTokenType.Plain): Span =
    Span(text, tokenType)

  private def kw(text: String): Span = span(text, XPathTokenType.Keyword)
  private def op(text: String): Span = span(text, XPathTokenType.Operator)
  private def punc(text: String): Span = span(text, XPathTokenType.Punctuation)
  private def vr(name: String): Span = span("$" + name, XPathTokenType.Variable)
  private def fn(name: String): Span = span(name, XPathTokenType.FunctionCall)
  private def num(raw: String): Span = span(raw, XPathTokenType.NumberLiteral)
  private def str(raw: String): Span = span(raw, XPathTokenType.StringLiteral)
  private def ax(text: String): Span = span(text, XPathTokenType.Axis)
  private def nt(text: String): Span = span(text, XPathTokenType.NodeTest)
  private def pl(text: String): Span = span(text, XPathTokenType.Plain)

  private def formatExprSpans(expr: Expr, level: Int): List[Span] = expr match {
    case FlworExpr(clauses, returnExpr) =>
      formatFlwor(clauses, returnExpr, level)

    case QuantifiedExpr(isEvery, bindings, satisfies) =>
      formatQuantified(isEvery, bindings, satisfies, level)

    case IfExpr(cond, thenExpr, elseExpr) =>
      formatIf(cond, thenExpr, elseExpr, level)

    case BinaryExpr(lhs, opText, rhs) =>
      formatBinary(lhs, opText, rhs, level)

    case UnaryExpr(opText, inner) =>
      op(opText) :: formatExprSpans(inner, level)

    case ArrowExpr(target, steps) =>
      formatArrow(target, steps, level)

    case SimpleMapExpr(steps) =>
      val sep: List[Span] =
        if (options.mapOperatorOnNewline) List(pl(nl), pl(indent(level + 1)), op("!"), pl(" "))
        else List(pl(" "), op("!"), pl(" "))
      joinSpans(steps.map(s => formatExprSpans(s, level)), sep)

    case FilterExpr(primary, preds) =>
      val pSpans = formatExprSpans(primary, level)
      val predSpans = preds.flatMap(p => punc("[") :: (formatExprSpans(p, level) :+ punc("]")))
      pSpans ::: predSpans

    case PathExpr(isAbsolute, steps) =>
      formatPath(isAbsolute, steps, level)

    case ContextItem =>
      List(punc("."))

    case VarRef(name) =>
      List(vr(name))

    case StringLiteral(value, raw, delim) =>
      List(str(formatStringLiteral(value, raw, delim)))

    case IntegerLiteral(raw) =>
      List(num(raw))

    case DecimalLiteral(raw) =>
      List(num(raw))

    case DoubleLiteral(raw) =>
      List(num(raw))

    case SequenceExpr(items) =>
      formatSequence(items, level)

    case FunctionCall(name, args) =>
      formatFunctionCall(name, args, level)

    case ArgumentPlaceholder() =>
      List(op("?"))

    case MapConstructor(entries) =>
      formatMap(entries, level)

    case ArrayConstructor(items, isSquare) =>
      formatArray(items, isSquare, level)

    case PostfixLookup(target, key) =>
      val tSpans = formatExprSpans(target, level)
      val kSpans = formatKeySpecifier(key, level)
      tSpans ::: (op("?") :: kSpans)

    case UnaryLookup(key) =>
      op("?") :: formatKeySpecifier(key, level)

    case InlineFunction(params, returnType, body) =>
      formatInlineFunction(params, returnType, body, level)
  }

  private def formatStringLiteral(value: String, raw: String, delim: Char): String = {
    options.quotePreference match {
      case QuotePreference.Preserve =>
        raw
      case QuotePreference.PreferDouble =>
        "\"" + value.replace("\"", "\"\"") + "\""
      case QuotePreference.PreferSingle =>
        "'" + value.replace("'", "''") + "'"
    }
  }

  // FLWOR Formatting
  private def formatFlwor(clauses: List[FlworClause], returnExpr: Expr, level: Int): List[Span] = {
    val inlineAttempt = formatFlworInline(clauses, returnExpr)
    val inlineText = inlineAttempt.toPlainText
    if (inlineText.length <= options.flworInlineThreshold && !inlineText.contains("\n")) {
      inlineAttempt
    } else {
      val clauseLines = clauses.flatMap(c => formatFlworClause(c, level))
      val returnLevel = if (options.flworIndentReturn) level + 1 else level
      val returnBodySpans = formatExprSpans(returnExpr, returnLevel + 1)
      val returnBodyText = returnBodySpans.toPlainText

      val returnLineSpans: List[Span] =
        if (returnBodyText.contains("\n")) {
          List(pl(indent(returnLevel)), kw("return"), pl(nl)) ::: ensureIndented(returnBodySpans, returnLevel + 1)
        } else {
          List(pl(indent(returnLevel)), kw("return"), pl(" ")) ::: returnBodySpans
        }

      joinLines(clauseLines :+ returnLineSpans)
    }
  }

  private def formatFlworInline(clauses: List[FlworClause], returnExpr: Expr): List[Span] = {
    val clausesSpans = clauses.flatMap {
      case ForClause(bindings) =>
        val bSpans = joinSpans(bindings.map { b =>
          vr(b.varName) :: pl(" ") :: kw("in") :: pl(" ") :: formatExprSpans(b.expr, 0)
        }, List(punc(","), pl(" ")))
        kw("for") :: pl(" ") :: bSpans ::: List(pl(" "))

      case LetClause(bindings) =>
        val bSpans = joinSpans(bindings.map { b =>
          vr(b.varName) :: pl(" ") :: op(":=") :: pl(" ") :: formatExprSpans(b.expr, 0)
        }, List(punc(","), pl(" ")))
        kw("let") :: pl(" ") :: bSpans ::: List(pl(" "))

      case WhereClause(cond) =>
        kw("where") :: pl(" ") :: formatExprSpans(cond, 0) ::: List(pl(" "))

      case OrderByClause(specs) =>
        val sSpans = joinSpans(specs.map(formatOrderSpec), List(punc(","), pl(" ")))
        kw("order") :: pl(" ") :: kw("by") :: pl(" ") :: sSpans ::: List(pl(" "))
    }
    clausesSpans ::: (kw("return") :: pl(" ") :: formatExprSpans(returnExpr, 0))
  }

  private def formatFlworClause(clause: FlworClause, level: Int): List[List[Span]] = clause match {
    case ForClause(bindings) =>
      if (options.flworVariablePerLine && bindings.length > 1) {
        val maxVarLen = if (options.flworAlignBindings) bindings.map(_.varName.length).max else 0
        def padSpaces(varName: String): String =
          if (options.flworAlignBindings) " " * (1 + (maxVarLen - varName.length))
          else " "

        val contIndent = indent(level) + "    "
        val first = (pl(indent(level)) :: kw("for") :: pl(" ") :: vr(bindings.head.varName) :: pl(padSpaces(bindings.head.varName)) :: kw("in") :: pl(" ") :: formatExprSpans(bindings.head.expr, level + 2)) :+ punc(",")
        val rest = bindings.tail.zipWithIndex.map { case (b, idx) =>
          val isLast = idx == bindings.length - 2
          val base = pl(contIndent) :: vr(b.varName) :: pl(padSpaces(b.varName)) :: kw("in") :: pl(" ") :: formatExprSpans(b.expr, level + 2)
          if (isLast) base else base :+ punc(",")
        }
        first :: rest
      } else {
        val bSpans = joinSpans(bindings.map { b =>
          vr(b.varName) :: pl(" ") :: kw("in") :: pl(" ") :: formatExprSpans(b.expr, level + 1)
        }, List(punc(","), pl(" ")))
        List(pl(indent(level)) :: kw("for") :: pl(" ") :: bSpans)
      }

    case LetClause(bindings) =>
      if (options.flworVariablePerLine && bindings.length > 1) {
        val maxVarLen = if (options.flworAlignBindings) bindings.map(_.varName.length).max else 0
        def padSpaces(varName: String): String =
          if (options.flworAlignBindings) " " * (1 + (maxVarLen - varName.length))
          else " "

        val contIndent = indent(level) + "    "
        val first = (pl(indent(level)) :: kw("let") :: pl(" ") :: vr(bindings.head.varName) :: pl(padSpaces(bindings.head.varName)) :: op(":=") :: pl(" ") :: formatExprSpans(bindings.head.expr, level + 2)) :+ punc(",")
        val rest = bindings.tail.zipWithIndex.map { case (b, idx) =>
          val isLast = idx == bindings.length - 2
          val base = pl(contIndent) :: vr(b.varName) :: pl(padSpaces(b.varName)) :: op(":=") :: pl(" ") :: formatExprSpans(b.expr, level + 2)
          if (isLast) base else base :+ punc(",")
        }
        first :: rest
      } else {
        val bSpans = joinSpans(bindings.map { b =>
          vr(b.varName) :: pl(" ") :: op(":=") :: pl(" ") :: formatExprSpans(b.expr, level + 1)
        }, List(punc(","), pl(" ")))
        List(pl(indent(level)) :: kw("let") :: pl(" ") :: bSpans)
      }

    case WhereClause(cond) =>
      List(pl(indent(level)) :: kw("where") :: pl(" ") :: formatExprSpans(cond, level + 1))

    case OrderByClause(specs) =>
      val sSpans = joinSpans(specs.map(formatOrderSpec), List(punc(","), pl(" ")))
      List(pl(indent(level)) :: kw("order") :: pl(" ") :: kw("by") :: pl(" ") :: sSpans)
  }

  private def formatOrderSpec(spec: OrderSpec): List[Span] = {
    val base = formatExprSpans(spec.expr, 0)
    val dir = if (!spec.ascending) List(pl(" "), kw("descending")) else Nil
    val empty = spec.emptyGreatest match {
      case Some(true)  => List(pl(" "), kw("empty"), pl(" "), kw("greatest"))
      case Some(false) => List(pl(" "), kw("empty"), pl(" "), kw("least"))
      case None        => Nil
    }
    val coll = spec.collation match {
      case Some(c) => List(pl(" "), kw("collation"), pl(" "), str(s""""$c""""))
      case None    => Nil
    }
    base ::: dir ::: empty ::: coll
  }

  // Quantified Formatting
  private def formatQuantified(isEvery: Boolean, bindings: List[ForBinding], satisfies: Expr, level: Int): List[Span] = {
    val kwText = if (isEvery) "every" else "some"
    val bSpans = joinSpans(bindings.map { b =>
      vr(b.varName) :: pl(" ") :: kw("in") :: pl(" ") :: formatExprSpans(b.expr, level)
    }, List(punc(","), pl(" ")))

    val satSpans = formatExprSpans(satisfies, level + 1)
    val satText = satSpans.toPlainText
    val inline = kw(kwText) :: pl(" ") :: bSpans ::: (pl(" ") :: kw("satisfies") :: pl(" ") :: satSpans)
    val inlineText = inline.toPlainText

    if (inlineText.length <= options.flworInlineThreshold && !satText.contains("\n")) {
      inline
    } else {
      List(
        pl(indent(level)), kw(kwText), pl(" ")
      ) ::: bSpans ::: List(
        pl(nl), pl(indent(level + 1)), kw("satisfies"), pl(" ")
      ) ::: satSpans
    }
  }

  // If Formatting
  private def formatIf(cond: Expr, thenExpr: Expr, elseExpr: Expr, level: Int): List[Span] = {
    val (branches, finalElse) = collectIfBranches(IfExpr(cond, thenExpr, elseExpr))
    val inline = formatIfInline(branches, finalElse)
    val inlineText = inline.toPlainText

    if (options.ifStyle == IfStyle.Inline || (inlineText.length <= options.ifInlineThreshold && !inlineText.contains("\n"))) {
      inline
    } else options.ifStyle match {
      case IfStyle.ClausePerLine =>
        val branchSpans = branches.zipWithIndex.flatMap { case ((bCond, bThen), idx) =>
          val prefix =
            if (idx == 0) List(pl(indent(level)), kw("if"), pl(" "), punc("("))
            else List(pl(nl), pl(indent(level)), kw("else"), pl(" "), kw("if"), pl(" "), punc("("))
          val condSpans = formatExprSpans(bCond, level)
          val thenSpans = formatExprSpans(bThen, level + 1)
          prefix ::: condSpans ::: List(
            punc(")"), pl(nl),
            pl(indent(level)), kw("then"), pl(" ")
          ) ::: thenSpans
        }
        val elseSpans = formatExprSpans(finalElse, level + 1)
        val elseBranchSpans = List(
          pl(nl),
          pl(indent(level)), kw("else"), pl(" ")
        ) ::: elseSpans
        branchSpans ::: elseBranchSpans

      case _ => // MultiLine
        val branchSpans = branches.zipWithIndex.flatMap { case ((bCond, bThen), idx) =>
          val prefix =
            if (idx == 0) List(pl(indent(level)), kw("if"), pl(" "), punc("("))
            else List(pl(nl), pl(indent(level)), kw("else"), pl(" "), kw("if"), pl(" "), punc("("))
          val condSpans = formatExprSpans(bCond, level)
          val thenSpans = formatExprSpans(bThen, level + 1)
          prefix ::: condSpans ::: List(
            punc(")"), pl(" "), kw("then"), pl(nl)
          ) ::: ensureIndented(thenSpans, level + 1)
        }
        val elseSpans = formatExprSpans(finalElse, level + 1)
        val elseBranchSpans = List(
          pl(nl), pl(indent(level)), kw("else"), pl(nl)
        ) ::: ensureIndented(elseSpans, level + 1)

        branchSpans ::: elseBranchSpans
    }
  }

  private def collectIfBranches(ifExpr: IfExpr): (List[(Expr, Expr)], Expr) = {
    val branches = ListBuffer[(Expr, Expr)]()
    var current: Expr = ifExpr
    var done = false
    while (!done) {
      current match {
        case IfExpr(cond, thenExpr, elseExpr) =>
          branches += ((cond, thenExpr))
          current = elseExpr
        case other =>
          done = true
      }
    }
    (branches.toList, current)
  }

  private def formatIfInline(branches: List[(Expr, Expr)], finalElse: Expr): List[Span] = {
    val branchSpans = branches.zipWithIndex.flatMap { case ((cond, thenExpr), idx) =>
      val prefix =
        if (idx == 0) List(kw("if"), pl(" "))
        else List(pl(" "), kw("else"), pl(" "), kw("if"), pl(" "))
      val condSpans = formatExprSpans(cond, 0)
      val thenSpans = formatExprSpans(thenExpr, 0)
      prefix ::: (punc("(") :: condSpans ::: List(punc(")"), pl(" "), kw("then"), pl(" ")) ::: thenSpans)
    }
    val elseSpans = formatExprSpans(finalElse, 0)
    branchSpans ::: List(pl(" "), kw("else"), pl(" ")) ::: elseSpans
  }

  private def ensureIndented(spans: List[Span], level: Int): List[Span] = {
    if (spans.isEmpty) Nil
    else {
      val cleanSpans = spans.dropWhile(s => s.tokenType == XPathTokenType.Plain && s.text.isEmpty)
      cleanSpans match {
        case Span(text, XPathTokenType.Plain) :: rest if text.nonEmpty && text.forall(c => c == ' ' || c == '\t') =>
          if (level > 0) pl(indent(level)) :: rest
          else rest
        case _ =>
          if (level > 0) pl(indent(level)) :: cleanSpans
          else cleanSpans
      }
    }
  }

  // Binary Expression Formatting
  private def formatBinary(lhs: Expr, opText: String, rhs: Expr, level: Int): List[Span] = {
    val isKeywordOp = Set("and", "or", "to", "div", "idiv", "mod", "eq", "ne", "lt", "le", "gt", "ge", "is", "union", "intersect", "except", "instance of", "treat as", "castable as", "cast as").contains(opText)
    val opSpan = if (isKeywordOp) kw(opText) else op(opText)
    val spaceSpans: List[Span] = if (options.spaceAroundOperators) List(pl(" ")) else Nil

    val lhsSpans = formatExprSpans(lhs, level)
    val rhsSpans = formatExprSpans(rhs, level)
    val inline = lhsSpans ::: spaceSpans ::: (opSpan :: spaceSpans) ::: rhsSpans

    if (inline.toPlainText.length > options.maxLineLength && (opText == "and" || opText == "or")) {
      if (options.breakBeforeBinaryOperator) {
        lhsSpans ::: List(pl(nl), pl(indent(level + 1)), opSpan, pl(" ")) ::: rhsSpans
      } else {
        lhsSpans ::: List(pl(" "), opSpan, pl(nl), pl(indent(level + 1))) ::: rhsSpans
      }
    } else {
      inline
    }
  }

  // Arrow Formatting
  private def formatArrow(target: Expr, steps: List[ArrowStep], level: Int): List[Span] = {
    val targetSpans = formatExprSpans(target, level)
    if (options.arrowOnNewline && steps.nonEmpty) {
      val stepLines = steps.map { step =>
        val argsSpans = joinSpans(step.args.map(a => formatExprSpans(a, level + 1)), List(punc(","), pl(" ")))
        List(pl(indent(level + 1)), op("=>"), pl(" "), fn(step.functionName), punc("(")) ::: (argsSpans :+ punc(")"))
      }
      targetSpans ::: List(pl(nl)) ::: joinLines(stepLines)
    } else {
      val stepsSpans = steps.flatMap { step =>
        val argsSpans = joinSpans(step.args.map(a => formatExprSpans(a, level)), List(punc(","), pl(" ")))
        List(pl(" "), op("=>"), pl(" "), fn(step.functionName), punc("(")) ::: (argsSpans :+ punc(")"))
      }
      targetSpans ::: stepsSpans
    }
  }

  // Path Formatting
  private def formatPath(isAbsolute: Boolean, steps: List[PathStep], level: Int): List[Span] = {
    if (steps.isEmpty) {
      if (isAbsolute) List(punc("/")) else Nil
    } else {
      val result = new ListBuffer[Span]()
      var first = true
      for (step <- steps) {
        val stepSpans = formatStep(step.step, level)
        if (first) {
          if (step.separator.nonEmpty) result += punc(step.separator)
          result ++= stepSpans
          first = false
        } else {
          if (options.stepPerLine) {
            result += pl(nl)
            result += pl(indent(level + 1))
            result += punc(step.separator)
            result ++= stepSpans
          } else {
            result += punc(step.separator)
            result ++= stepSpans
          }
        }
      }
      result.toList
    }
  }

  private def formatStep(step: Step, level: Int): List[Span] = step match {
    case AxisStep(axis, nodeTest, preds) =>
      val axisSpans: List[Span] = axis match {
        case Some("attribute") => List(ax("@"))
        case Some(a)           => List(ax(s"$a::"))
        case None              => Nil
      }
      val ntSpans: List[Span] = List(nt(nodeTest))
      val predSpans: List[Span] = preds.flatMap(p => punc("[") :: (formatExprSpans(p, level) :+ punc("]")))
      axisSpans ::: ntSpans ::: predSpans

    case PostfixStep(expr) =>
      formatExprSpans(expr, level)

    case ParentStep =>
      List(punc(".."))

    case ContextStep =>
      List(punc("."))
  }

  // Sequence Formatting
  private def formatSequence(items: List[Expr], level: Int): List[Span] = {
    if (items.isEmpty) {
      List(punc("()"))
    } else if (items.length == 1) {
      punc("(") :: (formatExprSpans(items.head, level) :+ punc(")"))
    } else {
      val commaSpans = if (options.spaceAfterComma) List(punc(","), pl(" ")) else List(punc(","))
      val itemsSpans = joinSpans(items.map(i => formatExprSpans(i, level)), commaSpans)
      punc("(") :: (itemsSpans :+ punc(")"))
    }
  }

  // Function Call Formatting
  private def formatFunctionCall(name: String, args: List[Expr], level: Int): List[Span] = {
    val commaSpans = if (options.spaceAfterComma) List(punc(","), pl(" ")) else List(punc(","))
    val inlineArgs = joinSpans(args.map(a => formatExprSpans(a, level)), commaSpans)
    val inline = fn(name) :: punc("(") :: (inlineArgs :+ punc(")"))

    if (options.functionArgsOnNewlineThreshold > 0 && args.length > options.functionArgsOnNewlineThreshold) {
      val argLines = args.map(a => pl(indent(level + 1)) :: formatExprSpans(a, level + 1))
      val multiLineArgs = joinLinesWithSuffix(argLines, List(punc(",")))
      fn(name) :: punc("(") :: pl(nl) :: multiLineArgs ::: List(pl(nl), pl(indent(level)), punc(")"))
    } else {
      inline
    }
  }

  // Map Formatting
  private def formatMap(entries: List[MapEntry], level: Int): List[Span] = {
    if (entries.isEmpty) {
      List(kw("map"), pl(" "), punc("{}"))
    } else if (options.mapEntriesOnNewline && entries.length > 1) {
      val colonSpans = if (options.spaceAroundMapColon) List(pl(" "), punc(":"), pl(" ")) else List(punc(":"), pl(" "))
      val entryLines = entries.map { e =>
        pl(indent(level + 1)) :: formatExprSpans(e.key, level + 1) ::: colonSpans ::: formatExprSpans(e.value, level + 1)
      }
      val joinedEntries = joinLinesWithSuffix(entryLines, List(punc(",")))
      kw("map") :: pl(" ") :: punc("{") :: pl(nl) :: joinedEntries ::: List(pl(nl), pl(indent(level)), punc("}"))
    } else {
      val colonSpans = if (options.spaceAroundMapColon) List(pl(" "), punc(":"), pl(" ")) else List(punc(":"), pl(" "))
      val commaSpans = if (options.spaceAfterComma) List(punc(","), pl(" ")) else List(punc(","))
      val entrySpans = joinSpans(entries.map { e =>
        formatExprSpans(e.key, level) ::: colonSpans ::: formatExprSpans(e.value, level)
      }, commaSpans)
      kw("map") :: pl(" ") :: punc("{") :: pl(" ") :: entrySpans ::: List(pl(" "), punc("}"))
    }
  }

  // Array Formatting
  private def formatArray(items: List[Expr], isSquare: Boolean, level: Int): List[Span] = {
    val commaSpans = if (options.spaceAfterComma) List(punc(","), pl(" ")) else List(punc(","))
    if (isSquare) {
      val itemsSpans = joinSpans(items.map(i => formatExprSpans(i, level)), commaSpans)
      punc("[") :: (itemsSpans :+ punc("]"))
    } else {
      if (items.isEmpty) {
        List(kw("array"), pl(" "), punc("{}"))
      } else {
        val itemsSpans = joinSpans(items.map(i => formatExprSpans(i, level)), commaSpans)
        kw("array") :: pl(" ") :: punc("{") :: pl(" ") :: itemsSpans ::: List(pl(" "), punc("}"))
      }
    }
  }

  private def formatKeySpecifier(key: KeySpecifier, level: Int): List[Span] = key match {
    case NameKey(name)           => List(nt(name))
    case IntegerKey(raw)         => List(num(raw))
    case WildcardKey             => List(nt("*"))
    case ParenthesizedKey(expr)  => punc("(") :: (formatExprSpans(expr, level) :+ punc(")"))
  }

  private def formatInlineFunction(params: List[FunctionParam], returnType: Option[String], body: Expr, level: Int): List[Span] = {
    val paramsSpans = joinSpans(params.map { p =>
      val tSpans = p.typeDeclaration.map(t => List(pl(" "), kw("as"), pl(" "), nt(t))).getOrElse(Nil)
      vr(p.name) :: tSpans
    }, List(punc(","), pl(" ")))

    val retSpans = returnType.map(t => List(pl(" "), kw("as"), pl(" "), nt(t))).getOrElse(Nil)
    val bodySpans = formatExprSpans(body, level + 1)
    val bodyText = bodySpans.toPlainText

    val prefix = kw("function") :: punc("(") :: paramsSpans ::: (punc(")") :: retSpans) ::: List(pl(" "), punc("{"))
    if (bodyText.contains("\n")) {
      prefix ::: List(pl(nl)) ::: ensureIndented(bodySpans, level + 1) ::: List(pl(nl), pl(indent(level)), punc("}"))
    } else {
      prefix ::: List(pl(" ")) ::: bodySpans ::: List(pl(" "), punc("}"))
    }
  }

  private def joinSpans(lists: List[List[Span]], sep: List[Span]): List[Span] = {
    if (lists.isEmpty) Nil
    else lists.reduceLeft((acc, el) => acc ::: sep ::: el)
  }

  private def joinLines(lines: List[List[Span]]): List[Span] = {
    if (lines.isEmpty) Nil
    else lines.reduceLeft((acc, el) => acc ::: List(pl(nl)) ::: el)
  }

  private def joinLinesWithSuffix(lines: List[List[Span]], suffix: List[Span]): List[Span] = {
    if (lines.isEmpty) Nil
    else {
      val init = lines.init.map(l => l ::: suffix)
      val all = init :+ lines.last
      all.reduceLeft((acc, el) => acc ::: List(pl(nl)) ::: el)
    }
  }
}

object XPathFormatter {
  def format(xpath: String, options: XPathFormatterOptions = XPathFormatterOptions.default): String =
    formatSpans(xpath, options).toPlainText

  def formatSpans(xpath: String, options: XPathFormatterOptions = XPathFormatterOptions.default): List[Span] = {
    val tokens = XPathLexer.tokenize(xpath)
    val parser = new XPathParser(tokens)
    val ast = parser.parse()
    val formatter = new XPathFormatter(options)
    formatter.formatSpans(ast)
  }

  def formatToHtml(xpath: String, options: XPathFormatterOptions = XPathFormatterOptions.default, cssPrefix: String = "xpath-"): String =
    formatSpans(xpath, options).toHtml(cssPrefix)

  def formatToAnsi(xpath: String, options: XPathFormatterOptions = XPathFormatterOptions.default): String =
    formatSpans(xpath, options).toAnsi
}
