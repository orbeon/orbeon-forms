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

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.Stack;

/**
 * Context used when converting XHTML+XForms into XHTML.
 */
public class HandlerContext {

    private final ElementHandlerController controller;
    private final PipelineContext pipelineContext;
    private final XFormsContainingDocument containingDocument;
    private final XFormsState encodedClientState;
    private final ExternalContext externalContext;
    private final String[] documentOrder;

    public final boolean isNoscript;
    public final boolean isNewXHTMLLayout;

    private boolean processedUserAgent;
    private boolean isRenderingEngineTrident;
    private boolean isRenderingEngineIE6OrEarlier;

    private static final int INDEX_INCREMENT = 100;
    private int currentTabIndex = 0;

    public HandlerContext(ElementHandlerController controller, PipelineContext pipelineContext,
                          XFormsContainingDocument containingDocument, XFormsState encodedClientState, ExternalContext externalContext) {
        this.controller = controller;
        this.pipelineContext = pipelineContext;
        this.containingDocument = containingDocument;
        this.encodedClientState = encodedClientState;
        this.externalContext = externalContext;
        this.documentOrder = StringUtils.split(XFormsProperties.getOrder(containingDocument));

        this.isNoscript = XFormsProperties.isNoscript(containingDocument);
        this.isNewXHTMLLayout = XFormsProperties.isNewXHTMLLayout(containingDocument);
    }

    final public ElementHandlerController getController() {
        return controller;
    }

    final public PipelineContext getPipelineContext() {
        return pipelineContext;
    }

    final public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    final public XFormsState getEncodedClientState() {
        return encodedClientState;
    }

    final public ExternalContext getExternalContext() {
        return externalContext;
    }

    public int nextTabIndex() {
        // NIY
//        final Integer[] repeatIndexes = XFormsUtils.getEffectiveIdSuffixParts(getIdPostfix());

        currentTabIndex += INDEX_INCREMENT;
        return currentTabIndex;
    }

    final public String[] getDocumentOrder() {
        return documentOrder;
    }

    final public boolean isRenderingEngineTrident() {
        processedUserAgentIfNeeded();
        return isRenderingEngineTrident;
    }

    final public boolean isRenderingEngineIE6OrEarlier() {
        processedUserAgentIfNeeded();
        return isRenderingEngineIE6OrEarlier;
    }

    private void processedUserAgentIfNeeded() {
        if (!processedUserAgent) {
            final ExternalContext.Request request = externalContext.getRequest();
            isRenderingEngineIE6OrEarlier = NetUtils.isRenderingEngineIE6OrEarlier(request);
            isRenderingEngineTrident = isRenderingEngineIE6OrEarlier ? true : NetUtils.isRenderingEngineIE6OrEarlier(request);
            processedUserAgent = true;
        }
    }

    final public boolean isNoScript() {
        return isNoscript;
    }

    final public boolean isNewXHTMLLayout() {
        return isNewXHTMLLayout;
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
        throw new ValidationException("No prefix found for HTML namespace", new LocationData(controller.getLocator()));
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

    public String findFormattingPrefixDeclare() throws SAXException {
        final String formattingPrefix;
        final boolean isNewPrefix;

        final String existingFormattingPrefix = findFormattingPrefix();
        if (existingFormattingPrefix == null || "".equals(existingFormattingPrefix)) {
            // No prefix is currently mapped
            formattingPrefix = findNewPrefix();
            isNewPrefix = true;
        } else {
            formattingPrefix = existingFormattingPrefix;
            isNewPrefix = false;
        }

        // Start mapping if needed
        if (isNewPrefix)
            getController().getOutput().startPrefixMapping(formattingPrefix, XMLConstants.OPS_FORMATTING_URI);

        return formattingPrefix;
    }

    public void findFormattingPrefixUndeclare(String formattingPrefix) throws SAXException {
        final boolean isNewPrefix;

        final String existingFormattingPrefix = findFormattingPrefix();
        if (existingFormattingPrefix == null || "".equals(existingFormattingPrefix)) {
            // No prefix is currently mapped
            isNewPrefix = true;
        } else {
            isNewPrefix = false;
        }

        // End mapping if needed
        if (isNewPrefix)
            getController().getOutput().endPrefixMapping(formattingPrefix);
    }

    public String findNewPrefix() {
        int i = 0;
        while (controller.getNamespaceSupport().getURI("p" + i) != null) {
            i++;
        }
        return "p" + i;
    }

    public String getId(Attributes controlElementAttributes) {
        return controlElementAttributes.getValue("id");
    }

    public String getEffectiveId(Attributes controlElementAttributes) {
        return getIdPrefix() + controlElementAttributes.getValue("id") + getIdPostfix();
    }

    /**
     * Return location data associated with the current SAX event.
     *
     * @return  LocationData, null if no Locator was found
     */
    public LocationData getLocationData() {
        final Locator locator = getController().getLocator();
        return (locator == null) ? null : new LocationData(locator);
    }

    private Stack componentContextStack; // Stack<String> of static component id prefixes

    public String getIdPrefix() {
        return (componentContextStack == null || componentContextStack.size() == 0) ? "" : (String) componentContextStack.peek();
    }

    public void pushComponentContext(String prefixedId) {

        final String newIdPrefix = prefixedId + XFormsConstants.COMPONENT_SEPARATOR;

        if (componentContextStack == null)
            componentContextStack = new Stack();
        componentContextStack.push(newIdPrefix);
    }

    public void popComponentContext() {
        componentContextStack.pop();
    }

    private Stack repeatContextStack;

    public String getIdPostfix() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return "";
        else
            return ((RepeatContext) repeatContextStack.peek()).getIdPostifx();
    }

    public boolean isTemplate() {
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
