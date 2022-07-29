/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.dom.{Namespace, QName}


// Names useful for the XForms engine.
//
// 2019-11-14 TODO:
//
// - split into files, for example XForms, XBL
// - some of these must be enumerations (maybe in definitions.scala)
// - see also `Namespaces`
//
object XFormsNames {

  def xformsQName   (name: String): QName = QName(name, XFORMS_NAMESPACE)
  def xxformsQName  (name: String): QName = QName(name, XXFORMS_NAMESPACE)
  def xmlEventsQName(name: String): QName = QName(name, XML_EVENTS_NAMESPACE)
  def xblQName      (name: String): QName = QName(name, XBL_NAMESPACE)
  def xxblQName     (name: String): QName = QName(name, XXBL_NAMESPACE)

  val XFORMS_PREFIX           = "xforms" // TODO: remove
  val XFORMS_SHORT_PREFIX     = "xf"
  val XFORMS_NAMESPACE_URI    = Namespaces.XF
  val XFORMS_NAMESPACE        = Namespace(XFORMS_PREFIX, XFORMS_NAMESPACE_URI)
  val XFORMS_NAMESPACE_SHORT  = Namespace(XFORMS_SHORT_PREFIX, XFORMS_NAMESPACE_URI)

  val XXFORMS_PREFIX          = "xxforms" // TODO: remove
  val XXFORMS_SHORT_PREFIX    = "xxf"
  val XXFORMS_NAMESPACE_URI   = Namespaces.XXF
  val XXFORMS_NAMESPACE       = Namespace(XXFORMS_PREFIX, XXFORMS_NAMESPACE_URI)
  val XXFORMS_NAMESPACE_SHORT = Namespace(XXFORMS_SHORT_PREFIX, XXFORMS_NAMESPACE_URI)

  // Common attributes
  val REF_QNAME        = QName("ref")
  val NODESET_QNAME    = QName("nodeset")
  val CONTEXT_QNAME    = QName("context")
  val BIND_QNAME       = QName("bind")
  val VALUE_QNAME      = QName("value")
  val MODEL_QNAME      = QName("model")

  val ID_QNAME         = QName("id")
  val NAME_QNAME       = QName("name")

  val CLASS_QNAME      = QName("class")
  val STYLE_QNAME      = QName("style")
  val ROLE_QNAME       = QName("role")

  val APPEARANCE_QNAME = QName("appearance")
  val MEDIATYPE_QNAME  = QName("mediatype")
  val ACCEPT_QNAME     = QName("accept")
  val SRC_QNAME        = QName("src")

  val TARGETID_QNAME   = QName("targetid")
  val TARGET_QNAME     = QName("target")
  val SELECT_QNAME     = QName("select")
  val FOR_QNAME        = QName("for")

  val SCHEMA_QNAME     = QName("schema")
  val RESOURCE_QNAME   = QName("resource")
  val SUBMISSION_QNAME = QName("submission")

  // XForms controls
  val XFORMS_GROUP_QNAME            = xformsQName("group")
  val XFORMS_REPEAT_QNAME           = xformsQName("repeat")
  val XFORMS_REPEAT_ITERATION_QNAME = xformsQName("repeat-iteration") // NOTE: Supposed to be xxf:repeat-iteration
  val REPEAT_NAME                   = XFORMS_REPEAT_QNAME.localName
  val XFORMS_SWITCH_QNAME           = xformsQName("switch")
  val XFORMS_CASE_QNAME             = xformsQName("case")
  val XXFORMS_DIALOG_QNAME          = xxformsQName("dialog")
  val XXFORMS_DIALOG_NAME           = XXFORMS_DIALOG_QNAME.localName
  val XXFORMS_DYNAMIC_QNAME         = xxformsQName("dynamic")

  val XFORMS_INPUT_QNAME            = xformsQName("input")
  val XFORMS_SECRET_QNAME           = xformsQName("secret")
  val XFORMS_TEXTAREA_QNAME         = xformsQName("textarea")
  val XFORMS_OUTPUT_QNAME           = xformsQName("output")
  val XFORMS_UPLOAD_QNAME           = xformsQName("upload")
  val UPLOAD_NAME                   = XFORMS_UPLOAD_QNAME.localName
  val XFORMS_RANGE_QNAME            = xformsQName("range")
  val XFORMS_SELECT_QNAME           = xformsQName("select")
  val XFORMS_SELECT1_QNAME          = xformsQName("select1")

  val XXFORMS_ATTRIBUTE_QNAME       = xxformsQName("attribute")
  val XXFORMS_TEXT_QNAME            = xxformsQName("text")

  val XFORMS_SUBMIT_QNAME           = xformsQName("submit")
  val XFORMS_TRIGGER_QNAME          = xformsQName("trigger")
  val XFORMS_BIND_QNAME             = xformsQName("bind")

  val ROOT_QNAME                    = QName("root")

  // eXForms at http://www.exforms.org/
  val EXFORMS_NAMESPACE_URI = "http://www.exforms.org/exf/1-0"
  val EXFORMS_PREFIX = "exf"
  val EXFORMS_NAMESPACE = Namespace(EXFORMS_PREFIX, EXFORMS_NAMESPACE_URI)

  // XBL
  val XBL_PREFIX = "xbl"
  val XBL_NAMESPACE            = Namespace(XBL_PREFIX, Namespaces.XBL)
  val XBL_XBL_QNAME            = xblQName("xbl")
  val XBL_BINDING_QNAME        = xblQName("binding")
  val XBL_SCRIPT_QNAME         = xblQName("script")
  val XBL_RESOURCES_QNAME      = xblQName("resources")
  val XBL_STYLE_QNAME          = xblQName("style")
  val XBL_TEMPLATE_QNAME       = xblQName("template")
  val XBL_HANDLERS_QNAME       = xblQName("handlers")
  val XBL_HANDLER_QNAME        = xblQName("handler")
  val XBL_IMPLEMENTATION_QNAME = xblQName("implementation")

  val ELEMENT_QNAME  = QName("element")
  val INCLUDES_QNAME = QName("includes")

  // XBL extensions
  val XXBL_PREFIX = "xxbl"
  val XXBL_NAMESPACE_URI = Namespaces.XXBL
  val XXBL_NAMESPACE     = Namespace(XXBL_PREFIX, XXBL_NAMESPACE_URI)
  val XXBL_TRANSFORM_QNAME                  = xxblQName("transform")
  val XXBL_AVT_QNAME                        = xxblQName("avt")
  val XXBL_SCOPE_QNAME                      = xxblQName("scope")
  val XXBL_CONTAINER_QNAME                  = xxblQName("container")
  val XXBL_GLOBAL_QNAME                     = xxblQName("global")
  val XXBL_MODE_QNAME                       = xxblQName("mode")
  val XXBL_LABEL_FOR_QNAME                  = xxblQName("label-for")
  val XXBL_FORMAT_QNAME                     = xxblQName("format")
  val XXBL_SERIALIZE_EXTERNAL_VALUE_QNAME   = xxblQName("serialize-external-value")
  val XXBL_DESERIALIZE_EXTERNAL_VALUE_QNAME = xxblQName("deserialize-external-value")

  // Variables
  val XXFORMS_VAR_QNAME      = xxformsQName("var")
  val XXFORMS_VARIABLE_QNAME = xxformsQName("variable")
  val XFORMS_VAR_QNAME       = xformsQName("var")
  val XFORMS_VARIABLE_QNAME  = xformsQName("variable")
  val EXFORMS_VARIABLE_QNAME = QName("variable", EXFORMS_NAMESPACE)
  val XXFORMS_SEQUENCE_QNAME = xxformsQName("sequence")
  val XXFORMS_VALUE_QNAME    = xxformsQName("value")

  val XML_EVENTS_PREFIX = "ev"
  val XML_EVENTS_NAMESPACE_URI = "http://www.w3.org/2001/xml-events"
  val XML_EVENTS_NAMESPACE = Namespace(XML_EVENTS_PREFIX, XML_EVENTS_NAMESPACE_URI)

  val XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME            = xmlEventsQName("event")
  val XML_EVENTS_EVENT_ATTRIBUTE_QNAME               = QName("event")
  val XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME         = xmlEventsQName("observer")
  val XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME            = QName("observer")
  val XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME           = xmlEventsQName("target")
  val XML_EVENTS_TARGET_ATTRIBUTE_QNAME              = QName("target")
  val XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME            = xmlEventsQName("phase")
  val XML_EVENTS_PHASE_ATTRIBUTE_QNAME               = QName("phase")
  val XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME        = xmlEventsQName("propagate")
  val XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME           = QName("propagate")
  val XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME   = xmlEventsQName("defaultAction")
  val XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME      = QName("defaultAction")

  val XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME       = xxformsQName(EventNames.KeyModifiersPropertyName)
  val XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME            = xxformsQName(EventNames.KeyTextPropertyName)
  val XXFORMS_EVENTS_PHANTOM_ATTRIBUTE_QNAME         = xxformsQName("phantom")
  val XXFORMS_EVENTS_IF_NON_RELEVANT_ATTRIBUTE_QNAME = xxformsQName("if-non-relevant")

  val XXFORMS_ALL_EVENTS = "#all"

  val XFORMS_FILENAME_QNAME         = xformsQName("filename")
  val XFORMS_MEDIATYPE_QNAME        = xformsQName("mediatype")
  val XXFORMS_SIZE_QNAME            = xxformsQName("size")
  val XXFORMS_TITLE_QNAME           = xxformsQName("title")
  val XXFORMS_ALT_QNAME             = xxformsQName("alt")
  val XXFORMS_MAXLENGTH_QNAME       = xxformsQName("maxlength")
  val XXFORMS_PATTERN_QNAME         = xxformsQName("pattern")
  val XXFORMS_AUTOCOMPLETE_QNAME    = xxformsQName("autocomplete")
  val XXFORMS_COLS_QNAME            = xxformsQName("cols")
  val XXFORMS_ROWS_QNAME            = xxformsQName("rows")
  val XXFORMS_GROUP_QNAME           = xxformsQName("group")
  val XXFORMS_ELEMENT_QNAME         = xxformsQName("element")
  val XXFORMS_EXTERNAL_EVENTS_QNAME = xxformsQName("external-events")

  val CONSTRAINT_LEVEL_ATTRIBUTE_NAME = "level"
  val RELEVANT_ATTRIBUTE_NAME = "relevant"
  val REQUIRED_ATTRIBUTE_NAME = "required"
  val READONLY_ATTRIBUTE_NAME = "readonly"

  // MIPs
  val RELEVANT_QNAME                      = QName(RELEVANT_ATTRIBUTE_NAME)
  val CALCULATE_QNAME                     = QName("calculate")
  val READONLY_QNAME                      = QName(READONLY_ATTRIBUTE_NAME)
  val REQUIRED_QNAME                      = QName(REQUIRED_ATTRIBUTE_NAME)
  val TYPE_QNAME                          = QName("type")
  val CONSTRAINT_QNAME                    = QName("constraint")
  val XXFORMS_DEFAULT_QNAME               = xxformsQName("default")
  val XXFORMS_DEFAULTS_QNAME              = xxformsQName("defaults")
  val XXFORMS_DEFERRED_QNAME              = xxformsQName("deferred")
  val XXFORMS_UPDATE_REPEATS_QNAME        = xxformsQName("update-repeats")

  val VALIDATION_QNAME                    = QName("validation")
  val XFORMS_VALIDATION_QNAME             = xformsQName("validation")

  val XFORMS_TYPE_QNAME = xformsQName("type")
  val XFORMS_RELEVANT_QNAME               = xformsQName("relevant")
  val XFORMS_REQUIRED_QNAME               = xformsQName("required")

  val XXFORMS_READONLY_ATTRIBUTE_QNAME    = xxformsQName(READONLY_ATTRIBUTE_NAME)
  val XXFORMS_INDEX_QNAME                 = xxformsQName("index")

  val XXFORMS_UUID_QNAME                  = xxformsQName("uuid")
  val XXFORMS_STATIC_STATE_QNAME          = xxformsQName("static-state")
  val XXFORMS_DYNAMIC_STATE_QNAME         = xxformsQName("dynamic-state")
  val XXFORMS_INITIAL_DYNAMIC_STATE_QNAME = xxformsQName("initial-dynamic-state")
  val XXFORMS_ACTION_QNAME                = xxformsQName("action")
  val XXFORMS_SERVER_EVENTS_QNAME         = xxformsQName("server-events")

  val XFORMS_PARAM_QNAME                  = xformsQName("param")
  val XFORMS_BODY_QNAME                   = xformsQName("body")

  val XFORMS_MODEL_QNAME                  = xformsQName("model")
  val XFORMS_INSTANCE_QNAME               = xformsQName("instance")
  val XFORMS_SUBMISSION_QNAME             = xformsQName("submission")
  val XFORMS_HEADER_QNAME                 = xformsQName("header")

  val XXFORMS_EVENT_QNAME                 = xxformsQName("event")
  val XXFORMS_EVENTS_QNAME                = xxformsQName("events")
  val XXFORMS_PROPERTY_QNAME              = xxformsQName("property")
  val LABEL_QNAME                         = xformsQName("label")
  val HELP_QNAME                          = xformsQName("help")
  val HINT_QNAME                          = xformsQName("hint")
  val ALERT_QNAME                         = xformsQName("alert")
  val XFORMS_NAME_QNAME                   = xformsQName("name")
  val XFORMS_VALUE_QNAME                  = xformsQName("value")
  val XFORMS_COPY_QNAME                   = xformsQName("copy")
  val XFORMS_ITEMSET_QNAME                = xformsQName("itemset")
  val XFORMS_ITEM_QNAME                   = xformsQName("item")
  val XFORMS_CHOICES_QNAME                = xformsQName("choices")

  val XFORMS_MESSAGE_QNAME                = xformsQName("message")

  val XFORMS_SUBMIT_REPLACE_ALL      = "all"
  val XFORMS_SUBMIT_REPLACE_INSTANCE = "instance"
  val XFORMS_SUBMIT_REPLACE_TEXT     = "text"
  val XFORMS_SUBMIT_REPLACE_NONE     = "none"

  val XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME  = "readonly-appearance"
  val XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME = xxformsQName(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_NAME)

  val XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME = "external-events"

  val ENCRYPT_ITEM_VALUES             = xxformsQName("encrypt-item-values")
  val XFORMS_FULL_APPEARANCE_QNAME    = QName("full")
  val XFORMS_COMPACT_APPEARANCE_QNAME = QName("compact")
  val XFORMS_MINIMAL_APPEARANCE_QNAME = QName("minimal")

  val XFORMS_MODAL_APPEARANCE_QNAME   = QName("modal")
  val XXFORMS_MODAL_QNAME             = xxformsQName("modal")

  val LEVEL_QNAME                     = QName("level")

  // This appearance is designed to be used internally when a text/html mediatype is encountered on <textarea>
  val XXFORMS_RICH_TEXT_APPEARANCE_QNAME    = xxformsQName("richtext")
  val XXFORMS_AUTOCOMPLETE_APPEARANCE_QNAME = xxformsQName("autocomplete")

  val XXFORMS_FIELDSET_APPEARANCE_QNAME     = xxformsQName("fieldset")
  val XXFORMS_INTERNAL_APPEARANCE_QNAME     = xxformsQName("internal")
  val XXFORMS_TEXT_APPEARANCE_QNAME         = xxformsQName("text")
  val XXFORMS_DOWNLOAD_APPEARANCE_QNAME     = xxformsQName("download")
  val XXFORMS_PLACEHOLDER_APPEARANCE_QNAME  = xxformsQName("placeholder")

  // See: https://github.com/orbeon/orbeon-forms/issues/418
  val XXFORMS_SEPARATOR_APPEARANCE_QNAME = QName("xxforms-separator")

  val XXFORMS_LEFT_APPEARANCE_QNAME = xxformsQName("left")

  val XXFORMS_TARGET_QNAME                    = xxformsQName("target")
  val XXFORMS_UPLOADS_QNAME                   = xxformsQName("uploads")
  val XXFORMS_XINCLUDE                        = xxformsQName("xinclude")
  val XXFORMS_RESPONSE_URL_TYPE               = xxformsQName("response-url-type")

  val XXFORMS_ORDER_QNAME                     = xxformsQName("order")

  val XXFORMS_CALCULATE_QNAME                 = xxformsQName("calculate")
  val XXFORMS_USERNAME_QNAME                  = xxformsQName("username")
  val XXFORMS_PASSWORD_QNAME                  = xxformsQName("password")
  val XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME = xxformsQName("preemptive-authentication")
  val XXFORMS_DOMAIN_QNAME                    = xxformsQName("domain")
  val XXFORMS_SHARED_QNAME                    = xxformsQName("shared")
  val XXFORMS_CACHE_QNAME                     = xxformsQName("cache")
  val XXFORMS_TIME_TO_LIVE_QNAME              = xxformsQName("ttl")
  val XXFORMS_VALIDATION_QNAME                = xxformsQName("validation")
  val XXFORMS_EXPOSE_XPATH_TYPES_QNAME        = xxformsQName("expose-xpath-types")
  val XXFORMS_EXCLUDE_RESULT_PREFIXES         = xxformsQName("exclude-result-prefixes")
  val XXFORMS_CUSTOM_MIPS_QNAME               = xxformsQName("custom-mips")
  val XXFORMS_RELEVANT_ATTRIBUTE_QNAME        = xxformsQName("relevant-attribute")
  val XXFORMS_ANNOTATE_QNAME                  = QName("annotate", XXFORMS_NAMESPACE_SHORT)

  val XXFORMS_INSTANCE_QNAME                  = xxformsQName("instance")
  val XXFORMS_SHOW_PROGRESS_QNAME             = xxformsQName("show-progress")
  val XXFORMS_ALLOW_DUPLICATES_QNAME          = xxformsQName("allow-duplicates")
  val XXFORMS_OPEN_QNAME                      = xxformsQName("open")

  // XForms 2.0 standardizes the xf:property element
  val XFORMS_PROPERTY_QNAME = xformsQName("property")
  val XXFORMS_CONTEXT_QNAME = xxformsQName("context")

  val XXFORMS_REFRESH_ITEMS_QNAME         = xxformsQName("refresh-items")
  val EXCLUDE_WHITESPACE_TEXT_NODES_QNAME = xxformsQName("exclude-whitespace-text-nodes")
  val XXFORMS_REPEAT_INDEXES_QNAME        = xxformsQName("repeat-indexes")
  val XXFORMS_REPEAT_STARTINDEX_QNAME     = QName("startindex")
  val XXFORMS_DND_QNAME                   = xxformsQName("dnd")
  val XXFORMS_DEFERRED_UPDATES_QNAME      = xxformsQName("deferred-updates")
  val XXFORMS_TOGGLE_ANCESTORS_QNAME      = xxformsQName("toggle-ancestors")
  val XXFORMS_WHITESPACE_QNAME            = xxformsQName("whitespace")
  val XXFORMS_STRUCTURAL_DEPENDENCIES     = xxformsQName("structural-dependencies")

  val IGNORE_QUERY_STRING                 = QName("ignore-query-string")

  val XXFORMS_FORMAT_QNAME                = xxformsQName("format")
  val XXFORMS_UNFORMAT_QNAME              = xxformsQName("unformat")

  val XXFORMS_UPDATE_QNAME                = xxformsQName("update")
  val XFORMS_FULL_UPDATE = "full"

  val XXFORMS_XFORMS11_SWITCH_QNAME = xxformsQName("xforms11-switch")

  val XFORMS_INTEGER_QNAME = xformsQName("integer")

  val XFORMS_STRING_QNAME = xformsQName("string")
  val XFORMS_BASE64BINARY_QNAME = xformsQName("base64Binary")

  val XS_STRING_EXPLODED_QNAME = {

    // NOTE: Copied from `XMLConstants` for now as we don't yet have a common place for it. Maybe `xml-common` subproject?
    val XSD_PREFIX = "xs"
    val XSD_URI = "http://www.w3.org/2001/XMLSchema"
    val XSD_NAMESPACE = Namespace(XSD_PREFIX, XSD_URI)
    val XS_STRING_QNAME = QName("string", XSD_NAMESPACE)

    XS_STRING_QNAME.clarkName
  }

  val XFORMS_STRING_EXPLODED_QNAME = XFORMS_STRING_QNAME.clarkName

  val XXFORMS_EVENT_MODE_QNAME = xxformsQName("events-mode")
  val XXFORMS_VALIDATION_MODE_QNAME = xxformsQName("validation-mode")

  val STATIC_STATE_PROPERTIES_QNAME = QName("properties")

  val SELECTED_QNAME = QName("selected")
  val CASEREF_QNAME  = QName("caseref")

  val XXFORMS_MULTIPLE_QNAME = xxformsQName("multiple")

  // TODO: Move.
  val XFORMS_SERVER_SUBMIT = "/xforms-server-submit"

  // TODO: Move to handlers
  val DUMMY_SCRIPT_URI   = "data:text/javascript;base64,KGZ1bmN0aW9uKCl7fSgpKTsK"                       // empty self-calling function
}
