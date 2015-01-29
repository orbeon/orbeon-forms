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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.properties.JPropertySet;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.*;

public class XFormsProperties {

    public static final String[] EMPTY_STRING_ARRAY = new String[]{};

    public static final String XFORMS_PROPERTY_PREFIX = "oxf.xforms.";

    // Document properties
    public static final String STATE_HANDLING_PROPERTY = "state-handling";
    public static final String STATE_HANDLING_SERVER_VALUE = "server";
    public static final String STATE_HANDLING_CLIENT_VALUE = "client";

    public static final String NOSCRIPT_SUPPORT_PROPERTY = "noscript-support";
    public static final String NOSCRIPT_PROPERTY = "noscript";
    public static final String NOSCRIPT_TEMPLATE = "noscript-template";
    public static final String NOSCRIPT_TEMPLATE_STATIC_VALUE = "static";
    public static final String NOSCRIPT_TEMPLATE_DYNAMIC_VALUE = "dynamic";

    public static final String READONLY_APPEARANCE_PROPERTY = "readonly-appearance";
    public static final String READONLY_APPEARANCE_STATIC_VALUE = "static";
    public static final String READONLY_APPEARANCE_DYNAMIC_VALUE = "dynamic";
    public static final String READONLY_APPEARANCE_STATIC_SELECT_PROPERTY  = "readonly-appearance.static.select";
    public static final String READONLY_APPEARANCE_STATIC_SELECT1_PROPERTY = "readonly-appearance.static.select1";

    public static final String ORDER_PROPERTY = "order";
    public static final String DEFAULT_ORDER_PROPERTY = "label control help alert hint";

    public static final String HOST_LANGUAGE = "host-language";

    public static final String LABEL_ELEMENT_NAME_PROPERTY = "label-element";
    public static final String HINT_ELEMENT_NAME_PROPERTY = "hint-element";
    public static final String HELP_ELEMENT_NAME_PROPERTY = "help-element";
    public static final String ALERT_ELEMENT_NAME_PROPERTY = "alert-element";

    public static final String HINT_APPEARANCE_PROPERTY = "hint.appearance";
    public static final String HELP_APPEARANCE_PROPERTY = "help.appearance";

    public static final String EXTERNAL_EVENTS_PROPERTY = "external-events";

    public static final String OPTIMIZE_GET_ALL_PROPERTY = "optimize-get-all";
    public static final String OPTIMIZE_LOCAL_SUBMISSION_REPLACE_ALL_PROPERTY = "optimize-local-submission";
    public static final String LOCAL_SUBMISSION_FORWARD_PROPERTY = "local-submission-forward";
    public static final String LOCAL_SUBMISSION_INCLUDE_PROPERTY = "local-submission-include";
    public static final String LOCAL_INSTANCE_INCLUDE_PROPERTY = "local-instance-include";
//    public static final String XFORMS_OPTIMIZE_LOCAL_INSTANCE_LOADS_PROPERTY = "optimize-local-instance-loads";

    public static final String EXPOSE_XPATH_TYPES_PROPERTY = "expose-xpath-types";
    public static final String AJAX_SHOW_LOADING_ICON_PROPERTY = "ajax.show-loading-icon";
    public static final String AJAX_UPDATE_FULL_THRESHOLD = "ajax.update.full.threshold";
    public static final String NO_UPDATES = "no-updates";

    public static final String TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX = "format.output.";
    public static final String TYPE_INPUT_FORMAT_PROPERTY_PREFIX = "format.input.";

    public static final String DATE_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "date";
    public static final String DATETIME_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "dateTime";
    public static final String TIME_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "time";
    public static final String DECIMAL_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "decimal";
    public static final String INTEGER_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "integer";
    public static final String FLOAT_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "float";
    public static final String DOUBLE_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "double";

    public static final String DATE_FORMAT_INPUT_PROPERTY = TYPE_INPUT_FORMAT_PROPERTY_PREFIX + "date";
    public static final String TIME_FORMAT_INPUT_PROPERTY = TYPE_INPUT_FORMAT_PROPERTY_PREFIX + "time";

    public static final String DATEPICKER_NAVIGATOR_PROPERTY = "datepicker.navigator";
    public static final String DATEPICKER_TWO_MONTHS_PROPERTY = "datepicker.two-months";
    public static final String SHOW_ERROR_DIALOG_PROPERTY = "show-error-dialog";
    public static final String SHOW_RECOVERABLE_ERRORS_PROPERTY = "show-recoverable-errors";
    public static final String FATAL_ERRORS_DURING_INITIALIZATION_PROPERTY = "fatal-errors-during-initialization";

    public static final String LOGIN_PAGE_DETECTION_REGEXP = "login-page-detection-regexp";
    public static final String CLIENT_EVENTS_MODE_PROPERTY = "client.events.mode";
    public static final String CLIENT_EVENTS_FILTER_PROPERTY = "client.events.filter";

    public static final String SESSION_HEARTBEAT_PROPERTY = "session-heartbeat";
    public static final String SESSION_HEARTBEAT_DELAY_PROPERTY = "session-heartbeat-delay";
    public static final String DELAY_BEFORE_INCREMENTAL_REQUEST_PROPERTY = "delay-before-incremental-request";
    public static final String DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_PROPERTY = "delay-before-force-incremental-request";
    public static final String DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_PROPERTY = "delay-before-gecko-communication-error";
    public static final String DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_PROPERTY = "delay-before-close-minimal-dialog";
    public static final String INTERNAL_SHORT_DELAY_PROPERTY = "internal-short-delay";
    public static final String DELAY_BEFORE_DISPLAY_LOADING_PROPERTY = "delay-before-display-loading";
    public static final String DELAY_BEFORE_UPLOAD_PROGRESS_REFRESH_PROPERTY = "delay-before-upload-progress-refresh";
    public static final String DEBUG_WINDOW_HEIGHT_PROPERTY = "debug-window-height";
    public static final String DEBUG_WINDOW_WIDTH_PROPERTY = "debug-window-width";
    public static final String LOADING_MIN_TOP_PADDING_PROPERTY = "loading-min-top-padding";

    public static final String REVISIT_HANDLING_PROPERTY = "revisit-handling";
    public static final String REVISIT_HANDLING_RESTORE_VALUE = "restore";
    public static final String REVISIT_HANDLING_RELOAD_VALUE = "reload";

    public static final String HELP_HANDLER_PROPERTY = "help-handler";
    public static final String HELP_TOOLTIP_PROPERTY = "help-tooltip";

    public static final String ASYNC_SUBMISSION_POLL_DELAY = "submission-poll-delay";

    // TODO: Make these global properties, see https://github.com/orbeon/orbeon-forms/issues/1391
    public static final String DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY = "delay-before-ajax-timeout";
    public static final String RETRY_DELAY_INCREMENT = "retry.delay-increment";
    public static final String RETRY_MAX_DELAY = "retry.max-delay";

    public static final String USE_ARIA = "use-aria";

    public static final String XFORMS11_SWITCH_PROPERTY = "xforms11-switch";

    public static final String ENCRYPT_ITEM_VALUES_PROPERTY = "encrypt-item-values";
    public static final String XPATH_ANALYSIS_PROPERTY = "xpath-analysis";

    // TODO: Make this a global property: right now it is used 1/2 global, 1/2 document
    public static final String CACHE_DOCUMENT_PROPERTY = "cache.document";
    public static final boolean CACHE_DOCUMENT_DEFAULT = true;

    public static final String DATATABLE_INIT_IN_VIEWPORT = "xbl.fr.datatable.init-in-viewport";

    public static final String SANITIZE_PROPERTY = "sanitize";

    public static class PropertyDefinition {

        public final String name;
        public final Object defaultValue;
        public final boolean isPropagateToClient;

        public PropertyDefinition(String name, String defaultValue, boolean propagateToClient) {
            this.name = name;
            this.defaultValue = defaultValue;
            isPropagateToClient = propagateToClient;
        }

        public PropertyDefinition(String name, boolean defaultValue, boolean propagateToClient) {
            this.name = name;
            this.defaultValue = defaultValue;
            isPropagateToClient = propagateToClient;
        }

        public PropertyDefinition(String name, int defaultValue, boolean propagateToClient) {
            this.name = name;
            this.defaultValue = defaultValue;
            isPropagateToClient = propagateToClient;
        }

        public final Object parseProperty(String value) {
            if (defaultValue instanceof Integer) {
                return Integer.parseInt(value);
            } else if (defaultValue instanceof Boolean) {
                return Boolean.valueOf(value);
            } else {
                return value;
            }
        }

        public void validate(Object value, LocationData locationData) {}
    }

    public static final PropertyDefinition[] SUPPORTED_DOCUMENT_PROPERTIES_DEFAULTS = {
            new PropertyDefinition(STATE_HANDLING_PROPERTY, STATE_HANDLING_SERVER_VALUE, false) {
                @Override
                public void validate(Object value, LocationData locationData) {
                    final String stringValue = value.toString();
                    if (!(stringValue.equals(XFormsProperties.STATE_HANDLING_SERVER_VALUE)
                            || stringValue.equals(XFormsProperties.STATE_HANDLING_CLIENT_VALUE)))
                        throw new ValidationException("Invalid xxf:" + name
                                + " property value value: " + stringValue, locationData);
                }
            },
            new PropertyDefinition(NOSCRIPT_SUPPORT_PROPERTY, true, false),
            new PropertyDefinition(NOSCRIPT_PROPERTY, false, false),
            new PropertyDefinition(NOSCRIPT_TEMPLATE, NOSCRIPT_TEMPLATE_STATIC_VALUE, false),
            new PropertyDefinition(READONLY_APPEARANCE_PROPERTY, READONLY_APPEARANCE_DYNAMIC_VALUE, false) {
                @Override
                public void validate(Object value, LocationData locationData) {
                    final String stringValue = value.toString();
                    if (! XFormsUtils.maybeAVT(stringValue) && !(stringValue.equals(XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE)
                            || stringValue.equals(XFormsProperties.READONLY_APPEARANCE_DYNAMIC_VALUE)))
                        throw new ValidationException("Invalid xxf:" + name
                                + " property value value: " + stringValue, locationData);
                }
            },
            new PropertyDefinition(READONLY_APPEARANCE_STATIC_SELECT_PROPERTY, "full", false),
            new PropertyDefinition(READONLY_APPEARANCE_STATIC_SELECT1_PROPERTY, "full", false),
            new PropertyDefinition(ORDER_PROPERTY, DEFAULT_ORDER_PROPERTY, false),
            new PropertyDefinition(HOST_LANGUAGE, "xhtml", false),
            new PropertyDefinition(LABEL_ELEMENT_NAME_PROPERTY, "label", false),
            new PropertyDefinition(HINT_ELEMENT_NAME_PROPERTY, "span", false),
            new PropertyDefinition(HELP_ELEMENT_NAME_PROPERTY, "span", false),
            new PropertyDefinition(ALERT_ELEMENT_NAME_PROPERTY, "span", false),
            new PropertyDefinition(HINT_APPEARANCE_PROPERTY, "inline", false),
            new PropertyDefinition(HELP_APPEARANCE_PROPERTY, "dialog", false),
            new PropertyDefinition(EXTERNAL_EVENTS_PROPERTY, "", false),
            new PropertyDefinition(OPTIMIZE_GET_ALL_PROPERTY, true, false),
            new PropertyDefinition(OPTIMIZE_LOCAL_SUBMISSION_REPLACE_ALL_PROPERTY, true, false),
            new PropertyDefinition(LOCAL_SUBMISSION_FORWARD_PROPERTY, true, false),
            new PropertyDefinition(LOCAL_SUBMISSION_INCLUDE_PROPERTY, false, false),
            new PropertyDefinition(LOCAL_INSTANCE_INCLUDE_PROPERTY, false, false),
            new PropertyDefinition(EXPOSE_XPATH_TYPES_PROPERTY, false, false),
            new PropertyDefinition(AJAX_SHOW_LOADING_ICON_PROPERTY, true, false),
            new PropertyDefinition(SHOW_RECOVERABLE_ERRORS_PROPERTY, 10, false),
            new PropertyDefinition(FATAL_ERRORS_DURING_INITIALIZATION_PROPERTY, true, false),
            new PropertyDefinition(DATE_FORMAT_PROPERTY, "if (. castable as xs:date) then format-date(xs:date(.), '[FNn] [MNn] [D], [Y] [ZN]', 'en', (), ()) else .", false),
            new PropertyDefinition(DATETIME_FORMAT_PROPERTY, "if (. castable as xs:dateTime) then format-dateTime(xs:dateTime(.), '[FNn] [MNn] [D], [Y] [H01]:[m01]:[s01] [ZN]', 'en', (), ()) else .", false),
            new PropertyDefinition(TIME_FORMAT_PROPERTY, "if (. castable as xs:time) then format-time(xs:time(.), '[H01]:[m01]:[s01] [ZN]', 'en', (), ()) else .", false),
            new PropertyDefinition(DECIMAL_FORMAT_PROPERTY, "if (. castable as xs:decimal) then format-number(xs:decimal(.),'###,###,###,##0.00') else .", false),
            new PropertyDefinition(INTEGER_FORMAT_PROPERTY, "if (. castable as xs:integer) then format-number(xs:integer(.),'###,###,###,##0') else .", false),
            new PropertyDefinition(FLOAT_FORMAT_PROPERTY, "if (. castable as xs:float) then format-number(xs:float(.),'#,##0.000') else .", false),
            new PropertyDefinition(DOUBLE_FORMAT_PROPERTY, "if (. castable as xs:double) then format-number(xs:double(.),'#,##0.000') else .", false),
            new PropertyDefinition(ENCRYPT_ITEM_VALUES_PROPERTY, true, false),
            new PropertyDefinition(ASYNC_SUBMISSION_POLL_DELAY, 10 * 1000, false), // 10 seconds
            new PropertyDefinition(AJAX_UPDATE_FULL_THRESHOLD, 20, false),
            new PropertyDefinition(NO_UPDATES, false, false),
            new PropertyDefinition(XFORMS11_SWITCH_PROPERTY, false, false), // false for now, but default should change at some point
            new PropertyDefinition(XPATH_ANALYSIS_PROPERTY, false, false),
            new PropertyDefinition(CACHE_DOCUMENT_PROPERTY, CACHE_DOCUMENT_DEFAULT, false),
            new PropertyDefinition(SANITIZE_PROPERTY, "", false),

            // Properties to propagate to the client
            new PropertyDefinition(RETRY_DELAY_INCREMENT, 5000, true),
            new PropertyDefinition(RETRY_MAX_DELAY, 30000, true),
            new PropertyDefinition(USE_ARIA, false, true),
            new PropertyDefinition(SESSION_HEARTBEAT_PROPERTY, true, true),
            new PropertyDefinition(SESSION_HEARTBEAT_DELAY_PROPERTY, 12 * 60 * 60 * 800, true), // dynamic; 80 % of 12 hours in ms
            new PropertyDefinition(DELAY_BEFORE_INCREMENTAL_REQUEST_PROPERTY, 500, true),
            new PropertyDefinition(DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_PROPERTY, 2000, true),
            new PropertyDefinition(DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_PROPERTY, 5000, true),
            new PropertyDefinition(DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_PROPERTY, 5000, true),
            new PropertyDefinition(DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY, 30000, true),
            new PropertyDefinition(INTERNAL_SHORT_DELAY_PROPERTY, 10, true),
            new PropertyDefinition(DELAY_BEFORE_DISPLAY_LOADING_PROPERTY, 500, true),
            new PropertyDefinition(DELAY_BEFORE_UPLOAD_PROGRESS_REFRESH_PROPERTY, 2000, true),
            new PropertyDefinition(DEBUG_WINDOW_HEIGHT_PROPERTY, 600, true),
            new PropertyDefinition(DEBUG_WINDOW_WIDTH_PROPERTY, 300, true),
            new PropertyDefinition(LOADING_MIN_TOP_PADDING_PROPERTY, 10, true),
            new PropertyDefinition(REVISIT_HANDLING_PROPERTY, REVISIT_HANDLING_RESTORE_VALUE, true),
            new PropertyDefinition(HELP_HANDLER_PROPERTY, false, true),// dynamic
            new PropertyDefinition(HELP_TOOLTIP_PROPERTY, false, true),
            new PropertyDefinition(DATE_FORMAT_INPUT_PROPERTY, "[M]/[D]/[Y]", true),
            new PropertyDefinition(TIME_FORMAT_INPUT_PROPERTY, "[h]:[m]:[s] [P]", true),
            new PropertyDefinition(DATEPICKER_NAVIGATOR_PROPERTY, true, true),
            new PropertyDefinition(DATEPICKER_TWO_MONTHS_PROPERTY, false, true),
            new PropertyDefinition(SHOW_ERROR_DIALOG_PROPERTY, true, true),
            new PropertyDefinition(LOGIN_PAGE_DETECTION_REGEXP, "", true),
            new PropertyDefinition(CLIENT_EVENTS_MODE_PROPERTY, "default", true),
            new PropertyDefinition(CLIENT_EVENTS_FILTER_PROPERTY, "", true),
            new PropertyDefinition(DATATABLE_INIT_IN_VIEWPORT, false, true)
    };

    public static final Map<String, PropertyDefinition> SUPPORTED_DOCUMENT_PROPERTIES;
    static {
        final Map<String, PropertyDefinition> tempMap = new HashMap<String, PropertyDefinition>();
        for (final PropertyDefinition propertyDefinition: SUPPORTED_DOCUMENT_PROPERTIES_DEFAULTS) {
            tempMap.put(propertyDefinition.name, propertyDefinition);
        }
        SUPPORTED_DOCUMENT_PROPERTIES = Collections.unmodifiableMap(tempMap);
    }

    // Global properties
    public static final String GZIP_STATE_PROPERTY = XFORMS_PROPERTY_PREFIX + "gzip-state"; // global but could possibly be per document
    public static final boolean GZIP_STATE_DEFAULT = true;

    public static final String HOST_LANGUAGE_AVTS_PROPERTY = XFORMS_PROPERTY_PREFIX + "host-language-avts"; // global but should be per document
    public static final String ADDITIONAL_AVT_ELEMENT_NAMESPACES = XFORMS_PROPERTY_PREFIX + "additional-avt-element-namespaces"; // global but should be per document
    public static final String ADDITIONAL_REF_ID_ATTRIBUTE_NAMES = XFORMS_PROPERTY_PREFIX + "additional-ref-id-attribute-names"; // global but should be per document
    public static final boolean HOST_LANGUAGE_AVTS_DEFAULT = false;

    public static final String MINIMAL_RESOURCES_PROPERTY = XFORMS_PROPERTY_PREFIX + "minimal-resources";
    public static final boolean MINIMAL_RESOURCES_PROPERTY_DEFAULT = true;

    public static final String COMBINE_RESOURCES_PROPERTY = XFORMS_PROPERTY_PREFIX + "combine-resources";
    public static final boolean COMBINE_RESOURCES_PROPERTY_DEFAULT = true;

    public static final String CACHE_COMBINED_RESOURCES_PROPERTY = XFORMS_PROPERTY_PREFIX + "cache-combined-resources";
    public static final boolean CACHE_COMBINED_RESOURCES_DEFAULT = false;

    public static final String JAVASCRIPT_AT_BOTTOM_PROPERTY = XFORMS_PROPERTY_PREFIX + "resources.javascript-at-bottom";
    public static final boolean JAVASCRIPT_AT_BOTTOM_PROPERTY_DEFAULT = true;

    public static final String ENCODE_VERSION_PROPERTY = XFORMS_PROPERTY_PREFIX + "resources.encode-version";
    public static final boolean ENCODE_VERSION_PROPERTY_DEFAULT = true;

    public static final String BASELINE_PROPERTY = XFORMS_PROPERTY_PREFIX + "resources.baseline";

    public static final String DEBUG_LOGGING_PROPERTY = XFORMS_PROPERTY_PREFIX + "logging.debug";
    public static final String ERROR_LOGGING_PROPERTY = XFORMS_PROPERTY_PREFIX + "logging.error";

    public static final String DEBUG_LOG_XPATH_ANALYSIS_PROPERTY = XFORMS_PROPERTY_PREFIX + "debug.log-xpath-analysis";
    public static final String DEBUG_REQUEST_STATS_PROPERTY      = XFORMS_PROPERTY_PREFIX + "debug.log-request-stats";

    public static final String LOCATION_MODE_PROPERTY = XFORMS_PROPERTY_PREFIX + "location-mode";

    // == Global properties ============================================================================================
    /**
     * Return a PropertyDefinition given a property name.
     *
     * @param propertyName  property name
     * @return              PropertyDefinition
     */
    public static PropertyDefinition getPropertyDefinition(String propertyName) {
        return SUPPORTED_DOCUMENT_PROPERTIES.get(propertyName);
    }

    public static Set<String> getDebugLogging() {
        final Set<String> result = Properties.instance().getPropertySet().getNmtokens(DEBUG_LOGGING_PROPERTY);
        return (result != null) ? result : Collections.<String>emptySet();
    }

    public static Set<String> getErrorLogging() {
        final Set<String> result = Properties.instance().getPropertySet().getNmtokens(ERROR_LOGGING_PROPERTY);
        return (result != null) ? result : Collections.<String>emptySet();
    }

    public static boolean isCacheDocument() {
        return Properties.instance().getPropertySet().getBoolean
                (CACHE_DOCUMENT_PROPERTY, CACHE_DOCUMENT_DEFAULT);
    }

    public static boolean isGZIPState() {
        return Properties.instance().getPropertySet().getBoolean
                (GZIP_STATE_PROPERTY, GZIP_STATE_DEFAULT);
    }

    public static boolean isHostLanguageAVTs() {
        return Properties.instance().getPropertySet().getBoolean
                (HOST_LANGUAGE_AVTS_PROPERTY, HOST_LANGUAGE_AVTS_DEFAULT);
    }

    public static String[] getAdditionalAvtElementNamespaces() {
        final String additionalElementNamespacesStr = Properties.instance().getPropertySet().getString
                (ADDITIONAL_AVT_ELEMENT_NAMESPACES);

        return additionalElementNamespacesStr != null?additionalElementNamespacesStr.split("\\s"):EMPTY_STRING_ARRAY;
    }

    public static String[] getAdditionalRefIdAttributeNames() {
        String additionalRefIdAttributeNames = Properties.instance().getPropertySet().getString
                (ADDITIONAL_REF_ID_ATTRIBUTE_NAMES);
        return additionalRefIdAttributeNames != null?additionalRefIdAttributeNames.split("\\s"):EMPTY_STRING_ARRAY;
    }

    public static boolean isMinimalResources() {
        return Properties.instance().getPropertySet().getBoolean
                (MINIMAL_RESOURCES_PROPERTY, MINIMAL_RESOURCES_PROPERTY_DEFAULT);
    }

    public static boolean isCombinedResources() {
        return Properties.instance().getPropertySet().getBoolean
                (COMBINE_RESOURCES_PROPERTY, COMBINE_RESOURCES_PROPERTY_DEFAULT);
    }

    public static boolean isCacheCombinedResources() {
        return Properties.instance().getPropertySet().getBoolean
                (CACHE_COMBINED_RESOURCES_PROPERTY, CACHE_COMBINED_RESOURCES_DEFAULT);
    }

    public static boolean isJavaScriptAtBottom() {
        return Properties.instance().getPropertySet().getBoolean
                (JAVASCRIPT_AT_BOTTOM_PROPERTY, JAVASCRIPT_AT_BOTTOM_PROPERTY_DEFAULT);
    }

    public static boolean isEncodeVersion() {
        return Properties.instance().getPropertySet().getBoolean
                (ENCODE_VERSION_PROPERTY, ENCODE_VERSION_PROPERTY_DEFAULT);
    }

    public static JPropertySet.Property getResourcesBaseline() {
        return Properties.instance().getPropertySet().getProperty(BASELINE_PROPERTY);
    }

    public static boolean getDebugLogXPathAnalysis() {
        return Properties.instance().getPropertySet().getBoolean(DEBUG_LOG_XPATH_ANALYSIS_PROPERTY, false);
    }

    public static boolean isRequestStats() {
        return Properties.instance().getPropertySet().getBoolean(DEBUG_REQUEST_STATS_PROPERTY, false);
    }

    public static int getAjaxTimeout() {
        return Properties.instance().getPropertySet().getInteger(XFORMS_PROPERTY_PREFIX + DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY, ((Integer) getPropertyDefinition(DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY).defaultValue).intValue());
    }

    public static int getRetryDelayIncrement() {
        return Properties.instance().getPropertySet().getInteger(XFORMS_PROPERTY_PREFIX + RETRY_DELAY_INCREMENT, ((Integer) getPropertyDefinition(RETRY_DELAY_INCREMENT).defaultValue).intValue());
    }

    public static boolean isKeepLocation() {
        return ! Properties.instance().getPropertySet().getString(LOCATION_MODE_PROPERTY, "none").equals("none");
    }
}
