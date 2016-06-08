package org.orbeon.dom.io

private object OutputFormat {
  val StandardIndent          = " " * 4
  val StandardEncoding        = "UTF-8"
  val LineSeparator           = "\n"
  val AttributeQuoteCharacter = '"'
}

case class OutputFormat(indent: Boolean, newlines: Boolean, trimText: Boolean) {
  def getIndent = if (indent) OutputFormat.StandardIndent else ""
}
