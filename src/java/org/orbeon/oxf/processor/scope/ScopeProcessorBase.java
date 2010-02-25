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
package org.orbeon.oxf.processor.scope;

import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorUtils;

public abstract class ScopeProcessorBase extends ProcessorImpl {

    public static final int REQUEST_CONTEXT = 0;
    public static final int SESSION_CONTEXT = 1;
    public static final int APPLICATION_CONTEXT = 2;

    public static final String APPLICATION_XML = "application/xml";
    public static final String TEXT_PLAIN = "text/plain";

    public static final String SCOPE_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/schemas/scope-config";

    protected ContextConfig readConfig(PipelineContext context) {
        return (ContextConfig) readCacheInputAsObject(context, getInputByName(ProcessorImpl.INPUT_CONFIG), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                final Element rootElement = readInputAsDOM4J(context, input).getRootElement();
                final String contextName = rootElement.element("scope").getStringValue();
                final Element sessionScopeElement = rootElement.element("session-scope");
                final Element contentTypeElement = rootElement.element("content-type");
                final String contentType = (contentTypeElement == null) ? APPLICATION_XML : (TEXT_PLAIN.equals(contentTypeElement.getStringValue())) ? TEXT_PLAIN : null ;
                final String sessionScopeValue = (sessionScopeElement == null) ? null : sessionScopeElement.getStringValue();
                return new ContextConfig("request".equals(contextName) ? REQUEST_CONTEXT
                        : "session".equals(contextName) ? SESSION_CONTEXT
                        : "application".equals(contextName) ? APPLICATION_CONTEXT
                        : -1,
                        "application".equals(sessionScopeValue) ? ExternalContext.Session.APPLICATION_SCOPE : "portlet".equals(sessionScopeValue) ? ExternalContext.Session.PORTLET_SCOPE : -1,
                        rootElement.element("key").getStringValue(),
                        contentType,
                        ProcessorUtils.selectBooleanValue(rootElement, "/*/test-ignore-stored-key-validity", false));
            }
        });
    }

    protected static class ContextConfig {
        private final int contextType;
        private final int sessionScope;
        private final String key;
        private final boolean testIgnoreStoredKeyValidity;
        private final String contentType;

        public ContextConfig(int contextType, int sessionScope, String key, String contentType, boolean testIgnoreInternalKeyValidity) {
            this.contextType = contextType;
            this.sessionScope = sessionScope;
            this.key = key;
            this.contentType = contentType;
            this.testIgnoreStoredKeyValidity = testIgnoreInternalKeyValidity;
        }

        public int getContextType() {
            return contextType;
        }

        public int getSessionScope() {
            return sessionScope;
        }

        public String getKey() {
            return key;
        }

        public String getContentType() {
            return contentType;
        }

        public boolean isTestIgnoreStoredKeyValidity() {
            return testIgnoreStoredKeyValidity;
        }
    }
}
