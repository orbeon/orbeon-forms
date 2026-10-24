package org.orbeon.xpath.formatter

/**
 * Tokens produced by the XPath 3.1 lexer.
 */
sealed trait XPathToken {
  def text: String
}

object XPathToken {

  case class Whitespace(text: String) extends XPathToken
  case class Comment(text: String) extends XPathToken

  // Literals
  case class StringLiteral(text: String, value: String, delimiter: Char) extends XPathToken
  case class IntegerLiteral(text: String) extends XPathToken
  case class DecimalLiteral(text: String) extends XPathToken
  case class DoubleLiteral(text: String) extends XPathToken

  // Variables and Names
  case class VarRef(name: String) extends XPathToken {
    def text: String = "$" + name
  }
  case class Name(text: String) extends XPathToken

  // Punctuation and Delimiters
  case object LParen extends XPathToken    { def text = "(" }
  case object RParen extends XPathToken    { def text = ")" }
  case object LBracket extends XPathToken  { def text = "[" }
  case object RBracket extends XPathToken  { def text = "]" }
  case object LBrace extends XPathToken    { def text = "{" }
  case object RBrace extends XPathToken    { def text = "}" }
  case object Comma extends XPathToken     { def text = "," }
  case object Colon extends XPathToken     { def text = ":" }
  case object ColonColon extends XPathToken { def text = "::" }
  case object Assign extends XPathToken    { def text = ":=" }
  case object Dot extends XPathToken       { def text = "." }
  case object DotDot extends XPathToken    { def text = ".." }
  case object Slash extends XPathToken     { def text = "/" }
  case object SlashSlash extends XPathToken { def text = "//" }
  case object At extends XPathToken        { def text = "@" }
  case object Question extends XPathToken  { def text = "?" }
  case object Bang extends XPathToken      { def text = "!" }
  case object Arrow extends XPathToken     { def text = "=>" }
  case object Pipe extends XPathToken      { def text = "|" }
  case object PipePipe extends XPathToken  { def text = "||" }
  case object Plus extends XPathToken      { def text = "+" }
  case object Minus extends XPathToken     { def text = "-" }
  case object Star extends XPathToken      { def text = "*" }
  case object Eq extends XPathToken        { def text = "=" }
  case object Ne extends XPathToken        { def text = "!=" }
  case object Lt extends XPathToken        { def text = "<" }
  case object Le extends XPathToken        { def text = "<=" }
  case object Gt extends XPathToken        { def text = ">" }
  case object Ge extends XPathToken        { def text = ">=" }
  case object Precedes extends XPathToken  { def text = "<<" }
  case object Follows extends XPathToken   { def text = ">>" }

  // Keywords
  case class Keyword(text: String) extends XPathToken

  case object EOF extends XPathToken       { def text = "" }
}
