package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.function.RandomSupport


trait CryptoFunctions extends OrbeonFunctionLibrary {

  @XPathFunction
  def random(isSeed: Boolean = true): Double =
    RandomSupport.evaluate(isSeed = true)
}
