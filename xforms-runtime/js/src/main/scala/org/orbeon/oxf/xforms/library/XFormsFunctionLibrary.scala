package org.orbeon.oxf.xforms.library

import org.orbeon.saxon.IndependentRequestFunctions
import org.orbeon.saxon.functions.registry.BuiltInFunctionSet
import org.orbeon.xforms.{Namespaces, XFormsNames}

object XFormsFunctionLibrary
  extends BuiltInFunctionSet
    with IndependentRequestFunctions {

  override def getNamespace: String = Namespaces.XF
  override def getConventionalPrefix: String = XFormsNames.XFORMS_SHORT_PREFIX
}
