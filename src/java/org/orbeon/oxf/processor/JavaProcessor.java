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

import com.sun.tools.javac.Main;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.SystemUtils;
import org.xml.sax.ContentHandler;

import java.io.File;
import java.io.FileFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class JavaProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(JavaProcessor.class);
    private final static String PATH_SEPARATOR = System.getProperty("path.separator");

    public static final String JARPATH_PROPERTY = "jarpath";
    public static final String CLASSPATH_PROPERTY = "classpath";

    public static final String JAVA_CONFIG_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/java";

    public JavaProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, JAVA_CONFIG_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(final String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {

            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                getInput(context).getOutput().read(context, contentHandler);
            }

            public OutputCacheKey getKeyImpl(PipelineContext context) {
                return isInputInCache(context, INPUT_CONFIG)
                        ? getInputKey(context, getInput(context)) : null;
            }
            public Object getValidityImpl(PipelineContext context) {
                return isInputInCache(context, INPUT_CONFIG)
                        ? getInputValidity(context, getInput(context)) : null;
            }

            private ProcessorInput getInput(PipelineContext context) {
                start(context);
                State state = (State) getState(context);
                return (ProcessorInput) state.bottomInputs.get(name);
            }
        };
        addOutput(name, output);
        return output;
    }

    public void start(PipelineContext context) {
        State state = (State) getState(context);
        if (!state.started) {

            Processor processor = JavaProcessor.this.getProcessor(context);

            // Check user processor does not have a config input
            for (Iterator i = processor.getInputsInfo().iterator(); i.hasNext();) {
                final ProcessorInputOutputInfo inputInfo = (ProcessorInputOutputInfo) i.next();
                if (inputInfo.getName().equals(INPUT_CONFIG))
                    throw new OXFException("Processor used by Java processor cannot have a config input");
            }

            List inputs = getConnectedInputs();
            for (Iterator i = inputs.iterator(); i.hasNext();) {
                final ProcessorInput javaProcessorInput = (ProcessorInput) i.next();
                String inputName = javaProcessorInput.getName();

                // Skip our own config input
                if (inputName.equals(INPUT_CONFIG))
                    continue;

                // Delegate
                ProcessorInput userProcessorInput = processor.createInput(inputName);
                ProcessorOutput topOutput = new ProcessorImpl.ProcessorOutputImpl(getClass(), inputName) {
                    protected void readImpl(PipelineContext context, ContentHandler contentHandler) {
                        javaProcessorInput.getOutput().read(context, contentHandler);
                    }
                };
                // Connect
                userProcessorInput.setOutput(topOutput);
                topOutput.setInput(userProcessorInput);
            }

            boolean hasOutputs = false;

            // Connect processor outputs
            for (Iterator i = processor.getOutputsInfo().iterator(); i.hasNext();) {
                hasOutputs = true;
                ProcessorInputOutputInfo outputInfo = (ProcessorInputOutputInfo) i.next();
                ProcessorOutput processorOutput = processor.createOutput(outputInfo.getName());
                ProcessorInput bottomInput = new ProcessorImpl.ProcessorInputImpl(getClass(), outputInfo.getName());
                processorOutput.setInput(bottomInput);
                bottomInput.setOutput(processorOutput);
                state.bottomInputs.put(outputInfo.getName(), bottomInput);
            }

            // Start processor if required
            if (!hasOutputs) {
                processor.start(context);
            }

            state.started = true;
        }
    }

    private Processor getProcessor(PipelineContext context) {
        try {
            // Read config input into a String, cache if possible
            ProcessorInput input = getInputByName(INPUT_CONFIG);
            final Config config = (Config) readCacheInputAsObject(context, input, new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    Document configDocument = readInputAsDOM4J(context, INPUT_CONFIG);
                    Element configElement = configDocument.getRootElement();
                    Config config = new Config();
                    config.clazz = configElement.attributeValue("class");
                    config.sourcepath = configElement.attributeValue("sourcepath");
                    if (config.sourcepath == null)
                        config.sourcepath = "oxf:/";
                    if (config.sourcepath.startsWith("oxf:"))
                        config.sourcepath = config.sourcepath.substring(4);
                    if (config.sourcepath.indexOf(":") != -1)
                        throw new OXFException("Invalid sourcepath: '" + config.sourcepath
                                + "', the Java processor only supports the oxf: protocol in sourcepath");
                    config.sourcepath = ResourceManagerWrapper.instance().getRealPath(config.sourcepath);
                    return config;
                }
            });

            // Check if need to compile
            String sourceFile = config.sourcepath + "/" + config.clazz.replace('.', '/') + ".java";
            String destinationDirectory = SystemUtils.getTemporaryDirectory().getAbsolutePath();
            String destinationFile = destinationDirectory + "/" + config.clazz.replace('.', '/') + ".class";
            boolean fileUpToDate = new File(sourceFile).lastModified() < new File(destinationFile).lastModified();

            // Compile
            if (! fileUpToDate) {
                if (logger.isDebugEnabled())
                    logger.debug("Compiling class '" + config.clazz + "'");
                StringWriter javacOutput = new StringWriter();
                int success = Main.compile(new String[]
                        {"-g", "-classpath", buildClassPath(context),
                         "-sourcepath", config.sourcepath,
                         "-d", SystemUtils.getTemporaryDirectory().getAbsolutePath(),
                         config.sourcepath + "/" + config.clazz.replace('.', '/') + ".java" },
                        new PrintWriter(javacOutput));
                if (success != 0) {
                    String javacOutputString = "\n" + javacOutput.toString();
                    javacOutputString = StringUtils.replace(javacOutputString, "\n", "\n    ");
                    throw new OXFException("Error compiling '" + config.clazz + "'" + javacOutputString);
                }
            }

            // Try to get sourcepath info
            InternalCacheKey sourcepathKey = new InternalCacheKey(JavaProcessor.this, "javaFile", config.sourcepath);
            Object sourcepathValidity = new Long(0);
            Sourcepath sourcepath = (Sourcepath) ObjectCache.instance()
                    .findValid(context, sourcepathKey, sourcepathValidity);

            // Create classloader
            if (sourcepath == null || (sourcepath.callNameToProcessorClass.containsKey(config.clazz) && !fileUpToDate)) {
                if (logger.isDebugEnabled())
                    logger.debug("Creating classloader for sourcepath '" + config.sourcepath + "'");
                sourcepath = new Sourcepath();
                sourcepath.classLoader = new URLClassLoader
                    (new URL[] {SystemUtils.getTemporaryDirectory().toURL(), new File(config.sourcepath).toURL()},
                     this.getClass().getClassLoader());
                ObjectCache.instance().add(context, sourcepathKey, sourcepathValidity, sourcepath);
            }

            // Get processor class
            Class processorClass = (Class) sourcepath.callNameToProcessorClass.get(config.clazz);
            if (processorClass == null) {
                processorClass = sourcepath.classLoader.loadClass(config.clazz);
                sourcepath.callNameToProcessorClass.put(config.clazz, processorClass);
            }

            // Create processor from class
            Thread.currentThread().setContextClassLoader(processorClass.getClassLoader());
            return (Processor) processorClass.newInstance();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private String buildClassPath(PipelineContext context) {
        StringBuffer classpath = new StringBuffer();
        StringBuffer jarpath = new StringBuffer();

        String propJarpath = getPropertySet().getString(JARPATH_PROPERTY);
        String propClasspath = getPropertySet().getString(CLASSPATH_PROPERTY);

        // Add classpath for jproperties if available
        if(propClasspath != null)
            classpath.append(propClasspath).append(PATH_SEPARATOR);

        // Add jar path from properties
        if(propJarpath != null)
            jarpath.append(propJarpath).append(PATH_SEPARATOR);

        // Add jar path from webapp if available
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if(externalContext != null) {
            jarpath.append(externalContext.getRealPath("WEB-INF/lib"));
            classpath.append(externalContext.getRealPath("WEB-INF/classes")).append(PATH_SEPARATOR);
        }

        for (StringTokenizer tokenizer = new StringTokenizer(jarpath.toString(), PATH_SEPARATOR); tokenizer.hasMoreElements();) {
            String path = tokenizer.nextToken();

            // Find jars in path
            File[] jars = new File(path).listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    String absolutePath = pathname.getAbsolutePath();
                    return absolutePath.endsWith(".jar") || absolutePath.endsWith(".zip");
                }
            });

            // Add them to string buffer
            if (jars != null) {
                for (int i = 0; i < jars.length; i++)
                    classpath.append(jars[i].getAbsolutePath()).append(PATH_SEPARATOR);
            }
        }

        if(logger.isDebugEnabled())
            logger.debug("Classpath: "+ classpath.toString());
        return classpath.length() == 0 ? null : classpath.toString();
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class Config {
        public String sourcepath;
        public String clazz;
    }

    private static class State {
        public boolean started = false;
        public Map bottomInputs = new HashMap();
    }

    private static class Sourcepath {
        public ClassLoader classLoader;
        public Map callNameToProcessorClass = new HashMap();
    }
}
