package org.dom4j.io

import scala.beans.{BeanProperty, BooleanBeanProperty}

private object OutputFormat {
  val StandardIndent   = "  "
  val StandardEncoding = "UTF-8"

}

/**
 * `OutputFormat` represents the format configuration used by
 * and its base classes to format the XML output.
 */
class OutputFormat extends Cloneable {

  /**
    * The default indent is no spaces (as original document)
    */
  private var indent: String = null
  def getIndent = indent

  /**
   * The default new line flag, set to do new lines only as in original
   * document
   */
  @BooleanBeanProperty
  var newlines: Boolean = false

  /**
    * should we preserve whitespace or not in text nodes?
   */
  @BooleanBeanProperty
  var trimText: Boolean = false

  def setIndent(doIndent: Boolean): Unit =
    this.indent =
      if (doIndent)
        OutputFormat.StandardIndent
      else
        null

  def setIndentSize(indentSize: Int): Unit =
    this.indent = " " * indentSize

  def getLineSeparator = "\n"
  def getAttributeQuoteCharacter: Char = '"'
}
