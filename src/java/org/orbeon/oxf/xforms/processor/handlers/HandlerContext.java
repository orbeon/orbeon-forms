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
package org.orbeon.oxf.xforms.processor.handlers;

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.UserAgent;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * Context used when converting XHTML+XForms into XHTML.
 */
public class HandlerContext {

    // Passed from constructor
    private final ElementHandlerController controller;
    private final PipelineContext pipelineContext;
    private final XFormsContainingDocument containingDocument;
    private final XFormsState encodedClientState;
    private final ExternalContext externalContext;

    // Computed during construction
    private final String[] documentOrder;
    private final String labelElementName;
    private final String hintElementName;
    private final String helpElementName;
    private final String alertElementName;

    public final boolean isNoscript;
    public final boolean isNewXHTMLLayout;

    // UA information
    private boolean processedUserAgent;
    private boolean isRenderingEngineTrident;
    private boolean isRenderingEngineIE6OrEarlier;

    // Context information
    private Stack<String> componentContextStack;
    private Stack<RepeatContext> repeatContextStack;

//    private static final int INDEX_INCREMENT = 100;
//    private int currentTabIndex = 0;

    public HandlerContext(ElementHandlerController controller, PipelineContext pipelineContext,
                          XFormsContainingDocument containingDocument, XFormsState encodedClientState, ExternalContext externalContext) {
        this.controller = controller;
        this.pipelineContext = pipelineContext;
        this.containingDocument = containingDocument;
        this.encodedClientState = encodedClientState;
        this.externalContext = externalContext;

        this.documentOrder = StringUtils.split(XFormsProperties.getOrder(containingDocument));
        this.labelElementName = XFormsProperties.getLabelElementName(containingDocument);
        this.hintElementName = XFormsProperties.getHintElementName(containingDocument);
        this.helpElementName = XFormsProperties.getHelpElementName(containingDocument);
        this.alertElementName = XFormsProperties.getAlertElementName(containingDocument);

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

//    public int nextTabIndex() {
//        // NIY
////        final Integer[] repeatIndexes = XFormsUtils.getEffectiveIdSuffixParts(getIdPostfix());
//
//        currentTabIndex += INDEX_INCREMENT;
//        return currentTabIndex;
//    }

    final public String[] getDocumentOrder() {
        return documentOrder;
    }

    public String getLabelElementName() {
        return labelElementName;
    }

    public String getHintElementName() {
        return hintElementName;
    }

    public String getHelpElementName() {
        return helpElementName;
    }

    public String getAlertElementName() {
        return alertElementName;
    }

//    final public boolean isRenderingEngineTrident() {
//        processedUserAgentIfNeeded();
//        return isRenderingEngineTrident;
//    }

    final public boolean isRenderingEngineIE6OrEarlier() {
        processedUserAgentIfNeeded();
        return isRenderingEngineIE6OrEarlier;
    }

    private void processedUserAgentIfNeeded() {
        if (!processedUserAgent) {
            final ExternalContext.Request request = externalContext.getRequest();
            isRenderingEngineIE6OrEarlier = UserAgent.isRenderingEngineIE6OrEarlier(request);
            isRenderingEngineTrident = isRenderingEngineIE6OrEarlier || UserAgent.isRenderingEngineTrident(request);
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

        final String existingFormattingPrefix = findFormattingPrefix();
        final boolean isNewPrefix = StringUtils.isEmpty(existingFormattingPrefix);

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

    public String getIdPrefix() {
        return (componentContextStack == null || componentContextStack.size() == 0) ? "" : componentContextStack.peek();
    }

    public void pushComponentContext(String prefixedId) {

        final String newIdPrefix = prefixedId + XFormsConstants.COMPONENT_SEPARATOR;

        if (componentContextStack == null)
            componentContextStack = new Stack<String>();
        componentContextStack.push(newIdPrefix);
    }

    public void popComponentContext() {
        componentContextStack.pop();
    }

    public String getIdPostfix() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return "";
        else
            return (repeatContextStack.peek()).getIdPostfix();
    }

    public boolean isTemplate() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return false;
        else
            return (repeatContextStack.peek()).isGenerateTemplate();
    }

    public boolean isRepeatSelected() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return false;
        else
            return (repeatContextStack.peek()).isRepeatSelected();
    }

    public int getCurrentIteration() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return 0;
        else
            return (repeatContextStack.peek()).getIteration();
    }

    public int countParentRepeats() {
        return (repeatContextStack == null) ? 0 : repeatContextStack.size();
    }

    public void pushRepeatContext(boolean generateTemplate, int iteration, boolean repeatSelected) {

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
            repeatContextStack = new Stack<RepeatContext>();
        repeatContextStack.push(new RepeatContext(generateTemplate, iteration, newIdPostfix, repeatSelected));
    }

    public void popRepeatContext() {
        repeatContextStack.pop();
    }

    private static class RepeatContext {
        private boolean generateTemplate;
        private int iteration;
        private String idPostfix;
        private boolean repeatSelected;

        public RepeatContext(boolean generateTemplate, int iteration, String idPostfix, boolean repeatSelected) {
            this.generateTemplate = generateTemplate;
            this.iteration = iteration;
            this.idPostfix = idPostfix;
            this.repeatSelected = repeatSelected;
        }

        public boolean isGenerateTemplate() {
            return generateTemplate;
        }

        public int getIteration() {
            return iteration;
        }

        public String getIdPostfix() {
            return idPostfix;
        }

        public boolean isRepeatSelected() {
            return repeatSelected;
        }
    }

    /**
     * Restore the handler state up to (but excluding) the given control.
     *
     * Used if the context is not used from the root of the control tree.
     *
     * This restores repeats and components state.
     *
     * @param control   control
     */
    public void restoreContext(XFormsControl control) {

        // Get ancestor controls
        final List<XFormsControl> controls = new ArrayList<XFormsControl>();
        {
            XFormsControl currentControl = control.getParent();
            while (currentControl != null) {
                controls.add(currentControl);
                currentControl = currentControl.getParent();
            }
            Collections.reverse(controls);
        }

        // Iterate from root to leaf
        for (final XFormsControl currentControl: controls) {
            if (currentControl instanceof XFormsRepeatIterationControl) {
                // Handle repeat
                final XFormsRepeatIterationControl repeatIterationControl = (XFormsRepeatIterationControl) currentControl;
                final XFormsRepeatControl repeatControl = (XFormsRepeatControl) repeatIterationControl.getParent();

                final boolean isTopLevelRepeat = countParentRepeats() == 0;
                final boolean isRepeatSelected = isRepeatSelected() || isTopLevelRepeat;
                final int currentRepeatIteration = repeatIterationControl.getIterationIndex();

                pushRepeatContext(false, currentRepeatIteration, isRepeatSelected || currentRepeatIteration == repeatControl.getIndex());
            } else if (currentControl instanceof XFormsComponentControl) {
                // Handle component
                pushComponentContext(currentControl.getPrefixedId());
            }
        }
    }
}
