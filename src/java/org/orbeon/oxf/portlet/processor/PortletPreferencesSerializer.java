/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.portlet.processor;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;

import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;
import javax.portlet.ReadOnlyException;
import java.util.Iterator;
import java.util.List;

/**
 * This processor stores the preferences for the current portlet.
 */
public class PortletPreferencesSerializer extends ProcessorImpl {

    public static final String PORTLET_PREFERENCES_SERIALIZER_DATA_NAMESPACE_URI
            = "http://orbeon.org/oxf/xml/portlet-preferences-serializer-data";

    public PortletPreferencesSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, PORTLET_PREFERENCES_SERIALIZER_DATA_NAMESPACE_URI));
    }

    public void start(PipelineContext pipelineContext) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        final PortletRequest portletRequest = (PortletRequest) externalContext.getNativeRequest();
        final PortletPreferences preferences = portletRequest.getPreferences();

        final Document document = readInputAsDOM4J(pipelineContext, INPUT_DATA);

        boolean modified = false;
        for (Iterator i = document.getRootElement().elements().iterator(); i.hasNext();) {
            final Element currentElement = (Element) i.next();
            final String currentName = currentElement.element("name").getStringValue();

            final List valueElements = currentElement.elements("value");
            if (valueElements.size() > 0) {
                // There are some values, extract them...
                final String[] currentValuesArray = new String[valueElements.size()];
                int valueIndex = 0;
                for (Iterator j = valueElements.iterator(); j.hasNext(); valueIndex++) {
                    final Element currentValueElement = (Element) j.next();
                    final String currentValue = currentValueElement.getStringValue();
                    currentValuesArray[valueIndex] = currentValue;
                }

                try {
                    // ...and set the values
                    preferences.setValues(currentName, currentValuesArray);
                    modified = true;
                } catch (ReadOnlyException e) {
                    throw new OXFException(e);
                }
            } else {
                // If no value was passed, we take it that we want to reset the preference
                try {
                    preferences.reset(currentName);
                } catch (ReadOnlyException e) {
                    throw new OXFException(e);
                }
            }
        }
        if (modified) {
            try {
                preferences.store();
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }
    }
}
