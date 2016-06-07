package org.orbeon.dom.tree

trait WithData {
  private var _data: AnyRef = _
  def setData(data: AnyRef)= _data = data
  def getData = _data
}
