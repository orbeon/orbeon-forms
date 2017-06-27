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
package org.orbeon.oxf.xml;

import org.orbeon.dom.Namespace;
import org.orbeon.dom.Namespace$;
import org.orbeon.dom.QName;

public class XMLConstants {

    public static final String XML_PREFIX = "xml";
    public static final String XML_URI = "http://www.w3.org/XML/1998/namespace";
    public static final QName XML_BASE_QNAME = QName.get("base", Namespace$.MODULE$.apply(XML_PREFIX, XML_URI));
    public static final QName XML_LANG_QNAME = QName.get("lang", Namespace$.MODULE$.apply(XML_PREFIX, XML_URI));

    public static final String XSI_PREFIX = "xsi";
    public static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final QName XSI_TYPE_QNAME = QName.get("type", Namespace$.MODULE$.apply(XSI_PREFIX, XSI_URI));

    public static final String XSD_PREFIX = "xs";
    public static final String XSD_URI = "http://www.w3.org/2001/XMLSchema";
    public static final Namespace XSD_NAMESPACE = Namespace$.MODULE$.apply(XSD_PREFIX, XSD_URI);

    public final static QName XML_SCHEMA_QNAME = QName.get("schema", XSD_NAMESPACE);

    public static final String OPS_FORMATTING_URI = "http://orbeon.org/oxf/xml/formatting";
    public static final Namespace OPS_FORMATTING_NAMESPACE = Namespace$.MODULE$.apply("f", XMLConstants.OPS_FORMATTING_URI);
    public static final String OXF_PROCESSORS_URI = "http://www.orbeon.com/oxf/processors";
    public static final Namespace OXF_PROCESSORS_NAMESPACE = Namespace$.MODULE$.apply("oxf", OXF_PROCESSORS_URI);

    public final static QName FORMATTING_URL_TYPE_QNAME = QName.get("url-type", OPS_FORMATTING_NAMESPACE);
    public final static QName FORMATTING_URL_NOREWRITE_QNAME = QName.get("url-norewrite", OPS_FORMATTING_NAMESPACE);

    public final static QName IDENTITY_PROCESSOR_QNAME = QName.get("identity", OXF_PROCESSORS_NAMESPACE);
    public final static QName REQUEST_PROCESSOR_QNAME = QName.get("request", OXF_PROCESSORS_NAMESPACE);
    public final static QName NULL_PROCESSOR_QNAME = QName.get("null", OXF_PROCESSORS_NAMESPACE);
    public final static QName NULL_SERIALIZER_PROCESSOR_QNAME = QName.get("null-serializer", OXF_PROCESSORS_NAMESPACE);
    public final static QName PIPELINE_PROCESSOR_QNAME = QName.get("pipeline", OXF_PROCESSORS_NAMESPACE);
    public final static QName PAGE_FLOW_PROCESSOR_QNAME = QName.get("page-flow", OXF_PROCESSORS_NAMESPACE);
    public final static QName REDIRECT_PROCESSOR_QNAME = QName.get("redirect", OXF_PROCESSORS_NAMESPACE);
    public final static QName HTML_SERIALIZER_PROCESSOR_QNAME = QName.get("html-serializer", OXF_PROCESSORS_NAMESPACE);
    public final static QName UNSAFE_XSLT_PROCESSOR_QNAME = QName.get("unsafe-xslt", OXF_PROCESSORS_NAMESPACE);
    public final static QName XSLT_PROCESSOR_QNAME = QName.get("xslt", OXF_PROCESSORS_NAMESPACE);
    public final static QName PFC_XSLT10_PROCESSOR_QNAME = QName.get("pfc-xslt-1.0", OXF_PROCESSORS_NAMESPACE);
    public final static QName PFC_XSLT20_PROCESSOR_QNAME = QName.get("pfc-xslt-2.0", OXF_PROCESSORS_NAMESPACE);
    public final static QName INSTANCE_TO_PARAMETERS_PROCESSOR_QNAME = QName.get("instance-to-parameters", OXF_PROCESSORS_NAMESPACE);
    public final static QName URL_GENERATOR_PROCESSOR_QNAME = QName.get("url-generator", OXF_PROCESSORS_NAMESPACE);
    public final static QName DOM_GENERATOR_PROCESSOR_QNAME = QName.get("dom-generator", OXF_PROCESSORS_NAMESPACE);
    public final static QName ERROR_PROCESSOR_QNAME = QName.get("error", OXF_PROCESSORS_NAMESPACE);
    public final static QName XINCLUDE_PROCESSOR_QNAME = QName.get("xinclude", OXF_PROCESSORS_NAMESPACE);
    public final static QName DEBUG_PROCESSOR_QNAME = QName.get("debug", OXF_PROCESSORS_NAMESPACE);

    public final static QName XS_STRING_QNAME = QName.get("string", XSD_NAMESPACE);
    public final static QName XS_BOOLEAN_QNAME = QName.get("boolean", XSD_NAMESPACE);
    public final static QName XS_INTEGER_QNAME = QName.get("integer", XSD_NAMESPACE);
    public final static QName XS_DATE_QNAME = QName.get("date", XSD_NAMESPACE);
    public final static QName XS_DATETIME_QNAME = QName.get("dateTime", XSD_NAMESPACE);
    public final static QName XS_QNAME_QNAME = QName.get("QName", XSD_NAMESPACE);
    public final static QName XS_ANYURI_QNAME = QName.get("anyURI", XSD_NAMESPACE);
    public final static QName XS_BASE64BINARY_QNAME = QName.get("base64Binary", XSD_NAMESPACE);
    public final static QName XS_NCNAME_QNAME = QName.get("NCName", XSD_NAMESPACE);
    public final static QName XS_NMTOKEN_QNAME = QName.get("NMTOKEN", XSD_NAMESPACE);
    public final static QName XS_NMTOKENS_QNAME = QName.get("NMTOKENS", XSD_NAMESPACE);
    public final static QName XS_NONNEGATIVEINTEGER_QNAME = QName.get("nonNegativeInteger", XSD_NAMESPACE);
    public static final QName XS_INT_QNAME = QName.get("int", XSD_NAMESPACE);
    public static final QName XS_DECIMAL_QNAME = QName.get("decimal", XSD_NAMESPACE);
    public static final QName XS_FLOAT_QNAME = QName.get("float", XSD_NAMESPACE);
    public static final QName XS_DOUBLE_QNAME = QName.get("double", XSD_NAMESPACE);

    public static final String OPS_TYPES_URI = "http://orbeon.org/oxf/xml/datatypes";
    public static final QName OPS_XMLFRAGMENT_QNAME = QName.get("xmlFragment", Namespace$.MODULE$.apply("ops", OPS_TYPES_URI));

    public static final Namespace XSI_NAMESPACE = Namespace$.MODULE$.apply(XSI_PREFIX, XSI_URI);
    public static final String XSI_NIL_ATTRIBUTE = "nil";
    public static final QName XSI_NIL_QNAME = QName.get(XSI_NIL_ATTRIBUTE, XSI_NAMESPACE);

    public static final String XSLT_PREFIX = "xsl";
    public static final String XSLT_NAMESPACE_URI = "http://www.w3.org/1999/XSL/Transform";
    public static final Namespace XSLT_NAMESPACE = Namespace$.MODULE$.apply("xsl", XSLT_NAMESPACE_URI);
    public static final QName XSLT_VERSION_QNAME = QName.get("version", XSLT_NAMESPACE);

    public static final String XHTML_PREFIX = "xhtml";
    public static final String XHTML_SHORT_PREFIX = "xh";
    public static final String XHTML_NAMESPACE_URI = "http://www.w3.org/1999/xhtml";

    public static final String XPATH_FUNCTIONS_NAMESPACE_URI     = "http://www.w3.org/2005/xpath-functions";
    public static final String XPATH_MAP_FUNCTIONS_NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/map";

    public static final String SAX_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";

    private XMLConstants() {
        // Disallow construction
    }
}
