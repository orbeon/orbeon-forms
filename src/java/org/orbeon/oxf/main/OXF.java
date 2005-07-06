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
package org.orbeon.oxf.main;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.CommandLineExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.PipelineEngineFactory;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a simple command-line interface to the pipeline engine.
 *
 * The command-line interface also illustrates how to use the basic pipeline engine APIs.
 *
 * This code performs the following major steps:
 *
 * 1. Parse the command-line arguments
 * 2. Initialize a resource manager
 * 3. Initialize OXF Properties
 * 4. Initialize logger based on properties
 * 5. Build a processor definition object
 * 6. Initialize a PipelineContext
 * 7. Run the pipeline
 * 8. Display exceptions if needed
 */
public class OXF {

    private static Logger logger = Logger.getLogger(OXF.class);

    private String resourceManagerSandbox;
    private String[] otherArgs;

    private ProcessorDefinition processorDefinition;

    public OXF(String[] args) {
        // 1. Parse the command-line arguments
        parseArgs(args);
    }

    public void init() {

        // Initialize a basic logging configuration until the resource manager is setup
        LoggerFactory.initBasicLogger();

        // Signal that we are starting
        logger.info("Starting Orbeon XML Server " + Version.getVersion());

        // 2. Initialize resource manager
        // Resources are first searched in a file hierarchy, then from the classloader
        Map props = new HashMap();
        props.put("oxf.resources.factory", "org.orbeon.oxf.resources.PriorityResourceManagerFactory");
        if (resourceManagerSandbox != null) {
            // Use a sandbox
            props.put("oxf.resources.filesystem.sandbox-directory", resourceManagerSandbox);
        }
        props.put("oxf.resources.priority.1", "org.orbeon.oxf.resources.FilesystemResourceManagerFactory");
        props.put("oxf.resources.priority.2", "org.orbeon.oxf.resources.ClassLoaderResourceManagerFactory");
        if (logger.isInfoEnabled())
            logger.info("Initializing Resource Manager with: " + props);
        ResourceManagerWrapper.init(props);

        // 3. Initialize properties with default properties file.
        OXFProperties.init(OXFProperties.DEFAULT_PROPERTIES_URI);

        // 4. Initialize log4j (using the properies this time)
        LoggerFactory.initLogger();

        // 5. Build processor definition from command-line parameters
        if (otherArgs != null && otherArgs.length == 1) {
            // Assume the pipeline processor and a config input
            processorDefinition = new ProcessorDefinition();
            processorDefinition.setName(new QName("pipeline", XMLConstants.OXF_PROCESSORS_NAMESPACE));

            String configURL;
            if (!NetUtils.urlHasProtocol(otherArgs[0])) {
                // URL is considered relative to current directory
                try {
                    // Create absolute URL, and switch to the oxf: protocol
                    String fileURL = new URL(new File(".").toURL(), otherArgs[0]).toExternalForm();
                    configURL = "oxf:" + fileURL.substring(fileURL.indexOf(':') + 1);
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
            } else {
                configURL = otherArgs[0];
            }

            processorDefinition.addInput("config", configURL);
        } else {
            throw new OXFException("No main processor definition found.");
        }
    }

    public void parseArgs(String[] args) {
        Options options = new Options();
        {
            Option o = new Option("r", "root", true, "Resource manager root");
            o.setRequired(false);
            options.addOption(o);
        }
        try {
            // Parse the command line options
            CommandLine cmd = new PosixParser().parse(options, args, true);

            // Get resource manager root if any
            resourceManagerSandbox = cmd.getOptionValue('r');

            // Check for remaining args
            otherArgs = cmd.getArgs();
            if (otherArgs == null || otherArgs.length != 1) {
                new HelpFormatter().printHelp("Pipeline URL is required", options);
                System.exit(1);
            }

        } catch (MissingArgumentException e) {
            new HelpFormatter().printHelp("Missing argument", options);
            System.exit(1);
        } catch (UnrecognizedOptionException e) {
            new HelpFormatter().printHelp("Unrecognized option", options);
            System.exit(1);
        } catch (MissingOptionException e) {
            new HelpFormatter().printHelp("Missing option", options);
            System.exit(1);
        } catch (Exception e) {
            new HelpFormatter().printHelp("Unknown error", options);
            System.exit(1);
        }
    }

    public void start() {

        // 6. Initialize a PipelineContext
        PipelineContext pipelineContext = new PipelineContext();

        // Some processors may require a JNDI context. In general, this is not required.
        Context jndiContext;
        try {
            jndiContext = new InitialContext();
        } catch (NamingException e) {
            throw new OXFException(e);
        }
        pipelineContext.setAttribute(PipelineContext.JNDI_CONTEXT, jndiContext);

        try {
            // 7. Run the pipeline from the processor definition created earlier. An ExternalContext
            // is supplied for those processors using external contexts, such as most serializers.
            PipelineEngineFactory.instance().executePipeline(processorDefinition, new CommandLineExternalContext(), pipelineContext);
        } catch (Exception e) {
            // 8. Display exceptions if needed
            LocationData locationData = ValidationException.getRootLocationData(e);
            Throwable throwable = OXFException.getRootThrowable(e);
            String message = locationData == null
                    ? "Exception with no location data"
                    : "Exception at " + locationData.toString();
            logger.error(message, throwable);
        }
    }

    public static void main(String[] args) {
        try {
            OXF oxf = new OXF(args);
            oxf.init();
            oxf.start();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            OXFException.getRootThrowable(e).printStackTrace();
        }
    }
}
