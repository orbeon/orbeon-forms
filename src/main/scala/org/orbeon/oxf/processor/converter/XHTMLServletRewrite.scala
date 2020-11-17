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
package org.orbeon.oxf.processor.converter

import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.externalcontext.ServletURLRewriter
import org.orbeon.oxf.rewrite.Rewrite
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiver

/**
 * This rewriter always rewrites using ServletURLRewriter.
 */
class XHTMLServletRewrite extends XHTMLRewrite {
  override def createOutput(name: String): ProcessorOutput =
    addOutput(name, new CacheableTransformerOutputImpl(this, name) {
      def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

        val externalContext = NetUtils.getExternalContext

        val rewriter =
          Rewrite.getRewriteXMLReceiver(
            rewriter    = new ServletURLRewriter(externalContext.getRequest),
            xmlReceiver = xmlReceiver,
            fragment    = false,
            rewriteURI  = XHTML_NAMESPACE_URI
          )

        readInputAsSAX(pipelineContext, ProcessorImpl.INPUT_DATA, rewriter)
      }
    })
}