package org.orbeon.oxf.xforms.analysis.model

import org.dom4j._
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.{XPath ⇒ OrbeonXPath}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.XFormsUtils.getElementId
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.model.Model._
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{Dom4j, ShareableXPathStaticContext, XMLReceiverHelper}

import scala.collection.immutable.List

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

    staticBind ⇒

    import ValidationLevels._

    def parentBind = parent match {
        case parentBind: StaticBind ⇒ Some(parentBind)
        case _                      ⇒ None
    }

    val ancestorBinds: List[StaticBind] = parentBind match {
        case Some(parent) ⇒ parent :: parent.ancestorBinds
        case None         ⇒ Nil
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
        val compiledExpression = {
            val booleanOrStringExpression =
                if (BooleanXPathMIPNames(name))
                    OrbeonXPath.makeBooleanExpression(expression)
                else
                    OrbeonXPath.makeStringExpression(expression)

            OrbeonXPath.compileExpression(
                xpathString      = booleanOrStringExpression,
                namespaceMapping = staticBind.namespaceMapping,
                locationData     = staticBind.locationData,
                functionLibrary  = XFormsFunctionLibrary,
                avt              = false
            )
        }

        // Default to negative, analyzeXPath() can change that
        var analysis: XPathAnalysis = NegativeAnalysis(expression)

        def analyzeXPath() {

            val allBindVariablesInScope = bindTree.allBindVariables

            // Saxon: "In the case of free-standing XPath expressions it will be the StaticContext object"
            val staticContext  = compiledExpression.expression.getInternalExpression.getContainer.asInstanceOf[ShareableXPathStaticContext]
            if (staticContext ne null) {
                // NOTE: The StaticContext can be null if the expression is a constant such as BooleanValue
                val usedVariables = staticContext.referencedVariables

                // Check whether all variables used by the expression are actually in scope, throw otherwise
                usedVariables find (name ⇒ ! allBindVariablesInScope.contains(name.getLocalName)) foreach { name ⇒
                    throw new ValidationException("Undeclared variable in XPath expression: $" + name.getClarkName, staticBind.locationData)
                }
            }

            // Analyze and remember if figured out
            staticBind.analyzeXPath(getChildrenContext, allBindVariablesInScope, compiledExpression) match {
                case valueAnalysis if valueAnalysis.figuredOutDependencies ⇒ this.analysis = valueAnalysis
                case _ ⇒ // NOP
            }
        }
    }

    // The type MIP is not an XPath expression
    class TypeMIP(val id: String, val datatype: String) extends {
        val name  = Type.name
        val level = ErrorLevel
    } with MIP

    // Globally remember binds ids
    bindTree.bindIds += staticId
    bindTree.bindsById += staticId → staticBind

    // Remember variables mappings
    val name = element.attributeValue(NAME_QNAME)
    if (name ne null)
        bindTree.bindsByName += name → staticBind

    // Type MIP is special as it is not an XPath expression
    val typeMIPOpt: Option[TypeMIP] = {

        def fromAttribute =
            for (value ← Option(element.attributeValue(TYPE_QNAME)))
            yield new TypeMIP(staticId, value)

        // Only support collecting one type MIP per bind (but at runtime more than one type MIP can touch a given node)
        def fromNestedElements =
            for {
                e           ← Dom4j.elements(element, XFORMS_VALIDATION_QNAME) // <xf:validation>
                value       ← Option(e.attributeValue(TYPE_QNAME))
            } yield
                new TypeMIP(getElementId(e), value)
        
        fromAttribute orElse fromNestedElements.headOption
    }

    val dataTypeOrNull =
        typeMIPOpt map (_.datatype) orNull

    val dataType: Option[QName] =
        typeMIPOpt map (_.datatype) map
            (Dom4jUtils.extractTextValueQName(namespaceMapping.mapping, _, true))

    // Built-in XPath MIPs
    val mipNameToXPathMIP = {

        def fromAttribute(name: QName) =
            for (value ← Option(element.attributeValue(name)).toList)
            yield (staticId, value, ErrorLevel)

        def fromLegacyNestedElement(name: QName) =
            for {
                e     ← Dom4j.elements(element, name).toList
                value ← Option(e.attributeValue(VALUE_QNAME))
                if value ne null
            } yield
                (getElementId(e), value, Option(e.attributeValue(LEVEL_QNAME)) map LevelByName getOrElse ErrorLevel)

        def fromNestedElements(name: QName) =
            for {
                e     ← Dom4j.elements(element, XFORMS_VALIDATION_QNAME).toList
                value ← Option(e.attributeValue(name))
            } yield
                (getElementId(e), value, Option(e.attributeValue(LEVEL_QNAME)) map LevelByName getOrElse ErrorLevel)

        for {
            mip              ← QNameToXPathMIP.values
            idValuesAndLevel = fromLegacyNestedElement(mip.eName) ::: fromNestedElements(mip.aName) ::: fromAttribute(mip.aName)
            if idValuesAndLevel.nonEmpty
            mips             = idValuesAndLevel map {
                case (id, value, level) ⇒
                    // Ignore level for non-constraint MIPs as it's not supported yet
                    val overriddenLevel = if (mip.name == Constraint.name) level else ErrorLevel
                    new XPathMIP(id, mip.name, overriddenLevel, value)
            }
        } yield
            mip.name → mips
    }

    // Custom XPath MIPs
    val customMIPNameToXPathMIP = { // Q: Why String → List[XPathMIP] and not String → XPathMIP?

        def attributeCustomMIP =
            for {
                att           ← Dom4j.attributes(element).toList
                if bindTree.isCustomMIP(att.getQName)
                value         = att.getValue
                customMIPName = buildCustomMIPName(att.getQualifiedName)
            } yield
                (staticId, customMIPName, value)

        def elementCustomMIPs =
            for {
                elem          ← Dom4j.elements(element).toList
                if bindTree.isCustomMIP(elem.getQName)
                value         = elem.attributeValue(VALUE_QNAME)
                if value ne null
                customMIPName = buildCustomMIPName(elem.getQualifiedName)
            } yield
                (getElementId(elem), customMIPName, value)

        for {
            (name, idNameValue) ← elementCustomMIPs ::: attributeCustomMIP groupBy (_._2)
            mips = idNameValue map { case (id, _, value) ⇒ new XPathMIP(id, name, ErrorLevel, value) }
        } yield
            name → mips
    }

    // All XPath MIPs
    private val allMIPNameToXPathMIP = customMIPNameToXPathMIP ++ mipNameToXPathMIP

    // All constraint XPath MIPs grouped by level
    val constraintsByLevel = {
        val levelWithMIP =
            for {
                mips ← allMIPNameToXPathMIP.get(Constraint.name).toList
                mip  ← mips
            } yield
                mip

        levelWithMIP groupBy (_.level)
    }

    val hasCalculateComputedMIPs = mipNameToXPathMIP exists { case (_, mips) ⇒ mips exists (_.isCalculateComputedMIP)}
    val hasValidateMIPs          = typeMIPOpt.nonEmpty || (mipNameToXPathMIP exists { case (_, mips) ⇒ mips exists (_.isValidateMIP) })
    val hasCustomMIPs            = customMIPNameToXPathMIP.nonEmpty
    val hasMIPs                  = hasCalculateComputedMIPs || hasValidateMIPs || hasCustomMIPs

    def iterateNestedIds =
        for {
            elem ← Dom4j.elements(element).iterator
            id   ← Option(XFormsUtils.getElementId(elem))
        } yield
            id

    // Create children binds
    private var _children: Seq[StaticBind] = Dom4j.elements(element, XFORMS_BIND_QNAME) map (new StaticBind(bindTree, _, staticBind, None))// NOTE: preceding not handled for now
    def children  = _children

    // Globally remember if we have seen these categories of binds
    bindTree.hasDefaultValueBind ||= getXPathMIPs(Default.name).nonEmpty
    bindTree.hasCalculateBind    ||= getXPathMIPs(Calculate.name).nonEmpty
    bindTree.hasRequiredBind     ||= getXPathMIPs(Required.name).nonEmpty

    bindTree.hasTypeBind         ||= typeMIPOpt.nonEmpty
    bindTree.hasConstraintBind   ||= constraintsByLevel.nonEmpty

    bindTree.hasCalculateComputedCustomBind ||= hasCalculateComputedMIPs || hasCustomMIPs
    bindTree.hasValidateBind ||= hasValidateMIPs

    def addBind(bindElement: Element, precedingId: Option[String]): Unit =
        _children = _children :+ new StaticBind(bindTree, bindElement, staticBind, None)// NOTE: preceding not handled for now

    def removeBind(bind: StaticBind): Unit = {
        bindTree.bindIds -= bind.staticId
        bindTree.bindsById -= bind.staticId
        if (bind.name ne null)
            bindTree.bindsByName -= bind.name

        _children = _children filterNot (_ eq bind)

        part.unmapScopeIds(bind)
    }

    // Used by PathMapXPathDependencies
    def getXPathMIPs(mipName: String) = allMIPNameToXPathMIP.getOrElse(mipName, Nil)

    // TODO: Support multiple relevant, readonly, and required MIPs.
    private def firstXPathMIPOrNull(mipName: String) = allMIPNameToXPathMIP.getOrElse(mipName, Nil).headOption.orNull

    // For Java callers (can return null)
    def getDefaultValue = firstXPathMIPOrNull(Default.name)
    def getCalculate    = firstXPathMIPOrNull(Calculate.name)

    // These can have multiple values
    def getRelevant     = firstXPathMIPOrNull(Relevant.name)
    def getReadonly     = firstXPathMIPOrNull(Readonly.name)
    def getRequired     = firstXPathMIPOrNull(Required.name)

    def getCustom(mipName: String) = firstXPathMIPOrNull(mipName)

    // Compute value analysis if we have a type bound, otherwise don't bother
    override protected def computeValueAnalysis: Option[XPathAnalysis] = typeMIPOpt match {
        case Some(_) if hasBinding ⇒ Some(analyzeXPath(getChildrenContext, "string(.)"))
        case _                     ⇒ None
    }

    // Return true if analysis succeeded
    def analyzeXPathGather: Boolean = {

        // Analyze context/binding
        analyzeXPath()

        // If successful, gather derived information
        val refSucceeded =
            ref match {
                case Some(_) ⇒
                    getBindingAnalysis match {
                        case Some(bindingAnalysis) if bindingAnalysis.figuredOutDependencies ⇒
                            // There is a binding and analysis succeeded

                            // Remember dependent instances
                            val returnableInstances = bindingAnalysis.returnableInstances
                            bindTree.bindInstances ++= returnableInstances

                            if (hasCalculateComputedMIPs || hasCustomMIPs)
                                bindTree.computedBindExpressionsInstances ++= returnableInstances

                            if (hasValidateMIPs)
                                bindTree.validationBindInstances ++= returnableInstances

                            true

                        case _ ⇒
                            // Analysis failed
                            false
                    }

                case None ⇒
                    // No binding, consider a success
                    true
            }

        // Analyze children
        val childrenSucceeded = (_children map (_.analyzeXPathGather)).foldLeft(true)(_ && _)

        // Result
        refSucceeded && childrenSucceeded
    }

    def analyzeMIPs() {
        // Analyze local MIPs if there is a @ref
        ref match {
            case Some(_) ⇒
                for (mips ← allMIPNameToXPathMIP.values; mip ← mips)
                    mip.analyzeXPath()
            case None ⇒
        }

        // Analyze children
        _children foreach (_.analyzeMIPs())
    }

    override def freeTransientState() {

        super.freeTransientState()

        for (mips ← allMIPNameToXPathMIP.values; mip ← mips)
            mip.analysis.freeTransientState()

        for (child ← _children)
            child.freeTransientState()
    }

    override def toXMLAttributes = Seq(
        "id"      → staticId,
        "context" → context.orNull,
        "ref"     → ref.orNull
    )

    override def toXMLContent(helper: XMLReceiverHelper): Unit = {
        super.toXMLContent(helper)

        // @ref analysis is handled by superclass

        // MIP analysis
        for ((_, mips) ← allMIPNameToXPathMIP.to[List].sortBy(_._1); mip ← mips) {
            helper.startElement("mip", Array("name", mip.name, "expression", mip.compiledExpression.string))
            mip.analysis.toXML(helper)
            helper.endElement()
        }

        // Children binds
        for (child ← _children)
            child.toXML(helper)
    }
}

object ValidationLevels {
    sealed trait ValidationLevel { val name: String }
    case object ErrorLevel   extends { val name = "error" }   with ValidationLevel
    case object WarningLevel extends { val name = "warning" } with ValidationLevel
    case object InfoLevel    extends { val name = "info" }    with ValidationLevel

    implicit object LevelOrdering extends Ordering[ValidationLevel] {
        override def compare(x: ValidationLevel, y: ValidationLevel) =
            if (x == y) 0
            else if (y == ErrorLevel || y == WarningLevel && x == InfoLevel) -1
            else 1
    }

    val LevelsByPriority = Seq(ErrorLevel, WarningLevel, InfoLevel): Seq[ValidationLevel]
    val LevelByName      = LevelsByPriority map (l ⇒ l.name → l) toMap
    val LevelSet         = LevelsByPriority.to[Set]

    def jErrorLevel: ValidationLevel = ErrorLevel
}
