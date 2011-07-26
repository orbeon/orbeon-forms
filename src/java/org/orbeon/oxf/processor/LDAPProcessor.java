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
package org.orbeon.oxf.processor;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;

import javax.naming.*;
import javax.naming.directory.Attribute;
import javax.naming.directory.*;
import java.util.*;

public class LDAPProcessor extends ProcessorImpl {
    static private Logger logger = LoggerFactory.createLogger(LDAPProcessor.class);

    public static final String INPUT_FILTER = "filter";
    public static final String LDAP_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/ldap/config";
    public static final String LDAP_FILTER_NAMESPACE_URI = "http://orbeon.org/oxf/ldap/filter";

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 389;
    public static final String LDAP_VERSION = "java.naming.ldap.version";
    public static final String DEFAULT_LDAP_VERSION = "3";

    //private static final String CTX_ENV = "java.naming.ldap.attributes.binary";
    private static final String DEFAULT_CTX = "com.sun.jndi.ldap.LdapCtxFactory";

    public static final String HOST_PROPERTY = "host";
    public static final String PORT_PROPERTY = "port";
    public static final String BIND_PROPERTY = "bind-dn";
    public static final String PASSWORD_PROPERTY = "password";
    public static final String PROTOCOL_PROPERTY = "protocol";
    public static final String SCOPE_PROPERTY = "scope";


    public LDAPProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, LDAP_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_FILTER, LDAP_FILTER_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(LDAPProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    // Read configuration
                    Config config = (Config) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                        public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                            Config config = new Config();
                            Document doc = readInputAsDOM4J(context, input);

                            // Try local configuration first
                            String host = XPathUtils.selectStringValueNormalize(doc, "/config/host");
                            Integer port = XPathUtils.selectIntegerValue(doc, "/config/port");
                            String bindDN = XPathUtils.selectStringValueNormalize(doc, "/config/bind-dn");
                            String password = XPathUtils.selectStringValueNormalize(doc, "/config/password");
                            String protocol = XPathUtils.selectStringValueNormalize(doc, "/config/protocol");
                            String referral = XPathUtils.selectStringValueNormalize(doc, "/config/referral ");
                            String scope = XPathUtils.selectStringValueNormalize(doc, "/config/scope");

                            //logger.info("Referral="+referral);

                            // Override with properties if needed
                            config.setHost(host != null ? host : getPropertySet().getString(HOST_PROPERTY));
                            config.setPort(port != null ? port.intValue() : getPropertySet().getInteger(PORT_PROPERTY).intValue());
                            config.setBindDN(bindDN != null ? bindDN : getPropertySet().getString(BIND_PROPERTY));
                            config.setPassword(password != null ? password : getPropertySet().getString(PASSWORD_PROPERTY));
                            config.setProtocol(protocol != null ? protocol : getPropertySet().getString(PROTOCOL_PROPERTY));
                            config.setScope(scope != null ? scope: getPropertySet().getString(SCOPE_PROPERTY));

                            // If not set use providers default. Valid values are follow, ignore, throw
                            if (referral != null){
                              config.setReferral(referral);
                            }

                            // The password and bind DN are allowed to be blank
                            if (password == null)
                                config.setPassword("");
                            if (bindDN == null)
                                config.setBindDN("");

                            config.setRootDN(XPathUtils.selectStringValueNormalize(doc, "/config/root-dn"));

                            for (Iterator i = XPathUtils.selectIterator(doc, "/config/attribute"); i.hasNext();) {
                                Element e = (Element) i.next();
                                config.addAttribute(e.getTextTrim());
                            }
                            return config;
                        }
                    });

                    Command command = (Command) readCacheInputAsObject(context, getInputByName(INPUT_FILTER), new CacheableInputReader() {
                        public Object read(PipelineContext context, ProcessorInput input) {
                            Command command;
                            Document filterDoc = readInputAsDOM4J(context, input);
                            String filterNodeName = filterDoc.getRootElement().getName();

                            if ("update".equals(filterNodeName)) {
                                command = new Update();
                                command.setName(XPathUtils.selectStringValue(filterDoc, "/update/name"));
                                parseAttributes(filterDoc, "/update/attribute", (Update) command);

                            } else if ("add".equals(filterNodeName)) {
                                command = new Add();
                                command.setName(XPathUtils.selectStringValue(filterDoc, "/add/name"));
                                parseAttributes(filterDoc, "/add/attribute", (Add) command);

                            } else if ("delete".equals(filterNodeName)) {
                                command = new Delete();
                                command.setName(XPathUtils.selectStringValue(filterDoc, "/delete/name"));

                            } else if ("filter".equals(filterNodeName)) {
                                command = new Search();
                                command.setName(XPathUtils.selectStringValueNormalize(filterDoc, "/filter"));
                            } else {
                                throw new OXFException("Wrong command: use filter or update");
                            }

                            return command;
                        }
                    });

                    if(logger.isDebugEnabled())
                        logger.debug("LDAP Command: "+command.toString());

                    DirContext ctx = connect(config);

                    if (command instanceof Update) {
                        update(ctx, (Update) command);
                        outputSuccess(xmlReceiver, "update");
                    } else if (command instanceof Add) {
                        add(ctx, (Add) command);
                        outputSuccess(xmlReceiver, "add");
                    } else if (command instanceof Delete) {
                        delete(ctx, (Delete) command);
                        outputSuccess(xmlReceiver, "delete");
                    } else if (command instanceof Search) {
                        // There was incorrect code here earlier testing on instanceof String[], which broke stuff. For
                        // now assume all attrs are strings.
                        final List attributesList = config.getAttributes();
                        final String[] attrs = new String[attributesList.size()];
                        attributesList.toArray(attrs);

                        List results = search(ctx, config.getRootDN(), config.getScope(), command.getName(), attrs);
                        serialize(results, config, xmlReceiver);
                    }

                    disconnect(ctx);
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private void parseAttributes(Node filterDoc, String attributeXPath, CommandWithAttributes command) {
        for (Iterator i = XPathUtils.selectIterator(filterDoc, attributeXPath); i.hasNext();) {
            Node curAttr = (Node) i.next();
            String name = XPathUtils.selectStringValue(curAttr, "name");
            List values = new ArrayList();
            for (Iterator j = XPathUtils.selectIterator(curAttr, "value"); j.hasNext();) {
                String value = ((Node) j.next()).getText();
                values.add(value);
            }
            command.addAttribute(name, values);
        }
    }

    private void update(DirContext ctx, Update update) {
        try {
            ctx.modifyAttributes(update.getName(), DirContext.REPLACE_ATTRIBUTE, update.getAttributes());
        } catch (NamingException e) {
            throw new OXFException("LDAP Update Failed", e);
        }
    }


    private void add(DirContext ctx, Add add) {
        try {
            ctx.createSubcontext(add.getName(), add.getAttributes());
        } catch (NamingException e) {
            throw new OXFException("LDAP Add Failed", e);
        }
    }

    private void delete(DirContext ctx, Delete delete) {
        try {
            ctx.destroySubcontext(delete.getName());
        } catch (NamingException e) {
            throw new OXFException("LDAP Delete Failed", e);
        }
    }

    private List search(DirContext ctx, String rootDN, String scope, String filter, String[] attributes) {
        try {
            List listResults = new ArrayList();
            SearchControls constraints = new SearchControls();

            constraints.setSearchScope(convertSearchScope(scope));
            constraints.setReturningAttributes(attributes);

            try {
                if (scope != null && scope.toUpperCase().equals("ALLLEVELS")) {
                    String[] levels = rootDN.split(",");
                    for (int i = 0; i < levels.length; i++) {
                        String[] currentLevels = new String[levels.length - i];
                        System.arraycopy(levels, i, currentLevels, 0, levels.length - i);
                        String levelRootDN = StringUtils.join(currentLevels, ",");
                        if (logger.isDebugEnabled())
                            logger.debug("LDAP Search on level " + levelRootDN);
                        NamingEnumeration results = ctx.search(levelRootDN, filter, constraints);
                        for (; results.hasMore(); ) {
                            SearchResult result = (SearchResult) results.next();
                            listResults.add(result);
                        }
                    }
                } else {
                    NamingEnumeration results = ctx.search(rootDN, filter, constraints);
                    for (; results.hasMore(); ) {
                        SearchResult result = (SearchResult) results.next();
                        listResults.add(result);
                    }
                }

            } catch (NameNotFoundException e) {
                // for example in case of ALLLEVELS scope, if the LDAP database suffix has more than one component, the last iteration would result in NameNotFoundException
            }
            return listResults;
        } catch (NamingException e) {
            throw new OXFException("LDAP Search Failed", e);
        }
    }


    private void serialize(List results, Config config, ContentHandler ch) {
        try {
            ch.startDocument();
            ch.startElement("", "results", "results", XMLUtils.EMPTY_ATTRIBUTES);
            for (Iterator i = results.iterator(); i.hasNext();) {
                SearchResult sr = (SearchResult) i.next();

                ch.startElement("", "result", "result", XMLUtils.EMPTY_ATTRIBUTES);
                addElement(ch, "name", sr.getName());
                try {
                  addElement(ch, "fullname", sr.getNameInNamespace());
                } catch (UnsupportedOperationException e) {
                    // This seems to be the only  way to know if sr contains a name!
                }
                Attributes attr = sr.getAttributes();
                NamingEnumeration attrEn = attr.getAll();
                while (attrEn.hasMoreElements()) {
                    Attribute a = (Attribute) attrEn.next();
                    if (config.getAttributes().isEmpty() ||
                            config.getAttributes().contains(a.getID())) {
                        ch.startElement("", "attribute", "attribute", XMLUtils.EMPTY_ATTRIBUTES);
                        addElement(ch, "name", a.getID());
                        NamingEnumeration aEn = a.getAll();
                        while (aEn.hasMoreElements()) {
                            Object o = aEn.next();
                            addElement(ch, "value", o.toString());
                        }
                        ch.endElement("", "attribute", "attribute");
                    }
                }
                ch.endElement("", "result", "result");
            }
            ch.endElement("", "results", "results");
            ch.endDocument();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private void outputSuccess(ContentHandler ch, String operationName) {
        try {
            ch.startDocument();
            addElement(ch, operationName, "success");
            ch.endDocument();
        }catch(Exception e) {
            throw new OXFException(e);
        }
    }

    private DirContext connect(Config config) {
        try {
            Properties env = new Properties();

            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, config.getBindDN());
            env.put(Context.SECURITY_CREDENTIALS, config.getPassword());
            env.put(LDAP_VERSION, DEFAULT_LDAP_VERSION);
            env.put(Context.INITIAL_CONTEXT_FACTORY, DEFAULT_CTX);
            env.put(Context.PROVIDER_URL, "ldap://" + config.getHost() + ":" + config.getPort());
            if (config.getReferral() != null){
              env.put(Context.REFERRAL, config.getReferral());
            }

            if (config.getProtocol() != null)
                env.put(Context.SECURITY_PROTOCOL, config.getProtocol());
            env.put("com.sun.jndi.ldap.connect.pool", "true");

            return new InitialDirContext(env);
        } catch (NamingException e) {
            throw new OXFException("LDAP connect Failed", e);
        }
    }

    private void disconnect(DirContext ctx) {
        try {
            if (ctx != null) ctx.close();
        } catch (NamingException e) {
            throw new OXFException("LDAP disconnect Failed", e);
        }
    }

    private void addElement(ContentHandler contentHandler, String name, String value)
            throws Exception {
        if (value != null) {
            contentHandler.startElement("", name, name, XMLUtils.EMPTY_ATTRIBUTES);
            addString(contentHandler, value);
            contentHandler.endElement("", name, name);
        }
    }

    private void addString(ContentHandler contentHandler, String string)
            throws Exception {
        char[] charArray = string.toCharArray();
        contentHandler.characters(charArray, 0, charArray.length);
    }

    private int convertSearchScope(String scope) {
        if (scope != null && scope.toUpperCase().equals("SUBTREE")) {
            return SearchControls.SUBTREE_SCOPE;
        } else if (scope != null && scope.toUpperCase().equals("OBJECT")) {
            return SearchControls.OBJECT_SCOPE;
        } else if (scope != null && (scope.toUpperCase().equals("ALLLEVELS") || scope.toUpperCase().equals("ONELEVEL"))) {
            return SearchControls.ONELEVEL_SCOPE;
        } else {
            return SearchControls.SUBTREE_SCOPE;
        }
    }

    private static class Config {
        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private String bindDN;
        private String password;
        private String rootDN;
        private String protocol;
        private String referral;
        private String scope;

        private List attributes = new ArrayList();

        public String getBindDN() {
            return bindDN;
        }

        public void setBindDN(String bindDN) {
            this.bindDN = bindDN;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getRootDN() {
            return rootDN;
        }

        public void setRootDN(String rootDN) {
            this.rootDN = rootDN;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getReferral() {
            return referral;
        }

        public void setReferral(String referral) {
            this.referral = referral;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public List getAttributes() {
            return attributes;
        }

        public void addAttribute(String attr) {
            this.attributes.add(attr);
        }

        public String toString() {
            return "Host: "+host + " Port: " + port + " Bind DN: "+bindDN + " password: "+password+" root: "+rootDN;
        }
    }


    private abstract static class Command {
        protected String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    private abstract static class CommandWithAttributes extends Command {
        protected Attributes attributes = new BasicAttributes(true);

        public Attributes getAttributes() {
            return attributes;
        }

        public void addAttribute(String name, List values) {
            BasicAttribute ba = new BasicAttribute(name);
            for (Iterator i = values.iterator(); i.hasNext();)
                ba.add((String) i.next());
            attributes.put(ba);
        }

    }

    private static class Search extends Command {
        public String toString() {
            return "Search Command: filter = " + name;
        }
    }

    private static class Update extends CommandWithAttributes {

        public String toString() {
            return "Update Command: name = " + name + " attributes = " + attributes.toString();
        }
    }

    private static class Add extends CommandWithAttributes {

        public String toString() {
            return "Add Command: name = " + name + " attributes = " + attributes.toString();
        }
    }

    private static class Delete extends Command {

        public String toString() {
            return "Delete Command: name = " + name;
        }

    }

}