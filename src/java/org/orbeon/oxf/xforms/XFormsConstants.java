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
package org.orbeon.oxf.xforms;

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.xml.XMLConstants;

/**
 * Constants useful for the XForms engine. 
 */
public class XFormsConstants {

    public static final String XFORMS_PREFIX = "xforms";
    public static final String XFORMS_SHORT_PREFIX = "xf";
    public static final String XFORMS_NAMESPACE_URI = "http://www.w3.org/2002/xforms";
    public static final Namespace XFORMS_NAMESPACE = new Namespace(XFORMS_PREFIX, XFORMS_NAMESPACE_URI);

    public static final String XXFORMS_PREFIX = "xxforms";
    public static final String XXFORMS_SHORT_PREFIX = "xxf";
    public static final String XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
    public static final Namespace XXFORMS_NAMESPACE = new Namespace(XXFORMS_PREFIX, XXFORMS_NAMESPACE_URI);

    // eXForms at http://www.exforms.org/
    public static final String EXFORMS_NAMESPACE_URI = "http://www.exforms.org/exf/1-0";
    public static final String EXFORMS_PREFIX = "exf";
    public static final Namespace EXFORMS_NAMESPACE = new Namespace(EXFORMS_PREFIX, EXFORMS_NAMESPACE_URI);
    public static final QName EXFORMS_IF_ATTRIBUTE_QNAME = new QName("if", EXFORMS_NAMESPACE);
    public static final QName EXFORMS_WHILE_ATTRIBUTE_QNAME = new QName("while", EXFORMS_NAMESPACE);

    public static final String XML_EVENTS_PREFIX = "ev";
    public static final String XML_EVENTS_NAMESPACE_URI = "http://www.w3.org/2001/xml-events";
    public static final Namespace XML_EVENTS_NAMESPACE = new Namespace(XML_EVENTS_PREFIX, XML_EVENTS_NAMESPACE_URI);

    public static final QName XML_EVENTS_EVENT_ATTRIBUTE_QNAME = new QName("event", XFormsConstants.XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME = new QName("observer", XFormsConstants.XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_TARGET_ATTRIBUTE_QNAME = new QName("target", XFormsConstants.XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_HANDLER_ATTRIBUTE_QNAME = new QName("handler", XFormsConstants.XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_PHASE_ATTRIBUTE_QNAME = new QName("phase", XFormsConstants.XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME = new QName("propagate", XFormsConstants.XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME = new QName("defaultAction", XFormsConstants.XML_EVENTS_NAMESPACE);

    public static final String XFORMS_FILENAME_ELEMENT_NAME = "filename";
    public static final QName XFORMS_FILENAME_ELEMENT_QNAME = new QName(XFORMS_FILENAME_ELEMENT_NAME, XFORMS_NAMESPACE);
    public static final String XFORMS_MEDIATYPE_ELEMENT_NAME = "mediatype";
    public static final QName XFORMS_MEDIATYPE_ELEMENT_QNAME = new QName(XFORMS_MEDIATYPE_ELEMENT_NAME, XFORMS_NAMESPACE);
    public static final String XXFORMS_SIZE_ELEMENT_NAME = "size";
    public static final QName XXFORMS_SIZE_ELEMENT_QNAME = new QName(XXFORMS_SIZE_ELEMENT_NAME, XXFORMS_NAMESPACE);

    public static final String XXFORMS_VALID_ATTRIBUTE_NAME = "valid";
    public static final QName XXFORMS_VALID_ATTRIBUTE_QNAME = new QName(XXFORMS_VALID_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);
    public static final String XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_NAME = "invalid-bind-ids";
    public static final QName XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_QNAME = new QName(XXFORMS_INVALID_BIND_IDS_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);
    public static final String XXFORMS_RELEVANT_ATTRIBUTE_NAME = "relevant";
    public static final QName XXFORMS_RELEVANT_ATTRIBUTE_QNAME = new QName(XXFORMS_RELEVANT_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);
    public static final String XXFORMS_REQUIRED_ATTRIBUTE_NAME = "required";
    public static final QName XXFORMS_REQUIRED_ATTRIBUTE_QNAME = new QName(XXFORMS_REQUIRED_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);
    public static final String XXFORMS_READONLY_ATTRIBUTE_NAME = "readonly";
    public static final QName XXFORMS_READONLY_ATTRIBUTE_QNAME = new QName(XXFORMS_READONLY_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);
    public static final String XXFORMS_TYPE_ATTRIBUTE_NAME = "type";
    public static final QName XXFORMS_TYPE_ATTRIBUTE_QNAME = new QName(XXFORMS_TYPE_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);
    public static final String XXFORMS_NODE_IDS_ATTRIBUTE_NAME = "node-ids";
    public static final QName XXFORMS_NODE_IDS_ATTRIBUTE_QNAME = new QName(XXFORMS_READONLY_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);

    public static final QName XXFORMS_STATIC_STATE_QNAME = new QName("static-state", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DYNAMIC_STATE_QNAME = new QName("dynamic-state", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ACTION_QNAME = new QName("action", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_FILES_QNAME = new QName("files", XFormsConstants.XXFORMS_NAMESPACE);

    public static final QName XXFORMS_CONTROLS_QNAME = new QName("controls", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_MODELS_QNAME = new QName("models", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INSTANCES_QNAME = new QName("instances", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENT_QNAME = new QName("event", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DIVS_QNAME = new QName("divs", XFormsConstants.XXFORMS_NAMESPACE);

    public static final QName XFORMS_LABEL_QNAME = new QName("label", XFormsConstants.XFORMS_NAMESPACE);
    public static final QName XFORMS_HELP_QNAME = new QName("help", XFormsConstants.XFORMS_NAMESPACE);
    public static final QName XFORMS_HINT_QNAME = new QName("hint", XFormsConstants.XFORMS_NAMESPACE);
    public static final QName XFORMS_ALERT_QNAME = new QName("alert", XFormsConstants.XFORMS_NAMESPACE);
    public static final QName XFORMS_VALUE_QNAME = new QName("value", XFormsConstants.XFORMS_NAMESPACE);
    public static final QName XFORMS_COPY_QNAME = new QName("copy", XFormsConstants.XFORMS_NAMESPACE);
    public static final QName XFORMS_ITEMSET_QNAME = new QName("itemset", XFormsConstants.XFORMS_NAMESPACE);
    public static final QName XFORMS_OUTPUT_QNAME = new QName("output", XFormsConstants.XFORMS_NAMESPACE);

    public static final String XFORMS_SUBMIT_REPLACE_ALL = "all";
    public static final String XFORMS_SUBMIT_REPLACE_INSTANCE = "instance";
    public static final String XFORMS_SUBMIT_REPLACE_NONE = "none";

    public static final String XFORMS_PASSWORD_PROPERTY = "oxf.xforms.password";
    public static final String XFORMS_ENCRYPT_NAMES_PROPERTY = "oxf.xforms.encrypt-names";
    public static final String XFORMS_ENCRYPT_HIDDEN_PROPERTY = "oxf.xforms.encrypt-hidden";
    public static final String XFORMS_VALIDATION_PROPERTY = "oxf.xforms.validate";

    public static final String XFORMS_DEFAULT_DATE_FORMAT_PROPERTY = "oxf.xforms.format.date";
    public static final String XFORMS_DEFAULT_DATETIME_FORMAT_PROPERTY = "oxf.xforms.format.dateTime";
    public static final String XFORMS_DEFAULT_TIME_FORMAT_PROPERTY = "oxf.xforms.format.time";
    public static final String XFORMS_DEFAULT_DECIMAL_FORMAT_PROPERTY = "oxf.xforms.format.decimal";
    public static final String XFORMS_DEFAULT_INTEGER_FORMAT_PROPERTY = "oxf.xforms.format.integer";
    public static final String XFORMS_DEFAULT_FLOAT_FORMAT_PROPERTY = "oxf.xforms.format.float";
    public static final String XFORMS_DEFAULT_DOUBLE_FORMAT_PROPERTY = "oxf.xforms.format.double";

    public static final String XFORMS_STATE_HANDLING_PROPERTY = "oxf.xforms.state-handling";
    public static final String XFORMS_CACHE_DOCUMENT_PROPERTY = "oxf.xforms.cache.document";
    public static final String XFORMS_CACHE_SESSION_SIZE_PROPERTY = "oxf.xforms.cache.session.size";
    public static final String XFORMS_CACHE_APPLICATION_SIZE_PROPERTY = "oxf.xforms.cache.application.size";
    public static final String XFORMS_OPTIMIZE_POST_ALL_PROPERTY = "oxf.xforms.optimize-post-all";
    public static final String XFORMS_OPTIMIZE_GET_ALL_PROPERTY = "oxf.xforms.optimize-get-all";
    public static final String XFORMS_OPTIMIZE_LOCAL_SUBMISSION_PROPERTY = "oxf.xforms.optimize-local-submission";
    public static final String XFORMS_OPTIMIZE_LOCAL_INSTANCE_LOADS_PROPERTY = "oxf.xforms.optimize-local-instance-loads";
    public static final String XFORMS_AJAX_SHOW_LOADING_ICON_PROPERTY = "oxf.xforms.ajax.show-loading-icon";
    public static final String XFORMS_AJAX_SHOW_ERRORS_PROPERTY = "oxf.xforms.ajax.show-errors";
    public static final String XFORMS_EXCEPTION_INVALID_CLIENT_CONTROL_PROPERTY = "oxf.xforms.exception-invalid-client-control";
    public static final String XFORMS_GZIP_STATE_PROPERTY = "oxf.xforms.gzip-state";
    public static final String XFORMS_HOST_LANGUAGE_AVTS_PROPERTY = "oxf.xforms.host-language-avts";
    public static final String XFORMS_MINIMAL_RESOURCES_PROPERTY = "oxf.xforms.minimal-resources";

    public static final String XXFORMS_STATE_HANDLING_ATTRIBUTE_NAME = "state-handling";
    public static final String XXFORMS_STATE_HANDLING_CLIENT_VALUE = "client";
    public static final String XXFORMS_STATE_HANDLING_SESSION_VALUE = "session";

    public static final String XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME = "readonly-appearance";
    public static final String XXFORMS_READONLY_APPEARANCE_STATIC_VALUE = "static";
    public static final String XXFORMS_READONLY_APPEARANCE_DYNAMIC_VALUE = "dynamic";

    public static final QName XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME = new QName(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME, XFormsConstants.XXFORMS_NAMESPACE);

    public static final String XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME = "external-events";

    public static final QName XFORMS_FULL_APPEARANCE_QNAME = new QName("full");
    public static final QName XFORMS_COMPACT_APPEARANCE_QNAME = new QName("compact");
    public static final QName XFORMS_MINIMAL_APPEARANCE_QNAME = new QName("minimal");

    public static final QName XXFORMS_TREE_APPEARANCE_QNAME = new QName("tree", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_MENU_APPEARANCE_QNAME = new QName("menu", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME = new QName("autocomplete", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_AUTOSIZE_APPEARANCE_QNAME = new QName("autosize", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LINK_APPEARANCE_QNAME = new QName("link", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_IMAGE_APPEARANCE_QNAME = new QName("image", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_HTML_APPEARANCE_QNAME = new QName("html", XFormsConstants.XXFORMS_NAMESPACE);// legacy

    public static final QName XXFORMS_TARGET_QNAME = new QName("target", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EXTERNALIZE_QNAME = new QName("externalize", XFormsConstants.XXFORMS_NAMESPACE);

    public static final QName XXFORMS_USERNAME_QNAME = new QName("username", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PASSWORD_QNAME = new QName("password", XFormsConstants.XXFORMS_NAMESPACE);

    public static final QName XXFORMS_INSTANCE_QNAME = new QName("instance", XFormsConstants.XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SHOW_PROGRESS_QNAME = new QName("show-progress", XFormsConstants.XXFORMS_NAMESPACE);

    public static final char REPEAT_HIERARCHY_SEPARATOR_1 = 0xB7;
    public static final char REPEAT_HIERARCHY_SEPARATOR_2 = '-';
    public static final String DEFAULT_UPLOAD_TYPE_EXPLODED_QNAME = ProcessorUtils.XS_ANYURI_EXPLODED_QNAME;
    public static final QName DEFAULT_UPLOAD_TYPE_QNAME = XMLConstants.XS_ANYURI_QNAME;
    public static final int DEFAULT_SESSION_STATE_CACHE_SIZE = 1024 * 1024;
    public static final int DEFAULT_APPLICATION_STATE_CACHE_SIZE = 1024 * 1024;
    public static final boolean DEFAULT_GZIP_STATE = true;
    public static final boolean DEFAULT_HOST_LANGUAGE_AVTS = false;
    public static final boolean DEFAULT_MINIMAL_RESOURCES = false;

    public static final String DUMMY_IMAGE_URI = "/ops/images/xforms/spacer.gif";

    private XFormsConstants() {
        // Disallow contruction
    }
}
