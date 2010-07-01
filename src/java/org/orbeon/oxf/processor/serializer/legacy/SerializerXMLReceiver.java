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
package org.orbeon.oxf.processor.serializer.legacy;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xml.NamespaceCleanupXMLReceiver;
import org.xml.sax.SAXException;

import java.io.*;

/**
 * A forwarding content handler that flushes the output when receiving a given processing instruction.
 *
 * Also clean invalid XML 1.0 namespace declarations if needed.
 */
public class SerializerXMLReceiver extends NamespaceCleanupXMLReceiver {
    private Writer writer;
    private OutputStream os;

    public SerializerXMLReceiver(XMLReceiver xmlReceiver, boolean serializeXML11) {
        super(xmlReceiver, serializeXML11);
    }

    public SerializerXMLReceiver(XMLReceiver xmlReceiver, Writer writer, boolean serializeXML11) {
        this(xmlReceiver, serializeXML11);
        this.writer = writer;
    }

    public SerializerXMLReceiver(XMLReceiver xmlReceiver, OutputStream os, boolean serializeXML11) {
        this(xmlReceiver, serializeXML11);
        this.os = os;
    }

    public void processingInstruction(String target, String data) throws SAXException {
        if ("oxf-serializer".equals(target)) {
                try {
                if ("flush".equals(data)) {
                    if (writer != null)
                        writer.flush();
                    if (os != null)
                        os.flush();
                }
            } catch (IOException e) {
                throw new OXFException(e);
            }
        } else {
            super.processingInstruction(target, data);
        }
    }
}
