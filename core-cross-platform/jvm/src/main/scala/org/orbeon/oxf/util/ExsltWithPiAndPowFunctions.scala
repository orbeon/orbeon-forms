package org.orbeon.oxf.util

import org.orbeon.saxon.value.{DoubleValue, NumericValue}


// Otherwise Saxon doesn't find the functions
class ExsltWithPiAndPowFunctions extends org.orbeon.saxon.exslt.Math

object ExsltWithPiAndPowFunctions extends org.orbeon.saxon.exslt.Math {
  def pi(): Double = java.lang.Math.PI
  def pow(n: Double, e: NumericValue): NumericValue = org.orbeon.saxon.exslt.Math.power(new DoubleValue(n) ,e)
}
