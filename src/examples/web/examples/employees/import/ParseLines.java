/*
    Copyright 2004 Orbeon, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.SimpleProcessor;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.StringReader;

public class ParseLines extends SimpleProcessor {

    public ParseLines() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public void generateData(PipelineContext pipelineContext, ContentHandler contentHandler) throws SAXException {
        try {
            Document data = readInputAsDOM4J(pipelineContext, INPUT_DATA);

            String text = data.getRootElement().getStringValue();
            BufferedReader br = new BufferedReader(new StringReader(text));

            ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
            helper.startDocument();
            helper.startElement("document");

            String line;
            while ((line = br.readLine()) != null) {
                helper.element("line", line);
            }

            helper.endElement();
            helper.endDocument();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}