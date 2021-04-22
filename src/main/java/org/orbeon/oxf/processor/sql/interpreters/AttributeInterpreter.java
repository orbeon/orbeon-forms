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
package org.orbeon.oxf.processor.sql.interpreters;

import org.orbeon.dom.Namespace$;
import org.orbeon.dom.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.util.StringUtils;
import org.orbeon.oxf.xml.DeferredXMLReceiver;
import org.orbeon.oxf.xml.DeferredXMLReceiverImpl;
import org.orbeon.oxf.xml.XMLReceiverAdapter;
import org.orbeon.oxf.xml.dom.XmlLocationData;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.Map;

/**
 * Add an attribute to the last element output.
 */
public class AttributeInterpreter extends SQLProcessor.InterpreterContentHandler {

    private DeferredXMLReceiver savedOutput;
    private String attributeName;
    private StringBuilder content;

    public AttributeInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
        setForward(true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        // Attribute name
        this.attributeName = attributes.getValue("name");

        // Remember output
        savedOutput = getInterpreterContext().getOutput();
        // New output just gather character data
        getInterpreterContext().setOutput(new DeferredXMLReceiverImpl(new XMLReceiverAdapter() {
            public void characters(char ch[], int start, int length) {
                if (content == null)
                    content = new StringBuilder();
                content.append(ch, start, length);
            }
        }));

        addAllDefaultElementHandlers();
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Restore output
        getInterpreterContext().setOutput(savedOutput);

        // Normalize
        final String contentString;
        if (content == null)
            contentString = "";
        else
            contentString = StringUtils.trimAllToEmpty(content.toString());

        // Output attribute
        final QName attributeQName = getQName(getDocumentLocator(), attributeName, getInterpreterContext().getPrefixesMap());
        getInterpreterContext().getOutput().startPrefixMapping(attributeQName.localName(), attributeQName.namespace().uri());// NOTE: we may have to check that the mapping does not exist yet
        getInterpreterContext().getOutput().addAttribute(attributeQName.namespace().uri(), attributeQName.localName(), attributeName, contentString);

        // Clear state
        savedOutput = null;
        attributeName = null;
        content = null;
    }

    private static QName getQName(Locator locator, String qNameString, Map prefixesMap) {

        final int colonIndex = qNameString.indexOf(':');
        if (colonIndex == -1)
            return QName.apply(qNameString);
        if (colonIndex == 0)
            throw new ValidationException("Invalid QName:" + qNameString, XmlLocationData.apply(locator));

        final String prefix = qNameString.substring(0, colonIndex);
        final String localName = qNameString.substring(colonIndex + 1);

        if (prefixesMap.get(prefix) == null) {
            throw new ValidationException("Undeclared prefix for QName:" + qNameString, XmlLocationData.apply(locator));
        } else {
            return QName.apply(localName, Namespace$.MODULE$.apply(prefix, (String) prefixesMap.get(prefix)));
        }
    }
}
