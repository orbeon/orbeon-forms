package org.orbeon.dom.tree

import scala.compiletime.uninitialized


trait WithData {
  private var _data: AnyRef = uninitialized
  def setData(data: AnyRef): Unit = _data = data
  def getData: AnyRef = _data
}
