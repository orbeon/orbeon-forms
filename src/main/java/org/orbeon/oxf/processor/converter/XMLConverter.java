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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.ProcessorSupport;
import org.orbeon.oxf.util.ContentTypes;
import org.orbeon.oxf.xml.TransformerUtils;

/**
 * Converts XML into text according to the XSLT XML output method.
 *
 * See http://www.w3.org/TR/xslt#section-XML-Output-Method
 */
public class XMLConverter extends TextConverterBase {

    public static final String DEFAULT_CONTENT_TYPE = ContentTypes.XmlContentType();
    public static final QName DEFAULT_METHOD = QName.apply("xml");
    public static final String DEFAULT_VERSION = "1.0";

    public XMLConverter() {
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected TransformerXMLReceiver createTransformer(Config config) {

        // Create an identity transformer and start the transformation
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();

        if(config.publicDoctype != null && config.systemDoctype == null)
            throw new OXFException("System doctype is required if a public doctype is present");

        TransformerUtils.applyOutputProperties(identity.getTransformer(),
                config.method != null ? config.method : ProcessorSupport.qNameToExplodedQName(getPropertySet().getQName(DEFAULT_METHOD_PROPERTY_NAME, DEFAULT_METHOD)),
                config.version != null ? config.version : DEFAULT_VERSION,
                config.publicDoctype != null ? config.publicDoctype : null,
                config.systemDoctype != null ? config.systemDoctype : null,
                getEncoding(config, DEFAULT_ENCODING),
                config.omitXMLDeclaration,
                config.standalone,
                config.indent,
                config.indentAmount);

        return identity;
    }
}
