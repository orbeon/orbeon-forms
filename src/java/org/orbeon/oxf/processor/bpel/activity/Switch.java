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
import org.jaxen.expr.*;
import org.jaxen.expr.iter.IterableChildAxis;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.bpel.BPELConstants;
import org.orbeon.oxf.processor.bpel.Variables;
import org.orbeon.oxf.processor.bpel.JaxenXPathRewrite;
import org.orbeon.oxf.processor.pipeline.ast.*;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.saxpath.Axis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Switch implements Activity {

    public String getElementName() {
        return "switch";
    }

    public void toXPL(Variables variables, List statements, Element switchElement) {

        ASTChoose astChoose = new ASTChoose();
        statements.add(astChoose);

        // Create <p:when> for every case
        List caseInputs = new ArrayList();
        Variables[] caseVariables = new Variables[switchElement.elements().size()];
        Variables maxVariables = new Variables();
        Element[] caseElements =  (Element[]) switchElement.elements().toArray(new Element[switchElement.elements().size()]);
        for (int i = 0; i < caseElements.length; i++) {
            Element caseElement = caseElements[i];

            // Get condition and rewrite
            String condition = caseElement.attributeValue("condition");
            if (condition != null)
                condition = rewriteCondition(condition, (LocationData) caseElement.getData(),
                        Dom4jUtils.getNamespaceContextNoDefault(caseElement), caseInputs);

            // Create <p:when>
            ASTWhen astWhen = new ASTWhen(condition);
            Variables currentCaseVariables = (Variables) variables.clone();
            ActivityUtils.activitiesToXPL(currentCaseVariables, astWhen.getStatements(), caseElement.elements());
            caseVariables[i] = currentCaseVariables;
            maxVariables = maxVariables.max(currentCaseVariables);
            astChoose.addWhen(astWhen);
        }

        // Add identity processors to each <p:when>
        for (int i = 0; i < caseVariables.length; i++) {
            Variables currentCaseVariables = caseVariables[i];
            ASTWhen astWhen = (ASTWhen) astChoose.getWhen().get(i);
            Variables diff = maxVariables.minus(currentCaseVariables);
            for (Iterator j = diff.iterateVariablesParts(); j.hasNext();) {
                Variables.VariablePart variablePart = (Variables.VariablePart) j.next();
                String currentId = currentCaseVariables.getCurrentIdForVariablePart
                        (variablePart.getVariable(), variablePart.getPart());
                String newId = maxVariables.getCurrentIdForVariablePart
                        (variablePart.getVariable(), variablePart.getPart());
                ASTProcessorCall identity = new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME); {
                    identity.addInput(new ASTInput("data", new ASTHrefId(new ASTOutput(null, currentId))));
                    identity.addOutput(new ASTOutput("data", newId));
                }
                astWhen.getStatements().add(identity);
            }
        }

        // Update the variables
        variables.alias(maxVariables);

        // Connect input of choose
        ASTHrefAggregate astHrefAggregate = new ASTHrefAggregate(); {
            astHrefAggregate.setRoot("root");
            List hrefs = new ArrayList();
            astHrefAggregate.setHrefs(hrefs);
            for (Iterator i = caseInputs.iterator(); i.hasNext();) {
                CaseInput caseInput = (CaseInput) i.next();
                ASTHref astHref = ActivityUtils.getHref(variables, caseInput.variable, caseInput.part, caseInput.query);
                hrefs.add(new ASTHrefAggregate(caseInput.elementName, astHref));
            }
            astChoose.setHref(astHrefAggregate);
        }
    }

    private String rewriteCondition(String condition, final LocationData locationData,
                                    final Map namespaceContext, final List caseInputs) {

        return JaxenXPathRewrite.rewrite(condition, namespaceContext, BPELConstants.BPEL_NAMESPACE_URI,
                "getVariableData", 3, locationData, new JaxenXPathRewrite.Rewriter() {
                    public void rewrite(FunctionCallExpr expr) {
                        // Save parameters
                        CaseInput caseInput = new CaseInput();
                        caseInputs.add(caseInput);
                        caseInput.elementName = "bpws-" + caseInputs.size();
                        caseInput.variable = getArgument(expr, 0);
                        caseInput.part = getArgument(expr, 1);
                        caseInput.query = getArgument(expr, 2);

                        // Replace by identity(xpath)
                        expr.setPrefix(null);
                        expr.setFunctionName("identity");
                        expr.getParameters().clear();
                        DefaultAbsoluteLocationPath locationPath = new DefaultAbsoluteLocationPath(); {
                            locationPath.addStep(new DefaultNameStep(new IterableChildAxis(Axis.CHILD),
                                    null, "root", new PredicateSet()));
                            locationPath.addStep(new DefaultNameStep(new IterableChildAxis(Axis.CHILD),
                                    null, caseInput.elementName, new PredicateSet()));
                            locationPath.addStep(new DefaultNameStep(new IterableChildAxis(Axis.CHILD),
                                    null, "node()", new PredicateSet()));
                            expr.getParameters().add(locationPath);
                        }

                    }

                    private String getArgument(FunctionCallExpr expr, int position) {
                        Object argument = expr.getParameters().get(position);
                        if (argument instanceof LiteralExpr) {
                            return ((LiteralExpr) argument).getLiteral();
                        } else {
                            throw new ValidationException("Parameter number " + position + " is not a string literal",
                                    locationData);
                        }
                    }
                });
    }

    private static class CaseInput {
        public String elementName;
        public String variable;
        public String part;
        public String query;
    }
}
