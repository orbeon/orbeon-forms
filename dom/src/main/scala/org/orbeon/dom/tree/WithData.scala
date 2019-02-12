package org.orbeon.dom.tree

trait WithData {
  private var _data: AnyRef = _
  def setData(data: AnyRef): Unit = _data = data
  def getData: AnyRef = _data
}
