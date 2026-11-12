package org.orbeon.xpath.formatter

import scala.collection.mutable.ListBuffer
import XPathToken.*
import Span.*

object XPathHighlighter {

  private val kindTestNames = Set(
    "node", "text", "comment", "processing-instruction",
    "element", "attribute", "schema-element", "schema-attribute",
    "document-node", "namespace-node"
  )

  /**
   * Highlights an XPath 3.1 expression (or value template when `avt = true`) into a list of styled [[Span]]s.
   * Preserves all original whitespace, line breaks, and comments.
   */
  def highlight(xpath: String, avt: Boolean = false): List[Span] =
    if (avt) highlightAvt(xpath) else highlightXPath(xpath)

  def highlightToHtml(xpath: String, cssPrefix: String = "xpath-", avt: Boolean = false): String =
    highlight(xpath, avt = avt).toHtml(cssPrefix)

  def highlightToAnsi(xpath: String, avt: Boolean = false): String =
    highlight(xpath, avt = avt).toAnsi

  private def highlightAvt(avtString: String): List[Span] = {
    val spans = new ListBuffer[Span]()
    val len   = avtString.length
    var pos   = 0

    while (pos < len) {
      val literalStart = pos
      var atExprStart  = false

      while (pos < len && !atExprStart) {
        val ch = avtString.charAt(pos)
        if (ch == '{') {
          if (pos + 1 < len && avtString.charAt(pos + 1) == '{') {
            // Escaped `{{` in literal text
            pos += 2
          } else {
            atExprStart = true
          }
        } else if (ch == '}' && pos + 1 < len && avtString.charAt(pos + 1) == '}') {
          // Escaped `}}` in literal text
          pos += 2
        } else {
          // Regular literal character (or lone unmatched `}`)
          pos += 1
        }
      }

      if (pos > literalStart)
        spans += Span(avtString.substring(literalStart, pos), XPathTokenType.Plain)

      if (atExprStart) {
        spans += Span("{", XPathTokenType.Punctuation)
        pos += 1 // skip opening `{`

        val exprStart  = pos
        var braceDepth = 1

        while (pos < len && braceDepth > 0) {
          val ch = avtString.charAt(pos)
          if (ch == '"' || ch == '\'') {
            pos = skipStringLiteral(avtString, pos, len, ch)
          } else if (ch == '(' && pos + 1 < len && avtString.charAt(pos + 1) == ':') {
            pos = skipComment(avtString, pos, len)
          } else if (ch == '{') {
            braceDepth += 1
            pos += 1
          } else if (ch == '}') {
            braceDepth -= 1
            if (braceDepth > 0)
              pos += 1
          } else {
            pos += 1
          }
        }

        val exprEnd = pos
        if (exprEnd > exprStart)
          spans ++= highlightXPath(avtString.substring(exprStart, exprEnd))

        if (braceDepth == 0) {
          spans += Span("}", XPathTokenType.Punctuation)
          pos += 1 // skip closing `}`
        }
      }
    }

    spans.toList
  }

  private def skipStringLiteral(s: String, start: Int, len: Int, delimiter: Char): Int = {
    var pos = start + 1 // skip opening quote
    while (pos < len) {
      if (s.charAt(pos) == delimiter) {
        if (pos + 1 < len && s.charAt(pos + 1) == delimiter) {
          pos += 2 // escaped quote
        } else {
          return pos + 1
        }
      } else {
        pos += 1
      }
    }
    pos
  }

  private def skipComment(s: String, start: Int, len: Int): Int = {
    var pos   = start + 2 // skip opening `(:`
    var depth = 1
    while (pos < len && depth > 0) {
      if (s.charAt(pos) == '(' && pos + 1 < len && s.charAt(pos + 1) == ':') {
        depth += 1
        pos += 2
      } else if (s.charAt(pos) == ':' && pos + 1 < len && s.charAt(pos + 1) == ')') {
        depth -= 1
        pos += 2
      } else {
        pos += 1
      }
    }
    pos
  }

  private def highlightXPath(xpath: String): List[Span] = {
    val tokens = new XPathLexer(xpath).tokenize(includeWhitespaceAndComments = true)
    val spans = new ListBuffer[Span]()
    var i = 0
    val len = tokens.length

    def nextNonWhitespace(from: Int): Option[XPathToken] = {
      var j = from
      while (j < len) {
        tokens(j) match {
          case Whitespace(_) | Comment(_) => j += 1
          case other => return Some(other)
        }
      }
      None
    }

    while (i < len) {
      val tok = tokens(i)
      tok match {
        case EOF => // done

        case Whitespace(ws) =>
          spans += Span(ws, XPathTokenType.Plain)

        case Comment(c) =>
          spans += Span(c, XPathTokenType.Comment)

        case VarRef(name) =>
          spans += Span("$" + name, XPathTokenType.Variable)

        case StringLiteral(raw, _, _) =>
          spans += Span(raw, XPathTokenType.StringLiteral)

        case IntegerLiteral(raw) =>
          spans += Span(raw, XPathTokenType.NumberLiteral)

        case DecimalLiteral(raw) =>
          spans += Span(raw, XPathTokenType.NumberLiteral)

        case DoubleLiteral(raw) =>
          spans += Span(raw, XPathTokenType.NumberLiteral)

        case Keyword(kw) =>
          spans += Span(kw, XPathTokenType.Keyword)

        case ColonColon =>
          spans += Span("::", XPathTokenType.Axis)

        case At =>
          spans += Span("@", XPathTokenType.Axis)

        case Arrow =>
          spans += Span("=>", XPathTokenType.Operator)

        case Bang =>
          spans += Span("!", XPathTokenType.Operator)

        case Question =>
          spans += Span("?", XPathTokenType.Operator)

        case Pipe | PipePipe | Plus | Minus | Star | Eq | Ne | Lt | Le | Gt | Ge | Precedes | Follows | Assign =>
          spans += Span(tok.text, XPathTokenType.Operator)

        case LParen | RParen | LBracket | RBracket | LBrace | RBrace | Comma | Colon | Slash | SlashSlash | Dot | DotDot =>
          spans += Span(tok.text, XPathTokenType.Punctuation)

        case Name(name) =>
          val next = nextNonWhitespace(i + 1)
          if (next.contains(ColonColon)) {
            spans += Span(name, XPathTokenType.Axis)
          } else if (next.contains(LParen)) {
            if (kindTestNames.contains(name)) {
              spans += Span(name, XPathTokenType.NodeTest)
            } else {
              spans += Span(name, XPathTokenType.FunctionCall)
            }
          } else {
            spans += Span(name, XPathTokenType.NodeTest)
          }
      }
      i += 1
    }

    spans.toList
  }
}
