/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.processor.XQuery;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.*;
import org.orbeon.saxon.xqj.SaxonXQDataSource;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.xquery.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

/**
 * XQuery client/server processor, typically based on XQJ.
 */


public class XQueryProcessor extends ProcessorImpl {


    public static final String XQUERY_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/xquery";

    private static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(XQueryProcessor.class);

    private static HashMap<String, String> knownImplementations = initKnownImplementations();

    private static HashMap<String, String> initKnownImplementations() {
        HashMap<String, String> implementations = new HashMap<String, String>();
        implementations.put("exist", "net.xqj.exist.ExistXQDataSource");
        implementations.put("oracle", "oracle.xquery.xqj.OXQDataSource");
        implementations.put("saxon", "org.orbeon.saxon.xqj.SaxonXQDataSource");
        return implementations;
    }

    private static HashMap<String, String> knownJDBCImplementations = initKnownJDBCImplementations();

    private static HashMap<String, String> initKnownJDBCImplementations() {
        HashMap<String, String> implementations = new HashMap<String, String>();
        implementations.put("oracle", "oracle.jdbc.OracleDriver");
        return implementations;
    }


    public XQueryProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, XQUERY_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(XQueryProcessor.this, name) {
            public void readImpl(final PipelineContext pipelineContext, XMLReceiver xmlReceiver) {

                class ConfigContainer extends ObjectReceiver {
                    public ConfigContainer() {
                    }

                    public Config config;

                    class Config extends ObjectReceiver {
                        public Config() {
                        }

                        public String vendor;
                        public String implementation;
                        public String info;
                        public String username;
                        public String password;
                        public JDBC jdbc;
                        public Vector<NameValuePair> property = new Vector<NameValuePair>();
                        public String query;
                        public Vector<NameValuePair> parameter = new Vector<NameValuePair>();

                        class NameValuePair extends ObjectReceiver {
                            public NameValuePair() {
                            }

                            public String name;
                            public String value;
                        }

                        class JDBC extends ObjectReceiver {
                            public JDBC() {
                            }

                            public String url;
                            public String implementation;

                        }

                        String getImplementation() {
                            if (vendor != null) {
                                return knownImplementations.get(vendor);
                            }
                            return implementation;
                        }

                        public String getJDBCImplementation() {
                            if (jdbc.implementation != null) {
                                return jdbc.implementation;
                            }
                            return knownJDBCImplementations.get(vendor);
                        }

                    }
                }

                final ConfigContainer container = new ConfigContainer();
                readInputAsSAX(pipelineContext, INPUT_CONFIG, container);
                final ConfigContainer.Config config = container.config;
                XMLReceiverHelper helper = new XMLReceiverHelper(xmlReceiver);
                try {
                    if ("oracle".equals(config.vendor)) {
                        // Use JDBC as a workaround until we find out how to set the connection info in XQJ
                        // (see https://forums.oracle.com/forums/thread.jspa?messageID=10338407#10338407)
                        Driver driver = (Driver) Class.forName(config.getJDBCImplementation()).newInstance();
                        Connection conn = driver.connect(config.jdbc.url, null);
                        StringBuilder xquery = new StringBuilder("SELECT * from XMLTable('" + config.query.replaceAll("'", "''") + "' ");
                        Iterator<ConfigContainer.Config.NameValuePair> iter = config.parameter.iterator();
                        int i = 1;
                        while (iter.hasNext()) {
                            ConfigContainer.Config.NameValuePair parameter = iter.next();
                            xquery.append((i == 1 ? "PASSING " : ", ") + ":" + i + " AS \"" + parameter.name + "\" ");
                            i++;
                        }
                        xquery.append(")");
                        logger.debug("XQuery: " + xquery.toString());
                        PreparedStatement statement = conn.prepareStatement(xquery.toString());
                        i = 1;
                        iter = config.parameter.iterator();
                        while (iter.hasNext()) {
                            ConfigContainer.Config.NameValuePair parameter = iter.next();
                            statement.setString(i, parameter.value);
                            i++;
                        }
                        ResultSet rs = statement.executeQuery();
                        helper.startDocument();
                        helper.startElement("results");

                        while (rs.next()) {
                            for (i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                                helper.startElement("result");
                                // This used to use SQLXML.getString() but "The behavior of this method is the same as
                                // ResultSet.getString() when the designated column of the ResultSet has a
                                // type java.sql.Types of SQLXML." For 1.5 compatibility we use getString() instead.
                                String sqlxml = rs.getString(i);
                                if (sqlxml != null) {
                                    XMLParsing.parseDocumentFragment(sqlxml, xmlReceiver);
                                }
                            }
                            helper.endElement();
                        }

                        helper.endElement();
                        helper.endDocument();
                        rs.close();
                        statement.close();
                        conn.close();
                    } else {
                        // Use XQJ
                        XQDataSource xqs = (XQDataSource) Class.forName(config.getImplementation()).newInstance();
                        if (config.info != null) {
                            helper.startDocument();
                            helper.startElement("info");
                            helper.element("vendor", config.vendor == null ? "" : config.vendor);
                            helper.element("implementation", config.getImplementation());
                            Class[] interfaces = xqs.getClass().getInterfaces();
                            for (int i = 0; i < interfaces.length; i++) {
                                helper.element("implements", interfaces[i].getCanonicalName());
                            }
                            Constructor[] constructors = xqs.getClass().getConstructors();
                            for (int i = 0; i < constructors.length; i++) {
                                helper.element("constructor", constructors[i].toString());
                            }
                            Method[] methods = xqs.getClass().getMethods();
                            for (int i = 0; i < methods.length; i++) {
                                helper.element("method", methods[i].toString());
                            }
                            String[] names = xqs.getSupportedPropertyNames();
                            helper.startElement("supported-properties");
                            for (int i = 0; i < names.length; i++) {
                                helper.element("property", names[i]);
                            }
                            helper.endElement();
                            helper.endElement();
                            helper.endDocument();
                        } else {
                            Iterator<ConfigContainer.Config.NameValuePair> iter = config.property.iterator();
                            while (iter.hasNext()) {
                                ConfigContainer.Config.NameValuePair property = iter.next();
                                xqs.setProperty(property.name, property.value);
                            }
                            if (SaxonXQDataSource.class.isInstance(xqs)) {
                                // For Saxon: setup a URI resolver to support the "input:" scheme
                                final TransformerURIResolver resolver = new TransformerURIResolver(XQueryProcessor.this, pipelineContext, INPUT_CONFIG, ParserConfiguration.Plain());
                                ((SaxonXQDataSource) xqs).getConfiguration().setURIResolver(resolver);
                            }
                            XQConnection conn;
                            if (config.jdbc != null) {
                                Driver driver = (Driver) Class.forName(config.getJDBCImplementation()).newInstance();
                                Connection jdbcConn = driver.connect(config.jdbc.url, null);
                                // Class.forName(config.getJDBCImplementation());
                                // Connection jdbcConn = DriverManager.getConnection(config.jdbc.url);
                                conn = xqs.getConnection(jdbcConn);
                            } else if (config.username != null) {
                                conn = xqs.getConnection(config.username, config.password);
                            } else {
                                conn = xqs.getConnection();
                            }
                            XQPreparedExpression xqpe = conn.prepareExpression(config.query);
                            iter = config.parameter.iterator();
                            while (iter.hasNext()) {
                                ConfigContainer.Config.NameValuePair parameter = iter.next();
                                xqpe.bindString(new QName(parameter.name), parameter.value, null);
                            }
                            XQResultSequence rs = xqpe.executeQuery();
                            helper.startDocument();
                            helper.startElement("results");
                            while (rs.next()) {
                                helper.startElement("result");
                                if (rs.getItemType().getItemKind() == XQItemType.XQITEMKIND_TEXT || rs.getItemType().getItemKind() == XQItemType.XQITEMKIND_ATOMIC) {
                                    helper.text(rs.getItem().getAtomicValue());
                                } else {
                                    rs.writeItemToSAX(new SimpleForwardingXMLReceiver(xmlReceiver) {
                                        public void startDocument() {
                                        }

                                        public void endDocument() {
                                        }
                                    });
                                }
                                helper.endElement();
                            }
                            helper.endElement();
                            helper.endDocument();
                            conn.close();
                        }
                    }


                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
