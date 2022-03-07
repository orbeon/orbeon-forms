package org.orbeon.oxf.xforms.upload.api.java

import _root_.java.{util => ju}


trait FileScanProvider2 {

  def init(): Unit
  def destroy(): Unit

  def startStream(
    filename  : String,
    headers   : ju.Map[String, Array[String]],
    language  : String, // UI language
    extension : ju.Map[String, Any]
  ): FileScan2
}
