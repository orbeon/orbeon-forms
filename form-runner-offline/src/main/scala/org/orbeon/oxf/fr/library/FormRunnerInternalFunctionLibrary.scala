package org.orbeon.oxf.fr.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.xbl.DateSupportJava


object FormRunnerInternalFunctionLibrary extends OrbeonFunctionLibrary {

  lazy val namespaces = List("java:org.orbeon.oxf.fr.FormRunner" -> "frf")

  @XPathFunction(name = "sendError")
  def sendError(status: Int): Unit =
    FormRunner.sendError(status)

  @XPathFunction(name = "isAutosaveSupported")
  def isAutosaveSupported(app: String, form: String): Boolean =
    FormRunner.isAutosaveSupported(app, form)

  @XPathFunction(name = "xpathAllAuthorizedOperations")
  def xpathAllAuthorizedOperations(
    permissionsElement : om.NodeInfo,
    dataUsername       : String,
    dataGroupname      : String
  ): Iterable[String] =
    FormRunner.xpathAllAuthorizedOperations(permissionsElement, dataUsername, dataGroupname)
}


object FormRunnerDateSupportFunctionLibrary extends OrbeonFunctionLibrary {

  lazy val namespaces = List("java:org.orbeon.xbl.DateSupportJava" -> "DateSupport")

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
