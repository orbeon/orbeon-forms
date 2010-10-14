///**
// * Copyright (C) 2010 Orbeon, Inc.
// *
// * This program is free software; you can redistribute it and/or modify it under the terms of the
// * GNU Lesser General Public License as published by the Free Software Foundation; either version
// * 2.1 of the License, or (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
// * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
// * See the GNU Lesser General Public License for more details.
// *
// * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
// */
//package org.orbeon.oxf.xforms.analysis.controls;
//
//import org.dom4j.Element;
//import org.dom4j.QName;
//import org.orbeon.oxf.util.PropertyContext;
//import org.orbeon.oxf.xforms.XFormsConstants;
//import org.orbeon.oxf.xforms.XFormsStaticState;
//import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
//import org.orbeon.oxf.xforms.xbl.XBLBindings;
//import org.orbeon.oxf.xml.ContentHandlerHelper;
//import org.orbeon.saxon.dom4j.DocumentWrapper;
//
//import java.util.*;
//
///**
// * Hold the static analysis for an XForms control.
// */
//public class ControlAnalysis extends ViewAnalysis {
//
//    public final int index;
//
//    private final LHHAAnalysis nestedLabel;
//    private final LHHAAnalysis nestedHelp;
//    private final LHHAAnalysis nestedHint;
//    private final LHHAAnalysis nestedAlert;
//
//    private LHHAAnalysis externalLabel;
//    private LHHAAnalysis externalHelp;
//    private LHHAAnalysis externalHint;
//    private LHHAAnalysis externalAlert;
//
//    private String classes;
//
//    public ControlAnalysis(PropertyContext propertyContext, XFormsStaticState staticState, DocumentWrapper controlsDocumentInfo,
//                           XBLBindings.Scope scope, Element element, int index, boolean isValueControl,
//                           ContainerAnalysis parentControlAnalysis, Map<String, SimpleAnalysis> inScopeVariables) {
//
//        super(staticState, scope, element, parentControlAnalysis, inScopeVariables, isValueControl);
//
//        this.index = index;
//
//        this.nestedLabel = findNestedLHHA(propertyContext, controlsDocumentInfo, XFormsConstants.LABEL_QNAME);
//        this.nestedHelp = findNestedLHHA(propertyContext, controlsDocumentInfo, XFormsConstants.HELP_QNAME);
//        this.nestedHint = findNestedLHHA(propertyContext, controlsDocumentInfo, XFormsConstants.HINT_QNAME);
//        this.nestedAlert = findNestedLHHA(propertyContext, controlsDocumentInfo, XFormsConstants.ALERT_QNAME);
//    }
//
//    // Constructor for root
//    protected ControlAnalysis(XFormsStaticState staticState, int index, XBLBindings.Scope scope) {
//        super(staticState, scope);
//        this.index = index;
//        this.nestedLabel = this.nestedHelp = this.nestedHint = this.nestedAlert = null;
//    }
//
//    private LHHAAnalysis findNestedLHHA(PropertyContext propertyContext, DocumentWrapper controlsDocumentInfo, QName qName) {
//        final Element e = findNestedLHHAElement(propertyContext, controlsDocumentInfo, qName);
//        return (e != null) ? new LHHAAnalysis(propertyContext, staticState(), scope(), getViewVariables(), controlsDocumentInfo, e, true) : null;
//    }
//
//    protected Element findNestedLHHAElement(PropertyContext propertyContext, DocumentWrapper controlsDocumentInfo, QName qName) {
//        return element().element(qName);
//    }
//
//    public void setExternalLHHA(PropertyContext propertyContext, DocumentWrapper controlsDocumentInfo, Element lhhaElement) {
//        assert lhhaElement != null;
//        final String name = lhhaElement.getName();
//        // TODO: check: getViewVariables() might not be right
//        if (XFormsConstants.LABEL_QNAME.getName().equals(name)) {
//            externalLabel = new LHHAAnalysis(propertyContext, staticState(), scope(), getViewVariables(), controlsDocumentInfo, lhhaElement, false);
//        } else if (XFormsConstants.HELP_QNAME.getName().equals(name)) {
//            externalHelp = new LHHAAnalysis(propertyContext, staticState(), scope(), getViewVariables(), controlsDocumentInfo, lhhaElement, false);
//        } else if (XFormsConstants.HINT_QNAME.getName().equals(name)) {
//            externalHint = new LHHAAnalysis(propertyContext, staticState(), scope(), getViewVariables(), controlsDocumentInfo, lhhaElement, false);
//        } else if (XFormsConstants.ALERT_QNAME.getName().equals(name)) {
//            externalAlert = new LHHAAnalysis(propertyContext, staticState(), scope(), getViewVariables(), controlsDocumentInfo, lhhaElement, false);
//        }
//    }
//
//    public LHHAAnalysis getLabel() {
//        return (nestedLabel != null) ? nestedLabel : externalLabel;
//    }
//
//    public LHHAAnalysis getHelp() {
//        return (nestedHelp != null) ? nestedHelp : externalHelp;
//    }
//
//    public LHHAAnalysis getHint() {
//        return (nestedHint != null) ? nestedHint : externalHint;
//    }
//
//    public LHHAAnalysis getAlert() {
//        return (nestedAlert != null) ? nestedAlert : externalAlert;
//    }
//
//    @Override
//    public XPathAnalysis computeValueAnalysis() {
//        if (element() != null && canHoldValue() && !element().getQName().equals(XFormsConstants.XXFORMS_ATTRIBUTE_QNAME)) {
//            return super.computeValueAnalysis();
//        } else {
//            return null;
//        }
//    }
//
//    public void addClasses(String classes) {
//        if (this.classes == null) {
//            // Set
//            this.classes = classes;
//        } else {
//            // Append
//            this.classes = this.classes + ' ' + classes;
//        }
//    }
//
//    public String getClasses() {
//        return classes;
//    }
//
//    public RepeatAnalysis getAncestorRepeat() {
//        SimpleAnalysis currentParent = parentAnalysis();
//        while (currentParent != null) {
//            if (currentParent instanceof RepeatAnalysis)
//                return (RepeatAnalysis) currentParent;
//            currentParent = currentParent.parentAnalysis();
//        }
//        return null;
//    }
//
//    public void toXML(PropertyContext propertyContext, ContentHandlerHelper helper) {
//        helper.startElement("control", new String[] {
//                "name", element().getName(),
//                "scope", scope().scopeId,
//                "prefixed-id", prefixedId(),
//                "model-prefixed-id", getModelPrefixedId(),
//                "binding", Boolean.toString(hasNodeBinding()),
//                "value", Boolean.toString(canHoldValue())
//        });
//
//        // Control binding and value analysis
//        if (getBindingAnalysis() != null && hasNodeBinding()) {// NOTE: for now there can be a binding analysis even if there is no binding on the control (hack to simplify determining which controls to update)
//            helper.startElement("binding");
//            getBindingAnalysis().toXML(propertyContext, helper);
//            helper.endElement();
//        }
//        if (getValueAnalysis() != null) {
//            helper.startElement("value");
//            getValueAnalysis().toXML(propertyContext, helper);
//            helper.endElement();
//        }
//
//        // LHHA analysis
//        final Collection<LHHAAnalysis> lhhaAnalysises = new ArrayList<LHHAAnalysis>();
//        if (getLabel() != null) lhhaAnalysises.add(getLabel());
//        if (getHelp() != null) lhhaAnalysises.add(getHelp());
//        if (getHint() != null) lhhaAnalysises.add(getHint());
//        if (getAlert() != null) lhhaAnalysises.add(getAlert());
//
//        for (final LHHAAnalysis analysis : lhhaAnalysises) {
//            helper.startElement(analysis.element.getName());
//            if (analysis.getBindingAnalysis() != null)
//                analysis.getBindingAnalysis().toXML(propertyContext, helper);
//            if (analysis.valueAnalysis != null)
//                analysis.valueAnalysis.toXML(propertyContext, helper);
//            helper.endElement();
//        }
//
//        helper.endElement();
//    }
//}