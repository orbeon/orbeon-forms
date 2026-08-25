package org.orbeon.oxf.xforms

import scala.scalajs.js

object NormalizerPlatform {

  def normalizeNFKD(s: String): String =
    s.asInstanceOf[js.Dynamic].normalize("NFKD").asInstanceOf[String] // TODO: add to Scala.js
}
