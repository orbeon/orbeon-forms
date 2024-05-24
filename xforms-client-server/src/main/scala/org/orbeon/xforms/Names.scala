package org.orbeon.xforms

import org.orbeon.dom.{Namespace, QName}

// TODO: move to DOM? other?
object Names {
  val XsBoolean = QName("boolean", Namespace("", Namespaces.XS))
  val XfBoolean = QName("boolean", Namespace("", Namespaces.XF))

  val XsString = QName("string", Namespace("", Namespaces.XS))
  val XfString = QName("string", Namespace("", Namespaces.XF))
}
