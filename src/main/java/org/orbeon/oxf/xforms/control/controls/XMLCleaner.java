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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorFactory;
import org.orbeon.oxf.processor.ProcessorFactoryRegistry;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

public class XMLCleaner {
    public static org.dom4j.Document cleanXML(org.dom4j.Document doc, String stylesheetURL) {
        try {
            final org.dom4j.Element element = doc.getRootElement();
            final String systemId = Dom4jUtils.makeSystemId(element);
            // The date to clean
            final DOMGenerator dataToClean = new DOMGenerator(doc, "clean xml", DOMGenerator.ZeroValidity, systemId);
            // The stylesheet
            final URLGenerator stylesheetGenerator = new URLGenerator(stylesheetURL);
            // The transformation
            // Define the name of the processor (this is a QName)
            final QName processorName = new QName("xslt", XMLConstants.OXF_PROCESSORS_NAMESPACE);
            // Get a factory for this processor
            final ProcessorFactory processorFactory = ProcessorFactoryRegistry.lookup(processorName);
            if (processorFactory == null)
                throw new OXFException("Cannot find processor factory with name '"
                        + processorName.getNamespacePrefix() + ":" + processorName.getName() + "'");

            // Create processor
            final Processor xsltProcessor = processorFactory.createInstance();
            // Where the result goes
            final DOMSerializer transformationOutput = new DOMSerializer();

            // Connect
            PipelineUtils.connect(stylesheetGenerator, "data", xsltProcessor, "config");
            PipelineUtils.connect(dataToClean, "data", xsltProcessor, "data");
            PipelineUtils.connect(xsltProcessor, "data", transformationOutput, "data");

            // Run the pipeline
            // Candidate for Scala withPipelineContext
            final PipelineContext pipelineContext = new PipelineContext();
            boolean success = false;
            try {
                final org.dom4j.Document result = transformationOutput.runGetDocument(pipelineContext);
                success = true;
                return result;
            } finally {
                pipelineContext.destroy(success);
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
