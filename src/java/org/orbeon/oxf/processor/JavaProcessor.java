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
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.ProcessorInputImpl;
import org.orbeon.oxf.resources.ExpirationMap;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

/**
 * The Java processor dynamically creates processors by compiling Java files on the fly.
 */
public class JavaProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(JavaProcessor.class);
    private final static String PATH_SEPARATOR = System.getProperty("path.separator");
    private final static ExpirationMap lastModifiedMap = new ExpirationMap(1000);

    public static final String JARPATH_PROPERTY = "jarpath";
    public static final String CLASSPATH_PROPERTY = "classpath";
    public static final String COMPILER_CLASS_PROPERTY = "compiler-class";
    public static final String COMPILER_JAR_PROPERTY = "compiler-jar";

    public static final String DEFAULT_COMPILER_MAIN = "com.sun.tools.javac.Main";

    public static final String JAVA_CONFIG_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/java";

    public JavaProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, JAVA_CONFIG_NAMESPACE_URI));
    }

    public ProcessorOutput createOutput(final String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(JavaProcessor.this, name) {

            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                getInput(context).getOutput().read(context, xmlReceiver);
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                return isInputInCache(pipelineContext, INPUT_CONFIG)
                        ? getInputKey(pipelineContext, getInput(pipelineContext)) : null;
            }

            @Override
            public Object getValidityImpl(PipelineContext pipelineContext) {
                return isInputInCache(pipelineContext, INPUT_CONFIG)
                        ? getInputValidity(pipelineContext, getInput(pipelineContext)) : null;
            }

            private ProcessorInput getInput(PipelineContext context) {
                start(context);
                State state = (State) getState(context);
                return state.bottomInputs.get(name);
            }
        };
        addOutput(name, output);
        return output;
    }

    @Override
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

            Map<String, List<ProcessorInput>> inputMap = getConnectedInputs();

            for (Iterator<String> i = inputMap.keySet().iterator(); i.hasNext();) {
                String inputName = i.next();
                List<ProcessorInput> inputsForName = inputMap.get(inputName);

                for (Iterator<ProcessorInput> j = inputsForName.iterator(); j.hasNext();) {
                    final ProcessorInput javaProcessorInput = j.next();

                    // Skip our own config input
                    if (inputName.equals(INPUT_CONFIG))
                        continue;

                    // Delegate
                    ProcessorInput userProcessorInput = processor.createInput(inputName);
                    ProcessorOutput topOutput = new ProcessorOutputImpl(JavaProcessor.this, inputName) {
                        protected void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                            javaProcessorInput.getOutput().read(context, xmlReceiver);
                        }
                    };
                    // Connect
                    userProcessorInput.setOutput(topOutput);
                    topOutput.setInput(userProcessorInput);
                }
            }

            boolean hasOutputs = false;
            Map<String, ProcessorOutput> outputMap = getConnectedOutputs();
            // Connect processor outputs
            for (Iterator<String> i = outputMap.keySet().iterator(); i.hasNext();) {
                hasOutputs = true;
                String outputName = i.next();

                ProcessorOutput processorOutput = processor.createOutput(outputName);
                ProcessorInput bottomInput = new ProcessorInputImpl(this, outputName);
                processorOutput.setInput(bottomInput);
                bottomInput.setOutput(processorOutput);
                state.bottomInputs.put(outputName, bottomInput);
            }

            // Reset and start processor if required
            processor.reset(context);
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

                    // Get source path
                    String sourcePathAttributeValue = configElement.attributeValue("sourcepath");
                    if (sourcePathAttributeValue == null)
                        sourcePathAttributeValue = ".";
                    File sourcePath = getFileFromURL(sourcePathAttributeValue, JavaProcessor.this.getLocationData());
                    if (!sourcePath.isDirectory())
                        throw new ValidationException("Invalid sourcepath attribute: cannot find directory for URL: " + sourcePathAttributeValue, (LocationData) configElement.getData());
                    try {
                        config.sourcepath = sourcePath.getCanonicalPath();
                    } catch (IOException e) {
                        throw new ValidationException("Invalid sourcepath attribute: cannot find directory for URL: " + sourcePathAttributeValue, (LocationData) configElement.getData());
                    }

                    return config;
                }
            });

            // Check if need to compile
            String sourceFile = config.sourcepath + "/" + config.clazz.replace('.', '/') + ".java";
            String destinationDirectory = SystemUtils.getTemporaryDirectory().getAbsolutePath();
            String destinationFile = destinationDirectory + "/" + config.clazz.replace('.', '/') + ".class";

            // Check if file is up-to-date
            long currentTimeMillis = System.currentTimeMillis();
            Long sourceLastModified;
            Long destinationLastModified;
            synchronized (lastModifiedMap) {
                sourceLastModified = (Long) lastModifiedMap.get(currentTimeMillis, sourceFile);
                if (sourceLastModified == null) {
                    sourceLastModified = new Long(new File(sourceFile).lastModified());
                    lastModifiedMap.put(currentTimeMillis, sourceFile, sourceLastModified);
                }
                destinationLastModified = (Long) lastModifiedMap.get(currentTimeMillis, destinationFile);
                if (destinationLastModified == null) {
                    destinationLastModified = new Long(new File(destinationFile).lastModified());
                    lastModifiedMap.put(currentTimeMillis, destinationFile, destinationLastModified);
                }
            }
            boolean fileUpToDate = sourceLastModified.longValue() < destinationLastModified.longValue();

            // Compile
            if (!fileUpToDate) {
                StringBuilderWriter javacOutput = new StringBuilderWriter();

                final ArrayList<String> argLst = new ArrayList<String>();
                final String[] cmdLine;
                {
                    argLst.add("-g");
                    final String cp = buildClassPath(context);
                    if (cp != null) {
                        argLst.add("-classpath");
                        argLst.add(cp);
                    }

                    if (config.sourcepath != null && config.sourcepath.length() > 0) {
                        argLst.add("-sourcepath");
                        argLst.add(config.sourcepath);
                    }
                    argLst.add("-d");
                    final File tmp = SystemUtils.getTemporaryDirectory();
                    final String tmpPth = tmp.getAbsolutePath();
                    argLst.add(tmpPth);
                    final String fnam = config.sourcepath + "/"
                            + config.clazz.replace('.', '/') + ".java";
                    argLst.add(fnam);

                    cmdLine = new String[argLst.size()];
                    argLst.toArray(cmdLine);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Compiling class '" + config.clazz + "'");
                    logger.debug("javac " + argLst.toString());
                }
                Throwable thrown = null;
                int exitCode = 1;
                try {
                    // Get compiler class, either user-specified or default to Sun compiler
                    String compilerMain = getPropertySet().getString(COMPILER_CLASS_PROPERTY, DEFAULT_COMPILER_MAIN);

                    ClassLoader classLoader;
                    {
                        URI compilerJarURI = getPropertySet().getURI(COMPILER_JAR_PROPERTY);
                        if (compilerJarURI != null) {
                            // 1: Always honor user-specified compiler JAR if present
                            // Use special class loader pointing to this URL
                            classLoader = new URLClassLoader(new URL[]{compilerJarURI.toURL()}, JavaProcessor.class.getClassLoader());
                            if (logger.isDebugEnabled())
                                logger.debug("Java processor using user-specified compiler JAR: " + compilerJarURI.toString());
                        } else {
                            // 2: Try to use the class loader that loaded this class
                            classLoader = JavaProcessor.class.getClassLoader();
                            try {
                                Class.forName(compilerMain, true, classLoader);
                                logger.debug("Java processor using current class loader");
                            } catch (ClassNotFoundException e) {
                                // Class not found
                                // 3: Try to get to Sun tools.jar
                                String javaHome = System.getProperty("java.home");
                                if (javaHome != null) {
                                    File javaHomeFile = new File(javaHome);
                                    if (javaHomeFile.getName().equals("jre")) {
                                        File toolsFile = new File(javaHomeFile.getParentFile(), "lib" + File.separator + "tools.jar");
                                        if (toolsFile.exists()) {
                                            // JAR file exists, will use it to load compiler class
                                            classLoader = new URLClassLoader(new URL[]{toolsFile.toURI().toURL()}, JavaProcessor.class.getClassLoader());
                                            if (logger.isDebugEnabled())
                                                logger.debug("Java processor using default tools.jar under " + toolsFile.toString());
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Load compiler class using class loader defined above
                    Class compilerClass = Class.forName(compilerMain, true, classLoader);

                    // Get method and run compiler
                    Method compileMethod = compilerClass.getMethod("compile", new Class[]{String[].class, PrintWriter.class});
                    Object result = compileMethod.invoke(null, cmdLine, new PrintWriter(javacOutput));
                    exitCode = ((Integer) result).intValue();

                } catch (final Throwable t) {
                    thrown = t;
                }
                if (exitCode != 0) {
                    String javacOutputString = "\n" + javacOutput.toString();
                    javacOutputString = StringUtils.replace(javacOutputString, "\n", "\n    ");
                    throw new OXFException("Error compiling '" + argLst.toString() + "'" + javacOutputString, thrown);
                }
            }

            // Try to get sourcepath info
            InternalCacheKey sourcepathKey = new InternalCacheKey(JavaProcessor.this, "javaFile", config.sourcepath);
            Object sourcepathValidity = new Long(0);
            Sourcepath sourcepath = (Sourcepath) ObjectCache.instance()
                    .findValid(sourcepathKey, sourcepathValidity);

            // Create classloader
            if (sourcepath == null || (sourcepath.callNameToProcessorClass.containsKey(config.clazz) && !fileUpToDate)) {
                if (logger.isDebugEnabled())
                    logger.debug("Creating classloader for sourcepath '" + config.sourcepath + "'");
                sourcepath = new Sourcepath();
                sourcepath.classLoader = new URLClassLoader
                        (new URL[]{SystemUtils.getTemporaryDirectory().toURI().toURL(), new File(config.sourcepath).toURI().toURL()},
                                this.getClass().getClassLoader());
                ObjectCache.instance().add(sourcepathKey, sourcepathValidity, sourcepath);
            }

            // Get processor class
            Class<Processor> processorClass = sourcepath.callNameToProcessorClass.get(config.clazz);
            if (processorClass == null) {
                processorClass = (Class<Processor>) sourcepath.classLoader.loadClass(config.clazz);
                sourcepath.callNameToProcessorClass.put(config.clazz, processorClass);
            }

            // Create processor from class
            Thread.currentThread().setContextClassLoader(processorClass.getClassLoader());
            return processorClass.newInstance();

        } catch (final IOException e) {
            throw new OXFException(e);
        } catch (final IllegalAccessException e) {
            throw new OXFException(e);
        } catch (final InstantiationException e) {
            throw new OXFException(e);
        } catch (final ClassNotFoundException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Return a File object based on an URL string that can be relative to the specified
     * LocationData. The protocols supported are "oxf:" and "file:". If the file doesn't exist, an
     * exception is thrown.
     */
    public static File getFileFromURL(String urlString, LocationData locationData) {
        URL sourcePathURL;
        {
            try {
                // Resolve relative URLs
                sourcePathURL = (locationData != null && locationData.getSystemID() != null)
                        ? URLFactory.createURL(locationData.getSystemID(), urlString)
                        : URLFactory.createURL(urlString);
            } catch (MalformedURLException e) {
                throw new ValidationException("Invalid sourcepath attribute: '" + urlString + "'", e, locationData);
            }
        }

        // Make sure the protocol is oxf: or file:
        if (sourcePathURL.getProtocol().equals("file")) {
            String fileName = sourcePathURL.getFile();
            File file = new File(fileName);
            if (!file.exists()) {
                // Try to decode only if we cannot find the file
                try {
                    fileName = URLDecoder.decode(fileName, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    // Should not happen
                    throw new ValidationException(e, locationData);
                }
                file = new File(fileName);
                if (!file.exists())
                    throw new ValidationException("Invalid sourcepath attribute: cannot find resource for URL: " + urlString, locationData);
            }
            return file;
        } else if (sourcePathURL.getProtocol().equals("oxf")) {
            // Get real path to source path
            String path = sourcePathURL.getFile();
            return new File(ResourceManagerWrapper.instance().getRealPath(path));
        } else {
            throw new ValidationException("Invalid sourcepath attribute: '" + urlString
                    + "'. The Java processor only supports the oxf: and file: protocols for the sourcepath attribute.", locationData);
        }
    }


    private String buildClassPath(PipelineContext context) throws UnsupportedEncodingException {

        StringBuffer classpath = new StringBuffer();
        StringBuffer jarpath = new StringBuffer();

        String propJarpath = getPropertySet().getString(JARPATH_PROPERTY);
        String propClasspath = getPropertySet().getString(CLASSPATH_PROPERTY);

        // Add class path from properties if available
        if (propClasspath != null)
            classpath.append(propClasspath).append(PATH_SEPARATOR);

        // Add JAR path from properties if available
        if (propJarpath != null)
            jarpath.append(propJarpath).append(PATH_SEPARATOR);

        // Add JAR path and class path from webapp if available
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        boolean gotLibDir = false;
        if (externalContext != null) {
            String webInfLibPath = externalContext.getRealPath("WEB-INF/lib");
            if (webInfLibPath != null) {
                jarpath.append(webInfLibPath).append(PATH_SEPARATOR);
                gotLibDir = true;
            }
            String webInfClasses = externalContext.getRealPath("WEB-INF/classes");
            if (webInfClasses != null)
                classpath.append(webInfClasses).append(PATH_SEPARATOR);
        }

        // Get class path based on class loader hierarchy
        {
            final String pathFromLoaders = SystemUtils.pathFromLoaders(JavaProcessor.class);
            classpath.append(pathFromLoaders);
            if (!pathFromLoaders.endsWith(File.pathSeparator))
                classpath.append(File.pathSeparatorChar);
        }

        if (!gotLibDir) {
            // WEB-INF/lib was not found, this SHOULD mean we are running from the command-line or
            // embedded rather than in a regular web app

            // Try to add directory containing current JAR file
            String pathToCurrentJarDir = SystemUtils.getJarPath(getClass());
            if (pathToCurrentJarDir != null) {
                if (logger.isDebugEnabled())
                    logger.debug("Found current JAR directory: " + pathToCurrentJarDir);
                jarpath.append(pathToCurrentJarDir).append(PATH_SEPARATOR);
            }
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

        if (logger.isDebugEnabled())
            logger.debug("Classpath: " + classpath.toString());
        return classpath.length() == 0 ? null : classpath.toString();
    }

    @Override
    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class Config {
        public String sourcepath;
        public String clazz;
    }

    private static class State {
        public boolean started = false;
        public Map<String, ProcessorInput> bottomInputs = new HashMap<String, ProcessorInput>();
    }

    private static class Sourcepath {
        public ClassLoader classLoader;
        public Map<String, Class<Processor>> callNameToProcessorClass = new HashMap<String, Class<Processor>>();
    }
}
