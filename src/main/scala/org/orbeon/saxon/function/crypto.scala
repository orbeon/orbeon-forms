/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.saxon.function

import org.orbeon.oxf.util.SecureUtils
import org.orbeon.oxf.xml.{DefaultFunctionSupport, RuntimeDependentFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.{DoubleValue, StringValue}
import org.orbeon.scaxon.Implicits._

class Digest extends DefaultFunctionSupport {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext

    SecureUtils.digestString(
      text      = stringArgument(0),
      algorithm = stringArgument(1),
      encoding  = stringArgumentOpt(2) getOrElse "base64"
    )
  }
}

class Hmac extends DefaultFunctionSupport {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext

    SecureUtils.hmacString(
      key       = stringArgument(0),
      text      = stringArgument(1),
      algorithm = stringArgument(2),
      encoding  = stringArgumentOpt(3) getOrElse "base64"
    )
  }
}

class Random extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(c: XPathContext): DoubleValue =
    RandomSupport.evaluate(isSeed = true)
}
