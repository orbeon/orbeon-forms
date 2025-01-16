/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.pipeline

import org.orbeon.oxf.common.Version
import org.orbeon.oxf.processor.pipeline.PipelineProcessor.PIPELINE_NAMESPACE_URI
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.om.{NamespaceConstant, NodeInfo}
import org.orbeon.saxon.sxpath.XPathEvaluator
import org.orbeon.saxon.*

// For backward compatibility
object PipelineFunctionLibrary extends PipelineFunctionLibrary

/**
 * Function library for XPath expressions in XPL.
 *
 * TODO:
 *
 * - add Java-accessible functions below to XXFormsIndependentFunctions
 * - then remove them from below
 * - then update XSLT stylesheets to use the p:* functions instead of direct Java calls
 */
trait PipelineNamespaces {
  val XFormsIndependentFunctionsNS : Seq[String] = Seq(PIPELINE_NAMESPACE_URI)
  val CryptoFunctionsNS            : Seq[String] = Seq(PIPELINE_NAMESPACE_URI)
  val PureUriFunctionsNS           : Seq[String] = Seq(PIPELINE_NAMESPACE_URI)
  val IndependentFunctionsNS       : Seq[String] = Seq(PIPELINE_NAMESPACE_URI)
  val XSLTFunctionsNS              : Seq[String] = Seq(NamespaceConstant.FN, PIPELINE_NAMESPACE_URI)
}

class PipelineFunctionLibrary extends OrbeonFunctionLibrary
  with PipelineNamespaces
  with CryptoFunctions
  with PureUriFunctions
  with IndependentFunctions
  with IndependentRequestFunctions
  with MapFunctions
  with ArrayFunctions
  with XSLTFunctions {

  // === Functions made accessible to XSLT/XPL via Java calls

  def newEvaluator(context: NodeInfo) = new XPathEvaluator(context.getConfiguration)

  def isPE: Boolean = Version.isPE
  def isPortlet: Boolean = "portlet" == NetUtils.getExternalContext.getRequest.getContainerType

  def setTitle(title: String): String = {
    NetUtils.getExternalContext.getResponse.setTitle(title)
    null
  }
}