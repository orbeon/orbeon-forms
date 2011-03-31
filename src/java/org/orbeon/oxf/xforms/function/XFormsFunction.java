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
package org.orbeon.oxf.xforms.function;

import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.functions.SystemFunction;
import org.orbeon.saxon.om.NamespaceResolver;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.sxpath.IndependentContext;
import org.orbeon.saxon.sxpath.XPathVariable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.QNameValue;

import java.util.*;

/**
 * Base class for all XForms functions.
 *
 * TODO: context should contain PropertyContext directly
 * TODO: context should contain BindingContext directly if any
 */
abstract public class XFormsFunction extends SystemFunction {

    protected XFormsFunction() {
    }

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     *
     * NOTE: A few functions would benefit from not having this, but it is always safe.
     */
    @Override
    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        return this;
    }

    private Context getContext(XPathContext xpathContext) {
        return (Context) PooledXPathExpression.getFunctionContext(xpathContext);
    }

    protected XFormsControls getControls(XPathContext xpathContext) {
        return getContext(xpathContext).getControls();
    }

    protected XBLContainer getXBLContainer(XPathContext xpathContext) {
        final Context functionContext = (XFormsFunction.Context) PooledXPathExpression.getFunctionContext(xpathContext);
        return functionContext.getXBLContainer();
    }

    protected Element getSourceElement(XPathContext xpathContext) {
        return getContext(xpathContext).getSourceElement();
    }

    protected String getSourceEffectiveId(XPathContext xpathContext) {
        final Context functionContext = (XFormsFunction.Context) PooledXPathExpression.getFunctionContext(xpathContext);
        final String sourceEffectiveId = functionContext.getSourceEffectiveId();
        if (sourceEffectiveId == null)
            throw new OXFException("Source effective id not available for resolution.");
        return sourceEffectiveId;
    }

    protected XFormsModel getModel(XPathContext xpathContext) {
        final Context functionContext = (XFormsFunction.Context) PooledXPathExpression.getFunctionContext(xpathContext);
        return functionContext.getModel();
    }

    protected XFormsContainingDocument getContainingDocument(XPathContext xpathContext) {
        final Context context = getContext(xpathContext);
        return (context != null) ? context.getXBLContainer().getContainingDocument() : null;
    }

    protected XFormsContextStack getContextStack(XPathContext xpathContext) {
        return getContext(xpathContext).getContextStack();
    }

    // NOTE: This is always constructed in XFormsContextStack
    public static class Context implements XPathCache.FunctionContext {

        private final XBLContainer container;
        private final XFormsContextStack contextStack;

        private String sourceEffectiveId;
        private Element sourceElement;
        private XFormsModel model;

        // Constructor for XBLContainer and XFormsActionInterpreter
        public Context(XBLContainer container, XFormsContextStack contextStack) {
            this.container = container;
            this.contextStack = contextStack;
        }

        // Constructor for XFormsModel
        public Context(XFormsModel containingModel, XFormsContextStack contextStack) {
            this.container = containingModel.getXBLContainer();
            this.contextStack = contextStack;
        }

        public XBLContainer getXBLContainer() {
            return container;
        }

        public XFormsContainingDocument getContainingDocument() {
            return container.getContainingDocument();
        }

        public XFormsControls getControls() {
            return getContainingDocument().getControls();
        }

        public XFormsModel getModel() {
            return model;
        }

        public void setModel(XFormsModel model) {
            this.model = model;
        }

        public XFormsContextStack getContextStack() {
            return contextStack;
        }

        public String getSourceEffectiveId() {
            return sourceEffectiveId;
        }

        public void setSourceEffectiveId(String sourceEffectiveId) {
            this.sourceEffectiveId = sourceEffectiveId;
        }

        public Element getSourceElement() {
            return sourceElement;
        }

        public void setSourceElement(Element sourceElement) {
            this.sourceElement = sourceElement;
        }
    }

    protected QName getQNameFromExpression(XPathContext xpathContext, Expression qNameExpression) throws XPathException {
        final Object qNameObject = (qNameExpression == null) ? null : qNameExpression.evaluateItem(xpathContext);
        if (qNameObject instanceof QNameValue) {
            // Directly got a QName
            final QNameValue qName = (QNameValue) qNameObject;

            final String qNameString = qName.getStringValue();
            final String qNameURI = qName.getNamespaceURI();

            final int colonIndex = qNameString.indexOf(':');
            final String prefix = qNameString.substring(0, colonIndex);
            return createQName(qNameString, qNameURI, colonIndex, prefix);

        } else if (qNameObject != null) {
            // Another atomic value
            final AtomicValue qName = (AtomicValue) qNameObject;
            final String qNameString = qName.getStringValue();

            final int colonIndex = qNameString.indexOf(':');
            if (colonIndex == -1) {
                // NCName
                return createQName(qNameString, null, colonIndex, null);
            } else {
                // QName-but-not-NCName
                final String prefix = qNameString.substring(0, colonIndex);

                final XFormsContextStack contextStack = getContextStack(xpathContext);
                // TODO: function context should directly provide BindingContext
                final NamespaceMapping namespaceMapping = getXBLContainer(xpathContext).getNamespaceMappings(contextStack.getCurrentBindingContext().getControlElement());

                // Get QName URI
                final String qNameURI = namespaceMapping.mapping.get(prefix);
                if (qNameURI == null)
                    throw new OXFException("Namespace prefix not in space for QName: " + qNameString);

                return createQName(qNameString, qNameURI, colonIndex, prefix);
            }
        } else {
            // Just don't return anything if no QName was passed
            return null;
        }
    }

    private QName createQName(String qNameString, String qNameURI, int colonIndex, String prefix) {
        if (colonIndex == -1) {
            // NCName
            // NOTE: This assumes that if there is no prefix, the QName is in no namespace
            return new QName(qNameString);
        } else {
            // QName-but-not-NCName
            return new QName(qNameString.substring(colonIndex + 1), new Namespace(prefix, qNameURI));
        }
    }
    
    // The following is inspired by saxon:evaluate()
    protected PooledXPathExpression prepareExpression(XPathContext initialXPathContext, Expression parameterExpression, boolean isAVT) throws XPathException {

        // Evaluate parameter into an XPath string
        final String xpathString
                = ((AtomicValue) parameterExpression.evaluateItem(initialXPathContext)).getStringValue();

        // Copy static context information
        final IndependentContext staticContext = this.staticContext.copy();
        staticContext.setFunctionLibrary(initialXPathContext.getController().getExecutable().getFunctionLibrary());

        // Propagate in-scope variable definitions since they are not copied automatically
        final XFormsContextStack contextStack = getContextStack(initialXPathContext);

        final Map<String, ValueRepresentation> inScopeVariables = contextStack.getCurrentBindingContext().getInScopeVariables();
        final Map<String, XPathVariable> variableDeclarations = new HashMap<String, XPathVariable>();
        if (inScopeVariables != null) {
            for (final String name: inScopeVariables.keySet()) {
                final XPathVariable variable = staticContext.declareVariable("", name);
                variableDeclarations.put(name, variable);
            }
        }

        // Create expression
        final PooledXPathExpression pooledXPathExpression
                = XPathCache.createPoolableXPathExpression(null, staticContext, xpathString, variableDeclarations, isAVT);

        // Set context items and position for use at runtime
        pooledXPathExpression.setContextItem(initialXPathContext.getContextItem(), initialXPathContext.getContextPosition());

        // Set variables for use at runtime
        pooledXPathExpression.setVariables(inScopeVariables);

        return pooledXPathExpression;
    }

    // See comments in Saxon Evaluate.java
    private IndependentContext staticContext;

    // The following copies all the StaticContext information into a new StaticContext
    public void copyStaticContextIfNeeded(ExpressionVisitor visitor) throws XPathException {
        // See same method in Saxon Evaluate.java
        if (staticContext == null) { // only do this once
            final StaticContext env = visitor.getStaticContext();
            super.checkArguments(visitor);

            final NamespaceResolver namespaceResolver = env.getNamespaceResolver();

            staticContext = new IndependentContext(env.getConfiguration());

            staticContext.setBaseURI(env.getBaseURI());
            staticContext.setImportedSchemaNamespaces(env.getImportedSchemaNamespaces());
            staticContext.setDefaultFunctionNamespace(env.getDefaultFunctionNamespace());
            staticContext.setDefaultElementNamespace(env.getDefaultElementNamespace());
            staticContext.setFunctionLibrary(env.getFunctionLibrary());

            for (Iterator iterator = namespaceResolver.iteratePrefixes(); iterator.hasNext();) {
                final String prefix = (String) iterator.next();
                if (!"".equals(prefix)) {
                    final String uri = namespaceResolver.getURIForPrefix(prefix, true);
                    staticContext.declareNamespace(prefix, uri);
                }
            }
        }
    }

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // By default, all XForms function invalidate the map. Subclasses can override this behavior. This ensures that
        // we do not, by default, produce invalid maps.
        pathMap.setInvalidated(true);
        return null;
    }

    // Access to Saxon's default implementation
    protected PathMap.PathMapNodeSet saxonAddToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        return super.addToPathMap(pathMap, pathMapNodeSet);
    }
}
