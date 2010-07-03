package config.utils;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.xml.sax.ContentHandler;

public class GenerateResource extends ProcessorImpl {

    public GenerateResource() {
        addInputInfo(new ProcessorInputOutputInfo("file"));
    }

    public ProcessorOutput createOutput(final String name) {
        ProcessorOutput output = new ProcessorOutputImpl(GenerateResource.this, name) {

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
