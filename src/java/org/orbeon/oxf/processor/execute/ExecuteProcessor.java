/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.processor.execute;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Commandline;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * The Execute Processor allows executing an external command.
 *
 * TODO: Currently, we store the outputs using ant properties. This can cause the processor to take up a lot of memory.
 *
 * TODO: Handle stdin if connected.
 */
public class ExecuteProcessor extends ProcessorImpl {

//    private static Logger logger = LoggerFactory.createLogger(ExecuteProcessor.class);

    public static final String EXECUTE_PROCESSOR_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/execute-processor-config";

    private static final String OUTPUT_PROPERTY = "org.orbeon.oxf.processor.execute.output";
    private static final String ERROR_PROPERTY = "org.orbeon.oxf.processor.execute.error";
    private static final String RESULT_PROPERTY = "org.orbeon.oxf.processor.execute.result";

//    private static final String INPUT_STDIN = "stdin";
    private static final String OUTPUT_STDOUT = "stdout";
    private static final String OUTPUT_STDERR = "stderr";
    private static final String OUTPUT_RESULT = "result";

    public ExecuteProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, EXECUTE_PROCESSOR_CONFIG_NAMESPACE_URI));
//        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_PROPERTY)); // optional
//        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_STDERR)); // optional
//        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_RESULT  )); // optional
    }

    private static class Config {

        public String executable;
        public String dir;
        public boolean spawn;
        public String output;
        public String error;
        public boolean logError;
        public boolean append;
        public String input;
        public String inputstring;
        public String resultproperty;
        public Integer timeout;
        public boolean failonerror; // -> exception?
        public boolean failifexecutionfails; // -> exception?
        public boolean newenvironment;
        public boolean vmlauncher;
        public boolean resolveexecutable;
        public boolean searchpath;

        public String argLine;

        public Config(Document document) {
            // Directory and file
            executable = XPathUtils.selectStringValueNormalize(document, "/*/@executable");
            dir = XPathUtils.selectStringValueNormalize(document, "/*/@dir");
            spawn = ProcessorUtils.selectBooleanValue(document, "/*/@spawn", false);
            output = XPathUtils.selectStringValueNormalize(document, "/*/@output");
            error = XPathUtils.selectStringValueNormalize(document, "/*/@error");
            logError = ProcessorUtils.selectBooleanValue(document, "/*/@logError", false);
            append = ProcessorUtils.selectBooleanValue(document, "/*/@append", false);
            input = XPathUtils.selectStringValueNormalize(document, "/*/@input");
            inputstring = XPathUtils.selectStringValueNormalize(document, "/*/@inputstring");
            timeout = XPathUtils.selectIntegerValue(document, "/*/@timeout");

            failonerror = ProcessorUtils.selectBooleanValue(document, "/*/@error", false);
            failifexecutionfails = ProcessorUtils.selectBooleanValue(document, "/*/@failifexecutionfails", true);
            newenvironment = ProcessorUtils.selectBooleanValue(document, "/*/@newenvironment", false);
            vmlauncher = ProcessorUtils.selectBooleanValue(document, "/*/@vmlauncher", true);
            resolveexecutable = ProcessorUtils.selectBooleanValue(document, "/*/@resolveexecutable", false);
            searchpath = ProcessorUtils.selectBooleanValue(document, "/*/@searchpath", false);

            // TODO: other args options
            argLine = XPathUtils.selectStringValue(document, "/*/arg[1]/@line");

            // value
            // file
            // path
            // pathref [no]

            // TODO: set max size for output
        }
    }

    public void doIt(PipelineContext pipelineContext, String outputName, ContentHandler contentHandler) {
        try {
            // Read config
            final Config config = (Config) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    return new Config(readInputAsDOM4J(context, input));
                }
            });

            // Create task
            final ExecTask execTask = new ExecTask();
            final Project project = new Project();
            execTask.setProject(project);

            // Set mandatory executable
            execTask.setExecutable(config.executable);
            if (config.dir != null)
                execTask.setDir(new File(config.dir));

            // Set optional command-line arguments
            if (config.argLine != null) {
                final Commandline.Argument argument = execTask.createArg();
                argument.setLine(config.argLine);
            }

            // Set optional spawn
            execTask.setSpawn(config.spawn);

            // Set output file if available
            final File outputFile;
            if (config.output != null) {
                outputFile = new File(config.output);
            } else {
//                final FileItem fileItem = XMLUtils.prepareFileItem(pipelineContext);
//                if (fileItem instanceof DefaultFileItem) {
//                    final DefaultFileItem defaultFileItem = (DefaultFileItem) fileItem;
//                    outputFile = defaultFileItem.getStoreLocation();
//                } else {
                    outputFile = null;
//                }
            }

            if (outputFile != null)
                execTask.setOutput(outputFile);

            // Set output property
            execTask.setOutputproperty(OUTPUT_PROPERTY);

            // Handle result code
            execTask.setResultProperty(RESULT_PROPERTY);

            // Set error file is available
            final File errorFile;
            if (config.error != null) {
                errorFile = new File(config.error);
            } else {
                errorFile = null;
            }

            if (errorFile != null)
                execTask.setError(errorFile);

            // Set error property
            execTask.setErrorProperty(ERROR_PROPERTY);

            // Set optional append
            execTask.setAppend(config.append);

            // TODO: Handle input
//            execTask.setInput(config.input;
//            execTask.setInputString(config.inputstring);
//
            // Set command timeout
            if (config.timeout != null)
                execTask.setTimeout(config.timeout);

            // Other boolean properties
            execTask.setFailonerror(config.failonerror);
            execTask.setFailIfExecutionFails(config.failifexecutionfails);
            execTask.setNewenvironment(config.newenvironment);
            execTask.setVMLauncher(config.vmlauncher);
            execTask.setResolveExecutable(config.resolveexecutable);
            execTask.setSearchPath(config.searchpath);

//            logger.info("xxx before execute");
            execTask.execute();
//            logger.info("xxx after execute");

            final State state = (State) getState(pipelineContext);
            state.isRead = true;

            if (contentHandler != null) {
                // Output or remember stdout
                state.outputString = project.getProperty(OUTPUT_PROPERTY);
                if (outputName.equals(OUTPUT_STDOUT)) {
                    outputStdout(contentHandler, state);
                }

                // Output or remember stderr
                state.errorString = project.getProperty(ERROR_PROPERTY);
                if (outputName.equals(OUTPUT_STDERR)) {
                    outputStderr(contentHandler, state);
                }

                // Output or remember result
                state.result = project.getProperty(RESULT_PROPERTY);
//                logger.info("xxx: result " + state.result);
                if (outputName.equals(OUTPUT_RESULT)) {
                    outputResult(contentHandler, state);
                }
            }

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private void outputStdout(ContentHandler contentHandler, State state) {
        ProcessorUtils.readText(state.outputString, contentHandler, "text/plain", null);
        state.outputString = null;
    }

    private void outputStderr(ContentHandler contentHandler, State state) {
        ProcessorUtils.readText(state.errorString, contentHandler, "text/plain", null);
        state.errorString = null;
    }

    private void outputResult(ContentHandler contentHandler, State state) {
        final ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
        helper.startDocument();
        helper.startElement("result");
        helper.text(state.result);
        helper.endElement();
        helper.endDocument();
    }

    /**
     * Case where an output must be generated.
     */
    public ProcessorOutput createOutput(final String outputName) {
        final ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), outputName) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                final State state = (State) getState(pipelineContext);
                if (!state.isRead) {
                    // We haven't run the command yet
                    doIt(pipelineContext, outputName, contentHandler);
                } else {
                    // We have already run the command
                    if (outputName.equals(OUTPUT_STDOUT)) {
                        outputStdout(contentHandler, state);
                    } else if (outputName.equals(OUTPUT_STDERR)) {
                        outputStderr(contentHandler, state);
                    } else if (outputName.equals(OUTPUT_RESULT)) {
                        outputResult(contentHandler, state);
                    }
                }
            }
        };
        addOutput(outputName, output);
        return output;
    }

    /**
     * Case where no output is generated.
     */
    public void start(PipelineContext pipelineContext) {
        doIt(pipelineContext, null, null);
    }

    public void reset(PipelineContext pipelineContext) {
        setState(pipelineContext, new State());
    }

    private static class State {
        public boolean isRead;
        public String outputString;
        public String errorString;
        public String result;
    }
}