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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.converter.XHTMLRewrite;
import org.orbeon.oxf.util.ContentHandlerWriter;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDynamicControl;
import org.orbeon.oxf.xforms.processor.handlers.*;
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLBodyHandler;
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XHTMLElementHandler;
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XXFormsAttributeHandler;
import org.orbeon.oxf.xml.*;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ControlsComparator {

    private final XFormsContainingDocument containingDocument;
    private final Set<String> valueChangeControlIds;
    private final boolean isTestMode;

    private final AttributesImpl attributesImpl = new AttributesImpl();

    private XMLReceiverHelper ch;
    private XMLReceiverHelper tempCH;

    private final int fullUpdateThreshold;

    public ControlsComparator(XMLReceiverHelper ch, XFormsContainingDocument containingDocument,
                              Set<String> valueChangeControlIds, boolean isTestMode) {

        this.ch = ch;
        this.containingDocument = containingDocument;
        this.valueChangeControlIds = valueChangeControlIds;
        this.isTestMode = isTestMode;

        this.fullUpdateThreshold = XFormsProperties.getAjaxFullUpdateThreshold(containingDocument);
    }

    public boolean diff(List<XFormsControl> state1, List<XFormsControl> state2) {

        // Normalize (accept null or empty lists)
        if (state1 != null && state1.size() == 0)
            state1 = null;
        if (state2 != null && state2.size() == 0)
            state2 = null;

        // Trivial case
        if (state1 == null && state2 == null)
            return true;

        // Both lists must have the same size if present; state1 can be null
        if ((state1 != null && state2 != null && state1.size() != state2.size()) || (state2 == null)) {
            throw new IllegalStateException("Illegal state when comparing controls.");
        }

        final Iterator<XFormsControl> leftIterator = (state1 == null) ? null : state1.iterator();
        final Iterator<XFormsControl> rightIterator = (state2 == null) ? null : state2.iterator();
        final Iterator<XFormsControl> leadingIterator = (rightIterator != null) ? rightIterator : leftIterator;

        while (leadingIterator.hasNext()) {

            final XFormsControl control1 = (leftIterator == null) ? null : leftIterator.next();
            final XFormsControl control2 = (rightIterator == null) ? null : rightIterator.next();

            // Whether it is necessary to output information about this control because the control was previously non-existing
            // TODO: distinction between new iteration AND control just becoming relevant?
            final boolean isNewlyVisibleSubtree = control1 == null;

            // Full updates
            final SAXStore.Mark mark;
            final boolean isFullUpdateLevel;

            // 1: Check current control
            boolean mustDoFullUpdate;
            if (control2.hasStructuralChange()) {

                assert ! withinFullUpdate();

                // This control will need a full update
                // As of 2012-04-16, includes XXFormsDynamicControl and contained control with updated XBL bindings
                mustDoFullUpdate = true;

                mark = containingDocument.getStaticOps().getMark(control2.getPrefixedId());
                assert mark != null;
                isFullUpdateLevel = true;
                tempCH = ch;
            } else {
                // Handle xxf:update="full"
                mark = getUpdateFullMark(control2);
                isFullUpdateLevel = mark != null;
                if (isFullUpdateLevel) {
                    // Start buffering
                    tempCH = ch;
                    ch = new XMLReceiverHelper(new SAXStore());
                }


                // Don't send anything if nothing has changed, but we force a change for controls whose values changed in the request
                final boolean isValueChangeControl = valueChangeControlIds != null && valueChangeControlIds.contains(control2.getEffectiveId());
                if (control2.supportAjaxUpdates() && (!control2.equalsExternal(control1) || isValueChangeControl)) {
                    // Output the diff for this control between the old state and the new state
                    attributesImpl.clear();
                    control2.outputAjaxDiff(ch, control1, attributesImpl, isNewlyVisibleSubtree);
                }

                // Whether at this point we must do a full update
                mustDoFullUpdate = mustDoFullUpdate();
            }

            // 2: Check children unless we already know we must do a full update
            if (!mustDoFullUpdate) {
                foobar: if (control2 instanceof XFormsContainerControl) {

                    final XFormsContainerControl containerControl1 = (XFormsContainerControl) control1;
                    final XFormsContainerControl containerControl2 = (XFormsContainerControl) control2;

                    final List<XFormsControl> children1 = (containerControl1 == null) ? Collections.<XFormsControl>emptyList() : containerControl1.childrenJava();
                    final List<XFormsControl> children2 = containerControl2.childrenJava();

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
                    if (control2 instanceof XFormsRepeatControl && ! children1.isEmpty()) {

                        final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control2;

                        // Special case of repeat update

                        final int size1 = children1.size();
                        final int size2 = children2.size();

                        if (size1 == size2) {
                            // No add or remove of children
                            if (!diff(children1, children2)) {
                                mustDoFullUpdate = true;
                                break foobar;
                            }
                        } else if (size2 > size1) {
                            // Size has grown

                            // Copy template instructions
                            outputCopyRepeatTemplate(ch, repeatControl, size1 + 1, size2);

                            // Diff the common subset
                            if (!diff(children1, children2.subList(0, size1))) {
                                mustDoFullUpdate = true;
                                break foobar;
                            }

                            // Issue new values for new iterations
                            if (!diff(null, children2.subList(size1, size2))) {
                                mustDoFullUpdate = true;
                                break foobar;
                            }

                        } else if (size2 < size1) {
                            // Size has shrunk
                            outputDeleteRepeatTemplate(ch, control2, size1 - size2);

                            // Diff the remaining subset
                            if (!diff(children1.subList(0, size2), children2)) {
                                mustDoFullUpdate = true;
                                break foobar;
                            }
                        }

                    } else if (control2 instanceof XFormsRepeatControl && control1 == null) {

                        final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control2;

                        // Handle new sub-xf:repeat

                        // Copy template instructions
                        final int size2 = children2.size();
                        if (size2 > 1) {
                            outputCopyRepeatTemplate(ch, repeatControl, 2, size2);// don't copy the first template, which is already copied when the parent is copied
                        } else if (size2 == 1) {
                            // NOP, the client already has the template copied
                        } else if (size2 == 0) {
                            // Delete first template
                            outputDeleteRepeatTemplate(ch, control2, 1);
                        }

                        // Issue new values for the children
                        if (!diff(null, children2)) {
                            mustDoFullUpdate = true;
                            break foobar;
                        }

                    } else if (control2 instanceof XFormsRepeatControl && children1.isEmpty()) {

                        final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control2;

                        // Handle repeat growing from size 0 (case of instance replacement, for example)

                        // Copy template instructions
                        final int size2 = children2.size();
                        if (size2 > 0) {
                            outputCopyRepeatTemplate(ch, repeatControl, 1, size2);

                            // Issue new values for the children
                            if (!diff(null, children2)) {
                                mustDoFullUpdate = true;
                                break foobar;
                            }
                        }
                    } else {
                        // Other grouping controls
                        if (!diff(children1, children2)) {
                            mustDoFullUpdate = true;
                            break foobar;
                        }
                    }
                }
            }

            // Handle xxf:update="full"
            if (mustDoFullUpdate && !isFullUpdateLevel) {
                // Ancestor will process full update
                return false;
            } else if (isFullUpdateLevel) {
                if (mustDoFullUpdate) {
                    // Incremental updates did trigger full updates

                    // Restore output and discard incremental updates
                    ch = tempCH;
                    tempCH = null;

                    // Process full update
                    processFullUpdate(mark, control1, control2);
                } else {
                    // Incremental updates did not trigger full updates

                    // Write out incremental updates
                    try {
                        ((SAXStore) ch.getXmlReceiver()).replay(tempCH.getXmlReceiver());
                    } catch (SAXException e) {
                        throw new OXFException(e);
                    }

                    // Restore output
                    ch = tempCH;
                    tempCH = null;
                }
            }
        }

        return true;
    }

    private boolean withinFullUpdate() {
        return tempCH != null;
    }

    private boolean mustDoFullUpdate() {
        return withinFullUpdate() && ((SAXStore) ch.getXmlReceiver()).getAttributesCount() >= fullUpdateThreshold;
    }

    private SAXStore.Mark getUpdateFullMark(XFormsControl control) {
        // Conditions:
        //
        // - there is not already a full update in progress
        // - we are in span layout OR we are using XXFormsDynamicControl
        // - the control supports full Ajax updates
        // - there is a mark for the given control
        //
        if (! withinFullUpdate() && (control instanceof XXFormsDynamicControl) && control.supportFullAjaxUpdates()) {
            return containingDocument.getStaticOps().getMark(control.getPrefixedId());
        } else {
            return null;
        }
    }

    private void processFullUpdate(SAXStore.Mark mark, XFormsControl control1, XFormsControl control2) {
        try {

            // 1: Send differences for just this control if needed
            if (!control2.equalsExternal(control1)) {
                final boolean isNewlyVisibleSubtree = control1 == null;
                attributesImpl.clear();
                control2.outputAjaxDiff(ch, control1, attributesImpl, isNewlyVisibleSubtree);
            }

            // 2: Send difference for content of this control

            final ElementHandlerController controller = new ElementHandlerController();

            // Register handlers on controller
            XHTMLBodyHandler.registerHandlers(controller, containingDocument);
            {
                // Register a handler for AVTs on HTML elements
                // TODO: this should be obtained per document, but we only know about this in the extractor
                final boolean hostLanguageAVTs = XFormsProperties.isHostLanguageAVTs();
                if (hostLanguageAVTs) {
                    controller.registerHandler(XXFormsAttributeHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", XHTMLBodyHandler.ANY_MATCHER);
                    controller.registerHandler(XHTMLElementHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI);
                }

                // Swallow XForms elements that are unknown
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI);
                controller.registerHandler(NullHandler.class.getName(), XFormsConstants.XBL_NAMESPACE_URI);
            }

            // Create the output SAX pipeline:
            //
            // - perform URL rewriting
            // - serialize to String
            //
            // NOTE: we could possibly hook-up the standard epilogue here, which would:
            //
            // - perform URL rewriting
            // - apply the theme
            // - serialize
            //
            // But this would raise some issues:
            //
            // - epilogue must match on xhtml:* instead of xhtml:html
            // - themes must be modified to support XHTML fragments
            // - serialization must output here, not to the ExternalContext OutputStream
            //
            // So for now, perform simple steps here, and later this can be revisited.
            //
            final ExternalContext externalContext = NetUtils.getExternalContext();
            controller.setOutput(new DeferredXMLReceiverImpl(new XHTMLRewrite().getRewriteXMLReceiver(externalContext.getResponse(),
                    new HTMLFragmentSerializer(new ContentHandlerWriter(ch.getXmlReceiver()), true), true)));// NOTE: skip the root element

            // Create handler context
            final HandlerContext handlerContext = new HandlerContext(controller, containingDocument, externalContext, control2.getEffectiveId()) {
                @Override
                public String findXHTMLPrefix() {
                    // We know we serialize to plain HTML so unlike during initial page show, we don't need a particular prefix
                    return "";
                }
            };
            handlerContext.restoreContext(control2);
            controller.setElementHandlerContext(handlerContext);

            attributesImpl.clear();
            attributesImpl.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, XFormsUtils.namespaceId(containingDocument, control2.getEffectiveId()));
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "inner-html", attributesImpl);
            {
                // Replay into SAX pipeline
                controller.startDocument();
//                mark.replay(new SAXLoggerProcessor.DebugXMLReceiver(controller));
                mark.replay(controller);
                controller.endDocument();
            }
            ch.endElement();

        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    protected void outputDeleteRepeatTemplate(XMLReceiverHelper ch, XFormsControl xformsControl2, int count) {
        if (!isTestMode) {
            final String repeatControlId = xformsControl2.getEffectiveId();
            final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_SEPARATOR);
            final String templateId = (indexOfRepeatHierarchySeparator == -1) ? repeatControlId : repeatControlId.substring(0, indexOfRepeatHierarchySeparator);
            final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements",
                    new String[] { "id", XFormsUtils.namespaceId(containingDocument, templateId), "parent-indexes", parentIndexes, "count", "" + count });
        }
    }

    protected void outputCopyRepeatTemplate(XMLReceiverHelper ch, XFormsRepeatControl repeatControl, int startSuffix, int endSuffix) {
        if (!isTestMode) {
            final String repeatControlId = repeatControl.getEffectiveId();
            final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_SEPARATOR);
            final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "copy-repeat-template",
                    new String[] {
                            // Get prefixed id without suffix as templates are global
                            "id", XFormsUtils.namespaceId(containingDocument, repeatControl.getPrefixedId()),
                            "parent-indexes", parentIndexes,
                            "start-suffix", Integer.toString(startSuffix), "end-suffix", Integer.toString(endSuffix)
                    });
        }
    }
}
