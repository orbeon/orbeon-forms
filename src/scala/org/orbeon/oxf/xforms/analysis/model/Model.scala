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

import org.dom4j._
import java.util.{Map => JMap, LinkedHashMap => JLinkedHashMap, Set => JSet, List => JList }
import org.orbeon.oxf.xforms._


import analysis._
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.collection.JavaConversions._
import org.orbeon.oxf.xforms.XFormsConstants._
import java.lang.String
import collection.immutable.List
import collection.mutable.{LinkedHashSet, LinkedHashMap}

/**
 * Static analysis of an XForms model <xf:model> element.
 */
class Model(val staticStateContext: StaticStateContext, scope: XBLBindings#Scope, element: Element)
        extends ElementAnalysis(element, None, None) with ContainerTrait { // consider that the model doesn't have a parent

    require(staticStateContext != null)
    require(scope != null)

    // ElementAnalysis
    // NOTE: It is possible to imagine a model having a context and binding, but this is not supported now
    protected def computeContextAnalysis = None
    protected def computeValueAnalysis = None
    protected def computeBindingAnalysis = None
    val scopeModel = new ScopeModel(scope, Some(this))

    override def getChildrenContext = defaultInstancePrefixedId match {
        case Some(defaultInstancePrefixedId) => // instance('defaultInstanceId')
            Some(PathMapXPathAnalysis(staticStateContext.staticState, PathMapXPathAnalysis.buildInstanceString(defaultInstancePrefixedId),
                null, None, Map.empty[String, VariableTrait], null, scope, Some(defaultInstancePrefixedId), locationData, element))
        case None => None // no instance
    }

    // NOTE: It is possible to imagine a model having in-scope variables, but this is not supported now
    val inScopeVariables = Map.empty[String, VariableTrait]

    // Instance objects
    val instances = LinkedHashMap((
        for {
            instanceElement <- Dom4jUtils.elements(element, XFORMS_INSTANCE_QNAME)
            newInstance = new Instance(instanceElement, scope)
        } yield (newInstance.staticId -> newInstance)): _*)

    val instancesMap: JMap[String, Instance] = instances // JAVA COLLECTION for Java access only

    // General info about instances
    val hasInstances = instances.nonEmpty
    val defaultInstanceStaticId = if (hasInstances) instances.head._1 else null
    val defaultInstancePrefixedId = Option(if (hasInstances) scope.getFullPrefix + defaultInstanceStaticId else null)

    // Get *:variable/*:var elements
    val variableElements: JList[Element] = Dom4jUtils.elements(element) filter (VariableAnalysis.isVariableElement(_))

    // Handle variables
    val variablesSeq: Seq[VariableAnalysisTrait] = {

        // NOTE: For now, all top-level variables in a model are visible first, then only are binds variables visible.
        // In the future, we might want to change that to use document order between variables and binds, but some
        // more thinking is needed wrt the processing model.

        // Iterate and resolve all variables in order
        var preceding: Option[SimpleElementAnalysis with VariableAnalysisTrait] = None

        for {
            variableElement <- variableElements
            analysis = {
                val result = new SimpleElementAnalysis(staticStateContext, variableElement, Some(Model.this), preceding, scope) with VariableAnalysisTrait
                preceding = Some(result)
                result
            }
        } yield {
            analysis
        }
    }

    val variablesMap: Map[String, VariableTrait] = Map(variablesSeq map (variable => (variable.name -> variable)): _*)
    val jVariablesMap: JMap[String, VariableTrait] = variablesMap

    // Handle binds
    val bindIds = new LinkedHashSet[String]
    val bindInstances = new LinkedHashSet[String]                       // instances to which binds apply (i.e. bind/@ref point to them)
    val computedBindExpressionsInstances = new LinkedHashSet[String]    // instances to which computed binds apply
    val validationBindInstances = new LinkedHashSet[String]             // instances to which validation binds apply

    // TODO: use and produce variables introduced with xf:bind/@name

    // All binds by static id
    val bindsById = new JLinkedHashMap[String, Bind]// JAVA COLLECTION

    // Binds by name (for binds with a name)
    val bindsByName = new LinkedHashMap[String, Bind]
    val jBindsByName: JMap[String, Bind] = bindsByName

    var hasInitialValueBind = false
    var hasCalculateBind = false
    var hasTypeBind = false
    var hasRequiredBind = false
    var hasConstraintBind = false

    var hasCalculateComputedCustomBind = false // default
    var hasValidateBind = false // default

    // Top-level binds and create static binds hierarchy
    val topLevelBinds: Seq[Bind] = {
        // NOTE: For now, do as if binds follow all top-level variables
        val preceding = if (variablesSeq.isEmpty) None else Some(variablesSeq.last)
        for (bindElement <- Dom4jUtils.elements(element, XFORMS_BIND_QNAME))
            yield new Bind(bindElement, this, preceding)
    }

    val topLevelBindsJava: JList[Bind] = topLevelBinds

    // In-scope variable on binds include variables implicitly declared with bind/@name
    lazy val bindsVariablesSeq = variablesMap ++ (bindsByName map (e => (e._1 -> new BindAsVariable(e._2))))

    class BindAsVariable(bind: Bind) extends VariableTrait {
        def name = bind.name
        def variableAnalysis = bind.getBindingAnalysis
    }

    var figuredAllBindRefAnalysis = !hasBinds // default value sets to true if no binds
    
    def hasBinds = topLevelBinds.nonEmpty
    def containsBind(bindId: String) = bindIds.contains(bindId)

    def getDefaultInstance = if (instances.nonEmpty) instances.head._2 else null

    // Represent a static <xf:bind> element
    class Bind(element: Element, parent: ContainerTrait, preceding: Option[ElementAnalysis])
            extends SimpleElementAnalysis(staticStateContext, element, Some(parent), preceding, scope)
            with ContainerTrait {

        // Represent an individual MIP on an <xf:bind> element
        class MIP(val name: String) {
            val isCalculateComputedMIP = Model.CALCULATE_MIP_NAMES.contains(name)
            val isValidateMIP = Model.VALIDATE_MIP_NAMES.contains(name)
            val isCustomMIP = !isCalculateComputedMIP && !isValidateMIP
        }

        // Represent an XPath MIP
        class XPathMIP(name: String, val expression: String) extends MIP(name) {

            // Default to negative, analyzeXPath() can change that
            var analysis: XPathAnalysis = NegativeAnalysis(expression)

            def analyzeXPath() {

                def booleanOrStringExpression =
                    if (Model.BOOLEAN_XPATH_MIP_NAMES.contains(name)) "xs:boolean((" + expression + ")[1])" else "xs:string((" + expression + ")[1])"

                // Analyze and remember if figured out
                Bind.this.analyzeXPath(getChildrenContext, bindsVariablesSeq, booleanOrStringExpression) match {
                    case valueAnalysis if valueAnalysis.figuredOutDependencies => this.analysis = valueAnalysis
                    case _ => // NOP
                }
            }
        }

        // The type MIP is not an XPath expression
        class TypeMIP(name: String, val datatype: String) extends MIP(name)

        // Globally remember binds ids
        Model.this.bindIds.add(staticId)
        Model.this.bindsById.put(staticId, Bind.this)

        // Remember variables mappings
        val name = element.attributeValue(XFormsConstants.NAME_QNAME)
        if (name != null)
            bindsByName.put(name, Bind.this)

        // Built-in XPath MIPs
        val mipNameToXPathMIP =
            for ((qName, name) <- Model.QNAME_TO_XPATH_MIP_NAME; attributeValue = element.attributeValue(qName); if attributeValue ne null)
                yield (name -> new XPathMIP(name, attributeValue))

        // Type MIP is special as it is not an XPath expression
        val typeMIP = element.attributeValue(TYPE_QNAME) match {
            case value if value ne null => Some(new TypeMIP(Model.TYPE, value))
            case _ => None
        }

        // Custom MIPs
        val customMIPNameToXPathMIP = Predef.Map((
            for {
                attribute <- Dom4jUtils.attributes(element) // check all the element's attributes

                attributeQName = attribute.getQName
                attributePrefix = attributeQName.getNamespacePrefix
                attributeURI = attributeQName.getNamespaceURI

                // Any QName-but-not-NCName which is not in the xforms or xxforms namespace or an XML attribute
                // NOTE: Also allow for xxf:events-mode extension MIP
                if attributePrefix.nonEmpty && !attributePrefix.startsWith("xml") &&
                    attributeURI != XFORMS_NAMESPACE_URI &&
                    (attributeURI != XXFORMS_NAMESPACE_URI || attributeQName == XXFORMS_EVENT_MODE_QNAME)

                customMIPName = Model.buildCustomMIPName(attribute.getQualifiedName)
            } yield (customMIPName -> new XPathMIP(customMIPName, attribute.getValue))): _*)

        val customMIPs: JMap[String, Bind#XPathMIP] = customMIPNameToXPathMIP

        // All XPath MIPs
        val allMIPNameToXPathMIP = mipNameToXPathMIP ++ customMIPNameToXPathMIP

        // Create children binds
        val children: Seq[Bind] = Dom4jUtils.elements(element, XFORMS_BIND_QNAME) map (new Bind(_, Bind.this, preceding))
        val childrenJava: JList[Bind] = children

        def getMIP(mipName: String) = if (mipName == Model.TYPE) typeMIP else allMIPNameToXPathMIP.get(mipName)

        // For Java callers (can return null)
        def getInitialValue = getMIPExpressionOrNull(Model.INITIAL_VALUE)
        def getCalculate = getMIPExpressionOrNull(Model.CALCULATE)
        def getRelevant = getMIPExpressionOrNull(Model.RELEVANT)
        def getReadonly = getMIPExpressionOrNull(Model.READONLY)
        def getRequired = getMIPExpressionOrNull(Model.REQUIRED)
        def getConstraint = getMIPExpressionOrNull(Model.CONSTRAINT)
        def getType = typeMIP match {
            case Some(mip) => mip.datatype
            case None => null
        }
        def getCustom(mipName: String) = getMIPExpressionOrNull(mipName)

        def getMIPExpressionOrNull(mipName: String) = allMIPNameToXPathMIP.get(mipName) match {
            case Some(mip) => mip.expression
            case None => null
        }

        def hasCalculateComputedMIPs = mipNameToXPathMIP exists (_._2 isCalculateComputedMIP)
        def hasValidateMIPs = typeMIP.isDefined || (mipNameToXPathMIP exists (_._2 isValidateMIP))
        def hasCustomMIPs = customMIPNameToXPathMIP.nonEmpty
        def hasMIPs = hasCalculateComputedMIPs || hasValidateMIPs || hasCustomMIPs

        // Globally remember if we have seen these categories of binds
        Model.this.hasInitialValueBind ||= getInitialValue ne null
        Model.this.hasCalculateBind ||= getCalculate ne null
        Model.this.hasTypeBind ||= getType ne null
        Model.this.hasRequiredBind ||= getRequired ne null
        Model.this.hasConstraintBind ||= getConstraint ne null

        Model.this.hasCalculateComputedCustomBind ||= hasCalculateComputedMIPs || hasCustomMIPs
        Model.this.hasValidateBind ||= hasValidateMIPs

        // Compute value analysis if we have a type bound, otherwise don't bother
        override protected def computeValueAnalysis: Option[XPathAnalysis] = typeMIP match {
            case Some(_) if hasNodeBinding => Some(analyzeXPath(getChildrenContext, "xs:string(.[1])"))
            case _ => None
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
                                bindInstances.addAll(returnableInstances)

                                if (hasCalculateComputedMIPs || hasCustomMIPs)
                                    computedBindExpressionsInstances.addAll(returnableInstances)

                                if (hasValidateMIPs)
                                    validationBindInstances.addAll(returnableInstances)

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
            val childrenSucceeded = (children map (_.analyzeXPathGather)).foldLeft(true)(_ && _)

            // Result
            refSucceeded && childrenSucceeded
        }

        def analyzeMIPs() {
            // Analyze local MIPs if there is a @ref
            ref match {
                case Some(_) =>
                    for (mip <- allMIPNameToXPathMIP.values)
                        mip.analyzeXPath()
                case None =>
            }

            // Analyze children
            children foreach (_.analyzeMIPs())
        }

        override def freeTransientState() {

            super.freeTransientState()

            for (mip <- allMIPNameToXPathMIP.values)
                mip.analysis.freeTransientState()

            for (child <- children)
                child.freeTransientState()
        }

        override def toXML(helper: ContentHandlerHelper, attributes: List[String] = Nil)(content: => Unit = ()) {
            super.toXML(helper, List("id", staticId, "context", context.orNull, "ref", ref.orNull)) {
                // @ref analysis is handled by superclass

                // MIP analysis
                for (mip <- allMIPNameToXPathMIP.values) {
                    helper.startElement("mip", Array("name", mip.name, "expression", mip.expression))
                    mip.analysis.toXML(helper)
                    helper.endElement()
                }

                // Children
                for (child <- children)
                    child.toXML(helper)()
            }
        }
    }

    // TODO: instances on which MIPs depend

    override def analyzeXPath() {
        // Analyze this
        super.analyzeXPath()

        // Variables
        for (variable <- variablesSeq)
            variable.analyzeXPath()
        
        // Analyze all binds and return whether all of them were successfully analyzed
        figuredAllBindRefAnalysis = (topLevelBinds map (_.analyzeXPathGather)).foldLeft(true)(_ && _)

        // Analyze all MIPs
        // NOTE: Do this here, because MIPs can depend on bind/@name, which requires all bind/@ref to be analyzed first
        topLevelBinds foreach (_.analyzeMIPs())

        if (!figuredAllBindRefAnalysis) {
            bindInstances.clear()
            computedBindExpressionsInstances.clear()
            validationBindInstances.clear()
            // keep bindAnalysis as those can be used independently from each other
        }
    }

    override def toXML(helper: ContentHandlerHelper, attributes: List[String] = Nil)(content: => Unit = ()) {

        super.toXML(helper, List(
                "scope", scope.scopeId,
                "prefixed-id", prefixedId,
                "default-instance-prefixed-id", defaultInstancePrefixedId.orNull,
                "analyzed-binds", figuredAllBindRefAnalysis.toString
        )) {
            // Output variable information
            for (variable <- variablesSeq)
                variable.toXML(helper, List())({})

            // Output binds information
            if (topLevelBinds.nonEmpty) {
                helper.startElement("binds")
                for (bind <- topLevelBinds)
                    bind.toXML(helper)()
                helper.endElement()
            }

            // Output instances information
            def outputInstanceList(name: String, values: JSet[String]) {
                if (values.nonEmpty) {
                    helper.startElement(name)
                    for (value <- values)
                        helper.element("instance", value)
                    helper.endElement()
                }
            }

            outputInstanceList("bind-instances", bindInstances)
            outputInstanceList("computed-binds-instances", computedBindExpressionsInstances)
            outputInstanceList("validation-binds-instances", validationBindInstances)
        }
    }

    override def freeTransientState() {

        super.freeTransientState()

        for (variable <- variablesSeq)
            variable.freeTransientState()

        for (bind <- topLevelBinds)
            bind.freeTransientState();
    }
}

object Model {

    // MIP enumeration
    object MIP extends Enumeration {
        val Relevant = Value("relevant")
        val Readonly = Value("readonly")
        val Required = Value("required")
        val Constraint = Value("constraint")
        val Calculate = Value("calculate")
        val InitialValue = Value("initial-value")
        val Type = Value("type")
    }

    // Constants for Java callers
    val RELEVANT = MIP.Relevant.toString
    val READONLY = MIP.Readonly.toString
    val REQUIRED = MIP.Required.toString
    val CONSTRAINT = MIP.Constraint.toString
    val CALCULATE = MIP.Calculate.toString
    val INITIAL_VALUE = MIP.InitialValue.toString
    val TYPE = MIP.Type.toString

    // MIP default values
    val DEFAULT_RELEVANT = true
    val DEFAULT_READONLY = false
    val DEFAULT_REQUIRED = false
    val DEFAULT_VALID = true

    // NOTE: "required" is special: it is evaluated during recalculate, but used during revalidate. In effect both
    // recalculate AND revalidate depend on it. Ideally maybe revalidate would depend on the the *value* of the
    // "required" MIP, not on the XPath of it. See also what we would need for xxf:valid(), etc. functions.

    val QNAME_TO_XPATH_COMPUTED_MIP_NAME = Predef.Map(
        RELEVANT_QNAME -> RELEVANT,
        READONLY_QNAME -> READONLY,
        REQUIRED_QNAME -> REQUIRED,
        CALCULATE_QNAME -> CALCULATE,
        XXFORMS_DEFAULT_QNAME -> INITIAL_VALUE)

    val QNAME_TO_XPATH_VALIDATE_MIP_NAME = Predef.Map(
        REQUIRED_QNAME -> REQUIRED,
        CONSTRAINT_QNAME -> CONSTRAINT)

    private val QNAME_TO_VALIDATE_MIP_NAME = QNAME_TO_XPATH_VALIDATE_MIP_NAME + (TYPE_QNAME -> TYPE)
    val QNAME_TO_XPATH_MIP_NAME = QNAME_TO_XPATH_COMPUTED_MIP_NAME ++ QNAME_TO_XPATH_VALIDATE_MIP_NAME

    val CALCULATE_MIP_NAMES = Set(QNAME_TO_XPATH_COMPUTED_MIP_NAME.values.toSeq: _*)
    val VALIDATE_MIP_NAMES = Set(QNAME_TO_VALIDATE_MIP_NAME.values.toSeq: _*)

    val BOOLEAN_XPATH_MIP_NAMES = Set(RELEVANT, READONLY, REQUIRED, CONSTRAINT)
    val STRING_XPATH_MIP_NAMES = Set(CALCULATE, INITIAL_VALUE)

    def buildCustomMIPName(qualifiedName: String) = qualifiedName.replace(':', '-')
}
