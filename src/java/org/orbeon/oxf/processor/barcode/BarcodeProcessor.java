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
package org.orbeon.oxf.processor.barcode;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
import org.apache.tools.ant.filters.StringInputStream;
import org.dom4j.Document;
import org.krysalis.barcode4j.BarcodeGenerator;
import org.krysalis.barcode4j.BarcodeUtil;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.om.DocumentInfo;

import java.awt.image.BufferedImage;
import java.io.OutputStream;

/**
 * This processor wraps around barcode4j.
 */
public class BarcodeProcessor extends HttpBinarySerializer {// TODO: HttpBinarySerializer is supposedly deprecated

    public static String DEFAULT_CONTENT_TYPE = "image/png";

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

	public BarcodeProcessor() {
		addInputInfo(new ProcessorInputOutputInfo("barcode"));
		addInputInfo(new ProcessorInputOutputInfo("data"));
	}

	protected void readInput(PipelineContext context, ProcessorInput input, Config config, OutputStream outputStream) {

        // Read inputs
        final Document configDocument = readCacheInputAsDOM4J(context, "barcode");
		final Document instanceDocument = readInputAsDOM4J(context, "data");

        // Wraps documents for XPath API
        final DocumentInfo configDocumentInfo = new DocumentWrapper(configDocument, null, XPathCache.getGlobalConfiguration());
		final DocumentInfo instanceDocumentInfo = new DocumentWrapper(instanceDocument, null, XPathCache.getGlobalConfiguration());

		try {
            final StringBuilderWriter barcodeConfigWriter = new StringBuilderWriter();
			configDocument.write(barcodeConfigWriter);

			final DefaultConfigurationBuilder builder = new DefaultConfigurationBuilder();
			final Configuration cfg = builder.build(new StringInputStream(Dom4jUtils.domToString(configDocument)));
			final BarcodeGenerator gen = BarcodeUtil.getInstance().createBarcodeGenerator(cfg);

            // TODO: These parameters (DPI, etc.) should be configurable
            final BitmapCanvasProvider provider = new BitmapCanvasProvider(
					outputStream, "image/x-png", 300,
					BufferedImage.TYPE_BYTE_GRAY, false);

            // Read text
            final String messageRef = XPathCache.evaluateAsString(
                    configDocumentInfo, "/barcode/@message", null, null, null,
					null, null, null);

			final String message = XPathCache.evaluateAsString(
                    instanceDocumentInfo, messageRef, null, null, null, null,
					null, null);

            // Produce barcode
            gen.generateBarcode(provider, message);
			provider.finish();

		} catch (Exception e) {
			throw new OXFException(e);
		}
	}
}
