/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XPathUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RedirectProcessor extends ProcessorImpl {

    public static final String REDIRECT_SCHEMA_URI = "http://orbeon.org/oxf/redirect";


    public RedirectProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, REDIRECT_SCHEMA_URI));
    }

    public void start(PipelineContext context) {
        try {
            // Read the data stream entirely
            final Node node = readCacheInputAsDOM4J(context, INPUT_DATA);

            // Is this a server-side redirect?
            final String serverSideString = XPathUtils.selectStringValueNormalize(node, "redirect-url/server-side");
            final boolean isServerSide = "true".equals(serverSideString);

            // Is this going to exit the portal, if any?
            final String exitPortalString = XPathUtils.selectStringValueNormalize(node, "redirect-url/exit-portal");
            final boolean isExitPortal = "true".equals(exitPortalString);

            // Build parameters
            final String pathInfo = XPathUtils.selectStringValueNormalize(node, "normalize-space(redirect-url/path-info)");
            final Map<String, String[]> parameters = new HashMap<String, String[]>();

            for (Iterator i = XPathUtils.selectIterator(node, "redirect-url/parameters/parameter"); i.hasNext();) {
                final Node parameter = (Node) i.next();
                final String name = XPathUtils.selectStringValue(parameter, "name");
                final int valueCount = XPathUtils.selectIntegerValue(parameter, "count(value)");
                final String[] values = new String[valueCount];
                int valueIndex = 0;
                for (Iterator j = XPathUtils.selectIterator(parameter, "value"); j.hasNext(); valueIndex++) {
                    final Node value = (Node) j.next();
                    values[valueIndex] = XPathUtils.selectStringValue(value, ".");
                }
                parameters.put(name, values);
            }

            // Send the redirect
            final ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            final ExternalContext.Response response = externalContext.getResponse();

            // Don't rewrite the path if doing a server-side redirect, because the forward expects a URL without the servlet context
            final String redirectURL = isServerSide ? pathInfo : response.rewriteRenderURL(pathInfo);
            response.sendRedirect(redirectURL, parameters, isServerSide, isExitPortal);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
