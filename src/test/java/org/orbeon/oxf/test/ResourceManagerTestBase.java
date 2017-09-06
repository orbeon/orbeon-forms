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
package org.orbeon.oxf.test;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.orbeon.dom.Document;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.externalcontext.TestExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.XMLProcessorRegistry;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLParsing;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;


public abstract class ResourceManagerTestBase {

    public static final Logger logger = LoggerFactory.createLogger(ResourceManagerTestBase.class);

    public static IndentedLogger newIndentedLogger() {
        return new IndentedLogger(logger, true);
    }

    public ResourceManagerTestBase() {}

    @BeforeClass
    public static void staticSetup() throws Exception {
        ResourceManagerSupport$.MODULE$.initializeJava();
	}

    private PipelineContext pipelineContext;

    @Before
    public void setupResourceManagerTestPipelineContext() {
        this.pipelineContext = PipelineSupport.createPipelineContextWithExternalContextJava();
    }

    @After
    public void tearDownResourceManagerTestPipelineContext() {
        if (pipelineContext != null)
            pipelineContext.destroy(true);
    }
}
