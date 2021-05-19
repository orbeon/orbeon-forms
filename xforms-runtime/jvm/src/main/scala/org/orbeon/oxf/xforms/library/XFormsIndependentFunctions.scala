package org.orbeon.oxf.xforms.library

import org.orbeon.oxf.xforms.function.XFormsParse
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.`type`.BuiltInAtomicType.STRING
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.StaticProperty.{ALLOWS_ZERO_OR_ONE, EXACTLY_ONE}


trait XFormsIndependentFunctions extends OrbeonFunctionLibrary {

  val XFormsIndependentFunctionsNS: Seq[String]

  Namespace(XFormsIndependentFunctionsNS) {

    Fun("parse", classOf[XFormsParse], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

  }
}
