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
import org.orbeon.oxf.processor.SimpleProcessor;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.DocumentException;

import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

public class UrlToXml extends SimpleProcessor {

    private static final Attributes NO_ATTRIBUTES = new AttributesImpl();

    public UrlToXml() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public void generateData(PipelineContext context, ContentHandler contentHandler) throws SAXException, IOException, DocumentException {

        String input = (String) readInputAsDOM4J(context, "request")
                .selectObject("string(/request/parameters/parameter/value)");
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(Base64.decode(input)));
        byte[] buffer = new byte[1024];
        StringBuffer stringBuffer = new StringBuffer();
        while (true) {
            int size = gzipInputStream.read(buffer, 0, 1024);
            if (size == -1) break;
            stringBuffer.append(new String(buffer, 0, size));
        }

        Document document = DocumentHelper.parseText(stringBuffer.toString());
        LocationSAXWriter locationSAXWriter = new LocationSAXWriter();
        locationSAXWriter.setContentHandler(contentHandler);
        locationSAXWriter.write(document);
    }
}