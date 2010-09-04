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
import java.util.{Map => JMap, Collections => JCollections, LinkedHashMap => JLinkedHashMap, LinkedHashSet => JLinkedHashSet, Set => JSet}
import org.orbeon.oxf.xforms._


import org.orbeon.oxf.xforms.analysis.XPathAnalysis
import org.orbeon.oxf.xforms.analysis.XPathAnalysis2
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.collection.JavaConversions._
import collection.mutable.LinkedHashMap
import org.orbeon.oxf.xforms.XFormsConstants._

/**
 * Static analysis of an XForms model.
 */
class Model(val staticState: XFormsStaticState, val scope: XBLBindings#Scope, val document: Document) {

    // TODO: ideally document should not be kept as a model variable: attributes should be extracted and copied etc.

    assert(staticState != null)
    assert(scope != null)
    assert(document != null)

    // Ids
    val staticId = XFormsUtils.getElementStaticId(document.getRootElement)
    val prefixedId = scope.getFullPrefix + staticId

    // Instance objects
    // SEE: http://stackoverflow.com/questions/3827964/scala-for-comprehension-returning-an-ordered-map
    val instances = LinkedHashMap((
        for {
            instanceElement <- Dom4jUtils.elements(document.getRootElement, XFORMS_INSTANCE_QNAME)
            newInstance = new Instance(instanceElement, scope)
        } yield (newInstance.staticId -> newInstance)): _*)

    val instancesMap: JMap[String, Instance] = instances // JAVA COLLECTION for Java access only

    // General info about instances
    val hasInstances = instances.nonEmpty
    val defaultInstanceStaticId = if (hasInstances) instances.head._1 else null
    val defaultInstancePrefixedId = if (hasInstances) scope.getFullPrefix + defaultInstanceStaticId else null

    // Handle variables
    val variables: JMap[String, SimpleAnalysis] = {// JAVA COLLECTION

        // Get xxf:variable and exf:variable (in fact *:variable) elements
        val variableElements = Dom4jUtils.elements(document.getRootElement) filter (_.getName == XXFORMS_VARIABLE_NAME)

        if (variableElements.nonEmpty) {
            // Root analysis for model
            val modelRootAnalysis = new ModelAnalysis(staticState, scope, null, null, JCollections.emptyMap(), false, this) {
                override def computeBindingAnalysis(element: Element) = {
                    if (defaultInstancePrefixedId != null)
                        // Start with instance('defaultInstanceId')
                        analyzeXPath(staticState, null, getModelPrefixedId, XPathAnalysis2.buildInstanceString(defaultInstancePrefixedId))
                    else
                        null
                }
            }

            // Iterate and resolve all variables in order
            // TODO: Here all variables are passed with the same Map of in-scope variables. In the end, all variables see all other variables, which is not correct.
            val result = new JLinkedHashMap[String, SimpleAnalysis]
            for (variableElement <- variableElements) {
                // TODO: Here all variables are passed with the same Map of in-scope variables. In the end, all variables see all other variables, which is not correct.
                val currentVariableAnalysis = new ModelVariableAnalysis(staticState, scope, variableElement, modelRootAnalysis, result, this)
                result.put(currentVariableAnalysis.name, currentVariableAnalysis)
            }

            result
        } else {
            JCollections.emptyMap()
        }
    }

    // Handle binds
    val bindElements = Dom4jUtils.elements(document.getRootElement, XFORMS_BIND_QNAME)// JAVA COLLECTION
    val (bindIds: JSet[String],
         customMIPs: JMap[String, JMap[String, Bind#MIP]], // bind static id => MIP mapping
         bindInstances: JSet[String],                      // instances to which binds apply (i.e. bind/@ref point to them)
         computedBindExpressionsInstances: JSet[String],   // instances to which computed binds apply
         validationBindInstances: JSet[String]) =          // instances to which validation binds apply
        // TODO: use and produce variables introduced with xf:bind/@name
        if (hasBinds) {
            // Analyse binds
            (new JLinkedHashSet[String],
             new JLinkedHashMap[String, JMap[String, Bind#MIP]],
             new JLinkedHashSet[String],
             new JLinkedHashSet[String],
             new JLinkedHashSet[String])
        } else {
            // Easy case to figure out
            (JCollections.emptySet[String],
             JCollections.emptyMap[String, JMap[String, Bind#MIP]],
             JCollections.emptySet[String],
             JCollections.emptySet[String],
             JCollections.emptySet[String])
        }

    var figuredAllBindRefAnalysis = !hasBinds // default value sets to true if no binds

    // All binds by static id
    val bindsById = new JLinkedHashMap[String, Bind]// JAVA COLLECTION

    var hasCalculateComputedBind = false // default
    var hasValidateBind = false // default

    // Top-level binds and create static binds hierarchy
    val topLevelBinds: Seq[Bind] =
        for (bindElement <- Dom4jUtils.elements(document.getRootElement, XFORMS_BIND_QNAME))
            yield new Bind(bindElement, null)

    // Represent a static <xf:bind> element
    class Bind(element: Element, val parent: Bind) {

        // Represent an individual MIP on an <xf:bind> element
        class MIP(val name: String, val expression: String) {

            val isCalculateComputedMIP = Model.CALCULATE_MIP_NAMES.contains(name)
            val isValidateMIP = Model.VALIDATE_MIP_NAMES.contains(name)
            val isCustomMIP = !isCalculateComputedMIP && !isValidateMIP

            var analysis: XPathAnalysis = XPathAnalysis2.CONSTANT_NEGATIVE_ANALYSIS // default to negative, analyzeXPath() can change that

            def analyzeXPath(parentAnalysis: SimpleAnalysis) {
                // TODO: handle model variables + xf:bind/@name
                val mipAnalysis = new ModelAnalysis(staticState, scope, element, parentAnalysis, null, true, Model.this) {
                    // Just use xf:bind/@ref analysis as binding analysis
                    override def computeBindingAnalysis(element: Element) = parentAnalysis.getBindingAnalysis
                    // What interests us is the value analysis
                    override def computeValueAnalysis = analyzeXPath(staticState, parentAnalysis.getBindingAnalysis, prefixedId, modifiedExpression)
                }
                mipAnalysis.analyzeXPath()// evaluate aggressively

                // Remember MIP analysis if non-null
                if (mipAnalysis.getValueAnalysis != null)
                    this.analysis = mipAnalysis.getValueAnalysis
            }

            private def modifiedExpression =
                if (Model.BOOLEAN_XPATH_MIP_NAMES.contains(name)) "xs:boolean((" + expression + ")[1])" else "xs:string((" + expression + ")[1])"
        }

        // Common attributes
        val staticId = XFormsUtils.getElementStaticId(element)
        val prefixedId = scope.getFullPrefix + staticId
        val ref = Option(SimpleAnalysis.getBindingExpression(element))
        val context = Option(element.attributeValue(CONTEXT_QNAME))

        // Globally remember binds ids
        Model.this.bindIds.add(staticId)
        Model.this.bindsById.put(staticId, Bind.this)

        // Built-in XPath MIPs
        val mipNameToXPathMIP =
            for (entry <- Model.QNAME_TO_XPATH_MIP_NAME; attributeValue = element.attributeValue(entry._1); if attributeValue != null)
                yield (entry._2 -> new MIP(entry._2, attributeValue))

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

        // Type MIP is special as it is not an XPath expression
        val typeMIP = Option(element.attributeValue(TYPE_QNAME))

        // Create children binds
        val children: Seq[Bind] = Dom4jUtils.elements(element, XFORMS_BIND_QNAME) map (new Bind(_, Bind.this))

        var refAnalysis: XPathAnalysis = XPathAnalysis2.CONSTANT_NEGATIVE_ANALYSIS // default to negative, analyzeXPath() can change that

//        def getRelevant = mipNameToXPathMIP.get(Model.RELEVANT)
//        def getReadonly = mipNameToXPathMIP.get(Model.READONLY)
//        def getRequired = mipNameToXPathMIP.get(Model.REQUIRED)
//        def getConstraint = mipNameToXPathMIP.get(Model.CONSTRAINT)
//        def getCalculate = mipNameToXPathMIP.get(Model.CALCULATE)
//        def getInitialValue = mipNameToXPathMIP.get(Model.INITIAL_VALUE)
//        def getType = typeMIP
//        def getCustom(mipName: String) = customMIPNameToXPathMIP.get(mipName)

        def getMIP(mipName: String) = allMIPNameToXPathMIP.get(mipName).getOrElse(null)

        def hasCalculateComputedBind = Model.QNAME_TO_XPATH_COMPUTED_MIP_NAME exists (mipNameToXPathMIP contains _._2)
        def hasValidateBind = typeMIP.isDefined || (Model.QNAME_TO_XPATH_VALIDATE_MIP_NAME exists (mipNameToXPathMIP contains _._2))
        def hasCustomMip = customMIPNameToXPathMIP.nonEmpty

        // Globally remember if we have seen these categories of binds
        Model.this.hasCalculateComputedBind ||= hasCalculateComputedBind
        Model.this.hasValidateBind ||= hasValidateBind

        // Return true if analysis succeeded
        def analyzeXPath(parentAnalysis: SimpleAnalysis): Boolean = {
            if (context.isDefined) {
                // TODO: handle @context
                // Don't recurse into children as they might depend on the ancestor context as well
                false
            } else {

                def analyzeChildren(baseAnalysis: SimpleAnalysis) = children.isEmpty || ((children map (_.analyzeXPath(baseAnalysis))) reduceLeft (_ && _))

                // Match on whether we have a @ref or not
                ref match {
                    case Some(refValue) => {
                        // Analyze @ref first
                        // TODO: handle model variables
                        val refModelAnalysis = new ModelAnalysis(staticState, scope, element, parentAnalysis, null, false, Model.this)
                        refModelAnalysis.analyzeXPath()// evaluate aggressively

                        if (refModelAnalysis.getBindingAnalysis.figuredOutDependencies) {
                            // Analysis succeeded

                            // Remember it
                            Bind.this.refAnalysis = refModelAnalysis.getBindingAnalysis

                            // MIP analysis
                            for (mip <- allMIPNameToXPathMIP.values)
                                mip.analyzeXPath(refModelAnalysis)// all MIPs are in the context of the @ref

                            // Remember dependent instances
                            val returnableInstances = refModelAnalysis.getBindingAnalysis.returnableInstances
                            bindInstances.addAll(returnableInstances)
                            if (hasCustomMip || hasCalculateComputedBind)
                                computedBindExpressionsInstances.addAll(returnableInstances)

                            if (hasValidateBind)
                                validationBindInstances.addAll(returnableInstances)

                            // Analyze children and compute result
                            analyzeChildren(refModelAnalysis)
                        } else {
                            // Analysis failed

                            // NOTE: We might still want to recurse in case descendant binds don't depend at all on the context,
                            // but in general, they will.

                            false
                        }
                    }
                    case None => {
                        // Just skip this <xf:bind> as it doesn't have a @ref
                        // Recurse to find nested bind elements
                        // Analyze children and compute result
                        analyzeChildren(parentAnalysis)
                    }
                }
            }
        }

        def freeTransientState() {
            refAnalysis.freeTransientState()

            for (mip <- allMIPNameToXPathMIP.values)
                mip.analysis.freeTransientState()
        }

        def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) {
            helper.startElement("bind", Array("id", staticId, "context", context.orNull, "ref", ref.orNull))

            // @ref analysis
            refAnalysis.toXML(propertyContext, helper)

            // MIP analysis
            for (mip <- allMIPNameToXPathMIP.values) {
                helper.startElement("mip", Array("name", mip.name, "expression", mip.expression))
                mip.analysis.toXML(propertyContext, helper)
                helper.endElement
            }

            // Children
            for (child <- children)
                child.toXML(propertyContext, helper)

            helper.endElement
        }
    }

    // TODO: instances on which MIPs depend

    def analyzeXPath() {
        // Variables
        for (variableAnalysis <- variables.values)
            variableAnalysis.analyzeXPath()
        
        // Binds
        val rootAnalysis =
            new ModelAnalysis(staticState, scope, document.getRootElement, null, variables, false, Model.this) {
                override def computeBindingAnalysis(element: Element): XPathAnalysis = {
                    if (defaultInstancePrefixedId != null)
                        // Start with instance('defaultInstanceId')
                        analyzeXPath(staticState, null, getModelPrefixedId, XPathAnalysis2.buildInstanceString(defaultInstancePrefixedId))
                    else
                        null
                }
            }

        // Analyze all binds and return whether all of them were successfully analyzed
        figuredAllBindRefAnalysis = topLevelBinds.isEmpty || ((topLevelBinds map (_.analyzeXPath(rootAnalysis))) reduceLeft (_ && _))

        if (!figuredAllBindRefAnalysis) {
            bindInstances.clear()
            computedBindExpressionsInstances.clear()
            validationBindInstances.clear()
            // keep bindAnalysis as those can be used independently from each other
        }
    }

    def hasBinds = bindElements != null && bindElements.nonEmpty
    def containsBind(bindId: String) = bindIds.contains(bindId)

    def getDefaultInstance = if (instances.nonEmpty) instances.head._2 else null

    def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) {
        helper.startElement("model", Array(
                "scope", scope.scopeId,
                "prefixed-id", prefixedId,
                "default-instance-prefixed-id", defaultInstancePrefixedId,
                "analyzed-binds", figuredAllBindRefAnalysis.toString
        ))

        // Output variable information
        for (variable <- variables)
            variable._2.asInstanceOf[ModelVariableAnalysis].toXML(propertyContext, helper)

        // Output binds information
        if (topLevelBinds.nonEmpty) {
            helper.startElement("binds")
            for (bind <- topLevelBinds) {
                bind.toXML(propertyContext, helper)
            }
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

        helper.endElement()
    }

    def freeTransientState() {
        for (analysis <- variables.values)
            analysis.freeTransientState()

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

    val QNAME_TO_XPATH_COMPUTED_MIP_NAME = Predef.Map(
        RELEVANT_QNAME -> RELEVANT,
        READONLY_QNAME -> READONLY,
        CALCULATE_QNAME -> CALCULATE,
        XXFORMS_DEFAULT_QNAME -> INITIAL_VALUE)

    val QNAME_TO_XPATH_VALIDATE_MIP_NAME = Predef.Map(
        REQUIRED_QNAME -> REQUIRED,
        CONSTRAINT_QNAME -> CONSTRAINT)

    val QNAME_TO_VALIDATE_MIP_NAME = QNAME_TO_XPATH_VALIDATE_MIP_NAME + (TYPE_QNAME -> TYPE)
    val QNAME_TO_XPATH_MIP_NAME = QNAME_TO_XPATH_COMPUTED_MIP_NAME ++ QNAME_TO_XPATH_VALIDATE_MIP_NAME

    val CALCULATE_MIP_NAMES = Set(QNAME_TO_XPATH_COMPUTED_MIP_NAME.values.toSeq: _*)
    val VALIDATE_MIP_NAMES = Set(QNAME_TO_VALIDATE_MIP_NAME.values.toSeq: _*)

    val BOOLEAN_XPATH_MIP_NAMES = Set(RELEVANT, READONLY, REQUIRED, CONSTRAINT)
    val STRING_XPATH_MIP_NAMES = Set(CALCULATE, INITIAL_VALUE)

    def buildCustomMIPName(qualifiedName: String) = qualifiedName.replace(':', '-')
}