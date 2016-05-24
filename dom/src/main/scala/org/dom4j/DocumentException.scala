package org.dom4j

/**
 * `DocumentException` is a nested Throwable which may be thrown
 * during the processing of a DOM4J document.
 */
class DocumentException(message: String, throwable: Throwable) extends Exception(message, throwable) {
  def this(message: String)      = this(message, null)
  def this(throwable: Throwable) = this(throwable.getMessage, throwable)
}
