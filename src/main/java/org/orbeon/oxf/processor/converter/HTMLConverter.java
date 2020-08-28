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
package org.orbeon.oxf.processor.converter;

import org.orbeon.dom.QName;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.ProcessorSupport;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLReceiver;

/**
 * Converts XML into text according to the XSLT HTML output method.
 *
 * http://www.w3.org/TR/xslt#section-HTML-Output-Method
 */
public class HTMLConverter extends TextConverterBase {

    public static final String DEFAULT_CONTENT_TYPE = "text/html";
    public static final QName DEFAULT_METHOD = QName.apply("html");

    public HTMLConverter() {}

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected TransformerXMLReceiver createTransformer(Config config) {

        // Create an identity transformer and start the transformation
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        TransformerUtils.applyOutputProperties(identity.getTransformer(),
                config.method != null ? config.method : ProcessorSupport.qNameToExplodedQName(getPropertySet().getQName(DEFAULT_METHOD_PROPERTY_NAME, DEFAULT_METHOD)),
                config.version != null ? config.version : null,
                config.publicDoctype != null ? config.publicDoctype : null,
                config.systemDoctype != null ? config.systemDoctype : null,
                getEncoding(config, DEFAULT_ENCODING),
                config.omitXMLDeclaration,
                config.standalone,
                config.indent,
                config.indentAmount);

        return identity;
    }

    @Override
    protected XMLReceiver createFilterReceiver(XMLReceiver downstreamReceiver, XMLReceiver transformer, boolean[] didEndDocument) {
        // Override so we can filter namespace declarations. It is not clear whether this is meant to be done this way!
        // As of 2012-10-02, do this for backward compatibility.
        return new HTMLFilterReceiver(downstreamReceiver, transformer, didEndDocument);
    }

    protected static class HTMLFilterReceiver extends FilterReceiver {
        public HTMLFilterReceiver(XMLReceiver downstreamReceiver, XMLReceiver transformer, boolean[] didEndDocument) {
            super(downstreamReceiver, transformer, didEndDocument);
        }

        public void startPrefixMapping(String s, String s1) {
            // Do nothing
        }

        public void endPrefixMapping(String s) {
            // Do nothing
        }
    }
}
