package org.orbeon.oxf.xforms.analysis.model

import org.orbeon.dom._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.Whitespace.Policy.Preserve
import org.orbeon.oxf.util.Whitespace._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.model.ModelDefs._
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.model.ValidationLevel

import scala.util.Try

// Represent a static <xf:bind> element
class StaticBind(
  bindTree  : BindTree,
  element   : Element,
  parent    : ElementAnalysis,
  preceding : Option[ElementAnalysis]
) extends ElementAnalysis(
  bindTree.model.part,
  bindTree.model.index,
  element,
  Some(parent),
  preceding,
  bindTree.model.scope
) {

  staticBind =>

  def parentBind: Option[StaticBind] = parent match {
    case parentBind: StaticBind => Some(parentBind)
    case _                      => None
  }

  val ancestorBinds: List[StaticBind] = parentBind match {
    case Some(parent) => parent :: parent.ancestorBinds
    case None         => Nil
  }

  val ancestorOrSelfBinds: List[StaticBind] = staticBind :: ancestorBinds

  // Represent an individual MIP on an <xf:bind> element
  trait MIP {
    val id    : String
    val name  : String
    val level : ValidationLevel

    // WARNING: The following requires early initialization of `name`.
    val isCalculateComputedMIP = CalculateMIPNames(name)
    val isValidateMIP          = ValidateMIPNames(name)
  }

  // Represent an XPath MIP
  class XPathMIP(
    val id     : String,
    val name   : String,
    val level  : ValidationLevel,
    expression : String
  ) extends MIP {

    // Compile the expression right away
    val compiledExpression: XPath.CompiledExpression = {

      val booleanOrStringExpression =
        if (BooleanXPathMIPNames(name))
          XPath.makeBooleanExpression(expression)
        else
          XPath.makeStringExpression(expression)

      XPath.compileExpression(
        xpathString      = booleanOrStringExpression,
        namespaceMapping = staticBind.namespaceMapping,
        locationData     = staticBind.locationData,
        functionLibrary  = part.staticState.functionLibrary,
        avt              = false
      )
    }

    // Default to negative, analyzeXPath() can change that
    var analysis: XPathAnalysis = new NegativeAnalysis(expression)
  }

  object XPathMIP {
    def createOrNone(
      id         : String,
      name       : String,
      level      : ValidationLevel,
      expression : String
    ): Option[XPathMIP] = {
      // 1. Ignoring errors makes sense for Form Builder. For other dynamic components, which we don't have as of
      //    2015-04-06, we should also fail at this point. In short, this should probably be decided by an
      //    attribute  of xxf:dynamic. This should also allow providing feedback to the Form Builder user.
      // 2. When using Try, we should capture errors so that xxf:dynamic can report them.
      if (part.isTopLevel)
        Some(new XPathMIP(id, name, level, expression))
      else
        Try(new XPathMIP(id, name, level, expression)).toOption
    }
  }

  // The type MIP is not an XPath expression
  class TypeMIP(val id: String, val datatype: String) extends {
    val name  = Type.name
    val level = ValidationLevel.ErrorLevel
  } with MIP

  class WhitespaceMIP(val id: String, val policy: Policy) extends {
    val name  = ModelDefs.Whitespace.name
    val level = ValidationLevel.ErrorLevel
  } with MIP

  // Globally remember binds ids
  bindTree.bindIds += staticId
  bindTree.bindsById += staticId -> staticBind

  // Remember variables mappings
  val nameOpt = Option(element.attributeValue(NAME_QNAME))
  nameOpt foreach { name =>
    bindTree.bindsByName += name -> staticBind
  }

  // Type MIP is special as it is not an XPath expression
  val typeMIPOpt: Option[TypeMIP] = {

    def fromAttribute =
      for (value <- Option(element.attributeValue(TYPE_QNAME)))
      yield new TypeMIP(staticId, value)

    // For a while we supported <xf:validation>
    def fromNestedElementsLegacy =
      for {
        e           <- element.elements(XFORMS_VALIDATION_QNAME)
        value       <- Option(e.attributeValue(TYPE_QNAME))
      } yield
        new TypeMIP(e.idOrNull, value)

    def fromNestedElement =
      for {
        e           <- element.elements(XFORMS_TYPE_QNAME) // `<xf:type>`
        value       <- e.getText.trimAllToOpt              // text literal (doesn't support `@value`)
      } yield
        new TypeMIP(e.idOrNull, value)

    // Only support collecting one type MIP per bind (but at runtime more than one type MIP can touch a given node)
    fromAttribute orElse fromNestedElement.headOption orElse fromNestedElementsLegacy.headOption
  }

  val dataType: Option[QName] =
    typeMIPOpt map (_.datatype) flatMap
      (Extensions.resolveQName(namespaceMapping.mapping, _, unprefixedIsNoNamespace = true))

  val nonPreserveWhitespaceMIPOpt: Option[WhitespaceMIP] = {
    Option(element.attributeValue(XXFORMS_WHITESPACE_QNAME)) map
    (Policy.withNameOption(_) getOrElse Preserve)            filterNot
    (_ == Preserve)                                          map
    (new WhitespaceMIP(element.idOrNull, _))
  }

  // Built-in XPath MIPs
  val mipNameToXPathMIP: Iterable[(String, List[XPathMIP])] = {

    def fromAttribute(name: QName) =
      for (value <- Option(element.attributeValue(name)).toList)
      yield (staticId, value, ValidationLevel.ErrorLevel)

    // For a while we supported `<xf:validation>`
    def fromNestedElementLegacy(name: QName) =
      for {
        e     <- element.elements(XFORMS_VALIDATION_QNAME).toList
        value <- e.attributeValueOpt(name)
      } yield
        (e.idOrNull, value, e.attributeValueOpt(LEVEL_QNAME) map ValidationLevel.LevelByName getOrElse ValidationLevel.ErrorLevel)

    def fromNestedElement(name: QName) =
      for {
        e     <- element.elements(name).toList
        value <- Option(e.attributeValue(VALUE_QNAME))
      } yield
        (e.idOrNull, value, e.attributeValueOpt(LEVEL_QNAME) map ValidationLevel.LevelByName getOrElse ValidationLevel.ErrorLevel)

    for {
      mip              <- QNameToXPathMIP.values
      idValuesAndLevel = fromNestedElement(mip.eName) ::: fromNestedElementLegacy(mip.aName) ::: fromAttribute(mip.aName)
      if idValuesAndLevel.nonEmpty
      mips             = idValuesAndLevel flatMap {
        case (id, value, level) =>
          // Ignore level for non-constraint MIPs as it's not supported yet
          val overriddenLevel = if (mip.name == Constraint.name) level else ValidationLevel.ErrorLevel
          XPathMIP.createOrNone(id, mip.name, overriddenLevel, value)
      }
    } yield
      mip.name -> mips
  }

  // Custom XPath MIPs
  val customMIPNameToXPathMIP: Map[String, List[XPathMIP]] = { // Q: Why String -> List[XPathMIP] and not String -> XPathMIP?

    def attributeCustomMIP =
      for {
        att           <- element.attributes.iterator
        if bindTree.isCustomMIP(att.getQName)
        value         = att.getValue
        customMIPName = buildInternalCustomMIPName(att.getQName)
      } yield
        (staticId, customMIPName, value)

    // NOTE: We don't support custom MIP elements yet as those are pruned by the annotator/extractor. The following
    // function can be used to retrieve them once that is fixed.
    // def elementCustomMIPs =
    //     for {
    //         elem          <- Dom4j.elements(element).toList
    //         if bindTree.isCustomMIP(elem.getQName)
    //         value         <- Option(elem.attributeValue(VALUE_QNAME))
    //         customMIPName = buildCustomMIPName(elem.getQualifiedName)
    //     } yield
    //         (getElementId(elem), customMIPName, value)

    for {
      (name, idNameValue) <- attributeCustomMIP.toList groupBy (_._2)
      mips                = idNameValue flatMap {
        case (id, _, value) =>
          XPathMIP.createOrNone(id, name, ValidationLevel.ErrorLevel, value)
      }
    } yield
      name -> mips
  }

  // All XPath MIPs
  val allMIPNameToXPathMIP = customMIPNameToXPathMIP ++ mipNameToXPathMIP

  // All constraint XPath MIPs grouped by level
  val constraintsByLevel: Map[ValidationLevel, List[XPathMIP]] = {
    val levelWithMIP =
      for {
        mips <- allMIPNameToXPathMIP.get(Constraint.name).toList
        mip  <- mips
      } yield
        mip

    levelWithMIP groupBy (_.level)
  }

  val hasCalculateComputedMIPs = mipNameToXPathMIP exists { case (_, mips) => mips exists (_.isCalculateComputedMIP)}
  val hasValidateMIPs          = typeMIPOpt.nonEmpty || (mipNameToXPathMIP exists { case (_, mips) => mips exists (_.isValidateMIP) })
  val hasCustomMIPs            = customMIPNameToXPathMIP.nonEmpty
  val hasMIPs                  = hasCalculateComputedMIPs || hasValidateMIPs || hasCustomMIPs || nonPreserveWhitespaceMIPOpt.isDefined

  def iterateNestedIds: Iterator[String] =
    for {
      elem <- element.elements.iterator
      id   <- elem.idOpt
    } yield
      id

  // Create children binds
  private var _children: Seq[StaticBind] = element.elements(XFORMS_BIND_QNAME) map (new StaticBind(bindTree, _, staticBind, None))// NOTE: preceding not handled for now
  def children: Seq[StaticBind] = _children

  // Globally remember if we have seen these categories of binds
  bindTree.hasDefaultValueBind      ||= getXPathMIPs(Default.name).nonEmpty
  bindTree.hasCalculateBind         ||= getXPathMIPs(Calculate.name).nonEmpty
  bindTree.hasRequiredBind          ||= getXPathMIPs(Required.name).nonEmpty

  bindTree.hasTypeBind              ||= typeMIPOpt.nonEmpty
  bindTree.hasConstraintBind        ||= constraintsByLevel.nonEmpty

  bindTree.mustRecalculate          ||= hasCalculateComputedMIPs || hasCustomMIPs || nonPreserveWhitespaceMIPOpt.isDefined
  bindTree.mustRevalidate           ||= hasValidateMIPs

  bindTree.hasNonPreserveWhitespace ||= nonPreserveWhitespaceMIPOpt.isDefined

  def hasDefaultOrCalculateBind = getXPathMIPs(Default.name).nonEmpty || getXPathMIPs(Calculate.name).nonEmpty

  def addBind(bindElement: Element, precedingId: Option[String]): Unit =
    _children = _children :+ new StaticBind(bindTree, bindElement, staticBind, None)// NOTE: preceding not handled for now

  def removeBind(bind: StaticBind): Unit = {
    bindTree.bindIds -= bind.staticId
    bindTree.bindsById -= bind.staticId
    bind.nameOpt foreach { name =>
      bindTree.bindsByName -= name
    }

    _children = _children filterNot (_ eq bind)

    part.unmapScopeIds(bind)
  }

  // Used by PathMapXPathDependencies
  def getXPathMIPs(mipName: String): List[XPathMIP] = allMIPNameToXPathMIP.getOrElse(mipName, Nil)

  // TODO: Support multiple relevant, readonly, and required MIPs.
  def firstXPathMIP(mip: ModelDefs.XPathMIP): Option[XPathMIP] = allMIPNameToXPathMIP.getOrElse(mip.name, Nil).headOption
  def hasXPathMIP  (mip: ModelDefs.XPathMIP): Boolean          = firstXPathMIP(mip).isDefined

  override def freeTransientState(): Unit = {

    super.freeTransientState()

    for (mips <- allMIPNameToXPathMIP.values; mip <- mips)
      mip.analysis.freeTransientState()

    for (child <- _children)
      child.freeTransientState()
  }
}
