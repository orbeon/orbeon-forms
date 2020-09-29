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
package org.orbeon.oxf.xforms.analysis.model

import cats.implicits.catsSyntaxOptionId
import org.orbeon.dom._
import org.orbeon.oxf.xforms.analysis.model.Model._
import org.orbeon.oxf.xforms.analysis.{StaticStateContext, _}
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xforms.xbl.XBLBindingBuilder
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.XXBLScope
import org.orbeon.xforms.xbl.Scope

import scala.collection.{mutable => m}

/**
 * Static analysis of an XForms model <xf:model> element.
 */
class Model(
  staticStateContext : StaticStateContext,
  elem               : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends SimpleElementAnalysis(staticStateContext, elem, parent, preceding, scope)
  with WithChildrenTrait
  with ModelInstances
  with ModelVariables
  with ModelSubmissions
  with ModelEventHandlers
  with ModelBinds {

  require(staticStateContext ne null)
  require(scope ne null)

  override lazy val model = this.some

  override def getChildrenContext = defaultInstancePrefixedId map { defaultInstancePrefixedId => // instance('defaultInstanceId')
    PathMapXPathAnalysis(
      partAnalysis              = part,
      xpathString               = PathMapXPathAnalysis.buildInstanceString(defaultInstancePrefixedId),
      namespaceMapping          = null,
      baseAnalysis              = None,
      inScopeVariables          = Map.empty[String, VariableTrait],
      pathMapContext            = null,
      scope                     = scope,
      defaultInstancePrefixedId = Some(defaultInstancePrefixedId),
      locationData              = locationData,
      element                   = element,
      avt                       = false
    )
  }

  // Above we only create actions, submissions and instances as children. But binds are also indexed so add them.
  override def indexedElements: Iterator[ElementAnalysis] =
    super.indexedElements ++ bindsById.valuesIterator

  override def freeTransientState(): Unit = {
    super.freeTransientState()
    freeVariablesTransientState()
    freeBindsTransientState()
  }
}

trait ModelInstances {

  self: Model =>

  // Instance objects
  lazy val instances: collection.Map[String, Instance] =
    m.LinkedHashMap(children collect { case instance: Instance => instance.staticId -> instance }: _*)

  // General info about instances
  lazy val hasInstances = instances.nonEmpty
  lazy val defaultInstanceOpt = instances.headOption map (_._2)
  lazy val defaultInstanceStaticId = instances.headOption map (_._1) orNull
  lazy val defaultInstancePrefixedId = Option(if (hasInstances) scope.fullPrefix + defaultInstanceStaticId else null)
  // TODO: instances on which MIPs depend
}

trait ModelVariables {

  self: Model =>

  // NOTE: It is possible to imagine a model having in-scope variables, but this is not supported now
  override lazy val inScopeVariables = Map.empty[String, VariableTrait]

  // Get *:variable/*:var elements
  private val variableElements = self.element.elements filter (e => ControlAnalysisFactory.isVariable(e.getQName))

  // Handle variables
  val variablesSeq: Seq[VariableAnalysisTrait] = {

    // NOTE: For now, all top-level variables in a model are visible first, then only are binds variables visible.
    // In the future, we might want to change that to use document order between variables and binds, but some
    // more thinking is needed wrt the processing model.

    val someSelf = self.some

    // Iterate and resolve all variables in order
    var preceding: Option[VariableAnalysisTrait] = None

    for {
      variableElement <- variableElements
      analysis: VariableAnalysisTrait = {
        val result = new SimpleElementAnalysis(staticStateContext, variableElement, someSelf, preceding, scope) with VariableAnalysisTrait
        preceding = result.some
        result
      }
    } yield
      analysis
  }

  val variablesMap: Map[String, VariableAnalysisTrait] = variablesSeq map (variable => variable.name -> variable) toMap

  def freeVariablesTransientState(): Unit =
    for (variable <- variablesSeq)
      variable.freeTransientState()
}

trait ModelSubmissions {

  self: Model =>

  // Submissions (they are all direct children)
  lazy val submissions: Seq[Submission] = children collect { case s: Submission => s }
}

trait ModelEventHandlers {

  self: Model =>

  // Event handlers, including on submissions and within nested actions
  lazy val eventHandlers: Seq[EventHandlerImpl] = descendants collect { case e: EventHandlerImpl => e } toList
}

trait ModelBinds {

  selfModel: Model =>

  // FIXME: A bit unhappy with this. Laziness desired because of init order issues with the superclass. There has to be a simpler way!
  private class LazyConstant[T](evaluate: => T) extends (() => T) {
    private lazy val result = evaluate
    def apply() = result
  }

  // Q: Why do we pass isCustomMIP to BindTree? Init order issue?
  private def isCustomMIP: QName => Boolean = {

    import ElementAnalysis.attQNameSet

    def canBeCustomMIP(qName: QName) =
      qName.namespace.prefix.nonEmpty &&
      ! qName.namespace.prefix.startsWith("xml") &&
      (StandardCustomMIPsQNames(qName) || ! NeverCustomMIPsURIs(qName.namespace.uri))

    Option(selfModel.element.attribute(XXFORMS_CUSTOM_MIPS_QNAME)) match {
      case Some(_) =>
        // If the attribute is present, allow all specified QNames if valid, plus standard MIP QNames
        attQNameSet(selfModel.element, XXFORMS_CUSTOM_MIPS_QNAME, namespaceMapping) ++ StandardCustomMIPsQNames filter canBeCustomMIP
      case None    =>
        // Attribute not present: backward-compatible behavior
        canBeCustomMIP
    }
  }

  private var _bindTree = new LazyConstant(new BindTree(selfModel, selfModel.element.elements(XFORMS_BIND_QNAME), isCustomMIP))
  def bindTree = _bindTree()

  private def annotateSubTree(rawElement: Element) = {
    val annotatedTree =
      XBLBindingBuilder.annotateSubtree(
        selfModel.part,
        None,
        rawElement.createDocumentCopyParentNamespaces(detach = false),
        scope,
        scope,
        XXBLScope.Inner,
        containerScope,
        hasFullUpdate = false,
        ignoreRoot = false
      )

    annotatedTree
  }

  def rebuildBinds(rawModelElement: Element): Unit = {

    assert(! selfModel.part.isTopLevel)

    _bindTree().destroy()
    _bindTree = new LazyConstant(
      new BindTree(
        selfModel,
        rawModelElement.elements(XFORMS_BIND_QNAME) map (annotateSubTree(_).getRootElement),
        isCustomMIP
      )
    )
  }

  def bindsById                             = _bindTree().bindsById
  def bindsByName                           = _bindTree().bindsByName

  def hasDefaultValueBind                   = _bindTree().hasDefaultValueBind
  def hasCalculateBind                      = _bindTree().hasCalculateBind
  def hasTypeBind                           = _bindTree().hasTypeBind
  def hasRequiredBind                       = _bindTree().hasRequiredBind
  def hasConstraintBind                     = _bindTree().hasConstraintBind
  def hasNonPreserveWhitespace              = _bindTree().hasNonPreserveWhitespace

  def mustRevalidate                        = _bindTree().mustRevalidate
  def mustRecalculate                       = _bindTree().mustRecalculate

  def bindInstances                         = _bindTree().bindInstances
  def computedBindExpressionsInstances      = _bindTree().computedBindExpressionsInstances
  def validationBindInstances               = _bindTree().validationBindInstances

  // TODO: use and produce variables introduced with xf:bind/@name

  def topLevelBinds                         = _bindTree().topLevelBinds

  def hasBinds                              = _bindTree().hasBinds
  def containsBind(bindId: String)          = _bindTree().bindIds(bindId)

  def figuredAllBindRefAnalysis             = _bindTree().figuredAllBindRefAnalysis
  def recalculateOrder                      = _bindTree().recalculateOrder
  def defaultValueOrder                     = _bindTree().defaultValueOrder

  def freeBindsTransientState()             = _bindTree().freeBindsTransientState()
}

object Model {

  // MIP enumeration
  sealed trait MIP         { def name: String; val aName: QName;                                 val eName: QName }
  sealed trait StdMIP extends MIP { val name: String; val aName = QName(name);                          val eName = QName(name, XFORMS_NAMESPACE_SHORT) }
  sealed trait ExtMIP extends MIP { val name: String; val aName = QName(name, XXFORMS_NAMESPACE_SHORT); val eName = QName(name, XXFORMS_NAMESPACE_SHORT) }

  sealed trait ComputedMIP extends MIP
  sealed trait ValidateMIP extends MIP
  sealed trait XPathMIP    extends MIP
  sealed trait BooleanMIP  extends XPathMIP
  sealed trait StringMIP   extends XPathMIP

  // NOTE: "required" is special: it is evaluated during recalculate, but used during revalidate. In effect both
  // recalculate AND revalidate depend on it. Ideally maybe revalidate would depend on the the *value* of the
  // "required" MIP, not on the XPath of it. See also what we would need for valid(), etc. functions.
  case object Relevant     extends { val name = "relevant"   } with StdMIP with BooleanMIP with ComputedMIP
  case object Readonly     extends { val name = "readonly"   } with StdMIP with BooleanMIP with ComputedMIP
  case object Required     extends { val name = "required"   } with StdMIP with BooleanMIP with ComputedMIP with ValidateMIP
  case object Constraint   extends { val name = "constraint" } with StdMIP with BooleanMIP with ValidateMIP

  case object Calculate    extends { val name = "calculate"  } with StdMIP with StringMIP  with ComputedMIP
  case object Default      extends { val name = "default"    } with ExtMIP with StringMIP  with ComputedMIP
  case object Type         extends { val name = "type"       } with StdMIP with ValidateMIP
  case object Whitespace   extends { val name = "whitespace" } with ExtMIP with ComputedMIP

  //case class Custom(n: String) extends { val name = n }        with StdMIP with XPathMIP

  val AllMIPs                  = Set[MIP](Relevant, Readonly, Required, Constraint, Calculate, Default, Type, Whitespace)
  val AllMIPsInOrder           = AllMIPs.toList.sortBy(_.name)
  val AllMIPNamesInOrder       = AllMIPsInOrder map (_.name)
  val AllMIPsByName            = AllMIPs map (m => m.name -> m) toMap
  val AllMIPNames              = AllMIPs map (_.name)
  val MIPNameToAttributeQName  = AllMIPs map (m => m.name -> m.aName) toMap

  val AllComputedMipsByName    = AllMIPs collect { case m: ComputedMIP => m.name -> m } toMap

  val QNameToXPathComputedMIP  = AllMIPs collect { case m: XPathMIP with ComputedMIP => m.aName -> m } toMap
  val QNameToXPathValidateMIP  = AllMIPs collect { case m: XPathMIP with ValidateMIP => m.aName -> m } toMap
  val QNameToXPathMIP          = QNameToXPathComputedMIP ++ QNameToXPathValidateMIP

  val CalculateMIPNames        = AllMIPs collect { case m: ComputedMIP => m.name }
  val ValidateMIPNames         = AllMIPs collect { case m: ValidateMIP => m.name }
  val BooleanXPathMIPNames     = AllMIPs collect { case m: XPathMIP with BooleanMIP => m.name }
  val StringXPathMIPNames      = AllMIPs collect { case m: XPathMIP with StringMIP => m.name }

  val StandardCustomMIPsQNames = Set(XXFORMS_EVENT_MODE_QNAME)
  val NeverCustomMIPsURIs      = Set(XFORMS_NAMESPACE_URI, XXFORMS_NAMESPACE_URI)

  def buildInternalCustomMIPName(qName: QName): String = qName.qualifiedName
  def buildExternalCustomMIPName(name: String): String = name.replace(':', '-')

  // MIP default values
  val DEFAULT_RELEVANT   = true
  val DEFAULT_READONLY   = false
  val DEFAULT_REQUIRED   = false
  val DEFAULT_VALID      = true
  val DEFAULT_CONSTRAINT = true

  // NOTE: If changed, QName returned has an empty prefix.
  def getVariationTypeOrKeep(datatype: QName) =
    if (XFormsVariationTypeNames(datatype.localName))
      if (datatype.namespace.uri == XFORMS_NAMESPACE_URI)
        QName(datatype.localName, "", XSD_URI)
      else if (datatype.namespace.uri == XSD_URI)
        QName(datatype.localName, "", XFORMS_NAMESPACE_URI)
      else
        datatype
    else
      datatype

  def uriForBuiltinTypeName(builtinTypeString: String, required: Boolean) =
    if (XFormsTypeNames(builtinTypeString) || ! required && XFormsVariationTypeNames(builtinTypeString))
      XFORMS_NAMESPACE_URI
    else
      XSD_URI

  // NOTE: QName returned has an empty prefix.
  def qNameForBuiltinTypeName(builtinTypeString: String, required: Boolean) =
    QName(builtinTypeString, "", uriForBuiltinTypeName(builtinTypeString, required))

  val XFormsSchemaTypeNames = Set(
    "dayTimeDuration",
    "yearMonthDuration",
    "card-number"
  )

  private val CoreXFormsVariationTypeNames = Set(
    "dateTime",
    "time",
    "date",
    "gYearMonth",
    "gYear",
    "gMonthDay",
    "gDay",
    "gMonth",
    "string",
    "boolean",
    "base64Binary",
    "hexBinary",
    "float",
    "decimal",
    "double",
    "anyURI",
    "QName"
  )

  private val SecondaryXFormsVariationTypeNames = Set(
    "normalizedString",
    "token",
    "language",
    "Name",
    "NCName",
    "ID",
    "IDREF",
    "IDREFS",
    "NMTOKEN",
    "NMTOKENS",
    "integer",
    "nonPositiveInteger",
    "negativeInteger",
    "long",
    "int",
    "short",
    "byte",
    "nonNegativeInteger",
    "unsignedLong",
    "unsignedInt",
    "unsignedShort",
    "unsignedByte",
    "positiveInteger"
  )

  val XFormsVariationTypeNames =
    CoreXFormsVariationTypeNames ++ SecondaryXFormsVariationTypeNames

  private val XForms11TypeNames = Set(
    "listItem",
    "listItems",
    "dayTimeDuration",
    "yearMonthDuration",
    "email",
    "card-number"
  )

  private val XForms20TypeNames = Set(
    "HTMLFragment"
  )

  val XFormsTypeNames =
    XForms11TypeNames ++ XForms20TypeNames
}
