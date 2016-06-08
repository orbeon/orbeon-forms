package org.orbeon.dom

// ORBEON TODO: Do we need this exception? It's unclear that it helps with anything. Only used by SAXReader.
class DocumentException(message: String, throwable: Throwable) extends Exception(message, throwable) {
  def this(message: String)      = this(message, null)
  def this(throwable: Throwable) = this(throwable.getMessage, throwable)
}
