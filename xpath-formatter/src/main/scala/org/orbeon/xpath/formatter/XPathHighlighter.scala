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
   * Highlights an XPath 3.1 expression into a list of styled [[Span]]s.
   * Preserves all original whitespace, line breaks, and comments.
   */
  def highlight(xpath: String): List[Span] = {
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

  def highlightToHtml(xpath: String, cssPrefix: String = "xpath-"): String =
    highlight(xpath).toHtml(cssPrefix)

  def highlightToAnsi(xpath: String): String =
    highlight(xpath).toAnsi
}
