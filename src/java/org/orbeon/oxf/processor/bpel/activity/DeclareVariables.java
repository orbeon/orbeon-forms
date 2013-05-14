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
import org.orbeon.oxf.processor.bpel.Variables;

import java.util.Iterator;
import java.util.List;

public class DeclareVariables implements Activity {
    public String getElementName() {
        return "variables";
    }

    public void toXPL(Variables variables, List statements, Element variablesElement) {
        for (Iterator i = variablesElement.elements().iterator(); i.hasNext();) {
            Element variableElement = (Element) i.next();
            variables.declareVariable(variableElement.attributeValue("name"));
        }
    }
}
