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
import org.orbeon.oxf.util.PropertyContext
import java.util.{Map => JMap, HashMap => JHashMap, Collections => JCollections, LinkedHashMap => JLinkedHashMap, LinkedHashSet => JLinkedHashSet, Set => JSet}
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
                null, None, Map.empty[String, VariableAnalysisTrait], null, scope, Some(defaultInstancePrefixedId), locationData, element))
        case None => None // no instance
    }

    // NOTE: It is possible to imagine a model having in-scope variables, but this is not supported now
    val inScopeVariables = Map.empty[String, VariableAnalysisTrait]

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

    // Handle variables
    val variablesSeq: Seq[VariableAnalysisTrait] = {

        // Get xxf:variable, exf:variable and xf:variable (in fact *:variable) elements
        val variableElements = Dom4jUtils.elements(element) filter (_.getName == XXFORMS_VARIABLE_NAME)

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

    val variablesMap: Map[String, VariableAnalysisTrait] = Map(variablesSeq map (variable => (variable.name -> variable)): _*)
    val jVariablesMap: JMap[String, VariableAnalysisTrait] = variablesMap

    // Handle binds
    val bindElements = Dom4jUtils.elements(element, XFORMS_BIND_QNAME) // JAVA COLLECTION
    val bindIds = new LinkedHashSet[String]
    val customMIPs = new JLinkedHashMap[String, JMap[String, Bind#MIP]] // bind static id => MIP mapping JAVA COLLECTION
    val bindInstances = new LinkedHashSet[String]                       // instances to which binds apply (i.e. bind/@ref point to them)
    val computedBindExpressionsInstances = new LinkedHashSet[String]    // instances to which computed binds apply
    val validationBindInstances = new LinkedHashSet[String]             // instances to which validation binds apply

    // TODO: use and produce variables introduced with xf:bind/@name

    var figuredAllBindRefAnalysis = !hasBinds // default value sets to true if no binds

    // All binds by static id
    val bindsById = new JLinkedHashMap[String, Bind]// JAVA COLLECTION

    var hasCalculateComputedCustomBind = false // default
    var hasValidateBind = false // default

    // Bind name -> id mapping
    val bindNamesToIds: JMap[String, String] = new JHashMap[String, String]

    // Top-level binds and create static binds hierarchy
    val topLevelBinds: Seq[Bind] = {
        // NOTE: For now, do as if binds follow all top-level variables
        val preceding = if (variablesSeq.isEmpty) None else Some(variablesSeq.last)
        for (bindElement <- Dom4jUtils.elements(element, XFORMS_BIND_QNAME))
            yield new Bind(bindElement, this, preceding)
    }

    // Represent a static <xf:bind> element
    class Bind(element: Element, parent: ContainerTrait, preceding: Option[ElementAnalysis])
            extends SimpleElementAnalysis(staticStateContext, element, Some(parent), preceding, scope)
            with ContainerTrait {

        // Represent an individual MIP on an <xf:bind> element
        class MIP(val name: String, val expression: String) {

            val isCalculateComputedMIP = Model.CALCULATE_MIP_NAMES.contains(name)
            val isValidateMIP = Model.VALIDATE_MIP_NAMES.contains(name)
            val isCustomMIP = !isCalculateComputedMIP && !isValidateMIP

            var analysis: XPathAnalysis = NegativeAnalysis(expression) // default to negative, analyzeXPath() can change that

            def analyzeXPath() {

                def booleanOrStringExpression =
                    if (Model.BOOLEAN_XPATH_MIP_NAMES.contains(name)) "xs:boolean((" + expression + ")[1])" else "xs:string((" + expression + ")[1])"

                // Analyze and remember if figured out
                Bind.this.analyzeXPath(getChildrenContext, booleanOrStringExpression) match {
                    case valueAnalysis if valueAnalysis.figuredOutDependencies => this.analysis = valueAnalysis
                    case _ => // NOP
                }
            }
        }

        // Globally remember binds ids
        Model.this.bindIds.add(staticId)
        Model.this.bindsById.put(staticId, Bind.this)

        // Remember variables mappings
        val name = element.attributeValue(XFormsConstants.NAME_QNAME)
        if (name != null)
            bindNamesToIds.put(name, staticId)

        // Built-in XPath MIPs
        val mipNameToXPathMIP =
            for ((qName, name) <- Model.QNAME_TO_XPATH_MIP_NAME; attributeValue = element.attributeValue(qName); if attributeValue ne null)
                yield (name -> new MIP(name, attributeValue))

        // Type MIP is special as it is not an XPath expression
        val typeMIP = Option(element.attributeValue(TYPE_QNAME))

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
            } yield (customMIPName -> new MIP(customMIPName, attribute.getValue))): _*)

        // Globally remember custom MIPs
        if (customMIPNameToXPathMIP.nonEmpty)
            Model.this.customMIPs.put(staticId, customMIPNameToXPathMIP)

        // All XPath MIPs
        val allMIPNameToXPathMIP = mipNameToXPathMIP ++ customMIPNameToXPathMIP

        // Create children binds
        val children: Seq[Bind] = Dom4jUtils.elements(element, XFORMS_BIND_QNAME) map (new Bind(_, Bind.this, preceding))

        def getMIP(mipName: String) = allMIPNameToXPathMIP.get(mipName)

        // For Java callers (can return null)
        def getRelevant = getMIPExpression(Model.RELEVANT)
        def getReadonly = getMIPExpression(Model.READONLY)
        def getRequired = getMIPExpression(Model.REQUIRED)
        def getConstraint = getMIPExpression(Model.CONSTRAINT)
        def getCalculate = getMIPExpression(Model.CALCULATE)
        def getInitialValue = getMIPExpression(Model.INITIAL_VALUE)
        def getType = typeMIP.orNull
        def getCustom(mipName: String) = getMIPExpression(mipName)  

        def getMIPExpression(mipName: String) = getMIP(mipName) match {
            case Some(mip) => mip.expression
            case None => null
        }

        def hasCalculateComputedBind = mipNameToXPathMIP exists (_._2 isCalculateComputedMIP)
        def hasValidateBind = typeMIP.isDefined || (mipNameToXPathMIP exists (_._2 isValidateMIP))
        def hasCustomMip = customMIPNameToXPathMIP.nonEmpty

        // Globally remember if we have seen these categories of binds
        Model.this.hasCalculateComputedCustomBind ||= hasCalculateComputedBind || hasCustomMip
        Model.this.hasValidateBind ||= hasValidateBind

        // Return true if analysis succeeded
        def analyzeXPathGather(): Boolean = {

            // Analyze context/binding
            analyzeXPath()

            // Match on whether we have a @ref or not
            ref match {
                case Some(refValue) =>
                    getBindingAnalysis match {
                        case Some(bindingAnalysis) if bindingAnalysis.figuredOutDependencies =>
                            // There is a binding and analysis succeeded

                            // Remember dependent instances
                            val returnableInstances = bindingAnalysis.returnableInstances
                            bindInstances.addAll(returnableInstances)
                            if (hasCalculateComputedBind || hasCustomMip)
                                computedBindExpressionsInstances.addAll(returnableInstances)

                            if (hasValidateBind)
                                validationBindInstances.addAll(returnableInstances)

                        case _ => // analysis failed
                    }

                    // MIP analysis
                    for (mip <- allMIPNameToXPathMIP.values)
                        mip.analyzeXPath()
                case None => // No binding so don't look at MIPs
            }

            // Analyze children and compute result
            children.isEmpty || ((children map (_.analyzeXPathGather())) reduceLeft (_ && _))
        }

        override def freeTransientState() {

            super.freeTransientState()

            for (mip <- allMIPNameToXPathMIP.values)
                mip.analysis.freeTransientState()

            for (child <- children)
                child.freeTransientState()
        }

        override def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper, attributes: List[String] = Nil)(content: => Unit = {}) {
            super.toXML(propertyContext, helper, List("id", staticId, "context", context.orNull, "ref", ref.orNull)) {
                // @ref analysis is handled by superclass

                // MIP analysis
                for (mip <- allMIPNameToXPathMIP.values) {
                    helper.startElement("mip", Array("name", mip.name, "expression", mip.expression))
                    mip.analysis.toXML(propertyContext, helper)
                    helper.endElement
                }

                // Children
                for (child <- children)
                    child.toXML(propertyContext, helper)()
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
        
        // Binds: analyze all binds and return whether all of them were successfully analyzed
        figuredAllBindRefAnalysis = topLevelBinds.isEmpty || ((topLevelBinds map (_.analyzeXPathGather())) reduceLeft (_ && _))

        if (!figuredAllBindRefAnalysis) {
            bindInstances.clear()
            computedBindExpressionsInstances.clear()
            validationBindInstances.clear()
            // keep bindAnalysis as those can be used independently from each other
        }
    }

    def hasBinds = (bindElements ne null) && bindElements.nonEmpty
    def containsBind(bindId: String) = bindIds.contains(bindId)

    def getDefaultInstance = if (instances.nonEmpty) instances.head._2 else null

    override def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper, attributes: List[String] = Nil)(content: => Unit = {}) {

        super.toXML(propertyContext, helper, List(
                "scope", scope.scopeId,
                "prefixed-id", prefixedId,
                "default-instance-prefixed-id", defaultInstancePrefixedId.orNull,
                "analyzed-binds", figuredAllBindRefAnalysis.toString
        )) {
            // Output variable information
            for (variable <- variablesSeq)
                variable.toXML(propertyContext, helper, List())({})

            // Output binds information
            if (topLevelBinds.nonEmpty) {
                helper.startElement("binds")
                for (bind <- topLevelBinds)
                    bind.toXML(propertyContext, helper)()
                helper.endElement
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

    // Built-in MIP names
    val RELEVANT = "relevant"
    val READONLY = "readonly"
    val REQUIRED = "required"
    val CONSTRAINT = "constraint"
    val CALCULATE = "calculate"
    val INITIAL_VALUE = "initial-value"
    val TYPE = "type"

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
