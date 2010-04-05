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

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.*;

/**
 * Static analysis of an XForms model.
 */
public class Model {
    public final XBLBindings.Scope scope;
    public final Document document;

    public final List<Element> instanceElements;
    public final Set<String> instanceStaticIds;

    public final List<Element> bindElements;
    public final Set<String> bindIds;

    public final String staticId;
    public final String prefixedId;
    public final String defaultInstanceStaticId;
    public final String defaultInstancePrefixedId;

    public Model(XBLBindings.Scope scope, Document document) {

        assert scope != null;
        assert document != null;

        this.scope = scope;
        this.document = document;

        instanceElements = Dom4jUtils.elements(document.getRootElement(), XFormsConstants.XFORMS_INSTANCE_QNAME);
        instanceStaticIds = new LinkedHashSet<String>(instanceElements.size());
        for (final Element instanceElement: instanceElements) {
            instanceStaticIds.add(XFormsUtils.getElementStaticId(instanceElement));
        }

        bindElements = Dom4jUtils.elements(document.getRootElement(), XFormsConstants.XFORMS_BIND_QNAME);
        if (hasBinds()) {
            bindIds = new HashSet<String>();
            findBindIds(bindElements);
        } else {
            bindIds = Collections.emptySet();
        }

        staticId = XFormsUtils.getElementStaticId(document.getRootElement());
        prefixedId = scope.getFullPrefix() + staticId;
        final boolean hasInstances = instanceStaticIds.size() > 0;
        defaultInstanceStaticId = hasInstances  ? instanceStaticIds.iterator().next() : null;
        defaultInstancePrefixedId = hasInstances ? scope.getFullPrefix() + defaultInstanceStaticId : null;
    }

    private void findBindIds(List<Element> bindElements) {
        for (final Element element: bindElements) {
            // Add id of this element
            bindIds.add(XFormsUtils.getElementStaticId(element));
            // Recurse to find nested bind elements
            findBindIds(Dom4jUtils.elements(element, XFormsConstants.XFORMS_BIND_QNAME));
        }
    }

    public boolean hasBinds() {
        return bindElements != null && bindElements.size() > 0;
    }

    public boolean containsBind(String bindId) {
        return bindIds.contains(bindId);
    }
}
