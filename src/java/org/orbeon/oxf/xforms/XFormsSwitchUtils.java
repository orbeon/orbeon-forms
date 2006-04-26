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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.controls.ControlInfo;
import org.orbeon.oxf.xforms.controls.RepeatIterationInfo;
import org.orbeon.oxf.xforms.controls.RepeatControlInfo;
import org.dom4j.Node;
import org.dom4j.Element;
import org.dom4j.Attribute;

import java.util.Iterator;
import java.util.Map;
import java.util.List;

/**
 * Utilities related to xforms:switch.
 */
public class XFormsSwitchUtils {

    public static boolean prepareSwitches(PipelineContext pipelineContext, XFormsControls xformsControls) {
        // Store temporary switch information into appropriate nodes
        boolean found = false;
        for (Iterator i = xformsControls.getCurrentControlsState().getSwitchIdToSelectedCaseIdMap().entrySet().iterator(); i.hasNext();) {
            final Map.Entry entry = (Map.Entry) i.next();
            final String switchId = (String) entry.getKey();

//            System.out.println("xxx 1: switch id: " + switchId);

            if (switchId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1) {
                // This switch id may be affected by this insertion

//                System.out.println("xxx 1: has separator");

                final ControlInfo switchControlInfo = (ControlInfo) xformsControls.getObjectById(switchId);
                ControlInfo parent = switchControlInfo;
                while ((parent = parent.getParent()) != null) {
                    if (parent instanceof RepeatIterationInfo) {
                        // Found closest enclosing repeat iteration

                        final RepeatIterationInfo repeatIterationInfo = (RepeatIterationInfo) parent;
                        final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) repeatIterationInfo.getParent();

                        xformsControls.setBinding(pipelineContext, repeatControlInfo);
                        final List currentNodeset = xformsControls.getCurrentNodeset();

                        final Node node = (Node) currentNodeset.get(repeatIterationInfo.getIteration() - 1);
                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);

                        // Store an original case id instead of an effective case id
                        final String caseId = (String) entry.getValue();
                        instanceData.addSwitchIdToCaseId(switchControlInfo.getOriginalId(), caseId.substring(0, caseId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1)));

//                        System.out.println("xxx 1: adding case id " + switchControlInfo.getOriginalId() + " " + entry.getValue());

                        found = true;

                        break;
                    }
                }
            }
        }
        return found;
    }

    public static void prepareSwitches(PipelineContext pipelineContext, XFormsControls xformsControls, Element sourceElement, Element clonedElement) {
        final boolean found = prepareSwitches(pipelineContext, xformsControls);
        if (found) {
            // Propagate temporary switch information to new nodes
            copySwitchInfo(sourceElement, clonedElement);
        }
    }

    private static void copySwitchInfo(Element sourceElement, Element destElement) {

        final InstanceData sourceInstanceData = XFormsUtils.getLocalInstanceData(sourceElement);
        final InstanceData destInstanceData = XFormsUtils.getLocalInstanceData(destElement);
        destInstanceData.setSwitchIdsToCaseIds(sourceInstanceData.getSwitchIdsToCaseIds());

        // Recurse over attributes
        {
            final Iterator j = destElement.attributes().iterator();
            for (Iterator i = sourceElement.attributes().iterator(); i.hasNext();) {
                final Attribute sourceAttribute = (Attribute) i.next();
                final Attribute destAttribute = (Attribute) j.next();

                final InstanceData sourceAttributeInstanceData = XFormsUtils.getLocalInstanceData(sourceAttribute);
                final InstanceData destAttributeInstanceData = XFormsUtils.getLocalInstanceData(destAttribute);

                destAttributeInstanceData.setSwitchIdsToCaseIds(sourceAttributeInstanceData.getSwitchIdsToCaseIds());
            }
        }
        // Recurse over children elements
        {
            final Iterator j = destElement.elements().iterator();
            for (Iterator i = sourceElement.elements().iterator(); i.hasNext();) {
                final Element sourceChild = (Element) i.next();
                final Element destChild = (Element) j.next();
                copySwitchInfo(sourceChild, destChild);
            }
        }
    }

    public static void updateSwitches(PipelineContext pipelineContext, XFormsControls xformsControls) {
        for (Iterator i = xformsControls.getCurrentControlsState().getSwitchIdToSelectedCaseIdMap().entrySet().iterator(); i.hasNext();) {
            final Map.Entry entry = (Map.Entry) i.next();
            final String switchId = (String) entry.getKey();

//            System.out.println("xxx 2: switch id: " + switchId);

            if (switchId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1) {
                // This switch id may be affected by this insertion

//                System.out.println("xxx 2: has separator");

                final ControlInfo switchControlInfo = (ControlInfo) xformsControls.getObjectById(switchId);
                ControlInfo parent = switchControlInfo;
                while ((parent = parent.getParent()) != null) {
                    if (parent instanceof RepeatIterationInfo) {
                        // Found closest enclosing repeat iteration

                        final RepeatIterationInfo repeatIterationInfo = (RepeatIterationInfo) parent;
                        final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) repeatIterationInfo.getParent();

                        xformsControls.setBinding(pipelineContext, repeatControlInfo);
                        final List currentNodeset = xformsControls.getCurrentNodeset();

                        final Node node = (Node) currentNodeset.get(repeatIterationInfo.getIteration() - 1);
                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);

                        final String caseId = instanceData.getCasedIdForSwitchId(switchControlInfo.getOriginalId());

//                        System.out.println("xxx 2: found case id " + caseId);

                        if (caseId != null) {
                            // Set effective case id
                            final String effectiveCaseId = caseId + switchId.substring(switchId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1));
//                            System.out.println("xxx 2: setting case id " + effectiveCaseId);
                            entry.setValue(effectiveCaseId);
                        }

                        break;
                    }
                }
            }
        }
    }
}
