package config.utils;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.DocumentException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.xml.sax.ContentHandler;

import java.io.*;
import java.net.URL;

public class GenerateResource extends ProcessorImpl {

    public GenerateResource() {
        addInputInfo(new ProcessorInputOutputInfo("file"));
    }

    public ProcessorOutput createOutput(final String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {

            // Read from URL generator
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {
                ProcessorOutputImpl urlGeneratorOutput = getURLGenerator(pipelineContext);
                urlGeneratorOutput.read(pipelineContext, contentHandler);
            }

            // Return key from URL generator
            public OutputCacheKey getKeyImpl(PipelineContext context) {
                ProcessorOutputImpl urlGeneratorOutput = getURLGenerator(context);
                return urlGeneratorOutput.getKey(context);
            }

            // Return validity from URL generator
            public Object getValidityImpl(PipelineContext context) {
                ProcessorOutputImpl urlGeneratorOutput = getURLGenerator(context);
                return urlGeneratorOutput.getValidity(context);
            }

            // If necessary create URL generator and store in state. Then retourn URL generator output
            private ProcessorOutputImpl getURLGenerator(PipelineContext context) {
                State state = (State) getState(context);
                if (state.urlGenerator == null) {
                    ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                    ExternalContext.Request request = externalContext.getRequest();
                    state.urlGenerator = new URLGenerator("oxf:" + request.getRequestPath() + ".xhtml");
//                    state.urlGenerator = new URLGenerator("oxf:" + "todo" + ".xhtml");
                    state.urlGenerator.createOutput(ProcessorImpl.OUTPUT_DATA);
                }
                return (ProcessorOutputImpl) state.urlGenerator.getOutputByName(name);
            }
        };
        addOutput(name, output);
        return output;
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State {
        public URLGenerator urlGenerator;
    }
}
