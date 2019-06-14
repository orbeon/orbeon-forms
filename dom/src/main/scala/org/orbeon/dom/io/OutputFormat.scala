package org.orbeon.dom.io

object OutputFormat {
  private[io] val StandardIndent          = " " * 4
  private[io] val StandardEncoding        = "UTF-8"
  private[io] val LineSeparator           = "\n"
  private[io] val AttributeQuoteCharacter = '"'
}

case class OutputFormat(indent: Boolean, newlines: Boolean, trimText: Boolean) {
  def getIndent = if (indent) OutputFormat.StandardIndent else ""
}
