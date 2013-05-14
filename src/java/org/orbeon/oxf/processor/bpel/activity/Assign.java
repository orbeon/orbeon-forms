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

import org.dom4j.*;
import org.orbeon.oxf.processor.bpel.BPELConstants;
import org.orbeon.oxf.processor.bpel.Variables;
import org.orbeon.oxf.processor.pipeline.ast.ASTInput;
import org.orbeon.oxf.processor.pipeline.ast.ASTOutput;
import org.orbeon.oxf.processor.pipeline.ast.ASTProcessorCall;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;

import java.util.List;

public class Assign implements Activity {

    public String getElementName() {
        return "assign";
    }

    public void toXPL(Variables variables, List statements, Element assignElement) {

        // Get info from element
        Element copyElement = assignElement.element(new QName("copy", BPELConstants.BPEL_NAMESPACE));
        Element fromElement = copyElement.element(new QName("from", BPELConstants.BPEL_NAMESPACE));
        Element toElement = copyElement.element(new QName("to", BPELConstants.BPEL_NAMESPACE));
        String toQuery = toElement.attributeValue("query");

        // Are we updating just a section of a variable part?
        if (toQuery == null) {

            // Handle this with an identity processor
            ASTProcessorCall astProcessorCall = new ASTProcessorCall(XMLConstants.IDENTITY_PROCESSOR_QNAME); {
                ASTInput newValueAstInput = newValueASTInput(variables, fromElement); {
                    newValueAstInput.setName("data");
                    astProcessorCall.addInput(newValueAstInput);
                }
                astProcessorCall.addOutput(new ASTOutput("data", variables.getNewIdForVariablePart
                        (toElement.attributeValue("variable"),
                         toElement.attributeValue("part"))));
                statements.add(astProcessorCall);
            }
        } else {

            // Handle this with an XUpdate processor
            ASTProcessorCall astProcessorCall = new ASTProcessorCall(XMLConstants.XUPDATE_PROCESSOR_QNAME); {
                ASTInput newValueAstInput = newValueASTInput(variables, fromElement); {
                    newValueAstInput.setName("update");
                    astProcessorCall.addInput(newValueAstInput);
                }
                astProcessorCall.addInput(new ASTInput("data", ActivityUtils.getHref(variables,
                        toElement.attributeValue("variable"),
                        toElement.attributeValue("part"),
                        null)));
                astProcessorCall.addInput(new ASTInput("config", createXUpdateConfig(toElement.attributeValue("query"))));
                astProcessorCall.addOutput(new ASTOutput("data", variables.getNewIdForVariablePart
                        (toElement.attributeValue("variable"),
                         toElement.attributeValue("part"))));
                statements.add(astProcessorCall);
            }
        }
    }

    private ASTInput newValueASTInput(Variables variables, Element fromElement) {
        if (fromElement.elements().size() == 0) {
            return new ASTInput(null, ActivityUtils.getHref(variables,
                            fromElement.attributeValue("variable"),
                            fromElement.attributeValue("part"),
                            fromElement.attributeValue("query")));
        } else {
            return new ASTInput(null, ((Element) fromElement.elements().get(0)).createCopy());
        }
    }

    /**
     * Creates an XUpdate configuration that looks like:
     *
     * <xu:modification>
     *     <xu:update select="query">
     *         <xu:copy-of select="doc('#update')/*"/>
     *     </xu:update>
     * </xu:modification>
     */
    private static Node createXUpdateConfig(String query) {
        Namespace xupdateNamespace = new Namespace("xu", "http://www.xmldb.org/xupdate");
        Document document = new NonLazyUserDataDocument(); {
            Element modificationsElement = document.addElement( new QName("modifications", xupdateNamespace) );
             {
                document.setRootElement(modificationsElement);
                Element updateElement = modificationsElement.addElement(new QName("update", xupdateNamespace)); {
                    updateElement.addAttribute("select", query);
                    Element copyOf = updateElement.addElement(new QName("copy-of", xupdateNamespace)); {
                        copyOf.addAttribute("select", "doc('#update')/*");
                    }
                }
            }
        }
        return document;
    }
}
