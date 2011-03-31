/**
 *  Copyright (C) 2007 Orbeon, Inc.
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

import org.dom4j.Document;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This class shows how you can call a processor and handle its inputs and outputs with the pipeline API.
 */
public class CallXSLT extends SimpleProcessor {

    public CallXSLT() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public void generateMyoutput(PipelineContext pipelineContext, XMLReceiver xmlReceiver) throws SAXException {

        // Define the name of the processor (this is a QName)
        final QName processorName = new QName("xslt", XMLConstants.OXF_PROCESSORS_NAMESPACE);

        // Get a factory for this processor
        final ProcessorFactory processorFactory = ProcessorFactoryRegistry.lookup(processorName);

        if (processorFactory == null)
            throw new OXFException("Cannot find processor factory with name '"
                    + processorName.getNamespacePrefix() + ":" + processorName.getName() + "'");

        // Create processor
        final Processor processor = processorFactory.createInstance();

        // Connect inputs (one from URL, the other one from a DOM)
        final URLGenerator urlGeneratorConfig = new URLGenerator("oxf:/apps/java-api/transform.xsl");
        PipelineUtils.connect(urlGeneratorConfig, "data", processor, "config");

        final Document dataInputDocument = readInputAsDOM4J(pipelineContext, "myinput");
        final DOMGenerator domGeneratorData = PipelineUtils.createDOMGenerator(dataInputDocument, "data input", DOMGenerator.ZeroValidity, DOMGenerator.DefaultContext);
        PipelineUtils.connect(domGeneratorData, "data", processor, "data");

        // Connect outputs
        final DOMSerializer domSerializerData = new DOMSerializer();
        PipelineUtils.connect(processor, "data", domSerializerData, "data");

        boolean success = false;
        final PipelineContext newPipelineContext = new PipelineContext(); // here we decide to use our own PipelineContext
        try {
            // Execute processor by running serializer
            domSerializerData.start(newPipelineContext);

            // Get result as a dom4j Document
            final Document result = domSerializerData.getDocument(newPipelineContext); // must use same PipelineContext as start()
            success = true;
        } finally {
            newPipelineContext.destroy(success);
        }

        // Serialize result to output
        TransformerUtils.writeDom4j(result, xmlReceiver);
    }
}