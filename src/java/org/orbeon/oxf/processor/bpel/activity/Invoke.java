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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorFactoryRegistry;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.bpel.Variables;
import org.orbeon.oxf.processor.pipeline.ast.ASTInput;
import org.orbeon.oxf.processor.pipeline.ast.ASTOutput;
import org.orbeon.oxf.processor.pipeline.ast.ASTProcessorCall;

import java.util.Iterator;
import java.util.List;

public class Invoke implements Activity {

    public String getElementName() {
        return "invoke";
    }

    public void toXPL(Variables variables, List statements, Element invokeElement) {

        // Get info from element
        String partnerLink = invokeElement.attributeValue("partnerLink");
        String inputVariable = invokeElement.attributeValue("inputVariable");
        String outputVariable = invokeElement.attributeValue("outputVariable");

        // Create a dummy processor so we can get the input and outputs
        String processorURI = "oxf/processor/" + partnerLink;
        Processor processor = ProcessorFactoryRegistry.lookup(processorURI).createInstance(new PipelineContext());

        ASTProcessorCall astProcessorCall = new ASTProcessorCall(processorURI); {
            // Processor inputs
            for (Iterator i = processor.getInputsInfo().iterator(); i.hasNext();) {
                ProcessorInputOutputInfo inputOutputInfo = (ProcessorInputOutputInfo) i.next();
                astProcessorCall.addInput(new ASTInput(inputOutputInfo.getName(), ActivityUtils.getHref(variables,
                        inputVariable, inputOutputInfo.getName(), null)));
            }

            // Processor outputs
            for (Iterator i = processor.getOutputsInfo().iterator(); i.hasNext();) {
                ProcessorInputOutputInfo inputOutputInfo = (ProcessorInputOutputInfo) i.next();
                astProcessorCall.addOutput(new ASTOutput(inputOutputInfo.getName(),
                        variables.getNewIdForVariablePart(outputVariable, inputOutputInfo.getName())));
            }

            statements.add(astProcessorCall);
        }
    }
}
