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
package org.orbeon.oxf.processor.xforms.input;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.util.UserDataDocumentFactory;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.XMLConstants;
import org.orbeon.oxf.processor.scope.ScopeStore;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Iterator;
import java.util.List;

public class Instance {

    public static final String REQUEST_INSTANCE_DOCUMENT = "org.orbeon.oxf.request.xforms-instance-document";
    public static final String DEFAULT_UPLOAD_TYPE = "xs:anyURI";

    private PipelineContext pipelineContext;
    private Document instance;

    public Instance(PipelineContext pipelineContext, Document template) {
        this.pipelineContext = pipelineContext;
        instance = XMLUtils.createDOM4JDocument();
        instance.add(template.getRootElement().createCopy());
    }

    public Document getDocument() {
        return instance;
    }

    /**
     * Remove all the attribute and child nodes in the instance.
     */
    public void empty() {
        // TODO: instead of this, we should probably rather just create an empty document. The code
        // that updates the instance will have to be modified.
        Element root = instance.getRootElement();
        // Remove attributes
        while (root.attributeCount() > 0)
            root.attribute(0).detach();
        // Remove elements and text nodes
        for (Iterator i = root.elements().iterator(); i.hasNext();) {
            i.next();
            i.remove();
        }
    }

    /**
     * Set a value on the instance.
     *
     * If a type is specified, it means that the Request generator set it, which, for now, means
     * that it was a file upload.
     */
    public void setValue(String path, String value, String type, boolean dontSetIfExisting) {
        // Ignore blank path, which is sometimes submitted and can cause other parameters being erased if we go
        // through the update algorithm below
        if ("".equals(path))
            return;

        // Adjust null values
        // NOTE: We get nulls here when no value is specified for a given request parameter 
        if (value == null)
            value = "";

        Element currentElement = instance.getRootElement();
        if (".".equals(path)) {
            // Just have a root element
            setElementValue(currentElement, value, type, dontSetIfExisting);
        } else {

            // Get placeholder
            String rest = path;
            while (true) {

                // Stop when end of string
                if (rest.length() == 0)
                    break;

                // Attribute (optional)
                boolean isAttribute = false; {
                    if (rest.startsWith("@")) {
                        isAttribute = true;
                        rest = rest.substring(1);
                    }
                }

                // Namespace (optional)
                String namespaceURI = null; {
                    int nextOpeningBrace = rest.indexOf('{');
                    int nextSlash = rest.indexOf('/');
                    if ((nextOpeningBrace < nextSlash || nextSlash == -1) && nextOpeningBrace != -1) {
                        int endNamespace = rest.indexOf("}");
                        namespaceURI = rest.substring(1, endNamespace);
                        rest = rest.substring(endNamespace + 1);
                    }
                }

                // Local name
                QName qname; {
                    int nextOpeningBracket = rest.indexOf('[');
                    int nextSlash = rest.indexOf('/');
                    int endName = (nextOpeningBracket < nextSlash || nextSlash == -1) && nextOpeningBracket != -1
                            ? nextOpeningBracket
                            : nextSlash != -1 ? nextSlash : rest.length();
                    String qualifiedName = rest.substring(0, endName);
                    int colonIndex = qualifiedName.indexOf(':');
                    String prefix = colonIndex == -1 ? null : qualifiedName.substring(0, colonIndex);
                    String localName = colonIndex == -1 ? qualifiedName : qualifiedName.substring(colonIndex + 1);
                    qname = namespaceURI == null
                            ? new QName(qualifiedName)
                            : prefix == null
                                ? new QName(qualifiedName, new Namespace(null, namespaceURI))
                                : new QName(localName, new Namespace(null, namespaceURI), qualifiedName);
                    rest = rest.substring(endName);
                }

                // Index (optional)
                int index = 0; {
                    if (rest.startsWith("[")) {
                        int closingBracket = rest.indexOf("]");
                        index = Integer.parseInt(rest.substring(1, closingBracket)) - 1;
                        rest = rest.substring(closingBracket + 1);
                    }
                }

                // Closing slash (optional)
                if (rest.startsWith("/"))
                    rest = rest.substring(1);

                // Update currentElement
                if (isAttribute) {

                    // Handle xsi:type if needed
                    if (XMLConstants.XSI_TYPE_QNAME.equals(qname) && !"".equals(currentElement.getText())) {
                        // This is a type attribute and we already have content
                        String currentType = currentElement.attributeValue(XMLConstants.XSI_TYPE_QNAME);
                        if (currentType != null && !currentType.equals(value)) { // FIXME: prefixes of type name could be different!
                            // Convert element value
                            String newValue = convertUploadTypes(currentElement.getText(), currentType, value);
                            currentElement.clearContent();
                            currentElement.addText(newValue);
                            if ("".equals(qname.getNamespacePrefix()))
                                qname = XMLConstants.XSI_TYPE_QNAME;
                        }
                    }

                    // Add attribute
                    currentElement.add(UserDataDocumentFactory.getInstance()
                            .createAttribute(currentElement, qname, value));
                    currentElement = null;

                    // NOTE: We do not yet support binding types to attributes, which means that if
                    // there is a type specified on the input value, there will be no conversion.
                } else {
                    // Get element
                    List children = currentElement.elements();
                    if (index >= children.size()) {
                        // We need to create element(s)
                        int insertions = index - children.size() + 1;
                        for (int i = 0; i < insertions; i++)
                            children.add(UserDataDocumentFactory.getInstance().createElement("dummy"));
                    }
                    currentElement = (Element) children.get(index);
                    currentElement.setQName(qname);
                }
            }

            // Fill leaf element
            if (currentElement != null) {
                setElementValue(currentElement, value, type, dontSetIfExisting);
            }
        }
    }

    private void setElementValue(Element currentElement, String value, String type, boolean dontSetIfExisting) {
        // Don't do anything if value exists and dontSetIfexisting is true
        if (dontSetIfExisting && !"".equals(currentElement.getText()))
            return;
        if (type != null) {
            // Handle value type
            String currentType = currentElement.attributeValue(XMLConstants.XSI_TYPE_QNAME);
            if (currentType != null && !currentType.equals(type)) { // FIXME: prefixes of type name could be different!
                // There is a different type already, do a conversion
                value = convertUploadTypes(value, type, currentType);
                currentElement.clearContent();
            } else if (currentType == null) {
                // There is no type, convert to default type
                if (!DEFAULT_UPLOAD_TYPE.equals(type)) // FIXME: prefixes of type name could be different!
                    value = convertUploadTypes(value, type, DEFAULT_UPLOAD_TYPE);
                currentElement.add(UserDataDocumentFactory.getInstance().createAttribute(currentElement, XMLConstants.XSI_TYPE_QNAME, DEFAULT_UPLOAD_TYPE));
            }
            currentElement.setText(value);
        } else {
            // No type, just set the value
            currentElement.setText(value);
        }
    }

    /**
     * Output the instance to the specified content handler
     */
    public void read(ContentHandler contentHandler) {
        try {
            LocationSAXWriter saxw = new LocationSAXWriter();
            saxw.setContentHandler(contentHandler);
            saxw.write(instance);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    private String convertUploadTypes(String value, String currentType, String newType) {
        if (currentType.equals("newType"))
            return value;
        if (ProcessorUtils.supportedBinaryTypes.get(currentType) == null)
            throw new UnsupportedOperationException("Unsupported type: " + currentType);
        if (ProcessorUtils.supportedBinaryTypes.get(newType) == null)
            throw new UnsupportedOperationException("Unsupported type: " + newType);

        if (currentType.equals(XMLConstants.BASE64BINARY_TYPE)) {
            // Convert from xs:base64Binary to xs:anyURI
            return XMLUtils.base64BinaryToAnyURI(pipelineContext, value); 
        } else {
            // Convert from xs:anyURI to xs:base64Binary
            return XMLUtils.anyURIToBase64Binary(value);
        }
    }

    public static Instance createInstanceFromContext(PipelineContext pipelineContext) {
        ExternalContext.Request request = getRequest(pipelineContext);
        ScopeStore instanceContextStore = (ScopeStore) request.getAttributesMap().get(REQUEST_INSTANCE_DOCUMENT);
        return (instanceContextStore == null || instanceContextStore.getSaxStore() == null) ? null : new Instance(pipelineContext, instanceContextStore.getSaxStore().getDocument());
    }

    private static ExternalContext.Request getRequest(PipelineContext context) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context");
        ExternalContext.Request request = externalContext.getRequest();
        return request;
    }
}
