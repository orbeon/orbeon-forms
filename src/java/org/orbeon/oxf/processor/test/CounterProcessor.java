package org.orbeon.oxf.processor.test;

import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This processor returns a new value each time it its output is being read. This processor is
 * designed to be used in tests that want to get that an input is read only once.
 */
public class CounterProcessor extends ProcessorImpl {

    private int counter = 0;

    public CounterProcessor() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    counter++;
                    contentHandler.startDocument();
                    contentHandler.startElement("", "counter", "counter", XMLUtils.EMPTY_ATTRIBUTES);
                    final String counterString = Integer.toString(counter);
                    contentHandler.characters(counterString.toCharArray(), 0, counterString.length());
                    contentHandler.endElement("", "counter", "counter");
                    contentHandler.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
