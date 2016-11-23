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

import org.orbeon.dom.Namespace;
import org.orbeon.dom.Namespace$;
import org.orbeon.dom.QName;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
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
        label, help, hint, alert
    }

    public static final int LHHACount = LHHA.values().length;

    static {
        // TODO: Keeping this static list is not ideal
        ALLOWED_XXFORMS_ELEMENTS.add("dialog");
        ALLOWED_XXFORMS_ELEMENTS.add("var");
        ALLOWED_XXFORMS_ELEMENTS.add("variable");
        ALLOWED_XXFORMS_ELEMENTS.add("sequence");
        ALLOWED_XXFORMS_ELEMENTS.add("value");
        ALLOWED_XXFORMS_ELEMENTS.add("attribute");
        ALLOWED_XXFORMS_ELEMENTS.add("text");
        ALLOWED_XXFORMS_ELEMENTS.add("context");
        ALLOWED_XXFORMS_ELEMENTS.add("size");//xf:upload/xxf:size
        ALLOWED_XXFORMS_ELEMENTS.add("dynamic");
        ALLOWED_XXFORMS_ELEMENTS.add("param");
        ALLOWED_XXFORMS_ELEMENTS.add("body");

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

    public static final String XFORMS_PREFIX = "xforms"; // TODO: remove
    public static final String XFORMS_SHORT_PREFIX = "xf";
    public static final String XFORMS_NAMESPACE_URI = "http://www.w3.org/2002/xforms";
    public static final Namespace XFORMS_NAMESPACE       = Namespace$.MODULE$.apply(XFORMS_PREFIX, XFORMS_NAMESPACE_URI);
    public static final Namespace XFORMS_NAMESPACE_SHORT = Namespace$.MODULE$.apply(XFORMS_SHORT_PREFIX, XFORMS_NAMESPACE_URI);

    public static final String XXFORMS_PREFIX = "xxforms"; // TODO: remove
    public static final String XXFORMS_SHORT_PREFIX = "xxf";
    public static final String XXFORMS_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xforms";
    public static final Namespace XXFORMS_NAMESPACE = Namespace$.MODULE$.apply(XXFORMS_PREFIX, XXFORMS_NAMESPACE_URI);
    public static final Namespace XXFORMS_NAMESPACE_SHORT = Namespace$.MODULE$.apply(XXFORMS_SHORT_PREFIX, XXFORMS_NAMESPACE_URI); // TODO: remove

    // Common attributes
    public static final QName REF_QNAME = QName.get("ref");
    public static final QName NODESET_QNAME = QName.get("nodeset");
    public static final QName CONTEXT_QNAME = QName.get("context");
    public static final QName BIND_QNAME = QName.get("bind");
    public static final QName VALUE_QNAME = QName.get("value");
    public static final QName MODEL_QNAME = QName.get("model");

    public static final QName ID_QNAME = QName.get("id");
    public static final QName NAME_QNAME = QName.get("name");

    public static final QName CLASS_QNAME = QName.get("class");
    public static final QName STYLE_QNAME = QName.get("style");

    public static final QName APPEARANCE_QNAME = QName.get("appearance");
    public static final QName MEDIATYPE_QNAME = QName.get("mediatype");
    public static final QName ACCEPT_QNAME = QName.get("accept");
    public static final QName SRC_QNAME = QName.get("src");

    public static final QName TARGETID_QNAME = QName.get("targetid");
    public static final QName TARGET_QNAME = QName.get("target");
    public static final QName SELECT_QNAME = QName.get("select");
    public static final QName FOR_QNAME = QName.get("for");

    public static final QName SCHEMA_QNAME = QName.get("schema");
    public static final QName RESOURCE_QNAME = QName.get("resource");
    public static final QName SUBMISSION_QNAME = QName.get("submission");

    // XForms controls
    public static final QName XFORMS_GROUP_QNAME = QName.get("group", XFORMS_NAMESPACE);
    public static final QName XFORMS_REPEAT_QNAME = QName.get("repeat", XFORMS_NAMESPACE);
    public static final QName XFORMS_REPEAT_ITERATION_QNAME = QName.get("repeat-iteration", XFORMS_NAMESPACE); // NOTE: Supposed to be xxf:repeat-iteration
    public static final String REPEAT_NAME = XFORMS_REPEAT_QNAME.getName();
    public static final QName XFORMS_SWITCH_QNAME = QName.get("switch", XFORMS_NAMESPACE);
    public static final QName XFORMS_CASE_QNAME = QName.get("case", XFORMS_NAMESPACE);
    public static final QName XXFORMS_DIALOG_QNAME = QName.get("dialog", XXFORMS_NAMESPACE);
    public static final String XXFORMS_DIALOG_NAME = XXFORMS_DIALOG_QNAME.getName();
    public static final QName XXFORMS_DYNAMIC_QNAME = QName.get("dynamic", XXFORMS_NAMESPACE);

    public static final QName XFORMS_INPUT_QNAME = QName.get("input", XFORMS_NAMESPACE);
    public static final QName XFORMS_SECRET_QNAME = QName.get("secret", XFORMS_NAMESPACE);
    public static final QName XFORMS_TEXTAREA_QNAME = QName.get("textarea", XFORMS_NAMESPACE);
    public static final QName XFORMS_OUTPUT_QNAME = QName.get("output", XFORMS_NAMESPACE);
    public static final QName XFORMS_UPLOAD_QNAME = QName.get("upload", XFORMS_NAMESPACE);
    public static final String UPLOAD_NAME = XFORMS_UPLOAD_QNAME.getName();
    public static final QName XFORMS_RANGE_QNAME = QName.get("range", XFORMS_NAMESPACE);
    public static final QName XFORMS_SELECT_QNAME = QName.get("select", XFORMS_NAMESPACE);
    public static final QName XFORMS_SELECT1_QNAME = QName.get("select1", XFORMS_NAMESPACE);

    public static final QName XXFORMS_ATTRIBUTE_QNAME = QName.get("attribute", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TEXT_QNAME = QName.get("text", XXFORMS_NAMESPACE);

    public static final QName XFORMS_SUBMIT_QNAME = QName.get("submit", XFORMS_NAMESPACE);
    public static final QName XFORMS_TRIGGER_QNAME = QName.get("trigger", XFORMS_NAMESPACE);
    public static final QName XFORMS_BIND_QNAME = QName.get("bind", XFORMS_NAMESPACE);

    // eXForms at http://www.exforms.org/
    public static final String EXFORMS_NAMESPACE_URI = "http://www.exforms.org/exf/1-0";
    public static final String EXFORMS_PREFIX = "exf";
    public static final Namespace EXFORMS_NAMESPACE = Namespace$.MODULE$.apply(EXFORMS_PREFIX, EXFORMS_NAMESPACE_URI);


    // XBL
    public static final String XBL_PREFIX = "xbl";
    public static final String XBL_NAMESPACE_URI = "http://www.w3.org/ns/xbl";
    public static final Namespace XBL_NAMESPACE = Namespace$.MODULE$.apply(XBL_PREFIX, XBL_NAMESPACE_URI);
    public static final QName XBL_XBL_QNAME = QName.get("xbl", XBL_NAMESPACE);
    public static final QName XBL_BINDING_QNAME = QName.get("binding", XBL_NAMESPACE);
    public static final QName XBL_SCRIPT_QNAME = QName.get("script", XBL_NAMESPACE);
    public static final QName XBL_RESOURCES_QNAME = QName.get("resources", XBL_NAMESPACE);
    public static final QName XBL_STYLE_QNAME = QName.get("style", XBL_NAMESPACE);
    public static final QName XBL_TEMPLATE_QNAME = QName.get("template", XBL_NAMESPACE);
    public static final QName XBL_HANDLERS_QNAME = QName.get("handlers", XBL_NAMESPACE);
    public static final QName XBL_HANDLER_QNAME = QName.get("handler", XBL_NAMESPACE);
    public static final QName XBL_IMPLEMENTATION_QNAME = QName.get("implementation", XBL_NAMESPACE);

    public static final QName ELEMENT_QNAME = QName.get("element");
    public static final QName INCLUDES_QNAME = QName.get("includes");

    // XBL extensions
    public static final String XXBL_PREFIX = "xxbl";
    public static final String XXBL_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xbl";
    public static final Namespace XXBL_NAMESPACE = Namespace$.MODULE$.apply(XXBL_PREFIX, XXBL_NAMESPACE_URI);
    public static final QName XXBL_TRANSFORM_QNAME = QName.get("transform", XXBL_NAMESPACE);
    public static final QName XXBL_AVT_QNAME = QName.get("avt", XXBL_NAMESPACE);
    public static final QName XXBL_SCOPE_QNAME = QName.get("scope", XXBL_NAMESPACE);
    public static final QName XXBL_CONTAINER_QNAME = QName.get("container", XXBL_NAMESPACE);
    public static final QName XXBL_GLOBAL_QNAME = QName.get("global", XXBL_NAMESPACE);
    public static final QName XXBL_MODE_QNAME = QName.get("mode", XXBL_NAMESPACE);
    public static final QName XXBL_LABEL_FOR_QNAME = QName.get("label-for", XXBL_NAMESPACE);


    public enum XXBLScope { inner, outer }
    public enum DeploymentType { separate, integrated, standalone}

    // Variables
    public static final QName XXFORMS_VAR_QNAME = QName.get("var", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VARIABLE_QNAME = QName.get("variable", XXFORMS_NAMESPACE);
    public static final QName XFORMS_VAR_QNAME = QName.get("var", XFORMS_NAMESPACE);
    public static final QName XFORMS_VARIABLE_QNAME = QName.get("variable", XFORMS_NAMESPACE);
    public static final QName EXFORMS_VARIABLE_QNAME = QName.get("variable", EXFORMS_NAMESPACE);
    public static final QName XXFORMS_SEQUENCE_QNAME = QName.get("sequence", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VALUE_QNAME = QName.get("value", XXFORMS_NAMESPACE);

    public static final String XML_EVENTS_PREFIX = "ev";
    public static final String XML_EVENTS_NAMESPACE_URI = "http://www.w3.org/2001/xml-events";
    public static final Namespace XML_EVENTS_NAMESPACE = Namespace$.MODULE$.apply(XML_EVENTS_PREFIX, XML_EVENTS_NAMESPACE_URI);

    public static final QName XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME = QName.get("event", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_EVENT_ATTRIBUTE_QNAME = QName.get("event");
    public static final QName XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME = QName.get("observer", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME = QName.get("observer");
    public static final QName XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME = QName.get("target", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_TARGET_ATTRIBUTE_QNAME = QName.get("target");
    public static final QName XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME = QName.get("phase", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_PHASE_ATTRIBUTE_QNAME = QName.get("phase");
    public static final QName XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME = QName.get("propagate", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME = QName.get("propagate");
    public static final QName XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME = QName.get("defaultAction", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME = QName.get("defaultAction");

    public static final QName XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME = QName.get("modifiers", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME = QName.get("text", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_PHANTOM_ATTRIBUTE_QNAME = QName.get("phantom", XXFORMS_NAMESPACE);

    public static final String XXFORMS_ALL_EVENTS = "#all";

    public static final QName XFORMS_FILENAME_QNAME = QName.get("filename", XFORMS_NAMESPACE);
    public static final QName XFORMS_MEDIATYPE_QNAME = QName.get("mediatype", XFORMS_NAMESPACE);
    public static final QName XXFORMS_SIZE_QNAME = QName.get("size", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TITLE_QNAME = QName.get("title", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ALT_QNAME = QName.get("alt", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_MAXLENGTH_QNAME = QName.get("maxlength", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PATTERN_QNAME = QName.get("pattern", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_AUTOCOMPLETE_QNAME = QName.get("autocomplete", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_COLS_QNAME = QName.get("cols", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ROWS_QNAME = QName.get("rows", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_GROUP_QNAME = QName.get("group", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ELEMENT_QNAME = QName.get("element", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EXTERNAL_EVENTS_QNAME = QName.get(XFormsProperties.EXTERNAL_EVENTS_PROPERTY, XXFORMS_NAMESPACE);

    public static final String CONSTRAINT_LEVEL_ATTRIBUTE_NAME = "level";
    public static final String RELEVANT_ATTRIBUTE_NAME = "relevant";
    public static final String REQUIRED_ATTRIBUTE_NAME = "required";
    public static final String READONLY_ATTRIBUTE_NAME = "readonly";

    // MIPs
    public static final QName RELEVANT_QNAME = QName.get(RELEVANT_ATTRIBUTE_NAME);
    public static final QName CALCULATE_QNAME = QName.get("calculate");
    public static final QName READONLY_QNAME = QName.get(READONLY_ATTRIBUTE_NAME);
    public static final QName REQUIRED_QNAME = QName.get(REQUIRED_ATTRIBUTE_NAME);
    public static final QName TYPE_QNAME = QName.get("type");
    public static final QName CONSTRAINT_QNAME = QName.get("constraint");
    public static final QName XXFORMS_DEFAULT_QNAME = QName.get("default", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DEFAULTS_QNAME = QName.get("defaults", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DEFERRED_QNAME = QName.get("deferred", XXFORMS_NAMESPACE);

    public static final QName VALIDATION_QNAME = QName.get("validation");
    public static final QName XFORMS_VALIDATION_QNAME = QName.get("validation", XFORMS_NAMESPACE);

    public static final QName XFORMS_TYPE_QNAME = QName.get("type", XFORMS_NAMESPACE);
    public static final QName XFORMS_RELEVANT_QNAME = QName.get("relevant", XFORMS_NAMESPACE);
    public static final QName XFORMS_REQUIRED_QNAME = QName.get("required", XFORMS_NAMESPACE);

    public static final QName XXFORMS_READONLY_ATTRIBUTE_QNAME = QName.get(READONLY_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INDEX_QNAME = QName.get("index", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_UUID_QNAME = QName.get("uuid", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_STATIC_STATE_QNAME = QName.get("static-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DYNAMIC_STATE_QNAME = QName.get("dynamic-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INITIAL_DYNAMIC_STATE_QNAME = QName.get("initial-dynamic-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ACTION_QNAME = QName.get("action", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SERVER_EVENTS_QNAME = QName.get("server-events", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_FILES_QNAME = QName.get("files", XXFORMS_NAMESPACE);

    public static final QName XFORMS_PARAM_QNAME = QName.get("param", XFORMS_NAMESPACE);
    public static final QName XFORMS_BODY_QNAME = QName.get("body", XFORMS_NAMESPACE);

    public static final QName XFORMS_MODEL_QNAME = QName.get("model", XFORMS_NAMESPACE);
    public static final QName XFORMS_INSTANCE_QNAME = QName.get("instance", XFORMS_NAMESPACE);
    public static final QName XFORMS_SUBMISSION_QNAME = QName.get("submission", XFORMS_NAMESPACE);
    public static final QName XFORMS_HEADER_QNAME = QName.get("header", XFORMS_NAMESPACE);

    public static final QName XXFORMS_EVENT_QNAME = QName.get("event", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_QNAME = QName.get("events", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PROPERTY_QNAME = QName.get("property", XXFORMS_NAMESPACE);

    public static final QName LABEL_QNAME = QName.get("label", XFORMS_NAMESPACE);
    public static final QName HELP_QNAME = QName.get("help", XFORMS_NAMESPACE);
    public static final QName HINT_QNAME = QName.get("hint", XFORMS_NAMESPACE);
    public static final QName ALERT_QNAME = QName.get("alert", XFORMS_NAMESPACE);
    public static final QName XFORMS_NAME_QNAME = QName.get("name", XFORMS_NAMESPACE);
    public static final QName XFORMS_VALUE_QNAME = QName.get("value", XFORMS_NAMESPACE);
    public static final QName XFORMS_COPY_QNAME = QName.get("copy", XFORMS_NAMESPACE);
    public static final QName XFORMS_ITEMSET_QNAME = QName.get("itemset", XFORMS_NAMESPACE);
    public static final QName XFORMS_ITEM_QNAME = QName.get("item", XFORMS_NAMESPACE);
    public static final QName XFORMS_CHOICES_QNAME = QName.get("choices", XFORMS_NAMESPACE);

    public static final String XFORMS_SUBMIT_REPLACE_ALL = "all";
    public static final String XFORMS_SUBMIT_REPLACE_INSTANCE = "instance";
    public static final String XFORMS_SUBMIT_REPLACE_TEXT = "text";
    public static final String XFORMS_SUBMIT_REPLACE_NONE = "none";

    public static final String XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME = "readonly-appearance";
    public static final QName XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME = QName.get(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);

    public static final String XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME = "external-events";

    public static final QName ENCRYPT_ITEM_VALUES = QName.get(XFormsProperties.ENCRYPT_ITEM_VALUES_PROPERTY, XXFORMS_NAMESPACE);

    public static final QName XFORMS_FULL_APPEARANCE_QNAME = QName.get("full");
    public static final QName XFORMS_COMPACT_APPEARANCE_QNAME = QName.get("compact");
    public static final QName XFORMS_MINIMAL_APPEARANCE_QNAME = QName.get("minimal");

    public static final QName XFORMS_MODAL_APPEARANCE_QNAME = QName.get("modal");
    public static final QName XXFORMS_MODAL_QNAME = QName.get("modal", XXFORMS_NAMESPACE);

    public static final QName LEVEL_QNAME = QName.get("level");
    public static final QName XFORMS_MODAL_LEVEL_QNAME = QName.get("modal");
    public static final QName XFORMS_MODELESS_LEVEL_QNAME = QName.get("modeless");
    public static final QName XFORMS_EPHEMERAL_LEVEL_QNAME = QName.get("ephemeral");

    public static final QName XXFORMS_LOG_DEBUG_LEVEL_QNAME = QName.get("log-debug", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_INFO_DEBUG_LEVEL_QNAME = QName.get("log-info", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_WARN_DEBUG_LEVEL_QNAME = QName.get("log-warn", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_ERROR_DEBUG_LEVEL_QNAME = QName.get("log-error", XXFORMS_NAMESPACE);

    // This appearance is designed to be used internally when a text/html mediatype is encountered on <textarea>
    public static final QName XXFORMS_RICH_TEXT_APPEARANCE_QNAME = QName.get("richtext", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME = QName.get("autocomplete", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_FIELDSET_APPEARANCE_QNAME = QName.get("fieldset", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INTERNAL_APPEARANCE_QNAME = QName.get("internal", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TEXT_APPEARANCE_QNAME = QName.get("text", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DOWNLOAD_APPEARANCE_QNAME = QName.get("download", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PLACEHOLDER_APPEARANCE_QNAME = QName.get("placeholder", XXFORMS_NAMESPACE);

    // See: https://github.com/orbeon/orbeon-forms/issues/418
    public static final QName XXFORMS_SEPARATOR_APPEARANCE_QNAME = QName.get("xxforms-separator");

    public static final QName XXFORMS_TARGET_QNAME = QName.get("target", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_UPLOADS_QNAME = QName.get("uploads", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_XINCLUDE = QName.get("xinclude", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_ORDER_QNAME = QName.get("order", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_CALCULATE_QNAME = QName.get("calculate", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_USERNAME_QNAME = QName.get("username", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PASSWORD_QNAME = QName.get("password", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME = QName.get("preemptive-authentication", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DOMAIN_QNAME = QName.get("domain", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SHARED_QNAME = QName.get("shared", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_CACHE_QNAME = QName.get("cache", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TIME_TO_LIVE_QNAME = QName.get("ttl", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VALIDATION_QNAME = QName.get("validation", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EXPOSE_XPATH_TYPES_QNAME = QName.get(XFormsProperties.EXPOSE_XPATH_TYPES_PROPERTY, XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EXCLUDE_RESULT_PREFIXES = QName.get("exclude-result-prefixes", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_CUSTOM_MIPS_QNAME = QName.get("custom-mips", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ANNOTATE_QNAME = QName.get("annotate", XXFORMS_NAMESPACE_SHORT);

    public static final QName XXFORMS_INSTANCE_QNAME = QName.get("instance", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SHOW_PROGRESS_QNAME = QName.get("show-progress", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_OPEN_QNAME = QName.get("open", XXFORMS_NAMESPACE);

    // XForms 2.0 standardizes the xf:property element
    public static final QName XFORMS_PROPERTY_QNAME = QName.get("property", XFORMS_NAMESPACE);
    public static final QName XXFORMS_CONTEXT_QNAME = QName.get("context", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_REFRESH_ITEMS_QNAME = QName.get("refresh-items", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_REPEAT_INDEXES_QNAME = QName.get("repeat-indexes", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DND_QNAME = QName.get("dnd", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DEFERRED_UPDATES_QNAME = QName.get("deferred-updates", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_WHITESPACE_QNAME = QName.get("whitespace", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_FORMAT_QNAME = QName.get("format", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_UNFORMAT_QNAME = QName.get("unformat", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_UPDATE_QNAME = QName.get("update", XXFORMS_NAMESPACE);
    public static final String XFORMS_FULL_UPDATE = "full";

    public static final QName XXFORMS_XFORMS11_SWITCH_QNAME = QName.get("xforms11-switch", XXFORMS_NAMESPACE);

    public static final QName XFORMS_INTEGER_QNAME = QName.get("integer", XFORMS_NAMESPACE);

    public static final QName XFORMS_STRING_QNAME = QName.get("string", XFORMS_NAMESPACE);
    public static final QName XFORMS_BASE64BINARY_QNAME = QName.get("base64Binary", XFORMS_NAMESPACE);

    public static final String XS_STRING_EXPLODED_QNAME = Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_STRING_QNAME);
    public static final String XFORMS_STRING_EXPLODED_QNAME = Dom4jUtils.qNameToExplodedQName(XFORMS_STRING_QNAME);

    public static final QName XXFORMS_EVENT_MODE_QNAME = QName.get("events-mode", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VALIDATION_MODE_QNAME = QName.get("validation-mode", XXFORMS_NAMESPACE);

    public static final char COMPONENT_SEPARATOR    = '\u2261'; // ≡ IDENTICAL TO
    public static final char REPEAT_SEPARATOR       = '\u2299'; // ⊙ CIRCLED DOT OPERATOR
    public static final char REPEAT_INDEX_SEPARATOR = '-';      // - (just has to not be a digit)
    public static final char ABSOLUTE_ID_SEPARATOR  = '|';      // | see https://github.com/orbeon/orbeon-forms/issues/551

    public static final String REPEAT_INDEX_SEPARATOR_STRING = "" + REPEAT_INDEX_SEPARATOR;

    public static final String XF_COMPONENT_SEPARATOR_STRING = "" + COMPONENT_SEPARATOR;
    // Use "$$" to minimize chances of conflict with user-defined ids
    public static final String LHHAC_SEPARATOR = "" + COMPONENT_SEPARATOR + COMPONENT_SEPARATOR;

    public static final String DUMMY_IMAGE_URI   = "/ops/images/xforms/spacer.gif";
    public static final String DUMMY_SCRIPT_URI  = "data:text/javascript;base64,KGZ1bmN0aW9uKCl7fSgpKTsK"; // empty self-calling function
    public static final String CALENDAR_IMAGE_URI = "/ops/images/xforms/calendar.png";

    public static final QName STATIC_STATE_PROPERTIES_QNAME = QName.get("properties");

    public static final QName SELECTED_QNAME = QName.get("selected");
    public static final QName CASEREF_QNAME  = QName.get("caseref");

    private XFormsConstants() {
        // Disallow construction
    }
}
