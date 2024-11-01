/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.orbeon.dom.QName
import org.orbeon.oxf.fr.Names.*
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.Controls.AncestorOrSelfIterator
import org.orbeon.oxf.xforms.control.*
import org.orbeon.oxf.xforms.control.controls.{XFormsOutputControl, XFormsSelect1Control, XFormsSelectControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.itemset.{ItemNode, ItemsetSupport}
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xforms.submission.SubmissionUtils
import org.orbeon.saxon.function.ProcessTemplateSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xbl.ErrorSummary
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable.*

import scala.xml.Elem


object FormRunnerMetadata {

  case class ControlDetails(
    name         : String,
    typ          : String,
    level        : Int,
    repeated     : Boolean,
    iterations   : List[Int],
    datatype     : Option[String],
    lhhaAndItems : List[(Lang, (List[(LHHA, String)], List[ItemNode]))],
    value        : Option[ControlValue],
    forHashes    : List[String]
  )

  case class Lang(lang: String) extends AnyVal

  sealed trait ControlValue { val storageValue: String }
  case class SingleControlValue(storageValue: String, formattedValue: Option[String]) extends ControlValue
  case class MultipleControlValue(storageValue: String, formattedValues: List[String]) extends ControlValue

  private val Debug            = false

  val FrExcludeFromEmailBody   = "fr-exclude-from-email-body"
  val ControlsToIgnore         = Set("image", "trigger", "handwritten-signature", "explanation")

  val SelectedCheckboxString   = "☒"
  val DeselectedCheckboxString = "☐"

  //@XPathFunction
  def findAllControlsWithValues(html: Boolean, controlsToExclude: List[NodeInfo]): String = {

    val collector: ErrorEventCollector = EventCollector.Throw

    val currentFormRunnerResources = FormRunnerLang.currentFRResources

    val naString = currentFormRunnerResources / "email" / "na" stringValue
    val iterationResource = currentFormRunnerResources / "email" / "iteration" stringValue

    def iterationString(it: Int) =
      ProcessTemplateSupport.processTemplateWithNames(iterationResource, List(("iteration", it)))

    val controlDetails =
      gatherRelevantControls(
        doc                   = XFormsAPI.inScopeContainingDocument,
        currentLang           = Lang(FormRunnerLang.currentFRLang),
        controlNamesToExclude = controlsToExclude.map(_.stringValue).toSet,
        collector             = collector
      )

    def createLine(
      name     : String,
      typ      : String,
      labelOpt : Option[String],
      value    : Option[ControlValue],
      isFirst  : Boolean
    ): Option[String] = {

      // TODO: escape values when in HTML

      val valueOpt =
        value flatMap {
          case SingleControlValue  (_, formattedValue)  =>
            formattedValue
          case MultipleControlValue(_, formattedValues) =>
            formattedValues.nonEmpty option (formattedValues map (SelectedCheckboxString + ' ' + _) mkString ", ")
        }

      def combineLabelAndValue(label: String, value: Option[String]): Option[String] = {

        val normalizedLabel =
          label.trimAllToOpt map { l =>
            if (l.endsWith(":")) l.init else l
          }

        val normalizedValue =
          value map (_.trimAllToOpt getOrElse naString)

        val list = normalizedLabel.toList ::: normalizedValue.toList

        list.nonEmpty option (list mkString ": ")
      }

      val r =
        typ match {
          case "section" => labelOpt
          case _         => labelOpt flatMap (combineLabelAndValue(_, valueOpt))
        }

        r map (_ + (if (Debug) s" [$name/$typ]" else ""))
    }

    val sb = new StringBuilder

    def processNext(controlDetails: List[ControlDetails], level: Int): Unit =
      controlDetails match {
        case Nil =>

        case ControlDetails(_, "grid", gridLevel, _, _, _, _, _, _) :: rest =>

          val nextLevel = gridLevel + 1

          def f(c: ControlDetails) = c.level > gridLevel

          val grouped = rest.takeWhile(f).groupBy(_.iterations.headOption)

          grouped.toList.sortBy(_._1) foreach {
            case (Some(iteration), content) =>
              sb ++= "<li>"
              sb ++= iterationString(iteration)
              sb ++= "<ul>"
              processNext(content, nextLevel)
              sb ++= "</ul>"
              sb ++= "</li>"
            case _ => // ignore
          }

          processNext(rest.dropWhile(f), level)

        case ControlDetails(name, typ @ "section", sectionLevel, _, _, _, (lang, (lhhas, _)) :: _, value, _) :: rest =>

          val nextLevel    = sectionLevel + 1
          val headingLevel = nextLevel + 1 // so we start at `<h2>` for top-level sections

          // TODO: use current/requested lang
          createLine(name, typ, lhhas.toMap.get(LHHA.Label), value, isFirst = false) foreach { line =>
            sb ++= s"<h$headingLevel>"
            sb ++= line
            sb ++= s"</h$headingLevel>"
          }

          def f(c: ControlDetails) = c.level > sectionLevel

          sb ++= "<ul>"
          processNext(rest.takeWhile(f), nextLevel)
          sb ++= "</ul>"
          processNext(rest.dropWhile(f), level)

        case ControlDetails(name, typ, _, _, _, _, (lang, (lhhas, _)) :: _, value, _) :: rest if ! ControlsToIgnore(typ) =>

          // TODO: use current/requested lang
          createLine(name, typ, lhhas.toMap.get(LHHA.Label), value, isFirst = false) foreach { line =>
            sb ++= "<li>"
            sb ++= line
            sb ++= "</li>"
          }
          processNext(rest, level)

        case _ :: rest  =>

          processNext(rest, level)
      }

    processNext(controlDetails, 1)

    if (html)
      sb.toString()
    else
      sb.toString()
  }

  //@XPathFunction
  def createFormMetadataDocument: NodeInfo = {

    val doc = XFormsAPI.inScopeContainingDocument

    val controls = doc.controls.getCurrentControlTree.effectiveIdsToControls

    val collector: ErrorEventCollector = EventCollector.Throw

    def instanceInScope(control: XFormsSingleNodeControl, staticId: String): Option[XFormsInstance] =
      control.container.resolveObjectByIdInScope(control.effectiveId, staticId, None) flatMap
        (_.narrowTo[XFormsInstance])

    def resourcesInstance(control: XFormsSingleNodeControl): Option[XFormsInstance] =
      instanceInScope(control, FormResources)

    def isBoundToFormDataInScope(control: XFormsSingleNodeControl): Boolean = {

      val boundNode = control.boundNodeOpt
      val data      = instanceInScope(control, FormInstance)

      (boundNode map (_.getDocumentRoot)) == (data map (_.root))
    }

    def iterateResources(resourcesInstance: XFormsInstance): Iterator[(String, NodeInfo)] =
      for (resource <- resourcesInstance.rootElement / Resource iterator)
      yield
        resource.attValue("*:lang") -> resource

    def resourcesForControl(
      staticControl: StaticLHHASupport,
      lang         : String,
      resourcesRoot: NodeInfo,
      controlName  : String
    ): List[Elem] = {

      val enclosingHolder = resourcesRoot descendant controlName take 1

      val lhhas =
        for {
          lhha     <- LHHA.values.toList
          if staticControl.hasDirectLhha(lhha)
          lhhaName = lhha.entryName
          holder   <- enclosingHolder child lhhaName
        } yield
          <_>{holder.stringValue}</_>.copy(label = lhhaName)

      val items =
        (enclosingHolder child Names.Item nonEmpty) list
          <items>{
            for (item <- enclosingHolder child Names.Item)
            yield
              <item>{
                for (el <- item child *)
                yield
                  <_>{el.stringValue}</_>.copy(label = el.localname)
              }</item>

          }</items>


      // TODO: multiple alerts: level of alert

      lhhas ++ items
    }

    val selectedControls =
      controls.values flatMap (_.narrowTo[XFormsSingleNodeControl]) filter isBoundToFormDataInScope

    val sortedControls: List[XFormsSingleNodeControl] =
      selectedControls.toList.sortBy(c => ErrorSummary.controlSearchIndexes(c.absoluteId))(ErrorSummary.IntIteratorOrdering)

    val controlMetadata =
      for {
        control           <- sortedControls
        staticControl     <- control.staticControl.cast[StaticLHHASupport]
        resourcesInstance <- resourcesInstance(control)
        if ! staticControl.staticId.startsWith("fb-lhh-editor-for-") // HACK for this in grid.xbl
        controlName       <- FormRunner.controlNameFromIdOpt(control.getId)
        boundNode         <- control.boundNodeOpt
        dataHash          = SubmissionUtils.dataNodeHash(boundNode)
      } yield
        dataHash ->
          <control
            name={controlName}
            type={staticControl.localName}
            datatype={control.getTypeLocalNameOpt.orNull}>{
            for ((lang, resourcesRoot) <- iterateResources(resourcesInstance))
            yield
              <resources lang={lang}>{resourcesForControl(staticControl, lang, resourcesRoot, controlName)}</resources>
          }{
            for (valueControl <- control.narrowTo[XFormsValueControl].toList)
            yield
              <value>{valueControl.getValue(collector)}</value>
          }</control>

    import scala.xml as sxml

    def addAttribute(elem: Elem, name: String, value: String) =
      elem % sxml.Attribute(None, name, sxml.Text(value), sxml.Null)

    val groupedMetadata =
      controlMetadata groupByKeepOrder (x => x._2) map
      { case (elem, hashes) => addAttribute(elem, "for", hashes map (_._1) mkString " ")}

    <metadata>{groupedMetadata}</metadata>
  }

  private def gatherRelevantControls(
    doc                  : XFormsContainingDocument,
    currentLang          : Lang,
    controlNamesToExclude: Set[String],
    collector            : ErrorEventCollector
  ): List[ControlDetails] = {

    val controls = doc.controls.getCurrentControlTree.effectiveIdsToControls

    def instanceInScope(control: XFormsSingleNodeControl, staticId: String): Option[XFormsInstance] =
      control.container.resolveObjectByIdInScope(control.effectiveId, staticId, None) flatMap
        (_.narrowTo[XFormsInstance])

    def resourcesInstance(control: XFormsSingleNodeControl): Option[XFormsInstance] =
      instanceInScope(control, FormResources)

    def isBoundToFormDataInScope(control: XFormsControl): Boolean = control match {
      case c: XFormsSingleNodeControl =>

        val boundNode = c.boundNodeOpt
        val data      = instanceInScope(c, FormInstance)

        (boundNode map (_.getDocumentRoot)) == (data map (_.root))
      case _ =>
        false
    }

    def isSection (c: XFormsControl) = c.localName == "section"
    def isGrid    (c: XFormsControl) = c.localName == "grid"
    def isRepeat  (c: XFormsControl) = c.staticControl.element.attributeValue("repeat") == "content"
    def isExcluded(c: XFormsControl) = c.staticControl.element.attributeValue("class").splitTo[Set]().contains(FrExcludeFromEmailBody)

    def isRepeatedGridComponent(control: XFormsControl): Boolean =
      control match {
        case c: XFormsComponentControl if c.localName == "grid" && isRepeat(c) => true
        case _                                                                 => false
      }

    // Controls can be excluded via isExcluded (class on control) or controlNamesToExclude (retrieved from email template)

    val selectedControls =
      controls.values  filter
        (_.isRelevant) filterNot
        isExcluded     filter
        (c => isBoundToFormDataInScope(c) || isRepeatedGridComponent(c))

    val sortedControls =
      selectedControls.toList.sortBy(c => ErrorSummary.controlSearchIndexes(c.absoluteId))(ErrorSummary.IntIteratorOrdering)

    val controlMetadata =
      for {
        control       <- sortedControls
        staticControl = control.staticControl
        if ! staticControl.staticId.startsWith("fb-lhh-editor-for-") // HACK for this in grid.xbl
        controlName   <- FormRunner.controlNameFromIdOpt(control.getId)
        if ! controlNamesToExclude.contains(controlName)
      } yield {

        val singleNodeControlOpt = control.narrowTo[XFormsSingleNodeControl]

        val boundNodeOpt         = singleNodeControlOpt flatMap (_.boundNodeOpt)
        val dataHashOpt          = boundNodeOpt map SubmissionUtils.dataNodeHash

        dataHashOpt -> {

          val lhhaAndItemsList: List[(Lang, (List[(LHHA, String)], List[ItemNode]))] =
            List(
              (
                currentLang,
                (
                  List(
                    LHHA.Label -> control.getLabel(collector),
                    LHHA.Help  -> control.getHelp(collector),
                    LHHA.Hint  -> control.getHint(collector),
                    LHHA.Alert -> control.getAlert(collector)
                  ),
                  for {
                    sc             <- control.narrowTo[XFormsSelect1Control].toList
                    (valueNode, _) <- sc.getItemset(collector).allItemsWithValueIterator(reverse = false)
                  } yield
                    valueNode
                )
              )
            )

          val valueOpt =
            Option(control) collect {
              case c: XFormsSelectControl  =>

                val selectedLabels = c.findSelectedItems(collector) map (_.label.label) // TODO: HTML

                MultipleControlValue(c.getValue(collector), selectedLabels) // TODO

              case c: XFormsSelect1Control =>

                val selectedLabel = c.findSelectedItem(collector) map (_.label.label) // TODO: HTML

                SingleControlValue(c.getValue(collector), selectedLabel) // TODO

              case c: XFormsValueComponentControl if c.staticControl.commonBinding.modeSelection =>

                val selectionControlOpt = ItemsetSupport.findSelectionControl(c)

                selectionControlOpt match {
                  case Some(selectControl: XFormsSelectControl) =>
                    val selectedLabels = selectControl.findSelectedItems(collector) map (_.label.label)  // TODO: HTML
                    MultipleControlValue(selectControl.getValue(collector), selectedLabels) // TODO
                  case Some(select1Control) =>

                    // HACK for https://github.com/orbeon/orbeon-forms/issues/4042
                    val itemOpt =
                      if (c.staticControl.element.getQName == QName("dropdown-select1", XMLNames.FRNamespace))
                        select1Control.findSelectedItem(collector) filter (_.value match {
                          case Left(v) if v.nonAllBlank => true
                          case _                        => false
                        })
                      else
                        select1Control.findSelectedItem(collector)

                    // TODO: HTML
                    val selectedLabelOpt  = itemOpt map (_.label.label) orElse "".some // use a blank string so we get `N/A` in the end
                    SingleControlValue(select1Control.getValue(collector), selectedLabelOpt)
                  case None =>
                    throw new IllegalStateException
                }

              case c: XFormsOutputControl =>

                // Special case: if there is a "Calculated Value" control, but which has a blank value in the
                // instance and no initial/calculated expressions, then consider that this control doesn't have
                // a formatted value.
                val noCalculationAndIsEmpty =
                  ! c.bind.get.staticBind.hasDefaultOrCalculateBind && c.getValue(collector).trimAllToOpt.isEmpty

                val formattedValue =
                  if (noCalculationAndIsEmpty)
                    None
                  else
                    c.getFormattedValue(collector) orElse Option(c.getValue(collector))

                SingleControlValue(c.getValue(collector), formattedValue)

              case c: XFormsValueControl if Set("image-attachment", "attachment")(staticControl.localName) =>

                SingleControlValue(c.getValue(collector), c.boundNodeOpt flatMap (_.attValueOpt("filename")))

              case c: XFormsValueControl =>

                SingleControlValue(c.getValue(collector), c.getFormattedValue(collector) orElse Option(c.getValue(collector)))
            }

          // Include sections and repeated grids only
          def ancestorLevelContainers(control: XFormsControl): Iterator[XFormsComponentControl] =
            new AncestorOrSelfIterator(control.parent) collect {
              case c: XFormsComponentControl if isSection(c)             => c
              case c: XFormsComponentControl if isGrid(c) && isRepeat(c) => c
            }

          val repeatDepth = control.staticControl.ancestorRepeatsAcrossParts.size

          val id = XFormsId.fromEffectiveId(control.effectiveId)

          ControlDetails(
            name         = controlName,
            typ          = staticControl.localName,
            level        = ancestorLevelContainers(control).size,
            repeated     = isRepeat(control),
            iterations   = id.iterations drop (repeatDepth - 1),
            datatype     = control.narrowTo[XFormsSelectControl] flatMap (_.getTypeLocalNameOpt),
            lhhaAndItems = lhhaAndItemsList,
            value        = valueOpt,
            forHashes    = Nil
          )
        }
      }

    controlMetadata groupByKeepOrder (x => x._2) map { case (elem, hashes) =>
      elem.copy(forHashes = hashes flatMap (_._1))
    }
  }
}