package org.orbeon.oxf.fr.`export`

import org.apache.poi.ss.usermodel.{CellStyle, CellType, IgnoredErrorType}
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.{XSSFCellStyle, XSSFFont, XSSFWorkbook}
import org.orbeon.io.IOUtils
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.excel.ExcelSupport._
import org.orbeon.oxf.fr.excel.{ExcelNumberFormat, ExcelSupport}
import org.orbeon.oxf.fr.importexport.ImportExportSupport
import org.orbeon.oxf.fr.importexport.ImportExportSupport.buildFilename
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.api.PersistenceApi.DataDetails
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.search.adt.Metadata
import org.orbeon.oxf.fr.persistence.relational.{IndexedControl, SummarySettings}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._

import java.io.OutputStream
import java.time.Instant
import java.{util => ju}


object ExportWithSearch {

  def exportWithSearch(
    form          : DocumentNodeInfoType,
    appFormVersion: AppFormVersion,
    outputStream  : OutputStream,
    setFileName   : String => Unit,
    searchQuery   : DocumentNodeInfoType,
  )(implicit
    indentedLogger: IndentedLogger
  ): Unit = {

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    val frDocCtx: FormRunnerDocContext = new InDocFormRunnerDocContext(form)

    val requestedLang =
      searchQuery.rootElement.elemValueOpt("lang") // ec.getRequest.getFirstParamAsString(LanguageParam) // like the `send` action (vs. `lang`, `fr-language`, `fr-lang`)
        .orElse(FormRunner.allLangs(frDocCtx.resourcesRootElem).headOption)
        .getOrElse(throw new IllegalArgumentException("missing resources in form definition"))

    lazy val frResourcesForRequestedLang =
      ImportExportSupport.frResourcesForRequestedLang(requestedLang)

    lazy val frSummaryTitles  = (frResourcesForRequestedLang / "summary" / "titles").head
    lazy val frSummaryFormats = (frResourcesForRequestedLang / "summary" / "formats").head

    // Honor the same properties as the Summary page
    // We can't depend on the Search query we received, as "Send metadata queries to server only if match value
    // or sort attribute set".
    val showableMetadata: List[Metadata] = {

      implicit val formRunnerParams: FormRunnerParams = FormRunnerParams(appFormVersion._1, "summary")

      (FormRunner.booleanFormRunnerProperty("oxf.fr.summary.show-created")         : Boolean).option(Metadata.Created)       .toList :::
      (FormRunner.booleanFormRunnerProperty("oxf.fr.summary.show-last-modified")   : Boolean).option(Metadata.LastModified)  .toList :::
      (FormRunner.booleanFormRunnerProperty("oxf.fr.summary.show-workflow-stage")  : Boolean).option(Metadata.WorkflowStage) .toList :::
      (FormRunner.booleanFormRunnerProperty("oxf.fr.summary.show-created-by")      : Boolean).option(Metadata.CreatedBy)     .toList :::
      (FormRunner.booleanFormRunnerProperty("oxf.fr.summary.show-last-modified-by"): Boolean).option(Metadata.LastModifiedBy).toList
    }

    // For values, we do like the Summary page and we show all controls marked as "Show in Summary".
    // If in the future this is exposed as an API endpoint we could conceivably look at the search query.
    val showableValues =
      Index.searchableValues(
        form,
        appFormVersion._1,
        Some(appFormVersion._2),
        FormRunnerPersistence.providerDataFormatVersionOrThrow(appFormVersion._1)
      )
      .controls
      .toList
      .collect { case c @ IndexedControl(_, _, _, _, SummarySettings(true, _, _, _), _, _, _) => c }

    IOUtils.useAndClose(new XSSFWorkbook) { workbook =>

      val ctx = new SummaryWorkbookContext(workbook, frSummaryFormats)

      // Output heading row
      val sheet       = workbook.createSheet()
      val headingsRow = getOrCreateRow(sheet, 0)

      def metadataHeadingDetails: List[String] =
        showableMetadata.map(s => frSummaryTitles.elemValue(s.string))

      def dataHeadingDetails: List[String] =
        showableValues.map { indexedControl =>
          indexedControl.resources.collectFirst {
            case (`requestedLang`, r) => r.elemValueOpt("label")
          }.flatten.getOrElse(indexedControl.xpath)
        }

      (metadataHeadingDetails ++ dataHeadingDetails).zipWithIndex.foreach { case (value, index) =>
        headingsRow
          .createCell(index)
          .setCellValue(value)
      }

      PersistenceApi.search(
        appFormVersion      = appFormVersion,
        isInternalAdminUser = false,
        searchQueryOpt      = Option(searchQuery),
        returnDetails       = true
      ).zipWithIndex.foreach { case (dd @ DataDetails(_, _, _, _, _, _, documentId, isDraft, details), rowIndex) =>

        val currentRow = getOrCreateRow(sheet, rowIndex + 1)

        // TODO: documentId
        // TODO: draft?

        def getFromMetadata(dataDetail: DataDetails, metadata: Metadata): Either[Instant, Option[String]] =
          metadata match {
            case Metadata.Created        => Left(dataDetail.createdTime)
            case Metadata.CreatedBy      => Right(dataDetail.createdBy)
            case Metadata.LastModified   => Left(dataDetail.modifiedTime)
            case Metadata.LastModifiedBy => Right(dataDetail.lastModifiedBy)
            case Metadata.WorkflowStage  => Right(dataDetail.workflowStage)
          }

        def metadataDetails: List[(CellType, XSSFCellStyle, Option[Either[ju.Date, String]])] =
          showableMetadata.map(getFromMetadata(dd, _)).map {
            case Left(instant)   => (CellType.NUMERIC, ctx.dateTimeCellStyle, Some(Left(new ju.Date(instant.toEpochMilli))))
            case Right(valueOpt) => (CellType.STRING,  ctx.textCellStyle,     valueOpt.map(Right.apply))
          }

        def dataDetails: List[(CellType, XSSFCellStyle, Option[Either[ju.Date, String]])] =
          details.zip(showableValues).map {
            case (detail, indexedControl) =>
              ExcelSupport.getCellDetailsForDatatype(
                datatypeOpt        = Some(SaxonUtils.parseQName(indexedControl.xsType)._2),
                isReadonly         = false,
                trimmedValue       = detail.trimAllToOpt,
                defaultCellDetails = (
                  CellType.STRING,
                  ctx.textCellStyle,
                  detail.trimAllToOpt.map(Right.apply)
                )
              )(ctx)
          }

        (metadataDetails ++ dataDetails).zipWithIndex.foreach {
          case ((cellType, cellStyle, cellValueOpt), colIndex) =>
            val cell = currentRow.createCell(colIndex)
            cell.setCellType(cellType)
            cell.setCellStyle(cellStyle)
            cellValueOpt match {
              case Some(Left(date)) =>
                cell.setCellValue(date)
              case Some(Right(value)) =>
                cell.setCellValue(value)
              case None =>
                cell.setBlank()
            }
            if (cellType == CellType.STRING)
              sheet.addIgnoredErrors(
                new CellRangeAddress(
                  currentRow.getRowNum,
                  currentRow.getRowNum,
                  colIndex,
                  colIndex
                ),
                IgnoredErrorType.NUMBER_STORED_AS_TEXT
              )
        }
      }

      setFileName(
        buildFilename(
          FormRunner.formTitleFromMetadataElem(frDocCtx.metadataRootElem, requestedLang)
            .getOrElse("Untitled Form"),
          requestedLang,
          Mediatypes.getExtensionForMediatypeOrThrow(ContentTypes.ExcelContentType)
        )
      )

      workbook.write(outputStream)
    }
  }

  private class SummaryWorkbookContext(workbook: XSSFWorkbook, frSummaryFormats: om.NodeInfo) extends WorkbookContext {

    private val dateCellFormat     = ExcelNumberFormat.convertFromXPathFormat(frSummaryFormats.elemValue("date"))
    private val timeCellFormat     = ExcelNumberFormat.convertFromXPathFormat(frSummaryFormats.elemValue("time"))
    private val dateTimeCellFormat = ExcelNumberFormat.convertFromXPathFormat(frSummaryFormats.elemValue("dateTime"))

    private val baseCellStyle = workbook.createCellStyle

    private def unused: Nothing = throw new IllegalStateException("should not be used")

    override def controlLabelFont         : XSSFFont  = unused
    override def controlLabelStyle        : CellStyle = baseCellStyle
    override def sectionTitleFont         : XSSFFont  = unused
    override def sectionTitleStyle        : CellStyle = unused
    override def explanationCellStyle     : CellStyle = unused
    override def explanationTextFont      : XSSFFont  = unused
    override def explanationTextBoldFont  : XSSFFont  = unused
    override def explanationTextItalicFont: XSSFFont  = unused

    override val itemCellStyle            : XSSFCellStyle = baseCellStyle
    override val textareaCellStyle        : XSSFCellStyle = baseCellStyle

    override val textCellStyle            : XSSFCellStyle =
      workbook.createCellStyle |!>
        (_.cloneStyleFrom(baseCellStyle)) |!>
        (_.setDataFormat(workbook.createDataFormat.getFormat("@")))

    override val numberCellStyle          : XSSFCellStyle = baseCellStyle

    override val dateCellStyle            : XSSFCellStyle =
      workbook.createCellStyle |!>
        (_.cloneStyleFrom(baseCellStyle)) |!>
        (_.setDataFormat(workbook.createDataFormat.getFormat(dateCellFormat)))

    override val timeCellStyle            : XSSFCellStyle =
      workbook.createCellStyle |!>
        (_.cloneStyleFrom(baseCellStyle)) |!>
        (_.setDataFormat(workbook.createDataFormat.getFormat(timeCellFormat)))

    override val dateTimeCellStyle        : XSSFCellStyle =
      workbook.createCellStyle |!>
        (_.cloneStyleFrom(baseCellStyle)) |!>
        (_.setDataFormat(workbook.createDataFormat.getFormat(dateTimeCellFormat)))

    override def readonlyValueCellStyle   : XSSFCellStyle = unused
    override def readonlyTextCellStyle    : XSSFCellStyle = unused
    override def readonlyNumberCellStyle  : XSSFCellStyle = unused
    override def readonlyTextareaCellStyle: XSSFCellStyle = unused
    override def readonlyDateCellStyle    : XSSFCellStyle = unused
    override def readonlyTimeCellStyle    : XSSFCellStyle = unused
    override def readonlyDateTimeCellStyle: XSSFCellStyle = unused
  }
}
