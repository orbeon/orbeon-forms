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
package org.orbeon.oxf.xforms;

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.Item;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Constants useful for the XForms engine.
 */
public class XFormsConstants {

    public static final List<Item> EMPTY_ITEM_LIST = Collections.emptyList();

    public static final Set<String> ALLOWED_XXFORMS_ELEMENTS = new HashSet<String>();
    public static final Set<String> ALLOWED_EXFORMS_ELEMENTS = new HashSet<String>();
    public static final Set<String> ALLOWED_XBL_ELEMENTS = new HashSet<String>();
    public static final Set<String> LABEL_HINT_HELP_ALERT_ELEMENT = new HashSet<String>();

    public enum LHHA {
        LABEL, HELP, HINT, ALERT
    }

    static {
        // TODO: Keeping this static list is not ideal
        ALLOWED_XXFORMS_ELEMENTS.add("dialog");
        ALLOWED_XXFORMS_ELEMENTS.add("var");
        ALLOWED_XXFORMS_ELEMENTS.add("variable");
        ALLOWED_XXFORMS_ELEMENTS.add("sequence");
        ALLOWED_XXFORMS_ELEMENTS.add("attribute");
        ALLOWED_XXFORMS_ELEMENTS.add("text");
        ALLOWED_XXFORMS_ELEMENTS.add("context");
        ALLOWED_XXFORMS_ELEMENTS.add("size");//xforms:upload/xxforms:size

        ALLOWED_XXFORMS_ELEMENTS.add("dynamic");

        ALLOWED_EXFORMS_ELEMENTS.add("variable");

        ALLOWED_XBL_ELEMENTS.add("xbl");
        ALLOWED_XBL_ELEMENTS.add("binding");
        ALLOWED_XBL_ELEMENTS.add("handlers");
        ALLOWED_XBL_ELEMENTS.add("handler");// just for the case of top-level <xbl:handler>
        ALLOWED_XBL_ELEMENTS.add("implementation");
        ALLOWED_XBL_ELEMENTS.add("template");

        XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.add("label");
        XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.add("hint");
        XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.add("help");
        XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.add("alert");
    }

    public static final String XFORMS_PREFIX = "xforms";
    public static final String XFORMS_SHORT_PREFIX = "xf";
    public static final String XFORMS_NAMESPACE_URI = "http://www.w3.org/2002/xforms";
    public static final Namespace XFORMS_NAMESPACE = new Namespace(XFORMS_PREFIX, XFORMS_NAMESPACE_URI);

    public static final String XXFORMS_PREFIX = "xxforms";
    public static final String XXFORMS_SHORT_PREFIX = "xxf";
    public static final String XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
    public static final Namespace XXFORMS_NAMESPACE = new Namespace(XXFORMS_PREFIX, XXFORMS_NAMESPACE_URI);

    // Common attributes
    public static final QName REF_QNAME = new QName("ref");
    public static final QName NODESET_QNAME = new QName("nodeset");
    public static final QName CONTEXT_QNAME = new QName("context");
    public static final QName BIND_QNAME = new QName("bind");
    public static final QName VALUE_QNAME = new QName("value");
    public static final QName MODEL_QNAME = new QName("model");

    public static final QName ID_QNAME = new QName("id");
    public static final QName NAME_QNAME = new QName("name");

    public static final QName CLASS_QNAME = new QName("class");
    public static final QName STYLE_QNAME = new QName("style");

    public static final QName APPEARANCE_QNAME = new QName("appearance");
    public static final QName MEDIATYPE_QNAME = new QName("mediatype");
    public static final QName SRC_QNAME = new QName("src");

    public static final QName TARGETID_QNAME = new QName("targetid");
    public static final QName TARGET_QNAME = new QName("target");
    public static final QName SELECT_QNAME = new QName("select");
    public static final QName FOR_QNAME = new QName("for");

    public static final QName SCHEMA_QNAME = new QName("schema");
    public static final QName RESOURCE_QNAME = new QName("resource");
    public static final QName SUBMISSION_QNAME = new QName("submission");

    // XForms controls
    public static final QName XFORMS_GROUP_QNAME = new QName("group", XFORMS_NAMESPACE);
    public static final QName XFORMS_REPEAT_QNAME = new QName("repeat", XFORMS_NAMESPACE);
    public static final String REPEAT_NAME = XFORMS_REPEAT_QNAME.getName();
    public static final QName XFORMS_SWITCH_QNAME = new QName("switch", XFORMS_NAMESPACE);
    public static final QName XFORMS_CASE_QNAME = new QName("case", XFORMS_NAMESPACE);
    public static final QName XXFORMS_DIALOG_QNAME = new QName("dialog", XXFORMS_NAMESPACE);
    public static final String XXFORMS_DIALOG_NAME = XXFORMS_DIALOG_QNAME.getName();
    public static final QName XXFORMS_DYNAMIC_QNAME = new QName("dynamic", XXFORMS_NAMESPACE);

    public static final QName XFORMS_INPUT_QNAME = new QName("input", XFORMS_NAMESPACE);
    public static final QName XFORMS_SECRET_QNAME = new QName("secret", XFORMS_NAMESPACE);
    public static final QName XFORMS_TEXTAREA_QNAME = new QName("textarea", XFORMS_NAMESPACE);
    public static final QName XFORMS_OUTPUT_QNAME = new QName("output", XFORMS_NAMESPACE);
    public static final QName XFORMS_UPLOAD_QNAME = new QName("upload", XFORMS_NAMESPACE);
    public static final String UPLOAD_NAME = XFORMS_UPLOAD_QNAME.getName();
    public static final QName XFORMS_RANGE_QNAME = new QName("range", XFORMS_NAMESPACE);
    public static final QName XFORMS_SELECT_QNAME = new QName("select", XFORMS_NAMESPACE);
    public static final QName XFORMS_SELECT1_QNAME = new QName("select1", XFORMS_NAMESPACE);

    public static final QName XXFORMS_ATTRIBUTE_QNAME = new QName("attribute", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TEXT_QNAME = new QName("text", XXFORMS_NAMESPACE);

    public static final QName XFORMS_SUBMIT_QNAME = new QName("submit", XFORMS_NAMESPACE);
    public static final QName XFORMS_TRIGGER_QNAME = new QName("trigger", XFORMS_NAMESPACE);

    // XForms QNames
    public static final QName XFORMS_BIND_QNAME = new QName("bind", XFORMS_NAMESPACE);

    // eXForms at http://www.exforms.org/
    public static final String EXFORMS_NAMESPACE_URI = "http://www.exforms.org/exf/1-0";
    public static final String EXFORMS_PREFIX = "exf";
    public static final Namespace EXFORMS_NAMESPACE = new Namespace(EXFORMS_PREFIX, EXFORMS_NAMESPACE_URI);
    public static final QName EXFORMS_IF_ATTRIBUTE_QNAME = new QName("if", EXFORMS_NAMESPACE);
    public static final QName EXFORMS_WHILE_ATTRIBUTE_QNAME = new QName("while", EXFORMS_NAMESPACE);
    public static final QName EXFORMS_ITERATE_ATTRIBUTE_QNAME = new QName("iterate", EXFORMS_NAMESPACE);

    // XBL
    public static final String XBL_PREFIX = "xbl";
    public static final String XBL_NAMESPACE_URI = "http://www.w3.org/ns/xbl";
    public static final Namespace XBL_NAMESPACE = new Namespace(XBL_PREFIX, XBL_NAMESPACE_URI);
    public static final QName XBL_XBL_QNAME = new QName("xbl", XBL_NAMESPACE);
    public static final QName XBL_BINDING_QNAME = new QName("binding", XBL_NAMESPACE);
    public static final QName XBL_SCRIPT_QNAME = new QName("script", XBL_NAMESPACE);
    public static final QName XBL_RESOURCES_QNAME = new QName("resources", XBL_NAMESPACE);
    public static final QName XBL_STYLE_QNAME = new QName("style", XBL_NAMESPACE);
    public static final QName XBL_TEMPLATE_QNAME = new QName("template", XBL_NAMESPACE);
    public static final QName XBL_HANDLERS_QNAME = new QName("handlers", XBL_NAMESPACE);
    public static final QName XBL_HANDLER_QNAME = new QName("handler", XBL_NAMESPACE);
    public static final QName XBL_IMPLEMENTATION_QNAME = new QName("implementation", XBL_NAMESPACE);

    public static final QName XBL_HANDLER_EVENT_ATTRIBUTE_QNAME = new QName("event");
    public static final QName XBL_HANDLER_PHASE_ATTRIBUTE_QNAME = new QName("phase");
    public static final QName XBL_HANDLER_PROPAGATE_ATTRIBUTE_QNAME = new QName("propagate");
    // NOTE: XBL 2 working draft has "default-action" instead
    public static final QName XBL_HANDLER_DEFAULT_ACTION_ATTRIBUTE_QNAME = new QName("defaultAction");

    public static final QName ELEMENT_QNAME = new QName("element");
    public static final QName INCLUDES_QNAME = new QName("includes");

    // XBL extensions
    public static final String XXBL_PREFIX = "xxbl";
    public static final String XXBL_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xbl";
    public static final Namespace XXBL_NAMESPACE = new Namespace(XXBL_PREFIX, XXBL_NAMESPACE_URI);
    public static final QName XXBL_TRANSFORM_QNAME = new QName("transform", XXBL_NAMESPACE);
    public static final QName XXBL_SCOPE_QNAME = new QName("scope", XXBL_NAMESPACE);
    public static final QName XXBL_CONTAINER_QNAME = new QName("container", XXBL_NAMESPACE);
    public static final QName XXBL_GLOBAL_QNAME = new QName("global", XXBL_NAMESPACE);

    public enum XXBLScope { inner, outer }
    public enum DeploymentType { separate, integrated, standalone}

    // Variables
    public static final QName XXFORMS_VAR_QNAME = new QName("var", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VARIABLE_QNAME = new QName("variable", XXFORMS_NAMESPACE);
    public static final QName XFORMS_VAR_QNAME = new QName("var", XFORMS_NAMESPACE);
    public static final QName XFORMS_VARIABLE_QNAME = new QName("variable", XFORMS_NAMESPACE);
    public static final QName EXFORMS_VARIABLE_QNAME = new QName("variable", EXFORMS_NAMESPACE);
    public static final QName XXFORMS_SEQUENCE_QNAME = new QName("sequence", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_ITERATE_ATTRIBUTE_QNAME = new QName("iterate", XXFORMS_NAMESPACE);

    public static final String XML_EVENTS_PREFIX = "ev";
    public static final String XML_EVENTS_NAMESPACE_URI = "http://www.w3.org/2001/xml-events";
    public static final Namespace XML_EVENTS_NAMESPACE = new Namespace(XML_EVENTS_PREFIX, XML_EVENTS_NAMESPACE_URI);

    public static final QName XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME = new QName("event", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME = new QName("observer", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME = new QName("target", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_EV_HANDLER_ATTRIBUTE_QNAME = new QName("handler", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME = new QName("phase", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME = new QName("propagate", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME = new QName("defaultAction", XML_EVENTS_NAMESPACE);

    public static final QName XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME = new QName("modifiers", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME = new QName("text", XXFORMS_NAMESPACE);

    public static final String XXFORMS_ALL_EVENTS = "#all";

    public static final QName XFORMS_FILENAME_QNAME = new QName("filename", XFORMS_NAMESPACE);
    public static final QName XFORMS_MEDIATYPE_QNAME = new QName("mediatype", XFORMS_NAMESPACE);
    public static final QName XXFORMS_SIZE_QNAME = new QName("size", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_MAXLENGTH_QNAME = new QName("maxlength", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_AUTOCOMPLETE_QNAME = new QName("autocomplete", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_COLS_QNAME = new QName("cols", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ROWS_QNAME = new QName("rows", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_GROUP_QNAME = new QName("group", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ELEMENT_QNAME = new QName("element", XXFORMS_NAMESPACE);

    public static final String VALID_ATTRIBUTE_NAME = "valid";
    public static final String RELEVANT_ATTRIBUTE_NAME = "relevant";
    public static final String REQUIRED_ATTRIBUTE_NAME = "required";
    public static final String READONLY_ATTRIBUTE_NAME = "readonly";

    // MIPs
    public static final QName RELEVANT_QNAME = new QName(RELEVANT_ATTRIBUTE_NAME);
    public static final QName CALCULATE_QNAME = new QName("calculate");
    public static final QName READONLY_QNAME = new QName(READONLY_ATTRIBUTE_NAME);
    public static final QName REQUIRED_QNAME = new QName(REQUIRED_ATTRIBUTE_NAME);
    public static final QName TYPE_QNAME = new QName("type");
    public static final QName CONSTRAINT_QNAME = new QName("constraint");
    public static final QName XXFORMS_DEFAULT_QNAME = new QName("default", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DEFAULTS_QNAME = new QName("defaults", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_READONLY_ATTRIBUTE_QNAME = new QName(READONLY_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);

    public static final QName XXFORMS_UUID_QNAME = new QName("uuid", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_STATIC_STATE_QNAME = new QName("static-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DYNAMIC_STATE_QNAME = new QName("dynamic-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INITIAL_DYNAMIC_STATE_QNAME = new QName("initial-dynamic-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ACTION_QNAME = new QName("action", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SERVER_EVENTS_QNAME = new QName("server-events", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_FILES_QNAME = new QName("files", XXFORMS_NAMESPACE);

    public static final QName XFORMS_MODEL_QNAME = new QName("model", XFORMS_NAMESPACE);
    public static final QName XFORMS_INSTANCE_QNAME = new QName("instance", XFORMS_NAMESPACE);
    public static final QName XFORMS_SUBMISSION_QNAME = new QName("submission", XFORMS_NAMESPACE);
    public static final QName XFORMS_HEADER_QNAME = new QName("header", XFORMS_NAMESPACE);

    public static final QName XXFORMS_CONTROLS_QNAME = new QName("controls", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_MODELS_QNAME = new QName("models", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INSTANCES_QNAME = new QName("instances", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENT_QNAME = new QName("event", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_QNAME = new QName("events", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DIVS_QNAME = new QName("divs", XXFORMS_NAMESPACE);

    public static final QName LABEL_QNAME = new QName("label", XFORMS_NAMESPACE);
    public static final QName HELP_QNAME = new QName("help", XFORMS_NAMESPACE);
    public static final QName HINT_QNAME = new QName("hint", XFORMS_NAMESPACE);
    public static final QName ALERT_QNAME = new QName("alert", XFORMS_NAMESPACE);
    public static final QName XFORMS_NAME_QNAME = new QName("name", XFORMS_NAMESPACE);
    public static final QName XFORMS_VALUE_QNAME = new QName("value", XFORMS_NAMESPACE);
    public static final QName COPY_QNAME = new QName("copy", XFORMS_NAMESPACE);
    public static final QName ITEMSET_QNAME = new QName("itemset", XFORMS_NAMESPACE);
    public static final QName ITEM_QNAME = new QName("item", XFORMS_NAMESPACE);
    public static final QName CHOICES_QNAME = new QName("choices", XFORMS_NAMESPACE);
    public static final QName LOAD_QNAME = new QName("load", XFORMS_NAMESPACE);

    public static final String XFORMS_SUBMIT_REPLACE_ALL = "all";
    public static final String XFORMS_SUBMIT_REPLACE_INSTANCE = "instance";
    public static final String XFORMS_SUBMIT_REPLACE_TEXT = "text";
    public static final String XFORMS_SUBMIT_REPLACE_NONE = "none";

    public static final String XXFORMS_STATE_HANDLING_ATTRIBUTE_NAME = "state-handling";
    public static final String XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME = "readonly-appearance";
    public static final QName XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME = new QName(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);

    public static final String XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME = "external-events";

    public static final QName ENCRYPT_ITEM_VALUES = new QName("encrypt-item-values", XXFORMS_NAMESPACE);

    public static final QName XFORMS_FULL_APPEARANCE_QNAME = new QName("full");
    public static final QName XFORMS_COMPACT_APPEARANCE_QNAME = new QName("compact");
    public static final QName XFORMS_MINIMAL_APPEARANCE_QNAME = new QName("minimal");
    public static final QName XXFORMS_MINIMAL_APPEARANCE_QNAME = new QName("minimal", XXFORMS_NAMESPACE);

    public static final QName XFORMS_MODAL_LEVEL_QNAME = new QName("modal");
    public static final QName XFORMS_MODELESS_LEVEL_QNAME = new QName("modeless");
    public static final QName XFORMS_EPHEMERAL_LEVEL_QNAME = new QName("ephemeral");

    public static final QName XXFORMS_LOG_DEBUG_LEVEL_QNAME = new QName("log-debug", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_INFO_DEBUG_LEVEL_QNAME = new QName("log-info", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_WARN_DEBUG_LEVEL_QNAME = new QName("log-warn", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_ERROR_DEBUG_LEVEL_QNAME = new QName("log-error", XXFORMS_NAMESPACE);

    // This appearance is designed to be used internally when a text/html mediatype is encountered on <textarea>
    public static final QName XXFORMS_RICH_TEXT_APPEARANCE_QNAME = new QName("richtext", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_TREE_APPEARANCE_QNAME = new QName("tree", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_MENU_APPEARANCE_QNAME = new QName("menu", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME = new QName("autocomplete", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_AUTOSIZE_APPEARANCE_QNAME = new QName("autosize", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LINK_APPEARANCE_QNAME = new QName("link", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_FIELDSET_APPEARANCE_QNAME = new QName("fieldset", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INTERNAL_APPEARANCE_QNAME = new QName("internal", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TEXT_APPEARANCE_QNAME = new QName("text", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DOWNLOAD_APPEARANCE_QNAME = new QName("download", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SEPARATOR_APPEARANCE_QNAME = new QName("separator", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_TARGET_QNAME = new QName("target", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_UPLOADS_QNAME = new QName("uploads", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_XINCLUDE = new QName("xinclude", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_ORDER_QNAME = new QName("order", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_CALCULATE_QNAME = new QName("calculate", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_USERNAME_QNAME = new QName("username", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PASSWORD_QNAME = new QName("password", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DOMAIN_QNAME = new QName("domain", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SHARED_QNAME = new QName("shared", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_CACHE_QNAME = new QName("cache", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TIME_TO_LIVE_QNAME = new QName("ttl", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VALIDATION_QNAME = new QName("validation", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EXCLUDE_RESULT_PREFIXES = new QName("exclude-result-prefixes", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_INSTANCE_QNAME = new QName("instance", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SHOW_PROGRESS_QNAME = new QName("show-progress", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PROGRESS_MESSAGE_QNAME = new QName("progress-message", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_OPEN_QNAME = new QName("open", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_REFRESH_ITEMS_QNAME = new QName("refresh-items", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_CONTEXT_QNAME = new QName("context", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_REPEAT_INDEXES_QNAME = new QName("repeat-indexes", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DND_QNAME = new QName("dnd", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_UPDATE_QNAME = new QName("update", XXFORMS_NAMESPACE);
    public static final String XFORMS_FULL_UPDATE = "full";
    public static final String XFORMS_INCREMENTAL_UPDATE = "incremental";

    public static final QName XXFORMS_XFORMS11_SWITCH_QNAME = new QName("xforms11-switch", XXFORMS_NAMESPACE);

    public static final QName XFORMS_INTEGER_QNAME = new QName("integer", XFORMS_NAMESPACE);

    public static final String XFORMS_STRING_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XFORMS_NAMESPACE_URI, "string");
    public static final String XFORMS_BOOLEAN_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XFORMS_NAMESPACE_URI, "boolean");
    public static final String XFORMS_INTEGER_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XFORMS_NAMESPACE_URI, "integer");
    public static final String XFORMS_DATE_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XFORMS_NAMESPACE_URI, "date");
    public static final String XFORMS_DATETIME_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XFORMS_NAMESPACE_URI, "dateTime");
    public static final String XFORMS_TIME_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XFORMS_NAMESPACE_URI, "time");
    public static final String XFORMS_ANYURI_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XFORMS_NAMESPACE_URI, "anyURI");
    public static final String XFORMS_BASE64BINARY_EXPLODED_QNAME = XMLUtils.buildExplodedQName(XFORMS_NAMESPACE_URI, "base64Binary");

    public static final QName XXFORMS_EVENT_MODE_QNAME = new QName("events-mode", XXFORMS_NAMESPACE);

    public static final char REPEAT_HIERARCHY_SEPARATOR_1 = 0xB7;
    public static final char REPEAT_HIERARCHY_SEPARATOR_2 = '-';

    public static final char COMPONENT_SEPARATOR = '$';
    // Use "$$" to minimize chances of conflict with user-defined ids
    public static final String LHHAC_SEPARATOR = "$$";

    public static final String DEFAULT_UPLOAD_TYPE_EXPLODED_QNAME = XMLConstants.XS_ANYURI_EXPLODED_QNAME;
    public static final QName DEFAULT_UPLOAD_TYPE_QNAME = XMLConstants.XS_ANYURI_QNAME;

    public static final String DUMMY_IMAGE_URI = "/ops/images/xforms/spacer.gif";
    public static final String HELP_IMAGE_URI = "/ops/images/xforms/help.png";
    public static final String CALENDAR_IMAGE_URI = "/ops/images/xforms/calendar.png";

    public static final String XSD_EXPLODED_TYPE_PREFIX = "{" + XMLConstants.XSD_URI + "}";
    public static final String XFORMS_EXPLODED_TYPE_PREFIX = "{" + XFORMS_NAMESPACE_URI + "}";

    public static final QName STATIC_STATE_SCRIPTS_QNAME = new QName("scripts");
    public static final QName STATIC_STATE_PROPERTIES_QNAME = new QName("properties");

    public static final String NO_INDEX_ADJUSTMENT = XMLUtils.buildExplodedQName(XXFORMS_NAMESPACE_URI, "no-index-adjustment");

    private XFormsConstants() {
        // Disallow construction
    }
}
