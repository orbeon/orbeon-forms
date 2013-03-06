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
package org.orbeon.oxf.util;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.NodeInfo;

public class PipelineUtils {

    private static void configDOMGenerator(final DOMGenerator dm) {
        dm.setId("N/A");
        dm.setName(XMLConstants.DOM_GENERATOR_PROCESSOR_QNAME);
    }

    public static void connect(Processor producer, String outputName, Processor consumer, String inputName) {
        final ProcessorOutput output = producer.createOutput(outputName);
        final ProcessorInput input = consumer.createInput(inputName);
        output.setInput(input);
        input.setOutput(output);
    }

    public static DOMGenerator createDOMGenerator(final Element element, final String id, final Object v, final String systemId) {
        final DOMGenerator ret = new DOMGenerator(element, id, v, systemId);
        configDOMGenerator(ret);
        return ret;
    }

    public static DOMGenerator createDOMGenerator(final Document document, final String id, final Object v, final String systemId) {
        final DOMGenerator ret = new DOMGenerator(document, id, v, systemId);
        configDOMGenerator(ret);
        return ret;
    }

    public static DOMGenerator createDOMGenerator(final NodeInfo nodeInfo, final String id, final Object v, final String systemId) {
        final DOMGenerator ret = new DOMGenerator(nodeInfo, id, v, systemId);
        configDOMGenerator(ret);
        return ret;
    }

    public static Processor createURLGenerator(String urlStr) {
        if (urlStr == null || urlStr.equals(""))
            throw new OXFException("Empty url string");

        return new URLGenerator(urlStr);
    }

    public static Processor createURLGenerator(String urlStr, boolean handleXInclude) {
        if (urlStr == null || urlStr.equals(""))
            throw new OXFException("Empty url string");

        return new URLGenerator(urlStr, handleXInclude);
    }
}
