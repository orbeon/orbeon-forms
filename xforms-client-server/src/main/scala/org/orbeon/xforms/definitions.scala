package org.orbeon.xforms


sealed trait HeadElement
object HeadElement {
  case class Reference(src: String) extends HeadElement
  case class Inline(text: String)   extends HeadElement
}
