/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.pipeline;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.dom4j.Node;

import java.util.*;

public class StaticExternalContext {
    private static Map staticContextx = Collections.synchronizedMap(new HashMap());

    public static class StaticContext {
        private ExternalContext externalContext;
        private PipelineContext pipelineContext;

        public StaticContext(ExternalContext externalContext, PipelineContext pipelineContext) {
            this.externalContext = externalContext;
            this.pipelineContext = pipelineContext;
        }

        public ExternalContext getExternalContext() {
            return externalContext;
        }

        public PipelineContext getPipelineContext() {
            return pipelineContext;
        }
    }

    public static void setStaticContext(StaticContext staticContext) {
        List current = (List) staticContextx.get(Thread.currentThread());
        if (current == null)
            current = new ArrayList();
        current.add(staticContext);
        staticContextx.put(Thread.currentThread(), current);
    }

    public static void removeStaticContext() {
        List current = (List) staticContextx.get(Thread.currentThread());
        current.remove(current.size() - 1);
        if (current.size() == 0)
            staticContextx.remove(Thread.currentThread());
    }

    public static StaticContext getStaticContext() {
        List current = (List) staticContextx.get(Thread.currentThread());
        return (StaticContext) current.get(current.size() - 1);
    }

    public static String rewriteActionURL(String urlString) {
        return getStaticContext().getExternalContext().getResponse().rewriteActionURL(urlString);
    }

    public static String rewriteRenderURL(String urlString) {
        return getStaticContext().getExternalContext().getResponse().rewriteRenderURL(urlString);
    }

    public static String rewriteResourceURL(String urlString) {
        return rewriteResourceURL(urlString, false);
    }

    public static String rewriteResourceURL(String urlString, boolean absolute) {
        return getStaticContext().getExternalContext().getResponse().rewriteResourceURL(urlString, absolute);
    }

    public static String setTitle(String title) {
        getStaticContext().getExternalContext().getResponse().setTitle(title);
        return "";
    }

    public static String encodeXML(org.w3c.dom.Node node) {
        return XFormsUtils.encodeXMLAsDOM(getStaticContext().getPipelineContext(), node);
    }

//    public static org.w3c.dom.Node decodeXML(String encodedXML) {
//        return XFormsUtils.decodeXMLAsDOM(getStaticContext().getPipelineContext(), encodedXML);
//    }

    public static Node decodeXML(String encodedXML) {
        return XFormsUtils.decodeXML(getStaticContext().getPipelineContext(), encodedXML);
    }
}
