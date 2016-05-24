package org.dom4j.tree

trait WithData {
  private var _data: AnyRef = _
  def setData(data: AnyRef)= _data = data
  def getData = _data
}
