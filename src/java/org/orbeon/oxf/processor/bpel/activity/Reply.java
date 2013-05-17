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
import org.orbeon.oxf.processor.pipeline.ast.ASTInput;
import org.orbeon.oxf.processor.pipeline.ast.ASTOutput;
import org.orbeon.oxf.processor.pipeline.ast.ASTParam;
import org.orbeon.oxf.processor.pipeline.ast.ASTProcessorCall;
import org.orbeon.oxf.xml.XMLConstants;

import java.util.Iterator;
import java.util.List;

public class Reply implements Activity {

    public String getElementName() {
        return "reply";
    }

    public void toXPL(Variables variables, List statements, Element element) {
        String variable = element.attributeValue("variable");
        variables.setOutputVariable(variable);
        for (Iterator i = variables.iterateVariablesParts(); i.hasNext();) {
            Variables.VariablePart variablePart = (Variables.VariablePart) i.next();
            if (variablePart.getVariable().equals(variable)) {
                ASTProcessorCall astProcessorCall = new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME); {
                    statements.add(astProcessorCall);
                    astProcessorCall.addInput(new ASTInput("data",
                            ActivityUtils.getHref(variables, variable, variablePart.getPart(), null)));
                    astProcessorCall.addOutput(new ASTOutput("data",
                            new ASTParam(ASTParam.OUTPUT, variablePart.getPart())));
                }
            }
        }
    }
}
