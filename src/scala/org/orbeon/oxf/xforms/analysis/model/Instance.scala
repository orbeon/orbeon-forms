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
package org.orbeon.oxf.xforms.analysis.model


import org.orbeon.oxf.common.Version
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.dom4j.LocationData
import org.dom4j.Element
import xbl.XBLBindings


/**
 * Static analysis of an XForms instance.
 */
class Instance(val element: Element, val scope: XBLBindings#Scope) {

    val locationData = element.getData.asInstanceOf[LocationData]

    val staticId = XFormsUtils.getElementStaticId(element)
    val prefixedId = scope.getFullPrefix + staticId;;

    val isReadonlyHint = XFormsInstance.isReadonlyHint(element)
    val isCacheHint = Version.instance.isPEFeatureEnabled(XFormsInstance.isCacheHint(element), "cached XForms instance")
    val xxformsTimeToLive = XFormsInstance.getTimeToLive(element)
    val xxformsValidation = element.attributeValue(XFormsConstants.XXFORMS_VALIDATION_QNAME)

    // Extension: username, password and domain
    // NOTE: AVTs not supported because XPath expressions in those could access instances that haven't been loaded
    val xxformsUsername = element.attributeValue(XFormsConstants.XXFORMS_USERNAME_QNAME)
    val xxformsPassword = element.attributeValue(XFormsConstants.XXFORMS_PASSWORD_QNAME)
    val xxformsDomain = element.attributeValue(XFormsConstants.XXFORMS_DOMAIN_QNAME)

    // Allow "", which will cause an xforms-link-exception at runtime
    // NOTE: It could make sense to throw here
    val src = {
        val srcAttribute = element.attributeValue(XFormsConstants.SRC_QNAME)
        if (srcAttribute eq null) null else NetUtils.encodeHRRI(srcAttribute.trim, true)
    }

    // Allow "", which will cause an xforms-link-exception at runtime
    // NOTE: It could make sense to throw here
    val resource = {
        val resourceAttribute = element.attributeValue("resource")
        if (resourceAttribute eq null) null else NetUtils.encodeHRRI(resourceAttribute.trim, true)
    }

    val (instanceSource, dependencyURL) = {
        val unresolvedInstanceSource =
            if (src != null)// @src is checked first
                src
            else if (Dom4jUtils.elements(element).size > 0)// TODO: static error here if more than 1 child element
                null
            else if (resource != null)// @resource is checked if there are no nested elements
                resource
            else
                null

        if (unresolvedInstanceSource != null && (ProcessorImpl.getProcessorInputSchemeInputName(unresolvedInstanceSource) eq null))
            (unresolvedInstanceSource, unresolvedInstanceSource)
        else if (unresolvedInstanceSource != null)// input:* doesn't add a URL dependency, but is handled by the pipeline engine
            (unresolvedInstanceSource, null)
        else
            (null, null)
    }
}
