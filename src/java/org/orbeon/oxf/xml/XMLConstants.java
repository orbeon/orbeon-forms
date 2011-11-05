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

import org.dom4j.Namespace;
import org.dom4j.QName;

public class XMLConstants {

    public static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";

    public static final String XML_PREFIX = "xml";
    public static final String XML_URI = "http://www.w3.org/XML/1998/namespace";
    public static final QName XML_BASE_QNAME = new QName("base", new Namespace(XML_PREFIX, XML_URI));
    public static final QName XML_LANG_QNAME = new QName("lang", new Namespace(XML_PREFIX, XML_URI));

    public static final String XSI_PREFIX = "xsi";
    public static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final QName XSI_TYPE_QNAME = new QName("type", new Namespace(XSI_PREFIX, XSI_URI));

    public static final String XSD_PREFIX = "xs";
    public static final String XSD_URI = "http://www.w3.org/2001/XMLSchema";
    public static final Namespace XSD_NAMESPACE = new Namespace(XSD_PREFIX, XSD_URI);

    public final static QName XML_SCHEMA_QNAME = new QName("schema", XSD_NAMESPACE);

    public static final String XINCLUDE_PREFIX = "xi";
    // NOTE: "2003" was used at some point; the correct value for XInclude 1.0 is "2001"
    public static final String XINCLUDE_URI = "http://www.w3.org/2001/XInclude";
    public static final String OLD_XINCLUDE_URI = "http://www.w3.org/2003/XInclude";
    public static final String XXINCLUDE_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xinclude";
    public static final Namespace XXINCLUDE_NAMESPACE = new Namespace("xxi", XXINCLUDE_NAMESPACE_URI);
    public static final QName XXINCLUDE_OMIT_XML_BASE = new QName("omit-xml-base", XXINCLUDE_NAMESPACE);
    public static final QName XINCLUDE_FIXUP_XML_BASE = new QName("fixup-xml-base", Namespace.NO_NAMESPACE);

    public static final Namespace XINCLUDE_NAMESPACE = new Namespace(XSD_PREFIX, XSD_URI);

    public static final String OPS_FORMATTING_URI = "http://orbeon.org/oxf/xml/formatting";
    public static final Namespace OPS_FORMATTING_NAMESPACE = new Namespace("f", XMLConstants.OPS_FORMATTING_URI);
    public static final String OXF_PROCESSORS_URI = "http://www.orbeon.com/oxf/processors";
    public static final Namespace OXF_PROCESSORS_NAMESPACE = new Namespace("oxf", OXF_PROCESSORS_URI);

    public final static QName FORMATTING_URL_TYPE_QNAME = new QName("url-type", OPS_FORMATTING_NAMESPACE);
    public final static QName FORMATTING_URL_NOREWRITE_QNAME = new QName("url-norewrite", OPS_FORMATTING_NAMESPACE);

    public final static QName IDENTITY_PROCESSOR_QNAME = new QName("identity", OXF_PROCESSORS_NAMESPACE);
    public final static QName REQUEST_PROCESSOR_QNAME = new QName("request", OXF_PROCESSORS_NAMESPACE);
    public final static QName NULL_PROCESSOR_QNAME = new QName("null", OXF_PROCESSORS_NAMESPACE);
    public final static QName NULL_SERIALIZER_PROCESSOR_QNAME = new QName("null-serializer", OXF_PROCESSORS_NAMESPACE);
    public final static QName PIPELINE_PROCESSOR_QNAME = new QName("pipeline", OXF_PROCESSORS_NAMESPACE);
    public final static QName PAGE_FLOW_PROCESSOR_QNAME = new QName("page-flow", OXF_PROCESSORS_NAMESPACE);
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
    public final static QName EXCEPTION_PROCESSOR_QNAME = new QName("exception", OXF_PROCESSORS_NAMESPACE);
    public final static QName ERROR_PROCESSOR_QNAME = new QName("error", OXF_PROCESSORS_NAMESPACE);
    public final static QName XINCLUDE_PROCESSOR_QNAME = new QName("xinclude", OXF_PROCESSORS_NAMESPACE);
    public final static QName SAX_DEBUGGER_PROCESSOR_QNAME = new QName("sax-debugger", OXF_PROCESSORS_NAMESPACE);
    public final static QName DEBUG_PROCESSOR_QNAME = new QName("debug", OXF_PROCESSORS_NAMESPACE);
    public final static QName PERL5_PROCESSOR_QNAME = new QName("perl5-matcher", OXF_PROCESSORS_NAMESPACE);

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
    public final static QName XS_NMTOKENS_QNAME = new QName("NMTOKENS", XSD_NAMESPACE);
    public final static QName XS_NONNEGATIVEINTEGER_QNAME = new QName("nonNegativeInteger", XSD_NAMESPACE);
    public static final QName XS_INT_QNAME = new QName("int", XSD_NAMESPACE);
    public static final QName XS_DECIMAL_QNAME = new QName("decimal", XSD_NAMESPACE);
    public static final QName XS_FLOAT_QNAME = new QName("float", XSD_NAMESPACE);
    public static final QName XS_DOUBLE_QNAME = new QName("double", XSD_NAMESPACE);

    public static final String XS_STRING_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XS_STRING_QNAME);
    public static final String XS_ANYURI_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XS_ANYURI_QNAME);
    public static final String XS_BASE64BINARY_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XS_BASE64BINARY_QNAME);

    public static final String OPS_TYPES_URI = "http://orbeon.org/oxf/xml/datatypes";
    public static final QName OPS_XMLFRAGMENT_QNAME = new QName("xmlFragment", new Namespace("ops", OPS_TYPES_URI));

    public static final Namespace XSI_NAMESPACE = new Namespace(XSI_PREFIX, XSI_URI);
    public static final String XSI_NIL_ATTRIBUTE = "nil";
    public static final QName XSI_NIL_QNAME = new QName(XSI_NIL_ATTRIBUTE, XSI_NAMESPACE);

    public static final String XSLT_PREFIX = "xsl";
    public static final String XSLT_NAMESPACE_URI = "http://www.w3.org/1999/XSL/Transform";
    public static final Namespace XSLT_NAMESPACE = new Namespace("xsl", XSLT_NAMESPACE_URI);
    public static final QName XSLT_VERSION_QNAME = new QName("version", XSLT_NAMESPACE);

    public static final String XHTML_PREFIX = "xhtml";
    public static final String XHTML_NAMESPACE_URI = "http://www.w3.org/1999/xhtml";
    public static final Namespace XHTML_NAMESPACE = new Namespace(XHTML_PREFIX, XHTML_NAMESPACE_URI);
    public static final QName XHTML_HTML_QNAME = new QName("html", XMLConstants.XHTML_NAMESPACE);

    public static final String XPATH_FUNCTIONS_NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions";

    public static final String NBSP = "\u00a0";

    public static final String SAX_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";

    private XMLConstants() {
        // Disallow contruction
    }
}
