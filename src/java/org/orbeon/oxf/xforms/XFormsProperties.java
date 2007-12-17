/**
 *  Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.oxf.resources.OXFProperties;

public class XFormsProperties {

    private static final String ENCRYPT_NAMES_PROPERTY = "oxf.xforms.encrypt-names";
    private static final String ENCRYPT_HIDDEN_PROPERTY = "oxf.xforms.encrypt-hidden";

    private static final String CACHE_DOCUMENT_PROPERTY = "oxf.xforms.cache.document";

    private static final String OPTIMIZE_GET_ALL_PROPERTY = "oxf.xforms.optimize-get-all";
    private static final String OPTIMIZE_LOCAL_SUBMISSION_PROPERTY = "oxf.xforms.optimize-local-submission";

    private static final String OPTIMIZE_RELEVANCE_PROPERTY = "oxf.xforms.optimize-relevance";
    private static final boolean OPTIMIZE_RELEVANCE_DEFAULT = false;

    private static final String EXCEPTION_INVALID_CLIENT_CONTROL_PROPERTY = "oxf.xforms.exception-invalid-client-control";
    private static final String AJAX_SHOW_LOADING_ICON_PROPERTY = "oxf.xforms.ajax.show-loading-icon";
    private static final String AJAX_SHOW_ERRORS_PROPERTY = "oxf.xforms.ajax.show-errors";

    private static final String HOST_LANGUAGE_AVTS_PROPERTY = "oxf.xforms.host-language-avts";
    private static final boolean HOST_LANGUAGE_AVTS_DEFAULT = false;

    private static final String MINIMAL_RESOURCES_PROPERTY = "oxf.xforms.minimal-resources";
    private static final boolean MINIMAL_RESOURCES_DEFAULT = false;
    private static final String COMBINE_RESOURCES_PROPERTY = "oxf.xforms.combine-resources";
    private static final boolean COMBINE_RESOURCES_DEFAULT = false;
    private static final String CACHE_COMBINED_RESOURCES_PROPERTY = "oxf.xforms.cache-combined-resources";
    private static final boolean CACHE_COMBINED_RESOURCES_DEFAULT = false;

    public static final String PASSWORD_PROPERTY = "oxf.xforms.password";

    public static final String DATE_FORMAT_PROPERTY_DEFAULT = "oxf.xforms.format.date";
    public static final String DATETIME_FORMAT_PROPERTY_DEFAULT = "oxf.xforms.format.dateTime";
    public static final String TIME_FORMAT_PROPERTY_DEFAULT = "oxf.xforms.format.time";
    public static final String DECIMAL_FORMAT_PROPERTY_DEFAULT = "oxf.xforms.format.decimal";
    public static final String INTEGER_FORMAT_PROPERTY_DEFAULT = "oxf.xforms.format.integer";
    public static final String FLOAT_FORMAT_PROPERTY_DEFAULT = "oxf.xforms.format.float";
    public static final String DOUBLE_FORMAT_PROPERTY_DEFAULT = "oxf.xforms.format.double";

    private static final String GZIP_STATE_PROPERTY = "oxf.xforms.gzip-state";
    private static final boolean GZIP_STATE_DEFAULT = true;

    private static final String STATE_HANDLING_PROPERTY = "oxf.xforms.state-handling";
    public static final String STATE_HANDLING_CLIENT_VALUE = "client";
    public static final String STATE_HANDLING_SESSION_VALUE = "session"; // deprecated
    public static final String STATE_HANDLING_SERVER_VALUE = "server";

    private static final String STORE_APPLICATION_SIZE_PROPERTY = "oxf.xforms.store.application.size";
    private static final int STORE_APPLICATION_SIZE_DEFAULT = 20 * 1024 * 1024;

    private static final String STORE_APPLICATION_USERNAME_PROPERTY = "oxf.xforms.store.application.username";
    private static final String STORE_APPLICATION_PASSWORD_PROPERTY = "oxf.xforms.store.application.password";
    private static final String STORE_APPLICATION_URI_PROPERTY = "oxf.xforms.store.application.uri";
    private static final String STORE_APPLICATION_COLLECTION_PROPERTY = "oxf.xforms.store.application.collection";

    private static final String STORE_APPLICATION_USERNAME_DEFAULT = "guest";
    private static final String STORE_APPLICATION_PASSWORD_DEFAULT = "";
    private static final String STORE_APPLICATION_URI_DEFAULT = "xmldb:exist:///";
    private static final String STORE_APPLICATION_COLLECTION_DEFAULT = "/db/orbeon/xforms/cache/";

    // The following are deprecated in favor of the persistent application store
    private static final String CACHE_SESSION_SIZE_PROPERTY = "oxf.xforms.cache.session.size";
    private static final int CACHE_SESSION_SIZE_DEFAULT = 1024 * 1024;
    private static final String CACHE_APPLICATION_SIZE_PROPERTY = "oxf.xforms.cache.application.size";
    private static final int CACHE_APPLICATION_SIZE_DEFAULT = 1024 * 1024;

    // TODO: This should probably be deprecated
    public static final String VALIDATION_PROPERTY = "oxf.xforms.validate";
//    public static final String XFORMS_OPTIMIZE_LOCAL_INSTANCE_LOADS_PROPERTY = "oxf.xforms.optimize-local-instance-loads";

    private static final String TEST_AJAX_PROPERTY = "oxf.xforms.test.ajax";
    private static final boolean TEST_AJAX_DEFAULT = false;

    /**
     * @return  whether name encryption is enabled (legacy XForms engine only).
     */
    public static boolean isNameEncryptionEnabled() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (ENCRYPT_NAMES_PROPERTY, false).booleanValue();
    }

    /**
     * @return  whether hidden fields encryption is enabled.
     */
    public static boolean isHiddenEncryptionEnabled() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (ENCRYPT_HIDDEN_PROPERTY, false).booleanValue();
    }

    public static boolean isCacheDocument() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (CACHE_DOCUMENT_PROPERTY, false).booleanValue();
    }

    public static boolean isOptimizeGetAllSubmission() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (OPTIMIZE_GET_ALL_PROPERTY, true).booleanValue();
    }

    public static boolean isOptimizeLocalSubmission() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (OPTIMIZE_LOCAL_SUBMISSION_PROPERTY, true).booleanValue();
    }

    public static boolean isServerStateHandling() {
        final String propertyValue = OXFProperties.instance().getPropertySet().getString
                (STATE_HANDLING_PROPERTY, STATE_HANDLING_CLIENT_VALUE);

        return propertyValue.equals(STATE_HANDLING_SESSION_VALUE)
                || propertyValue.equals(STATE_HANDLING_SERVER_VALUE);
    }

    public static boolean isExceptionOnInvalidClientControlId() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (EXCEPTION_INVALID_CLIENT_CONTROL_PROPERTY, false).booleanValue();
    }

    public static boolean isAjaxShowLoadingIcon() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (AJAX_SHOW_LOADING_ICON_PROPERTY, false).booleanValue();
    }

    public static boolean isAjaxShowErrors() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (AJAX_SHOW_ERRORS_PROPERTY, true).booleanValue();
    }

    public static int getSessionStoreSize() {
        return OXFProperties.instance().getPropertySet().getInteger
                (CACHE_SESSION_SIZE_PROPERTY, CACHE_SESSION_SIZE_DEFAULT).intValue();
    }

    public static int getApplicationStateStoreSize() {
        return OXFProperties.instance().getPropertySet().getInteger
                (STORE_APPLICATION_SIZE_PROPERTY, STORE_APPLICATION_SIZE_DEFAULT).intValue();
    }

    public static int getApplicationCacheSize() {
        return OXFProperties.instance().getPropertySet().getInteger
                (CACHE_APPLICATION_SIZE_PROPERTY, CACHE_APPLICATION_SIZE_DEFAULT).intValue();
    }

    public static boolean isGZIPState() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (GZIP_STATE_PROPERTY, GZIP_STATE_DEFAULT).booleanValue();
    }

    public static boolean isHostLanguageAVTs() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (HOST_LANGUAGE_AVTS_PROPERTY, HOST_LANGUAGE_AVTS_DEFAULT).booleanValue();
    }

    public static boolean isMinimalResources() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (MINIMAL_RESOURCES_PROPERTY, MINIMAL_RESOURCES_DEFAULT).booleanValue();
    }

    public static boolean isCombineResources() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (COMBINE_RESOURCES_PROPERTY, COMBINE_RESOURCES_DEFAULT).booleanValue();
    }

    public static boolean isCacheCombinedResources() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (CACHE_COMBINED_RESOURCES_PROPERTY, CACHE_COMBINED_RESOURCES_DEFAULT).booleanValue();
    }

    public static boolean isOptimizeRelevance() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (OPTIMIZE_RELEVANCE_PROPERTY, OPTIMIZE_RELEVANCE_DEFAULT).booleanValue();
    }

    public static boolean isAjaxTest() {
        return OXFProperties.instance().getPropertySet().getBoolean
                (TEST_AJAX_PROPERTY, TEST_AJAX_DEFAULT).booleanValue();
    }

    // This is not used currently
//    public static boolean isOptimizeLocalInstanceLoads() {
//        return OXFProperties.instance().getPropertySet().getBoolean
//                (XFormsConstants.XFORMS_OPTIMIZE_LOCAL_INSTANCE_LOADS_PROPERTY, true).booleanValue();
//    }

    public static String getXFormsPassword() {
        if (isHiddenEncryptionEnabled())
            return OXFProperties.instance().getPropertySet().getString(PASSWORD_PROPERTY);
        else
            return null;
    }

    public static String getStoreUsername() {
        return OXFProperties.instance().getPropertySet().getString
                (STORE_APPLICATION_USERNAME_PROPERTY, STORE_APPLICATION_USERNAME_DEFAULT);
    }

    public static String getStorePassword() {
        return OXFProperties.instance().getPropertySet().getString
                (STORE_APPLICATION_PASSWORD_PROPERTY, STORE_APPLICATION_PASSWORD_DEFAULT);
    }

    public static String getStoreURI() {
        return OXFProperties.instance().getPropertySet().getStringOrURIAsString
                (STORE_APPLICATION_URI_PROPERTY, STORE_APPLICATION_URI_DEFAULT);
    }

    public static String getStoreCollection() {
        return OXFProperties.instance().getPropertySet().getString
                (STORE_APPLICATION_COLLECTION_PROPERTY, STORE_APPLICATION_COLLECTION_DEFAULT);
    }
}
