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
package org.orbeon.oxf.processor.generator;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XLSUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataElement;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.trans.XPathException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.StringTokenizer;

/**
 * NOTE: This generator depends on the Servlet API.
 */
public class XLSGenerator extends ProcessorImpl {

    private final static String INPUT_REQUEST = "request";

    public XLSGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_REQUEST));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(XLSGenerator.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

                try {
                    // Read binary content of uploaded Excel file
                    final byte[] fileContent;
                    {
                        final String NO_FILE = "No file was uploaded";
                        final DocumentInfo requestDocument = readInputAsTinyTree(context, XPathCache.getGlobalConfiguration(),
                                getInputByName(INPUT_REQUEST));

                        final PooledXPathExpression expr = XPathCache.getXPathExpression(
                                requestDocument.getConfiguration(), requestDocument,
                                "/request/parameters/parameter[1]/value", getLocationData());

                        final Element valueElement;
                        try {
                             valueElement = (Element) expr.evaluateSingle();
                        } finally{
                            if (expr != null)
                                expr.returnToPool();
                        }
                        
                        if (valueElement == null) throw new OXFException(NO_FILE);
                        String type = valueElement.attributeValue(XMLConstants.XSI_TYPE_QNAME);
                        if (type == null) throw new OXFException(NO_FILE);

                        if (type.endsWith("anyURI")) {
                            // Read file from disk
                            String url = valueElement.getStringValue();
                            InputStream urlInputStream = new URL(url).openStream();
                            byte[] buffer = new byte[1024];
                            ByteArrayOutputStream fileByteArray = new ByteArrayOutputStream();
                            int size;
                            while ((size = urlInputStream.read(buffer)) != -1)
                                fileByteArray.write(buffer, 0, size);
                            urlInputStream.close();
                            fileContent = fileByteArray.toByteArray();
                        } else {
                            // Decode base64
                            fileContent = Base64.decode(valueElement.getStringValue());
                        }
                    }

                    // Generate XML from Excel file
                    final java.io.ByteArrayInputStream bais 
                        = new ByteArrayInputStream( fileContent );
                    final org.dom4j.Document d = extractFromXLS( bais );
                    final DOMGenerator domGenerator = new DOMGenerator
                        ( d, "xls generator output", DOMGenerator.ZeroValidity
                          , DOMGenerator.DefaultContext );
                    domGenerator.createOutput(OUTPUT_DATA).read(context, xmlReceiver);
                } catch (XPathException xpe) {
                    throw new OXFException(xpe);
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            private Document extractFromXLS(InputStream inputStream) throws IOException {

                // Create workbook
                HSSFWorkbook workbook = new HSSFWorkbook(new POIFSFileSystem(inputStream));

                // Create document
                final NonLazyUserDataElement root = new NonLazyUserDataElement( "workbook" );
                final Document resultDocument = new NonLazyUserDataDocument( root );

                // Add elements for each sheet
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    HSSFSheet sheet = workbook.getSheetAt(i);

                    final Element element = new NonLazyUserDataElement("sheet");
                    resultDocument.getRootElement().add(element);

                    // Go though each cell
                    XLSUtils.walk(workbook.createDataFormat(), sheet, new XLSUtils.Handler() {
                        public void cell(HSSFCell cell, String sourceXPath, String targetXPath) {
                            if (targetXPath != null) {
                                int cellType = cell.getCellType();
                                String value = null;
                                switch (cellType) {
                                    case HSSFCell.CELL_TYPE_STRING:
                                    case HSSFCell.CELL_TYPE_BLANK:
                                        value = cell.getStringCellValue();
                                        break;
                                    case HSSFCell.CELL_TYPE_NUMERIC:
                                        double doubleValue = cell.getNumericCellValue();
                                        if (((double) ((int) doubleValue)) == doubleValue) {
                                            // This is an integer
                                            value = Integer.toString((int) doubleValue);
                                        } else {
                                            // This is a floating point number
                                            value = XMLUtils.removeScientificNotation(doubleValue);
                                        }
                                        break;
                                }
                                if (value == null)
                                    throw new OXFException("Unkown cell type " + cellType
                                            + " for XPath expression '" + targetXPath + "'");
                                addToElement(element, targetXPath, value);
                            }
                        }
                    });
                }

                return resultDocument;
            }

            private void addToElement(Element element, String xpath, String value) {
                StringTokenizer elements = new StringTokenizer(xpath, "/");

                while (elements.hasMoreTokens()) {
                    String name = elements.nextToken();
                    if (elements.hasMoreTokens()) {
                        // Not the last: try to find sub element, otherwise create
                        Element child = element.element(name);
                        if (child == null) {
                            child = new NonLazyUserDataElement(name);
                            element.add(child);
                        }
                        element = child;
                    } else {
                        // Last: add element, set content to value
                        Element child = new NonLazyUserDataElement(name);
                        child.add(Dom4jUtils.createText(value));
                        element.add(child);
                    }
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
