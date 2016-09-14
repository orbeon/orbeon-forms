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

import org.orbeon.oxf.xforms.function.xxforms._
import PipelineProcessor.PIPELINE_NAMESPACE_URI
import org.orbeon.oxf.util.NetUtils
import org.orbeon.saxon.sxpath.XPathEvaluator
import org.orbeon.oxf.common.Version
import org.w3c.dom.Node
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.oxf.xforms.library._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.state.DynamicState
import org.orbeon.saxon.om.{NamespaceConstant, NodeInfo}

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
class PipelineFunctionLibrary extends {
    // Namespace the functions (we wish we had trait constructors!)
    val XFormsIndependentFunctionsNS  = Seq(PIPELINE_NAMESPACE_URI)
    val XXFormsIndependentFunctionsNS = Seq(PIPELINE_NAMESPACE_URI)
    val XSLTFunctionsNS               = Seq(NamespaceConstant.FN, PIPELINE_NAMESPACE_URI)
    val tryXFormsDocument             = false
}
    with OrbeonFunctionLibrary
    with XFormsIndependentFunctions
    with XXFormsIndependentFunctions
    with XSLTFunctions {

    // === Functions made accessible to XSLT via Java calls

    // Add these to XXFormsIndependentFunctions?
    def encodeXML(node: Node) = XFormsUtils.encodeXMLAsDOM(node)
    def decodeDynamicStateString(dynamicState: String) = DynamicState.apply(dynamicState).toXML // for unit tests only
  def decodeXML(encodedXML: String) = XFormsUtils.decodeXML(encodedXML, forceEncryption = true) // only used by `xforms-xml-submission.xpl`
    def newEvaluator(context: NodeInfo) = new XPathEvaluator(context.getConfiguration)
    def isPE = Version.isPE
    def isPortlet = "portlet" == NetUtils.getExternalContext.getRequest.getContainerType
    def setTitle(title: String): String = {
        NetUtils.getExternalContext.getResponse.setTitle(title)
        null
    }

    // These are already available in XXFormsIndependentFunctions
    def property(name: String) = XXFormsProperty.property(name)
    def propertiesStartsWith(name: String) = XXFormsPropertiesStartsWith.propertiesStartsWith(name)
    def rewriteServiceURI(uri: String, absolute: Boolean) = XXFormsRewriteServiceURI.rewriteServiceURI(uri, absolute)
    def rewriteResourceURI(uri: String, absolute: Boolean) = XXFormsRewriteResourceURI.rewriteResourceURI(uri, absolute)
}