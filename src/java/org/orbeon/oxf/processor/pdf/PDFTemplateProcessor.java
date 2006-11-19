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
package org.orbeon.oxf.processor.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.serializer.HttpBinarySerializer;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * The PDF Template processor reads a PDF template and performs textual annotations on it.
 */
public class PDFTemplateProcessor extends HttpBinarySerializer {

//    static private Logger logger = LoggerFactory.createLogger(PDFTemplateProcessor.class);

    public static String DEFAULT_CONTENT_TYPE = "application/pdf";
    public static final String PDF_TEMPLATE_MODEL_NAMESPACE_URI = "http://www.orbeon.com/oxf/pdf-template/model";

    public PDFTemplateProcessor() {
        addInputInfo(new ProcessorInputOutputInfo("model", PDF_TEMPLATE_MODEL_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo("instance"));
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(PipelineContext pipelineContext, ProcessorInput input, Config config, OutputStream outputStream) {
        final org.dom4j.Document configDocument = readCacheInputAsDOM4J(pipelineContext, "model");
        final org.dom4j.Document instanceDocument = readInputAsDOM4J(pipelineContext, "instance");

        final Configuration configuration = new Configuration();
        final DocumentInfo configDocumentInfo = new DocumentWrapper(configDocument, null, configuration);
        final DocumentInfo instanceDocumentInfo = new DocumentWrapper(instanceDocument, null, configuration);

        try {
            // Get reader

            final String templateHref = XPathCache.evaluateAsString(pipelineContext, configDocumentInfo, "/*/template/@href", null, null, null, null);
            final PdfReader reader = new PdfReader(URLFactory.createURL(templateHref));
            // Get total number of pages
            final int pageCount = reader.getNumberOfPages();
            // Get size of first page
            final Rectangle psize = reader.getPageSize(1);
            final float width = psize.width();
            final float height = psize.height();

            final String showGrid = XPathCache.evaluateAsString(pipelineContext, configDocumentInfo, "/*/template/@show-grid", null, null, null, null);

            // Create result document and writer
            final Document document = new Document(psize, 50, 50, 50, 50);
            final PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            document.open();

            // Add content to the resulting document
            final PdfContentByte contentByte = writer.getDirectContent();

            for (int currentPage = 1; currentPage <= pageCount; currentPage++) {
                document.newPage();
                PdfImportedPage page1 = writer.getImportedPage(reader, currentPage);
                contentByte.addTemplate(page1, 0, 0);

                // Handle root group
                final GroupContext initialGroupContext = new GroupContext(contentByte, height, currentPage,  Collections.singletonList(instanceDocumentInfo), 1,
                        0, 0, "Courier", 14, 15.9f);
                handleGroup(pipelineContext, initialGroupContext, configDocument.getRootElement().elements());

                // Handle preview grid (NOTE: This can be heavy in memory.)
                if ("true".equalsIgnoreCase(showGrid)) {
                    final float topPosition = 10f;

                    final BaseFont baseFont2 = BaseFont.createFont("Courier", BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                    contentByte.beginText();
                    {
                        // 20-pixel lines and side legences

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
            document.close();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class GroupContext {
        public PdfContentByte contentByte;
        
        public float pageHeight;
        public int pageNumber;

        public List contextNodeSet;
        public int contextPosition;

        public float offsetX;
        public float offsetY;

        public String fontFamily;
        public float fontSize;
        public float fontPitch;

        public GroupContext(PdfContentByte contentByte, float pageHeight, int pageNumber, List contextNodeSet, int contextPosition,
                            float offsetX, float offsetY, String fontFamily, float fontSize, float fontPitch) {
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
        }
    }

    private void handleGroup(PipelineContext pipelineContext, GroupContext groupContext, List statements) throws DocumentException, IOException {

        final NodeInfo contextNode = (NodeInfo) groupContext.contextNodeSet.get(groupContext.contextPosition - 1);
        final Map variableToValueMap = new HashMap();
        variableToValueMap.put("page-number", new Integer(groupContext.pageNumber));
        variableToValueMap.put("page-height", new Float(groupContext.pageHeight));

        // Iterate through statements
        for (Iterator i = statements.iterator(); i.hasNext();) {
            final Element currentElement = (Element) i.next();

            // Check whether this statement applies to the current page
            final String elementPage = currentElement.attributeValue("page");
            if ((elementPage != null ) && !Integer.toString(groupContext.pageNumber).equals(elementPage))
                continue;

            final Map namespaceMap = Dom4jUtils.getNamespaceContextNoDefault(currentElement);

            if (currentElement.getName().equals("group")) {
                // Handle group

                final GroupContext newGroupContext = new GroupContext(groupContext);

                final String ref = currentElement.attributeValue("ref");
                if (ref != null) {
                    final NodeInfo newContextNode = (NodeInfo) XPathCache.evaluateSingle(pipelineContext, groupContext.contextNodeSet, groupContext.contextPosition, ref, namespaceMap, variableToValueMap, null, null);

                    if (newContextNode == null)
                        continue;

                    newGroupContext.contextNodeSet = Collections.singletonList(newContextNode);
                    newGroupContext.contextPosition = 1;
                }

                final String offsetXString = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, currentElement.attributeValue("offset-x"));
                if (offsetXString != null) {
                    newGroupContext.offsetX = groupContext.offsetX + Float.parseFloat(offsetXString);
                }

                final String offsetYString = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, currentElement.attributeValue("offset-y"));
                if (offsetYString != null) {
                    newGroupContext.offsetY = groupContext.offsetY + Float.parseFloat(offsetYString);
                }

                final String fontPitch = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, currentElement.attributeValue("font-pitch"));
                if (fontPitch != null)
                    newGroupContext.fontPitch = Float.parseFloat(fontPitch);

                final String fontFamily = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, currentElement.attributeValue("font-family"));
                if (fontFamily != null)
                    newGroupContext.fontFamily = fontFamily;

                final String fontSize = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, currentElement.attributeValue("font-size"));
                if (fontSize != null)
                    newGroupContext.fontSize = Float.parseFloat(fontSize);

                handleGroup(pipelineContext, newGroupContext, currentElement.elements());
                
            } else if (currentElement.getName().equals("repeat")) {
                // Handle repeat

                final String nodeset = currentElement.attributeValue("nodeset");
                final List iterations = XPathCache.evaluate(pipelineContext, groupContext.contextNodeSet, groupContext.contextPosition, nodeset, namespaceMap, variableToValueMap, null, null);

                final String offsetXString = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, currentElement.attributeValue("offset-x"));
                final String offsetYString = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, currentElement.attributeValue("offset-y"));
                final float offsetIncrementX = (offsetXString == null) ? 0 : Float.parseFloat(offsetXString);
                final float offsetIncrementY = (offsetYString == null) ? 0 : Float.parseFloat(offsetYString);

                for (int iterationIndex = 1; iterationIndex <= iterations.size(); iterationIndex++) {

                    final GroupContext newGroupContext = new GroupContext(groupContext);

                    newGroupContext.contextNodeSet = iterations;
                    newGroupContext.contextPosition = iterationIndex;

                    newGroupContext.offsetX = groupContext.offsetX + (iterationIndex - 1) * offsetIncrementX;
                    newGroupContext.offsetY = groupContext.offsetY + (iterationIndex - 1) * offsetIncrementY;

                    handleGroup(pipelineContext, newGroupContext, currentElement.elements());
                }

            } else if (currentElement.getName().equals("field")) {
                // Handle field

                final String leftAttribute = currentElement.attributeValue("left") == null ? currentElement.attributeValue("left-position") : currentElement.attributeValue("left");
                final String topAttribute = currentElement.attributeValue("top") == null ? currentElement.attributeValue("top-position") : currentElement.attributeValue("top");

                final String leftPosition = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, leftAttribute);
                final String topPosition = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, topAttribute);

                final String size = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, currentElement.attributeValue("size"));
                final String value = currentElement.attributeValue("value") == null ? currentElement.attributeValue("ref") : currentElement.attributeValue("value");

                final float fontPitch;
                {
                    final String fontPitchAttribute = currentElement.attributeValue("font-pitch") == null ? currentElement.attributeValue("spacing") : currentElement.attributeValue("font-pitch");
                    if (fontPitchAttribute != null)
                        fontPitch = Float.parseFloat(XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, fontPitchAttribute));
                    else
                        fontPitch = groupContext.fontPitch;
                }

                final String fontFamily;
                {
                    final String fontFamilyAttribute = currentElement.attributeValue("font-family");
                    if (fontFamilyAttribute != null)
                        fontFamily = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, fontFamilyAttribute);
                    else
                        fontFamily = groupContext.fontFamily;
                }

                final float fontSize;
                {
                    final String fontSizeAttribute = currentElement.attributeValue("font-size");
                    if (fontSizeAttribute != null)
                        fontSize = Float.parseFloat(XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNode, variableToValueMap, null, currentElement, fontSizeAttribute));
                    else
                        fontSize = groupContext.fontSize;
                }

                // Output value
                final BaseFont baseFont = BaseFont.createFont(fontFamily, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                groupContext.contentByte.beginText();
                {
                    groupContext.contentByte.setFontAndSize(baseFont, fontSize);

                    final float xPosition = Float.parseFloat(leftPosition) + groupContext.offsetX;
                    final float yPosition = groupContext.pageHeight - (Float.parseFloat(topPosition) + groupContext.offsetY);

                    // Get value from instance
                    final String text = XPathCache.evaluateAsString(pipelineContext, groupContext.contextNodeSet, groupContext.contextPosition, value, namespaceMap, variableToValueMap, null, null);

                    // Iterate over characters and print them
                    if (text != null) {
                        int len = Math.min(text.length(), (size != null) ? Integer.parseInt(size) : Integer.MAX_VALUE);
                        for (int j = 0; j < len; j++)
                            groupContext.contentByte.showTextAligned(PdfContentByte.ALIGN_CENTER, text.substring(j, j + 1), xPosition + ((float) j) * fontPitch, yPosition, 0);
                    }
                }
                groupContext.contentByte.endText();
            } else {
                // NOP
            }
        }
    }
}
