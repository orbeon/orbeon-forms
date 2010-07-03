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
package org.orbeon.oxf.portlet.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.xml.ContentHandlerHelper;

import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;
import java.util.Enumeration;

/**
 * This processor outputs the preferences for the current portlet.
 */
public class PortletPreferencesGenerator extends ProcessorImpl {

    public PortletPreferencesGenerator() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(PortletPreferencesGenerator.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

                if (!(externalContext.getNativeRequest() instanceof PortletRequest))
                    throw new OXFException("Portlet preferences are only available from within a portlet");

                final PortletRequest portletRequest = (PortletRequest) externalContext.getNativeRequest();
                final PortletPreferences preferences = portletRequest.getPreferences();

                final ContentHandlerHelper helper = new ContentHandlerHelper(xmlReceiver);
                helper.startDocument();
                helper.startElement("portlet-preferences");

                for (Enumeration e = preferences.getNames(); e.hasMoreElements();) {
                    final String name = (String) e.nextElement();
                    final String[] values = preferences.getValues(name, null);

                    helper.startElement("preference");
                    helper.element("name", name);
                    if (values != null) {
                        for (int i = 0; i < values.length; i++)
                            helper.element("value", values[i]);
                    }

                    helper.endElement();
                }

                helper.endElement();
                helper.endDocument();
            }
        };
        addOutput(name, output);
        return output;
    }
}
