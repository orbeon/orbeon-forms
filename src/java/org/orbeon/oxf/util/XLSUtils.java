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
package org.orbeon.oxf.util;

import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.util.Region;
import org.orbeon.oro.text.regex.*;
import org.orbeon.oxf.common.OXFException;

public class XLSUtils {

    private static Pattern FORMAT_XPATH;

    static {
        try {
            Perl5Compiler compiler = new Perl5Compiler();
            FORMAT_XPATH = compiler.compile("\"([^\"]+)\"$");
        } catch (MalformedPatternException e) {
            throw new OXFException(e);
        }
    }

    public interface Handler {
        public void cell(HSSFCell cell, String sourceXPath, String targetXPath);
    }

    public static void walk(HSSFDataFormat dataFormat, HSSFSheet sheet, Handler handler) {

        // Go through cells
        boolean[][] merged = getMergedCells(sheet);
        for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
            HSSFRow row = sheet.getRow(rowNum);
            walk(merged, dataFormat, row, handler);
        }
    }

    public static void walk(HSSFDataFormat dataFormat, HSSFSheet sheet, HSSFRow row, Handler handler) {
        boolean[][] merged = getMergedCells(sheet);
        walk(merged, dataFormat, row, handler);
    }

    private static void walk(boolean[][] merged, HSSFDataFormat dataFormat, HSSFRow row, Handler handler) {
        final PatternMatcher matcher = new Perl5Matcher();
        if (row != null) {
            for (int cellNum = 0; cellNum <= row.getLastCellNum(); cellNum++) {
                HSSFCell cell = row.getCell((short) cellNum);
                if (cell != null && !merged[row.getRowNum()][cellNum]) {
                    short dataFormatId = cell.getCellStyle().getDataFormat();
                    if (dataFormatId > 0) {
                        String format = dataFormat.getFormat(dataFormatId);
                        if (matcher.contains(format, FORMAT_XPATH)) {
                            // Found XPath expression
                            String xpath = matcher.getMatch().group(1);
                            int separtorPosition = xpath.indexOf('|');
                            String sourceXPath = separtorPosition == -1 ? xpath : xpath.substring(0, separtorPosition);
                            String targetXPath = separtorPosition == -1 ? null : xpath.substring(separtorPosition + 1);
                            handler.cell(cell, sourceXPath, targetXPath);
                        }
                    }
                }
            }
        }
    }

    public static boolean[][] getMergedCells(HSSFSheet sheet) {
        int lastRowNum = sheet.getLastRowNum();
        short maxCellNum = getMaxCellNum(sheet);

        // Compute merged regions
        boolean[][] merged = new boolean[lastRowNum + 1][maxCellNum + 1];
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            Region region = sheet.getMergedRegionAt(i);
            for (int rowNum = region.getRowFrom(); rowNum <= region.getRowTo(); rowNum++) {
                for (int columnNum = region.getColumnFrom(); columnNum <= region.getColumnTo(); columnNum++) {
                    if (rowNum != region.getRowFrom() || columnNum != region.getColumnFrom())
                        merged[rowNum][columnNum] = true;
                }
            }
        }

        return merged;
    }

    /**
     * Compute max number of columns
     */
    public static short getMaxCellNum(HSSFSheet sheet) {
        int lastRowNum = sheet.getLastRowNum();
        short maxCellNum = 0;
        for (int rowNum = 0; rowNum <= lastRowNum; rowNum++) {
            HSSFRow hssfRow = sheet.getRow(rowNum);
            if (hssfRow != null) {
                short lastCellNum = hssfRow.getLastCellNum();
                if (lastCellNum > maxCellNum)
                    maxCellNum = lastCellNum;
            }
        }
        return maxCellNum;
    }

    public static void copySheet(HSSFWorkbook workbook, HSSFSheet destination, HSSFSheet source) {

        // Copy column width
        short maxCellNum = getMaxCellNum(source);
        for (short i = 0; i <= maxCellNum; i++) {
            destination.setColumnWidth(i, source.getColumnWidth(i));
        }

        // Copy merged cells
        for (int i = 0; i < source.getNumMergedRegions(); i++) {
            Region region = source.getMergedRegionAt(i);
            destination.addMergedRegion(region);
        }

        // Copy rows
        for (int i = 0; i <= source.getLastRowNum(); i++) {
            HSSFRow sourceRow = source.getRow(i);
            HSSFRow destinationRow = destination.createRow(i);
            copyRow(workbook, destinationRow, sourceRow);
        }
    }

    public static void copyRow(HSSFWorkbook workbook, HSSFRow destination, HSSFRow source) {
        for (short i = 0; i <= source.getLastCellNum(); i++) {
            HSSFCell templateCell = source.getCell(i);
            if (templateCell != null) {
                HSSFCell newCell = destination.createCell(i);
                XLSUtils.copyCell(workbook, newCell, templateCell);
            }
        }
    }

    public static void copyCell(HSSFWorkbook workbook, HSSFCell destination, HSSFCell source) {

        // Copy cell content
        destination.setCellType(source.getCellType());
        switch (source.getCellType()) {
            case HSSFCell.CELL_TYPE_BOOLEAN:
                destination.setCellValue(source.getBooleanCellValue());
                break;
            case HSSFCell.CELL_TYPE_FORMULA:
            case HSSFCell.CELL_TYPE_STRING:
                destination.setCellValue(source.getStringCellValue());
                break;
            case HSSFCell.CELL_TYPE_NUMERIC:
                destination.setCellValue(source.getNumericCellValue());
                break;
        }

        // Copy cell style
        HSSFCellStyle sourceCellStyle = source.getCellStyle();
        HSSFCellStyle destinationCellStyle = workbook.createCellStyle();
        destinationCellStyle.setAlignment(sourceCellStyle.getAlignment());
        destinationCellStyle.setBorderBottom(sourceCellStyle.getBorderBottom());
        destinationCellStyle.setBorderLeft(sourceCellStyle.getBorderLeft());
        destinationCellStyle.setBorderRight(sourceCellStyle.getBorderRight());
        destinationCellStyle.setBorderTop(sourceCellStyle.getBorderTop());
        destinationCellStyle.setBottomBorderColor(sourceCellStyle.getBottomBorderColor());
        destinationCellStyle.setDataFormat(sourceCellStyle.getDataFormat());
        destinationCellStyle.setFillBackgroundColor(sourceCellStyle.getFillForegroundColor());
        destinationCellStyle.setFillForegroundColor(sourceCellStyle.getFillForegroundColor());
        destinationCellStyle.setFillPattern(sourceCellStyle.getFillPattern());
        destinationCellStyle.setFont(workbook.getFontAt(sourceCellStyle.getFontIndex()));
        destinationCellStyle.setHidden(sourceCellStyle.getHidden());
        destinationCellStyle.setIndention(sourceCellStyle.getIndention());
        destinationCellStyle.setLeftBorderColor(sourceCellStyle.getLeftBorderColor());
        destinationCellStyle.setLocked(sourceCellStyle.getLocked());
        destinationCellStyle.setRightBorderColor(sourceCellStyle.getRightBorderColor());
        destinationCellStyle.setRotation(sourceCellStyle.getRotation());
        destinationCellStyle.setTopBorderColor(sourceCellStyle.getTopBorderColor());
        destinationCellStyle.setVerticalAlignment(sourceCellStyle.getVerticalAlignment());
        destinationCellStyle.setWrapText(sourceCellStyle.getWrapText());
        destination.setCellStyle(destinationCellStyle);
    }

    public static void copyFont(HSSFFont destination, HSSFFont source) {
        destination.setBoldweight(source.getBoldweight());
        destination.setColor(source.getColor());
        destination.setFontHeight(source.getFontHeight());
        destination.setFontHeightInPoints(source.getFontHeightInPoints());
        destination.setFontName(source.getFontName());
        destination.setItalic(source.getItalic());
        destination.setStrikeout(source.getStrikeout());
        destination.setTypeOffset(source.getTypeOffset());
        destination.setUnderline(source.getUnderline());
    }
}
