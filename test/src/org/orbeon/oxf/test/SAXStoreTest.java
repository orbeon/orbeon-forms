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
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.resources.FlatFileResourceManagerFactory;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.apache.log4j.BasicConfigurator;

import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.TransformerHandler;
import java.util.Iterator;
import java.util.Properties;

public class SAXStoreTest /*extends TestCase*/ {

//    private static String ORBEON_DATA = "<?xmlversion=\"1.0\"encoding=\"iso-8859-1\"?><orbeon><test1/><test2><test3/></test2></orbeon>";
//
//    static {
//        BasicConfigurator.configure();
//
//        Properties props = new Properties();
//        props.setProperty(ResourceManagerWrapper.FACTORY_PROPERTY, FlatFileResourceManagerFactory.class.getName());
//        props.setProperty(FlatFileResourceManagerFactory.ROOT_DIR, "src/org/orbeon/oxf/resources/test/import");
//        ResourceManagerWrapper.init(props);
//    }
//
//    public SAXStoreTest(String s) {
//        super(s);
//    }
//
//    public static TestSuite suite() {
//        TestSuite suite = new TestSuite();
//
//        suite.addTest(new SAXStoreTest("testStoreAndReplay"));
//        suite.addTest(new SAXStoreTest("testStoreAndReplayReplay"));
//        suite.addTest(new SAXStoreTest("testStoreForwardAndReplay"));
//        suite.addTest(new SAXStoreTest("testLocator"));
//        suite.addTest(new SAXStoreTest("testDOM4JLocator"));
//
//        return suite;
//    }
//
//    public void testStoreAndReplay() {
//
//        SAXStore store = new SAXStore();
//        ResourceManagerWrapper.instance().getContentAsSAX("/display/orbeon.xml", store);
//
//        TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
//        DOMResult dom = new DOMResult(XMLUtils.createDocument());
//        identity.setResult(dom);
//
//        try {
//            store.replay(identity);
//            compareResult(dom.getNode(), ORBEON_DATA);
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail();
//        }
//    }
//
//    public void testStoreAndReplayReplay() {
//
//        SAXStore store = new SAXStore();
//        ResourceManagerWrapper.instance().getContentAsSAX("/display/big.xml", store);
//
//        TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
//        DOMResult dom = new DOMResult(XMLUtils.createDocument());
//        identity.setResult(dom);
//
//        TransformerHandler identity2 = TransformerUtils.getIdentityTransformerHandler();
//        DOMResult dom2 = new DOMResult(XMLUtils.createDocument());
//        identity2.setResult(dom2);
//
//        try {
//            store.replay(identity);
//            store.replay(identity2);
//
//            compareResult(dom.getNode(),  ResourceManagerWrapper.instance().getContentAsDOM("/display/big.xml"));
//            compareResult(dom2.getNode(),  ResourceManagerWrapper.instance().getContentAsDOM("/display/big.xml"));
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail();
//        }
//    }
//
//
//    public void testStoreForwardAndReplay() {
//
//        TransformerHandler identity1 = TransformerUtils.getIdentityTransformerHandler();
//        DOMResult dom1 = new DOMResult(XMLUtils.createDocument());
//        identity1.setResult(dom1);
//
//        SAXStore store = new SAXStore(identity1);
//        ResourceManagerWrapper.instance().getContentAsSAX("/display/big.xml", store);
//
//        TransformerHandler identity2 = TransformerUtils.getIdentityTransformerHandler();
//        DOMResult dom2 = new DOMResult(XMLUtils.createDocument());
//        identity2.setResult(dom2);
//
//        try {
//            store.replay(identity2);
//            compareResult(dom1.getNode(), ResourceManagerWrapper.instance().getContentAsDOM("/display/big.xml"));
//            compareResult(dom2.getNode(), ResourceManagerWrapper.instance().getContentAsDOM("/display/big.xml"));
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail();
//        }
//
//    }
//
//    public void testLocator() {
//        XMLReader reader = ResourceManagerWrapper.instance().getXMLReader();
//
//
//        SAXStore store = new SAXStore(new LocatorTestHandler());
//        reader.setContentHandler(store);
//        try {
//            reader.parse("/display/orbeon.xml");
//            store.replay(new LocatorTestHandler());
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail();
//        }
//    }
//
//    public void testDOM4JLocator() {
//        try {
//            // sax to dom4j
//            XMLReader reader = ResourceManagerWrapper.instance().getXMLReader();
//            LocationSAXContentHandler ch = new LocationSAXContentHandler();
//            reader.setContentHandler(ch);
//            reader.parse("/display/orbeon.xml");
//
//            Document doc = ch.getDocument();
//
//            //dom4j to sax: check that line/col number are conserved
//            LocationSAXWriter saxw = new LocationSAXWriter();
//            saxw.setContentHandler(new LocatorTestHandler());
//            saxw.write(doc);
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail();
//        }
//
//    }
//
//    private class LocatorTestHandler extends DefaultHandler {
//        Locator loc = null;
//
//        public void setDocumentLocator(Locator locator) {
//            loc = locator;
//        }
//
//        public void startElement(String uri, String localName,
//                                 String qName, Attributes attributes)
//                throws SAXException {
//            if (loc != null) {
//                assertEquals("oxf://display/orbeon.xml", loc.getSystemId());
//                switch (loc.getLineNumber()) {
//                    case 2:
//                        assertEquals(localName, "orbeon");
//                        break;
//                    case 3:
//                        assertEquals(localName, "test1");
//                        break;
//                    case 4:
//                        assertEquals(localName, "test2");
//                        break;
//                    case 5:
//                        assertEquals(localName, "test3");
//                        break;
//                    default:
//                        fail("Wrong line number: " + loc.getLineNumber() + " element: " + localName);
//                }
//                switch (loc.getColumnNumber()) {
//                    case 9:
//                        assertEquals(localName, "orbeon");
//                        break;
//                    case 11:
//                        assertEquals(localName, "test1");
//                        break;
//                    case 10:
//                        assertEquals(localName, "test2");
//                        break;
//                    case 13:
//                        assertEquals(localName, "test3");
//                        break;
//                    default:
//                        System.out.println("Wrong column cumber: " + loc.getColumnNumber() + " element: " + localName);
//                }
//            } else {
//                fail("No Locator is provided");
//            }
//        }
//    }
//
//
//    private void compareResult(Node actualData, Node  expectedData) throws Exception {
//        compareResult(actualData, domToNormalizedString(expectedData));
//    }
//
//
//    private void compareResult(Node actualData, String expectedString) throws Exception {
//        try {
//            String actualString = domToNormalizedString(actualData);
//            boolean testPassed = expectedString.equals(actualString);
//            if (!testPassed) {
//                System.out.println(getName());
//                System.out.println("Expected data: " + expectedString);
//                System.out.println("Actual data:   " + actualString);
//            }
//            assertTrue(testPassed);
//        } catch (Exception e) {
//            System.err.println("failed:");
//            e.printStackTrace();
//            fail();
//        }
//    }
//
//    private String domToNormalizedString(Node node) {
//        String text = XMLUtils.domToString(node);
//        StringBuffer buffer = new StringBuffer();
//        for (int i = 0; i < text.length(); i++) {
//            if (text.charAt(i) != ' ' && text.charAt(i) != '\n' && text.charAt(i) != '\r')
//                buffer.append(text.charAt(i));
//        }
//        return buffer.toString();
//    }

}

/*
  $Log: SAXStoreTest.java,v $
  Revision 1.1  2004/08/21 20:06:36  ebruchez
  Initial revision

  Revision 1.5  2004/08/21 03:48:14  ebruchez
  *** empty log message ***

  Revision 1.4  2004/08/18 23:10:15  ebruchez
  Fixed Java headers again

  Revision 1.3  2004/06/15 00:17:33  avernet
  Make sure Java test pass as well

  Revision 1.2  2003/05/02 22:54:20  jmercay
  updated test

  Revision 1.1  2002/08/13 17:37:41  avernet
  Move from src/org/orbeon/oxf/pipeline/test to test

  Revision 1.3  2002/08/08 19:04:42  jmercay
  dom4j changes, for location information

  Revision 1.2  2002/08/05 18:35:49  jmercay
  new SAXStore

  Revision 1.1  2002/08/01 23:13:14  jmercay
  fixed sax parser namespace problem

*/