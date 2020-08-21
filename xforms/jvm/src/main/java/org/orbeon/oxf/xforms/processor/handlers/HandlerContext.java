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

import org.apache.commons.lang3.StringUtils;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.xforms.PartAnalysis;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDynamicControl;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.xforms.Constants;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import scala.Tuple2;

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
    private final XFormsContainingDocument containingDocument;
    private final ExternalContext externalContext;
    private final String topLevelControlEffectiveId;

    // Computed during construction
    private final Tuple2<scala.collection.immutable.List<String>, scala.collection.immutable.List<String>> documentOrder;
    private final String labelElementName;
    private final String hintElementName;
    private final String helpElementName;
    private final String alertElementName;

    // Context information
    private final Stack<PartAnalysis> partAnalysisStack;
    private Stack<String> componentContextStack;
    private Stack<RepeatContext> repeatContextStack;
    private Stack<Boolean> caseContextStack;

    public HandlerContext(ElementHandlerController controller,
                          XFormsContainingDocument containingDocument, ExternalContext externalContext, String topLevelControlEffectiveId) {

        this.controller = controller;
        this.containingDocument = containingDocument;
        this.externalContext = externalContext;
        this.topLevelControlEffectiveId = topLevelControlEffectiveId;

        this.documentOrder = containingDocument.lhhacOrder();
        this.labelElementName = containingDocument.getLabelElementName();
        this.hintElementName = containingDocument.getHintElementName();
        this.helpElementName = containingDocument.getHelpElementName();
        this.alertElementName = containingDocument.getAlertElementName();

        // Top-level part is containing document
        this.partAnalysisStack = new Stack<>();
        this.partAnalysisStack.push(containingDocument.staticState().topLevelPart());
    }

    public void pushPartAnalysis(PartAnalysis partAnalysis) {
        partAnalysisStack.push(partAnalysis);
    }

    public void popPartAnalysis() {
        partAnalysisStack.pop();
    }

    public PartAnalysis getPartAnalysis() {
        return partAnalysisStack.peek();
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

    final public Tuple2<scala.collection.immutable.List<String>, scala.collection.immutable.List<String>> getDocumentOrder() {
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

    public String findXHTMLPrefix() {
        final String prefix = controller.namespaceContext().getPrefix(XMLConstants.XHTML_NAMESPACE_URI());
        if (prefix != null)
            return prefix;

        if (XMLConstants.XHTML_NAMESPACE_URI().equals(controller.namespaceContext().getURI(""))) {
            return "";
        }

        // TEMP: in this case, we should probably map our own prefix, or set
        // the default namespace and restore it on children elements
        throw new ValidationException("No prefix found for HTML namespace", LocationData.createIfPresent(controller.locator()));
    }

    private String findFormattingPrefix() {
        final String prefix = controller.namespaceContext().getPrefix(XMLConstants.OPS_FORMATTING_URI());
        if (prefix != null)
            return prefix;

        if (XMLConstants.OPS_FORMATTING_URI().equals(controller.namespaceContext().getURI(""))) {
            return "";
        }

        return null;
    }

    public String findFormattingPrefixDeclare() throws SAXException {
        final String formattingPrefix;
        final boolean isNewPrefix;

        final String existingFormattingPrefix = findFormattingPrefix();
        if (StringUtils.isEmpty(existingFormattingPrefix)) {
            // No prefix is currently mapped
            formattingPrefix = findNewPrefix();
            isNewPrefix = true;
        } else {
            formattingPrefix = existingFormattingPrefix;
            isNewPrefix = false;
        }

        // Start mapping if needed
        if (isNewPrefix)
            getController().getOutput().startPrefixMapping(formattingPrefix, XMLConstants.OPS_FORMATTING_URI());

        return formattingPrefix;
    }

    public void findFormattingPrefixUndeclare(String formattingPrefix) throws SAXException {

        final String existingFormattingPrefix = findFormattingPrefix();
        final boolean isNewPrefix = StringUtils.isEmpty(existingFormattingPrefix);

        // End mapping if needed
        if (isNewPrefix)
            getController().getOutput().endPrefixMapping(formattingPrefix);
    }

    private String findNewPrefix() {
        int i = 0;
        while (controller.namespaceContext().getURI("p" + i) != null) {
            i++;
        }
        return "p" + i;
    }

    public String getPrefixedId(Attributes controlElementAttributes) {
        final String id = controlElementAttributes.getValue("id");
        return (id != null) ? getIdPrefix() + id : null;
    }

    public String getEffectiveId(Attributes controlElementAttributes) {
        final String prefixedId = getPrefixedId(controlElementAttributes);
        return (prefixedId != null) ? prefixedId + getIdPostfix() : null;
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

    public boolean hasFullUpdateTopLevelControl() {
        return topLevelControlEffectiveId != null;
    }

    /**
     * Return location data associated with the current SAX event.
     *
     * @return  LocationData, null if no Locator was found
     */
    public LocationData getLocationData() {
        return LocationData.createIfPresent(getController().locator());
    }

    public String getIdPrefix() {
        return (componentContextStack == null || componentContextStack.size() == 0) ? "" : componentContextStack.peek();
    }

    public void pushComponentContext(String prefixedId) {

        final String newIdPrefix = prefixedId + Constants.ComponentSeparator();

        if (componentContextStack == null)
            componentContextStack = new Stack<>();
        componentContextStack.push(newIdPrefix);
    }

    public void popComponentContext() {
        componentContextStack.pop();
    }

    public void pushCaseContext(boolean visible) {
        if (caseContextStack == null)
            caseContextStack = new Stack<>();
        final boolean currentVisible = caseContextStack.size() == 0 ? true : caseContextStack.peek();
        caseContextStack.push(currentVisible && visible);
    }

    public void popCaseContext() {
        caseContextStack.pop();
    }

    public String getIdPostfix() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return "";
        else
            return (repeatContextStack.peek()).getIdPostfix();
    }

    public boolean isRepeatSelected() {
        if (repeatContextStack == null || repeatContextStack.size() == 0)
            return false;
        else
            return (repeatContextStack.peek()).isRepeatSelected();
    }

    public int countParentRepeats() {
        return (repeatContextStack == null) ? 0 : repeatContextStack.size();
    }

    public void pushRepeatContext(int iteration, boolean repeatSelected) {

        final String currentIdPostfix = getIdPostfix();

        // Create postfix depending on whether we are appending to an existing postfix or not
        final String newIdPostfix = (currentIdPostfix.length() == 0)
                ? Constants.RepeatSeparatorString() + iteration
                : currentIdPostfix + Constants.RepeatIndexSeparatorString() + iteration;

        if (repeatContextStack == null)
            repeatContextStack = new Stack<>();
        repeatContextStack.push(new RepeatContext(iteration, newIdPostfix, repeatSelected));
    }

    public void popRepeatContext() {
        repeatContextStack.pop();
    }

    private static class RepeatContext {
        private int iteration;
        private String idPostfix;
        private boolean repeatSelected;

        public RepeatContext(int iteration, String idPostfix, boolean repeatSelected) {
            this.iteration = iteration;
            this.idPostfix = idPostfix;
            this.repeatSelected = repeatSelected;
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
        final List<XFormsControl> controls = new ArrayList<>();
        {
            XFormsControl currentControl = control.parent();
            while (currentControl != null) {
                controls.add(currentControl);
                currentControl = currentControl.parent();
            }
            Collections.reverse(controls);
        }

        // Iterate from root to leaf
        for (final XFormsControl currentControl: controls) {
            if (currentControl instanceof XFormsRepeatIterationControl) {
                // Repeat
                final XFormsRepeatIterationControl repeatIterationControl = (XFormsRepeatIterationControl) currentControl;
                final XFormsRepeatControl repeatControl = repeatIterationControl.repeat();

                final boolean isTopLevelRepeat = countParentRepeats() == 0;
                final boolean isRepeatSelected = isRepeatSelected() || isTopLevelRepeat;
                final int currentRepeatIteration = repeatIterationControl.iterationIndex();

                pushRepeatContext(currentRepeatIteration, isRepeatSelected || currentRepeatIteration == repeatControl.getIndex());
            } else if (currentControl instanceof XFormsComponentControl) {
                // Component
                pushComponentContext(currentControl.getPrefixedId());
            } else if (currentControl instanceof XXFormsDynamicControl) {
                // Dynamic
                pushComponentContext(currentControl.getPrefixedId());
                pushPartAnalysis(((XXFormsDynamicControl) currentControl).nested().get().partAnalysis());
            } else if (currentControl instanceof XFormsCaseControl) {
                // Case (not used as of 2012-04-16)
                pushCaseContext(((XFormsCaseControl) currentControl).isCaseVisible());
            }
        }
    }
}
