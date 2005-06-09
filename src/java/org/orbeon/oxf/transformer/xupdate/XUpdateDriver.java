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
package org.orbeon.oxf.transformer.xupdate;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;

import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import org.orbeon.oxf.xml.dom4j.NonLazySAXContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class XUpdateDriver {

    private final static String XUPDATE_FACTORY =
            "org.orbeon.oxf.transformer.xupdate.TransformerFactoryImpl";

    public static void main(String[] args) 
            throws TransformerException, IOException, ParserConfigurationException, SAXException {

        // Check arguments
        if (args.length != 2) {
            System.err.println("Syntax: java " + XUpdateDriver.class.getName() + " input.xml xupdate.xml");
            return;
        }

        // Perform transformation
        Templates templates = createTemplates(new FileReader(args[1]));
        final NonLazySAXContentHandler saxContentHandler = new NonLazySAXContentHandler();
        templates.newTransformer().transform(new SAXSource( newXMLReader(),
                new InputSource(new FileReader(args[0]))),
                new SAXResult(saxContentHandler));

        // Output result
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setIndentSize(4);
        XMLWriter xmlWriter = new XMLWriter(System.out, format);
        xmlWriter.write(saxContentHandler.getDocument());
    }

    private static XMLReader newXMLReader() 
            throws ParserConfigurationException, SAXException {
        final SAXParserFactory pf = SAXParserFactory.newInstance();
        final SAXParser p = pf.newSAXParser();
        final XMLReader ret = p.getXMLReader();
        ret.setFeature( "http://xml.org/sax/features/namespaces", true );
        return ret;
    }

    private static Templates createTemplates(Reader xupdateReader) {
        try {
            SAXTransformerFactory factory = (SAXTransformerFactory) Class.forName(XUPDATE_FACTORY).newInstance();
            TemplatesHandler templatesHandler = factory.newTemplatesHandler();
            XMLReader xmlReader = newXMLReader();
            xmlReader.setContentHandler(templatesHandler);
            xmlReader.parse(new InputSource(xupdateReader));
            return templatesHandler.getTemplates();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
