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
package org.orbeon.oxf.util;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.XMLConstants;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.w3c.dom.Node;

public class PipelineUtils {

    public static void connect(Processor producer, String outputName, Processor consumer, String inputName) {
        ProcessorOutput output = producer.createOutput(outputName);
        ProcessorInput input = consumer.createInput(inputName);
        output.setInput(input);
        input.setOutput(output);
    }

    public static Processor createDOMGenerator(Node config, Object validity) {
        DOMGenerator domGenerator = new DOMGenerator(config, validity);
        domGenerator.setId("N/A");
        domGenerator.setName(XMLConstants.DOM_GENERATOR_PROCESSOR_QNAME);
        return domGenerator;
    }

    public static Processor createDOMGenerator(org.dom4j.Node config, Object validity) {
        DOMGenerator domGenerator = new DOMGenerator(config, validity);
        domGenerator.setId("N/A");
        domGenerator.setName(XMLConstants.DOM_GENERATOR_PROCESSOR_QNAME);
        return domGenerator;
    }

    public static Processor createURLGenerator(String urlStr) {
        if (urlStr == null || urlStr.equals(""))
            throw new OXFException("Empty url string");

        return new URLGenerator(urlStr);
    }

}
