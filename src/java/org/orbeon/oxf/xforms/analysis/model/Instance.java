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
package org.orbeon.oxf.xforms.analysis.model;

import org.dom4j.Element;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

/**
 * Static analysis of an XForms instance.
 */
public class Instance {

    public final Element element;
    public final LocationData locationData;

    public final String staticId;
    public final String prefixedId;
    public final boolean isReadonlyHint;
    public final boolean isCacheHint;
    public final long xxformsTimeToLive;
    public final String xxformsValidation;

    // Extension: username, password and domain
    // NOTE: AVTs not supported because XPath expressions in those could access instances that haven't been loaded
    public final String xxformsUsername;
    public final String xxformsPassword;
    public final String xxformsDomain;

    public final String src;
    public final String resource;

    public final String instanceSource;
    public final String dependencyURL;


    public Instance(Element element, XBLBindings.Scope scope) {

        this.element = element;
        this.staticId = XFormsUtils.getElementStaticId(element);
        this.prefixedId = scope.getFullPrefix() + staticId;

        locationData = (LocationData) element.getData();
        isReadonlyHint = XFormsInstance.isReadonlyHint(element);
        isCacheHint = Version.instance().isPEFeatureEnabled(XFormsInstance.isCacheHint(element), "cached XForms instance");
        xxformsTimeToLive = XFormsInstance.getTimeToLive(element);
        xxformsValidation = element.attributeValue(XFormsConstants.XXFORMS_VALIDATION_QNAME);

        xxformsUsername = element.attributeValue(XFormsConstants.XXFORMS_USERNAME_QNAME);
        xxformsPassword = element.attributeValue(XFormsConstants.XXFORMS_PASSWORD_QNAME);
        xxformsDomain = element.attributeValue(XFormsConstants.XXFORMS_DOMAIN_QNAME);

        // Allow "", which will cause an xforms-link-exception at runtime
        // NOTE: It could make sense to throw here
        final String srcAttribute = element.attributeValue("src");
        src = (srcAttribute == null) ? null : NetUtils.encodeHRRI(srcAttribute.trim(), true);

        // Allow "", which will cause an xforms-link-exception at runtime
        // NOTE: It could make sense to throw here
        final String resourceAttribute = element.attributeValue("resource");
        resource = (resourceAttribute == null) ? null : NetUtils.encodeHRRI(resourceAttribute.trim(), true);

        final String unresolvedInstanceSource;
        if (src != null) {
            // @src is checked first
            unresolvedInstanceSource = src;
        } else if (Dom4jUtils.elements(element).size() > 0) {
            // TODO: static error here if more than 1 child element
            unresolvedInstanceSource = null;
        } else {
            // @resource is checked if there are no nested elements
            if (resource != null) {
                unresolvedInstanceSource = resource;
            } else {
                unresolvedInstanceSource = null;
            }
        }

        if (unresolvedInstanceSource != null && ProcessorImpl.getProcessorInputSchemeInputName(unresolvedInstanceSource) == null) {
            // Resolve to absolute URL
            instanceSource = dependencyURL = unresolvedInstanceSource;
        } else if (unresolvedInstanceSource != null) {
            // input:*
            instanceSource = unresolvedInstanceSource;
            dependencyURL = null; // this doesn't add an URL dependency, but is handled by the pipeline engine
        } else {
            instanceSource = null;
            dependencyURL = null;
        }
    }
}
