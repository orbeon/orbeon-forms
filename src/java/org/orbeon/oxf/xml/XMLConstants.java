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
package org.orbeon.oxf.xml;

import org.dom4j.Namespace;
import org.dom4j.QName;

public class XMLConstants {

    public static final String XSI_PREFIX = "xsi";
    public static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final QName XSI_TYPE_QNAME = new QName("type", new Namespace(XSI_PREFIX, XSI_URI));

    public static final String XSD_PREFIX = "xs";
    public static final String XSD_URI = "http://www.w3.org/2001/XMLSchema";
    public static final Namespace XSD_NAMESPACE = new Namespace(XSD_PREFIX, XSD_URI);

    public static final String OXF_PROCESSORS_URI = "http://www.orbeon.com/oxf/processors";
    public static final Namespace OXF_PROCESSORS_NAMESPACE = new Namespace("oxf", OXF_PROCESSORS_URI);

    public final static QName IDENTITY_PROCESSOR_QNAME = new QName("identity", OXF_PROCESSORS_NAMESPACE);
    public final static QName REQUEST_PROCESSOR_QNAME = new QName("request", OXF_PROCESSORS_NAMESPACE);
    public final static QName NULL_PROCESSOR_QNAME = new QName("null", OXF_PROCESSORS_NAMESPACE);
    public final static QName NULL_SERIALIZER_PROCESSOR_QNAME = new QName("null-serializer", OXF_PROCESSORS_NAMESPACE);
    public final static QName PIPELINE_PROCESSOR_QNAME = new QName("pipeline", OXF_PROCESSORS_NAMESPACE);
    public final static QName REDIRECT_PROCESSOR_QNAME = new QName("redirect", OXF_PROCESSORS_NAMESPACE);
    public final static QName HTML_SERIALIZER_PROCESSOR_QNAME = new QName("html-serializer", OXF_PROCESSORS_NAMESPACE);
    public final static QName XFORMS_INPUT_PROCESSOR_QNAME = new QName("xforms-input", OXF_PROCESSORS_NAMESPACE);
    public final static QName XFORMS_SUBMISSION_PROCESSOR_QNAME = new QName("xforms-submission", OXF_PROCESSORS_NAMESPACE);
    public final static QName XFORMS_OUTPUT_PROCESSOR_QNAME = new QName("xforms-output", OXF_PROCESSORS_NAMESPACE);
    public final static QName XUPDATE_PROCESSOR_QNAME = new QName("xupdate", OXF_PROCESSORS_NAMESPACE);
    public final static QName UNSAFE_XSLT_PROCESSOR_QNAME = new QName("unsafe-xslt", OXF_PROCESSORS_NAMESPACE);
    public final static QName XSLT_PROCESSOR_QNAME = new QName("xslt", OXF_PROCESSORS_NAMESPACE);
    public final static QName XSLT10_PROCESSOR_QNAME = new QName("xslt-1.0", OXF_PROCESSORS_NAMESPACE);
    public final static QName XSLT20_PROCESSOR_QNAME = new QName("xslt-2.0", OXF_PROCESSORS_NAMESPACE);
    public final static QName PFC_XSLT10_PROCESSOR_QNAME = new QName("pfc-xslt-1.0", OXF_PROCESSORS_NAMESPACE);
    public final static QName PFC_XSLT20_PROCESSOR_QNAME = new QName("pfc-xslt-2.0", OXF_PROCESSORS_NAMESPACE);
    public final static QName INSTANCE_TO_PARAMETERS_PROCESSOR_QNAME = new QName("instance-to-parameters", OXF_PROCESSORS_NAMESPACE);
    public final static QName SCOPE_SERIALIZER_PROCESSOR_QNAME = new QName("scope-serializer", OXF_PROCESSORS_NAMESPACE);
    public final static QName RESOURCE_SERVER_PROCESSOR_QNAME = new QName("resource-server", OXF_PROCESSORS_NAMESPACE);
    public final static QName URL_GENERATOR_PROCESSOR_QNAME = new QName("url-generator", OXF_PROCESSORS_NAMESPACE);
    public final static QName DOM_GENERATOR_PROCESSOR_QNAME = new QName("dom-generator", OXF_PROCESSORS_NAMESPACE);

    public final static QName XS_STRING_QNAME = new QName("string", XSD_NAMESPACE);
    public final static QName XS_BOOLEAN_QNAME = new QName("boolean", XSD_NAMESPACE);
    public final static QName XS_INTEGER_QNAME = new QName("integer", XSD_NAMESPACE);
    public final static QName XS_DATE_QNAME = new QName("date", XSD_NAMESPACE);
    public final static QName XS_DATETIME_QNAME = new QName("dateTime", XSD_NAMESPACE);
    public final static QName XS_QNAME_QNAME = new QName("QName", XSD_NAMESPACE);
    public final static QName XS_ANYURI_QNAME = new QName("anyURI", XSD_NAMESPACE);
    public final static QName XS_BASE64BINARY_QNAME = new QName("base64Binary", XSD_NAMESPACE);
    public final static QName XS_NCNAME_QNAME = new QName("NCName", XSD_NAMESPACE);
    public final static QName XS_NMTOKEN_QNAME = new QName("NMTOKEN", XSD_NAMESPACE);
    public final static QName XS_NONNEGATIVEiNTEGER_QNAME 
        = new QName("nonNegativeInteger", XSD_NAMESPACE);
    
    public static final String XSI_NIL_ATTRIBUTE = "nil";

    public static final String XSLT_PREFIX = "xsl";
    public static final String XSLT_NAMESPACE = "http://www.w3.org/1999/XSL/Transform";

    public static final String XHTML_NAMESPACE_URI = "http://www.w3.org/1999/xhtml";

    private XMLConstants() {
        // Disallow contruction
    }
}
