/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.controls;

import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.common.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an xforms:select1 control.
 */
public class Select1ControlInfo extends ControlInfo {

    private List items;

    public Select1ControlInfo(XFormsContainingDocument containingDocument, ControlInfo parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
    }

    public void evaluateItemsets(PipelineContext pipelineContext) {

        // Find itemset element if any
        // TODO: Handle multiple itemsets, xforms:choice, and mixed xforms:item / xforms:itemset
        final Element itemsetElement = getElement().element(XFormsConstants.XFORMS_ITEMSET_QNAME);

        if (itemsetElement != null) {
            final XFormsControls xformsControls = containingDocument.getXFormsControls();
            xformsControls.pushBinding(pipelineContext, itemsetElement); // when entering this method, binding must be on control
            final XFormsControls.BindingContext currentBindingContext = xformsControls.getCurrentContext();

            //if (model == null || model == currentBindingContext.getModel()) { // it is possible to filter on a particular model
            items = new ArrayList();
            final List currentNodeSet = xformsControls.getCurrentNodeset();
            if (currentNodeSet != null) {
                for (int currentPosition = 1; currentPosition <= currentNodeSet.size(); currentPosition++) {

                    // Push "artificial" binding with just current node in nodeset
                    xformsControls.getContextStack().push(new XFormsControls.BindingContext(currentBindingContext.getModel(), xformsControls.getCurrentNodeset(), currentPosition, null, true, null));
                    {
                        // Handle children of xforms:itemset
                        final Element labelElement = itemsetElement.element(XFormsConstants.XFORMS_LABEL_QNAME);
                        if (labelElement == null)
                            throw new ValidationException("xforms:itemset element must contain one xforms:label element.", getLocationData());
                        xformsControls.pushBinding(pipelineContext, labelElement);
                        final String label = xformsControls.getCurrentSingleNodeValue();
                        xformsControls.popBinding();
                        final Element valueCopyElement;
                        {
                            final Element valueElement = itemsetElement.element(XFormsConstants.XFORMS_VALUE_QNAME);
                            valueCopyElement = (valueElement != null)
                                ? valueElement : itemsetElement.element(XFormsConstants.XFORMS_COPY_QNAME);
                        }
                        if (valueCopyElement == null)
                            throw new ValidationException("xforms:itemset element must contain one xforms:value or one xforms:copy element.", getLocationData());
                        xformsControls.pushBinding(pipelineContext, valueCopyElement);
                        final String value = xformsControls.getCurrentSingleNodeValue();;
                        // TODO: handle xforms:copy
                        if (value != null)
                            items.add(new XFormsControls.ItemsetInfo(getId(), label != null ? label : "", value)); // don't allow for null label

                        xformsControls.popBinding();
                    }
                    xformsControls.getContextStack().pop();
                }
            }
            xformsControls.popBinding();
        }
    }

    public List getItemset() {
        return items;
    }
}
