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

import org.apache.fop.apps.Driver;
import org.apache.fop.messaging.MessageHandler;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.serializer.HttpBinarySerializer;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.SAXStore;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import java.io.OutputStream;

public class XSLFOSerializer extends HttpBinarySerializer {

    private static final Logger logger = LoggerFactory.createLogger(XSLFOSerializer.class);
    private static final FOPLogger fopLogger = new FOPLogger();

    public static String DEFAULT_CONTENT_TYPE = "application/pdf";

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    static {
        MessageHandler.setScreenLogger(fopLogger);
    }

    protected void readInput(PipelineContext context, ProcessorInput input, Config config, OutputStream outputStream) {
        try {
            // First read input, as we don't want to get the output stream
            // before we are sure we can read the input without error
            final SAXStore inputSAXStore = new SAXStore();
            readInputAsSAX(context, (input != null) ? input : getInputByName(INPUT_DATA), inputSAXStore);

            Driver fop = new Driver();
            fop.setLogger(fopLogger);
            fop.setRenderer(Driver.RENDER_PDF);
            fop.setOutputStream(outputStream);
            fop.setXMLReader(new XMLFilterImpl() {
                public void parse(InputSource inputSource) throws SAXException {
                    inputSAXStore.replay(getContentHandler());
                }
            });
            fop.setInputSource(new InputSource("toto"));
            fop.run();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }


    private static class FOPLogger implements org.apache.avalon.framework.logger.Logger {
        public void debug(String s) {
            logger.debug(s);
        }

        public void debug(String s, Throwable throwable) {
            logger.debug(s, throwable);
        }

        public void error(String s) {
            logger.error(s);
        }

        public void error(String s, Throwable throwable) {
            logger.error(s, throwable);
        }

        public void fatalError(String s) {
            logger.fatal(s);
        }

        public void fatalError(String s, Throwable throwable) {
            logger.fatal(s, throwable);
        }

        public org.apache.avalon.framework.logger.Logger getChildLogger(String s) {
            return null;
        }

        public void info(String s) {
            logger.debug(s);
        }

        public void info(String s, Throwable throwable) {
            logger.debug(s, throwable);
        }

        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }

        public boolean isErrorEnabled() {
            return true;
        }

        public boolean isFatalErrorEnabled() {
            return true;
        }

        public boolean isInfoEnabled() {
            return isInfoEnabled();
        }

        public boolean isWarnEnabled() {
            return true;
        }

        public void warn(String s) {
            logger.warn(s);
        }

        public void warn(String s, Throwable throwable) {
            logger.warn(s, throwable);
        }
    }
}
