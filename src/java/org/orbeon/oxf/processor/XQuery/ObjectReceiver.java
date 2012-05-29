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

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ObjectReceiver implements XMLReceiver {


    private XMLReceiver currentReceiver = null;
    private Field currentField = null;
    private Class currentMemberType = null;
    private StringBuilder buffer = null;
    private int level = 0;

    public void setDocumentLocator(Locator locator) {
        if (currentReceiver != null) {
            currentReceiver.setDocumentLocator(locator);
        }
    }

    public void startDocument() throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.startDocument();
        }
    }

    public void endDocument() throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.endDocument();
        }
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.startPrefixMapping(s, s1);
        }
    }

    public void endPrefixMapping(String s) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.endPrefixMapping(s);
        }
    }

    private XMLReceiver instantiateXMLReceiver(Class klass) throws SAXException {
        try {
            return (XMLReceiver) (klass.newInstance());
        } catch (InstantiationException e) {
            // If we can't instantiate the class, it may be because it is an inner class of the current one...
            try {
                Constructor constructor = klass.getConstructor(this.getClass());
                return (XMLReceiver) constructor.newInstance(this);
            } catch (NoSuchMethodException e1) {
                throw new SAXException(e1);
            } catch (InstantiationException e1) {
                throw new SAXException(e1);
            } catch (InvocationTargetException e1) {
                throw new SAXException(e1);
            } catch (IllegalAccessException e1) {
                throw new SAXException(e1);
            }
        } catch (IllegalAccessException e) {
            throw new SAXException(e);
        }
    }

    private static boolean classIsCollection(Class klass) {
        return Collection.class.isAssignableFrom(klass);
    }

    private static boolean classIsXMLReceiver(Class klass) {
        return XMLReceiver.class.isAssignableFrom(klass);
    }

    private static Class classMemberType(Field field) {
        // Can't believe there isn't some smarter way to do that...
        String genericType = field.getGenericType().toString();
        Pattern memberPattern = Pattern.compile(".*<(.*)>");
        Matcher matcher = memberPattern.matcher(genericType);
        if (matcher.matches()) {
            String memberTypeName = matcher.group(1);
            try {
                return Class.forName(memberTypeName);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        try {
            if (currentReceiver != null) {
                /* Forwarding state */
                currentReceiver.startElement(uri, localName, qName, attributes);
            } else if (currentField == null) {
                /* Need to create a new object to receive the content of a new element */
                currentField = this.getClass().getField(localName);
                Class klass = currentField.getType();
                if (classIsXMLReceiver(klass)) {
                    currentReceiver = instantiateXMLReceiver(klass);
                } else if (classIsCollection(klass)) {
                    currentMemberType = classMemberType(currentField);
                    if (classIsXMLReceiver(currentMemberType)) {
                        currentReceiver = instantiateXMLReceiver(currentMemberType);
                    }
                }
                buffer = null;
            } else {
                /* Sub elements are not expected here! */
                throw new SAXException("Sub element " + localName + " was not expected here!");
            }
        } catch (NoSuchFieldException e) {
            throw new SAXException(e);
        }
        level++;
    }


    public void endElement(String s, String s1, String s2) throws SAXException {
        try {
            level--;
            if (level == 0) {
                /* We have finished to receive an object */
                if (currentReceiver == null && currentMemberType == null) {
                    /* This was a non repeatable simple type element */
                    Class klass = currentField.getType();
                    String className = klass.getName();
                    String value = buffer == null ? "" : buffer.toString();
                    if (className.equals("int")) {
                        currentField.setInt(this, Integer.parseInt(value));
                    } else if (className.equals("boolean")) {
                        currentField.setBoolean(this, value.equals("true"));
                    } else if (klass.equals(value.getClass())) {
                        currentField.set(this, value);
                    } else {
                        currentField.set(this, klass.getConstructor(value.getClass()).newInstance(value));
                    }
                    //Method method = this.getClass().getMethod("addValue", Class.forName("java.lang.String"));
                    //method.invoke(buffer.toString());
                } else if (currentReceiver == null && currentMemberType != null) {
                    /* This was a repeatable simple type element */
                    String value = buffer == null ? "" : buffer.toString();
                    Class klass = currentField.getType();
                    Collection collection = (Collection) currentField.get(this);
                    if (collection == null) {
                        collection = (Collection) klass.newInstance();
                        currentField.set(this, collection);
                    }
                    if (currentMemberType.equals(value.getClass())) {
                        collection.add(value);
                    } else {
                        collection.add(currentMemberType.getConstructor(value.getClass()).newInstance(value));
                    }
                } else if (currentMemberType != null) {
                    Class klass = currentField.getType();
                    Collection collection = (Collection) currentField.get(this);
                    if (collection == null) {
                        collection = (Collection) klass.newInstance();
                        currentField.set(this, collection);
                    }
                    collection.add(currentReceiver);
                } else {
                    /* This was not a simple type element */
                    currentField.set(this, currentReceiver);
                }
                currentReceiver = null;
                currentField = null;
                currentMemberType = null;
                buffer = null;
            } else {
                currentReceiver.endElement(s, s1, s2);
            }
        } catch (IllegalAccessException e) {
            throw new SAXException(e);
        } catch (NoSuchMethodException e) {
            throw new SAXException(e);
        } catch (InstantiationException e) {
            throw new SAXException(e);
        } catch (InvocationTargetException e) {
            throw new SAXException(e);
        }
    }

    public void characters(char[] chars, int i, int i1) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.characters(chars, i, i1);
        } else if (buffer == null) {
            buffer = new StringBuilder(new String(chars, i, i1));
        } else {
            buffer.append(chars, i, i1);
        }
    }

    public void ignorableWhitespace(char[] chars, int i, int i1) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.ignorableWhitespace(chars, i, i1);
        }
    }

    public void processingInstruction(String s, String s1) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.processingInstruction(s, s1);
        }
    }

    public void skippedEntity(String s) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.skippedEntity(s);
        }
    }

    public void startDTD(String s, String s1, String s2) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.startDTD(s, s1, s2);
        }
    }

    public void endDTD() throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.endDTD();
        }
    }

    public void startEntity(String s) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.startEntity(s);
        }
    }

    public void endEntity(String s) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.endEntity(s);
        }
    }

    public void startCDATA() throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.startCDATA();
        }
    }

    public void endCDATA() throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.endCDATA();
        }
    }

    public void comment(char[] chars, int i, int i1) throws SAXException {
        if (currentReceiver != null) {
            currentReceiver.comment(chars, i, i1);
        }
    }
}