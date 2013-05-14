/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
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
package org.orbeon.oxf.processor.generator;

import org.apache.struts.Globals;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.util.MessageResources;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.cache.SimpleOutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TeeContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.util.Base64;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;


public class StrutsActionErrorsGenerator extends org.orbeon.oxf.processor.ProcessorImpl {

    public static final String ACTION_ERRORS_ELEMENT = "errors";
    public static final String ACTION_ERROR_ELEMENT = "error";
    public static final String ACTION_ERROR_PROPERTY_ATTRIBUTE = "property";

    public static final String DEFAULT_MESSAGE = "Key {0} not specified in application resources";

    public static final String ERRORS_MESSAGE_BUNDLE = "bundle";

    private static Locale defaultLocale = Locale.getDefault();
    private static MessageFormat DEFAULT_MESSAGE_FORMAT = new MessageFormat(DEFAULT_MESSAGE);
    private static Long INITIAL_VALIDITY = new Long(0);

    // Very basic cache; in general, we can assume that this processor doesn't cache
    private static String latestDigest;
    private static Long latestValidity = INITIAL_VALIDITY;

    public StrutsActionErrorsGenerator() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    protected void readOutput(PipelineContext pipelineContext, ContentHandler contentHandler) {
        ExternalContext external = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (!(external instanceof ServletExternalContext))
            throw new OXFException("Orbeon Forms is not running as a servlet. This is not supported.");

        HttpServletRequest request = (HttpServletRequest) external.getNativeRequest();
        if (request == null)
            throw new OXFException("HTTP Request not found in context");

        ServletContext servletContext = (ServletContext) external.getNativeContext();
        if (servletContext == null)
            throw new OXFException("Servlet Context not found in context");

        try {
            contentHandler.startDocument();
            contentHandler.startElement("", ACTION_ERRORS_ELEMENT, ACTION_ERRORS_ELEMENT, XMLUtils.EMPTY_ATTRIBUTES);

            ActionErrors errors = (ActionErrors) request.getAttribute(Globals.ERROR_KEY);

            if (errors != null) {
                Locale locale = (Locale) request.getSession().getAttribute(Globals.LOCALE_KEY);

                if (locale == null)
                    locale = defaultLocale;

                String bundle = getPropertySet().getString(ERRORS_MESSAGE_BUNDLE);
                if (bundle == null) {
                    bundle = Globals.MESSAGES_KEY;
                }

                MessageResources messages = (MessageResources) request.getAttribute(bundle);
                if (messages == null) {
                    messages = (MessageResources) servletContext.getAttribute(bundle);
                }

                if (messages == null) {
                    throw new OXFException("Can't load message resource from bundle: " + bundle);
                }

                String message = null;
                message = messages.getMessage(locale, "errors.header");
                if (message != null) {
                    AttributesImpl atts = new AttributesImpl();
                    atts.addAttribute("", ACTION_ERROR_PROPERTY_ATTRIBUTE, ACTION_ERROR_PROPERTY_ATTRIBUTE, "CDATA", "errors.header");
                    contentHandler.startElement("", ACTION_ERROR_ELEMENT, ACTION_ERROR_ELEMENT, atts);
                    contentHandler.characters(message.toCharArray(), 0, message.length());
                    contentHandler.endElement("", ACTION_ERROR_ELEMENT, ACTION_ERROR_ELEMENT);
                }

                for (Iterator i = errors.properties(); i.hasNext();) {
                    String property = (String) i.next();
                    for (Iterator j = errors.get(property); j.hasNext();) {
                        ActionError error = (ActionError) j.next();
                        AttributesImpl atts = new AttributesImpl();
                        atts.addAttribute("", ACTION_ERROR_PROPERTY_ATTRIBUTE, ACTION_ERROR_PROPERTY_ATTRIBUTE, "CDATA", property);
                        contentHandler.startElement("", ACTION_ERROR_ELEMENT, ACTION_ERROR_ELEMENT, atts);
                        message = messages.getMessage(locale, error.getKey(), error.getValues());
                        if (message == null) {
                            Object[] objs = {error.getKey()};
                            message = DEFAULT_MESSAGE_FORMAT.format(objs);
                        }
                        contentHandler.characters(message.toCharArray(), 0, message.length());
                        contentHandler.endElement("", ACTION_ERROR_ELEMENT, ACTION_ERROR_ELEMENT);
                    }
                }

                message = messages.getMessage(locale, "errors.footer");
                if (message != null) {
                    AttributesImpl atts = new AttributesImpl();
                    atts.addAttribute("", ACTION_ERROR_PROPERTY_ATTRIBUTE, ACTION_ERROR_PROPERTY_ATTRIBUTE, "CDATA", "errors.footer");
                    contentHandler.startElement("", ACTION_ERROR_ELEMENT, ACTION_ERROR_ELEMENT, atts);
                    contentHandler.characters(message.toCharArray(), 0, message.length());
                    contentHandler.endElement("", ACTION_ERROR_ELEMENT, ACTION_ERROR_ELEMENT);
                }


            }

            contentHandler.endElement("", ACTION_ERRORS_ELEMENT, ACTION_ERRORS_ELEMENT);
            contentHandler.endDocument();
        } catch (Exception e) {
            throw new OXFException(e);
        }

    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    State state = (State) getState(context);
                    if (state.store == null)
                        computeState(context, state);

                    state.store.replay(contentHandler);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            protected Object getValidityImpl(PipelineContext context) {
                State state = (State) getState(context);
                if (state.outputCacheKey == null)
                    computeState(context, state);
                return state.validity;
            }

            protected OutputCacheKey getKeyImpl(PipelineContext context) {
                State state = (State) getState(context);
                if (state.outputCacheKey == null)
                   computeState(context, state);

                return state.outputCacheKey;
            }

            private void computeState(PipelineContext context, State state) {
                state.store = new SAXStore();
                XMLUtils.DigestContentHandler dch = new XMLUtils.DigestContentHandler("MD5");
                TeeContentHandler tee = new TeeContentHandler(Arrays.asList(new Object[]{state.store, dch}));

                readOutput(context, tee);
                String digest = Base64.encode(dch.getResult());

                // Make sure that validity extraction is thread-safe
                synchronized (StrutsActionErrorsGenerator.class) {
                    if (!digest.equals(latestDigest)) {
                        latestDigest = digest;
                        latestValidity = new Long(System.currentTimeMillis());
                    }
                    state.validity = latestValidity;
                }

                state.outputCacheKey = new SimpleOutputCacheKey
                    ( StrutsActionErrorsGenerator.class, OUTPUT_DATA, "constant" );
             }
        };

        addOutput(name, output);
        return output;
    }


    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    /**
     * We store in the state the request document (output of this processor) and
     * its key. This information is stored to be reused by readImpl() after a
     * getKeyImpl() in the same pipeline context, or vice versa.
     */
    private static class State {
        public OutputCacheKey outputCacheKey;
        public Long validity;
        public SAXStore store;
    }

//    private String escape(String string) {
//
//        if ((string == null) || (string.indexOf('\'') < 0))
//            return (string);
//        int n = string.length();
//        StringBuffer sb = new StringBuffer(n);
//        for (int i = 0; i < n; i++) {
//            char ch = string.charAt(i);
//            if (ch == '\'')
//                sb.append('\'');
//            sb.append(ch);
//        }
//        return (sb.toString());
//
//    }

}
