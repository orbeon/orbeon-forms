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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.Attributes;

import java.util.Stack;

/**
 *
 */
public class HandlerContext {

    private ElementHandlerController controller;
    private PipelineContext pipelineContext;
    private XFormsContainingDocument containingDocument;
    private XFormsServer.XFormsState xformsState;
    private ExternalContext externalContext;

    public HandlerContext(ElementHandlerController controller, PipelineContext pipelineContext, XFormsContainingDocument containingDocument, XFormsServer.XFormsState xformsState, ExternalContext externalContext) {
        this.controller = controller;
        this.pipelineContext = pipelineContext;
        this.containingDocument = containingDocument;
        this.xformsState = xformsState;
        this.externalContext = externalContext;
    }

    public ElementHandlerController getController() {
        return controller;
    }

    public PipelineContext getPipelineContext() {
        return pipelineContext;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public XFormsServer.XFormsState getXFormsState() {
        return xformsState;
    }

    public ExternalContext getExternalContext() {
        return externalContext;
    }

    public String findXHTMLPrefix() {
        final String prefix = controller.getNamespaceSupport().getPrefix(XMLConstants.XHTML_NAMESPACE_URI);
        if (prefix != null)
            return prefix;

        if (XMLConstants.XHTML_NAMESPACE_URI.equals(controller.getNamespaceSupport().getURI(""))) {
            return "";
        }

        // TEMP: in this case, we should probably map our own prefix, or set
        // the default namespace and restore it on children elements
        throw new OXFException("No prefix found for HTML namespace");
//                return null;
    }

    public String findFormattingPrefix() {
        final String prefix = controller.getNamespaceSupport().getPrefix(XMLConstants.OPS_FORMATTING_URI);
        if (prefix != null)
            return prefix;

        if (XMLConstants.OPS_FORMATTING_URI.equals(controller.getNamespaceSupport().getURI(""))) {
            return "";
        }

        return null;
    }

    public String findNewPrefix() {
        int i = 0;
        while (controller.getNamespaceSupport().getURI("p" + i) != null) {
            i++;
        }
        return "p" + i;
    }

    public String getEffectiveId(Attributes controlElementAttributes) {
        return controlElementAttributes.getValue("id") + getIdPostfix();
    }

    private Stack repeatContextStack;

    public String getIdPostfix() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return "";
        else
            return ((RepeatContext) repeatContextStack.peek()).getIdPostifx();
    }

    public boolean isGenerateTemplate() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return false;
        else
            return ((RepeatContext) repeatContextStack.peek()).isGenerateTemplate();
    }

    public boolean isRepeatSelected() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return false;
        else
            return ((RepeatContext) repeatContextStack.peek()).isRepeatSelected();
    }

    public boolean isTopLevelRepeat() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return false;
        else
            return ((RepeatContext) repeatContextStack.peek()).isTopLevelRepeat();
    }

    public int getCurrentIteration() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return 0;
        else
            return ((RepeatContext) repeatContextStack.peek()).getIteration();
    }

    public int countParentRepeats() {
        return (repeatContextStack == null) ? 0 : repeatContextStack.size();
    }

    public void pushRepeatContext(boolean generateTemplate, int iteration, boolean topLevelRepeat, boolean repeatSelected) {

        final String currentIdPostfix = getIdPostfix();
        final String newIdPostfix;
        if (generateTemplate) {
            // No postfix is added for templates
            newIdPostfix = "";
        } else {
            // Create postfix depending on whether we are appending to an existing postfix or not
            newIdPostfix = (currentIdPostfix.length() == 0)
                    ? "" + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + iteration
                    : currentIdPostfix + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2 + iteration;
        }

        if (repeatContextStack == null)
            repeatContextStack = new Stack();
        repeatContextStack.push(new RepeatContext(generateTemplate, iteration, newIdPostfix, topLevelRepeat, repeatSelected));
    }

    public void popRepeatContext() {
        repeatContextStack.pop();
    }

    private static class RepeatContext {
        private boolean generateTemplate;
        private int iteration;
        private String idPostifx;
        private boolean topLevelRepeat;
        private boolean repeatSelected;

        public RepeatContext(boolean generateTemplate, int iteration, String idPostifx, boolean topLevelRepeat, boolean repeatSelected) {
            this.generateTemplate = generateTemplate;
            this.iteration = iteration;
            this.idPostifx = idPostifx;
            this.topLevelRepeat = topLevelRepeat;
            this.repeatSelected = repeatSelected;
        }

        public boolean isGenerateTemplate() {
            return generateTemplate;
        }

        public int getIteration() {
            return iteration;
        }

        public String getIdPostifx() {
            return idPostifx;
        }

        public boolean isRepeatSelected() {
            return repeatSelected;
        }

        public boolean isTopLevelRepeat() {
            return topLevelRepeat;
        }
    }
}
