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
package org.orbeon.dom.saxon;

import org.orbeon.dom.Document;
import org.orbeon.dom.Node;
import org.orbeon.dom.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.model.InstanceData;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.Err;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.SchemaType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.UntypedAtomicValue;
import org.orbeon.saxon.value.Value;

/**
 * This wrapper is an extension of the Saxon node wrapper which is aware of XForms type annotations.
 */
public class TypedNodeWrapper extends org.orbeon.dom.saxon.NodeWrapper {

    private TypedNodeWrapper(Node node, org.orbeon.dom.saxon.NodeWrapper parent) {
        super(node, parent);
    }

    @Override
    protected org.orbeon.dom.saxon.NodeWrapper makeWrapper(Node node, org.orbeon.dom.saxon.DocumentWrapper docWrapper, org.orbeon.dom.saxon.NodeWrapper parent) {
        return makeTypedWrapper(node, docWrapper, parent);
    }

    static org.orbeon.dom.saxon.NodeWrapper makeTypedWrapper(Node node, org.orbeon.dom.saxon.DocumentWrapper docWrapper, org.orbeon.dom.saxon.NodeWrapper parent) {
        if (node instanceof Document) {
            return docWrapper;
        } else {
            final org.orbeon.dom.saxon.NodeWrapper wrapper = new TypedNodeWrapper(node, parent);
            wrapper.docWrapper = docWrapper;
            return wrapper;
        }
    }

    @Override
    public SequenceIterator getTypedValue() throws XPathException {
        int annotation = getTypeAnnotation();
        if ((annotation & NodeInfo.IS_DTD_TYPE) != 0) {
            annotation = StandardNames.XS_UNTYPED_ATOMIC;
        }
        annotation &= NamePool.FP_MASK;
        if (annotation == -1 || annotation == StandardNames.XS_UNTYPED_ATOMIC || annotation == StandardNames.XS_UNTYPED) {
            return SingletonIterator.makeIterator(new UntypedAtomicValue(getStringValueCS()));
        } else {
            final SchemaType stype = getConfiguration().getSchemaType(annotation);
            if (stype == null) {
                throw new XPathException("Unknown type annotation " +
                        Err.wrap(getAnnotationTypeName(annotation)) + " in document instance");
            } else {
                try {
                    return stype.getTypedValue(this);
                } catch (Exception err) {
                    throw new TypedValueException(getDisplayName(), getAnnotationTypeName(annotation), getStringValue());
                }
            }
        }
    }

    private String getAnnotationTypeName(int annotation) {
        try {
            return getNamePool().getDisplayName(annotation);
        } catch (Exception err) {
            return Integer.toString(annotation);
        }
    }

    public static class TypedValueException extends RuntimeException {
        public final String nodeName;
        public final String typeName;
        public final String nodeValue;
        public TypedValueException(String nodeName, String typeName, String nodeValue) {
            this.nodeName = nodeName;
            this.typeName = typeName;
            this.nodeValue = nodeValue;
        }
    }

    // FIXME: This is almost 100% duplicated from getTypedValue above.
    @Override
    public Value atomize() throws XPathException {
        int annotation = getTypeAnnotation();
        if ((annotation & NodeInfo.IS_DTD_TYPE) != 0) {
            annotation = StandardNames.XS_UNTYPED_ATOMIC;
        }
        annotation &= NamePool.FP_MASK;
        if (annotation == -1 || annotation == StandardNames.XS_UNTYPED_ATOMIC || annotation == StandardNames.XS_UNTYPED) {
            return new UntypedAtomicValue(getStringValueCS());
        } else {
            final SchemaType stype = getConfiguration().getSchemaType(annotation);
            if (stype == null) {
                throw new XPathException("Unknown type annotation " +
                        Err.wrap(getAnnotationTypeName(annotation)) + " in document instance");
            } else {
                try {
                    return stype.atomize(this);
                } catch (Exception err) { // TODO: Would be good to pass err.getMessage()
                    throw new TypedValueException(getDisplayName(), getAnnotationTypeName(annotation), getStringValue());
                }
            }
        }
    }

    @Override
    public int getTypeAnnotation() {

        final QName nodeType = InstanceData.getType((Node) node);
        if (nodeType == null) {
            return getUntypedType();
        } else {
            // Extract QName
            String uri = nodeType.namespace().uri();
            final String localname = nodeType.localName();

            // For type annotation purposes, xforms:integer is translated into xs:integer. This is because XPath has no
            // knowledge of the XForms union types.
            if (uri.equals(XFormsConstants.XFORMS_NAMESPACE_URI()) && Model.jXFormsVariationTypeNames().contains(localname))
                uri = XMLConstants.XSD_URI();

            final int requestedTypeFingerprint = StandardNames.getFingerprint(uri, localname);
            if (requestedTypeFingerprint == -1) {
                // Back to default case
                return getUntypedType();
            } else {
                // Return identified type
                return requestedTypeFingerprint;
            }
        }
    }

    private int getUntypedType() {
        if (getNodeKind() == Type.ATTRIBUTE) {
            return StandardNames.XS_UNTYPED_ATOMIC;
        }
        return StandardNames.XS_UNTYPED;
    }
}
