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

import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.XPath;
import org.orbeon.oro.text.regex.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.XLSUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

public class XLSSerializer extends ProcessorImpl {

    private static Pattern FORMAT_XPATH;

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

    public void start(final PipelineContext context) {
        try {
            final PatternMatcher matcher = new Perl5Matcher();
            Document dataDocument = readInputAsDOM4J(context, INPUT_DATA);
            Document configDocument = readInputAsDOM4J(context, INPUT_CONFIG);

            // Read template sheet
            String templateName = configDocument.getRootElement().attributeValue("template");
            String fileName = configDocument.getRootElement().attributeValue("filename");
            InputStream templateInputStream = URLFactory.createURL(templateName).openStream();
            final HSSFWorkbook workbook = new HSSFWorkbook(new POIFSFileSystem(templateInputStream));
            final HSSFDataFormat dataFormat = workbook.createDataFormat();
            templateInputStream.close();

            int sheetIndex = 0;
            for (Iterator i = dataDocument.selectNodes("/workbook/sheet").iterator(); i.hasNext();) {

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
                        Object newObject = sheetElement.selectObject(sourceXPath);
                        XPath xpath = XPathCache.createCacheXPath(context, "string()");
                        String newValue = (String) xpath.evaluate(newObject);
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

            // Write in byte array, so we can determine the file size
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            workbook.write(byteArrayOutputStream);

            // Send to user

            ExternalContext externalContext = (ExternalContext) context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.EXTERNAL_CONTEXT);
            ExternalContext.Response response = externalContext.getResponse();
            response.setContentType("application/vnd.ms-excel");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName != null ? fileName : "export.xls");
            response.setContentLength(byteArrayOutputStream.size());
            response.getOutputStream().write(byteArrayOutputStream.toByteArray());
            response.getOutputStream().flush();

        } catch (java.io.IOException e) {
            throw new OXFException(e);
        }
    }
}
