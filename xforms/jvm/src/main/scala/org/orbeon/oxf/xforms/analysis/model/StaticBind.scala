package org.orbeon.oxf.xforms.analysis.model

import org.orbeon.dom._
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.Whitespace.Policy.Preserve
import org.orbeon.oxf.util.Whitespace._
import org.orbeon.oxf.util.XPath
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.XFormsUtils.getElementId
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.model.Model._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevel._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{ShareableXPathStaticContext, XMLReceiverHelper}
import org.orbeon.oxf.{util => u}

import scala.collection.JavaConverters._
import scala.collection.immutable.List
import scala.util.Try
import scala.collection.compat._

// Represent a static <xf:bind> element
class StaticBind(
  bindTree  : BindTree,
  element   : Element,
  parent    : ElementAnalysis,
  preceding : Option[ElementAnalysis]
) extends SimpleElementAnalysis(
  bindTree.model.staticStateContext,
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
          u.XPath.makeBooleanExpression(expression)
        else
          u.XPath.makeStringExpression(expression)

      u.XPath.compileExpression(
        xpathString      = booleanOrStringExpression,
        namespaceMapping = staticBind.namespaceMapping,
        locationData     = staticBind.locationData,
        functionLibrary  = staticStateContext.partAnalysis.staticState.functionLibrary,
        avt              = false
      )
    }

    // Default to negative, analyzeXPath() can change that
    var analysis: XPathAnalysis = NegativeAnalysis(expression)

    def analyzeXPath(): Unit = {

      val allBindVariablesInScope = bindTree.allBindVariables

      // Saxon: "In the case of free-standing XPath expressions it will be the StaticContext object"
      val staticContext  = compiledExpression.expression.getInternalExpression.getContainer.asInstanceOf[ShareableXPathStaticContext]
      if (staticContext ne null) {
        // NOTE: The StaticContext can be null if the expression is a constant such as BooleanValue
        val usedVariables = staticContext.referencedVariables

        // Check whether all variables used by the expression are actually in scope, throw otherwise
        usedVariables find (name => ! allBindVariablesInScope.contains(name.getLocalName)) foreach { name =>
          throw new ValidationException("Undeclared variable in XPath expression: $" + name.getClarkName, staticBind.locationData)
        }
      }

      // Analyze and remember if figured out
      staticBind.analyzeXPath(getChildrenContext, allBindVariablesInScope, compiledExpression) match {
        case valueAnalysis if valueAnalysis.figuredOutDependencies => this.analysis = valueAnalysis
        case _ => // NOP
      }
    }
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
    val level = ErrorLevel
  } with MIP

  class WhitespaceMIP(val id: String, val policy: Policy) extends {
    val name  = Model.Whitespace.name
    val level = ErrorLevel
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
        new TypeMIP(getElementId(e), value)

    def fromNestedElement =
      for {
        e           <- element.elements(XFORMS_TYPE_QNAME) // `<xf:type>`
        value       <- e.getText.trimAllToOpt              // text literal (doesn't support `@value`)
      } yield
        new TypeMIP(getElementId(e), value)

    // Only support collecting one type MIP per bind (but at runtime more than one type MIP can touch a given node)
    fromAttribute orElse fromNestedElement.headOption orElse fromNestedElementsLegacy.headOption
  }

  val dataType: Option[QName] =
    typeMIPOpt map (_.datatype) map
      (Dom4jUtils.extractTextValueQName(namespaceMapping.mapping, _, true))

  val nonPreserveWhitespaceMIPOpt: Option[WhitespaceMIP] = {
    Option(element.attributeValue(XXFORMS_WHITESPACE_QNAME)) map
    (Policy.withNameOption(_) getOrElse Preserve)            filterNot
    (_ == Preserve)                                          map
    (new WhitespaceMIP(getElementId(element), _))
  }

  // Built-in XPath MIPs
  val mipNameToXPathMIP: Iterable[(String, List[XPathMIP])] = {

    def fromAttribute(name: QName) =
      for (value <- Option(element.attributeValue(name)).toList)
      yield (staticId, value, ErrorLevel)

    // For a while we supported `<xf:validation>`
    def fromNestedElementLegacy(name: QName) =
      for {
        e     <- element.elements(XFORMS_VALIDATION_QNAME).toList
        value <- Option(e.attributeValue(name))
      } yield
        (getElementId(e), value, Option(e.attributeValue(LEVEL_QNAME)) map LevelByName getOrElse ErrorLevel)

    def fromNestedElement(name: QName) =
      for {
        e     <- element.elements(name).toList
        value <- Option(e.attributeValue(VALUE_QNAME))
      } yield
        (getElementId(e), value, Option(e.attributeValue(LEVEL_QNAME)) map LevelByName getOrElse ErrorLevel)

    for {
      mip              <- QNameToXPathMIP.values
      idValuesAndLevel = fromNestedElement(mip.eName) ::: fromNestedElementLegacy(mip.aName) ::: fromAttribute(mip.aName)
      if idValuesAndLevel.nonEmpty
      mips             = idValuesAndLevel flatMap {
        case (id, value, level) =>
          // Ignore level for non-constraint MIPs as it's not supported yet
          val overriddenLevel = if (mip.name == Constraint.name) level else ErrorLevel
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
      (name, idNameValue) <- attributeCustomMIP.to(List) groupBy (_._2)
      mips                = idNameValue flatMap {
        case (id, _, value) =>
          XPathMIP.createOrNone(id, name, ErrorLevel, value)
      }
    } yield
      name -> mips
  }

  // All XPath MIPs
  private val allMIPNameToXPathMIP = customMIPNameToXPathMIP ++ mipNameToXPathMIP

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
      id   <- Option(XFormsUtils.getElementId(elem))
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
  def firstXPathMIP(mip: Model.XPathMIP): Option[XPathMIP] = allMIPNameToXPathMIP.getOrElse(mip.name, Nil).headOption
  def hasXPathMIP  (mip: Model.XPathMIP): Boolean          = firstXPathMIP(mip).isDefined

  // Compute value analysis if we have a `type` or `xxf:whitespace`, otherwise don't bother
  override protected def computeValueAnalysis: Option[XPathAnalysis] = typeMIPOpt orElse nonPreserveWhitespaceMIPOpt match {
    case Some(_) if hasBinding => Some(analyzeXPath(getChildrenContext, "string(.)"))
    case _                     => None
  }

  // Return true if analysis succeeded
  def analyzeXPathGather: Boolean = {

    // Analyze context/binding
    analyzeXPath()

    // If successful, gather derived information
    val refSucceeded =
      ref match {
        case Some(_) =>
          getBindingAnalysis match {
            case Some(bindingAnalysis) if bindingAnalysis.figuredOutDependencies =>
              // There is a binding and analysis succeeded

              // Remember dependent instances
              val returnableInstances = bindingAnalysis.returnableInstances
              bindTree.bindInstances ++= returnableInstances

              if (hasCalculateComputedMIPs || hasCustomMIPs)
                bindTree.computedBindExpressionsInstances ++= returnableInstances

              if (hasValidateMIPs)
                bindTree.validationBindInstances ++= returnableInstances

              true

            case _ =>
              // Analysis failed
              false
          }

        case None =>
          // No binding, consider a success
          true
      }

    // Analyze children
    val childrenSucceeded = (_children map (_.analyzeXPathGather)).forall(identity)

    // Result
    refSucceeded && childrenSucceeded
  }

  def analyzeMIPs(): Unit = {
    // Analyze local MIPs if there is a @ref
    ref match {
      case Some(_) =>
        for (mips <- allMIPNameToXPathMIP.values; mip <- mips)
          mip.analyzeXPath()
      case None =>
    }

    // Analyze children
    _children foreach (_.analyzeMIPs())
  }

  override def freeTransientState(): Unit = {

    super.freeTransientState()

    for (mips <- allMIPNameToXPathMIP.values; mip <- mips)
      mip.analysis.freeTransientState()

    for (child <- _children)
      child.freeTransientState()
  }

  override def toXMLAttributes = Seq(
    "id"      -> staticId,
    "context" -> context.orNull,
    "ref"     -> ref.orNull
  )

  override def toXMLContent(helper: XMLReceiverHelper): Unit = {
    super.toXMLContent(helper)

    // @ref analysis is handled by superclass

    // MIP analysis
    for ((_, mips) <- allMIPNameToXPathMIP.to(List).sortBy(_._1); mip <- mips) {
      helper.startElement("mip", Array("name", mip.name, "expression", mip.compiledExpression.string))
      mip.analysis.toXML(helper)
      helper.endElement()
    }

    // Children binds
    for (child <- _children)
      child.toXML(helper)
  }
}
