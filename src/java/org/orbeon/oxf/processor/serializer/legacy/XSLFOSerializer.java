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

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.ForwardingXMLReceiver;

import java.io.File;
import java.io.OutputStream;
import java.net.URL;

public class XSLFOSerializer extends HttpBinarySerializer {

    private static final Logger logger = LoggerFactory.createLogger(XSLFOSerializer.class);

    public static String DEFAULT_CONTENT_TYPE = "application/pdf";

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(PipelineContext context, ProcessorInput input, Config config, OutputStream outputStream) {
        try {
            // Setup FOP to output PDF
            final FopFactory fopFactory = FopFactory.newInstance();
            final FOUserAgent foUserAgent = fopFactory.newFOUserAgent();

            final URL configFileUrl = this.getClass().getClassLoader().getResource("fop-userconfig.xml");
            if (configFileUrl == null) {
                logger.warn("FOP config file not found. Please put a fop-userconfig.xml file in your classpath for proper display of UTF-8 characters.");
            } else {
                final File userConfigXml = new File(configFileUrl.getFile());
                fopFactory.setUserConfig(userConfigXml);
            }

            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outputStream);

            // Send data to FOP
            readInputAsSAX(context, INPUT_DATA, new ForwardingXMLReceiver(fop.getDefaultHandler()));
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
