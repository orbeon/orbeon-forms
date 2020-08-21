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
package org.orbeon.oxf.xforms.model;

import org.orbeon.dom.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.*;

import java.util.*;

/**
 * Instances of this class are used to annotate XForms instance nodes with MIPs and other information.
 *
 * Previously, all element and attribute nodes in every XForms instance were annotated with instances of this class.
 * Annotations are now done lazily when needed in order to reduce the number of objects created. This has a positive
 * impact on memory usage and garbage collection. This is also why most methods in this class are static.
 *
 * Since 2010-12, this now points back to bind nodes, which store bind MIPs directly.
 */
public class InstanceData {// rename to DataNodeProperties once done

    private LocationData locationData;

    // Point back to binds that impacted this node
    private List<BindNode> bindNodes;

    // Types set by schema or binds
    private QName bindType;
    private QName schemaType;

    // Schema validity: only set by schema
    private boolean schemaInvalid;

    // Whether to evaluate the default value
    private boolean requireDefaultValue;

    // Annotations (used only for multipart submission as of 2010-12)
    private Map<String, String> transientAnnotations;

    public List<BindNode> getBindNodes() {
        if (bindNodes == null)
            return Collections.emptyList();
        else
            return bindNodes;
    }

    public static void addBindNode(NodeInfo nodeInfo, BindNode bindNode) {
        final InstanceData instanceData = getOrCreateInstanceData(nodeInfo, false);
        if (instanceData != READONLY_LOCAL_INSTANCE_DATA) {
            // only register ourselves if we are not a readonly node
            if (instanceData.bindNodes == null)
                instanceData.bindNodes = Collections.singletonList(bindNode);
            else if (instanceData.bindNodes.size() == 1) {
                final BindNode oldBindNode = instanceData.bindNodes.get(0);
                instanceData.bindNodes = new ArrayList<BindNode>(4); // hoping that situations where many binds point to same node are rare
                instanceData.bindNodes.add(oldBindNode);
                instanceData.bindNodes.add(bindNode);
            } else {
                instanceData.bindNodes.add(bindNode);
            }
        }
    }

    private static final InstanceData READONLY_LOCAL_INSTANCE_DATA = new InstanceData() {

        @Override
        public boolean getRequired() {
            return Model.DEFAULT_REQUIRED();
        }

        @Override
        public boolean getValid() {
            return Model.DEFAULT_VALID();
        }

        @Override
        public QName getSchemaOrBindType() {
            return null;
        }

        @Override
        public String getInvalidBindIds() {
            return null;
        }

        @Override
        public LocationData getLocationData() {
            return null;
        }
    };

    private boolean getLocalRelevant() {
        if (bindNodes != null && bindNodes.size() > 0)
            for (final BindNode bindNode : bindNodes)
                if (bindNode.relevant() != Model.DEFAULT_RELEVANT())
                    return !Model.DEFAULT_RELEVANT();

        return Model.DEFAULT_RELEVANT();
    }

    private boolean getLocalReadonly() {
        if (bindNodes != null && bindNodes.size() > 0)
            for (final BindNode bindNode : bindNodes)
                if (bindNode.readonly() != Model.DEFAULT_READONLY())
                    return !Model.DEFAULT_READONLY();

        return Model.DEFAULT_READONLY();
    }

    public boolean getRequired() {
        if (bindNodes != null && bindNodes.size() > 0)
            for (final BindNode bindNode : bindNodes)
                if (bindNode.required() != Model.DEFAULT_REQUIRED())
                    return !Model.DEFAULT_REQUIRED();

        return Model.DEFAULT_REQUIRED();
    }

    public boolean getValid() {

        if (schemaInvalid)
            return false;

        if (bindNodes != null && bindNodes.size() > 0)
            for (final BindNode bindNode : bindNodes)
                if (bindNode.valid() != Model.DEFAULT_VALID())
                    return !Model.DEFAULT_VALID();

        return Model.DEFAULT_VALID();
    }

    private scala.collection.immutable.Map<String, String> collectAllClientCustomMIPs() {
        return BindNode.collectAllClientCustomMIPs(bindNodes);
    }

    private scala.Option<String> findCustomMip(String mipName) {
        return BindNode.findCustomMip(bindNodes, mipName);
    }

    public QName getSchemaOrBindType() {

        if (schemaType != null)
            return schemaType;

        return bindType;
    }

    public String getInvalidBindIds() {
        StringBuilder sb = null;
        if (bindNodes != null && bindNodes.size() > 0)
            for (final BindNode bindNode : bindNodes)
                if (bindNode.valid() != Model.DEFAULT_VALID()) {
                    if (sb == null)
                        sb = new StringBuilder();
                    else if (sb.length() > 0)
                        sb.append(' ');

                    sb.append(bindNode.parentBind().staticId());
                }
        return sb == null ? null : sb.toString();
    }

    private void setTransientAnnotation(String name, String value) {
        if (transientAnnotations == null)
            transientAnnotations = new HashMap<String, String>();
        transientAnnotations.put(name, value);
    }

    private String getTransientAnnotation(String name) {
        return (transientAnnotations == null) ? null : transientAnnotations.get(name);
    }

    public static void setTransientAnnotation(NodeInfo nodeInfo, String name, String value) {
        final InstanceData instanceData = getOrCreateInstanceData(nodeInfo, true);
        instanceData.setTransientAnnotation(name,value);
    }

    public static String getTransientAnnotation(Node node, String name) {
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        return (existingInstanceData == null) ? null : existingInstanceData.getTransientAnnotation(name);
    }

    public static scala.Option<scala.collection.immutable.Map<String, String>> collectAllClientCustomMIPs(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return scala.Option.apply((existingInstanceData == null) ? null : existingInstanceData.collectAllClientCustomMIPs());
    }

    public static scala.Option<String> findCustomMip(NodeInfo nodeInfo, String mipName) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null) ?scala.Option.<String>apply(null) : existingInstanceData.findCustomMip(mipName);
    }

    public static boolean getInheritedRelevant(NodeInfo nodeInfo) {
        if (nodeInfo instanceof VirtualNode) {
            return getInheritedRelevant(XFormsUtils.getNodeFromNodeInfo(nodeInfo, ""));
        } else if (nodeInfo != null) {
            return Model.DEFAULT_RELEVANT();
        } else {
            throw new OXFException("Cannot get relevant Model Item Property on null object.");
        }
    }

    public static boolean getInheritedRelevant(Node node) {
        // Iterate this node and its parents. The node is non-relevant if it or any ancestor is non-relevant.
        for (Node currentNode = node; currentNode != null; currentNode = currentNode.getParent()) {
            final InstanceData currentInstanceData = getLocalInstanceData(currentNode);
            final boolean currentRelevant = (currentInstanceData == null) ? Model.DEFAULT_RELEVANT() : currentInstanceData.getLocalRelevant();
            if (!currentRelevant)
                return false;
        }
        return true;
    }

    public static boolean getLocalRelevant(Node node) {
        final InstanceData currentInstanceData = getLocalInstanceData(node);
        return (currentInstanceData == null) ? Model.DEFAULT_RELEVANT() : currentInstanceData.getLocalRelevant();
    }

    public static boolean getRequired(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null) ? Model.DEFAULT_REQUIRED() : existingInstanceData.getRequired();
    }

    public static boolean getRequired(Node node) {
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        return (existingInstanceData == null) ? Model.DEFAULT_REQUIRED() : existingInstanceData.getRequired();
    }

    public static boolean getInheritedReadonly(NodeInfo nodeInfo) {
        if (nodeInfo instanceof VirtualNode) {
            return getInheritedReadonly(XFormsUtils.getNodeFromNodeInfo(nodeInfo, ""));
        } else if (nodeInfo != null) {
            return true;// Default for non-mutable nodes is to be read-only
        } else {
            throw new OXFException("Cannot get readonly Model Item Property on null object.");
        }
    }

    public static boolean getInheritedReadonly(Node node) {
        // Iterate this node and its parents. The node is readonly if it or any ancestor is readonly.
        for (Node currentNode = node; currentNode != null; currentNode = currentNode.getParent()) {
            final InstanceData currentInstanceData = getLocalInstanceData(currentNode);
            final boolean currentReadonly = (currentInstanceData == null) ? Model.DEFAULT_READONLY() : currentInstanceData.getLocalReadonly();
            if (currentReadonly)
                return true;
        }
        return false;
    }

    public static boolean getValid(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null) ? Model.DEFAULT_VALID() : existingInstanceData.getValid();
    }

    public static boolean getValid(Node node) {
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        return (existingInstanceData == null) ? Model.DEFAULT_VALID() : existingInstanceData.getValid();
    }

    public static void setBindType(NodeInfo nodeInfo, QName type) {
        getOrCreateInstanceData(nodeInfo, true).bindType = type;
    }

    public static void setSchemaType(Node node, QName type) {
        getOrCreateInstanceData(node).schemaType = type;
    }

    public static QName getType(NodeInfo nodeInfo) {

        // Try schema or bind type
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        if (existingInstanceData != null) {
            final QName schemaOrBindType = existingInstanceData.getSchemaOrBindType();
            if (schemaOrBindType != null)
                return schemaOrBindType;
        }

        // No type was assigned by schema or MIP, try xsi:type
        if (nodeInfo.getNodeKind() == org.w3c.dom.Node.ELEMENT_NODE) {
            // Check for xsi:type attribute
            // NOTE: Saxon 9 has new code to resolve such QNames
            final String typeQName = nodeInfo.getAttributeValue(StandardNames.XSI_TYPE);
            if (typeQName != null) {
                try {
                    final NameChecker checker = nodeInfo.getConfiguration().getNameChecker();
                    final String[] parts = checker.getQNameParts(typeQName);

                    // No prefix
                    if (parts[0].equals("")) {
                        return QName.apply(parts[1]);
                    }

                    // There is a prefix, resolve it
                    final SequenceIterator namespaceNodes = nodeInfo.iterateAxis(Axis.NAMESPACE);
                    while (true) {
                        final NodeInfo currentNamespaceNode = (NodeInfo) namespaceNodes.next();
                        if (currentNamespaceNode == null) {
                            break;
                        }
                        final String prefix = currentNamespaceNode.getLocalPart();
                        if (prefix.equals(parts[0])) {
                            return QName.apply(parts[1], "", currentNamespaceNode.getStringValue());
                        }
                    }
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        }

        return null;
    }

    public static QName getType(Node node) {

        // Try schema or bind type
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        if (existingInstanceData != null) {
            final QName schemaOrBindType = existingInstanceData.getSchemaOrBindType();
            if (schemaOrBindType != null)
                return schemaOrBindType;
        }

        // No type was assigned by schema or MIP, try xsi:type
        if (node instanceof Element) {
            // Check for xsi:type attribute
            final Element element = (Element) node;
            return Dom4jUtils.extractAttributeValueQName(element, XMLConstants.XSI_TYPE_QNAME(), false); // TODO: should pass true?
        }

        return null;
    }

    public static String getInvalidBindIds(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null) ? null : existingInstanceData.getInvalidBindIds();
    }

    public static void setRequireDefaultValue(Node node) {
        final InstanceData instanceData = getOrCreateInstanceData(node);
        instanceData.requireDefaultValue = true;
    }

    public static void clearRequireDefaultValue(Node node) {
        final InstanceData instanceData = getLocalInstanceData(node);
        if (instanceData != null)
            instanceData.requireDefaultValue = false;
    }

    public static boolean getRequireDefaultValue(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return existingInstanceData.requireDefaultValue;
    }

    public static void removeInstanceData(Node node) {
        if (node instanceof Element) {
             ((Element) node).setData(null);
        } else if (node instanceof Attribute) {
            ((Attribute) node).setData(null);
        }
    }

    public static void addSchemaError(Node node) {

        // Get or create InstanceData
        final InstanceData instanceData = getOrCreateInstanceData(node);

        // Remember that the value is invalid
        instanceData.schemaInvalid = true;
    }

    public static void clearStateForRebuild(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData =
            getLocalInstanceData(nodeInfo, false);// not really an update since for read-only nothing changes
        if (existingInstanceData != null) {
            existingInstanceData.bindNodes = null;
            existingInstanceData.bindType = null;
            existingInstanceData.schemaType = null;
            existingInstanceData.schemaInvalid = false;
            existingInstanceData.transientAnnotations = null;
            // NOTE: Do not clear requireInitialData which must survive a rebuild
        }
    }

    public static void clearSchemaState(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData =
            getLocalInstanceData(nodeInfo, false);// not really an update since for read-only nothing changes
        if (existingInstanceData != null) {
            existingInstanceData.schemaType = null;
            existingInstanceData.schemaInvalid = false;
        }
    }

    private static InstanceData getOrCreateInstanceData(NodeInfo nodeInfo, boolean forUpdate) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, forUpdate);
        return (existingInstanceData != null) ? existingInstanceData : createNewInstanceData(nodeInfo);
    }

    private static InstanceData getOrCreateInstanceData(Node node) {
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        return (existingInstanceData != null) ? existingInstanceData : createNewInstanceData(node);
    }

    public static InstanceData getLocalInstanceData(NodeInfo nodeInfo, boolean forUpdate) {
        if (nodeInfo instanceof VirtualNode) {
            return getLocalInstanceData(XFormsUtils.getNodeFromNodeInfo(nodeInfo, ""));
        } else if (nodeInfo != null && ! forUpdate) {
            return READONLY_LOCAL_INSTANCE_DATA;
        } else if (nodeInfo != null && forUpdate) {
            throw new OXFException("Cannot update MIP information on non-VirtualNode NodeInfo.");
        } else {
            throw new OXFException("Null NodeInfo found.");
        }
    }

    public static InstanceData getLocalInstanceData(Node node) {

        // Find data annotation on node
        final Object instanceData;
        if (node instanceof Element) {
             instanceData = ((Element) node).getData();
        } else if (node instanceof Attribute) {
            instanceData = ((Attribute) node).getData();
        } else if (node instanceof Document) {
            // We can't store data on the Document object. Use root element instead.
            instanceData = ((Document) node).getRootElement().getData();
        } else {
            // TODO: other node types once we update to handling text nodes correctly. But it looks like Text does not support data.
            return null;
        }

        // Make sure we return InstanceData and not something else
        if (instanceData instanceof InstanceData)
            return (InstanceData) instanceData;
        else
            return null;
    }

    private static InstanceData createNewInstanceData(NodeInfo nodeInfo) {
        if (nodeInfo instanceof VirtualNode) {
            return createNewInstanceData(XFormsUtils.getNodeFromNodeInfo(nodeInfo, ""));
        } else {
            throw new OXFException("Cannot create InstanceData on non-VirtualNode NodeInfo.");
        }
    }

    private static InstanceData createNewInstanceData(Node node) {
        final InstanceData instanceData;
        if (node instanceof Element) {
            final Element element = (Element) node;
            instanceData = InstanceData.createNewInstanceData(element.getData());
            element.setData(instanceData);
        } else if (node instanceof Attribute) {
            final Attribute attribute = (Attribute) node;
            instanceData = InstanceData.createNewInstanceData(attribute.getData());
            attribute.setData(instanceData);
        } else if (node instanceof Document) {
            // We can't store data on the Document object. Use root element instead.
            final Element element = ((Document) node).getRootElement();
            instanceData = InstanceData.createNewInstanceData(element.getData());
            element.setData(instanceData);
        } else {
            // No other node type is supported
            throw new OXFException("Cannot create InstanceData on node type: " + Node$.MODULE$.nodeTypeName(node));
        }
        return instanceData;
    }

    private static InstanceData createNewInstanceData(Object existingData) {
        if (existingData instanceof LocationData) {
            return new InstanceData((LocationData) existingData);
        } else if (existingData instanceof InstanceData) {
            return new InstanceData(((InstanceData) existingData).getLocationData());
        } else {
            return new InstanceData(null);
        }
    }

    private InstanceData() {}

    private InstanceData(LocationData locationData) {
        this.locationData = locationData;
    }

    public LocationData getLocationData() {
        return locationData;
    }
}
