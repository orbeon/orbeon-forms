package org.orbeon.oxf.xforms.upload.api.java


trait FileScan2 {

  def bytesReceived(bytes: Array[Byte], offset: Int, length: Int): FileScanResult
  def complete(file: _root_.java.io.File): FileScanResult

  def abort(): Unit
}
