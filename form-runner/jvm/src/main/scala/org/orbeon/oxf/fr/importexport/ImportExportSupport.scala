package org.orbeon.oxf.fr.importexport

import cats.syntax.option._
import org.orbeon.dom.QName
import org.orbeon.oxf.fr.SimpleDataMigration.{DataMigrationBehavior, DataMigrationOp}
import org.orbeon.oxf.fr.XMLNames.FRNamespace
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.excel.ExcelExport.RepeatsPref
import org.orbeon.oxf.util.CollectionUtils.IteratorExt._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.model.ModelDefs
import org.orbeon.oxf.xforms.model.XFormsInstanceSupport
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om
import org.orbeon.scaxon.NodeInfoConversions
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.XFormsId


object ImportExportSupport {

  val HiddenClasses = Set("xforms-hidden", "hidden-field") // TODO: the latter is custom, should be configurable

  val FlattenRepeatedGrids = true

  private val ValueControlQNames = Set(
    XFORMS_INPUT_QNAME,
    XFORMS_SECRET_QNAME,
    XFORMS_TEXTAREA_QNAME,
    XFORMS_OUTPUT_QNAME,
    XFORMS_RANGE_QNAME,
    XFORMS_SELECT_QNAME,
    XFORMS_SELECT1_QNAME,
    QName("number",   FRNamespace),
    QName("currency", FRNamespace),
    QName("us-phone", FRNamespace),
    QName("us-state", FRNamespace),
  )

  // TODO: components
  // TODO: normalize names, for example `fr|checkbox-input -> `xf|input:xxf-type('xs:boolean')[appearance ~= checkbox]`
  //   This way, we can ignore the specific appearances and handle more controls out of the box.
  def isValueControl(controlElem: om.NodeInfo): Boolean = {
    val controlQName = controlElem.resolveQName(controlElem.name)
    ValueControlQNames(controlQName)                          ||
      isSelectionControl(controlElem)                         ||
      controlQName == QName("date", XMLNames.FRNamespace)     ||
      controlQName == QName("time", XMLNames.FRNamespace)     ||
      controlQName == QName("datetime", XMLNames.FRNamespace) ||
      isYesNoInput(controlElem)                               ||
      isCheckboxInput(controlElem)
  }

  def isYesNoInput(controlElem: om.NodeInfo): Boolean = {
    val controlQName = controlElem.resolveQName(controlElem.name)
    controlQName == QName("yesno-input", XMLNames.FRNamespace)
  }

  def isCheckboxInput(controlElem: om.NodeInfo): Boolean = {
    val controlQName = controlElem.resolveQName(controlElem.name)
    controlQName == QName("checkbox-input", XMLNames.FRNamespace)
  }

  def isSelectionControl(controlElem: om.NodeInfo): Boolean = {
    val localname = controlElem.localname
    FormRunner.isSingleSelectionControl(localname)     ||
      FormRunner.isMultipleSelectionControl(localname) ||
      isYesNoInput(controlElem)                        ||
      isCheckboxInput(controlElem)
  }

  def isMultipleSelectionControl(controlElem: om.NodeInfo): Boolean =
    FormRunner.isMultipleSelectionControl(controlElem.localname)

  def isTextAreaControl(controlElem: om.NodeInfo): Boolean = {
    val controlQName = controlElem.resolveQName(controlElem.name)
    controlQName == XFORMS_TEXTAREA_QNAME
  }

  def isExplanationControl(controlElem: om.NodeInfo): Boolean = {
    val controlQName = controlElem.resolveQName(controlElem.name)
    controlQName == QName("explanation", XMLNames.FRNamespace)
  }

  def isExcludedControl(controlElem: om.NodeInfo): Boolean =
    controlElem.name.endsWith("attachment")

  def deleteRow(gridModel: GridModel[om.NodeInfo], index: Int): GridModel[om.NodeInfo] = {

    GridModel(gridModel.cells.take(index) ::: gridModel.cells.drop(index + 1).map(row => row.map(cell => cell.copy(y = cell.y - 1)(12)))) // TODO: 24

    // TODO: adjust h and y
//    // Reduce height of cells which start on a previous row
//    val distinctOriginCellsSpanning = collectDistinctOriginCellsSpanningBefore(allCells, adjustedRowPos)
//
//    distinctOriginCellsSpanning foreach (cell => NodeInfoCellOps.updateH(cell, NodeInfoCellOps.h(cell).getOrElse(1) - 1))
//
//    // Decrement the position of all cells on subsequent rows
//    allCells.view.slice(adjustedRowPos + 1, allCells.size).flatten foreach {
//      case Cell(Some(u), None, _, y, _, _) => NodeInfoCellOps.updateY(u, y - 1)
//      case _ =>
//    }
  }

//  // TODO: copied from `FormBuilder`
//  private def collectDistinctOriginCellsSpanningBefore[Underlying](cells: List[List[Cell[Underlying]]], rowPos: Int): List[Underlying] =
//    cells(rowPos) collect {
//      case Cell(Some(u), Some(origin), _, y, _, _) if origin.y < y => u
//    } distinct

  // TODO: correct handling of names
  def controlNameToNamedRangeName(controlName: String): String =
    controlName.translate("-", "_")

  def controlIsHiddenWithCss(control: om.NodeInfo): Boolean =
    control.attValueOpt(CLASS_QNAME) exists
      (_.tokenizeToSet.intersect(HiddenClasses).nonEmpty)

  // Filter out rows where all controls match the filterOut predicate
  def filterGridIfNeeded(
    gridModel : GridModel[om.NodeInfo],
    filterOut : om.NodeInfo => Boolean
  ): GridModel[om.NodeInfo] = {

    // Remove rows without visible content
    // This matches what we do in HTML/CSS, where rows that are empty or that don't contain relevant controls
    // don't take up vertical space.
    def rowIsNonRelevant(row: List[Cell[om.NodeInfo]]) =
      row forall {
        case Cell(Some(u), _, _, _, _, _) => u firstChildOpt * exists filterOut
        case Cell(None, _, _, _, _, _)    => false
      }

    val nonRelevantIndexes =
      gridModel.cells.zipWithIndex collect { case (row, index) if rowIsNonRelevant(row) => index }

    if (nonRelevantIndexes.nonEmpty)
      nonRelevantIndexes.reverse.foldLeft(gridModel)(deleteRow)
    else
      gridModel
  }

  // Take a grid with possibly multiple rows and transform it into a grid with a single row
  def flattenGrids(gridModels: Iterable[GridModel[om.NodeInfo]]): GridModel[om.NodeInfo] = {

    val (_, cells) =
      gridModels.foldLeft((0, Nil: List[Cell[om.NodeInfo]])) { case ((sizeSoFar, cellsSoFar), gridModel) =>

        val newList =
          for {
              (row, rowIndex) <- gridModel.cells.zipWithIndex
              cell            <- row
            } yield
              cell.copy(y = 1, h = 1, x = cell.x + rowIndex * 12)(12 * gridModel.cells.size)

        (sizeSoFar + gridModel.cells.size * 12, cellsSoFar ::: newList)
      }

    GridModel(List(cells))
  }

  // A named range can be up to 255 characters long and can contain letters, numbers, periods and underscores (no spaces or special punctuation characters).
  // Named ranges are not case sensitive and they can contain both upper and lower case letters. They cannot resemble any actual cell addresses such as "B3" or "AA12".
  // All named ranges must begin with a letter, an underscore "_" or a backslash "\".
  // Named ranges can include numbers but cannot include any spaces.
  // You cannot use any named ranges that resemble actual cell addresses (e.g. A$5 or R3C8).
  // You cannot use any symbols except for an underscore and a full stop. It is possible to include a backslash and a question mark as long as they are not the first characters.
  // Named ranges can be just single letters with the exception of the letters R and C.
  // When you add a named range it is the cell that is named and not the cell contents.
  // They are case insensitive. You cannot have another named range with the same letters but in a different case.
  // By default named ranges are created as absolute references.
  // It is possible for a cell (or range) to have more than one named range so typing a new name using the Name Box will not change the named range but will create a new one.

  def findDatatype(bind: om.NodeInfo): Option[QName] =
    bind.attValueOpt(TYPE_QNAME) map bind.resolveQName

  def ancestorRepeatNames(controlName: String)(implicit ctx: FormRunnerDocContext): List[String] =
    FormRunner.findControlByName(ctx.bodyElem, controlName).toList flatMap
      (c => FormRunner.findAncestorRepeatNames(c, includeSelf = false))

  def countRepeatIterations(holders: Option[Seq[om.NodeInfo]]): Int =
    (holders.toList.flatten / *).size

  def buildFilename(title: String, lang: String, ext: String): String =
    s"$title ($lang).$ext"

  def prepareFormRunnerDocContextOrThrow(
    form              : om.NodeInfo,
    appFormVersionOpt : Option[(AppForm, Int)],
    formDataOpt       : Option[(om.DocumentInfo, DataMigrationBehavior)]
  ): FormRunnerDocContext =
    prepareFormRunnerDocContext(form, appFormVersionOpt, formDataOpt)
      .getOrElse(throw new IllegalArgumentException("data migration error"))

  def prepareFormRunnerDocContext(
    form              : om.NodeInfo,
    appFormVersionOpt : Option[(AppForm, Int)],
    formDataOpt       : Option[(om.DocumentInfo, DataMigrationBehavior)]
  ): List[DataMigrationOp] Either FormRunnerDocContext = {

    val (metadataRootElem, templateDataRootElem, excludeResultPrefixes) = {

      val ctx = new FormRunnerDocContext {

        // Create a mutable copy if needed so we can extract the nested instance further below while
        // semi-correctly removing in-scope namespaces.
        val formDefinitionRootElem: om.NodeInfo = {
          if (formDataOpt.isDefined)
            form.rootElement
          else
            TransformerUtils.extractAsMutableDocument(form.rootElement)
        }
      }

      (
        ctx.metadataRootElem,
        ctx.dataRootElem,
        ctx.dataInstanceElem.attTokens(XXFORMS_EXCLUDE_RESULT_PREFIXES)
      )
    }

    val dataMaybeMigrated =
      (appFormVersionOpt, formDataOpt) match {
        case (Some((appForm, _)), Some((formData, dataMigrationBehavior))) =>

          // TODO: Move this to function and reuse from `persistence-model.xml`
          val dataMaybeGridMigrated =
            MigrationSupport.migrateDataWithFormMetadataMigrations(
              appForm             = appForm,
              data                = formData,
              metadataRootElemOpt = metadataRootElem.some,
              srcVersion          = FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm.app, appForm.form),
              dstVersion          = DataFormatVersion.Edge,
              pruneMetadata       = false
            )

          val dataMaybeGridMigratedOrOriginalRootElem =
            dataMaybeGridMigrated.getOrElse(formData).rootElement

          val dataMaybeSimplyMigratedRootElem =
            SimpleDataMigration.dataMaybeWithSimpleMigrationUseOps(
              enclosingModelAbsoluteId = XFormsId.effectiveIdToAbsoluteId(Names.FormModel),
              templateInstanceRootElem = templateDataRootElem,
              dataToMigrateRootElem    = dataMaybeGridMigratedOrOriginalRootElem,
              dataMigrationBehavior    = dataMigrationBehavior)(
              formOps                  = new FormDefinitionOps(form)
            )

          dataMaybeSimplyMigratedRootElem.getOrElse(Right(dataMaybeGridMigratedOrOriginalRootElem))
        case _ =>

          // See also `XXFormsExtractDocument`
          // NOTE: This only works because above we convert the form definition to a mutable DOM. Otherwise, we would
          // hit the case where we need to convert from `NodeInfo` to the Orbeon DOM and that function "properly"
          // copies the namespaces in scope. We don't have a truly correct function currently to extract a subtree
          // and correctly un-scope namespaces, but we should!
          Right(
            XFormsInstanceSupport.extractDocument(
              element               = NodeInfoConversions.unsafeUnwrapElement(templateDataRootElem),
              excludeResultPrefixes = excludeResultPrefixes,
              readonly              = true,
              exposeXPathTypes      = false,
              removeInstanceData    = true
            ).rootElement
          )
      }

    dataMaybeMigrated map { effectiveDataRootElem =>
      new FormRunnerDocContext {
        val formDefinitionRootElem: om.NodeInfo = form.rootElement
        override lazy val dataRootElem = effectiveDataRootElem
      }
    }
  }

  def controlIsReadonly(control: om.NodeInfo)(implicit ctx: FormRunnerDocContext): Boolean =
    FormRunner.searchControlBindPathHoldersInDoc(List(control), control, ctx.dataRootElem.some, _ => true).headOption exists {
      case FormRunner.ControlBindPathHoldersResources(_, bind, _, _, _) => isBindReadonly(bind)
    }

  def isBindReadonly(bind: om.NodeInfo)(implicit ctx: FormRunnerDocContext): Boolean =
    FormRunner.readDenormalizedCalculatedMip(bind, ModelDefs.Readonly, ModelDefs.Readonly.aName) == FormRunner.TrueExpr

  def iterateAncestorOrSelfBinds(b: om.NodeInfo): Iterator[om.NodeInfo] =
    iterateFrom[om.NodeInfo](b, _ parent XMLNames.XFBindTest headOption)

  val NumericTypes = Set("decimal", "integer", "double", "dateTime", "date", "time")

  def controlDetailsWithRelevantAndVisible(
    control : om.NodeInfo)(implicit
    ctx     : FormRunnerDocContext
  ): Option[(FormRunner.ControlBindPathHoldersResources, Boolean)] =
    FormRunner.searchControlBindPathHoldersInDoc(List(control), ctx.formDefinitionRootElem, ctx.dataRootElem.some, _ => true).headOption map {
      case cbphr @ FormRunner.ControlBindPathHoldersResources(_, bind, _, _, _) =>

        val visible = {
          ! (control.namespaceURI == XMLNames.FR && control.localname == "hidden")  && // `fr:hidden`
          ! (control.namespaceURI == XMLNames.XF && control.localname == "trigger") && // `xf:trigger`
          ! controlIsHiddenWithCss(control)                                         &&
          FormRunner.readDenormalizedCalculatedMip(bind, ModelDefs.Relevant, ModelDefs.Relevant.aName) != FormRunner.FalseExpr
        }

        (cbphr, visible)
    }

  def controlIsNonRelevantOrNonVisible(control: om.NodeInfo)(implicit ctx: FormRunnerDocContext): Boolean =
    controlIsHiddenWithCss(control) || {
      FormRunner.searchControlBindPathHoldersInDoc(List(control), ctx.formDefinitionRootElem, ctx.dataRootElem.some, _ => true).headOption exists {
        case FormRunner.ControlBindPathHoldersResources(_, bind, _, _, _) =>
          FormRunner.readDenormalizedCalculatedMip(bind, ModelDefs.Relevant, ModelDefs.Relevant.aName) == FormRunner.FalseExpr
      }
    }

  def filterCellsWithControls(updatedGridModel: GridModel[om.NodeInfo]): List[List[(Cell[om.NodeInfo], om.NodeInfo)]] =
    updatedGridModel.cells.map (_ flatMap {
      case c @ Cell(Some(u), None, _, _, _, _) => FormRunner.findCellNestedControl(u).filterNot(isExcludedControl).map(c ->)
      case _                                   => None
    })

  def iterationsFollowPreference(holders: Option[List[om.NodeInfo]], repeatsPref: RepeatsPref): Int =
    repeatsPref match {
      case RepeatsPref.FollowFormDefinition => countRepeatIterations(holders)
      case RepeatsPref.AtLeast(count)       => count max countRepeatIterations(holders)
      case RepeatsPref.Exactly(count)       => count
    }

  def getItemsForRow(
    gridRow                     : List[(Cell[om.NodeInfo], om.NodeInfo)],
    requestedLang               : String,
    frResourcesForRequestedLang : om.NodeInfo)(implicit
    ctx                         : FormRunnerDocContext
  ): (List[(Int, Int, Seq[(String, String)], Boolean)], Int) = {

    val items =
      gridRow collect {
        case (cell, leafControl) if isSelectionControl(leafControl) =>
          (cell.x - 1, cell.w, leafControl, isMultipleSelectionControl(leafControl))
      } flatMap {
        case (cellX, cellW, leafControl, multiple) =>
          controlDetailsWithRelevantAndVisible(leafControl) map ((cellX, cellW, _, multiple))
      } collect {
        case (cellX, cellW, (FormRunner.ControlBindPathHoldersResources(leafControl, _, _, _, resources), true), multiple) =>
          (
            cellX,
            cellW,
            getItemsForControl(
              leafControl                 = leafControl,
              resources                   = resources,
              requestedLang               = requestedLang,
              frResourcesForRequestedLang = frResourcesForRequestedLang
            ),
            multiple
        )
      }

    val maxItemCount = items.nonEmpty option (items map (_._3.size) max) getOrElse 0

    (items, maxItemCount)
  }

  def getItemsForControl(
    leafControl                 : om.NodeInfo,
    resources                   : Seq[(String, om.NodeInfo)],
    requestedLang               : String,
    frResourcesForRequestedLang : om.NodeInfo
  ): Seq[(String, String)] =
    if (isYesNoInput(leafControl) || isCheckboxInput(leafControl)) {
      // Special behavior

      val yesLabel = frResourcesForRequestedLang / "components" / "labels" / "yes"
      val noLabel  = frResourcesForRequestedLang / "components" / "labels" / "no"

      List(yesLabel.stringValue -> "true", noLabel.stringValue -> "false")
    } else {
      val items = resources.find(_._1 == requestedLang).toList map (_._2) child "item"
      items map (item => item.elemValue("label") -> item.elemValue("value"))
    }
}
