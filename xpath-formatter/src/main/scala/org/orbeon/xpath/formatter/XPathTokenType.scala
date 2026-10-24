package org.orbeon.xpath.formatter

/**
 * Semantic token types for XPath 3.1 syntax highlighting.
 */
sealed trait XPathTokenType {
  def cssClass: String
  def ansiColor: String
}

object XPathTokenType {

  case object Keyword extends XPathTokenType {
    def cssClass: String = "keyword"
    def ansiColor: String = "\u001b[1;35m" // Magenta bold
  }

  case object Variable extends XPathTokenType {
    def cssClass: String = "variable"
    def ansiColor: String = "\u001b[36m" // Cyan
  }

  case object StringLiteral extends XPathTokenType {
    def cssClass: String = "string"
    def ansiColor: String = "\u001b[32m" // Green
  }

  case object NumberLiteral extends XPathTokenType {
    def cssClass: String = "number"
    def ansiColor: String = "\u001b[33m" // Yellow
  }

  case object FunctionCall extends XPathTokenType {
    def cssClass: String = "function"
    def ansiColor: String = "\u001b[1;34m" // Blue bold
  }

  case object Axis extends XPathTokenType {
    def cssClass: String = "axis"
    def ansiColor: String = "\u001b[35m" // Magenta
  }

  case object NodeTest extends XPathTokenType {
    def cssClass: String = "node-test"
    def ansiColor: String = "\u001b[34m" // Blue
  }

  case object Operator extends XPathTokenType {
    def cssClass: String = "operator"
    def ansiColor: String = "\u001b[1;37m" // White bold
  }

  case object Punctuation extends XPathTokenType {
    def cssClass: String = "punctuation"
    def ansiColor: String = "\u001b[37m" // White/Gray
  }

  case object Comment extends XPathTokenType {
    def cssClass: String = "comment"
    def ansiColor: String = "\u001b[2;37m" // Dim / Gray
  }

  case object Plain extends XPathTokenType {
    def cssClass: String = "plain"
    def ansiColor: String = "" // No color
  }

  val AnsiReset: String = "\u001b[0m"
}

/**
 * A styled piece of text associated with a semantic [[XPathTokenType]].
 */
case class Span(text: String, tokenType: XPathTokenType) {
  def length: Int = text.length

  def toHtml(cssPrefix: String = "xpath-"): String = {
    val escaped = Span.escapeHtml(text)
    if (tokenType == XPathTokenType.Plain) {
      escaped
    } else {
      s"""<span class="$cssPrefix${tokenType.cssClass}">$escaped</span>"""
    }
  }

  def toAnsi: String = {
    if (tokenType.ansiColor.isEmpty) text
    else s"${tokenType.ansiColor}$text${XPathTokenType.AnsiReset}"
  }
}

object Span {
  def escapeHtml(s: String): String = {
    val sb = new java.lang.StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '&'  => sb.append("&amp;")
        case '<'  => sb.append("&lt;")
        case '>'  => sb.append("&gt;")
        case '"'  => sb.append("&quot;")
        case '\'' => sb.append("&#39;")
        case c    => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  implicit class SpanListOps(val spans: List[Span]) extends AnyVal {
    def toPlainText: String =
      spans.map(_.text).mkString

    def toHtml(cssPrefix: String = "xpath-"): String =
      spans.map(_.toHtml(cssPrefix)).mkString

    def toAnsi: String =
      spans.map(_.toAnsi).mkString
  }
}
