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
package org.orbeon.oxf.xml;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorOutput;

/**
 * This is an SAX XML Reader that reads XML from a processor output.
 */
public class ProcessorOutputXMLReader extends XMLReaderToReceiver {

    private final PipelineContext pipelineContext;
    private final ProcessorOutput processorOutput;

    public ProcessorOutputXMLReader(PipelineContext pipelineContext, ProcessorOutput processorOutput) {
        this.pipelineContext = pipelineContext;
        this.processorOutput = processorOutput;
    }

    @Override
    public void parse(String systemId) {
        processorOutput.read(pipelineContext, createXMLReceiver());
    }
}
