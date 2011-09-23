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
package org.orbeon.oxf.processor;

import org.apache.log4j.Logger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.ContentHandler;

public class SessionInvalidator extends ProcessorImpl {
    static private Logger logger = LoggerFactory.createLogger(SessionInvalidator.class);

    public SessionInvalidator() {
        logger.error("Invalidator created");        
/*        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));*/
    }

    public void start(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        try {
            ExternalContext externalContext = (ExternalContext) context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.EXTERNAL_CONTEXT);
            externalContext.getRequest().sessionInvalidate();
            logger.error("Session forcibly invalidated");        
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
/*    
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(SessionInvalidator.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    start(context);
                    outputSuccess(xmlReceiver, "invalidated");
                    logger.error("Output created");        
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private void outputSuccess(ContentHandler ch, String operationName) {
        try {
            ch.startDocument();
            addElement(ch, operationName, "success");
            ch.endDocument();
        }catch(Exception e) {
            throw new OXFException(e);
        }
    }
    
    private void addElement(ContentHandler contentHandler, String name, String value)
            throws Exception {
        if (value != null) {
            contentHandler.startElement("", name, name, XMLUtils.EMPTY_ATTRIBUTES);
            addString(contentHandler, value);
            contentHandler.endElement("", name, name);
        }
    }

    private void addString(ContentHandler contentHandler, String string)
            throws Exception {
        char[] charArray = string.toCharArray();
        contentHandler.characters(charArray, 0, charArray.length);
    }
*/    
}
