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
package org.orbeon.oxf.processor.pdf;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver;
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.value.FloatValue;
import org.orbeon.saxon.value.Int64Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The PDF Template processor reads a PDF template and performs textual annotations on it.
 */
public class PDFTemplateProcessor extends HttpBinarySerializer {// TODO: HttpBinarySerializer is supposedly deprecated

    static private Logger logger = LoggerFactory.createLogger(PDFTemplateProcessor.class);

    public static String DEFAULT_CONTENT_TYPE = "application/pdf";
    public static final String PDF_TEMPLATE_MODEL_NAMESPACE_URI = "http://www.orbeon.com/oxf/pdf-template/model";

    // XPath function library
    private static final FunctionLibrary functionLibrary = org.orbeon.oxf.pipeline.api.FunctionLibrary.instance();

    public PDFTemplateProcessor() {
        addInputInfo(new ProcessorInputOutputInfo("model", PDF_TEMPLATE_MODEL_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo("data"));
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(PipelineContext pipelineContext, ProcessorInput input, Config config, OutputStream outputStream) {
        final org.dom4j.Document configDocument = readCacheInputAsDOM4J(pipelineContext, "model");// TODO: after all, should we use "config"?
        final org.dom4j.Document instanceDocument = readInputAsDOM4J(pipelineContext, input);

        final Configuration configuration = XPathCache.getGlobalConfiguration();
        final DocumentInfo configDocumentInfo = new DocumentWrapper(configDocument, null, configuration);
        final DocumentInfo instanceDocumentInfo = new DocumentWrapper(instanceDocument, null, configuration);

        try {
            // Get reader
            final String templateHref = XPathCache.evaluateAsString(configDocumentInfo, "/*/template/@href", null, null, functionLibrary, null, null, null);//TODO: LocationData

            // Create PDF reader
            final PdfReader reader;
            {
                final String inputName = ProcessorImpl.getProcessorInputSchemeInputName(templateHref);
                if (inputName != null) {
                    // Read the input
                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                    readInputAsSAX(pipelineContext, inputName,  new BinaryTextXMLReceiver(null, os, true, false, null, false, false, null, false));

                    // Create the reader
                    reader = new PdfReader(os.toByteArray());
                } else {
                    // Read and create the reader
                    reader = new PdfReader(URLFactory.createURL(templateHref));
                }
            }

            // Get total number of pages
            final int pageCount = reader.getNumberOfPages();

            // Get size of first page
            final Rectangle pageSize = reader.getPageSize(1);
            final float width = pageSize.getWidth();
            final float height = pageSize.getHeight();

            final String showGrid = XPathCache.evaluateAsString(configDocumentInfo, "/*/template/@show-grid", null, null, functionLibrary, null, null, null);//TODO: LocationData

            final PdfStamper stamper = new PdfStamper(reader, outputStream);
            stamper.setFormFlattening(true);

            for (int currentPage = 1; currentPage <= pageCount; currentPage++) {
            	final PdfContentByte contentByte = stamper.getOverContent(currentPage);
                // Handle root group
                final GroupContext initialGroupContext = new GroupContext(contentByte,stamper.getAcroFields(), height, currentPage,
                		Collections.singletonList((Item) instanceDocumentInfo), 1,
                        0, 0, "Courier", 14, 15.9f);
                handleGroup(pipelineContext, initialGroupContext, Dom4jUtils.elements(configDocument.getRootElement()), functionLibrary, reader);

                // Handle preview grid (NOTE: This can be heavy in memory.)
                if ("true".equalsIgnoreCase(showGrid)) {
                    final float topPosition = 10f;

                    final BaseFont baseFont2 = BaseFont.createFont("Courier", BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                    contentByte.beginText();
                    {
                        // 20-pixel lines and side legends

                        contentByte.setFontAndSize(baseFont2, (float) 7);

                        for (int w = 0; w <= width; w += 20) {
                            for (int h = 0; h <= height; h += 2)
                                contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, ".", (float) w, height - h, 0);
                        }
                        for (int h = 0; h <= height; h += 20) {
                            for (int w = 0; w <= width; w += 2)
                                contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, ".", (float) w, height - h, 0);
                        }

                        for (int w = 0; w <= width; w += 20) {
                            contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, "" + w, (float) w, height - topPosition, 0);
                            contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, "" + w, (float) w, topPosition, 0);
                        }
                        for (int h = 0; h <= height; h += 20) {
                            contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, "" + h, (float) 5, height - h, 0);
                            contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, "" + h, width - (float) 5, height - h, 0);
                        }

                        // 10-pixel lines

                        contentByte.setFontAndSize(baseFont2, (float) 3);

                        for (int w = 10; w <= width; w += 10) {
                            for (int h = 0; h <= height; h += 2)
                                contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, ".", (float) w, height - h, 0);
                        }
                        for (int h = 10; h <= height; h += 10) {
                            for (int w = 0; w <= width; w += 2)
                                contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, ".", (float) w, height - h, 0);
                        }
                    }
                    contentByte.endText();
                }
            }

            // Close the document
//            document.close();
            stamper.close();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Resolve attribute value templates (AVTs).
     *
     * @param pipelineContext    current pipeline context
     * @param contextNode        context node for evaluation
     * @param variableToValueMap variables
     * @param functionLibrary    XPath function library to use
     * @param functionContext    context object to pass to the XForms function
     * @param element            element on which the AVT attribute is present
     * @param attributeValue     attribute value
     * @return                   resolved attribute value
     */
    private static String resolveAttributeValueTemplates(PipelineContext pipelineContext, NodeInfo contextNode, Map<String, ValueRepresentation> variableToValueMap,
                                                         FunctionLibrary functionLibrary, XPathCache.FunctionContext functionContext,
                                                         Element element, String attributeValue) {

        if (attributeValue == null)
            return null;

        return XPathCache.evaluateAsAvt(contextNode, attributeValue,  new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(element)),
                variableToValueMap, functionLibrary, functionContext, null, (LocationData) element.getData());
    }

    private static class GroupContext {
        public PdfContentByte contentByte;
        public AcroFields acroFields;

        public float pageHeight;
        public int pageNumber;

        public List<Item> contextNodeSet;
        public int contextPosition;

        public float offsetX;
        public float offsetY;

        public String fontFamily;
        public float fontSize;
        public float fontPitch;

        public GroupContext(PdfContentByte contentByte,AcroFields acroFields, float pageHeight, int pageNumber, List<Item> contextNodeSet,
                            int contextPosition, float offsetX, float offsetY, String fontFamily, float fontSize, float fontPitch) {
            this.contentByte = contentByte;
            this.pageHeight = pageHeight;
            this.pageNumber = pageNumber;
            this.contextNodeSet = contextNodeSet;
            this.contextPosition = contextPosition;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.fontPitch = fontPitch;
            this.fontFamily = fontFamily;
            this.fontSize = fontSize;
            this.acroFields = acroFields;
        }

        public GroupContext(GroupContext other) {
            this.contentByte = other.contentByte;
            this.pageHeight = other.pageHeight;
            this.pageNumber = other.pageNumber;
            this.contextNodeSet = other.contextNodeSet;
            this.contextPosition = other.contextPosition;
            this.offsetX = other.offsetX;
            this.offsetY = other.offsetY;
            this.fontPitch = other.fontPitch;
            this.fontFamily = other.fontFamily;
            this.fontSize = other.fontSize;
            this.acroFields = other.acroFields;
        }
    }

    private void handleGroup(PipelineContext pipelineContext, GroupContext groupContext, List<Element> statements, FunctionLibrary functionLibrary, PdfReader reader) throws DocumentException, IOException {

        final NodeInfo contextNode = (NodeInfo) groupContext.contextNodeSet.get(groupContext.contextPosition - 1);
        final Map<String, ValueRepresentation> variableToValueMap = new HashMap<String, ValueRepresentation>();


        variableToValueMap.put("page-count", new Int64Value(reader.getNumberOfPages()));
        variableToValueMap.put("page-number", new Int64Value(groupContext.pageNumber));
        variableToValueMap.put("page-height", new FloatValue(groupContext.pageHeight));

        // Iterate through statements
        for (final Element currentElement: statements) {

            // Check whether this statement applies to the current page
            final String elementPage = currentElement.attributeValue("page");
            if ((elementPage != null) && !Integer.toString(groupContext.pageNumber).equals(elementPage))
                continue;

            final NamespaceMapping namespaceMapping = new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(currentElement));

            final String elementName = currentElement.getName();
            if (elementName.equals("group")) {
                // Handle group

                final GroupContext newGroupContext = new GroupContext(groupContext);

                final String ref = currentElement.attributeValue("ref");
                if (ref != null) {
                    final NodeInfo newContextNode = (NodeInfo) XPathCache.evaluateSingle(groupContext.contextNodeSet, groupContext.contextPosition, ref, namespaceMapping, variableToValueMap, functionLibrary, null, null, (LocationData) currentElement.getData());

                    if (newContextNode == null)
                        continue;

                    newGroupContext.contextNodeSet = Collections.singletonList((Item) newContextNode);
                    newGroupContext.contextPosition = 1;
                }

                final String offsetXString = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("offset-x"));
                if (offsetXString != null) {
                    newGroupContext.offsetX = groupContext.offsetX + Float.parseFloat(offsetXString);
                }

                final String offsetYString = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("offset-y"));
                if (offsetYString != null) {
                    newGroupContext.offsetY = groupContext.offsetY + Float.parseFloat(offsetYString);
                }

                final String fontPitch = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("font-pitch"));
                if (fontPitch != null)
                    newGroupContext.fontPitch = Float.parseFloat(fontPitch);

                final String fontFamily = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("font-family"));
                if (fontFamily != null)
                    newGroupContext.fontFamily = fontFamily;

                final String fontSize = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("font-size"));
                if (fontSize != null)
                    newGroupContext.fontSize = Float.parseFloat(fontSize);

                handleGroup(pipelineContext, newGroupContext, Dom4jUtils.elements(currentElement), functionLibrary, reader);

            } else if (elementName.equals("repeat")) {
                // Handle repeat

                final String nodeset = currentElement.attributeValue("nodeset");
                final List iterations = XPathCache.evaluate(groupContext.contextNodeSet, groupContext.contextPosition, nodeset, namespaceMapping,
                        variableToValueMap, functionLibrary, null, null, (LocationData) currentElement.getData());

                final String offsetXString = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("offset-x"));
                final String offsetYString = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("offset-y"));
                final float offsetIncrementX = (offsetXString == null) ? 0 : Float.parseFloat(offsetXString);
                final float offsetIncrementY = (offsetYString == null) ? 0 : Float.parseFloat(offsetYString);

                for (int iterationIndex = 1; iterationIndex <= iterations.size(); iterationIndex++) {

                    final GroupContext newGroupContext = new GroupContext(groupContext);

                    newGroupContext.contextNodeSet = iterations;
                    newGroupContext.contextPosition = iterationIndex;

                    newGroupContext.offsetX = groupContext.offsetX + (iterationIndex - 1) * offsetIncrementX;
                    newGroupContext.offsetY = groupContext.offsetY + (iterationIndex - 1) * offsetIncrementY;

                    handleGroup(pipelineContext, newGroupContext, Dom4jUtils.elements(currentElement), functionLibrary, reader);
                }
            } else if (elementName.equals("field")) {

                final String fieldNameStr = currentElement.attributeValue("acro-field-name");

                if (fieldNameStr != null) {
                    final String value = currentElement.attributeValue("value") == null ? currentElement.attributeValue("ref") : currentElement.attributeValue("value");
                    // Get value from instance

                    final String text = XPathCache.evaluateAsString(groupContext.contextNodeSet, groupContext.contextPosition, value, namespaceMapping, variableToValueMap, functionLibrary, null, null, (LocationData) currentElement.getData());
                    final String fieldName = XPathCache.evaluateAsString(groupContext.contextNodeSet, groupContext.contextPosition, fieldNameStr, namespaceMapping, variableToValueMap, functionLibrary, null, null, (LocationData) currentElement.getData());
                    groupContext.acroFields.setField(fieldName, text);

                } else {
                    // Handle field

                    final String leftAttribute = currentElement.attributeValue("left") == null ? currentElement.attributeValue("left-position") : currentElement.attributeValue("left");
                    final String topAttribute = currentElement.attributeValue("top") == null ? currentElement.attributeValue("top-position") : currentElement.attributeValue("top");

                    final String leftPosition = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, leftAttribute);
                    final String topPosition = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, topAttribute);

                    final String size = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("size"));
                    final String value = currentElement.attributeValue("value") == null ? currentElement.attributeValue("ref") : currentElement.attributeValue("value");

                    final FontAttributes fontAttributes = getFontAttributes(currentElement, pipelineContext, groupContext, variableToValueMap, contextNode);

                    // Output value
                    final BaseFont baseFont = BaseFont.createFont(fontAttributes.fontFamily, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                    groupContext.contentByte.beginText();
                    {
                        groupContext.contentByte.setFontAndSize(baseFont, fontAttributes.fontSize);

                        final float xPosition = Float.parseFloat(leftPosition) + groupContext.offsetX;
                        final float yPosition = groupContext.pageHeight - (Float.parseFloat(topPosition) + groupContext.offsetY);

                        // Get value from instance
                        final String text = XPathCache.evaluateAsString(groupContext.contextNodeSet, groupContext.contextPosition, value, namespaceMapping, variableToValueMap, functionLibrary, null, null, (LocationData) currentElement.getData());

                        // Iterate over characters and print them
                        if (text != null) {
                            int len = Math.min(text.length(), (size != null) ? Integer.parseInt(size) : Integer.MAX_VALUE);
                            for (int j = 0; j < len; j++)
                                groupContext.contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, text.substring(j, j + 1), xPosition + ((float) j) * fontAttributes.fontPitch, yPosition, 0);
                        }
                    }
                    groupContext.contentByte.endText();
                }
            } else if (elementName.equals("barcode")) {
                // Handle barcode

                final String leftAttribute = currentElement.attributeValue("left");
                final String topAttribute = currentElement.attributeValue("top");

                final String leftPosition = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, leftAttribute);
                final String topPosition = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, topAttribute);

//                final String size = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, currentElement.attributeValue("size"));
                final String value = currentElement.attributeValue("value") == null ? currentElement.attributeValue("ref") : currentElement.attributeValue("value");
                final String type = currentElement.attributeValue("type") == null ? "CODE39" : currentElement.attributeValue("type");
                final float height = currentElement.attributeValue("height") == null ? 10.0f : Float.parseFloat(currentElement.attributeValue("height"));

                final float xPosition = Float.parseFloat(leftPosition) + groupContext.offsetX;
                final float yPosition = groupContext.pageHeight - (Float.parseFloat(topPosition) + groupContext.offsetY);
                final String text = XPathCache.evaluateAsString(groupContext.contextNodeSet, groupContext.contextPosition, value, namespaceMapping, variableToValueMap, functionLibrary, null, null, (LocationData) currentElement.getData());

                final FontAttributes fontAttributes = getFontAttributes(currentElement, pipelineContext, groupContext, variableToValueMap, contextNode);
                final BaseFont baseFont = BaseFont.createFont(fontAttributes.fontFamily, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

                final Barcode barcode = createBarCode(type);
                barcode.setCode(text);
                barcode.setBarHeight(height);
                barcode.setFont(baseFont);
                barcode.setSize(fontAttributes.fontSize);
                final Image barcodeImage = barcode.createImageWithBarcode(groupContext.contentByte, null, null);
                barcodeImage.setAbsolutePosition(xPosition, yPosition);
                groupContext.contentByte.addImage(barcodeImage);
            } else if (elementName.equals("image")) {
                // Handle image

                // Read image
                final Image image;
                {
                    final String hrefAttribute = currentElement.attributeValue("href");
                    final String inputName = ProcessorImpl.getProcessorInputSchemeInputName(hrefAttribute);
                    if (inputName != null) {
                        // Read the input
                        final ByteArrayOutputStream os = new ByteArrayOutputStream();
                        readInputAsSAX(pipelineContext, inputName, new BinaryTextXMLReceiver(null, os, true, false, null, false, false, null, false));

                        // Create the image
                        image = Image.getInstance(os.toByteArray());
                    } else {
                        // Read and create the image
                        final URL url = URLFactory.createURL(hrefAttribute);

                        // Use ConnectionResult so that header/session forwarding takes place
                        final ConnectionResult connectionResult
                            = new Connection().open(NetUtils.getExternalContext(), new IndentedLogger(logger, ""), false, Connection.Method.GET.name(),
                                url, null, null, null, null, null, null, Connection.getForwardHeaders());

                        if (connectionResult.statusCode != 200) {
                            connectionResult.close();
                            throw new OXFException("Got invalid return code while loading image: " + url.toExternalForm() + ", " + connectionResult.statusCode);
                        }

                        // Make sure things are cleaned-up not too late
                        pipelineContext.addContextListener(new PipelineContext.ContextListener() {
                            public void contextDestroyed(boolean success) {
                                connectionResult.close();
                            }
                        });

                        // Here we decide to copy to temp file and load as a URL. We could also provide bytes directly.
                        final String tempURLString = NetUtils.inputStreamToAnyURI(connectionResult.getResponseInputStream(), NetUtils.REQUEST_SCOPE);
                        image = Image.getInstance(URLFactory.createURL(tempURLString));
                    }
                }


                final String fieldNameStr = currentElement.attributeValue("acro-field-name");
                if (fieldNameStr != null) {
                    // Use field as placeholder

                    final String fieldName = XPathCache.evaluateAsString(groupContext.contextNodeSet, groupContext.contextPosition, fieldNameStr, namespaceMapping, variableToValueMap, functionLibrary, null, null, (LocationData) currentElement.getData());
                    final float[] positions = groupContext.acroFields.getFieldPositions(fieldName);

                    if (positions != null) {
                        final Rectangle rectangle = new Rectangle(positions[1], positions[2], positions[3], positions[4]);

                        // This scales the image so that it fits in the box (but the aspect ratio is not changed)
                        image.scaleToFit(rectangle.getWidth(), rectangle.getHeight());

                        final float yPosition = positions[2] + rectangle.getHeight() - image.getScaledHeight();
                        image.setAbsolutePosition(positions[1] + (rectangle.getWidth() - image.getScaledWidth()) / 2, yPosition);

                        // Add image
                        groupContext.contentByte.addImage(image);
                    }

                } else {
                    // Use position, etc.
                    final String leftAttribute = currentElement.attributeValue("left");
                    final String topAttribute = currentElement.attributeValue("top");
                    final String scalePercentAttribute = currentElement.attributeValue("scale-percent");
                    final String dpiAttribute = currentElement.attributeValue("dpi");

                    final String leftPosition = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, leftAttribute);
                    final String topPosition = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, topAttribute);

                    final float xPosition = Float.parseFloat(leftPosition) + groupContext.offsetX;
                    final float yPosition = groupContext.pageHeight - (Float.parseFloat(topPosition) + groupContext.offsetY);

                    final String scalePercent = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, scalePercentAttribute);
                    final String dpi = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, dpiAttribute);

                    // Set image parameters
                    image.setAbsolutePosition(xPosition, yPosition);
                    if (scalePercent != null) {
                        image.scalePercent(Float.parseFloat(scalePercent));
                    }
                    if (dpi != null) {
                        final int dpiInt = Integer.parseInt(dpi);
                        image.setDpi(dpiInt, dpiInt);
                    }

                    // Add image
                    groupContext.contentByte.addImage(image);
                }
            } else {
                // NOP
            }
        }
    }

    class FontAttributes {
    	float fontPitch;
    	String fontFamily;
    	float fontSize;
    	public FontAttributes(float fontPitch,String fontFamily,float fontSize) {
    		this.fontPitch = fontPitch;
    		this.fontFamily = fontFamily;
    		this.fontSize = fontSize;
    	}
    }
    private FontAttributes getFontAttributes(Element currentElement,
    		PipelineContext pipelineContext,
    		GroupContext groupContext,
    		Map<String, ValueRepresentation> variableToValueMap,
    		NodeInfo contextNode){

        final float fontPitch;
        {
            final String fontPitchAttribute = currentElement.attributeValue("font-pitch") == null ? currentElement.attributeValue("spacing") : currentElement.attributeValue("font-pitch");
            if (fontPitchAttribute != null)
                fontPitch = Float.parseFloat(resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, fontPitchAttribute));
            else
                fontPitch = groupContext.fontPitch;
        }

        final String fontFamily;
        {
            final String fontFamilyAttribute = currentElement.attributeValue("font-family");
            if (fontFamilyAttribute != null)
                fontFamily = resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, fontFamilyAttribute);
            else
                fontFamily = groupContext.fontFamily;
        }

        final float fontSize;
        {
            final String fontSizeAttribute = currentElement.attributeValue("font-size");
            if (fontSizeAttribute != null)
                fontSize = Float.parseFloat(resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, null, currentElement, fontSizeAttribute));
            else
                fontSize = groupContext.fontSize;
        }
        return new FontAttributes(fontPitch,fontFamily,fontSize);

    }

    private Barcode createBarCode(String type) {
		if (type.equals("CODE39")) {
			return new Barcode39();
        } else if (type.equals("CODE128")) {
			return new Barcode128();
        } else if (type.equals("EAN")) {
			return new BarcodeEAN();
		}
		return new Barcode39();
	}
}
