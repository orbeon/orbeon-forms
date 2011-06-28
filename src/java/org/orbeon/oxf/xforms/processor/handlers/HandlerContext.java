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
import org.orbeon.oxf.util.UserAgent;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.*;

import java.util.*;

/**
 * Context used when converting XHTML+XForms into XHTML.
 */
public class HandlerContext {

    // Passed from constructor
    private final ElementHandlerController controller;
    private final XFormsContainingDocument containingDocument;
    private final ExternalContext externalContext;
    private final String topLevelControlEffectiveId;

    // Computed during construction
    private final String[] documentOrder;
    private final String labelElementName;
    private final String hintElementName;
    private final String helpElementName;
    private final String alertElementName;

    public final boolean isNoscript;
    public final boolean isSpanHTMLLayout;

    // UA information
    private boolean processedUserAgent;
    private boolean isRenderingEngineTrident;
    private boolean isRenderingEngineIE6OrEarlier;

    // Context information
    private Stack<String> componentContextStack;
    private Stack<RepeatContext> repeatContextStack;
    private Stack<Boolean> caseContextStack;

//    private static final int INDEX_INCREMENT = 100;
//    private int currentTabIndex = 0;

    public HandlerContext(ElementHandlerController controller,
                          XFormsContainingDocument containingDocument, ExternalContext externalContext, String topLevelControlEffectiveId) {

        this.controller = controller;
        this.containingDocument = containingDocument;
        this.externalContext = externalContext;
        this.topLevelControlEffectiveId = topLevelControlEffectiveId;

        this.documentOrder = StringUtils.split(XFormsProperties.getOrder(containingDocument));
        this.labelElementName = XFormsProperties.getLabelElementName(containingDocument);
        this.hintElementName = XFormsProperties.getHintElementName(containingDocument);
        this.helpElementName = XFormsProperties.getHelpElementName(containingDocument);
        this.alertElementName = XFormsProperties.getAlertElementName(containingDocument);

        this.isNoscript = containingDocument.getStaticState().isNoscript();
        this.isSpanHTMLLayout = XFormsProperties.isSpanHTMLLayout(containingDocument);
    }

    final public ElementHandlerController getController() {
        return controller;
    }

    final public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
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

    final public boolean isSpanHTMLLayout() {
        return isSpanHTMLLayout;
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

    private String findFormattingPrefix() {
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
     * Return true iif the given control effective id is the same as the top-level control passed during construction.
     *
     * NOTE: This is used by the repeat handler to not output delimiters during full updates.
     *
     * @param effectiveId   control effective id
     * @return              true iif id matches the id passed during construction
     */
    public boolean isFullUpdateTopLevelControl(String effectiveId) {
        return effectiveId.equals(topLevelControlEffectiveId);
    }

    /**
     * Whether this is running during a full client update.
     *
     * @return  true iif so
     */
    public boolean isFullUpdate() {
        return topLevelControlEffectiveId != null;
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

    public void pushCaseContext(boolean visible) {
        if (caseContextStack == null)
            caseContextStack = new Stack<Boolean>();
        final boolean currentVisible = caseContextStack.size() == 0 ? true : caseContextStack.peek();
        caseContextStack.push(currentVisible && visible);
    }

    public void popCaseContext() {
        caseContextStack.pop();
    }

    public boolean getCaseVisibility() {
        if (caseContextStack == null || caseContextStack.size() == 0)
            return true;
        else
            return caseContextStack.peek();
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
