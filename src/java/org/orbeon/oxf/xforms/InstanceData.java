/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.Axis;
import org.orbeon.saxon.om.NameChecker;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.style.StandardNames;

import java.util.*;

/**
 * Instances of this class are used to annotate XForms instance nodes with MIPs and other information.
 *
 * Previously, all element and attribute nodes in every XForms instance were annotated with instances of this class.
 * Annotations are now done lazily when needed in order to reduce the number of objects created. This has a positive
 * impact on memory usage and garbage collection. This is also why most methods in this class are static.
 *
 * @noinspection PointlessBooleanExpression
 */
public class InstanceData {

    private LocationData locationData;

    // All MIP default values
    private static final boolean DEFAULT_RELEVANT = true;
    private static final boolean DEFAULT_READONLY = false;
    private static final boolean DEFAULT_REQUIRED = false;
    private static final boolean DEFAULT_VALID = true;
    
    private static final String DEFAULT_CUSTOM = null;

    // All MIPs with their default values
    private boolean relevant = DEFAULT_RELEVANT;
    protected boolean readonly = DEFAULT_READONLY;
    private boolean required = DEFAULT_REQUIRED;

    private String type;
    private boolean valueValid = DEFAULT_VALID;
    private boolean constraint = DEFAULT_VALID;// TODO: we don't really need separate constraint information: valueValid is enough!

    private String invalidBindIds;
    private List<String> schemaErrors;

    // Custom MIPs
    private Map<String, String> customMips = null;

    private static InstanceData READONLY_LOCAL_INSTANCE_DATA = new InstanceData() {
        {
            // Default for non-mutable nodes is to be read-only
            this.readonly = true;
        }
    };

    private InstanceData() {
    }

    private InstanceData(LocationData locationData) {
        this.locationData = locationData;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public static void setCustom(NodeInfo nodeInfo, String name, String value) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, true);
        if (existingInstanceData == null) {
            if (value == DEFAULT_CUSTOM) {
                // Not changing from the default so don't even create object
                return;
            } else {
                // Changing from the default
                final InstanceData newInstanceData = createNewInstanceData(nodeInfo);
                if (newInstanceData.customMips == null)
                    newInstanceData.customMips = new HashMap<String, String>();
                newInstanceData.customMips.put(name, value);
            }
        } else {
            if (existingInstanceData.customMips == null)
                existingInstanceData.customMips = new HashMap<String, String>();
            existingInstanceData.customMips.put(name, value);
        }
    }

    public static Map<String, String> getAllCustom(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null) ? null : existingInstanceData.customMips;
    }

    public static String getCustom(NodeInfo nodeInfo, String name) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null)
                ? DEFAULT_CUSTOM
                : (existingInstanceData.customMips == null) ? null : existingInstanceData.customMips.get(name);
    }

    public static String getCustom(Node node, String name) {
        final InstanceData existingInstanceData = getLocalInstanceData(node, false);
        return (existingInstanceData == null)
                ? DEFAULT_CUSTOM
                : (existingInstanceData.customMips == null) ? null : existingInstanceData.customMips.get(name);
    }

    public static void setRelevant(NodeInfo nodeInfo, boolean relevant) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, true);
        if (existingInstanceData == null) {
            if (relevant == DEFAULT_RELEVANT) {
                // Not changing from the default so don't even create object
                return;
            } else {
                // Changing from the default
                final InstanceData newInstanceData = createNewInstanceData(nodeInfo);
                newInstanceData.relevant = relevant;
            }
        } else {
            existingInstanceData.relevant = relevant;
        }
    }

    public static boolean getInheritedRelevant(NodeInfo nodeInfo) {
        if (nodeInfo instanceof NodeWrapper) {
            return getInheritedRelevant(XFormsUtils.getNodeFromNodeInfo(nodeInfo, ""));
        } else if (nodeInfo != null) {
            return DEFAULT_RELEVANT;
        } else {
            throw new OXFException("Cannot get relevant Model Item Property on null object.");
        }
    }

    public static boolean getInheritedRelevant(Node node) {
        // Iterate this node and its parents. The node is non-relevant if it or any ancestor is non-relevant.
        for (Node currentNode = node; currentNode != null; currentNode = currentNode.getParent()) {
            final InstanceData currentInstanceData = getLocalInstanceData(currentNode);
            final boolean currentRelevant = (currentInstanceData == null) ? DEFAULT_RELEVANT : currentInstanceData.relevant;
            if (!currentRelevant)
                return false;
        }
        return true;
    }

    public static void setRequired(NodeInfo nodeInfo, boolean required) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, true);
        if (existingInstanceData == null) {
            if (required == DEFAULT_REQUIRED) {
                // Not changing from the default so don't even create object
                return;
            } else {
                // Changing from the default
                final InstanceData newInstanceData = createNewInstanceData(nodeInfo);
                newInstanceData.required = required;
            }
        } else {
            existingInstanceData.required = required;
        }
    }

    public static boolean getRequired(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null) ? DEFAULT_REQUIRED : existingInstanceData.required;
    }

    public static boolean getRequired(Node node) {
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        return (existingInstanceData == null) ? DEFAULT_REQUIRED : existingInstanceData.required;
    }

    public static void setReadonly(NodeInfo nodeInfo, boolean readonly) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, true);
        if (existingInstanceData == null) {
            if (readonly == DEFAULT_READONLY) {
                // Not changing from the default so don't even create object
                return;
            } else {
                // Changing from the default
                final InstanceData newInstanceData = createNewInstanceData(nodeInfo);
                newInstanceData.readonly = readonly;
            }
        } else {
            existingInstanceData.readonly = readonly;
        }
    }

    public static boolean getInheritedReadonly(NodeInfo nodeInfo) {
        if (nodeInfo instanceof NodeWrapper) {
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
            final boolean currentReadonly = (currentInstanceData == null) ? DEFAULT_READONLY : currentInstanceData.readonly;
            if (currentReadonly)
                return true;
        }
        return false;
    }

    public static boolean getValid(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null) ? DEFAULT_VALID : existingInstanceData.constraint && existingInstanceData.valueValid;
    }

    public static boolean getValid(Node node) {
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        return (existingInstanceData == null) ? DEFAULT_VALID : existingInstanceData.constraint && existingInstanceData.valueValid;
    }

    public static void setType(NodeInfo nodeInfo, String type) {
        getOrCreateInstanceData(nodeInfo).type = type;
    }

    public static void setType(Node node, String type) {
        getOrCreateInstanceData(node).type = type;
    }

    public static String getType(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        if (existingInstanceData == null || existingInstanceData.type == null) {
            // No type was assigned by schema or MIP
            if (nodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE) {
                // Check for xsi:type attribute
                // NOTE: Saxon 9 has new code to resolve such QNames
                final String typeQName = nodeInfo.getAttributeValue(StandardNames.XSI_TYPE);
                if (typeQName != null) {
                    try {
                        final NameChecker checker = nodeInfo.getConfiguration().getNameChecker();
                        final String[] parts = checker.getQNameParts(typeQName);

                        // No prefix
                        if (parts[0].equals("")) {
                            return parts[1];
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
                                return XMLUtils.buildExplodedQName(currentNamespaceNode.getStringValue(), parts[1]);
                            }
                        }
                    } catch (Exception e) {
                        throw new OXFException(e);
                    }
                }
            }
            return null;
        } else {
            // Return type assigned by schema or MIP
            return existingInstanceData.type;
        }
    }

    public static String getType(Node node) {
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        if (existingInstanceData == null || existingInstanceData.type == null) {
            // No type was assigned by schema or MIP
            if (node instanceof Element) {
                // Check for xsi:type attribute
                final Element element = (Element) node;
                // TODO: should pass true?
                final QName typeQName = Dom4jUtils.extractAttributeValueQName(element, XMLConstants.XSI_TYPE_QNAME, false);
                if (typeQName != null)
                    return Dom4jUtils.qNameToExplodedQName(typeQName);
            }
            return null;
        } else {
            // Return type assigned by schema or MIP
            return existingInstanceData.type;
        }
    }

    public static String getInvalidBindIds(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        return (existingInstanceData == null) ? null : existingInstanceData.invalidBindIds;
    }

    public static void addSchemaError(NodeInfo nodeInfo, final String schemaError, final String stringValue, String modelBindId) {
        if (nodeInfo instanceof NodeWrapper) {
            addSchemaError(XFormsUtils.getNodeFromNodeInfo(nodeInfo, ""), schemaError, stringValue, modelBindId);
        } else {
            throw new OXFException("Cannot add schema to non-NodeWrapper NodeInfo.");
        }
    }

    public static void addSchemaError(Node node, final String schemaError, final String stringValue, String modelBindId) {

        // Get or create InstanceData
        final InstanceData instanceData = getOrCreateInstanceData(node);

        // Remember that the value is invalid
        instanceData.valueValid = false;

        // Add schema errors if provided
        if (schemaError != null) {
            if (instanceData.schemaErrors == null)
                instanceData.schemaErrors = new ArrayList<String>(1);
            instanceData.schemaErrors.add(schemaError);
        }

        // Add bind id if provided
        if (modelBindId != null) {
            instanceData.invalidBindIds = (instanceData.invalidBindIds == null) ? modelBindId : instanceData.invalidBindIds + " " + modelBindId;
        }
    }

    public static Iterator getSchemaErrors(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);
        final Collection result;
        if (existingInstanceData != null) {
            if (existingInstanceData.schemaErrors == null) {
                result = Collections.EMPTY_LIST;
            } else {
                result = Collections.unmodifiableCollection(existingInstanceData.schemaErrors);
            }
        } else {
            result = Collections.EMPTY_LIST;
        }
        return result.iterator();
    }

    public static void updateValueValid(NodeInfo nodeInfo, boolean valueValid, String modelBindId) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, true);
        if (existingInstanceData == null) {
            if (valueValid == DEFAULT_VALID) {
                // Not changing from the default so don't even create object
                return;
            } else {
                // Changing from the default
                final InstanceData newInstanceData = createNewInstanceData(nodeInfo);
                newInstanceData.valueValid = valueValid;

                if (modelBindId != null)
                    newInstanceData.invalidBindIds = (newInstanceData.invalidBindIds == null) ? modelBindId : newInstanceData.invalidBindIds + " " + modelBindId;
            }
        } else {
            // Never go back from default
            if (valueValid != DEFAULT_VALID) {
                existingInstanceData.valueValid = valueValid;

                if (modelBindId != null)
                    existingInstanceData.invalidBindIds = (existingInstanceData.invalidBindIds == null) ? modelBindId : existingInstanceData.invalidBindIds + " " + modelBindId;
            }
        }
    }

    public static void clearValidationState(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);// not really an update since for read-only nothing changes
        if (existingInstanceData != null) {
            // Clear everything related to validity (except required)
            existingInstanceData.valueValid = DEFAULT_VALID;
            existingInstanceData.constraint = DEFAULT_VALID;
            existingInstanceData.type = null;
            existingInstanceData.invalidBindIds = null;
            if (existingInstanceData.schemaErrors != null)
                existingInstanceData.schemaErrors.clear();
        }
    }

    public static void clearOtherState(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, false);// not really an update since for read-only nothing changes
        if (existingInstanceData != null) {

            existingInstanceData.relevant = DEFAULT_RELEVANT;
            existingInstanceData.readonly = DEFAULT_READONLY;
            existingInstanceData.required = DEFAULT_REQUIRED;
        }
    }

    private static InstanceData getOrCreateInstanceData(NodeInfo nodeInfo) {
        final InstanceData existingInstanceData = getLocalInstanceData(nodeInfo, true);
        return (existingInstanceData != null) ? existingInstanceData : createNewInstanceData(nodeInfo);
    }

    private static InstanceData getOrCreateInstanceData(Node node) {
        final InstanceData existingInstanceData = getLocalInstanceData(node);
        return (existingInstanceData != null) ? existingInstanceData : createNewInstanceData(node);
    }

    private static InstanceData getLocalInstanceData(Node node, boolean forUpdate) {
        return getLocalInstanceData(node);
    }

    private static InstanceData getLocalInstanceData(NodeInfo nodeInfo, boolean forUpdate) {
        if (nodeInfo instanceof NodeWrapper) {
            return getLocalInstanceData(XFormsUtils.getNodeFromNodeInfo(nodeInfo, ""));
        } else if (nodeInfo != null && !forUpdate) {
            return READONLY_LOCAL_INSTANCE_DATA;
        } else if (nodeInfo != null && forUpdate) {
            throw new OXFException("Cannot update MIP information on non-NodeWrapper NodeInfo.");
        } else {
            throw new OXFException("Null NodeInfo found.");
        }
    }

    private static InstanceData getLocalInstanceData(Node node) {

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

    public static void remove(Node node) {

        // We can't store data on the Document object. Use root element instead.
        if (node instanceof Document)
            node = ((Document) node).getRootElement();

        if (node instanceof Element) {
            final Element element = (Element) node;

            // Handle current element
            element.setData(null);

            // Handle attributes
            for (Object o: element.attributes()) {
                final Attribute attribute = (Attribute) o;
                remove(attribute);
            }
            // Handle children elements
            for (Object o: element.elements()) {
                final Element childElement = (Element) o;
                remove(childElement);
            }

        } else if (node instanceof Attribute) {
            ((Attribute) node).setData(null);
        } else {
            // TODO: other node types once we update to handling text nodes correctly. But it looks like Text does not support data.
        }
    }

    private static InstanceData createNewInstanceData(NodeInfo nodeInfo) {
        if (nodeInfo instanceof NodeWrapper) {
            return createNewInstanceData(XFormsUtils.getNodeFromNodeInfo(nodeInfo, ""));
        } else {
            throw new OXFException("Cannot create InstanceData on non-NodeWrapper NodeInfo.");
        }
    }

    private static InstanceData createNewInstanceData(Node node) {
        final InstanceData instanceData;
        if (node instanceof Element) {
            final Element element = (Element) node;
            instanceData = InstanceData.newInstanceData(element.getData());
            element.setData(instanceData);
        } else if (node instanceof Attribute) {
            final Attribute attribute = (Attribute) node;
            instanceData = InstanceData.newInstanceData(attribute.getData());
            attribute.setData(instanceData);
        } else if (node instanceof Document) {
            // We can't store data on the Document object. Use root element instead.
            final Element element = ((Document) node).getRootElement();
            instanceData = InstanceData.newInstanceData(element.getData());
            element.setData(instanceData);
        } else {
            // TODO: other node types once we update to handling text nodes correctly. But it looks like Text does not support data.
            throw new OXFException("Cannot create InstanceData on node type: " + node.getNodeTypeName());
        }
        return instanceData;
    }

    private static InstanceData newInstanceData(Object existingData) {
        if (existingData instanceof LocationData) {
            return new InstanceData((LocationData) existingData);
        } else if (existingData instanceof InstanceData) {
            return new InstanceData(((InstanceData) existingData).getLocationData());
        } else {
            return new InstanceData(null);
        }
    }
}

