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
import org.orbeon.oxf.pipeline.api.{XMLReceiver, PipelineContext}
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.externalcontext.ServletURLRewriter

/**
 * This rewriter always rewrites using ServletURLRewriter.
 */
class XHTMLServletRewrite extends XHTMLRewrite {
    override def createOutput(name: String) =
        addOutput(name, new CacheableTransformerOutputImpl(this, name) {
            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
                val externalContext = NetUtils.getExternalContext
                val rewriter = getRewriteXMLReceiver(new ServletURLRewriter(externalContext.getRequest), xmlReceiver, false)

                readInputAsSAX(pipelineContext, ProcessorImpl.INPUT_DATA, rewriter)
            }
        })
}