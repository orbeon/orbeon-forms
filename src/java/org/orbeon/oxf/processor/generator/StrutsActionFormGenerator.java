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
package org.orbeon.oxf.processor.generator;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.exolab.castor.xml.Marshaller;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.ContentHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Enumeration;

/**
 * @version  Revision: $Revision: 1.1.1.1 $
 * @author   Modified by $Author: ebruchez $
 */

public class StrutsActionFormGenerator extends org.orbeon.oxf.processor.ProcessorImpl {
    static private Logger logger = LoggerFactory.createLogger(StrutsActionFormGenerator.class);

    public StrutsActionFormGenerator() {
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public org.orbeon.oxf.processor.ProcessorOutput createOutput(String name) {
        org.orbeon.oxf.processor.ProcessorOutput output = new org.orbeon.oxf.processor.ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                HttpServletRequest request = (HttpServletRequest) context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.REQUEST);
                if (request == null)
                    throw new OXFException("Missing request object in StrutsActionFormGenerator");
                try {
                    HttpSession session = request.getSession();

                    contentHandler.startDocument();
                    String rootElementName = "struts-beans";
                    contentHandler.startElement("", rootElementName, rootElementName, XMLUtils.EMPTY_ATTRIBUTES);

//                    for(Enumeration e = session.getAttributeNames(); e.hasMoreElements(); ) {
//                        String name = (String)e.nextElement();
//                        Object attribute = session.getAttribute(name);
//                        if(attribute instanceof ActionForm) {
//                            if (logger.isDebugEnabled())
//                                logger.debug("Serializing session Attr: " + name + " = " + attribute);
//                            addStrutsActionForms(name, attribute, contentHandler);
//                        }
//                    }


                    for (Enumeration e = request.getAttributeNames(); e.hasMoreElements();) {
                        String name = (String) e.nextElement();
                        Object attribute = request.getAttribute(name);
                        if (attribute instanceof ActionForm) {
                            if (logger.isDebugEnabled())
                                logger.debug("Serializing request Attr: " + name + " = " + attribute);
                            addStrutsActionForms(name, attribute, contentHandler);
                        }
                    }


                    contentHandler.endElement("", rootElementName, rootElementName);
                    contentHandler.endDocument();
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private void addStrutsActionForms(String name, Object bean, ContentHandler ch) {
        try {
            Marshaller marshaller = new Marshaller(ch);
            marshaller.setMarshalAsDocument(false);
            marshaller.setRootElement(name);
            marshaller.marshal(bean);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

}
