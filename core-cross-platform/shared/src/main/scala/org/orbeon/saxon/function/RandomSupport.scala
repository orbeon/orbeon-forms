package org.orbeon.saxon.function


object RandomSupport {

  lazy val random = new java.util.Random

  def evaluate(isSeed: Boolean): Double = random.nextDouble

  // TODO: We should also support the "non-seeded" mode, but this seems to imply that, in order to keep a
  // reproducible sequence, we need to keep the state per containing document, and also to be able to serialize
  // the state to the dynamic state.
  //        final Expression seedExpression = (argument == null || argument.length == 0) ? null : argument[0];
  //        final boolean isSeed = (seedExpression != null) && argument[0].effectiveBooleanValue(c);
  //        final java.util.Random random = isSeed ? new java.util.Random() : new java.util.Random(0);
  //        return new StringValue(XMLUtils.removeScientificNotation(random.nextDouble()));
}