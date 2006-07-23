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
package org.orbeon.oxf.xforms.function.xxforms;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * OPS call-xpl() function.
 */
public class XXFormsCallXPL extends XFormsFunction {

    private static Logger logger = LoggerFactory.createLogger(XXFormsCallXPL.class);

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        try {
            // Get XPL URL
            final URL xplURL;
            {
                Expression xplURIExpression = argument[0];
                //xplURL = new URL(((AnyURIValue) xplURIExpression.evaluateItem(xpathContext)).getStringValue());
                if (getSystemId() == null)
                    xplURL = URLFactory.createURL(xplURIExpression.evaluateAsString(xpathContext));
                else
                    xplURL = URLFactory.createURL(getSystemId(), xplURIExpression.evaluateAsString(xpathContext));
            }

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
            final List inputNodes = new ArrayList();
            {
                Expression inputDocumentsExpression = argument[2];
                SequenceIterator i = inputDocumentsExpression.iterate(xpathContext);

                Item currentItem;
                while ((currentItem = (Item) i.next()) != null) {
                    inputNodes.add(((NodeWrapper) currentItem).getUnderlyingNode());
                }
            }

            if (inputNames.size() != inputNodes.size())
                throw new OXFException("The length of sequence of input names (" + inputNames.size()
                        + ") must be equal to the length of the sequence of input nodes (" + inputNodes.size() + ").");//getDisplayName()

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

                    Iterator inputNodesIterator = inputNodes.iterator();
                    for (Iterator i = inputNames.iterator(); i.hasNext();) {
                        String inputName = (String) i.next();

                        Node inputNode = (Node) inputNodesIterator.next();

                        // For now we accept dom4j Document and Element
                        // TODO: must support Saxon's other format (e.g. result of doc() function or contructed trees)
                        Element rootElement;
                        if (inputNode instanceof Document)
                            rootElement = ((Document) inputNode).getRootElement();
                        else if (inputNode instanceof Element && inputNode.getParent() == null)
                            rootElement = (Element) inputNode;
                        else if (inputNode instanceof Element)
                            rootElement = Dom4jUtils.createDocumentCopyParentNamespaces((Element) inputNode).getRootElement();
                        else
                            throw new OXFException("Input node must be an instance of Document or Element");
                        processorDefinition.addInput(inputName, rootElement);
                    }
                }
                processor = InitUtils.createProcessor(processorDefinition);
            }

            // Try to obtain an existing PipelineContext, otherwise create a new one
            // PipelineContext should be found when this is called from controls. It is likely to
            // be missing when called from the model.
            final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
            PipelineContext pipelineContext = (staticContext != null) ? staticContext.getPipelineContext() : null;
            final boolean newPipelineContext = pipelineContext == null;
            if (newPipelineContext)
                pipelineContext = new PipelineContext();

            processor.reset(pipelineContext);

            try {
                if (outputNames.size() == 0) {
                    // Just run the processor
                    processor.start(pipelineContext);
                    if (newPipelineContext && !pipelineContext.isDestroyed())
                        pipelineContext.destroy(true);

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
                        results.add(new DocumentWrapper(Dom4jUtils.normalizeTextNodes(domSerializer.getDocument(pipelineContext)), null, new Configuration()));
                    }
                    if (newPipelineContext && !pipelineContext.isDestroyed())
                        pipelineContext.destroy(true);

                    return new ListIterator(results);
                }
            } catch (Exception e) {
                try {
                    if (newPipelineContext && !pipelineContext.isDestroyed())
                        pipelineContext.destroy(false);
                } catch (Exception f) {
                    logger.error("Exception while destroying context after exception", OXFException.getRootThrowable(f));
                }
                throw e;
            }
        } catch (XPathException e) {
            throw e;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
