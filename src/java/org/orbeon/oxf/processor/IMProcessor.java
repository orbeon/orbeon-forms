/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.pipeline.PipelineReader;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.webapp.ServletContextExternalContext;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataElement;
import org.xml.sax.ContentHandler;
import ymsg.network.AccountLockedException;
import ymsg.network.DirectConnectionHandler;
import ymsg.network.LoginRefusedException;
import ymsg.network.Session;
import ymsg.network.event.SessionAdapter;
import ymsg.network.event.SessionEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IMProcessor extends ProcessorImpl {

    private static Logger logger = LoggerFactory.createLogger(IMProcessor.class);

    public static final String IM_NAMESPACE_URI = "http://www.orbeon.com/oxf/im";
    private static final int SEND_MESSAGE_MAX_TRY = 2;

    public IMProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    public void start(PipelineContext context) {
        try {
            // Get ExternalContext
            ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

            // Read config
            Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                    Config result = new Config();
                    Element configElement = readInputAsDOM4J(context, input).getRootElement();
                    result.login = configElement.element("login").getText();
                    result.password = configElement.element("password").getText();
                    Element onMessageReceivedElement = configElement.element("on-message-received");
                    if (onMessageReceivedElement != null)
                        result.onMessageReceived = onMessageReceivedElement.getText();
                    return result;
                }
            });

            // Get session
            for (int sendMessageTry = 0;;) {
                sendMessageTry++;
                Session session;
                synchronized (loginToSession) {
                    session = (Session) loginToSession.get(config.login);
                    if (session == null) {
                        // Create session
                        session = new Session(new DirectConnectionHandler());
                        if (config.onMessageReceived != null) {
                            // Create pipeline listening to new messages
                            PipelineProcessor onMessageReceived;
                            if (config.onMessageReceived.startsWith("#")) {
                                String inputName = config.onMessageReceived.substring(1);
                                final ProcessorInput input = getInputByName(inputName);
                                PipelineReader reader = new PipelineReader();
                                reader.createInput("pipeline").setOutput(new ProcessorImpl.ProcessorOutputImpl(getClass(), inputName) {
                                    protected void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                                        ((ProcessorImpl.ProcessorOutputImpl) input.getOutput()).readImpl(context, contentHandler);
                                    }

                                    protected Object getValidityImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                                        return ((ProcessorImpl.ProcessorOutputImpl) input.getOutput()).getValidityImpl(context);
                                    }

                                    protected OutputCacheKey getKeyImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                                        return ((ProcessorImpl.ProcessorOutputImpl) input.getOutput()).getKeyImpl(context);
                                    }
                                });
                                reader.start(context);
                                onMessageReceived = new PipelineProcessor(reader.getPipeline());

                            } else {
                                onMessageReceived = new PipelineProcessor();
                                URLGenerator urlGenerator = new URLGenerator(config.onMessageReceived);
                                PipelineUtils.connect(urlGenerator, "data", onMessageReceived, "config");
                            }
                            onMessageReceived.createInput("data");
                            session.addSessionListener(new SessionListener(onMessageReceived, externalContext));
                        }
                        session.login(config.login, config.password);
                        loginToSession.put(config.login, session);
                    }
                }

                // Get message
                Document dataDocument = readCacheInputAsDOM4J(context, INPUT_DATA);
                Element messageElement = dataDocument.getRootElement();
                try {
                    session.sendMessage(messageElement.element("to").getText(),
                            messageElement.element("body").getText());
                } catch (IOException e) {
                    if (sendMessageTry < SEND_MESSAGE_MAX_TRY) {
                        // Invalidate session and continue
                        synchronized (loginToSession) {
                            loginToSession.remove(config.login);
                        }
                        continue;
                    } else {
                        throw e;
                    }
                }

                // No exception: no need to try again
                break;
            }

        } catch (IOException e) {
            throw new OXFException(e);
        } catch (AccountLockedException e) {
            throw new OXFException(e);
        } catch (LoginRefusedException e) {
            throw new OXFException(e);
        }
    }

    private static class Config {
        String login;
        String password;
        String onMessageReceived;
    }

    /**
     * Gets called when we receive a message. When this happens, start a processor.
     */
    private static class SessionListener extends SessionAdapter {

        private Processor onMessageReceived;
        private ExternalContext externalContext;

        public SessionListener(Processor onMessageReceived, ExternalContext externalContext) {
            this.onMessageReceived = onMessageReceived;
            this.externalContext = externalContext;
        }

        public void messageReceived(SessionEvent ev) {
            synchronized (onMessageReceived) {
                // Create message
                final NonLazyUserDataElement msgElt = new NonLazyUserDataElement( "message" );
                Document messageDocument = new NonLazyUserDataDocument( msgElt );
                msgElt.addElement("from").addText(ev.getFrom());
                msgElt.addElement("body").addText(ev.getMessage());

                // Connect to processor
                final DOMGenerator domGenerator = new DOMGenerator
                    ( messageDocument, "IM message", DOMGenerator.ZeroValidity
                      , DOMGenerator.DefaultContext );
                ProcessorOutput output = domGenerator.createOutput("data");
                ProcessorInput input = onMessageReceived.getInputByName("data");
                output.setInput(input);
                input.setOutput(output);

                // Run processor
                try {
                    // The ExternalContext passed has limited visibility on the application context only
                    InitUtils.runProcessor(onMessageReceived, new ServletContextExternalContext(externalContext), new PipelineContext(), logger);
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        }
    }

    private static Map loginToSession = new HashMap();
}
