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
package org.orbeon.oxf.test.old;

/*
import junit.framework.TestCase;
import org.orbeon.oxf.resources.FlatFileResourceManagerFactory;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.xml.TransformerUtils;
import org.w3c.dom.Node;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.naming.InitialContext;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
*/

public class SQLTransformerTest {}

//        extends TestCase {
//
//    public SQLTransformerTest(String s) {
//        super(s);
//    }
//
//    protected void setUp() {
//        // Make sure the Oracle driver is registered
//        try {
//            DriverManager.registerDriver(new oracle.jdbc.driver.OracleDriver());
//        } catch (SQLException e) {
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//
//        // Setup resource manager
//        Properties props = new Properties();
//        props.setProperty(ResourceManagerWrapper.FACTORY_PROPERTY, FlatFileResourceManagerFactory.class.getName());
//        props.setProperty(FlatFileResourceManagerFactory.ROOT_DIR, "src/org/orbeon/oxf/pipeline/test");
//        ResourceManagerWrapper.init(props);
//    }
//
//    public void testInterpreter() {
//        try {
//            // Get test config and input
//            Node config = ResourceManagerWrapper.instance().getContentAsDOM("sql/config");
//            Node input = ResourceManagerWrapper.instance().getContentAsDOM("sql/input");
//
//            // Get content handler
//            TransformerHandler output = TransformerUtils.getIdentityTransformerHandler();
//            output.setResult(new StreamResult(System.out));
//
//            // Start test
//            PipelineContext context = new PipelineContext();
//            context.setAttribute(PipelineContext.JNDI_CONTEXT, new InitialContext());
//            new SQLTransformer().testInterpreter(context, config, input, output);
//            assertTrue(true);
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
//    }
//}
