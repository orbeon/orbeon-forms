/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor.xforms.input.action;

import org.dom4j.Document;
import org.dom4j.Element;
import org.jaxen.FunctionContext;
import org.orbeon.oxf.common.OXFException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Insert implements Action {

    private static final String POSITION_BEFORE = "before";
    private static final String POSITION_AFTER = "after";

    private String nodeset;
    private String at;
    private String position;

    public void setParameters(Map parameters) {
        nodeset = (String) parameters.get("nodeset");
        at = (String) parameters.get("at");
        position = (String) parameters.get("position");
    }

    public void run(FunctionContext functionContext, Document instance) {

        // Get nodeset and element to duplicate
        List nodesetList = ActionUtils.getNodeset(functionContext, instance, nodeset);
        if (nodesetList.size() == 0)
            throw new OXFException("nodeset attribute '" + nodeset + "' in insert action must return a non-empty nodeset");
        Object lastNode = nodesetList.get(nodesetList.size() - 1);
        if (!(lastNode instanceof Element))
            throw new OXFException("last node in nodeset attribute from insert action must must be an element");
        Element elementToDuplicate = (Element) lastNode;

        // Determine where to insert the duplicated element
        int atValue = ActionUtils.getAtValue(functionContext, nodesetList, at);
        if (atValue == Integer.MAX_VALUE) {
            atValue = nodesetList.size() - 1;
            position = POSITION_AFTER;
        }

        // Locate position of atElement (element pointed by "at") among his siblings
        int atElementIndex = 0;
        List atElementSiblings = elementToDuplicate.getParent().elements();
        {
            Element atElement = (Element) nodesetList.get(atValue);
            boolean found = false;
            for (Iterator i = atElementSiblings.iterator(); i.hasNext();) {
                if (i.next() == atElement) {
                    found = true;
                    break;
                }
                atElementIndex++;
            }
            if (!found)
                throw new OXFException("Nodeset defined by '" + nodeset + "' is not homogeneous");
        }

        // Actually do the insertion
        atElementSiblings.add(POSITION_BEFORE.equals(position) ? atElementIndex : atElementIndex + 1,
                elementToDuplicate.clone());
    }
}
