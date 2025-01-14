package org.orbeon.saxon

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xml.OrbeonFunctionLibrary


trait IndependentRequestFunctions extends OrbeonFunctionLibrary {

  @XPathFunction
  def getRequestMethod: String =
    CoreCrossPlatformSupport.externalContext.getRequest.getMethod.entryName.toUpperCase

  @XPathFunction
  def getRequestPath: String =
    CoreCrossPlatformSupport.externalContext.getRequest.getRequestPath

//  @XPathFunction
//  def getRequestHeader(name: String, encoding: Option[String]): Iterable[String] = ???
//
//  @XPathFunction
//  def GetRequestParameter(name: String): Iterable[String] = ???

}
