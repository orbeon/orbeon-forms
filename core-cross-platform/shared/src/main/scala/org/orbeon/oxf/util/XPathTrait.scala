package org.orbeon.oxf.util


trait XPathTrait {

  // Marker for XPath function context
  trait FunctionContext

  // To report timing information
  type Reporter = (String, Long) => Unit


}
