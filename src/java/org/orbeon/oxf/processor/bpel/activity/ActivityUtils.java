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
package org.orbeon.oxf.processor.bpel.activity;

import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.bpel.Variables;
import org.orbeon.oxf.processor.pipeline.ast.ASTHref;
import org.orbeon.oxf.processor.pipeline.ast.ASTHrefId;
import org.orbeon.oxf.processor.pipeline.ast.ASTHrefXPointer;
import org.orbeon.oxf.processor.pipeline.ast.ASTOutput;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.Iterator;
import java.util.List;

public class ActivityUtils {

    private static final Activity[] activities = {
        new DeclareVariables(),
        new Assign(),
        new Invoke(),
        new Switch(),
        new Receive(),
        new Reply()
    };

    /**
     * Handle a list of activities (invoke, assign, ...)
     */
    public static void activitiesToXPL(Variables variables, List statements, List elements) {

        // Execute each activity
        for (Iterator i = elements.iterator(); i.hasNext();) {
            Element activityElement = (Element) i.next();

            // Find implementation for this activity and execute
            boolean foundActivity = false;
            for (int j = 0; j < activities.length; j++) {
                Activity activity = activities[j];
                if (activity.getElementName().equals(activityElement.getName())) {
                    foundActivity = true;
                    activity.toXPL(variables, statements, activityElement);
                    break;
                }
            }

            // Error if no implementation found
            if (!foundActivity)
                throw new ValidationException("Unsupported activity '" + activityElement.getName() + "'",
                        (LocationData) activityElement.getData());
        }
    }

    /**
     * Creates an input that corresponds to the given query applied on the
     * given variable/part.
     */
    public static ASTHref getHref(Variables variables, String variable, String part, String query) {
        String id = variables.getCurrentIdForVariablePart(variable, part);
        ASTHref astHref = new ASTHrefId(new ASTOutput(null, id));
        if (query != null)
            astHref = new ASTHrefXPointer(astHref, query);
        return astHref;
    }
}
