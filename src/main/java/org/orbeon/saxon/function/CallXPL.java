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
package org.orbeon.saxon.function;

import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.dom.Node;
import org.orbeon.dom.QName;
import org.orbeon.dom.saxon.DocumentWrapper;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.FunctionSupportJava;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.XPathException;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * xxf:call-xpl() function.
 */
public class CallXPL extends FunctionSupportJava {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        try {
            // Get XPL URL
            final URL xplURL;
            {
                Expression xplURIExpression = argument[0];
                //xplURL = new URL(((AnyURIValue) xplURIExpression.evaluateItem(xpathContext)).getStringValue());
                if (getSystemId() == null)
                    xplURL = URLFactory.createURL(xplURIExpression.evaluateAsString(xpathContext).toString());
                else
                    xplURL = URLFactory.createURL(getSystemId(), xplURIExpression.evaluateAsString(xpathContext).toString());
            }

            // Get list of input names
            final List<String> inputNames = new ArrayList<String>();
            {
                final Expression inputNamesExpression = argument[1];
                final SequenceIterator i = inputNamesExpression.iterate(xpathContext);

                Item currentItem;
                while ((currentItem = i.next()) != null) {
                    inputNames.add(currentItem.getStringValue());
                }
            }

            // Get list of input documents
            final List<Item> inputNodeInfos = new ArrayList<Item>();
            {
                final Expression inputDocumentsExpression = argument[2];
                final SequenceIterator i = inputDocumentsExpression.iterate(xpathContext);

                Item currentItem;
                while ((currentItem = i.next()) != null) {
                    inputNodeInfos.add(currentItem);
                }
            }

            if (inputNames.size() != inputNodeInfos.size())
                throw new OXFException("The length of sequence of input names (" + inputNames.size()
                        + ") must be equal to the length of the sequence of input nodes (" + inputNodeInfos.size() + ").");//getDisplayName()

            // Get list of output names
            final List<String> outputNames = new ArrayList<String>();
            {
                final Expression inputNamesExpression = argument[3];
                final SequenceIterator i = inputNamesExpression.iterate(xpathContext);

                Item currentItem;
                while ((currentItem = i.next()) != null) {
                    outputNames.add(currentItem.getStringValue());
                }
            }

            // Create processor definition and processor
            Processor processor;
            {
                ProcessorDefinition processorDefinition = new ProcessorDefinition(QName.apply("pipeline", XPLConstants.OXF_PROCESSORS_NAMESPACE()));
                {
                    processorDefinition.addInput("config", xplURL.toExternalForm());

                    Iterator inputNodesIterator = inputNodeInfos.iterator();
                    for (final String inputName: inputNames) {
                        final NodeInfo inputNodeInfo = (NodeInfo) inputNodesIterator.next();

                        if (!(inputNodeInfo.getNodeKind() == org.w3c.dom.Node.ELEMENT_NODE || inputNodeInfo.getNodeKind() == org.w3c.dom.Node.DOCUMENT_NODE))
                            throw new OXFException("Input node must be a document or element for input name: " + inputName);

                        // TODO: We should be able to just pass inputNodeInfo to addInput() and avoid the conversions, but that doesn't work!

                        if (inputNodeInfo instanceof VirtualNode) {
                            // Get reference to dom4j node

                            final Element inputElement;
                            final Node inputNode = (Node) ((VirtualNode) inputNodeInfo).getUnderlyingNode();

                            if (inputNode instanceof Document)
                                inputElement = ((Document) inputNode).getRootElement();
                            else if (inputNode instanceof Element && inputNode.getParent() == null)
                                inputElement = (Element) inputNode;
                            else if (inputNode instanceof Element)
                                inputElement = Dom4jUtils.createDocumentCopyParentNamespaces((Element) inputNode).getRootElement();
                            else
                                throw new OXFException("Input node must be a document or element for input name: " + inputName);

                            processorDefinition.addInput(inputName, inputElement);
                        } else {
                            // Copy to dom4j

//                            final DocumentInfo inputDocumentInfo = TransformerUtils.readTinyTree(inputNodeInfo);
//                            processorDefinition.addInput(inputName, inputDocumentInfo);

                            final Document inputDocument = TransformerUtils.tinyTreeToDom4j(inputNodeInfo);
                            processorDefinition.addInput(inputName, inputDocument.getRootElement());
                        }
                    }
                }
                processor = InitUtils.createProcessor(processorDefinition);
            }

            final PipelineContext pipelineContext = PipelineContext.get();
            processor.reset(pipelineContext);

            if (outputNames.size() == 0) {
                // Just run the processor
                processor.start(pipelineContext);
                return EmptyIterator.getInstance();
            } else {
                // Create all outputs to read
                List<ProcessorOutput> outputs = new ArrayList<ProcessorOutput>(outputNames.size());
                for (String outputName: outputNames) {
                    ProcessorOutput output = processor.createOutput(outputName);
                    outputs.add(output);
                }

                // Connect all DOM serializers
                List<DOMSerializer> domSerializers = new ArrayList<DOMSerializer>(outputNames.size());
                for (ProcessorOutput output: outputs) {
                    DOMSerializer domSerializer = new DOMSerializer();
                    PipelineUtils.connect(processor, output.getName(), domSerializer, "data");
                    domSerializers.add(domSerializer);
                }

                // Read all outputs in sequence
                List<DocumentWrapper> results = new ArrayList<DocumentWrapper>(outputNames.size());
                for (DOMSerializer domSerializer: domSerializers) {
                    results.add(new DocumentWrapper((Document) ProcessorSupport.normalizeTextNodesJava(domSerializer.runGetDocument(pipelineContext)), null,
                            xpathContext.getConfiguration()));
                }
                return new ListIterator(results);
            }
        } catch (XPathException e) {
            throw e;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
