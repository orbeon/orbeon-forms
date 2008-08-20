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
package org.orbeon.oxf.processor.serializer.legacy;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.NamespaceCleanupContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.Result;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * A forwarding content handler that flushes the output when receiving a given processing instruction.
 *
 * Also clean invalid XML 1.0 namespace declarations if needed.
 */
public class SerializerContentHandler extends NamespaceCleanupContentHandler {
    private Writer writer;
    private OutputStream os;

    public SerializerContentHandler(ContentHandler contentHandler, boolean serializeXML11) {
        super(contentHandler, serializeXML11);
    }

    public SerializerContentHandler(ContentHandler contentHandler, Writer writer, boolean serializeXML11) {
        this(contentHandler, serializeXML11);
        this.writer = writer;
    }

    public SerializerContentHandler(ContentHandler contentHandler, OutputStream os, boolean serializeXML11) {
        this(contentHandler, serializeXML11);
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
                } else if ("start-comment".equals(data)) {
                    super.processingInstruction(Result.PI_DISABLE_OUTPUT_ESCAPING, "");
                    super.characters("<!--".toCharArray(), 0, 4);
//                    if (writer != null) {
//                        writer.write("<!--");
//                    }
                } else if ("end-comment".equals(data)) {
                    super.characters("-->".toCharArray(), 0, 3);
                    super.processingInstruction(Result.PI_ENABLE_OUTPUT_ESCAPING, "");
//                    if (writer != null) {
//                        writer.write("-->");
//                    }
                }
            } catch (IOException e) {
                throw new OXFException(e);
            }
        } else {
            super.processingInstruction(target, data);
        }
    }
}
