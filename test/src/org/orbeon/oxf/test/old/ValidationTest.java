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
import junit.framework.TestSuite;

import java.util.Date;
import java.io.File;

import org.iso_relax.verifier.VerifierFactory;
import org.iso_relax.verifier.Schema;
import org.iso_relax.verifier.Verifier;


import com.sun.msv.verifier.jarv.TheFactoryImpl;
*/

public class ValidationTest /*extends TestCase*/ {

//    private static final Logger logger = Logger.getLogger(ValidationTest.class);
//
//    static {
//        BasicConfigurator.configure();
//
//        Properties props = new Properties();
//        props.setProperty(ResourceManagerWrapper.FACTORY_PROPERTY, PriorityResourceManagerFactory.class.getName());
//        props.setProperty("oxf.resources.priority.1", FlatFileResourceManagerFactory.class.getName());
//        props.setProperty("oxf.resources.priority.2", ClassLoaderResourceManagerFactory.class.getName());
//        props.setProperty(FlatFileResourceManagerFactory.ROOT_DIR, "test/resources");
//        ResourceManagerWrapper.init(props);
//
//        OXFProperties.init("oxf:/properties.xml");
//    }

//    public ValidationTest(String s) {
//        super(s);
//    }

//    public static TestSuite suite() {
//        TestSuite suite = new TestSuite();
//
//        suite.addTest(new ValidationTest("testDOM4JNamespaces"));
//        suite.addTest(new ValidationTest("testTransformer"));
//        suite.addTest(new ValidationTest("testMSV"));
//
//        return suite;
//    }


//    public void testTransformer() {
//        try {
//            PipelineContext context = new PipelineContext();
//
//            SAXTransformerFactory factory = new TransformerFactoryImpl();
//            factory.setAttribute(TransformerFactoryImpl.PIPELINE_CONTEXT_ATTRIBUTE, context);
//            TemplatesHandler th = factory.newTemplatesHandler();
//
//            ResourceManagerWrapper.instance().getContentAsSAX("/note.rng", th);
//            if (!(th.getTemplates() instanceof TemplatesImpl))
//                fail("Can't get templates object");
//
//            Source source = new SAXSource(ResourceManagerWrapper.instance().getXMLReader(),
//                    new InputSource("/note.xml"));
//
//            LocationSAXContentHandler document = new LocationSAXContentHandler();
//            Result result = new SAXResult(document);
//
//            th.getTemplates().newTransformer().transform(source, result);
//
//            System.out.println(XMLUtils.domToString(document.getDocument()));
//
//        } catch (Exception e) {
//            if (e instanceof OXFException)
//                ((OXFException) e).getNestedException().printStackTrace();
//            else
//                e.printStackTrace();
//            fail();
//        }
//
//    }

//    public void testDOM4JNamespaces() throws Exception {
//
//        Document xslt = ResourceManagerWrapper.instance().getContentAsDOM4J("/xalan-nodeset");
//        System.out.println(xslt.toString());
//
//
//        TransformerUtils.getXMLIdentityTransformer().transform(new DocumentSource(xslt), new StreamResult(System.out));
//        // Rhaaa... Julien!
//        //TransformerUtils.getXMLIdentityTransformer().transform(new DOMSource(xslt), new SAXResult(System.out));
//
//        //TransformerUtils.getTemplates(new DocumentSource(xslt), TransformerUtils.COMPILER_TYPE);
//
//
//    }


//    public void testMSV() throws Exception {
//        VerifierFactory verifierFactory = new TheFactoryImpl();
//        Schema schema = verifierFactory.compileSchema(new File("book.xsd"));
//        Verifier verifier = schema.newVerifier();
//        verifier.verify(new File("book.xml"));
//        new Date();
//    }

}
