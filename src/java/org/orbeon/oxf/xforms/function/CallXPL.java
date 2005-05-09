/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.value.AnyURIValue;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.util.PipelineUtils;
import org.dom4j.QName;
import org.dom4j.Node;
import org.dom4j.Element;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Collections;

/**
 * OPS call-xpl() function.
 */
public class CallXPL extends XFormsFunction {


    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get XPL URL
        Expression xplURIExpression = argument[0];
        URL xplURL = null;
        //URL xplURL = ((URL) ((AnyURIValue) xplURIExpression.evaluateItem(xpathContext)).convertToJava(URL.class, xpathContext));

        // Get list of input names
        final List inputNames = new ArrayList();
        {
            Expression inputNamesExpression = argument[1];
            SequenceIterator i = inputNamesExpression.iterate(xpathContext);

            Item currentItem;
            while ((currentItem = (Item) i.next()) != null) {
                inputNames.add(currentItem.getStringValue());
            }
        }

        // Get list of input documents
        final List inputDocuments = new ArrayList();
        {
            Expression inputDocumentsExpression = argument[2];
            SequenceIterator i = inputDocumentsExpression.iterate(xpathContext);

            Item currentItem;
            while ((currentItem = (Item) i.next()) != null) {
                inputDocuments.add(((DocumentWrapper) currentItem).getUnderlyingNode());
            }
        }

        if (inputNames.size() != inputDocuments.size())
            throw new OXFException("The length of sequence of input names (" + inputNames.size()
                    + "must be equal to the length of the sequence of input documents (" + inputDocuments.size() + ").");//getDisplayName()

        // Get list of output names
        final List outputNames = new ArrayList();
        {
            Expression inputNamesExpression = argument[3];
            SequenceIterator i = inputNamesExpression.iterate(xpathContext);

            Item currentItem;
            while ((currentItem = (Item) i.next()) != null) {
                outputNames.add(currentItem.getStringValue());
            }
        }

        // Create processor definition and processor
        Processor processor;
        {
            ProcessorDefinition processorDefinition = new ProcessorDefinition();
            {
                processorDefinition.setName(new QName("pipeline", XMLConstants.OXF_PROCESSORS_NAMESPACE));
                processorDefinition.addInput("config", xplURL.toExternalForm());

                Iterator inputDocumentsIterator = inputDocuments.iterator();
                for (Iterator i = inputNames.iterator(); i.hasNext();) {
                    String inputName = (String) i.next();

                    Node inputNode = (Node) inputDocumentsIterator.next();
                    Element rootElement = inputNode.getDocument().getRootElement();
                    processorDefinition.addInput(inputName, rootElement);
                }
            }
            processor = InitUtils.createProcessor(processorDefinition);
        }

        // Try to obtain an existing PipelineContext
        final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        PipelineContext pipelineContext = (staticContext != null) ? staticContext.getPipelineContext() : null;
        if (pipelineContext == null)
            pipelineContext = new PipelineContext();
        try {
            //pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, staticContext.getExternalContext());

//            if (staticContext == null) {
//                StaticExternalContext.setStaticContext(new StaticExternalContext.StaticContext(staticContext.getExternalContext(), pipelineContext));
//            } else {
//
//            }

            processor.reset(pipelineContext);

            if (outputNames.size() == 0) {
                // Just run the processor
                processor.start(pipelineContext);

                return new ListIterator(Collections.EMPTY_LIST);
            } else {
                // Create all outputs to read
                List outputs = new ArrayList(outputNames.size());
                for (Iterator i = outputNames.iterator(); i.hasNext();) {
                    String outputName = (String) i.next();

                    ProcessorOutput output = processor.createOutput(outputName);
                    outputs.add(output);
                }

                // Connect all DOM serializers
                List domSerializers = new ArrayList(outputNames.size());
                for (Iterator i = outputs.iterator(); i.hasNext();) {
                    ProcessorOutput output = (ProcessorOutput) i.next();

                    DOMSerializer domSerializer = new DOMSerializer();
                    PipelineUtils.connect(processor, output.getName(), domSerializer, "data");
                    domSerializers.add(domSerializer);
                }

                // Read all outputs in sequence
                List results = new ArrayList(outputNames.size());
                for (Iterator i = domSerializers.iterator(); i.hasNext();) {
                    DOMSerializer domSerializer = (DOMSerializer) i.next();

                    domSerializer.start(pipelineContext);
                    results.add(new DocumentWrapper(domSerializer.getDocument(pipelineContext), null));
                }

                return new ListIterator(results);
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
