package org.orbeon.oxf.xforms.analysis.model

import org.dom4j._
import org.orbeon.oxf.xforms._

import analysis._
import collection.JavaConverters._
import org.orbeon.oxf.xforms.XFormsConstants._
import collection.immutable.List
import org.orbeon.oxf.xml.{ShareableXPathStaticContext, Dom4j, ContentHandlerHelper}
import Model._
import org.orbeon.oxf.util.{XPath ⇒ OrbeonXPath}
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.common.ValidationException
import XFormsUtils.getElementId

// Represent a static <xf:bind> element
class StaticBind(
        bindTree: BindTree,
        element: Element,
        parent: ElementAnalysis,
        preceding: Option[ElementAnalysis])
    extends SimpleElementAnalysis(
        bindTree.model.staticStateContext,
        element,
        Some(parent),
        preceding,
        bindTree.model.scope) {

    staticBind ⇒

    import StaticBind._

    // Represent an individual MIP on an <xf:bind> element
    trait MIP {
        val id: String
        val name: String
        val isCalculateComputedMIP = CalculateMIPNames(name)
        val isValidateMIP          = ValidateMIPNames(name)
        val isCustomMIP            = ! isCalculateComputedMIP && ! isValidateMIP
    }

    // Represent an XPath MIP
    class XPathMIP(val id: String, val name: String, expression: String) extends MIP {

        // Compile the expression right away
        val compiledExpression = {
            val booleanOrStringExpression =
                if (BooleanXPathMIPNames(name)) "boolean(" + expression + ")" else "string((" + expression + ")[1])"

            OrbeonXPath.compileExpression(booleanOrStringExpression, staticBind.namespaceMapping, staticBind.locationData, XFormsFunctionLibrary, avt = false)
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

    class ConstraintXPathMIP(id: String, name: String, expression: String, val level: ConstraintLevel)
        extends XPathMIP(id, name, expression)

    // The type MIP is not an XPath expression
    class TypeMIP(val id: String, val name: String, val datatype: String) extends MIP

    // Globally remember binds ids
    bindTree.bindIds += staticId
    bindTree.bindsById += staticId → staticBind

    // Remember variables mappings
    val name = element.attributeValue(NAME_QNAME)
    if (name ne null)
        bindTree.bindsByName += name → staticBind

    // Type MIP is special as it is not an XPath expression
    private val typeMIPAsList =
        for (value ← Option(element.attributeValue(TYPE_QNAME)).toList)
        yield new TypeMIP(staticId, TYPE, value)

    val dataTypeOrNull =
        typeMIPAsList.headOption map (_.datatype) orNull

    // Built-in XPath MIPs
    val mipNameToXPathMIP = {

        def attributeMIP(name: QName) =
            for (value ← Option(element.attributeValue(name)).toList)
            yield (staticId, value, ErrorLevel) // level ignored for non-constraint MIPs

        def elementMIPs(name: QName) =
            for {
                e     ← Dom4j.elements(element, name).toList
                value = e.attributeValue(VALUE_QNAME)
                if value ne null
            } yield
                (getElementId(e), value, Option(e.attributeValue(LEVEL_QNAME)) map LevelByName getOrElse ErrorLevel) // level ignored for non-constraint MIPs

        for {
            mip              ← QNameToXPathMIP.values
            idValuesAndLevel = elementMIPs(mip.eName) ::: attributeMIP(mip.aName)
            if idValuesAndLevel.nonEmpty
            mips             = idValuesAndLevel map {
                case (id, value, level) if mip.name == Constraint.name ⇒ new ConstraintXPathMIP(id, mip.name, value, level)
                case (id, value, _)                                    ⇒ new XPathMIP(id, mip.name, value)
            }
        } yield
            mip.name → mips
    }

    // Custom XPath MIPs
    val customMIPNameToXPathMIP = {

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
            mips = idNameValue map { case (id, _, value) ⇒ new XPathMIP(id, name, value) }
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
                mip.asInstanceOf[ConstraintXPathMIP]

        levelWithMIP groupBy (_.level)
    }

    val hasCalculateComputedMIPs = mipNameToXPathMIP exists { case (_, mips) ⇒ mips exists (_.isCalculateComputedMIP)}
    val hasValidateMIPs          = typeMIPAsList.nonEmpty || (mipNameToXPathMIP exists { case (_, mips) ⇒ mips exists (_.isValidateMIP) })
    val hasCustomMIPs            = customMIPNameToXPathMIP.nonEmpty
    val hasMIPs                  = hasCalculateComputedMIPs || hasValidateMIPs || hasCustomMIPs

    // Create children binds
    private var _children: Seq[StaticBind] = Dom4j.elements(element, XFORMS_BIND_QNAME) map (new StaticBind(bindTree, _, staticBind, None))// NOTE: preceding not handled for now
    def children  = _children
    def jChildren = _children.asJava

    // Globally remember if we have seen these categories of binds
    bindTree.hasDefaultValueBind ||= getMIPs(Default.name).nonEmpty
    bindTree.hasCalculateBind    ||= getMIPs(Calculate.name).nonEmpty
    bindTree.hasRequiredBind     ||= getMIPs(Required.name).nonEmpty

    bindTree.hasTypeBind         ||= dataTypeOrNull ne null
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
    def getMIPs(mipName: String) = if (mipName == TYPE) typeMIPAsList else allMIPNameToXPathMIP.getOrElse(mipName, Nil)

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
    override protected def computeValueAnalysis: Option[XPathAnalysis] = typeMIPAsList match {
        case List(_) if hasBinding ⇒ Some(analyzeXPath(getChildrenContext, "string(.)"))
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

    override def toXMLContent(helper: ContentHandlerHelper): Unit = {
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

object StaticBind {
    sealed trait ConstraintLevel { val name: String }
    case object ErrorLevel   extends { val name = "error" }   with ConstraintLevel
    case object WarningLevel extends { val name = "warning" } with ConstraintLevel
    case object InfoLevel    extends { val name = "info" }    with ConstraintLevel

    val LevelsByPriority = Seq(ErrorLevel, WarningLevel, InfoLevel): Seq[ConstraintLevel]
    val LevelByName      = LevelsByPriority map (l ⇒ l.name → l) toMap
    val LevelSet         = LevelsByPriority.to[Set]

    def jErrorLevel: ConstraintLevel = ErrorLevel
}