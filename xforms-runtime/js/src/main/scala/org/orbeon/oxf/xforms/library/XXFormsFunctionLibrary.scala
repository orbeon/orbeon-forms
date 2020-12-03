package org.orbeon.oxf.xforms.library

import org.orbeon.saxon.IndependentRequestFunctions
import org.orbeon.saxon.functions.registry.BuiltInFunctionSet
import org.orbeon.xforms.{Namespaces, XFormsNames}

object XXFormsFunctionLibrary
  extends BuiltInFunctionSet
    with IndependentRequestFunctions {

  override def getNamespace: String = Namespaces.XXF
  override def getConventionalPrefix: String = XFormsNames.XXFORMS_SHORT_PREFIX
}
