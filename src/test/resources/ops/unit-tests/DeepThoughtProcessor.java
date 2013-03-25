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
import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.SimpleProcessor;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This test processor takes a number from its input, doubles it, and outputs the result to its
 * output.
 */
public class DeepThoughtProcessor extends SimpleProcessor {

    public DeepThoughtProcessor() {
        addInputInfo(new ProcessorInputOutputInfo("number"));
        addOutputInfo(new ProcessorInputOutputInfo("double"));
    }

    public void generateDouble(PipelineContext context, ContentHandler contentHandler) throws SAXException {

        // Get number from input using DOM4J
        Document numberDocument = readInputAsDOM4J(context, "number");
        String numberString = (String) numberDocument.selectObject("string(/number)");
        int number = Integer.parseInt(numberString);
        String doubleString = Integer.toString(number * 2);

        // Generate output document with SAX
        contentHandler.startDocument();
        contentHandler.startElement("", "number", "number", new AttributesImpl());
        contentHandler.characters(doubleString.toCharArray(), 0, doubleString.length());
        contentHandler.endElement("", "number", "number");
        contentHandler.endDocument();
    }
}
