package org.orbeon.oxf.xforms.library

import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.IndependentRequestFunctions
import org.orbeon.saxon.lib.NamespaceConstant
import org.orbeon.xforms.{Namespaces, XFormsNames}


object XFormsFunctionLibrary
  extends OrbeonFunctionLibrary
    with IndependentRequestFunctions
    with XFormsEnvFunctions
    with XFormsXXFormsEnvFunctions
    with CryptoFunctions {

  lazy val namespaces = List(
    Namespaces.XF        -> XFormsNames.XFORMS_SHORT_PREFIX,
    NamespaceConstant.FN -> "fn"
  )
}
