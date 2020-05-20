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
package org.orbeon.oxf.xforms

import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.xforms.{EventNames, Namespaces}

// Constants useful for the XForms engine.
//
// 2019-11-14 TODO:
//
// - split into files, for example XForms, XBL
// - some of these must be enumerations (maybe in definitions.scala)
// - see also `Namespaces`
//
object XFormsConstants {

  val InnerScopeJava = XXBLScope.Inner

  val SeparateDeploymentTypeJava = DeploymentType.Separate
  val StandaloneDeploymentTypeJava = DeploymentType.Standalone

  // TODO: Keeping this static list is not ideal
  val AllowedXXFormsElements =
    Set(
      "dialog",
      "var",
      "variable",
      "sequence",
      "value",
      "attribute",
      "text",
      "context",
      "size", //xf:upload/xxf:size
      "dynamic",
      "param",
      "body"
    )

  val AllowedEXFormElements =
    Set(
      "variable"
    )

  val AllowedXBLElements =
    Set(
      "xbl",
      "binding",
      "handlers",
      "handler", // just for the case of top-level `<xbl:handler>`
      "implementation",
      "template"
    )

  val LHHAElements =
    Set(
      "label",
      "hint",
      "help",
      "alert"
    )

  val XFORMS_PREFIX = "xforms" // TODO: remove
  val XFORMS_SHORT_PREFIX = "xf"
  val XFORMS_NAMESPACE_URI = Namespaces.XF
  val XFORMS_NAMESPACE = Namespace(XFORMS_PREFIX, XFORMS_NAMESPACE_URI)
  val XFORMS_NAMESPACE_SHORT = Namespace(XFORMS_SHORT_PREFIX, XFORMS_NAMESPACE_URI)

  val XXFORMS_PREFIX = "xxforms"
  val XXFORMS_SHORT_PREFIX = "xxf"
  val XXFORMS_NAMESPACE_URI = Namespaces.XXF
  val XXFORMS_NAMESPACE = Namespace(XXFORMS_PREFIX, XXFORMS_NAMESPACE_URI)
  val XXFORMS_NAMESPACE_SHORT = Namespace(XXFORMS_SHORT_PREFIX, XXFORMS_NAMESPACE_URI)

  // Common attributes
  val REF_QNAME = QName("ref")
  val NODESET_QNAME = QName("nodeset")
  val CONTEXT_QNAME = QName("context")
  val BIND_QNAME = QName("bind")
  val VALUE_QNAME = QName("value")
  val MODEL_QNAME = QName("model")

  val ID_QNAME = QName("id")
  val NAME_QNAME = QName("name")

  val CLASS_QNAME = QName("class")
  val STYLE_QNAME = QName("style")
  val ROLE_QNAME = QName("role")

  val APPEARANCE_QNAME = QName("appearance")
  val MEDIATYPE_QNAME = QName("mediatype")
  val ACCEPT_QNAME = QName("accept")
  val SRC_QNAME = QName("src")

  val TARGETID_QNAME = QName("targetid")
  val TARGET_QNAME = QName("target")
  val SELECT_QNAME = QName("select")
  val FOR_QNAME = QName("for")

  val SCHEMA_QNAME = QName("schema")
  val RESOURCE_QNAME = QName("resource")
  val SUBMISSION_QNAME = QName("submission")

  // XForms controls
  val XFORMS_GROUP_QNAME = QName("group", XFORMS_NAMESPACE)
  val XFORMS_REPEAT_QNAME = QName("repeat", XFORMS_NAMESPACE)
  val XFORMS_REPEAT_ITERATION_QNAME = QName("repeat-iteration", XFORMS_NAMESPACE) // NOTE: Supposed to be xxf:repeat-iteration
  val REPEAT_NAME = XFORMS_REPEAT_QNAME.localName
  val XFORMS_SWITCH_QNAME = QName("switch", XFORMS_NAMESPACE)
  val XFORMS_CASE_QNAME = QName("case", XFORMS_NAMESPACE)
  val XXFORMS_DIALOG_QNAME = QName("dialog", XXFORMS_NAMESPACE)
  val XXFORMS_DIALOG_NAME = XXFORMS_DIALOG_QNAME.localName
  val XXFORMS_DYNAMIC_QNAME = QName("dynamic", XXFORMS_NAMESPACE)

  val XFORMS_INPUT_QNAME = QName("input", XFORMS_NAMESPACE)
  val XFORMS_SECRET_QNAME = QName("secret", XFORMS_NAMESPACE)
  val XFORMS_TEXTAREA_QNAME = QName("textarea", XFORMS_NAMESPACE)
  val XFORMS_OUTPUT_QNAME = QName("output", XFORMS_NAMESPACE)
  val XFORMS_UPLOAD_QNAME = QName("upload", XFORMS_NAMESPACE)
  val UPLOAD_NAME = XFORMS_UPLOAD_QNAME.localName
  val XFORMS_RANGE_QNAME = QName("range", XFORMS_NAMESPACE)
  val XFORMS_SELECT_QNAME = QName("select", XFORMS_NAMESPACE)
  val XFORMS_SELECT1_QNAME = QName("select1", XFORMS_NAMESPACE)

  val XXFORMS_ATTRIBUTE_QNAME = QName("attribute", XXFORMS_NAMESPACE)
  val XXFORMS_TEXT_QNAME = QName("text", XXFORMS_NAMESPACE)

  val XFORMS_SUBMIT_QNAME = QName("submit", XFORMS_NAMESPACE)
  val XFORMS_TRIGGER_QNAME = QName("trigger", XFORMS_NAMESPACE)
  val XFORMS_BIND_QNAME = QName("bind", XFORMS_NAMESPACE)

  // eXForms at http://www.exforms.org/
  val EXFORMS_NAMESPACE_URI = "http://www.exforms.org/exf/1-0"
  val EXFORMS_PREFIX = "exf"
  val EXFORMS_NAMESPACE = Namespace(EXFORMS_PREFIX, EXFORMS_NAMESPACE_URI)

  // XBL
  val XBL_PREFIX = "xbl"
  val XBL_NAMESPACE_URI = Namespaces.XBL
  val XBL_NAMESPACE = Namespace(XBL_PREFIX, XBL_NAMESPACE_URI)
  val XBL_XBL_QNAME = QName("xbl", XBL_NAMESPACE)
  val XBL_BINDING_QNAME = QName("binding", XBL_NAMESPACE)
  val XBL_SCRIPT_QNAME = QName("script", XBL_NAMESPACE)
  val XBL_RESOURCES_QNAME = QName("resources", XBL_NAMESPACE)
  val XBL_STYLE_QNAME = QName("style", XBL_NAMESPACE)
  val XBL_TEMPLATE_QNAME = QName("template", XBL_NAMESPACE)
  val XBL_HANDLERS_QNAME = QName("handlers", XBL_NAMESPACE)
  val XBL_HANDLER_QNAME = QName("handler", XBL_NAMESPACE)
  val XBL_IMPLEMENTATION_QNAME = QName("implementation", XBL_NAMESPACE)

  val ELEMENT_QNAME = QName("element")
  val INCLUDES_QNAME = QName("includes")

  // XBL extensions
  val XXBL_PREFIX = "xxbl"
  val XXBL_NAMESPACE_URI = Namespaces.XXBL
  val XXBL_NAMESPACE = Namespace(XXBL_PREFIX, XXBL_NAMESPACE_URI)
  val XXBL_TRANSFORM_QNAME = QName("transform", XXBL_NAMESPACE)
  val XXBL_AVT_QNAME = QName("avt", XXBL_NAMESPACE)
  val XXBL_SCOPE_QNAME = QName("scope", XXBL_NAMESPACE)
  val XXBL_CONTAINER_QNAME = QName("container", XXBL_NAMESPACE)
  val XXBL_GLOBAL_QNAME = QName("global", XXBL_NAMESPACE)
  val XXBL_MODE_QNAME = QName("mode", XXBL_NAMESPACE)
  val XXBL_LABEL_FOR_QNAME = QName("label-for", XXBL_NAMESPACE)
  val XXBL_FORMAT_QNAME = QName("format", XXBL_NAMESPACE)
  val XXBL_SERIALIZE_EXTERNAL_VALUE_QNAME = QName("serialize-external-value", XXBL_NAMESPACE)
  val XXBL_DESERIALIZE_EXTERNAL_VALUE_QNAME = QName("deserialize-external-value", XXBL_NAMESPACE)

  // Variables
  val XXFORMS_VAR_QNAME = QName("var", XXFORMS_NAMESPACE)
  val XXFORMS_VARIABLE_QNAME = QName("variable", XXFORMS_NAMESPACE)
  val XFORMS_VAR_QNAME = QName("var", XFORMS_NAMESPACE)
  val XFORMS_VARIABLE_QNAME = QName("variable", XFORMS_NAMESPACE)
  val EXFORMS_VARIABLE_QNAME = QName("variable", EXFORMS_NAMESPACE)
  val XXFORMS_SEQUENCE_QNAME = QName("sequence", XXFORMS_NAMESPACE)
  val XXFORMS_VALUE_QNAME = QName("value", XXFORMS_NAMESPACE)

  val XML_EVENTS_PREFIX = "ev"
  val XML_EVENTS_NAMESPACE_URI = "http://www.w3.org/2001/xml-events"
  val XML_EVENTS_NAMESPACE = Namespace(XML_EVENTS_PREFIX, XML_EVENTS_NAMESPACE_URI)

  val XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME = QName("event", XML_EVENTS_NAMESPACE)
  val XML_EVENTS_EVENT_ATTRIBUTE_QNAME = QName("event")
  val XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME = QName("observer", XML_EVENTS_NAMESPACE)
  val XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME = QName("observer")
  val XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME = QName("target", XML_EVENTS_NAMESPACE)
  val XML_EVENTS_TARGET_ATTRIBUTE_QNAME = QName("target")
  val XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME = QName("phase", XML_EVENTS_NAMESPACE)
  val XML_EVENTS_PHASE_ATTRIBUTE_QNAME = QName("phase")
  val XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME = QName("propagate", XML_EVENTS_NAMESPACE)
  val XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME = QName("propagate")
  val XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME = QName("defaultAction", XML_EVENTS_NAMESPACE)
  val XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME = QName("defaultAction")

  val XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME = QName(EventNames.KeyModifiersPropertyName, XXFORMS_NAMESPACE)
  val XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME = QName(EventNames.KeyTextPropertyName, XXFORMS_NAMESPACE)
  val XXFORMS_EVENTS_PHANTOM_ATTRIBUTE_QNAME = QName("phantom", XXFORMS_NAMESPACE)
  val XXFORMS_EVENTS_IF_NON_RELEVANT_ATTRIBUTE_QNAME = QName("if-non-relevant", XXFORMS_NAMESPACE)

  val XXFORMS_ALL_EVENTS = "#all"

  val XFORMS_FILENAME_QNAME = QName("filename", XFORMS_NAMESPACE)
  val XFORMS_MEDIATYPE_QNAME = QName("mediatype", XFORMS_NAMESPACE)
  val XXFORMS_SIZE_QNAME = QName("size", XXFORMS_NAMESPACE)
  val XXFORMS_TITLE_QNAME = QName("title", XXFORMS_NAMESPACE)
  val XXFORMS_ALT_QNAME = QName("alt", XXFORMS_NAMESPACE)
  val XXFORMS_MAXLENGTH_QNAME = QName("maxlength", XXFORMS_NAMESPACE)
  val XXFORMS_PATTERN_QNAME = QName("pattern", XXFORMS_NAMESPACE)
  val XXFORMS_AUTOCOMPLETE_QNAME = QName("autocomplete", XXFORMS_NAMESPACE)
  val XXFORMS_COLS_QNAME = QName("cols", XXFORMS_NAMESPACE)
  val XXFORMS_ROWS_QNAME = QName("rows", XXFORMS_NAMESPACE)
  val XXFORMS_GROUP_QNAME = QName("group", XXFORMS_NAMESPACE)
  val XXFORMS_ELEMENT_QNAME = QName("element", XXFORMS_NAMESPACE)
  val XXFORMS_EXTERNAL_EVENTS_QNAME = QName(XFormsProperties.EXTERNAL_EVENTS_PROPERTY, XXFORMS_NAMESPACE)

  val CONSTRAINT_LEVEL_ATTRIBUTE_NAME = "level"
  val RELEVANT_ATTRIBUTE_NAME = "relevant"
  val REQUIRED_ATTRIBUTE_NAME = "required"
  val READONLY_ATTRIBUTE_NAME = "readonly"

  // MIPs
  val RELEVANT_QNAME = QName(RELEVANT_ATTRIBUTE_NAME)
  val CALCULATE_QNAME = QName("calculate")
  val READONLY_QNAME = QName(READONLY_ATTRIBUTE_NAME)
  val REQUIRED_QNAME = QName(REQUIRED_ATTRIBUTE_NAME)
  val TYPE_QNAME = QName("type")
  val CONSTRAINT_QNAME = QName("constraint")
  val XXFORMS_DEFAULT_QNAME = QName("default", XXFORMS_NAMESPACE)
  val XXFORMS_DEFAULTS_QNAME = QName("defaults", XXFORMS_NAMESPACE)
  val XXFORMS_DEFERRED_QNAME = QName("deferred", XXFORMS_NAMESPACE)
  val XXFORMS_UPDATE_REPEATS_QNAME = QName("update-repeats", XXFORMS_NAMESPACE)

  val VALIDATION_QNAME = QName("validation")
  val XFORMS_VALIDATION_QNAME = QName("validation", XFORMS_NAMESPACE)

  val XFORMS_TYPE_QNAME = QName("type", XFORMS_NAMESPACE)
  val XFORMS_RELEVANT_QNAME = QName("relevant", XFORMS_NAMESPACE)
  val XFORMS_REQUIRED_QNAME = QName("required", XFORMS_NAMESPACE)

  val XXFORMS_READONLY_ATTRIBUTE_QNAME = QName(READONLY_ATTRIBUTE_NAME, XXFORMS_NAMESPACE)
  val XXFORMS_INDEX_QNAME = QName("index", XXFORMS_NAMESPACE)

  val XXFORMS_UUID_QNAME = QName("uuid", XXFORMS_NAMESPACE)
  val XXFORMS_STATIC_STATE_QNAME = QName("static-state", XXFORMS_NAMESPACE)
  val XXFORMS_DYNAMIC_STATE_QNAME = QName("dynamic-state", XXFORMS_NAMESPACE)
  val XXFORMS_INITIAL_DYNAMIC_STATE_QNAME = QName("initial-dynamic-state", XXFORMS_NAMESPACE)
  val XXFORMS_ACTION_QNAME = QName("action", XXFORMS_NAMESPACE)
  val XXFORMS_SERVER_EVENTS_QNAME = QName("server-events", XXFORMS_NAMESPACE)
  val XXFORMS_FILES_QNAME = QName("files", XXFORMS_NAMESPACE)

  val XFORMS_PARAM_QNAME = QName("param", XFORMS_NAMESPACE)
  val XFORMS_BODY_QNAME = QName("body", XFORMS_NAMESPACE)

  val XFORMS_MODEL_QNAME = QName("model", XFORMS_NAMESPACE)
  val XFORMS_INSTANCE_QNAME = QName("instance", XFORMS_NAMESPACE)
  val XFORMS_SUBMISSION_QNAME = QName("submission", XFORMS_NAMESPACE)
  val XFORMS_HEADER_QNAME = QName("header", XFORMS_NAMESPACE)

  val XXFORMS_EVENT_QNAME = QName("event", XXFORMS_NAMESPACE)
  val XXFORMS_EVENTS_QNAME = QName("events", XXFORMS_NAMESPACE)
  val XXFORMS_PROPERTY_QNAME = QName("property", XXFORMS_NAMESPACE)
  val LABEL_QNAME = QName("label", XFORMS_NAMESPACE)
  val HELP_QNAME = QName("help", XFORMS_NAMESPACE)
  val HINT_QNAME = QName("hint", XFORMS_NAMESPACE)
  val ALERT_QNAME = QName("alert", XFORMS_NAMESPACE)
  val XFORMS_NAME_QNAME = QName("name", XFORMS_NAMESPACE)
  val XFORMS_VALUE_QNAME = QName("value", XFORMS_NAMESPACE)
  val XFORMS_COPY_QNAME = QName("copy", XFORMS_NAMESPACE)
  val XFORMS_ITEMSET_QNAME = QName("itemset", XFORMS_NAMESPACE)
  val XFORMS_ITEM_QNAME = QName("item", XFORMS_NAMESPACE)
  val XFORMS_CHOICES_QNAME = QName("choices", XFORMS_NAMESPACE)

  val XFORMS_SUBMIT_REPLACE_ALL = "all"
  val XFORMS_SUBMIT_REPLACE_INSTANCE = "instance"
  val XFORMS_SUBMIT_REPLACE_TEXT = "text"
  val XFORMS_SUBMIT_REPLACE_NONE = "none"

  val XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME = "readonly-appearance"
  val XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME = QName(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME, XXFORMS_NAMESPACE)

  val XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME = "external-events"

  val ENCRYPT_ITEM_VALUES = QName(XFormsProperties.ENCRYPT_ITEM_VALUES_PROPERTY, XXFORMS_NAMESPACE)
  val XFORMS_FULL_APPEARANCE_QNAME = QName("full")
  val XFORMS_COMPACT_APPEARANCE_QNAME = QName("compact")
  val XFORMS_MINIMAL_APPEARANCE_QNAME = QName("minimal")

  val XFORMS_MODAL_APPEARANCE_QNAME = QName("modal")
  val XXFORMS_MODAL_QNAME = QName("modal", XXFORMS_NAMESPACE)

  val LEVEL_QNAME = QName("level")
  val XFORMS_MODAL_LEVEL_QNAME = QName("modal")
  val XFORMS_MODELESS_LEVEL_QNAME = QName("modeless")
  val XFORMS_EPHEMERAL_LEVEL_QNAME = QName("ephemeral")

  val XXFORMS_LOG_DEBUG_LEVEL_QNAME = QName("log-debug", XXFORMS_NAMESPACE)
  val XXFORMS_LOG_INFO_DEBUG_LEVEL_QNAME = QName("log-info", XXFORMS_NAMESPACE)
  val XXFORMS_LOG_WARN_DEBUG_LEVEL_QNAME = QName("log-warn", XXFORMS_NAMESPACE)
  val XXFORMS_LOG_ERROR_DEBUG_LEVEL_QNAME = QName("log-error", XXFORMS_NAMESPACE)

  // This appearance is designed to be used internally when a text/html mediatype is encountered on <textarea>
  val XXFORMS_RICH_TEXT_APPEARANCE_QNAME = QName("richtext", XXFORMS_NAMESPACE)
  val XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME = QName("autocomplete", XXFORMS_NAMESPACE)

  val XXFORMS_FIELDSET_APPEARANCE_QNAME = QName("fieldset", XXFORMS_NAMESPACE)
  val XXFORMS_INTERNAL_APPEARANCE_QNAME = QName("internal", XXFORMS_NAMESPACE)
  val XXFORMS_TEXT_APPEARANCE_QNAME = QName("text", XXFORMS_NAMESPACE)
  val XXFORMS_DOWNLOAD_APPEARANCE_QNAME = QName("download", XXFORMS_NAMESPACE)
  val XXFORMS_PLACEHOLDER_APPEARANCE_QNAME = QName("placeholder", XXFORMS_NAMESPACE)

  // See: https://github.com/orbeon/orbeon-forms/issues/418
  val XXFORMS_SEPARATOR_APPEARANCE_QNAME = QName("xxforms-separator")

  val XXFORMS_LEFT_APPEARANCE_QNAME = QName("left", XXFORMS_NAMESPACE)

  val XXFORMS_TARGET_QNAME = QName("target", XXFORMS_NAMESPACE)
  val XXFORMS_UPLOADS_QNAME = QName("uploads", XXFORMS_NAMESPACE)
  val XXFORMS_XINCLUDE = QName("xinclude", XXFORMS_NAMESPACE)
  val XXFORMS_RESPONSE_URL_TYPE = QName("response-url-type", XXFORMS_NAMESPACE)

  val XXFORMS_ORDER_QNAME = QName("order", XXFORMS_NAMESPACE)

  val XXFORMS_CALCULATE_QNAME = QName("calculate", XXFORMS_NAMESPACE)
  val XXFORMS_USERNAME_QNAME = QName("username", XXFORMS_NAMESPACE)
  val XXFORMS_PASSWORD_QNAME = QName("password", XXFORMS_NAMESPACE)
  val XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME = QName("preemptive-authentication", XXFORMS_NAMESPACE)
  val XXFORMS_DOMAIN_QNAME = QName("domain", XXFORMS_NAMESPACE)
  val XXFORMS_SHARED_QNAME = QName("shared", XXFORMS_NAMESPACE)
  val XXFORMS_CACHE_QNAME = QName("cache", XXFORMS_NAMESPACE)
  val XXFORMS_TIME_TO_LIVE_QNAME = QName("ttl", XXFORMS_NAMESPACE)
  val XXFORMS_VALIDATION_QNAME = QName("validation", XXFORMS_NAMESPACE)
  val XXFORMS_EXPOSE_XPATH_TYPES_QNAME = QName(XFormsProperties.EXPOSE_XPATH_TYPES_PROPERTY, XXFORMS_NAMESPACE)
  val XXFORMS_EXCLUDE_RESULT_PREFIXES = QName("exclude-result-prefixes", XXFORMS_NAMESPACE)
  val XXFORMS_CUSTOM_MIPS_QNAME = QName("custom-mips", XXFORMS_NAMESPACE)
  val XXFORMS_RELEVANT_ATTRIBUTE_QNAME = QName("relevant-attribute", XXFORMS_NAMESPACE_SHORT)
  val XXFORMS_ANNOTATE_QNAME = QName("annotate", XXFORMS_NAMESPACE_SHORT)

  val XXFORMS_INSTANCE_QNAME = QName("instance", XXFORMS_NAMESPACE)
  val XXFORMS_SHOW_PROGRESS_QNAME = QName("show-progress", XXFORMS_NAMESPACE)
  val XXFORMS_ALLOW_DUPLICATES_QNAME = QName("allow-duplicates", XXFORMS_NAMESPACE)
  val XXFORMS_OPEN_QNAME = QName("open", XXFORMS_NAMESPACE)

  // XForms 2.0 standardizes the xf:property element
  val XFORMS_PROPERTY_QNAME = QName("property", XFORMS_NAMESPACE)
  val XXFORMS_CONTEXT_QNAME = QName("context", XXFORMS_NAMESPACE)

  val XXFORMS_REFRESH_ITEMS_QNAME = QName("refresh-items", XXFORMS_NAMESPACE)
  val EXCLUDE_WHITESPACE_TEXT_NODES_QNAME = QName("exclude-whitespace-text-nodes", XXFORMS_NAMESPACE)
  val XXFORMS_REPEAT_INDEXES_QNAME = QName("repeat-indexes", XXFORMS_NAMESPACE)
  val XXFORMS_REPEAT_STARTINDEX_QNAME = QName("startindex")
  val XXFORMS_DND_QNAME = QName("dnd", XXFORMS_NAMESPACE)
  val XXFORMS_DEFERRED_UPDATES_QNAME = QName("deferred-updates", XXFORMS_NAMESPACE)
  val XXFORMS_WHITESPACE_QNAME = QName("whitespace", XXFORMS_NAMESPACE)

  val XXFORMS_FORMAT_QNAME = QName("format", XXFORMS_NAMESPACE)
  val XXFORMS_UNFORMAT_QNAME = QName("unformat", XXFORMS_NAMESPACE)

  val XXFORMS_UPDATE_QNAME = QName("update", XXFORMS_NAMESPACE)
  val XFORMS_FULL_UPDATE = "full"

  val XXFORMS_XFORMS11_SWITCH_QNAME = QName("xforms11-switch", XXFORMS_NAMESPACE)

  val XFORMS_INTEGER_QNAME = QName("integer", XFORMS_NAMESPACE)

  val XFORMS_STRING_QNAME = QName("string", XFORMS_NAMESPACE)
  val XFORMS_BASE64BINARY_QNAME = QName("base64Binary", XFORMS_NAMESPACE)

  val XS_STRING_EXPLODED_QNAME = XMLConstants.XS_STRING_QNAME.clarkName
  val XFORMS_STRING_EXPLODED_QNAME = XFORMS_STRING_QNAME.clarkName

  val XXFORMS_EVENT_MODE_QNAME = QName("events-mode", XXFORMS_NAMESPACE)
  val XXFORMS_VALIDATION_MODE_QNAME = QName("validation-mode", XXFORMS_NAMESPACE)

  // TODO: Remove once callers use shared `xforms.Constants`
  val COMPONENT_SEPARATOR = '\u2261' // ≡ IDENTICAL TO
  val REPEAT_SEPARATOR = '\u2299' // ⊙ CIRCLED DOT OPERATOR
  val REPEAT_INDEX_SEPARATOR = '-' // - (just has to not be a digit)

  val REPEAT_INDEX_SEPARATOR_STRING = "" + REPEAT_INDEX_SEPARATOR

  val XF_COMPONENT_SEPARATOR_STRING = "" + COMPONENT_SEPARATOR
  // Use "$$" to minimize chances of conflict with user-defined ids
  val LHHAC_SEPARATOR = "" + COMPONENT_SEPARATOR + COMPONENT_SEPARATOR

  val XFORMS_SERVER_SUBMIT = "/xforms-server-submit"

  val DUMMY_IMAGE_URI = "/ops/images/xforms/spacer.gif"
  val DUMMY_SCRIPT_URI = "data:text/javascript;base64,KGZ1bmN0aW9uKCl7fSgpKTsK" // empty self-calling function
  val CALENDAR_IMAGE_URI = "/ops/images/xforms/calendar.png"

  val STATIC_STATE_PROPERTIES_QNAME = QName("properties")

  val SELECTED_QNAME = QName("selected")
  val CASEREF_QNAME = QName("caseref")

  val XXFORMS_MULTIPLE_QNAME = QName("multiple", XXFORMS_NAMESPACE)
}