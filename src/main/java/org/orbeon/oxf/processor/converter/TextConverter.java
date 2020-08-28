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

/**
 * Converts XML into text according to the XSLT Text output method.
 *
 * See http://www.w3.org/TR/xslt#section-Text-Output-Method
 */
public class TextConverter extends TextConverterBase {

    public static String DEFAULT_CONTENT_TYPE = "text/plain";
    public static QName DEFAULT_METHOD = QName.apply("text");

    public TextConverter() {}

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected TransformerXMLReceiver createTransformer(Config config) {

        // Create an identity transformer and start the transformation
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        TransformerUtils.applyOutputProperties(identity.getTransformer(),
                config.method != null ? config.method : ProcessorSupport.qNameToExplodedQName(getPropertySet().getQName(DEFAULT_METHOD_PROPERTY_NAME, DEFAULT_METHOD)),
                null,
                null,
                null,
                getEncoding(config, DEFAULT_ENCODING),
                true,
                null,
                false,
                DEFAULT_INDENT_AMOUNT);

        return identity;
    }
}
