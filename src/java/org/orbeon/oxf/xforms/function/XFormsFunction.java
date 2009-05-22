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

import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
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
        XFormsServer.logger.warn("Cannot find pipeline context from static context. Creating new pipeline context.");
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
}
