package org.orbeon.dom

class DocumentException(message: String, throwable: Throwable) extends Exception(message, throwable) {
  def this(message: String)      = this(message, null)
  def this(throwable: Throwable) = this(throwable.getMessage, throwable)
}
