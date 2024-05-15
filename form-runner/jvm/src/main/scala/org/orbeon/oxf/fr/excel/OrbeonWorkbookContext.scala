package org.orbeon.oxf.fr.excel

import org.apache.poi.hssf.util.HSSFColor.HSSFColorPredefined
import org.apache.poi.ss.usermodel.{BorderStyle, FillPatternType, IndexedColors, VerticalAlignment}
import org.apache.poi.xssf.usermodel.{DefaultIndexedColorMap, XSSFColor, XSSFWorkbook}
import org.orbeon.oxf.fr.excel.ExcelSupport._
import org.orbeon.oxf.util.CoreUtils._


class OrbeonWorkbookContext(workbook: XSSFWorkbook, requestedLang: String)
  extends ExcelSupport.WorkbookContext {

  val formTitleFont =
    workbook.createFont |!>
      (_.setFontName(BaseFont)) |!>
      (_.setFontHeightInPoints(FormTitleFontSize)) |!>
      (_.setBold(true))

  val formTitleStyle =
    workbook.createCellStyle |!>
      (_.setFont(formTitleFont)) |!>
      (_.setFillPattern(FillPatternType.SOLID_FOREGROUND)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_40_PERCENT.getIndex)) |!>
      (_.setWrapText(true))

  val controlLabelFont =
    workbook.createFont |!>
      (_.setFontName(BaseFont)) |!>
      (_.setFontHeightInPoints(LabelFontSize)) |!>
      (_.setBold(true))

  val controlLabelStyle =
    workbook.createCellStyle |!>
      (_.setFont(controlLabelFont)) |!>
      (_.setWrapText(true))

  val sectionTitleFont =
    workbook.createFont |!>
      (_.setFontName(BaseFont)) |!>
      (_.setFontHeightInPoints(SectionTitleFontSize)) |!>
      (_.setBold(true))

  val sectionTitleStyle =
    workbook.createCellStyle |!>
      (_.setFont(sectionTitleFont)) |!>
      (_.setFillPattern(FillPatternType.SOLID_FOREGROUND)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_40_PERCENT.getIndex)) |!>
      (_.setWrapText(true))

  val explanationCellStyle =
    workbook.createCellStyle |!>
      (_.setFillPattern(FillPatternType.SOLID_FOREGROUND)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_25_PERCENT.getIndex)) |!>
      (_.setWrapText(true)) |!>
      (_.setVerticalAlignment(VerticalAlignment.TOP))

  val explanationTextFont =
    workbook.createFont |!>
      (_.setFontName(BaseFont)) |!>
      (_.setFontHeightInPoints(ExplanationTextFontSize))

  val explanationTextBoldFont =
    workbook.createFont |!>
      (_.setFontName(BaseFont)) |!>
      (_.setFontHeightInPoints(ExplanationTextFontSize)) |!>
      (_.setBold(true))

  val explanationTextItalicFont =
    workbook.createFont |!>
      (_.setFontName(BaseFont)) |!>
      (_.setFontHeightInPoints(ExplanationTextFontSize)) |!>
      (_.setItalic(true))

  private val valueCellStyle =
    workbook.createCellStyle |!>
      (_.setFillPattern(FillPatternType.SOLID_FOREGROUND)) |!>
      (_.setFillForegroundColor(
        new XSSFColor(
          new java.awt.Color(
            ValueBackgroundRgb._1,
            ValueBackgroundRgb._2,
            ValueBackgroundRgb._3
          ),
          new DefaultIndexedColorMap
        )
      )) |!>
      (_.setVerticalAlignment(VerticalAlignment.TOP)) |!>
      (_.setBorderBottom(BorderStyle.MEDIUM)) |!>
      (_.setBottomBorderColor(IndexedColors.BLACK.getIndex)) |!>
      (_.setBorderLeft(BorderStyle.MEDIUM)) |!>
      (_.setLeftBorderColor(IndexedColors.BLACK.getIndex)) |!>
      (_.setBorderRight(BorderStyle.MEDIUM)) |!>
      (_.setRightBorderColor(IndexedColors.BLACK.getIndex)) |!>
      (_.setBorderTop(BorderStyle.MEDIUM)) |!>
      (_.setTopBorderColor(IndexedColors.BLACK.getIndex))

  val itemCellStyle =
    workbook.createCellStyle |!>
      (_.setWrapText(true))

  // TODO: ability to customize formats
  val isGermanAndFrenchFormat = requestedLang == "de" || requestedLang == "fr"

  private val dateFormat     = workbook.createDataFormat.getFormat(if (isGermanAndFrenchFormat) GermanAndFrenchDateFormat     else EnglishDateFormat)
  private val timeFormat     = workbook.createDataFormat.getFormat(if (isGermanAndFrenchFormat) GermanAndFrenchTimeFormat     else EnglishTimeFormat)
  private val dateTimeFormat = workbook.createDataFormat.getFormat(if (isGermanAndFrenchFormat) GermanAndFrenchDateTimeFormat else EnglishDateTimeFormat)

  val textareaCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(valueCellStyle)) |!>
      (_.setWrapText(true))

  val textCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(valueCellStyle)) |!>
      (_.setWrapText(true)) |!>
      (_.setDataFormat(workbook.createDataFormat.getFormat("@")))

  val numberCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(valueCellStyle)) |!>
      (_.setWrapText(true))

  val dateCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(valueCellStyle)) |!>
      (_.setDataFormat(dateFormat))

  val timeCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(valueCellStyle)) |!>
      (_.setDataFormat(timeFormat))

  val dateTimeCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(valueCellStyle)) |!>
      (_.setDataFormat(dateTimeFormat))

  val readonlyValueCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(valueCellStyle)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_50_PERCENT.getIndex))

  val readonlyTextCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(textCellStyle)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_50_PERCENT.getIndex))

  val readonlyNumberCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(numberCellStyle)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_50_PERCENT.getIndex))

  val readonlyTextareaCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(textareaCellStyle)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_50_PERCENT.getIndex))

  val readonlyDateCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(dateCellStyle)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_50_PERCENT.getIndex))

  val readonlyTimeCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(timeCellStyle)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_50_PERCENT.getIndex))

  val readonlyDateTimeCellStyle =
    workbook.createCellStyle |!>
      (_.cloneStyleFrom(dateTimeCellStyle)) |!>
      (_.setFillForegroundColor(HSSFColorPredefined.GREY_50_PERCENT.getIndex))
}