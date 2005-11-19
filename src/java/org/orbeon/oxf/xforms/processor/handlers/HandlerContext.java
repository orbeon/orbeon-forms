/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xml.ElementHandlerContext;
import org.orbeon.oxf.xml.DeferredContentHandler;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.processor.NewXFormsServer;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.Attributes;
import orbeon.apache.xml.utils.NamespaceSupport2;

/**
 *
 */
public class HandlerContext implements ElementHandlerContext {

    private DeferredContentHandler output;

    private PipelineContext pipelineContext;
    private XFormsContainingDocument containingDocument;
    private NewXFormsServer.XFormsState xformsState;
    private ExternalContext externalContext;

    private NamespaceSupport2 namespaceSupport;

    public HandlerContext(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, NewXFormsServer.XFormsState xformsState, ExternalContext externalContext, NamespaceSupport2 namespaceSupport, DeferredContentHandler output) {
        this.pipelineContext = pipelineContext;
        this.containingDocument = containingDocument;
        this.xformsState = xformsState;
        this.externalContext = externalContext;

        this.namespaceSupport = namespaceSupport;
        this.output = output;
    }

    public DeferredContentHandler getOutput() {
        return output;
    }

    public void setOutput(DeferredContentHandler output) {
        this.output = output;
    }

    public PipelineContext getPipelineContext() {
        return pipelineContext;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public NewXFormsServer.XFormsState getXFormsState() {
        return xformsState;
    }

    public ExternalContext getExternalContext() {
        return externalContext;
    }

    public NamespaceSupport2 getNamespaceSupport() {
        return namespaceSupport;
    }

    public String findXHTMLPrefix() {
        final String prefix = namespaceSupport.getPrefix(XMLConstants.XHTML_NAMESPACE_URI);
        if (prefix != null)
            return prefix;

        if (XMLConstants.XHTML_NAMESPACE_URI.equals(namespaceSupport.getURI(""))) {
            return "";
        }

        // TEMP: in this case, we should probably map our own prefix, or set
        // the default namespace and restore it on children elements
        throw new OXFException("No prefix found for HTML namespace");
//                return null;
    }

    public String findFormattingPrefix() {
        final String prefix = namespaceSupport.getPrefix(XMLConstants.OPS_FORMATTING_URI);
        if (prefix != null)
            return prefix;

        if (XMLConstants.OPS_FORMATTING_URI.equals(namespaceSupport.getURI(""))) {
            return "";
        }

        return null;
    }

    public String findNewPrefix() {
        int i = 0;
        while (namespaceSupport.getURI("p" + i) != null) {
            i++;
        }
        return "p" + i;
    }

    public String getEffectiveId(Attributes controlElementAttributes) {
        return controlElementAttributes.getValue("id") + getIdPostfix();
    }

    public String getIdPostfix() {
        // TODO
        return "";
    }

    public boolean isGenerateTemplate() {
        // TODO
        return false;
    }
}
