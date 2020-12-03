package org.orbeon.saxon

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.saxon.functions.registry.BuiltInFunctionSet


trait IndependentRequestFunctions extends BuiltInFunctionSet {

  override def getNamespace: String = super.getNamespace
  override def getConventionalPrefix: String = super.getConventionalPrefix

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
