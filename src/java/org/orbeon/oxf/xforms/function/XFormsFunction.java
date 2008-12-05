/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function;

import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.functions.SystemFunction;

/**
 * Base class for all XForms functions.
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
            // Deprecated
            return (XFormsControls) functionContext;
        } else if (functionContext instanceof XFormsModel) {
            // Deprecated
            final XFormsModel xformsModel = (XFormsModel) functionContext;
            return xformsModel.getContainingDocument().getControls();
        } else if (functionContext instanceof Context) {
            // The "right" way to do it
            return ((Context) functionContext).getControls();
        }
        return null;
    }

    public XFormsContainer getContainer(XPathContext xpathContext) {
        final Context functionContext = (XFormsFunction.Context) PooledXPathExpression.getFunctionContext(xpathContext);
        return functionContext.getContainer();
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
        XFormsServer.logger.warn("Cannot find pipeline context from static context. Creating new pipeline context.");
        return new PipelineContext();
    }

    public static class Context implements XPathCache.FunctionContext {

        private XFormsContainer container;
        private XFormsModel containingModel;
        private XFormsContextStack contextStack;

        public Context(XFormsContainer container, XFormsContextStack contextStack) {
            this.container = container;
            this.contextStack = contextStack;
        }

        public Context(XFormsModel containingModel, XFormsContextStack contextStack) {
            this.containingModel = containingModel;
            this.contextStack = contextStack;
        }

        public XFormsContainer getContainer() {
            return (container != null) ? container : containingModel.getContainer();
        }

        public XFormsContainingDocument getContainingDocument() {
            return (container != null) ? container.getContainingDocument() : containingModel.getContainingDocument();
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
    }
}
