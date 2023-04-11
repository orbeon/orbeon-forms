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

import org.orbeon.oxf.util.{ByteEncoding, SecureUtils}
import org.orbeon.oxf.xml.{DefaultFunctionSupport, RuntimeDependentFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.{DoubleValue, StringValue}
import org.orbeon.scaxon.Implicits._

import java.security.SecureRandom


class Digest extends DefaultFunctionSupport {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext

    SecureUtils.digestString(
      text      = stringArgument(0),
      algorithm = stringArgument(1),
      encoding  = stringArgumentOpt(2).map(ByteEncoding.fromString) getOrElse ByteEncoding.Base64
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
      encoding  = stringArgumentOpt(3).map(ByteEncoding.fromString) getOrElse ByteEncoding.Base64
    )
  }
}

class Random extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(c: XPathContext): DoubleValue =
    RandomSupport.evaluate(isSeed = true)
}

object RandomSupport {

  private lazy val secureRandom = new SecureRandom

  def evaluate(isSeed: Boolean): Double = secureRandom.nextDouble()

  // TODO: We should also support the "non-seeded" mode, but this seems to imply that, in order to keep a
  // reproducible sequence, we need to keep the state per containing document, and also to be able to serialize
  // the state to the dynamic state.
  //        final Expression seedExpression = (argument == null || argument.length == 0) ? null : argument[0];
  //        final boolean isSeed = (seedExpression != null) && argument[0].effectiveBooleanValue(c);
  //        final java.util.Random random = isSeed ? new java.util.Random() : new java.util.Random(0);
  //        return new StringValue(XMLUtils.removeScientificNotation(random.nextDouble()));
}