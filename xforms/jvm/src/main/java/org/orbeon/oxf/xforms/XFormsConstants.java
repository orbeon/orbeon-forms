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
    public static final QName REF_QNAME = QName.apply("ref");
    public static final QName NODESET_QNAME = QName.apply("nodeset");
    public static final QName CONTEXT_QNAME = QName.apply("context");
    public static final QName BIND_QNAME = QName.apply("bind");
    public static final QName VALUE_QNAME = QName.apply("value");
    public static final QName MODEL_QNAME = QName.apply("model");

    public static final QName ID_QNAME = QName.apply("id");
    public static final QName NAME_QNAME = QName.apply("name");

    public static final QName CLASS_QNAME = QName.apply("class");
    public static final QName STYLE_QNAME = QName.apply("style");
    public static final QName ROLE_QNAME  = QName.apply("role");

    public static final QName APPEARANCE_QNAME = QName.apply("appearance");
    public static final QName MEDIATYPE_QNAME = QName.apply("mediatype");
    public static final QName ACCEPT_QNAME = QName.apply("accept");
    public static final QName SRC_QNAME = QName.apply("src");

    public static final QName TARGETID_QNAME = QName.apply("targetid");
    public static final QName TARGET_QNAME = QName.apply("target");
    public static final QName SELECT_QNAME = QName.apply("select");
    public static final QName FOR_QNAME = QName.apply("for");

    public static final QName SCHEMA_QNAME = QName.apply("schema");
    public static final QName RESOURCE_QNAME = QName.apply("resource");
    public static final QName SUBMISSION_QNAME = QName.apply("submission");

    // XForms controls
    public static final QName XFORMS_GROUP_QNAME = QName.apply("group", XFORMS_NAMESPACE);
    public static final QName XFORMS_REPEAT_QNAME = QName.apply("repeat", XFORMS_NAMESPACE);
    public static final QName XFORMS_REPEAT_ITERATION_QNAME = QName.apply("repeat-iteration", XFORMS_NAMESPACE); // NOTE: Supposed to be xxf:repeat-iteration
    public static final String REPEAT_NAME = XFORMS_REPEAT_QNAME.localName();
    public static final QName XFORMS_SWITCH_QNAME = QName.apply("switch", XFORMS_NAMESPACE);
    public static final QName XFORMS_CASE_QNAME = QName.apply("case", XFORMS_NAMESPACE);
    public static final QName XXFORMS_DIALOG_QNAME = QName.apply("dialog", XXFORMS_NAMESPACE);
    public static final String XXFORMS_DIALOG_NAME = XXFORMS_DIALOG_QNAME.localName();
    public static final QName XXFORMS_DYNAMIC_QNAME = QName.apply("dynamic", XXFORMS_NAMESPACE);

    public static final QName XFORMS_INPUT_QNAME = QName.apply("input", XFORMS_NAMESPACE);
    public static final QName XFORMS_SECRET_QNAME = QName.apply("secret", XFORMS_NAMESPACE);
    public static final QName XFORMS_TEXTAREA_QNAME = QName.apply("textarea", XFORMS_NAMESPACE);
    public static final QName XFORMS_OUTPUT_QNAME = QName.apply("output", XFORMS_NAMESPACE);
    public static final QName XFORMS_UPLOAD_QNAME = QName.apply("upload", XFORMS_NAMESPACE);
    public static final String UPLOAD_NAME = XFORMS_UPLOAD_QNAME.localName();
    public static final QName XFORMS_RANGE_QNAME = QName.apply("range", XFORMS_NAMESPACE);
    public static final QName XFORMS_SELECT_QNAME = QName.apply("select", XFORMS_NAMESPACE);
    public static final QName XFORMS_SELECT1_QNAME = QName.apply("select1", XFORMS_NAMESPACE);

    public static final QName XXFORMS_ATTRIBUTE_QNAME = QName.apply("attribute", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TEXT_QNAME = QName.apply("text", XXFORMS_NAMESPACE);

    public static final QName XFORMS_SUBMIT_QNAME = QName.apply("submit", XFORMS_NAMESPACE);
    public static final QName XFORMS_TRIGGER_QNAME = QName.apply("trigger", XFORMS_NAMESPACE);
    public static final QName XFORMS_BIND_QNAME = QName.apply("bind", XFORMS_NAMESPACE);

    // eXForms at http://www.exforms.org/
    public static final String EXFORMS_NAMESPACE_URI = "http://www.exforms.org/exf/1-0";
    public static final String EXFORMS_PREFIX = "exf";
    public static final Namespace EXFORMS_NAMESPACE = Namespace$.MODULE$.apply(EXFORMS_PREFIX, EXFORMS_NAMESPACE_URI);


    // XBL
    public static final String XBL_PREFIX = "xbl";
    public static final String XBL_NAMESPACE_URI = "http://www.w3.org/ns/xbl";
    public static final Namespace XBL_NAMESPACE = Namespace$.MODULE$.apply(XBL_PREFIX, XBL_NAMESPACE_URI);
    public static final QName XBL_XBL_QNAME = QName.apply("xbl", XBL_NAMESPACE);
    public static final QName XBL_BINDING_QNAME = QName.apply("binding", XBL_NAMESPACE);
    public static final QName XBL_SCRIPT_QNAME = QName.apply("script", XBL_NAMESPACE);
    public static final QName XBL_RESOURCES_QNAME = QName.apply("resources", XBL_NAMESPACE);
    public static final QName XBL_STYLE_QNAME = QName.apply("style", XBL_NAMESPACE);
    public static final QName XBL_TEMPLATE_QNAME = QName.apply("template", XBL_NAMESPACE);
    public static final QName XBL_HANDLERS_QNAME = QName.apply("handlers", XBL_NAMESPACE);
    public static final QName XBL_HANDLER_QNAME = QName.apply("handler", XBL_NAMESPACE);
    public static final QName XBL_IMPLEMENTATION_QNAME = QName.apply("implementation", XBL_NAMESPACE);

    public static final QName ELEMENT_QNAME = QName.apply("element");
    public static final QName INCLUDES_QNAME = QName.apply("includes");

    // XBL extensions
    public static final String XXBL_PREFIX = "xxbl";
    public static final String XXBL_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xbl";
    public static final Namespace XXBL_NAMESPACE = Namespace$.MODULE$.apply(XXBL_PREFIX, XXBL_NAMESPACE_URI);
    public static final QName XXBL_TRANSFORM_QNAME = QName.apply("transform", XXBL_NAMESPACE);
    public static final QName XXBL_AVT_QNAME = QName.apply("avt", XXBL_NAMESPACE);
    public static final QName XXBL_SCOPE_QNAME = QName.apply("scope", XXBL_NAMESPACE);
    public static final QName XXBL_CONTAINER_QNAME = QName.apply("container", XXBL_NAMESPACE);
    public static final QName XXBL_GLOBAL_QNAME = QName.apply("global", XXBL_NAMESPACE);
    public static final QName XXBL_MODE_QNAME = QName.apply("mode", XXBL_NAMESPACE);
    public static final QName XXBL_LABEL_FOR_QNAME = QName.apply("label-for", XXBL_NAMESPACE);


    public enum XXBLScope { inner, outer }
    public enum DeploymentType { separate, integrated, standalone}

    // Variables
    public static final QName XXFORMS_VAR_QNAME = QName.apply("var", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VARIABLE_QNAME = QName.apply("variable", XXFORMS_NAMESPACE);
    public static final QName XFORMS_VAR_QNAME = QName.apply("var", XFORMS_NAMESPACE);
    public static final QName XFORMS_VARIABLE_QNAME = QName.apply("variable", XFORMS_NAMESPACE);
    public static final QName EXFORMS_VARIABLE_QNAME = QName.apply("variable", EXFORMS_NAMESPACE);
    public static final QName XXFORMS_SEQUENCE_QNAME = QName.apply("sequence", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VALUE_QNAME = QName.apply("value", XXFORMS_NAMESPACE);

    public static final String XML_EVENTS_PREFIX = "ev";
    public static final String XML_EVENTS_NAMESPACE_URI = "http://www.w3.org/2001/xml-events";
    public static final Namespace XML_EVENTS_NAMESPACE = Namespace$.MODULE$.apply(XML_EVENTS_PREFIX, XML_EVENTS_NAMESPACE_URI);

    public static final QName XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME = QName.apply("event", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_EVENT_ATTRIBUTE_QNAME = QName.apply("event");
    public static final QName XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME = QName.apply("observer", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME = QName.apply("observer");
    public static final QName XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME = QName.apply("target", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_TARGET_ATTRIBUTE_QNAME = QName.apply("target");
    public static final QName XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME = QName.apply("phase", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_PHASE_ATTRIBUTE_QNAME = QName.apply("phase");
    public static final QName XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME = QName.apply("propagate", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME = QName.apply("propagate");
    public static final QName XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME = QName.apply("defaultAction", XML_EVENTS_NAMESPACE);
    public static final QName XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME = QName.apply("defaultAction");

    public static final QName XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME = QName.apply("modifiers", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME = QName.apply("text", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_PHANTOM_ATTRIBUTE_QNAME = QName.apply("phantom", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_IF_NON_RELEVANT_ATTRIBUTE_QNAME = QName.apply("if-non-relevant", XXFORMS_NAMESPACE);

    public static final String XXFORMS_ALL_EVENTS = "#all";

    public static final QName XFORMS_FILENAME_QNAME = QName.apply("filename", XFORMS_NAMESPACE);
    public static final QName XFORMS_MEDIATYPE_QNAME = QName.apply("mediatype", XFORMS_NAMESPACE);
    public static final QName XXFORMS_SIZE_QNAME = QName.apply("size", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TITLE_QNAME = QName.apply("title", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ALT_QNAME = QName.apply("alt", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_MAXLENGTH_QNAME = QName.apply("maxlength", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PATTERN_QNAME = QName.apply("pattern", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_AUTOCOMPLETE_QNAME = QName.apply("autocomplete", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_COLS_QNAME = QName.apply("cols", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ROWS_QNAME = QName.apply("rows", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_GROUP_QNAME = QName.apply("group", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ELEMENT_QNAME = QName.apply("element", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EXTERNAL_EVENTS_QNAME = QName.apply(XFormsProperties.EXTERNAL_EVENTS_PROPERTY, XXFORMS_NAMESPACE);

    public static final String CONSTRAINT_LEVEL_ATTRIBUTE_NAME = "level";
    public static final String RELEVANT_ATTRIBUTE_NAME = "relevant";
    public static final String REQUIRED_ATTRIBUTE_NAME = "required";
    public static final String READONLY_ATTRIBUTE_NAME = "readonly";

    // MIPs
    public static final QName RELEVANT_QNAME = QName.apply(RELEVANT_ATTRIBUTE_NAME);
    public static final QName CALCULATE_QNAME = QName.apply("calculate");
    public static final QName READONLY_QNAME = QName.apply(READONLY_ATTRIBUTE_NAME);
    public static final QName REQUIRED_QNAME = QName.apply(REQUIRED_ATTRIBUTE_NAME);
    public static final QName TYPE_QNAME = QName.apply("type");
    public static final QName CONSTRAINT_QNAME = QName.apply("constraint");
    public static final QName XXFORMS_DEFAULT_QNAME = QName.apply("default", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DEFAULTS_QNAME = QName.apply("defaults", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DEFERRED_QNAME = QName.apply("deferred", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_UPDATE_REPEATS_QNAME = QName.apply("update-repeats", XXFORMS_NAMESPACE);

    public static final QName VALIDATION_QNAME = QName.apply("validation");
    public static final QName XFORMS_VALIDATION_QNAME = QName.apply("validation", XFORMS_NAMESPACE);

    public static final QName XFORMS_TYPE_QNAME = QName.apply("type", XFORMS_NAMESPACE);
    public static final QName XFORMS_RELEVANT_QNAME = QName.apply("relevant", XFORMS_NAMESPACE);
    public static final QName XFORMS_REQUIRED_QNAME = QName.apply("required", XFORMS_NAMESPACE);

    public static final QName XXFORMS_READONLY_ATTRIBUTE_QNAME = QName.apply(READONLY_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INDEX_QNAME = QName.apply("index", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_UUID_QNAME = QName.apply("uuid", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_STATIC_STATE_QNAME = QName.apply("static-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DYNAMIC_STATE_QNAME = QName.apply("dynamic-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INITIAL_DYNAMIC_STATE_QNAME = QName.apply("initial-dynamic-state", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ACTION_QNAME = QName.apply("action", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SERVER_EVENTS_QNAME = QName.apply("server-events", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_FILES_QNAME = QName.apply("files", XXFORMS_NAMESPACE);

    public static final QName XFORMS_PARAM_QNAME = QName.apply("param", XFORMS_NAMESPACE);
    public static final QName XFORMS_BODY_QNAME = QName.apply("body", XFORMS_NAMESPACE);

    public static final QName XFORMS_MODEL_QNAME = QName.apply("model", XFORMS_NAMESPACE);
    public static final QName XFORMS_INSTANCE_QNAME = QName.apply("instance", XFORMS_NAMESPACE);
    public static final QName XFORMS_SUBMISSION_QNAME = QName.apply("submission", XFORMS_NAMESPACE);
    public static final QName XFORMS_HEADER_QNAME = QName.apply("header", XFORMS_NAMESPACE);

    public static final QName XXFORMS_EVENT_QNAME = QName.apply("event", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EVENTS_QNAME = QName.apply("events", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PROPERTY_QNAME = QName.apply("property", XXFORMS_NAMESPACE);

    public static final QName LABEL_QNAME = QName.apply("label", XFORMS_NAMESPACE);
    public static final QName HELP_QNAME = QName.apply("help", XFORMS_NAMESPACE);
    public static final QName HINT_QNAME = QName.apply("hint", XFORMS_NAMESPACE);
    public static final QName ALERT_QNAME = QName.apply("alert", XFORMS_NAMESPACE);
    public static final QName XFORMS_NAME_QNAME = QName.apply("name", XFORMS_NAMESPACE);
    public static final QName XFORMS_VALUE_QNAME = QName.apply("value", XFORMS_NAMESPACE);
    public static final QName XFORMS_COPY_QNAME = QName.apply("copy", XFORMS_NAMESPACE);
    public static final QName XFORMS_ITEMSET_QNAME = QName.apply("itemset", XFORMS_NAMESPACE);
    public static final QName XFORMS_ITEM_QNAME = QName.apply("item", XFORMS_NAMESPACE);
    public static final QName XFORMS_CHOICES_QNAME = QName.apply("choices", XFORMS_NAMESPACE);

    public static final String XFORMS_SUBMIT_REPLACE_ALL = "all";
    public static final String XFORMS_SUBMIT_REPLACE_INSTANCE = "instance";
    public static final String XFORMS_SUBMIT_REPLACE_TEXT = "text";
    public static final String XFORMS_SUBMIT_REPLACE_NONE = "none";

    public static final String XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME = "readonly-appearance";
    public static final QName XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME = QName.apply(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME, XXFORMS_NAMESPACE);

    public static final String XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME = "external-events";

    public static final QName ENCRYPT_ITEM_VALUES = QName.apply(XFormsProperties.ENCRYPT_ITEM_VALUES_PROPERTY, XXFORMS_NAMESPACE);

    public static final QName XFORMS_FULL_APPEARANCE_QNAME = QName.apply("full");
    public static final QName XFORMS_COMPACT_APPEARANCE_QNAME = QName.apply("compact");
    public static final QName XFORMS_MINIMAL_APPEARANCE_QNAME = QName.apply("minimal");

    public static final QName XFORMS_MODAL_APPEARANCE_QNAME = QName.apply("modal");
    public static final QName XXFORMS_MODAL_QNAME = QName.apply("modal", XXFORMS_NAMESPACE);

    public static final QName LEVEL_QNAME = QName.apply("level");
    public static final QName XFORMS_MODAL_LEVEL_QNAME = QName.apply("modal");
    public static final QName XFORMS_MODELESS_LEVEL_QNAME = QName.apply("modeless");
    public static final QName XFORMS_EPHEMERAL_LEVEL_QNAME = QName.apply("ephemeral");

    public static final QName XXFORMS_LOG_DEBUG_LEVEL_QNAME = QName.apply("log-debug", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_INFO_DEBUG_LEVEL_QNAME = QName.apply("log-info", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_WARN_DEBUG_LEVEL_QNAME = QName.apply("log-warn", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_LOG_ERROR_DEBUG_LEVEL_QNAME = QName.apply("log-error", XXFORMS_NAMESPACE);

    // This appearance is designed to be used internally when a text/html mediatype is encountered on <textarea>
    public static final QName XXFORMS_RICH_TEXT_APPEARANCE_QNAME = QName.apply("richtext", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME = QName.apply("autocomplete", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_FIELDSET_APPEARANCE_QNAME = QName.apply("fieldset", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_INTERNAL_APPEARANCE_QNAME = QName.apply("internal", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TEXT_APPEARANCE_QNAME = QName.apply("text", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DOWNLOAD_APPEARANCE_QNAME = QName.apply("download", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PLACEHOLDER_APPEARANCE_QNAME = QName.apply("placeholder", XXFORMS_NAMESPACE);

    // See: https://github.com/orbeon/orbeon-forms/issues/418
    public static final QName XXFORMS_SEPARATOR_APPEARANCE_QNAME = QName.apply("xxforms-separator");

    public static final QName XXFORMS_TARGET_QNAME = QName.apply("target", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_UPLOADS_QNAME = QName.apply("uploads", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_XINCLUDE = QName.apply("xinclude", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_RESPONSE_URL_TYPE = QName.apply("response-url-type", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_ORDER_QNAME = QName.apply("order", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_CALCULATE_QNAME = QName.apply("calculate", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_USERNAME_QNAME = QName.apply("username", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PASSWORD_QNAME = QName.apply("password", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME = QName.apply("preemptive-authentication", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DOMAIN_QNAME = QName.apply("domain", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SHARED_QNAME = QName.apply("shared", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_CACHE_QNAME = QName.apply("cache", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_TIME_TO_LIVE_QNAME = QName.apply("ttl", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VALIDATION_QNAME = QName.apply("validation", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EXPOSE_XPATH_TYPES_QNAME = QName.apply(XFormsProperties.EXPOSE_XPATH_TYPES_PROPERTY, XXFORMS_NAMESPACE);
    public static final QName XXFORMS_EXCLUDE_RESULT_PREFIXES = QName.apply("exclude-result-prefixes", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_CUSTOM_MIPS_QNAME = QName.apply("custom-mips", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_RELEVANT_ATTRIBUTE_QNAME = QName.apply("relevant-attribute", XXFORMS_NAMESPACE_SHORT);
    public static final QName XXFORMS_ANNOTATE_QNAME = QName.apply("annotate", XXFORMS_NAMESPACE_SHORT);

    public static final QName XXFORMS_INSTANCE_QNAME = QName.apply("instance", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_SHOW_PROGRESS_QNAME = QName.apply("show-progress", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_ALLOW_DUPLICATES_QNAME = QName.apply("allow-duplicates", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_OPEN_QNAME = QName.apply("open", XXFORMS_NAMESPACE);

    // XForms 2.0 standardizes the xf:property element
    public static final QName XFORMS_PROPERTY_QNAME = QName.apply("property", XFORMS_NAMESPACE);
    public static final QName XXFORMS_CONTEXT_QNAME = QName.apply("context", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_REFRESH_ITEMS_QNAME = QName.apply("refresh-items", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_REPEAT_INDEXES_QNAME = QName.apply("repeat-indexes", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DND_QNAME = QName.apply("dnd", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_DEFERRED_UPDATES_QNAME = QName.apply("deferred-updates", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_WHITESPACE_QNAME = QName.apply("whitespace", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_FORMAT_QNAME = QName.apply("format", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_UNFORMAT_QNAME = QName.apply("unformat", XXFORMS_NAMESPACE);

    public static final QName XXFORMS_UPDATE_QNAME = QName.apply("update", XXFORMS_NAMESPACE);
    public static final String XFORMS_FULL_UPDATE = "full";

    public static final QName XXFORMS_XFORMS11_SWITCH_QNAME = QName.apply("xforms11-switch", XXFORMS_NAMESPACE);

    public static final QName XFORMS_INTEGER_QNAME = QName.apply("integer", XFORMS_NAMESPACE);

    public static final QName XFORMS_STRING_QNAME = QName.apply("string", XFORMS_NAMESPACE);
    public static final QName XFORMS_BASE64BINARY_QNAME = QName.apply("base64Binary", XFORMS_NAMESPACE);

    public static final String XS_STRING_EXPLODED_QNAME = Dom4jUtils.qNameToExplodedQName(XMLConstants.XS_STRING_QNAME);
    public static final String XFORMS_STRING_EXPLODED_QNAME = Dom4jUtils.qNameToExplodedQName(XFORMS_STRING_QNAME);

    public static final QName XXFORMS_EVENT_MODE_QNAME = QName.apply("events-mode", XXFORMS_NAMESPACE);
    public static final QName XXFORMS_VALIDATION_MODE_QNAME = QName.apply("validation-mode", XXFORMS_NAMESPACE);

    // TODO: Remove once callers use shared `xforms.Constants`
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

    public static final QName STATIC_STATE_PROPERTIES_QNAME = QName.apply("properties");

    public static final QName SELECTED_QNAME = QName.apply("selected");
    public static final QName CASEREF_QNAME  = QName.apply("caseref");

    private XFormsConstants() {
        // Disallow construction
    }
}
