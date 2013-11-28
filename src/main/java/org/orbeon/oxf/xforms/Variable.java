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
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.analysis.VariableAnalysis;
import org.orbeon.oxf.xforms.analysis.VariableAnalysisTrait;
import org.orbeon.oxf.xforms.analysis.controls.ViewTrait;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.LastPositionFinder;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.om.VirtualNode;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.EmptySequence;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.StringValue;

import java.util.List;

/**
 * Represents an exf:variable / xxf:variable element.
 *
 * TODO: Use more static information.
 */
public class Variable {

    private final VariableAnalysisTrait staticVariable;
    private final XFormsContainingDocument containingDocument;

    private final Element variableElement;
    private final Element valueElement;

    public final String variableName;
    public final String expression;

    private boolean evaluated;
    private ValueRepresentation variableValue;

    public Variable(VariableAnalysisTrait staticVariable, XFormsContainingDocument containingDocument) {
        this.staticVariable = staticVariable;
        this.containingDocument = containingDocument;
        this.variableElement = ((ElementAnalysis) staticVariable).element();

        this.variableName = variableElement.attributeValue(XFormsConstants.NAME_QNAME);
        if (variableName == null)
            throw new ValidationException("xf:var, xxf:variable or exf:variable element must have a \"name\" attribute", getLocationData());

        // Handle xxf:sequence
        final Element sequenceElement = variableElement.element(XFormsConstants.XXFORMS_SEQUENCE_QNAME);
        if (sequenceElement == null) {
            this.valueElement = variableElement;
        } else {
            this.valueElement = sequenceElement;
        }

        this.expression = VariableAnalysis.valueOrSelectAttribute(valueElement);
    }

    private void evaluate(XFormsContextStack contextStack, String sourceEffectiveId, boolean pushOuterContext, boolean handleNonFatal) {
        if (expression == null) {
            // Inline constructor (for now, only textual content, but in the future, we could allow xf:output in it? more?)
            variableValue = new StringValue(valueElement.getStringValue());
        } else {
            // There is a select attribute

            final boolean pushContext = pushOuterContext || staticVariable.hasSequence();
            if (pushContext) {
                // Push binding for evaluation, so that @context and @model are evaluated
                final Scope variableValueScope = staticVariable.valueScope();
                contextStack.pushBinding(valueElement, sourceEffectiveId, variableValueScope);
            }
            {
                final BindingContext bindingContext = contextStack.getCurrentBindingContext();
                final List<Item> currentNodeset = bindingContext.nodeset();
                if (currentNodeset != null && currentNodeset.size() > 0) {
                    // TODO: in the future, we should allow null context for expressions that do not depend on the context

                    final XFormsFunction.Context functionContext = contextStack.getFunctionContext(sourceEffectiveId);
                    final boolean scopeModelVariables = VariableAnalysis.variableScopesModelVariables(staticVariable);
                    try {
                        variableValue = XPathCache.evaluateAsExtent(
                                currentNodeset, bindingContext.position(),
                                expression, staticVariable.valueNamespaceMapping(), bindingContext.getInScopeVariables(scopeModelVariables),
                                XFormsContainingDocument.getFunctionLibrary(), functionContext, null, getLocationData(),
                                containingDocument.getRequestStats().getReporter());
                    } catch (Exception e) {
                        if (handleNonFatal) {
                            // Don't consider this as fatal
                            // Default value is the empty sequence
                            XFormsError.handleNonFatalXPathError(contextStack.container, e);
                            variableValue = EmptySequence.getInstance();
                        } else {
                            throw new OXFException(e);
                        }
                    }
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

    public ValueRepresentation getVariableValue(XFormsContextStack contextStack, String sourceEffectiveId, boolean pushOuterContext, boolean handleNonFatal) {
        // Make sure the variable is evaluated
        final boolean justEvaluated;
        if (!evaluated) {
            evaluated = true;
            evaluate(contextStack, XFormsUtils.getRelatedEffectiveId(sourceEffectiveId, staticVariable.valueStaticId()), pushOuterContext, handleNonFatal);
            justEvaluated = true;
        } else
            justEvaluated = false;

        // Return value and rewrap if necessary
        if (! justEvaluated && (variableValue instanceof SequenceExtent)) {
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
        return ((ElementAnalysis) staticVariable).locationData();
    }

    /**
     * This iterator rewraps NodeWrapper elements so that the original NodeWrapper is discarded and a new one created.
     * The reason we do this is that when we keep variables around, we don't want NodeWrapper.index to be set to
     * anything but -1. If we did that, then upon insertions of nodes in the DOM, the index would be out of date.
     *
     * Q: Could we instead do this only upon insert/delete, and use the dependency engine to mark only mutated
     * variables? What about in actions? What about using wrappers for variables which don't cache the position?
     */
    private static class RewrappingSequenceIterator implements SequenceIterator, LastPositionFinder {

        private SequenceIterator iter;
        private Item current;

        public RewrappingSequenceIterator(SequenceIterator iter) {
            this.iter = iter;
        }

        public Item next() throws XPathException {
            final Item item = iter.next();

            if (item instanceof VirtualNode) {
                // Rewrap
                final VirtualNode virtualNode = (VirtualNode) item;
                final DocumentWrapper documentWrapper = (DocumentWrapper) virtualNode.getDocumentRoot();

                current = documentWrapper.wrap(virtualNode.getUnderlyingNode());
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
