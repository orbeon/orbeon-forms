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
package org.orbeon.oxf.processor.serializer;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.NamespaceCleanupContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * A forwarding content handler that flushed the output when receiving a
 * given processing instruction.
 *
 * Also clean invalid XML 1.0 namespace declarations.
 */
public class SerializerContentHandler extends NamespaceCleanupContentHandler {
    private Writer writer;
    private OutputStream os;

    public SerializerContentHandler(ContentHandler contentHandler) {
        super(contentHandler);
    }

    public SerializerContentHandler(ContentHandler contentHandler, Writer writer) {
        this(contentHandler);
        this.writer = writer;
    }

    public SerializerContentHandler(ContentHandler contentHandler, OutputStream os) {
        this(contentHandler);
        this.os = os;
    }

    public void processingInstruction(String target, String data) throws SAXException {
        if ("oxf-serializer".equals(target) && "flush".equals(data)) {
            try {
                if (writer != null)
                    writer.flush();
                if (os != null)
                    os.flush();
            } catch (IOException e) {
                throw new OXFException(e);
            }
        }
        super.processingInstruction(target, data);
    }
}
