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
package org.orbeon.oxf.xml;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.XMLFilterImpl;

import java.io.IOException;

/**
 * This is an SAX XML Reader that reads XML from a processor output.
 */
public class ProcessorOutputXMLReader extends XMLFilterImpl {

    private PipelineContext pipelineContext;
    private ProcessorOutput processorOutput;

    public ProcessorOutputXMLReader(PipelineContext pipelineContext, ProcessorOutput processorOutput) {
        this.pipelineContext = pipelineContext;
        this.processorOutput = processorOutput;
    }

    public void parse(InputSource input) throws IOException, SAXException {
        processorOutput.read(pipelineContext, getContentHandler());
    }

    public void setFeature(String name, boolean state) throws SAXNotRecognizedException, SAXNotSupportedException {
        // We allow these two features
        if (name.equals("http://xml.org/sax/features/namespaces") && state)
            return;
        if (name.equals("http://xml.org/sax/features/namespace-prefixes") && !state)
            return;

        // Otherwise delegate (this will throw)
        super.setFeature(name, state);
    }

    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        // We accept it, however we are unable at the moment to send comments to a lexical handler
        // E.g. Saxon 9 sets this property
        if (name.equals("http://xml.org/sax/properties/lexical-handler"))
            return;

        // Otherwise delegate (this will throw)
        super.setProperty(name, value);
    }
}
