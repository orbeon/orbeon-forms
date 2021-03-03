package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.scalajs.dom.crypto.GlobalCrypto

import scala.scalajs.js.typedarray.{DataView, Int8Array}


trait CryptoFunctions extends OrbeonFunctionLibrary {
  @XPathFunction
  def random(isSeed: Boolean = true): Double =
    CryptoFunctions.random()
}


object CryptoFunctions {
  def random(): Double = {

    val ints = new Int8Array(8)
    GlobalCrypto.crypto.getRandomValues(ints)

    // https://stackoverflow.com/questions/34575635/cryptographically-secure-float
    ints(7) = 63
    ints(6) = (ints(6) | 0xf0).toByte

    new DataView(ints.buffer).getFloat64(0, littleEndian = true) - 1
  }
}