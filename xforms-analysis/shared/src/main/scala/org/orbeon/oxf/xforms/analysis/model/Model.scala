package org.orbeon.oxf.xforms.analysis.model

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.xforms.analysis.controls.VariableAnalysisTrait
import org.orbeon.oxf.xforms.analysis.model.ModelDefs.{NeverCustomMIPsURIs, StandardCustomMIPsQNames}
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, EventHandler, WithChildrenTrait}
import org.orbeon.xforms.XFormsNames.XXFORMS_CUSTOM_MIPS_QNAME
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

import scala.collection.mutable


class Model(
  index            : Int,
  elem             : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ElementAnalysis(index, elem, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
  with WithChildrenTrait
  with ModelInstances
  with ModelVariables
  with ModelSubmissions
  with ModelEventHandlers
  with ModelBinds {

  require(scope ne null)

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    freeVariablesTransientState()
    freeBindsTransientState()
  }
}

class ModelVariable(
  index                    : Int,
  element                  : Element,
  parent                   : Option[ElementAnalysis],
  preceding                : Option[ElementAnalysis],
  staticId                 : String,
  prefixedId               : String,
  namespaceMapping         : NamespaceMapping,
  scope                    : Scope,
  containerScope           : Scope,
  val name                 : String,
  val expressionOrConstant : Either[String, String]
) extends ElementAnalysis(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
  with VariableAnalysisTrait

trait ModelInstances {

  self: Model =>

  // Instance objects
  // `lazy` because the children are evaluated after the container
  lazy val instances: collection.Map[String, Instance] = {
    val m = mutable.LinkedHashMap[String, Instance]()
    m ++= children.iterator collect { case instance: Instance => instance.staticId -> instance }
    m
  }

  // General info about instances
  lazy val hasInstances = instances.nonEmpty
  lazy val defaultInstanceOpt = instances.headOption map (_._2)
  lazy val defaultInstanceStaticId = instances.headOption map (_._1) orNull
  lazy val defaultInstancePrefixedId = Option(if (hasInstances) scope.fullPrefix + defaultInstanceStaticId else null)
  // TODO: instances on which MIPs depend
}

trait ModelVariables {

  self: Model =>

  // `lazy` because the children are evaluated after the container
  lazy val variablesSeq: Iterable[VariableAnalysisTrait] = children collect { case v: VariableAnalysisTrait => v }
  lazy val variablesMap: Map[String, VariableAnalysisTrait] = variablesSeq map (variable => variable.name -> variable) toMap

  def freeVariablesTransientState(): Unit =
    for (variable <- variablesSeq)
      variable.freeTransientState()
}

trait ModelSubmissions {

  self: Model =>

  // Submissions (they are all direct children)
  // `lazy` because the children are evaluated after the container
  lazy val submissions: List[Submission] = children collect { case s: Submission => s } toList
}

trait ModelEventHandlers {

  self: Model =>

  // Event handlers, including on submissions and within nested actions
  lazy val eventHandlers: List[EventHandler] = descendants collect { case e: EventHandler => e } toList
}

trait ModelBinds extends BindTree {

  selfModel: Model =>

  // Q: Why do we pass isCustomMIP to BindTree? Init order issue?
  def isCustomMIP: QName => Boolean = {

    import ElementAnalysis.attQNameSet

    def canBeCustomMIP(qName: QName) =
      qName.namespace.prefix.nonEmpty &&
      ! qName.namespace.prefix.startsWith("xml") &&
      (StandardCustomMIPsQNames(qName) || ! NeverCustomMIPsURIs(qName.namespace.uri))

    selfModel.element.attributeOpt(XXFORMS_CUSTOM_MIPS_QNAME) match {
      case Some(_) =>
        // If the attribute is present, allow all specified QNames if valid, plus standard MIP QNames
        attQNameSet(selfModel.element, XXFORMS_CUSTOM_MIPS_QNAME, namespaceMapping) ++ StandardCustomMIPsQNames filter canBeCustomMIP
      case None    =>
        // Attribute not present: backward-compatible behavior
        canBeCustomMIP
    }
  }

  def iterateAllBinds: Iterator[StaticBind] = {

    def iterateBinds(bindsIt: Iterator[StaticBind]): Iterator[StaticBind] =
      bindsIt flatMap (b => Iterator(b) ++ iterateBinds(b.childrenBindsIt))

    iterateBinds(topLevelBinds.iterator)
  }

  // TODO: use and produce variables introduced with `xf:bind/@name`
}
