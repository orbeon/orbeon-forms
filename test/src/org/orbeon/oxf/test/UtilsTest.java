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
package org.orbeon.oxf.test;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.processor.JavaProcessor;
import org.orbeon.oxf.resources.ResourceManager;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.NumberUtils;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.dom4j.LocationDocumentSource;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class UtilsTest extends TestCase {

    public UtilsTest(String s) {
        super(s);
    }

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();

        suite.addTest(new UtilsTest("testNumberUtils"));
        suite.addTest(new UtilsTest("testLocationDocumentSourceResult"));

        return suite;
    }

    public void testNumberUtils() {
        byte[] bytes1 = {(byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78, (byte) 0x9a, (byte) 0xbc, (byte) 0xde, (byte) 0xf0};
        String results1 = "123456789abcdef0";
        assertEquals(NumberUtils.toHexString(bytes1), results1);

        byte[] bytes2 = {(byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef};
        String results2 = "0123456789abcdef";
        assertEquals(NumberUtils.toHexString(bytes2), results2);

        byte[] bytes3 = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        String results3 = "0000000000000000";
        assertEquals(NumberUtils.toHexString(bytes3), results3);
    }

    private ResourceManager setupResourceManager() {
        // Setup resource manager
        Map props = new HashMap();
        Properties properties = System.getProperties();
        for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            if (name.startsWith("oxf.resources."))
                props.put(name, properties.getProperty(name));
        }
        ResourceManagerWrapper.init(props);
        ResourceManager resourceManager = ResourceManagerWrapper.instance();

        return resourceManager;
    }

    public void testLocationDocumentSourceResult() {
        try {
            Transformer transformer = TransformerUtils.getIdentityTransformer();

            ResourceManager resourceManager = setupResourceManager();

            InputStream is = resourceManager.getContentAsStream("/unit-tests/company.xml");

            Source source = new StreamSource(is);
            LocationDocumentResult result = new LocationDocumentResult(resourceManager.getRealPath("/unit-tests/company.xml"));
            transformer.transform(source, result);

            Document doc = result.getDocument();
            Element firstName = (Element) doc.createXPath("//firstname").selectNodes(doc).get(0);
            assertEquals("Omar", firstName.getTextTrim());
            assertEquals(17, ((LocationData) firstName.getData()).getLine());

            source = new LocationDocumentSource(doc);
            SAXResult saxResult = new SAXResult(new ForwardingContentHandler() {
                Locator locator;
                StringBuffer buff = new StringBuffer();
                boolean foundFirstName = false;

                public void setDocumentLocator(Locator locator) {
                    this.locator = locator;
                }

                public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                    if ("firstname".equals(localname)) {
                        assertEquals(17, locator.getLineNumber());
                        foundFirstName = true;
                    }
                }

                public void characters(char[] chars, int start, int length) throws SAXException {
                    if (foundFirstName)
                        buff.append(chars, start, length);
                }

                public void endElement(String uri, String localname, String qName) throws SAXException {
                    if ("firstname".equals(localname)) {
                        assertEquals("Omar", buff.toString());
                    }
                }

            });
            transformer.transform(source, saxResult);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }

}
