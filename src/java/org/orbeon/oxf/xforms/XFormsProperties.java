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

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.*;

public class XFormsProperties {

    public static final String XFORMS_PROPERTY_PREFIX = "oxf.xforms.";

    // Document properties
    public static final String STATE_HANDLING_PROPERTY = "state-handling";
    public static final String STATE_HANDLING_SERVER_VALUE = "server";
    public static final String STATE_HANDLING_CLIENT_VALUE = "client";

    public static final String NOSCRIPT_PROPERTY = "noscript";
    public static final String NOSCRIPT_SUPPORT_PROPERTY = "noscript-support";
    public static final String AJAX_PORTLET_PROPERTY = "ajax-portlet";

    public static final String READONLY_APPEARANCE_PROPERTY = "readonly-appearance";
    public static final String READONLY_APPEARANCE_STATIC_VALUE = "static";
    public static final String READONLY_APPEARANCE_DYNAMIC_VALUE = "dynamic";

    public static final String ORDER_PROPERTY = "order";
    public static final String DEFAULT_ORDER_PROPERTY = "label control help alert hint";

    public static final String LABEL_ELEMENT_NAME_PROPERTY = "label-element";
    public static final String HINT_ELEMENT_NAME_PROPERTY = "hint-element";
    public static final String HELP_ELEMENT_NAME_PROPERTY = "help-element";
    public static final String ALERT_ELEMENT_NAME_PROPERTY = "alert-element";

    public static final String EXTERNAL_EVENTS_PROPERTY = "external-events";
    private static final String READONLY_PROPERTY = "readonly";

    private static final String OPTIMIZE_GET_ALL_PROPERTY = "optimize-get-all";
    private static final String OPTIMIZE_LOCAL_SUBMISSION_REPLACE_ALL_PROPERTY = "optimize-local-submission";
    private static final String LOCAL_SUBMISSION_FORWARD_PROPERTY = "local-submission-forward";
    private static final String LOCAL_SUBMISSION_INCLUDE_PROPERTY = "local-submission-include";
    private static final String LOCAL_INSTANCE_INCLUDE_PROPERTY = "local-instance-include";
//    private static final String XFORMS_OPTIMIZE_LOCAL_INSTANCE_LOADS_PROPERTY = "optimize-local-instance-loads";
    private static final String EXPOSE_XPATH_TYPES_PROPERTY = "expose-xpath-types";
    private static final String AJAX_SHOW_LOADING_ICON_PROPERTY = "ajax.show-loading-icon";
    private static final String AJAX_SHOW_ERRORS_PROPERTY = "ajax.show-errors";
    private static final String AJAX_UPDATE_FULL_THRESHOLD = "ajax.update.full.threshold";

    private static final String TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX = "format.output.";
    private static final String TYPE_INPUT_FORMAT_PROPERTY_PREFIX = "format.input.";

    private static final String DATE_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "date";
    private static final String DATETIME_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "dateTime";
    private static final String TIME_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "time";
    private static final String DECIMAL_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "decimal";
    private static final String INTEGER_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "integer";
    private static final String FLOAT_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "float";
    private static final String DOUBLE_FORMAT_PROPERTY = TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + "double";

    private static final String DATE_FORMAT_INPUT_PROPERTY = TYPE_INPUT_FORMAT_PROPERTY_PREFIX + "date";
    private static final String TIME_FORMAT_INPUT_PROPERTY = TYPE_INPUT_FORMAT_PROPERTY_PREFIX + "time";

    private static final String DATEPICKER_NAVIGATOR_PROPERTY = "datepicker.navigator";
    private static final String DATEPICKER_TWO_MONTHS_PROPERTY = "datepicker.two-months";
    private static final String SHOW_ERROR_DIALOG_PROPERTY = "show-error-dialog";

    private static final String CLIENT_EVENTS_MODE_PROPERTY = "client.events.mode";
    private static final String CLIENT_EVENTS_FILTER_PROPERTY = "client.events.filter";

    private static final String SESSION_HEARTBEAT_PROPERTY = "session-heartbeat";
    public static final String SESSION_HEARTBEAT_DELAY_PROPERTY = "session-heartbeat-delay";
    private static final String DELAY_BEFORE_INCREMENTAL_REQUEST_PROPERTY = "delay-before-incremental-request";
    private static final String DELAY_BEFORE_FORCE_INCREMENTAL_REQUEST_PROPERTY = "delay-before-force-incremental-request";
    private static final String DELAY_BEFORE_GECKO_COMMUNICATION_ERROR_PROPERTY = "delay-before-gecko-communication-error";
    private static final String DELAY_BEFORE_CLOSE_MINIMAL_DIALOG_PROPERTY = "delay-before-close-minimal-dialog";
    private static final String DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY = "delay-before-ajax-timeout";
    private static final String INTERNAL_SHORT_DELAY_PROPERTY = "internal-short-delay";
    private static final String DELAY_BEFORE_DISPLAY_LOADING_PROPERTY = "delay-before-display-loading";
    private static final String DELAY_BEFORE_UPLOAD_PROGRESS_REFRESH_PROPERTY = "delay-before-upload-progress-refresh";
    private static final String DEBUG_WINDOW_HEIGHT_PROPERTY = "debug-window-height";
    private static final String DEBUG_WINDOW_WIDTH_PROPERTY = "debug-window-width";
    private static final String LOADING_MIN_TOP_PADDING_PROPERTY = "loading-min-top-padding";

    private static final String REVISIT_HANDLING_PROPERTY = "revisit-handling";
    public static final String REVISIT_HANDLING_RESTORE_VALUE = "restore";
    public static final String REVISIT_HANDLING_RELOAD_VALUE = "reload";

    public static final String HELP_HANDLER_PROPERTY = "help-handler";
    private static final String HELP_TOOLTIP_PROPERTY = "help-tooltip";
    public static final String OFFLINE_SUPPORT_PROPERTY = "offline";
    public static final String OFFLINE_REPEAT_COUNT_PROPERTY = "offline-repeat-count";
    public static final String FORWARD_SUBMISSION_HEADERS = "forward-submission-headers";
    public static final String DEFAULT_FORWARD_SUBMISSION_HEADERS = "";

    private static final String ASYNC_SUBMISSION_POLL_DELAY = "submission-poll-delay";

    public static final String NEW_XHTML_LAYOUT = "new-xhtml-layout";   // deprecated
    public static final String XHTML_LAYOUT = "xhtml-layout";
    public static final String RETRY_DELAY_INCREMENT = "retry.delay-increment";
    public static final String RETRY_MAX_DELAY = "retry.max-delay";

    public static final String XFORMS11_SWITCH_PROPERTY = "xforms11-switch";

    public enum XHTMLLayout { NOSPAN, SPAN }

    public static final String ENCRYPT_ITEM_VALUES_PROPERTY = "encrypt-item-values";
    public static final String XPATH_ANALYSIS_PROPERTY = "xpath-analysis";

    public static final String CACHE_DOCUMENT_PROPERTY = "cache.document";
    private static final boolean CACHE_DOCUMENT_DEFAULT = true;

    public static class PropertyDefinition {

        private String name;
        private Object defaultValue;
        private boolean isPropagateToClient;

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

        public String getName() {
            return name;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public boolean isPropagateToClient() {
            return isPropagateToClient;
        }

        public Object parseProperty(String value) {
            if (getDefaultValue() instanceof Integer) {
                return new Integer(value);
            } else if (getDefaultValue() instanceof Boolean) {
                return Boolean.valueOf(value);
            } else {
                return value;
            }
        }

        public void validate(Object value, LocationData locationData) {}
    }

    private static final PropertyDefinition[] SUPPORTED_DOCUMENT_PROPERTIES_DEFAULTS = {
            new PropertyDefinition(STATE_HANDLING_PROPERTY, STATE_HANDLING_SERVER_VALUE, false) {
                @Override
                public void validate(Object value, LocationData locationData) {
                    final String stringValue = value.toString();
                    if (!(stringValue.equals(XFormsProperties.STATE_HANDLING_SERVER_VALUE)
                            || stringValue.equals(XFormsProperties.STATE_HANDLING_CLIENT_VALUE)))
                        throw new ValidationException("Invalid xxforms:" + getName()
                                + " property value value: " + stringValue, locationData);
                }
            },
            new PropertyDefinition(NOSCRIPT_PROPERTY, false, false),
            new PropertyDefinition(NOSCRIPT_SUPPORT_PROPERTY, true, false),
            new PropertyDefinition(AJAX_PORTLET_PROPERTY, false, false),
            new PropertyDefinition(READONLY_PROPERTY, false, false),
            new PropertyDefinition(READONLY_APPEARANCE_PROPERTY, READONLY_APPEARANCE_DYNAMIC_VALUE, false) {
                @Override
                public void validate(Object value, LocationData locationData) {
                    final String stringValue = value.toString();
                    if (!(stringValue.equals(XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE)
                            || stringValue.equals(XFormsProperties.READONLY_APPEARANCE_DYNAMIC_VALUE)))
                        throw new ValidationException("Invalid xxforms:" + getName()
                                + " property value value: " + stringValue, locationData);
                }
            },
            new PropertyDefinition(ORDER_PROPERTY, DEFAULT_ORDER_PROPERTY, false),
            new PropertyDefinition(LABEL_ELEMENT_NAME_PROPERTY, "label", false),
            new PropertyDefinition(HINT_ELEMENT_NAME_PROPERTY, "span", false),
            new PropertyDefinition(HELP_ELEMENT_NAME_PROPERTY, "span", false),
            new PropertyDefinition(ALERT_ELEMENT_NAME_PROPERTY, "span", false),
            new PropertyDefinition(EXTERNAL_EVENTS_PROPERTY, "", false),
            new PropertyDefinition(OPTIMIZE_GET_ALL_PROPERTY, true, false),
            new PropertyDefinition(OPTIMIZE_LOCAL_SUBMISSION_REPLACE_ALL_PROPERTY, true, false),
            new PropertyDefinition(LOCAL_SUBMISSION_FORWARD_PROPERTY, true, false),
            new PropertyDefinition(LOCAL_SUBMISSION_INCLUDE_PROPERTY, false, false),
            new PropertyDefinition(LOCAL_INSTANCE_INCLUDE_PROPERTY, false, false),
            new PropertyDefinition(EXPOSE_XPATH_TYPES_PROPERTY, false, false),
            new PropertyDefinition(AJAX_SHOW_LOADING_ICON_PROPERTY, true, false),
            new PropertyDefinition(AJAX_SHOW_ERRORS_PROPERTY, true, false),
            new PropertyDefinition(DATE_FORMAT_PROPERTY, "if (. castable as xs:date) then format-date(xs:date(.), '[FNn] [MNn] [D], [Y] [ZN]', 'en', (), ()) else .", false),
            new PropertyDefinition(DATETIME_FORMAT_PROPERTY, "if (. castable as xs:dateTime) then format-dateTime(xs:dateTime(.), '[FNn] [MNn] [D], [Y] [H01]:[m01]:[s01] [ZN]', 'en', (), ()) else .", false),
            new PropertyDefinition(TIME_FORMAT_PROPERTY, "if (. castable as xs:time) then format-time(xs:time(.), '[H01]:[m01]:[s01] [ZN]', 'en', (), ()) else .", false),
            new PropertyDefinition(DECIMAL_FORMAT_PROPERTY, "if (. castable as xs:decimal) then format-number(xs:decimal(.),'###,###,###,##0.00') else .", false),
            new PropertyDefinition(INTEGER_FORMAT_PROPERTY, "if (. castable as xs:integer) then format-number(xs:integer(.),'###,###,###,##0') else .", false),
            new PropertyDefinition(FLOAT_FORMAT_PROPERTY, "if (. castable as xs:float) then format-number(xs:float(.),'#,##0.000') else .", false),
            new PropertyDefinition(DOUBLE_FORMAT_PROPERTY, "if (. castable as xs:double) then format-number(xs:double(.),'#,##0.000') else .", false),
            new PropertyDefinition(ENCRYPT_ITEM_VALUES_PROPERTY, true, false),
            new PropertyDefinition(OFFLINE_REPEAT_COUNT_PROPERTY, 4, false),
            new PropertyDefinition(FORWARD_SUBMISSION_HEADERS, DEFAULT_FORWARD_SUBMISSION_HEADERS, false),
            new PropertyDefinition(ASYNC_SUBMISSION_POLL_DELAY, 10 * 1000, false), // 10 seconds
            new PropertyDefinition(AJAX_UPDATE_FULL_THRESHOLD, 20, false),
            new PropertyDefinition(XFORMS11_SWITCH_PROPERTY, false, false), // false for now, but default should change at some point
            new PropertyDefinition(XPATH_ANALYSIS_PROPERTY, false, false),
            new PropertyDefinition(CACHE_DOCUMENT_PROPERTY, CACHE_DOCUMENT_DEFAULT, false),

            // Properties to propagate to the client
            new PropertyDefinition(NEW_XHTML_LAYOUT, false, true),
            new PropertyDefinition(XHTML_LAYOUT, XHTMLLayout.NOSPAN.toString().toLowerCase(), true),
            new PropertyDefinition(RETRY_DELAY_INCREMENT, 5000, true),
            new PropertyDefinition(RETRY_MAX_DELAY, 30000, true),
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
            new PropertyDefinition(OFFLINE_SUPPORT_PROPERTY, false, true),// dynamic
            new PropertyDefinition(DATE_FORMAT_INPUT_PROPERTY, "[M]/[D]/[Y]", true),
            new PropertyDefinition(TIME_FORMAT_INPUT_PROPERTY, "[h]:[m]:[s] [P]", true),
            new PropertyDefinition(DATEPICKER_NAVIGATOR_PROPERTY, true, true),
            new PropertyDefinition(DATEPICKER_TWO_MONTHS_PROPERTY, false, true),
            new PropertyDefinition(SHOW_ERROR_DIALOG_PROPERTY, true, true),
            new PropertyDefinition(CLIENT_EVENTS_MODE_PROPERTY, "default", true),
            new PropertyDefinition(CLIENT_EVENTS_FILTER_PROPERTY, "", true)
    };

    private static final Map<Object, PropertyDefinition> SUPPORTED_DOCUMENT_PROPERTIES;
    static {
        final Map<Object, PropertyDefinition> tempMap = new HashMap<Object, PropertyDefinition>();
        for (final PropertyDefinition propertyDefinition: SUPPORTED_DOCUMENT_PROPERTIES_DEFAULTS) {
            tempMap.put(propertyDefinition.name, propertyDefinition);
        }
        SUPPORTED_DOCUMENT_PROPERTIES = Collections.unmodifiableMap(tempMap);
    }

    // Global properties
    private static final String PASSWORD_PROPERTY = XFORMS_PROPERTY_PREFIX + "password";

    private static final String STORE_APPLICATION_SIZE_PROPERTY = XFORMS_PROPERTY_PREFIX + "store.application.size";
    private static final int STORE_APPLICATION_SIZE_DEFAULT = 20 * 1024 * 1024;

    private static final String STORE_APPLICATION_USERNAME_PROPERTY = XFORMS_PROPERTY_PREFIX + "store.application.username";
    private static final String STORE_APPLICATION_PASSWORD_PROPERTY = XFORMS_PROPERTY_PREFIX + "store.application.password";
    private static final String STORE_APPLICATION_URI_PROPERTY = XFORMS_PROPERTY_PREFIX + "store.application.uri";
    private static final String STORE_APPLICATION_COLLECTION_PROPERTY = XFORMS_PROPERTY_PREFIX + "store.application.collection";

    private static final String STORE_APPLICATION_USERNAME_DEFAULT = "guest";
    private static final String STORE_APPLICATION_PASSWORD_DEFAULT = "";
    private static final String STORE_APPLICATION_URI_DEFAULT = "xmldb:exist:///";
    private static final String STORE_APPLICATION_COLLECTION_DEFAULT = "/db/orbeon/xforms/cache/";

    private static final String GZIP_STATE_PROPERTY = XFORMS_PROPERTY_PREFIX + "gzip-state"; // global but could possibly be per document
    private static final boolean GZIP_STATE_DEFAULT = true;

    private static final String HOST_LANGUAGE_AVTS_PROPERTY = XFORMS_PROPERTY_PREFIX + "host-language-avts"; // global but should be per document
    private static final boolean HOST_LANGUAGE_AVTS_DEFAULT = false;

    private static final String MINIMAL_RESOURCES_PROPERTY = XFORMS_PROPERTY_PREFIX + "minimal-resources";
    private static final boolean MINIMAL_RESOURCES_PROPERTY_DEFAULT = true;

    private static final String COMBINE_RESOURCES_PROPERTY = XFORMS_PROPERTY_PREFIX + "combine-resources";
    private static final boolean COMBINE_RESOURCES_PROPERTY_DEFAULT = true;

    private static final String CACHE_COMBINED_RESOURCES_PROPERTY = XFORMS_PROPERTY_PREFIX + "cache-combined-resources";
    private static final boolean CACHE_COMBINED_RESOURCES_DEFAULT = false;

    private static final String JAVASCRIPT_AT_BOTTOM_PROPERTY = XFORMS_PROPERTY_PREFIX + "resources.javascript-at-bottom";
    private static final boolean JAVASCRIPT_AT_BOTTOM_PROPERTY_DEFAULT = true;

    private static final String BASELINE_PROPERTY = XFORMS_PROPERTY_PREFIX + "resources.baseline";

    private static final String DEBUG_LOGGING_PROPERTY = XFORMS_PROPERTY_PREFIX + "logging.debug";
    private static final String ERROR_LOGGING_PROPERTY = XFORMS_PROPERTY_PREFIX + "logging.error";

    private static final String DEBUG_LOG_XPATH_ANALYSIS = XFORMS_PROPERTY_PREFIX + "debug.log-xpath-analysis";

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

    /**
     * Return an iterator over property definition entries.
     *
     * @return  Iterator<Entry<String,PropertyDefinition>> mapping a property name to a definition
     */
    public static Iterator<Map.Entry<Object,PropertyDefinition>> getPropertyDefinitionEntryIterator() {
        return SUPPORTED_DOCUMENT_PROPERTIES.entrySet().iterator();
    }

    public  static Object parseProperty(String propertyName, String propertyValue) {
        assert propertyName != null && propertyValue != null;

        final PropertyDefinition propertyDefinition = getPropertyDefinition(propertyName);
        return (propertyDefinition == null) ? null : propertyDefinition.parseProperty(propertyValue);
    }

    public static String getXFormsPassword() {
        return Properties.instance().getPropertySet().getString(PASSWORD_PROPERTY);
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

    public static int getApplicationStateStoreSize() {
        return Properties.instance().getPropertySet().getInteger
                (STORE_APPLICATION_SIZE_PROPERTY, STORE_APPLICATION_SIZE_DEFAULT);
    }

    public static boolean isGZIPState() {
        return Properties.instance().getPropertySet().getBoolean
                (GZIP_STATE_PROPERTY, GZIP_STATE_DEFAULT);
    }

    public static String getStoreUsername() {
        return Properties.instance().getPropertySet().getString
                (STORE_APPLICATION_USERNAME_PROPERTY, STORE_APPLICATION_USERNAME_DEFAULT);
    }

    public static String getStorePassword() {
        return Properties.instance().getPropertySet().getString
                (STORE_APPLICATION_PASSWORD_PROPERTY, STORE_APPLICATION_PASSWORD_DEFAULT);
    }

    public static String getStoreURI() {
        return Properties.instance().getPropertySet().getStringOrURIAsString
                (STORE_APPLICATION_URI_PROPERTY, STORE_APPLICATION_URI_DEFAULT);
    }

    public static String getStoreCollection() {
        return Properties.instance().getPropertySet().getString
                (STORE_APPLICATION_COLLECTION_PROPERTY, STORE_APPLICATION_COLLECTION_DEFAULT);
    }

    public static boolean isHostLanguageAVTs() {
        return Properties.instance().getPropertySet().getBoolean
                (HOST_LANGUAGE_AVTS_PROPERTY, HOST_LANGUAGE_AVTS_DEFAULT);
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

    public static PropertySet.Property getResourcesBaseline() {
        return Properties.instance().getPropertySet().getProperty(BASELINE_PROPERTY);
    }

    public static boolean getDebugLogXPathAnalysis() {
        return Properties.instance().getPropertySet().getBoolean(DEBUG_LOG_XPATH_ANALYSIS, false);
    }

    // == XForms document properties ===================================================================================

    public static String getStateHandling(XFormsContainingDocument containingDocument) {
        return getStringProperty(containingDocument, STATE_HANDLING_PROPERTY);
    }

    public static boolean isClientStateHandling(XFormsContainingDocument containingDocument) {
        return getStateHandling(containingDocument).equals(STATE_HANDLING_CLIENT_VALUE);
    }

    public static boolean isAjaxPortlet(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, AJAX_PORTLET_PROPERTY);
    }

    public static boolean isOptimizeGetAllSubmission(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, OPTIMIZE_GET_ALL_PROPERTY);
    }

    public static boolean isLocalSubmissionForward(XFormsContainingDocument containingDocument) {
        // Try new property first
        final boolean newPropertyValue = getBooleanProperty(containingDocument, LOCAL_SUBMISSION_FORWARD_PROPERTY);
        if (!newPropertyValue)
            return newPropertyValue;

        // Then old property
        return getBooleanProperty(containingDocument, OPTIMIZE_LOCAL_SUBMISSION_REPLACE_ALL_PROPERTY);
    }

    public static boolean isLocalSubmissionInclude(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, LOCAL_SUBMISSION_INCLUDE_PROPERTY);
    }

    public static boolean isLocalInstanceInclude(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, LOCAL_INSTANCE_INCLUDE_PROPERTY);
    }

    public static boolean isAjaxShowLoadingIcon(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, AJAX_SHOW_LOADING_ICON_PROPERTY);
    }

    public static boolean isAjaxShowErrors(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, AJAX_SHOW_ERRORS_PROPERTY);
    }

    public static boolean isSpanHTMLLayout(XFormsContainingDocument containingDocument) {
        // Check both properties for backward compatibility
        final String value = getStringProperty(containingDocument, XHTML_LAYOUT);
        return XHTMLLayout.SPAN.toString().toLowerCase().equals(value) || getBooleanProperty(containingDocument, NEW_XHTML_LAYOUT);
    }

    public static boolean isReadonly(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, READONLY_PROPERTY);
    }

    public static String getReadonlyAppearance(XFormsContainingDocument containingDocument) {
        return getStringProperty(containingDocument, READONLY_APPEARANCE_PROPERTY);
    }

    public static String getOrder(XFormsContainingDocument containingDocument) {
        return getStringProperty(containingDocument, ORDER_PROPERTY);
    }

    public static String getLabelElementName(XFormsContainingDocument containingDocument) {
        return getStringProperty(containingDocument, LABEL_ELEMENT_NAME_PROPERTY);
    }

    public static String getHintElementName(XFormsContainingDocument containingDocument) {
        return getStringProperty(containingDocument, HINT_ELEMENT_NAME_PROPERTY);
    }

    public static String getHelpElementName(XFormsContainingDocument containingDocument) {
        return getStringProperty(containingDocument, HELP_ELEMENT_NAME_PROPERTY);
    }

    public static String getAlertElementName(XFormsContainingDocument containingDocument) {
        return getStringProperty(containingDocument, ALERT_ELEMENT_NAME_PROPERTY);
    }

    public static boolean isStaticReadonlyAppearance(XFormsContainingDocument containingDocument) {
        return getReadonlyAppearance(containingDocument).equals(XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE);
    }

    public static String getTypeOutputFormat(XFormsContainingDocument containingDocument, String typeName) {
        return getStringProperty(containingDocument, TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX + typeName);
    }

    public static boolean isExposeXPathTypes(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, EXPOSE_XPATH_TYPES_PROPERTY);
    }

    /**
     * Get a format string given the given type and language.
     *
     * @param containingDocument    containing document
     * @param typeName              type name, e.g. "date", "dateTime", etc.
     * @param lang                  language, null, "en", "fr-CH"
     * @return                      format string, null if not found
     */
    public static String getTypeOutputFormat(XFormsContainingDocument containingDocument, String typeName, String lang) {
        final StringBuilder sb = new StringBuilder(TYPE_OUTPUT_FORMAT_PROPERTY_PREFIX);

        if (lang == null) {
            sb.append("*.*.");
        } else {
            final String[] langElements = StringUtils.split(lang, '-');
            // Support e.g. "en" or "fr-CH"
            for (int i = 0; i < 2; i++) {
                if (i < langElements.length) {
                    sb.append(langElements[i]);
                    sb.append('.');
                } else {
                    sb.append("*.");
                }
            }
        }

        sb.append(typeName);

        return getStringProperty(containingDocument, sb.toString());
    }

    public static String getTypeInputFormat(XFormsContainingDocument containingDocument, String typeName) {
        return getStringProperty(containingDocument, TYPE_INPUT_FORMAT_PROPERTY_PREFIX + typeName);
    }

    public static boolean isSessionHeartbeat(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, SESSION_HEARTBEAT_PROPERTY);
    }

    public static String getForwardSubmissionHeaders(XFormsContainingDocument containingDocument) {
        // Get from XForms property first, otherwise use global default
        final String result = getStringProperty(containingDocument, FORWARD_SUBMISSION_HEADERS);
        if (StringUtils.isNotBlank(result))
            return result;
        else
            return Connection.getForwardHeaders();
    }

    public static int getSubmissionPollDelay(XFormsContainingDocument containingDocument) {
        return getIntegerProperty(containingDocument, ASYNC_SUBMISSION_POLL_DELAY);
    }

    public static int getAjaxFullUpdateThreshold(XFormsContainingDocument containingDocument) {
        return getIntegerProperty(containingDocument, AJAX_UPDATE_FULL_THRESHOLD);
    }

    public static boolean isXForms11Switch(XFormsContainingDocument containingDocument) {
        return getBooleanProperty(containingDocument, XFORMS11_SWITCH_PROPERTY);
    }

    // == Helpers ======================================================================================================

    public static Object getProperty(XFormsContainingDocument containingDocument, String propertyName) {
        return containingDocument.getStaticState().getProperty(propertyName);
    }

    private static boolean getBooleanProperty(XFormsContainingDocument containingDocument, String propertyName) {
        return containingDocument.getStaticState().getBooleanProperty(propertyName);
    }

    private static String getStringProperty(XFormsContainingDocument containingDocument, String propertyName) {
        return containingDocument.getStaticState().getStringProperty(propertyName);
    }

    private static int getIntegerProperty(XFormsContainingDocument containingDocument, String propertyName) {
        return containingDocument.getStaticState().getIntegerProperty(propertyName);
    }

}
