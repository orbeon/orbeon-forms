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
package org.orbeon.oxf.resources.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.orbeon.oxf.resources.DataSourceResourceManagerFactory;
import org.orbeon.oxf.resources.FlatFileResourceManagerFactory;
import org.orbeon.oxf.resources.ResourceManager;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.TransformerHandler;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class ResourceManagerTest extends TestCase {
    private static Logger logger = Logger.getLogger(ResourceManagerTest.class);

    private static String ORBEON_DATA = "<?xmlversion=\"1.0\"encoding=\"iso-8859-1\"?><orbeon><test1/><test2><test3/></test2></orbeon>";
    private static String NAMESPACE_DATA = "<?xmlversion=\"1.0\"encoding=\"iso-8859-1\"?><xsl:namespacexmlns:d=\"http://orbeon.org/oxf/xml/document\"xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:testselect=\"d:h1/d:h2\"><title><xsl:value-ofselect=\"$title\"/></title><d:title><d:totod:test=\"test\"/></d:title></xsl:test></xsl:namespace>";

    private ResourceManager rm = null;

    static {
        //BasicConfigurator.configure();
        //Logger.getRootLogger().setLevel(Level.DEBUG);

        // Use Tyrex for JNDI.
        System.getProperties().setProperty("java.naming.factory.initial", "tyrex.naming.MemoryContextFactory");
    }

    public ResourceManagerTest(String name) {
        super(name);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new ResourceManagerTest("testFlatFileDOM"));
        suite.addTest(new ResourceManagerTest("testFlatFileSAX"));

//        suite.addTest(new ResourceManagerTest("testDBDOM"));
//        suite.addTest(new ResourceManagerTest("testDBSAX"));

//        suite.addTest(new ResourceManagerTest("testDSDOM"));

        suite.addTest(new ResourceManagerTest("testLocator"));
        suite.addTest(new ResourceManagerTest("testNamespace"));

        suite.addTest(new ResourceManagerTest("testWrite"));
        return suite;
    }

    protected void tearDown() {
        rm = null;
    }

    public void testDBDOM() {
        try {

            Properties props = new Properties();
            props.setProperty("org.orbeon.oxf.resources.DBResourceManagerFactory.username", "chub");
            props.setProperty("org.orbeon.oxf.resources.DBResourceManagerFactory.password", "chub");
            props.setProperty("org.orbeon.oxf.resources.DBResourceManagerFactory.jdbcUrl", "jdbc:oracle:thin:@localhost:1521:dune");
            props.setProperty("org.orbeon.oxf.resources.DBResourceManagerFactory.driver", "oracle.jdbc.driver.OracleDriver");
            ResourceManagerWrapper.init(props);

            Node doc = ResourceManagerWrapper.instance().getContentAsDOM("/display/orbeon.xml");

            compareResult(doc, ORBEON_DATA);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

    }

    public void testDBSAX() {
        try {

            Properties props = new Properties();
            props.setProperty("org.orbeon.oxf.resources.DBResourceManagerFactory.username", "chub");
            props.setProperty("org.orbeon.oxf.resources.DBResourceManagerFactory.password", "chub");
            props.setProperty("org.orbeon.oxf.resources.DBResourceManagerFactory.jdbcUrl", "jdbc:oracle:thin:@localhost:1521:dune");
            props.setProperty("org.orbeon.oxf.resources.DBResourceManagerFactory.driver", "oracle.jdbc.driver.OracleDriver");
            ResourceManagerWrapper.init(props);

            TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            DOMResult dom = new DOMResult(XMLUtils.createDocument());
            identity.setResult(dom);
            ResourceManagerWrapper.instance().getContentAsSAX("/display/orbeon.xml", identity);

            compareResult(dom.getNode(), ORBEON_DATA);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

    }


    public void testFlatFileDOM() {
        try {
            Properties props = new Properties();
            props.setProperty(ResourceManagerWrapper.FACTORY_PROPERTY, FlatFileResourceManagerFactory.class.getName());
            props.setProperty(FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY, "src/org/orbeon/oxf/resources/test/import");
            ResourceManagerWrapper.init(props);

            Node doc = ResourceManagerWrapper.instance().getContentAsDOM("/display/orbeon.xml");

            compareResult(doc, ORBEON_DATA);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

    }


    public void testFlatFileSAX() {
        try {

            Properties props = new Properties();
            props.setProperty(ResourceManagerWrapper.FACTORY_PROPERTY, FlatFileResourceManagerFactory.class.getName());
            props.setProperty(FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY, "src/org/orbeon/oxf/resources/test/import");
            ResourceManagerWrapper.init(props);


            TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            DOMResult dom = new DOMResult(XMLUtils.createDocument());
            identity.setResult(dom);
            ResourceManagerWrapper.instance().getContentAsSAX("/display/orbeon.xml", identity);
            compareResult(dom.getNode(), ORBEON_DATA);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

    }


    public void testDSDOM() {
        try {
            Context jndi = new InitialContext();
            DataSource ds = new DataSource() {
                final String driver = "oracle.jdbc.driver.OracleDriver";
                final String url = "jdbc:oracle:thin:@localhost:1521:dune";
                final String user = "chub";
                final String password = "chub";

                public Connection getConnection() throws SQLException {
                    Properties info = new Properties();
                    info.setProperty("user", user);
                    info.setProperty("password", password);
                    try {
                        Class.forName(driver);
                        return DriverManager.getConnection(url, info);
                    } catch (SQLException sql) {
                        throw sql;
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail();
                        return null;
                    }
                }

                public Connection getConnection(String s, String s1) throws SQLException {
                    return getConnection();
                }

                public int getLoginTimeout() throws SQLException {
                    return 0;
                }

                public PrintWriter getLogWriter() throws SQLException {
                    return null;
                }

                public void setLoginTimeout(int i) throws SQLException {
                }

                public void setLogWriter(PrintWriter writer) throws SQLException {
                }
            };
            jndi.bind("testDS", ds);

            Properties props = new Properties();
            props.setProperty(ResourceManagerWrapper.FACTORY_PROPERTY, DataSourceResourceManagerFactory.class.getName());
            props.setProperty("org.orbeon.oxf.resources.DataSourceResourceManagerFactory.datasource", "testDS");
            ResourceManagerWrapper.setFactory(new DataSourceResourceManagerFactory(props, jndi));


            Node doc = ResourceManagerWrapper.instance().getContentAsDOM("/display/orbeon.xml");
            compareResult(doc, ORBEON_DATA);

            /** Fix import task first
             InputStream stream = ResourceManagerWrapper.instance().getContentAsStream("/display/big.xml");
             int n = 0;
             byte[] b = new byte[512];
             while(n != -1) {
             n = stream.read(b);
             System.out.println(b);
             }
             **/

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

    }


    public void testLocator() {
        logger.debug("aaa");
        Properties props = new Properties();
        props.setProperty(ResourceManagerWrapper.FACTORY_PROPERTY, FlatFileResourceManagerFactory.class.getName());
        props.setProperty(FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY, "src/org/orbeon/oxf/resources/test/import");
        ResourceManagerWrapper.init(props);

        XMLReader reader = ResourceManagerWrapper.instance().getXMLReader();

        reader.setContentHandler(new DefaultHandler() {
            Locator loc = null;

            public void setDocumentLocator(Locator locator) {
                loc = locator;
                assertEquals("oxf:///display/orbeon.xml", loc.getSystemId());
            }

            public void startElement(String uri, String localName,
                                     String qName, Attributes attributes)
                    throws SAXException {
                if (loc != null) {
                    switch (loc.getLineNumber()) {
                        case 2:
                            assertEquals(localName, "orbeon");
                            break;
                        case 3:
                            assertEquals(localName, "test1");
                            break;
                        case 4:
                            assertEquals(localName, "test2");
                            break;
                        case 5:
                            assertEquals(localName, "test3");
                            break;
                        default:
                            fail("wrong line number" + loc.getLineNumber());
                    }
                } else {
                    fail("Not Locator is provided");
                }
            }
        });
        try {
            reader.parse("/display/orbeon.xml");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    public void testNamespace() {
        try {
            Properties props = new Properties();
            props.setProperty(ResourceManagerWrapper.FACTORY_PROPERTY, FlatFileResourceManagerFactory.class.getName());
            props.setProperty(FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY, "src/org/orbeon/oxf/resources/test/import");
            ResourceManagerWrapper.init(props);


            TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            DOMResult dom = new DOMResult(XMLUtils.createDocument());
            identity.setResult(dom);
            ResourceManagerWrapper.instance().getContentAsSAX("/display/namespace.xml", identity);
            compareResult(dom.getNode(), NAMESPACE_DATA);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

    }


    public void testWrite() {

    }

    public void compareResult(Node actualData, String expectedString) throws Exception {
        try {
            String actualString = domToNormalizedString(actualData);
            boolean testPassed = expectedString.equals(actualString);
            if (!testPassed) {
                System.out.println(getName());
                System.out.println("Expected data: " + expectedString);
                System.out.println("Actual data:   " + actualString);
            }
            assertTrue(testPassed);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    private String domToNormalizedString(Node node) {
        String text = XMLUtils.domToString(node);
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ' && text.charAt(i) != '\n' && text.charAt(i) != '\r')
                buffer.append(text.charAt(i));
        }
        return buffer.toString();
    }

}
