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
package org.orbeon.oxf.processor.serializer.legacy;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.xml.sax.*;

import java.io.OutputStream;

public class SVGSerializer extends HttpBinarySerializer {

    public static String DEFAULT_CONTENT_TYPE = "image/png";

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(final PipelineContext context, final ProcessorInput input, Config config, OutputStream outputStream) {
        try {

            //JPEGTranscoder t = new JPEGTranscoder();
            ImageTranscoder imageTranscoder = new PNGTranscoder();
            imageTranscoder.addTranscodingHint(JPEGTranscoder.KEY_QUALITY, new Float(0.8));
            //t.addTranscodingHint(JPEGTranscoder.KEY_WIDTH, new Integer(100));
            //trans.addTranscodingHint(JPEGTranscoder.KEY_AOI, aoi);
            //Document document = readInputAsDOM4J(context, input);
            //String documentString = XMLUtils.domToString(document);
            //TranscoderInput ti = new TranscoderInput(new StringReader(documentString));
            //TranscoderInput tixxx1 = new TranscoderInput(document);

            TranscoderInput transcoderInput = new TranscoderInput(new XMLReader() {
                private ContentHandler contentHandler;

                public ContentHandler getContentHandler() {
                    return contentHandler;
                }

                public DTDHandler getDTDHandler() {
                    return null;
                }

                public EntityResolver getEntityResolver() {
                    return null;
                }

                public ErrorHandler getErrorHandler() {
                    return null;
                }

                public boolean getFeature(String name) {
                    return false;
                }

                public Object getProperty(String name) {
                    return null;
                }

                private void parse() {
                    readInputAsSAX(context, (input != null) ? input : getInputByName(INPUT_DATA), contentHandler);
                }

                public void parse(InputSource input) {
                    parse();
                }

                public void parse(String systemId) {
                    parse();
                }

                public void setContentHandler(ContentHandler handler) {
                    this.contentHandler = handler;
                }

                public void setDTDHandler(DTDHandler handler) {
                }

                public void setEntityResolver(EntityResolver resolver) {
                }

                public void setErrorHandler(ErrorHandler handler) {
                }

                public void setFeature(String name, boolean value) {
                }

                public void setProperty(String name, Object value) {
                }
            });
            TranscoderOutput transcoderOutput = new TranscoderOutput(outputStream);
            imageTranscoder.transcode(transcoderInput, transcoderOutput);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
