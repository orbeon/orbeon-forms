/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * NOTE: This processor depends on the Servlet API.
 */
public class ServletFilterGenerator extends ProcessorImpl {

    public static final String SERVLET_FILTER_NAMESPACE_URI = "http://www.orbeon.org/oxf/servlet-filter";
    public static final String FILTER_CHAIN = "filter-chain"; // used by ServletFilterGenerator and OPSServletFilter

    public ServletFilterGenerator() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(ServletFilterGenerator.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

                final FilterChain chain = (FilterChain) context.getAttribute(FILTER_CHAIN);

                ExternalContext externalContext = (ExternalContext) context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.EXTERNAL_CONTEXT);
                ServletRequest request = (ServletRequest) externalContext.getNativeRequest();
                ServletResponse response = (ServletResponse) externalContext.getNativeResponse();

                if (chain == null)
                    throw new OXFException("Filter chain not found. Make sure that ServletFilterGenerator is used only when ProcessorFilter is configured.");

                // Include the result as XML
                ServletResponseWrapper wrapper = new ServletResponseWrapper((HttpServletResponse) response);
                try {
                    chain.doFilter(request, wrapper);
                } catch (IOException e) {
                    throw new OXFException(e);
                } catch (ServletException e) {
                    throw new OXFException(e);
                }

                // Parse the result
                wrapper.parse(xmlReceiver);
            }
        };
        addOutput(name, output);
        return output;
    }
}
