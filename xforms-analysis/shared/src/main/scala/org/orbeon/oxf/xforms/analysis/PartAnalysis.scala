package org.orbeon.oxf.xforms.analysis

import cats.syntax.option._
import org.orbeon.dom
import org.orbeon.oxf.xforms.{PartGlobalOps, _}
import org.orbeon.oxf.xforms.analysis.model._
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.saxon.om
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


case class Global(templateTree: SAXStore, compactShadowTree: dom.Document)

trait TopLevelPartAnalysis
  extends PartAnalysis {

  def bindingIncludes: Set[String]
  def bindingsIncludesAreUpToDate: Boolean
  def debugOutOfDateBindingsIncludes: String
}

trait PartAnalysisForStaticMetadataAndProperties {
  def startScope: Scope
  def findControlAnalysis(prefixedId: String): Option[ElementAnalysis]
  def ancestorIterator      : Iterator[PartAnalysis]
  def ancestorOrSelfIterator: Iterator[PartAnalysis]
}

trait PartAnalysis
  extends PartAnalysisRuntimeOps
     with PartAnalysisForStaticMetadataAndProperties{

  private def partAnalysisIterator(start: Option[PartAnalysis]): Iterator[PartAnalysis] =
    new Iterator[PartAnalysis] {

      private[this] var theNext = start

      def hasNext: Boolean = theNext.isDefined

      def next(): PartAnalysis = {
        val newResult = theNext.get
        theNext = newResult.parent
        newResult
      }
    }

  def ancestorIterator      : Iterator[PartAnalysis] = partAnalysisIterator(parent)
  def ancestorOrSelfIterator: Iterator[PartAnalysis] = partAnalysisIterator(this.some)
}

trait PartAnalysisRuntimeOps extends PartGlobalOps {

  def parent: Option[PartAnalysis]
  def startScope: Scope
  def isTopLevelPart: Boolean

  def functionLibrary: FunctionLibrary

  def getNamespaceMapping(prefixedId: String): Option[NamespaceMapping]
  def hasControls: Boolean
  def defaultModel: Option[Model]
  def getModel(prefixedId: String): Model
  def getTopLevelControls: List[ElementAnalysis]
  def observerHasHandlerForEvent(observerPrefixedId: String, eventName: String): Boolean
  def controlElement(prefixedId: String): Option[om.NodeInfo]
  def getNamespaceMapping(prefix: String, element: dom.Element): NamespaceMapping

  def getNamespaceMapping(scope: Scope, id: String): NamespaceMapping =
    getNamespaceMapping(scope.prefixedIdForStaticId(id)) getOrElse
      (throw new IllegalStateException(s"namespace mappings not cached for scope `$scope` on element with id `$id`"))
}