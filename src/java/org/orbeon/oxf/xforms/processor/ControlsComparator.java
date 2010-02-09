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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.processor.handlers.*;
import org.orbeon.oxf.xml.*;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.StringWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ControlsComparator {

    protected final PipelineContext pipelineContext;
    protected final ContentHandlerHelper ch;
    protected final XFormsContainingDocument containingDocument;
    protected final Set<String> valueChangeControlIds;
    protected final boolean isTestMode;

    public ControlsComparator(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsContainingDocument containingDocument,
                              Set<String> valueChangeControlIds, boolean isTestMode) {

        this.pipelineContext = pipelineContext;
        this.ch = ch;
        this.containingDocument = containingDocument;
        this.valueChangeControlIds = valueChangeControlIds;
        this.isTestMode = isTestMode;
    }

    public void diff(List<XFormsControl> state1, List<XFormsControl> state2) {

        // Normalize
        if (state1 != null && state1.size() == 0)
            state1 = null;
        if (state2 != null && state2.size() == 0)
            state2 = null;

        // Trivial case
        if (state1 == null && state2 == null)
            return;

        // Both lists must have the same size if present; state1 can be null
        if ((state1 != null && state2 != null && state1.size() != state2.size()) || (state2 == null)) {
            throw new IllegalStateException("Illegal state when comparing controls.");
        }

        final AttributesImpl attributesImpl = new AttributesImpl();
        final Iterator<XFormsControl> j = (state1 == null) ? null : state1.iterator();
        for (Object aState2 : state2) {
            final XFormsControl xformsControl1 = (state1 == null) ? null : j.next();
            final XFormsControl xformsControl2 = (XFormsControl) aState2;

            // XXX TEST innerHTML
            if (testInnerHTML(xformsControl1, xformsControl2, attributesImpl)) continue;

            // Whether it is necessary to output information about this control because the control was previously non-existing
            // TODO: distinction between new iteration AND control just becoming relevant
            final boolean isNewlyVisibleSubtree = xformsControl1 == null;

            // 1: Check current control

            // Don't send anything if nothing has changed, but we force a change for controls whose values changed in the request
            final boolean isValueChangeControl = valueChangeControlIds != null && valueChangeControlIds.contains(xformsControl2.getEffectiveId());
            if (xformsControl2.supportAjaxUpdates() && (!xformsControl2.equalsExternal(pipelineContext, xformsControl1) || isValueChangeControl)) {
                // Output the diff for this control between the old state and the new state
                attributesImpl.clear();
                xformsControl2.outputAjaxDiff(pipelineContext, ch, xformsControl1, attributesImpl, isNewlyVisibleSubtree);
            }

            // 2: Check children if any
            if (xformsControl2 instanceof XFormsContainerControl) {

                final XFormsContainerControl containerControl1 = (XFormsContainerControl) xformsControl1;
                final XFormsContainerControl containerControl2 = (XFormsContainerControl) xformsControl2;

                final List<XFormsControl> children1 = (containerControl1 == null) ? null : containerControl1.getChildren();
                final List<XFormsControl> children2 = (containerControl2.getChildren() == null) ? Collections.<XFormsControl>emptyList() : containerControl2.getChildren();

//                if (leadingControl instanceof XFormsContainerControl) {
//                    // Repeat update
//
//                    TODO: diff repeat nodesets
//
//                } else {
//                    // Other grouping controls
//                    diff(children1, children2);
//                }

                // Repeat grouping control
                if (xformsControl2 instanceof XFormsRepeatControl && children1 != null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Special case of repeat update

                    final int size1 = children1.size();
                    final int size2 = children2.size();

                    if (size1 == size2) {
                        // No add or remove of children
                        diff(children1, children2);
                    } else if (size2 > size1) {
                        // Size has grown

                        // Copy template instructions
                        outputCopyRepeatTemplate(ch, repeatControlInfo, size1 + 1, size2);

                        // Diff the common subset
                        diff(children1, children2.subList(0, size1));

                        // Issue new values for new iterations
                        diff(null, children2.subList(size1, size2));

                    } else if (size2 < size1) {
                        // Size has shrunk
                        outputDeleteRepeatTemplate(ch, xformsControl2, size1 - size2);

                        // Diff the remaining subset
                        diff(children1.subList(0, size2), children2);
                    }

                } else if (xformsControl2 instanceof XFormsRepeatControl && xformsControl1 == null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Handle new sub-xforms:repeat

                    // Copy template instructions
                    final int size2 = children2.size();
                    if (size2 > 1) {
                        outputCopyRepeatTemplate(ch, repeatControlInfo, 2, size2);// don't copy the first template, which is already copied when the parent is copied
                    } else if (size2 == 1) {
                        // NOP, the client already has the template copied
                    } else if (size2 == 0) {
                        // Delete first template
                        outputDeleteRepeatTemplate(ch, xformsControl2, 1);
                    }

                    // Issue new values for the children
                    diff(null, children2);

                } else if (xformsControl2 instanceof XFormsRepeatControl && children1 == null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Handle repeat growing from size 0 (case of instance replacement, for example)

                    // Copy template instructions
                    final int size2 = children2.size();
                    if (size2 > 0) {
                        outputCopyRepeatTemplate(ch, repeatControlInfo, 1, size2);

                        // Issue new values for the children
                        diff(null, children2);
                    }
                } else {
                    // Other grouping controls
                    diff(children1, children2);
                }
            }
        }
    }

    private boolean testInnerHTML(XFormsControl xformsControl1, XFormsControl xformsControl2, AttributesImpl attributesImpl) {
        if (xformsControl2 instanceof XFormsContainerControl && !(xformsControl2 instanceof XFormsPseudoControl)) {
            final SAXStore.Mark mark = containingDocument.getStaticState().getElementMark(xformsControl2.getPrefixedId());
            if (mark != null) {

                // Recursively check for differences
//                    final boolean hasDifferences = true;
                final boolean hasDifferences = !xformsControl2.equalsExternalRecurse(pipelineContext, xformsControl1);

                // If so, compute new fragment
                if (hasDifferences) {
                    try {
                        final ElementHandlerController controller = new ElementHandlerController();

                        // Register handlers on controller
                        XHTMLBodyHandler.registerHandlers(controller, containingDocument.getStaticState());

                        {
                            // Register a handler for AVTs on HTML elements
                            final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs(); // TODO: this should be obtained per document, but we only know about this in the extractor
                            if (hostLanguageAVTs) {
                                controller.registerHandler(XXFormsAttributeHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute");
                                controller.registerHandler(XHTMLElementHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI);
                            }

                            // Swallow XForms elements that are unknown
                            controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
                            controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI);
                            controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XBL_NAMESPACE_URI);
                        }

                        final StringWriter sw = new StringWriter();

                        // Set final output
                        controller.setOutput(new DeferredContentHandlerImpl(new HTMLFragmentSerializer(sw)));
                        // Set handler context
                        // TODO XXX must restore handler context state: prefixes, repeats, more?
                        controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, null, null));
                        controller.startDocument();
                        controller.startPrefixMapping("xhtml", XMLConstants.XHTML_NAMESPACE_URI);// TEMP HACK

                        // new SAXLoggerProcessor.DebugContentHandler()
                        containingDocument.getStaticState().getXHTMLDocument().replay(controller, mark);
                        controller.endDocument();

                        attributesImpl.clear();
                        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsControl2.getEffectiveId());
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "inner-html", attributesImpl);
                        ch.text(sw.toString());// TODO: StringWriter uses StringBuffer instead of StringBuilder...
//                            System.out.println(sw);
                        ch.endElement();

                    } catch (SAXException e) {
                        throw new OXFException(e);
                    }
                    // Skip regular updates
                    return true;
                }
            }
        }
        return false;
    }

    protected void outputDeleteRepeatTemplate(ContentHandlerHelper ch, XFormsControl xformsControl2, int count) {
        if (!isTestMode) {
            final String repeatControlId = xformsControl2.getEffectiveId();
            final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
            final String templateId = (indexOfRepeatHierarchySeparator == -1) ? repeatControlId : repeatControlId.substring(0, indexOfRepeatHierarchySeparator);
            final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements",
                    new String[] { "id", templateId, "parent-indexes", parentIndexes, "count", "" + count });
        }
    }

    protected void outputCopyRepeatTemplate(ContentHandlerHelper ch, XFormsRepeatControl repeatControlInfo, int startSuffix, int endSuffix) {
        if (!isTestMode) {
            final String repeatControlId = repeatControlInfo.getEffectiveId();
            final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
            final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "copy-repeat-template",
                    new String[] {
                            // Get prefixed id without suffix as templates are global
                            "id", repeatControlInfo.getPrefixedId(),
                            "parent-indexes", parentIndexes,
                            "start-suffix", Integer.toString(startSuffix), "end-suffix", Integer.toString(endSuffix)
                    });
        }
    }
}
