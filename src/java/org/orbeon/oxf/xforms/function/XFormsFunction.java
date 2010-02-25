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

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.functions.SystemFunction;
import org.orbeon.saxon.instruct.SlotManager;
import org.orbeon.saxon.om.NamespaceResolver;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.style.AttributeValueTemplate;
import org.orbeon.saxon.trans.DynamicError;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.QNameValue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
    public Expression preEvaluate(StaticContext env) {
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

    protected PipelineContext getOrCreatePipelineContext() {
        final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        PipelineContext pipelineContext = (staticContext != null) ? staticContext.getPipelineContext() : null;

        // Found existing pipeline context
        if (pipelineContext != null)
            return pipelineContext;

        // Create new pipeline context
        XFormsContainingDocument.logWarningStatic("", "Cannot find pipeline context from static context. Creating new pipeline context.");
        return new PipelineContext();
    }

    // NOTE: This is always constructed in XFormsContextStack
    public static class Context implements XPathCache.FunctionContext {

        private final XBLContainer container;
        private final XFormsContextStack contextStack;

        private String sourceEffectiveId;
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
                final Map namespaceMappings = getXBLContainer(xpathContext).getNamespaceMappings(contextStack.getCurrentBindingContext().getControlElement());

                // Get QName URI
                final String qNameURI = (String) namespaceMappings.get(prefix);
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
    protected PreparedExpression prepareExpression(XPathContext xpathContext, Expression parameterExpression, boolean isAVT) throws XPathException {

        final PreparedExpression preparedExpression = new PreparedExpression();

        final String xpathString;
        {
            final AtomicValue exprSource = (AtomicValue) parameterExpression.evaluateItem(xpathContext);
            xpathString = exprSource.getStringValue();
        }

        // Copy static context information
        final IndependentContext env = staticContext.copy();
        // We do staticContext.setFunctionLibrary(env.getFunctionLibrary()) above, so why would we need this?
//        env.setFunctionLibrary(getExecutable().getFunctionLibrary());
        preparedExpression.expStaticContext = env;

        // Propagate in-scope variable definitions since they are not copied automatically
        final XFormsContextStack contextStack = getContextStack(xpathContext);
        preparedExpression.inScopeVariables = contextStack.getCurrentBindingContext().getInScopeVariables();
        preparedExpression.variables = new HashMap<String, Variable>();
        {
            if (preparedExpression.inScopeVariables != null) {
                for (final Map.Entry<String, ValueRepresentation> currentEntry: preparedExpression.inScopeVariables.entrySet()) {
                    final String name = currentEntry.getKey();

                    final Variable variable = env.declareVariable(name);
                    variable.setUseStack(true);// "Indicate that values of variables are to be found on the stack, not in the Variable object itself"

                    preparedExpression.variables.put(name, variable);
                }
            }
        }

        // Create expression
        Expression expression;
        try {
            if (isAVT){
                expression = AttributeValueTemplate.make(xpathString, -1, env);
            } else {
                expression = ExpressionTool.make(xpathString, env, 0, Token.EOF, 1);
            }
        } catch (XPathException e) {
            final String name = xpathContext.getNamePool().getDisplayName(getFunctionNameCode());
            final DynamicError err = new DynamicError("Static error in XPath expression supplied to " + name + ": " +
                    e.getMessage().trim());
            err.setXPathContext(xpathContext);
            throw err;
        }

        // Prepare expression
        expression = expression.typeCheck(env, Type.ITEM_TYPE);
        preparedExpression.stackFrameMap = env.getStackFrameMap();
        ExpressionTool.allocateSlots(expression, preparedExpression.stackFrameMap.getNumberOfVariables(), preparedExpression.stackFrameMap);
        preparedExpression.expression = expression;

        return preparedExpression;
    }
    
    public static class PreparedExpression implements java.io.Serializable {
        public IndependentContext expStaticContext;
        public Expression expression;
        public Map<String, ValueRepresentation> inScopeVariables;
        public Map<String, Variable> variables;
        public SlotManager stackFrameMap;
    }

    // See comments in Saxon Evaluate.java
    private IndependentContext staticContext;

    // The following copies all the StaticContext information into a new StaticContext
    public void copyStaticContextIfNeeded(StaticContext env) throws XPathException {
        // See same method in Saxon Evaluate.java
        if (staticContext == null) { // only do this once
            super.checkArguments(env);

            final NamespaceResolver namespaceResolver = env.getNamespaceResolver();

            staticContext = new IndependentContext(env.getConfiguration());

            staticContext.setBaseURI(env.getBaseURI());
            staticContext.setImportedSchemaNamespaces(env.getImportedSchemaNamespaces());
            staticContext.setDefaultFunctionNamespace(env.getDefaultFunctionNamespace());
            staticContext.setDefaultElementNamespace(env.getNamePool().getURIFromURICode(env.getDefaultElementNamespace()));
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

    protected XPathContextMajor prepareXPathContext(XPathContext xpathContext, PreparedExpression preparedExpression) {
        final XPathContextMajor newXPathContext = xpathContext.newCleanContext();
        newXPathContext.openStackFrame(preparedExpression.stackFrameMap);
        newXPathContext.setCurrentIterator(xpathContext.getCurrentIterator());

        // Set variable values
        if (preparedExpression.variables != null) {
            for (final Map.Entry<String, Variable> entry: preparedExpression.variables.entrySet()) {
                final String name = entry.getKey();
                final Variable variable = entry.getValue();

                final Object object = preparedExpression.inScopeVariables.get(name);
                if (object != null) {
                    // Convert Java object to Saxon object
                    final ValueRepresentation valueRepresentation = XFormsUtils.convertJavaObjectToSaxonObject(object);
                    newXPathContext.setLocalVariable(variable.getLocalSlotNumber(), valueRepresentation);
                }
            }
        }
        return newXPathContext;
    }
}
