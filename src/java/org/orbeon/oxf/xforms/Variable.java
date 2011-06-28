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
package org.orbeon.oxf.xforms;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.analysis.VariableAnalysis;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.LastPositionFinder;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.EmptySequence;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.StringValue;

import java.util.List;

/**
 * Represents an exforms:variable / xxforms:variable element.
 *
 * TODO: Use more static information.
 */
public class Variable {

    private final XBLContainer container;
    private final XFormsContextStack contextStack;

    private final Element variableElement;
    private final Element valueElement;

    private String variableName;
    private String selectAttribute;

    private boolean evaluated;
    private ValueRepresentation variableValue;

    public Variable(XBLContainer container, XFormsContextStack contextStack, Element variableElement) {
        this.container = container;
        this.contextStack = contextStack;
        this.variableElement = variableElement;

        this.variableName = variableElement.attributeValue(XFormsConstants.NAME_QNAME);
        if (variableName == null)
            throw new ValidationException("xxforms:variable or exforms:variable element must have a \"name\" attribute", getLocationData());

        // Handle xxforms:sequence
        final Element sequenceElement = variableElement.element(XFormsConstants.XXFORMS_SEQUENCE_QNAME);
        if (sequenceElement == null) {
            this.valueElement = variableElement;
        } else {
            this.valueElement = sequenceElement;
        }

        this.selectAttribute = VariableAnalysis.valueOrSelectAttribute(valueElement);
    }

    private void evaluate(String sourceEffectiveId, boolean pushOuterContext) {
        if (selectAttribute == null) {
            // Inline constructor (for now, only textual content, but in the future, we could allow xforms:output in it? more?)
            variableValue = new StringValue(valueElement.getStringValue());
        } else {
            // There is a select attribute

            final boolean pushContext = pushOuterContext || variableElement != valueElement;
            if (pushContext) {
                // Push binding for evaluation, so that @context and @model are evaluated
                final String variableValuePrefixedId = container.getFullPrefix() + valueElement.attributeValue(XFormsConstants.ID_QNAME);
                final XBLBindingsBase.Scope variableValueScope = container.getPartAnalysis().getResolutionScopeByPrefixedId(variableValuePrefixedId);
                contextStack.pushBinding(valueElement, sourceEffectiveId, variableValueScope);
            }
            {
                final XFormsContextStack.BindingContext bindingContext = contextStack.getCurrentBindingContext();
                final List<Item> currentNodeset = bindingContext.getNodeset();
                if (currentNodeset != null && currentNodeset.size() > 0) {
                    // TODO: in the future, we should allow null context for expressions that do not depend on the context

                    final XFormsFunction.Context functionContext = contextStack.getFunctionContext(sourceEffectiveId);
                    variableValue = XPathCache.evaluateAsExtent(
                            currentNodeset, bindingContext.getPosition(),
                            selectAttribute, container.getNamespaceMappings(valueElement), bindingContext.getInScopeVariables(),
                            XFormsContainingDocument.getFunctionLibrary(), functionContext, null, getLocationData());
                    contextStack.returnFunctionContext();
                } else {
                    variableValue = EmptySequence.getInstance();
                }
            }
            if (pushContext) {
                contextStack.popBinding();
            }
        }
    }

    public String getVariableName() {
        return variableName;
    }

    public ValueRepresentation getVariableValue(String sourceEffectiveId, boolean pushOuterContext) {
        // Make sure the variable is evaluated
        if (!evaluated) {
            evaluated = true;
            evaluate(sourceEffectiveId, pushOuterContext);
        }

        // Return value and rewrap if necessary
        if (variableValue instanceof SequenceExtent) {
            // Rewrap NodeWrapper contained in the variable value. Not the most efficient, but at this point we have to
            // to ensure that things work properly. See RewrappingSequenceIterator for more details.
            try {
                return new SequenceExtent(new RewrappingSequenceIterator(((SequenceExtent) variableValue).iterate()));
            } catch (XPathException e) {
                // Should not happen with SequenceExtent
                throw new OXFException(e);
            }
        } else {
            // Return value as is
            return variableValue;
        }
    }

    public void markDirty() {
        evaluated = false;
    }

    public LocationData getLocationData() {
        return (LocationData) variableElement.getData();
    }

    /**
     * This iterator rewraps NodeWrapper elements so that the original NodeWrapper is discarded and a new one created.
     * The reason we do this is that when we keep variables around, we don't want NodeWrapper.index to be set to
     * anything but -1. If we did that, then upon insertions of nodes in the DOM, the index would be out of date.
     */
    private static class RewrappingSequenceIterator implements SequenceIterator, LastPositionFinder {

        private SequenceIterator iter;
        private Item current;

        public RewrappingSequenceIterator(SequenceIterator iter) {
            this.iter = iter;
        }

        public Item next() throws XPathException {
            final Item item = iter.next();

            if (item instanceof NodeWrapper) {
                // Rewrap
                final NodeWrapper nodeWrapper = (NodeWrapper) item;
                final DocumentWrapper documentWrapper = (DocumentWrapper) nodeWrapper.getDocumentRoot();

                current = documentWrapper.wrap(nodeWrapper.getUnderlyingNode());
            } else {
                // Pass through
                current = item;
            }

            return current;
        }

        public Item current() {
            return current;
        }

        public int position() {
            return iter.position();
        }

        public void close() {}

        public SequenceIterator getAnother() throws XPathException {
            return new RewrappingSequenceIterator(iter.getAnother());
        }

        public int getProperties() {
            return iter.getProperties();
        }

        public int getLastPosition() throws XPathException {
            if (iter instanceof LastPositionFinder)
                return ((LastPositionFinder) iter).getLastPosition();
            throw new OXFException("Call to getLastPosition() and nested iterator is not a LastPositionFinder.");
        }
    }
}
