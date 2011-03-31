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
package org.orbeon.oxf.xforms.submission;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.portlet.OrbeonPortletXFormsFilter;
import org.orbeon.oxf.servlet.OrbeonXFormsFilter;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.XFormsConstants;

import java.util.Map;

public class FilterPortletSubmission extends BaseSubmission {

    private static final String SKIPPING_SUBMISSION_DEBUG_MESSAGE = "skipping filter portlet submission";

    public FilterPortletSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public String getType() {
        return "filter portlet";
    }

    /**
     * Check whether submission is allowed.
     */
    public boolean isMatch(XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        final ExternalContext.Request request = NetUtils.getExternalContext().getRequest();
        final IndentedLogger indentedLogger = getDetailsLogger(p, p2);

        // Log a lot of stuff for development, as it is not always obvious why we pick this type of submission.

        final boolean isDebugEnabled = indentedLogger.isDebugEnabled();
        if (isDebugEnabled) {
            indentedLogger.logDebug("", "checking whether " + getType() + " submission is allowed",
                "resource", p2.actionOrResource,
                "replace", submission.getReplace(),
                "container type", request.getContainerType(),
                "deployment type", containingDocument.getDeploymentType().name(),
                "deployment source", (String) request.getAttributesMap().get(OrbeonXFormsFilter.RENDERER_DEPLOYMENT_SOURCE_ATTRIBUTE_NAME)
            );
        }

        // Absolute URL implies a regular submission
        if (NetUtils.urlHasProtocol(p2.actionOrResource)) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "resource URL has protocol", "resource", p2.actionOrResource);
            return false;
        }

        // Only enable this for replace="all"
        if (!p.isReplaceAll) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "only replace=\"all\" is supported");
            return false;
        }

        // Only for separate deployment from portlets
        if (containingDocument.getDeploymentType() != XFormsConstants.DeploymentType.separate
                || !"portlet".equals(request.getAttributesMap().get(OrbeonXFormsFilter.RENDERER_DEPLOYMENT_SOURCE_ATTRIBUTE_NAME))) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "deployment type is not portlet separate deployment");
            return false;
        }

        if (isDebugEnabled)
            indentedLogger.logDebug("", "enabling " + getType() + " submission");

        return true;
    }

    public SubmissionResult connect(final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {

        // URI with xml:base resolution
//        final URI resolvedURI = XFormsUtils.resolveXMLBase(containingDocument, submission.getSubmissionElement(), p2.actionOrResource);

        // Store stuff useful for portlet filter
        final Map<String, Object> attributes = NetUtils.getExternalContext().getRequest().getAttributesMap();
        attributes.put(OrbeonPortletXFormsFilter.PORTLET_SUBMISSION_METHOD_ATTRIBUTE, p.actualHttpMethod);
        attributes.put(OrbeonPortletXFormsFilter.PORTLET_SUBMISSION_BODY_ATTRIBUTE, sp.messageBody);
        attributes.put(OrbeonPortletXFormsFilter.PORTLET_SUBMISSION_PATH_ATTRIBUTE, p2.actionOrResource
                + ((p2.actionOrResource.indexOf('?') == -1) ? '?' : '&' ) + sp.queryString);
        attributes.put(OrbeonPortletXFormsFilter.PORTLET_SUBMISSION_MEDIATYPE_ATTRIBUTE, sp.actualRequestMediatype);


        // Nothing to do
        return null;
    }
}