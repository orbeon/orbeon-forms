package org.orbeon.oxf.fr.library

import org.orbeon.macros.XPathFunction
import org.orbeon.saxon.functions.registry.BuiltInFunctionSet
import org.orbeon.saxon.om
import org.orbeon.xbl.DateSupportJava


object FormRunnerInternalFunctionLibrary extends BuiltInFunctionSet {

  override def getNamespace          : String = "java:org.orbeon.oxf.fr.FormRunner"
  override def getConventionalPrefix : String = "frf"

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
