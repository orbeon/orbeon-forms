package org.orbeon.oxf.xforms

import java.text.Normalizer

object NormalizerPlatform {

  def normalizeNFKD(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKD)
}
