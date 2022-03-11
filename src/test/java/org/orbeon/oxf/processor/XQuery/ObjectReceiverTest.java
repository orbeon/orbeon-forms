/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.processor.XQuery;

import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ObjectReceiverTest extends ObjectReceiver {

    public String publicString;
    public int intValue = -1;
    public boolean booleanValue = false;
    public URI uri;

    class Foo extends ObjectReceiver {
        public String string;
        public boolean booleanValue = false;
        public Vector<String> repeatableString;
        public Vector<Integer> repeatableInt;
        public Vector<Bar> repeatableBar;
        public Bar bar;

        public Foo() {}

        class Bar extends ObjectReceiver {
            public String string;

            public Bar() {}
        }

    }

    public Foo foo;

    @Test
    public void publicSimpleElement() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<publicString>a public string</publicString>")));
        assertEquals("a public string", publicString);
    }

    @Test
    public void simpleElement() throws IOException, SAXException, ClassNotFoundException {
        /*
            Fields that are not declared public cannot be used
         */
        Exception caught = null;
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        try {
            reader.parse(new InputSource(new StringReader("<string>a string</string>")));
        } catch (SAXException e) {
            assertEquals(Class.forName("java.lang.NoSuchFieldException"), e.getException().getClass());
            caught = e;
        }
        assertNotNull(caught);
    }

    @Test
    public void privateSimpleElement() throws IOException, SAXException, ClassNotFoundException {
        /*
            Private fields cannot be used
         */
        Exception caught = null;
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        try {
            reader.parse(new InputSource(new StringReader("<privateString>a private string</privateString>")));
        } catch (SAXException e) {
            assertEquals(Class.forName("java.lang.NoSuchFieldException"), e.getException().getClass());
            caught = e;
        }
        assertNotNull(caught);
    }

    @Test
    public void intValue() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<intValue>5</intValue>")));
        assertEquals(5, intValue);
    }

    @Test
    public void booleanValue() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<booleanValue>true</booleanValue>")));
        assertEquals(true, booleanValue);
        reader.parse(new InputSource(new StringReader("<booleanValue>false</booleanValue>")));
        assertEquals(false, booleanValue);
    }

    @Test
    public void uri() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<uri>http://example.com</uri>")));
        assertEquals(URI.create("http://example.com"), uri);
    }

    @Test
    public void foo() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<foo><string>A string</string><booleanValue>true</booleanValue></foo>")));
        assertNotNull(foo);
        assertEquals("A string", foo.string);
        assertEquals(true, foo.booleanValue);
        reader.parse(new InputSource(new StringReader("<foo><string>Another string</string><booleanValue>false</booleanValue></foo>")));
        assertNotNull(foo);
        assertEquals("Another string", foo.string);
        assertEquals(false, foo.booleanValue);
    }

    @Test
    public void bar() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<foo><bar><string>bar</string></bar><string>A string</string><booleanValue>true</booleanValue></foo>")));
        assertNotNull(foo.bar);
        assertEquals("bar", foo.bar.string);
    }

    @Test
    public void comment() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<publicString>a public<!-- comment--> string</publicString>")));
        assertEquals("a public string", publicString);
    }

    @Test
    public void emptyString() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<publicString/>")));
        assertEquals("", publicString);
    }


    @Test
    public void repeatableString() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<foo><repeatableString>String 1</repeatableString><repeatableString>String 2</repeatableString></foo>")));
        assertNotNull(foo.repeatableString);
        assertEquals(2, foo.repeatableString.size());
        String string = foo.repeatableString.get(0);
        assertEquals(string, "String 1");
        string = foo.repeatableString.get(1);
        assertEquals(string, "String 2");
    }

    @Test
    public void repeatableInt() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<foo><repeatableInt>10</repeatableInt><repeatableInt>-3</repeatableInt></foo>")));
        assertNotNull(foo.repeatableInt);
        assertEquals(2, foo.repeatableInt.size());
        Integer integer = foo.repeatableInt.get(0);
        assertEquals(integer, new Integer(10));
        integer = foo.repeatableInt.get(1);
        assertEquals(integer, new Integer(-3));
    }

    @Test
    public void repeatableBar() throws IOException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setContentHandler(this);
        reader.parse(new InputSource(new StringReader("<foo><repeatableBar><string>A string</string></repeatableBar><repeatableBar/></foo>")));
        assertNotNull(foo.repeatableBar);
        assertEquals(2, foo.repeatableBar.size());
        Foo.Bar bar = foo.repeatableBar.get(0);
        assertEquals(bar.string, "A string");
        bar = foo.repeatableBar.get(1);
        assertNull(bar.string);
    }
}