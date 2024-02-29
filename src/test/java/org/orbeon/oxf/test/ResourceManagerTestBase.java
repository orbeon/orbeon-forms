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

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;


public abstract class ResourceManagerTestBase {

    public static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(ResourceManagerTestBase.class);

    public static IndentedLogger newIndentedLogger() {
        return new IndentedLogger(logger, true);
    }

    public ResourceManagerTestBase() {}

    @BeforeClass
    public static void staticSetup() throws Exception {
        ResourceManagerSupportInitializer$.MODULE$.initializeJava();
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
