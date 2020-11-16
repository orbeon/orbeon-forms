package org.orbeon.saxon.expr


// On the Scala.js side, this is just a marker trait. There will never be an instance of it.
trait PathMap {
  def setInvalidated(b: Boolean): Unit = ()
}

object PathMap {
  trait PathMapNodeSet
}
