/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.pipeline.ast;

import org.dom4j.*;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.pipeline.foreach.AbstractForEachProcessor;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataElement;

import java.util.*;

public class ASTDocumentHandler implements ASTHandler {

    private Document document = new NonLazyUserDataDocument();
    private Element currentElement;
    private Stack href;

    public boolean startPipeline(ASTPipeline pipeline) {
        currentElement = new NonLazyUserDataElement(new QName("config", PipelineProcessor.PIPELINE_NAMESPACE));
        document.setRootElement(currentElement);
        return true;
    }

    public void endPipeline(ASTPipeline pipeline) {
    }

    public void param(ASTParam param) {
        Element paramElement = currentElement.addElement(new QName("param", PipelineProcessor.PIPELINE_NAMESPACE));
        paramElement.addAttribute("type", param.getType() == ASTParam.INPUT ? "input" : "output");
        paramElement.addAttribute("name", param.getName());
    }

    public boolean startProcessorCall(ASTProcessorCall processorCall) {
        currentElement = currentElement.addElement(new QName("processor", PipelineProcessor.PIPELINE_NAMESPACE));
        if (processorCall.getName() != null) {
            currentElement.addAttribute("name", processorCall.getName().getQualifiedName());
        } else if (processorCall.getURI() != null) {
            currentElement.addAttribute("uri", processorCall.getURI());
        } else {
            currentElement.addAttribute("class", processorCall.getProcessor().getClass().getName());
        }
        return true;
    }

    public void endProcessorCall(ASTProcessorCall processorCall) {
        currentElement = currentElement.getParent();
    }

    public boolean startInput(ASTInput input) {
        href = new Stack();
        return true;
    }

    public void endInput(ASTInput input) {
        Element inputElement = currentElement.addElement(new QName("input", PipelineProcessor.PIPELINE_NAMESPACE));
        inputElement.addAttribute("name", input.getName());
        if (!href.isEmpty())
            inputElement.addAttribute("href", (String) href.pop());
        if (input.getContent() != null) {
            if (input.getContent() instanceof Document) {
                inputElement.add(((Document) input.getContent()).getRootElement().createCopy());
            } else {
                inputElement.add((Node) input.getContent().clone());
            }
        }
    }

    public void output(ASTOutput output) {
        Element outputElement = currentElement.addElement(new QName("output", PipelineProcessor.PIPELINE_NAMESPACE));
        outputElement.addAttribute("name", output.getName());
        if (output.getRef() != null)
            outputElement.addAttribute("ref", output.getRef());
        if (output.getId() != null)
            outputElement.addAttribute("id", output.getId());
    }

    public boolean startHrefAggregate(ASTHrefAggregate hrefAggregate) {
        return true;
    }

    public void endHrefAggregate(ASTHrefAggregate hrefAggregate) {
        StringBuffer result = new StringBuffer("aggregate('" + hrefAggregate.getRoot() + "'");

        // Get arguments from stack
        List hrefs = new ArrayList();
        for (int i = 0; i < hrefAggregate.getHrefs().size(); i++)
            hrefs.add(href.pop());
        Collections.reverse(hrefs);

        //  Add arguments in correct order
        for (Iterator i = hrefs.iterator(); i.hasNext();) {
            result.append(", ");
            result.append((String) i.next());
        }

        href.push(result.toString());
    }

    public void hrefId(ASTHrefId hrefId) {
        href.push(AbstractForEachProcessor.FOR_EACH_CURRENT_INPUT.equals(hrefId.getId())
            ? "current()"
            : "#" + hrefId.getId());
    }

    public void hrefURL(ASTHrefURL hrefURL) {
        href.push(hrefURL.getURL());
    }

    public boolean startHrefXPointer(ASTHrefXPointer hrefXPointer) {
        return true;
    }

    public void endHrefXPointer(ASTHrefXPointer hrefXPointer) {
        String result = (String) href.pop() + "#xpointer(" + hrefXPointer.getXpath() + ")";
        href.push(result);
    }

    public boolean startChoose(ASTChoose choose) {
        href = new Stack();
        currentElement = currentElement.addElement(new QName("choose", PipelineProcessor.PIPELINE_NAMESPACE));
        return true;
    }

    public void endChoose(ASTChoose choose) {
        currentElement = currentElement.getParent();
    }

    public boolean startForEach(ASTForEach forEach) {
        href = new Stack();
        currentElement = currentElement.addElement(new QName("for-each", PipelineProcessor.PIPELINE_NAMESPACE));
        if (forEach.getId() != null)
            currentElement.addAttribute("id", forEach.getId());
        if (forEach.getRef() != null)
            currentElement.addAttribute("ref", forEach.getRef());
        currentElement.addAttribute("root", forEach.getRoot());
        return true;
    }

    public void endStartForEach(ASTForEach forEach) {
    }

    public void endForEach(ASTForEach forEach) {
        currentElement = currentElement.getParent();
    }

    public boolean startWhen(ASTWhen when) {
        if (currentElement.elements().isEmpty()) {
            currentElement.addAttribute("href", (String) href.pop());
        }
        if (when.getTest() == null) {
            currentElement = currentElement.addElement(new QName("otherwise", PipelineProcessor.PIPELINE_NAMESPACE));
        } else {
            currentElement = currentElement.addElement(new QName("when", PipelineProcessor.PIPELINE_NAMESPACE));
            currentElement.addAttribute("test", when.getTest());
        }
        return true;
    }

    public void endWhen(ASTWhen when) {
        currentElement = currentElement.getParent();
    }

    public Document getDocument() {
        return document;
    }
}
