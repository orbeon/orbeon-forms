/**
 *  Copyright (C) 2004 Orbeon, Inc.
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

import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represent an xforms:bind element.
 */
public class ModelBind {

    private String id;
    private String nodeset;
    private String relevant;
    private String calculate;
    private String type;
    private String constraint;
    private String required;
    private String readonly;
    private String xxformsExternalize;

    private Map namespaceMap;
    private LocationData locationData;
    private ModelBind parent;
    private List children = new ArrayList();
    private NodeInfo currentNodeInfo;

    public ModelBind(Element bindElement, ModelBind parent) {
        this(bindElement.attributeValue("id"), bindElement.attributeValue("nodeset"),
                    bindElement.attributeValue("relevant"), bindElement.attributeValue("calculate"), bindElement.attributeValue("type"),
                    bindElement.attributeValue("constraint"), bindElement.attributeValue("required"), bindElement.attributeValue("readonly"),
                    bindElement.attributeValue(XFormsConstants.XXFORMS_EXTERNALIZE_QNAME),
                    Dom4jUtils.getNamespaceContextNoDefault(bindElement),
                    new ExtendedLocationData((LocationData) bindElement.getData(), "xforms:bind element", bindElement), parent);
    }

    private ModelBind(String id, String nodeset, String relevant, String calculate, String type, String constraint,
                     String required, String readonly, String xxformsExternalize,
                     Map namespaceMap, LocationData locationData, ModelBind parent) {

        if (nodeset == null)
            throw new ValidationException("Bind element is missing nodeset attribute", locationData);

        this.id = id;
        this.nodeset = nodeset;
        this.relevant = relevant;
        this.calculate = calculate;
        this.type = type;
        this.constraint = constraint;
        this.required = required;
        this.readonly = readonly;

        this.xxformsExternalize = xxformsExternalize;

        this.namespaceMap = namespaceMap;
        this.locationData = locationData;

        this.parent = parent;
    }

    public String getId() {
        return id;
    }

    public String getNodeset() {
        return nodeset;
    }

    public String getRelevant() {
        return relevant;
    }

    public String getCalculate() {
        return calculate;
    }

    public String getType() {
        return type;
    }

    public String getConstraint() {
        return constraint;
    }

    public String getRequired() {
        return required;
    }

    public String getReadonly() {
        return readonly;
    }

    public String getXXFormsExternalize() {
        return xxformsExternalize;
    }

    public Map getNamespaceMap() {
        return namespaceMap;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public void addChild(ModelBind bind) {
        children.add(bind);
    }

    public List getChildren() {
        return children;
    }

    public NodeInfo getCurrentNodeInfo() {
        return currentNodeInfo;
    }

    public void setCurrentNodeInfo(NodeInfo currentNodeInfo) {
        this.currentNodeInfo = currentNodeInfo;
    }

    public ModelBind getParent() {
        return parent;
    }

    public void setParent(ModelBind parent) {
        this.parent = parent;
    }

    public String toString() {
        return "ModelBind: id=" + getId() + " nodeset=" + getNodeset();
    }
}
