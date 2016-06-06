package org.dom4j.io

object OutputFormat {
  val StandardIndent          = "  "
  val StandardEncoding        = "UTF-8"
  def LineSeparator           = "\n"
  def AttributeQuoteCharacter = '"'
}

case class OutputFormat(indent: Boolean, newlines: Boolean, trimText: Boolean) {
  def getIndent = if (indent) OutputFormat.StandardIndent else ""
}
