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
package org.orbeon.oxf.processor.serializer.legacy;

import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oro.text.regex.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XLSUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.trans.XPathException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

public class XLSSerializer extends HttpBinarySerializer {

    private static Pattern FORMAT_XPATH;

    public static String DEFAULT_CONTENT_TYPE = "application/vnd.ms-excel";
    public static final String TO_XLS_CONVERTER_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/converter/to-xls";

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected String getConfigSchemaNamespaceURI() {
        return TO_XLS_CONVERTER_CONFIG_NAMESPACE_URI;
    }

    static {
        try {
            Perl5Compiler compiler = new Perl5Compiler();
            FORMAT_XPATH = compiler.compile("^(.*)\"([^\"]+)\"$");
        } catch (MalformedPatternException e) {
            throw new OXFException(e);
        }
    }

    public XLSSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    protected void readInput(final PipelineContext pipelineContext, ProcessorInput input, Config config, OutputStream outputStream) {
        try {
            final PatternMatcher matcher = new Perl5Matcher();
            Document dataDocument = readInputAsDOM4J(pipelineContext, INPUT_DATA);
            final DocumentWrapper wrapper = new DocumentWrapper(dataDocument, null, XPathCache.getGlobalConfiguration());

            Document configDocument = readInputAsDOM4J(pipelineContext, INPUT_CONFIG);

            // Read template sheet
            String templateName = configDocument.getRootElement().attributeValue("template");
            //String fileName = configDocument.getRootElement().attributeValue("filename");
            InputStream templateInputStream = URLFactory.createURL(templateName).openStream();
            final HSSFWorkbook workbook = new HSSFWorkbook(new POIFSFileSystem(templateInputStream));
            final HSSFDataFormat dataFormat = workbook.createDataFormat();
            templateInputStream.close();

            int sheetIndex = 0;

            PooledXPathExpression expr = null;
            List nodes = null;
            try {
                expr = XPathCache.getXPathExpression(wrapper.getConfiguration(), wrapper, "/workbook/sheet", getLocationData());
                nodes = expr.evaluate();
            } catch (XPathException e) {
                throw new OXFException(e);
            } finally {
                if (expr != null)
                    expr.returnToPool();
            }
            for (Iterator i = nodes.iterator(); i.hasNext();) {

                final Element sheetElement = (Element) i.next();
                HSSFSheet sheet = workbook.cloneSheet(0);
                workbook.setSheetName(sheetIndex + 1, sheetElement.attributeValue("name"));

                // Duplicate rows if we find a "repeat-row" in the config
                for (Iterator j = configDocument.selectNodes("/config/repeat-row").iterator(); j.hasNext();) {

                    // Get info about row to repeat
                    Element repeatRowElement = (Element) j.next();
                    final int rowNum = Integer.parseInt(repeatRowElement.attributeValue("row-num"));
                    final String forEach = repeatRowElement.attributeValue("for-each");
                    HSSFRow templateRow = sheet.getRow(rowNum);
                    int repeatCount = ((Double) sheetElement.selectObject("count(" + forEach + ")")).intValue();

                    // Move existing rows lower
                    int lastRowNum = sheet.getLastRowNum();
                    for (int k = lastRowNum; k > rowNum; k--) {
                        HSSFRow sourceRow = sheet.getRow(k);
                        HSSFRow newRow = sheet.createRow(k + repeatCount - 1);
                        XLSUtils.copyRow(workbook, newRow, sourceRow);
                    }

                    // Create rows, copying the template row
                    for (int k = rowNum + 1; k < rowNum + repeatCount; k++) {
                        HSSFRow newRow = sheet.createRow(k);
                        XLSUtils.copyRow(workbook, newRow, templateRow);
                    }

                    // Modify the XPath expression on each row
                    for (int k = rowNum; k < rowNum + repeatCount; k++) {
                        HSSFRow newRow = sheet.getRow(k);
                        for (short m = 0; m <= newRow.getLastCellNum(); m++) {
                            HSSFCell cell = newRow.getCell(m);
                            if (cell != null) {
                                String currentFormat = dataFormat.getFormat(cell.getCellStyle().getDataFormat());
                                if (matcher.contains(currentFormat, FORMAT_XPATH)) {
                                    String newFormat = matcher.getMatch().group(1) + "\""
                                            + forEach + "[" + (k - rowNum + 1) + "]/" + matcher.getMatch().group(2) + "\"";
                                    cell.getCellStyle().setDataFormat(dataFormat.getFormat(newFormat));
                                }
                            }
                        }
                    }
                }

                // Set values in cells with an XPath expression
                XLSUtils.walk(dataFormat, sheet, new XLSUtils.Handler() {
                    public void cell(HSSFCell cell, String sourceXPath, String targetXPath) {
                        if (sourceXPath.charAt(0) == '/')
                            sourceXPath = sourceXPath.substring(1);

                        // Set cell value
                        PooledXPathExpression expr = XPathCache.getXPathExpression(
                                wrapper.getConfiguration(), wrapper.wrap(sheetElement), "string(" + sourceXPath + ")", getLocationData());
                        String newValue;
                        try {
                            newValue = (String) expr.evaluateSingle();
                        } catch (XPathException e) {
                            throw new OXFException(e);
                        } finally {
                            if (expr != null)
                                expr.returnToPool();
                        }
                        if (newValue == null) {
                            throw new OXFException("Nothing matches the XPath expression '"
                                    + sourceXPath + "' in the input document");
                        }
                        try {
                            cell.setCellValue(Double.parseDouble(newValue));
                        } catch (NumberFormatException e) {
                            cell.setCellValue(newValue);
                        }

                        // Set cell format
                        Object element = sheetElement.selectObject(sourceXPath);
                        if (element instanceof Element) {
                            // NOTE: We might want to support other properties here
                            String bold = ((Element) element).attributeValue("bold");
                            if (bold != null) {
                                HSSFFont originalFont = workbook.getFontAt(cell.getCellStyle().getFontIndex());
                                HSSFFont newFont = workbook.createFont();
                                XLSUtils.copyFont(newFont, originalFont);
                                if ("true".equals(bold))
                                    newFont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
                                cell.getCellStyle().setFont(newFont);
                            }
                        }
                    }
                });
                sheetIndex++;
            }

            workbook.removeSheetAt(0);

            // Write out the workbook
            workbook.write(outputStream);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }
}
