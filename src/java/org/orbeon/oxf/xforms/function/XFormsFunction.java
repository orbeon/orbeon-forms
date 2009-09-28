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
package org.orbeon.oxf.xforms.function;

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.functions.SystemFunction;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.QNameValue;

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

    public XFormsModel getContainingModel(XPathContext xpathContext) {
        final Object functionContext = PooledXPathExpression.getFunctionContext(xpathContext);
        return (XFormsModel) ((functionContext instanceof XFormsModel) ? functionContext : null);
    }

    public XFormsControls getControls(XPathContext xpathContext) {
        final Object functionContext = PooledXPathExpression.getFunctionContext(xpathContext);
        if (functionContext instanceof XFormsControls) {
            // TODO: Deprecated
            return (XFormsControls) functionContext;
        } else if (functionContext instanceof XFormsModel) {
            // TODO: Deprecated
            final XFormsModel xformsModel = (XFormsModel) functionContext;
            return xformsModel.getContainingDocument().getControls();
        } else if (functionContext instanceof Context) {
            // The "right" way to do it
            return ((Context) functionContext).getControls();
        }
        return null;
    }

    public XBLContainer getXBLContainer(XPathContext xpathContext) {
        final Context functionContext = (XFormsFunction.Context) PooledXPathExpression.getFunctionContext(xpathContext);
        return functionContext.getXBLContainer();
    }

    public String getSourceEffectiveId(XPathContext xpathContext) {
        final Context functionContext = (XFormsFunction.Context) PooledXPathExpression.getFunctionContext(xpathContext);
        return functionContext.getSourceEffectiveId();
    }

    public XFormsContainingDocument getContainingDocument(XPathContext xpathContext) {
        final XFormsModel xformsModel = getContainingModel(xpathContext);
        if (xformsModel != null && xformsModel.getContainingDocument() != null)
            return xformsModel.getContainingDocument();
        final XFormsControls xformsControls = getControls(xpathContext);
        if (xformsControls != null)
            return xformsControls.getContainingDocument();

        return null;
    }

    public XFormsContextStack getContextStack(XPathContext xpathContext) {
        final Object functionContext = PooledXPathExpression.getFunctionContext(xpathContext);

        if (functionContext instanceof Context) {
            // Return specific context
            return ((Context) functionContext).getContextStack();
        } else if (functionContext instanceof XFormsControls) {
            // Return controls context
            return getControls(xpathContext).getContextStack();
        } else {
            return null;
        }
    }

    public PipelineContext getOrCreatePipelineContext() {
        final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        PipelineContext pipelineContext = (staticContext != null) ? staticContext.getPipelineContext() : null;

        // Found existing pipeline context
        if (pipelineContext != null)
            return pipelineContext;

        // Create new pipeline context
        XFormsContainingDocument.logWarningStatic("", "Cannot find pipeline context from static context. Creating new pipeline context.");
        return new PipelineContext();
    }

    public static class Context implements XPathCache.FunctionContext {

        private final XBLContainer container;
        private final XFormsModel containingModel;
        private final XFormsContextStack contextStack;

        private String sourceEffectiveId;

        public Context(XBLContainer container, XFormsContextStack contextStack) {
            this.container = container;
            this.containingModel = null;
            this.contextStack = contextStack;
        }

        public Context(XFormsModel containingModel, XFormsContextStack contextStack) {
            this.container = containingModel.getXBLContainer();
            this.containingModel = containingModel;
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

        public XFormsModel getContainingModel() {
            return containingModel;
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
}
