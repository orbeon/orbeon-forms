package org.orbeon.oxf.fr.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.xbl.DateSupportJava


object FormRunnerInternalFunctionLibrary extends OrbeonFunctionLibrary {

  lazy val namespaces = List( "java:org.orbeon.xbl.DateSupportJava" -> "DateSupport")

  @XPathFunction(name = "serializeExternalValueJava")
  def serializeExternalValueJava(
    binding : om.Item,
    format  : String
  ): String =
    DateSupportJava.serializeExternalValueJava(binding, format)

  @XPathFunction(name = "deserializeExternalValueJava")
  def deserializeExternalValueJava(externalValue: String): String =
    DateSupportJava.deserializeExternalValueJava(externalValue)
}
