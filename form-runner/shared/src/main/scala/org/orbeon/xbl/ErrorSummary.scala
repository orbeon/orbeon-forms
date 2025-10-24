/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.library.FRComponentParamSupport
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.xforms.NodeInfoFactory.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.control.{VisitableTrait, XFormsComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.XFormsEvent.xxfName
import org.orbeon.oxf.xforms.event.events.*
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.{Item, NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeInfoConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.analysis.model.ValidationLevel

import scala.collection.Searching.*
import scala.collection.Searching.*
import scala.collection.mutable.IndexedSeqOps
import scala.collection.{IterableFactoryDefaults, IterableOnce, Iterator, SeqFactory, StrictOptimizedSeqOps, mutable}
import scala.math.Ordering


object ErrorSummary {

  import Private.*

  // Return the subset of section names passed which contain errors in the global error summary
  def topLevelSectionsWithErrors(sectionNamesSet: Set[String], onlyVisible: Boolean): Map[String, (Int, Int)] =
    findErrorsInstance match {
      case Some(errorsInstance) =>

        val relevantErrorsIt = {

          def allErrorsIt =
            (errorsInstance.rootElement / ErrorElemName).iterator

          (if (onlyVisible) visibleErrorsIt else allErrorsIt) filter (_.attValue(LevelAttName) == ValidationLevel.ErrorLevel.entryName)
        }

        val sectionNameErrors = (
          relevantErrorsIt
          map    { e => e.attValue(SectionNameAttName) -> e }
          filter { case (name, _) => sectionNamesSet(name) }
          toList
        )

        sectionNameErrors groupBy (_._1) map  { case (sectionName, list) =>

          val requiredButEmptyCount =
            list count (_._2.attValue(RequiredEmptyAttName) == true.toString)

          sectionName -> (requiredButEmptyCount, list.size - requiredButEmptyCount)
        }

      case None =>
        Map.empty
    }

  // Returns all sections that contain an invalid control, either directly or indirectly through a subsection
  def sectionsWithVisibleErrors: List[String] = {
    val invalidControlIds    = visibleErrorsIt.map(_.attValue(IdAttName))
    val sectionsWithErrorsIt = invalidControlIds.flatMap { absoluteControlId =>
      val effectiveControlId = XFormsId.absoluteIdToEffectiveId(absoluteControlId)
      val controlOpt         = inScopeContainingDocument.findControlByEffectiveId(effectiveControlId)
      controlOpt.iterator flatMap { control =>
        val containingSections = FRComponentParamSupport.ancestorSectionsIt(control)
        containingSections.map(_.getId).flatMap(frc.controlNameFromIdOpt)
      }
    }
    sectionsWithErrorsIt.toList
  }

  def controlSearchIndexes(absoluteId: String): Iterator[Int] = {

    val effectiveId = XFormsId.absoluteIdToEffectiveId(absoluteId)
    val prefixedId  = XFormsId.getPrefixedId(effectiveId)

    val repeatsFromLeaf =
      inScopeContainingDocument.staticOps.getAncestorRepeats(prefixedId)

    val iterations =
      XFormsId.getEffectiveIdSuffixParts(effectiveId)

    val repeatsIt =
      repeatsFromLeaf.reverseIterator map (_.index) zip iterations.iterator flatMap {
        case (index, iteration) => Iterator(index, iteration)
      }

    repeatsIt ++ Iterator.single(inScopeContainingDocument.staticOps.getControlPosition(prefixedId).get) // argument must be a view control
  }

  //@XPathFunction
  def removeUpdateOrInsertError(
    errorsInstanceDoc : DocumentNodeInfoType,
    stateInstanceDoc  : DocumentNodeInfoType,
    visitValueChanged : Boolean,
    visitInvalid      : Boolean
  ): Unit = {

    // This can happen if the caller receives `xforms-disabled` when the dialog is closing, which causes
    // the instances to no longer be accessible by `XBLContainer.findInstance`.
    if ((errorsInstanceDoc eq null) || (stateInstanceDoc eq null))
      return

    // Get the event here so that we don't have to evaluate all the `event()` properties on the
    // XBL side. This is good for performance as not all the properties will be needed. For example,
    // the label is only needed if we are actually inserting or updating an error.
    val event =
      inScopeContainingDocument.currentEventOpt getOrElse (throw new IllegalStateException)

    val absoluteTargetId = XFormsId.effectiveIdToAbsoluteId(event.targetObject.effectiveId)

    val currentErrorOpt = SaxonUtils.selectID(errorsInstanceDoc, absoluteTargetId)

    def xxfProperty[T](allow: Boolean, name: String) =
      if (allow) event.property[T](xxfName(name)) else None

    // This function is called for the following events:
    // xforms-enabled xxforms-constraints-changed xforms-disabled xforms-invalid xforms-valid xforms-value-changed
    val (enabled, eventLevelOpt) = event match {
      case e: XXFormsConstraintsChangedEvent           => true  -> e.property[String]("level") .map(ValidationLevel.withNameInsensitive)
      case _: XFormsEnabledEvent                       => true  -> xxfProperty[String](allow = true, "level").map(ValidationLevel.withNameInsensitive)
      case _: XFormsValidEvent | _: XFormsInvalidEvent => true  -> Some(ValidationLevel.ErrorLevel)
      case _: XFormsDisabledEvent                      => false -> None
      case _                                           => true  -> None
    }

    // Ideally, we would evaluate this lazily, but we use it in the pattern match below
    val alertOpt =
      xxfProperty[String](allow = enabled, "alert")

    def bindingFromEventOpt =
      xxfProperty[Seq[Item]](allow = enabled, "binding") flatMap (_.headOption)

    def labelFromEventOpt =
      xxfProperty[String](allow = enabled, "label")

    def controlPositionFromEvent =
      xxfProperty[Int](allow = enabled, "control-position") getOrElse (throw new IllegalStateException)

    def requiredEmpty =
      bindingFromEventOpt exists {
        case n: NodeInfo => InstanceData.getRequired(n) && n.stringValue.isEmpty
        case _           => false
      }

    val previousStatusIsValid =
      (stateInstanceDoc.rootElement elemValue "valid") == "true"

    def updateValidStatus(value: Boolean) =
      XFormsAPI.setvalue(
        ref   = stateInstanceDoc.rootElement / "valid",
        value = value.toString
      )

    def updateValidStatusByScanning() =
      updateValidStatus(
        ! (errorsInstanceDoc.rootElement / * exists (_.attValue(LevelAttName) == ValidationLevel.ErrorLevel.entryName))
      )

    // https://github.com/orbeon/orbeon-forms/issues/5934
    def markVisited(): Unit =
      event.targetObject match {
        case valueControl: XFormsValueControl with VisitableTrait
          if valueControl.visible => valueControl.visited = true
        case _ =>
      }

    (event, currentErrorOpt, eventLevelOpt, alertOpt) match {
      case (_: XFormsValueChangedEvent, currentErrorOpt, _, alertOpt) =>

        // Maybe update the alert
        currentErrorOpt.lazyZip(alertOpt).foreach { (currentError, alert) =>
          XFormsAPI.setvalue(currentError /@ AlertAttName, alert)
        }

        if (visitValueChanged)
          markVisited()

      case (_, Some(currentError), Some(actualEventLevel), Some(alert)) =>

        val levelAtt      = currentError /@ LevelAttName
        val previousLevel = ValidationLevel.withNameInsensitive(levelAtt.stringValue)

        XFormsAPI.setvalue(levelAtt                            , actualEventLevel.entryName)
        XFormsAPI.setvalue(currentError /@ AlertAttName        , alert)
        XFormsAPI.setvalue(currentError /@ LabelAttName        , labelFromEventOpt getOrElse "")
        XFormsAPI.setvalue(currentError /@ RequiredEmptyAttName, requiredEmpty.toString)

        if (previousLevel != actualEventLevel) {
          if (previousStatusIsValid && actualEventLevel == ValidationLevel.ErrorLevel)
            updateValidStatus(false)
          else if (! previousStatusIsValid && actualEventLevel != ValidationLevel.ErrorLevel)
            updateValidStatusByScanning()

          if (actualEventLevel == ValidationLevel.ErrorLevel && visitInvalid)
            markVisited()
        }

      case (_, Some(currentError), _, _) =>

        XFormsAPI.delete(currentError)

        if ((currentError attValue LevelAttName) == ValidationLevel.ErrorLevel.entryName)
          updateValidStatusByScanning()

      case (_, None, Some(actualEventLevel), Some(alert)) =>

        insertNewError(
          errorsInstanceDoc,
          createNewErrorElem(
            absoluteTargetId = absoluteTargetId,
            controlPosition  = controlPositionFromEvent,
            level            = actualEventLevel,
            alert            = alert,
            labelOpt         = labelFromEventOpt,
            requiredEmpty    = requiredEmpty
          )
        )

        if (visitInvalid)
          markVisited()

        if (previousStatusIsValid && actualEventLevel == ValidationLevel.ErrorLevel)
          updateValidStatus(false)

      case _ =>
    }
  }

  //@XPathFunction
  def updateForMovedIteration(
    errorsInstanceDoc : DocumentNodeInfoType,
    absoluteTargetId  : String,
    fromIterations    : Array[Int],
    toIterations      : Array[Int]
  ): Unit = {

    // This can happen if the caller receives `xforms-disabled` when the dialog is closing, which causes
    // the instances to no longer be accessible by `XBLContainer.findInstance`.
    if (errorsInstanceDoc eq null)
      return

    // Update the iteration in a control's absolute id
    def updateIteration(existingErrorAbsoluteId: String, repeatAbsoluteId: String, fromIterations: Array[Int], toIterations: Array[Int]): String = {

      val existingErrorEffectiveId = XFormsId.absoluteIdToEffectiveId(existingErrorAbsoluteId)
      val existingErrorPrefixedId  = XFormsId.getPrefixedId(existingErrorEffectiveId)

      val repeatEffectiveId = XFormsId.absoluteIdToEffectiveId(repeatAbsoluteId)
      val repeatPrefixedId  = XFormsId.getPrefixedId(repeatEffectiveId)

      val ancestorRepeatsLeafToRoot = inScopeContainingDocument.staticOps.getAncestorRepeatIds(existingErrorPrefixedId)

      if (ancestorRepeatsLeafToRoot contains repeatPrefixedId) {
        // Control is a descendant of the repeat so might be impacted

        val idIterationPairs = XFormsId.getEffectiveIdSuffixParts(existingErrorEffectiveId) zip ancestorRepeatsLeafToRoot.reverse
        val iterationsMap    = fromIterations zip toIterations toMap

        val newIterations = idIterationPairs map {
          case (fromIt, `repeatPrefixedId`) if iterationsMap.contains(fromIt) => iterationsMap(fromIt)
          case (iteration, _)                                                 => iteration
        }

        val newEffectiveId = XFormsId.buildEffectiveId(existingErrorPrefixedId, newIterations)

        XFormsId.effectiveIdToAbsoluteId(newEffectiveId)

      } else
        existingErrorAbsoluteId // id is not impacted
    }

    val rootElem = errorsInstanceDoc.rootElement

    val affectedErrors =
      rootElem / * map { e =>
        e -> updateIteration(e.id, absoluteTargetId, fromIterations, toIterations)
      } filter { case (e, updatedId) =>
        e.id != updatedId
      }

    // Remove affected errors from instance
    XFormsAPI.delete(affectedErrors map (_._1))

    // Reinsert updated errors
    affectedErrors foreach { case (e, updatedId) =>

      insertNewError(
        errorsInstanceDoc,
        createNewErrorElem(
          absoluteTargetId = updatedId,
          controlPosition  = e attValue    PositionAttName toInt,
          level            = ValidationLevel.withNameInsensitive(e attValue LevelAttName),
          alert            = e attValue    AlertAttName,
          labelOpt         = e attValueOpt LabelAttName,
          requiredEmpty    = e attValue    RequiredEmptyAttName toBoolean
        )
      )
    }
  }

  //@XPathFunction
  def ancestorSectionNames(controlAbsoluteId: String): SequenceIterator =
    ancestorSectionNamesUseDocument(controlAbsoluteId, inScopeContainingDocument).toList // https://github.com/orbeon/orbeon-forms/issues/6016

  def ancestorSectionNamesUseDocument(controlAbsoluteId: String, xfcd: XFormsContainingDocument): Iterator[String] =
    xfcd.controls.getCurrentControlTree
      .findControl(XFormsId.absoluteIdToEffectiveId(controlAbsoluteId)).iterator
      .flatMap(FRComponentParamSupport.ancestorSectionNames)

  implicit object IntIteratorOrdering extends Ordering[Iterator[Int]] {
    def compare(x: Iterator[Int], y: Iterator[Int]): Int =
      (x.zipAll(y, 0, 0) dropWhile { case (a, b) => a == b }).nextOption() match {
        case Some((a, b)) => a.compare(b)
        case None         => 0
      }
  }

  private object Private {

    val ErrorSummaryIds          = List("error-summary-control-top", "error-summary-control-bottom")

    val ErrorElemName            = "error"
    val IdAttName                = "id"
    val PositionAttName          = "position"
    val LevelAttName             = "level"
    val AlertAttName             = "alert"
    val LabelAttName             = "label"
    val SectionNameAttName       = "section-name"
    val RequiredEmptyAttName     = "required-empty"

    // Needed for `binarySearch` below
    implicit object ErrorNodeOrdering extends Ordering[dom.Node] {
      def compare(x: dom.Node, y: dom.Node): Int = (x, y) match {
        case (n1: dom.Element, n2: dom.Element) =>

          IntIteratorOrdering.compare(
            x = controlSearchIndexes(n1.attributeValue(IdAttName)),
            y = controlSearchIndexes(n2.attributeValue(IdAttName))
          )

        case (n1: dom.Namespace, n2: dom.Element)   => -1                       // all elements are after the namespace nodes
        case (n1: dom.Element,   n2: dom.Namespace) => +1                       // all elements are after the namespace nodes
        case (n1: dom.Namespace, n2: dom.Namespace) => n1.uri.compareTo(n2.uri) // predictable order even though they won't be sorted
        case _                                      => throw new IllegalStateException
      }
    }

    def visibleErrorsIt: Iterator[NodeInfo] =
      findErrorSummaryModel.iterator flatMap (m => Implicits.asScalaIterator(m.getVariable("visible-errors"))) collect {
        case n: NodeInfo => n
      }

    def findErrorSummaryControl = (
      ErrorSummaryIds
      flatMap      { id => inScopeContainingDocument.findControlByEffectiveId(id) }
      collectFirst { case c: XFormsComponentControl => c }
    )

    def findErrorSummaryModel =
      findErrorSummaryControl flatMap (_.nestedContainerOpt) flatMap (_.models find (_.getId == "fr-error-summary-model"))

    def findErrorsInstance =
      findErrorSummaryModel map (_.getInstance("fr-errors-instance"))

    def createNewErrorElem(
      absoluteTargetId : String,
      controlPosition  : Int,
      level            : ValidationLevel,
      alert            : String,
      labelOpt         : Option[String],
      requiredEmpty    : Boolean
    ): NodeInfo =
      elementInfo(
        ErrorElemName,
        List(
          attributeInfo(IdAttName,            absoluteTargetId),
          attributeInfo(PositionAttName,      controlPosition.toString),
          attributeInfo(LevelAttName,         level.entryName),
          attributeInfo(AlertAttName,         alert),
          attributeInfo(LabelAttName,         labelOpt getOrElse ""),
          attributeInfo(SectionNameAttName,   FRComponentParamSupport.topLevelSectionNameForControlId(absoluteTargetId) getOrElse ""),
          attributeInfo(RequiredEmptyAttName, requiredEmpty.toString)
        )
      )

    // In order to make insertion efficient, the `<error>` elements are kept sorted, without
    // any other children nodes except namespace nodes at the beginning. We then use a binary
    // search to find the insertion point.
    def insertNewError(errorsInstanceDoc: DocumentNodeInfoType, newErrorElem: NodeInfo): Unit = {

      val rootElem = errorsInstanceDoc.rootElement

      import CustomJavaConversions._

      // We work with the underlying DOM here
      val newElemForSorting   = unsafeUnwrapElement(newErrorElem)
      val rootElemDomJContent = unsafeUnwrapElement(rootElem).jContent
      val rootElemDomContent  = rootElemDomJContent.asScala

      val insertionPoint =
        rootElemDomContent.search(newElemForSorting, 0, rootElemDomContent.length) match {
          case InsertionPoint(p) => p
          case Found(_)          => throw new IllegalStateException // must not be an existing error; we know because we search for it above
        }

      val afterElemList =
        (
          rootElemDomContent.nonEmpty &&
          insertionPoint > 0          &&
          ! rootElemDomContent(insertionPoint - 1).isInstanceOf[dom.Namespace]
        ) list
          errorsInstanceDoc.asInstanceOf[DocumentWrapper].wrap(rootElemDomContent(insertionPoint - 1))

      XFormsAPI.insert(
        into          = rootElem,
        after         = afterElemList,
        origin        = newErrorElem
      )
    }
  }
}

// This is so that we can convert `ju.RandomAccess` `ju.List` to an `IndexedBuffer`, which is necessary
// to be able to get a binary search.
object CustomJavaConversions {

  import java.util as ju
  import scala.jdk.CollectionConverters._

  implicit class ListHasAsScala[A](l: ju.List[A] & ju.RandomAccess) {
    def asScala: mutable.IndexedBuffer[A] = l match {
      case null                           => null
      case _                              => JRandomAccessListWrapper(l)
    }
  }

  case class JRandomAccessListWrapper[A](underlying: ju.List[A] & ju.RandomAccess)
    extends mutable.IndexedBuffer[A]
      with IndexedSeqOps[A, JRandomAccessListWrapper, JRandomAccessListWrapper[A]]
      with StrictOptimizedSeqOps[A, JRandomAccessListWrapper, JRandomAccessListWrapper[A]]
      with IterableFactoryDefaults[A, JRandomAccessListWrapper]
      with Serializable {
    def length = underlying.size
    override def knownSize: Int = if (underlying.isEmpty) 0 else super.knownSize
    override def isEmpty = underlying.isEmpty
    override def iterator: Iterator[A] = underlying.iterator.asScala
    def apply(i: Int) = underlying.get(i)
    def update(i: Int, elem: A) = underlying.set(i, elem)
    def prepend(elem: A) = { underlying.subList(0, 0) add elem; this }
    def addOne(elem: A): this.type = { underlying add elem; this }
    def insert(idx: Int,elem: A): Unit = underlying.subList(0, idx).add(elem)
    def insertAll(i: Int, elems: IterableOnce[A]) = {
      val ins = underlying.subList(0, i)
      elems.iterator.foreach(ins.add(_))
    }
    def remove(i: Int) = underlying.remove(i)
    def clear() = underlying.clear()
    // Note: Clone cannot just call underlying.clone because in Java, only specific collections
    // expose clone methods.  Generically, they're protected.
    override def clone(): JRandomAccessListWrapper[A] = JRandomAccessListWrapper(new ju.ArrayList[A](underlying))
    override def patchInPlace(from: Int, patch: scala.collection.IterableOnce[A], replaced: Int): this.type = {
      remove(from, replaced)
      insertAll(from, patch)
      this
    }
    def remove(from: Int, n: Int): Unit = underlying.subList(from, from+n).clear()
    override def iterableFactory: Nothing = ???
    override def subtractOne(elem: A): this.type = { underlying.remove(elem.asInstanceOf[AnyRef]); this }
  }

//  object JRandomAccessListWrapper extends SeqFactory.Delegate[mutable.IndexedBuffer](JRandomAccessListWrapper)
}
