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
import java.util.{LinkedHashMap ⇒ JLinkedHashMap}
import org.orbeon.oxf.xforms._

import action.XFormsActions
import analysis._
import event.EventHandlerImpl
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.collection.JavaConverters._
import org.orbeon.oxf.xforms.XFormsConstants._
import java.lang.String
import collection.immutable.List
import collection.mutable.{LinkedHashSet, LinkedHashMap}
import xbl.Scope
import org.orbeon.oxf.xml.{Dom4j, ContentHandlerHelper}
import Model._

/**
 * Static analysis of an XForms model <xf:model> element.
 */
class Model(val staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], val scope: Scope)
        extends ElementAnalysis(element, parent, preceding)
        with ChildrenBuilderTrait {

    require(staticStateContext ne null)
    require(scope ne null)

    val namespaceMapping = staticStateContext.partAnalysis.metadata.getNamespaceMapping(prefixedId)

    // NOTE: It is possible to imagine a model having a context and binding, but this is not supported now
    protected def computeContextAnalysis = None
    protected def computeValueAnalysis = None
    protected def computeBindingAnalysis = None
    val model = Some(this)

    // NOTE: Same code is in SimpleElementAnalysis, which is not optimal → maybe think about passing the container scope to constructors
    def containerScope = staticStateContext.partAnalysis.containingScope(prefixedId)

    override def getChildrenContext = defaultInstancePrefixedId map { defaultInstancePrefixedId ⇒ // instance('defaultInstanceId')
        PathMapXPathAnalysis(staticStateContext.partAnalysis, PathMapXPathAnalysis.buildInstanceString(defaultInstancePrefixedId),
            null, None, Map.empty[String, VariableTrait], null, scope, Some(defaultInstancePrefixedId), locationData, element)
    }

    // NOTE: It is possible to imagine a model having in-scope variables, but this is not supported now
    val inScopeVariables = Map.empty[String, VariableTrait]

    // Instance objects
    val instances = LinkedHashMap((
        for {
            instanceElement ← Dom4jUtils.elements(element, XFORMS_INSTANCE_QNAME).asScala
            newInstance = new Instance(staticStateContext, instanceElement, Some(Model.this), scope)
        } yield newInstance.staticId → newInstance): _*)

    def instancesMap = instances.asJava

    // General info about instances
    val hasInstances = instances.nonEmpty
    val defaultInstanceStaticId = instances.headOption map (_._1) orNull
    val defaultInstancePrefixedId = Option(if (hasInstances) scope.fullPrefix + defaultInstanceStaticId else null)

    // Get *:variable/*:var elements
    private val variableElements = Dom4jUtils.elements(element).asScala filter (e ⇒ ControlAnalysisFactory.isVariable(e.getQName)) asJava

    // Handle variables
    val variablesSeq: Seq[VariableAnalysisTrait] = {

        // NOTE: For now, all top-level variables in a model are visible first, then only are binds variables visible.
        // In the future, we might want to change that to use document order between variables and binds, but some
        // more thinking is needed wrt the processing model.

        // Iterate and resolve all variables in order
        var preceding: Option[SimpleElementAnalysis with VariableAnalysisTrait] = None

        for {
            variableElement ← variableElements.asScala
            analysis = {
                val result = new SimpleElementAnalysis(staticStateContext, variableElement, Some(Model.this), preceding, scope) with VariableAnalysisTrait
                preceding = Some(result)
                result
            }
        } yield
            analysis
    }

    def jVariablesSeq = variablesSeq.asJava

    val variablesMap: Map[String, VariableAnalysisTrait] = variablesSeq map (variable ⇒ variable.name → variable) toMap
    val jVariablesMap = variablesMap.asJava
    
    // For now this only checks actions and submissions, in the future should also build rest of content
    def findRelevantChildrenElements =
        Dom4j.elements(element) filter
            (e ⇒ XFormsActions.isAction(e.getQName) || e.getQName == XFORMS_SUBMISSION_QNAME) map
                ((_, containerScope))
    
    // Submissions (they are all direct children)
    private lazy val _submissions = children collect { case s: Submission ⇒ s }
    def jSubmissions = _submissions.asJava

    // Event handlers, including on submissions and within nested actions
    private lazy val _eventHandlers = descendants collect { case e: EventHandlerImpl ⇒ e }
    def jEventHandlers = _eventHandlers.asJava

    // Handle binds
    val bindIds = new LinkedHashSet[String]
    val bindInstances = new LinkedHashSet[String]                       // instances to which binds apply (i.e. bind/@ref point to them)
    val computedBindExpressionsInstances = new LinkedHashSet[String]    // instances to which computed binds apply
    val validationBindInstances = new LinkedHashSet[String]             // instances to which validation binds apply

    // TODO: use and produce variables introduced with xf:bind/@name

    // All binds by static id
    val bindsById = new JLinkedHashMap[String, Bind] // JAVA COLLECTION

    // Binds by name (for binds with a name)
    val bindsByName = new LinkedHashMap[String, Bind]
    def jBindsByName = bindsByName.asJava

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
        for (bindElement ← Dom4jUtils.elements(element, XFORMS_BIND_QNAME).asScala)
        yield new Bind(bindElement, this, preceding)
    }

    def topLevelBindsJava = topLevelBinds.asJava

    // In-scope variable on binds include variables implicitly declared with bind/@name
    lazy val bindsVariablesSeq = variablesMap ++ (bindsByName mapValues (new BindAsVariable(_)))

    class BindAsVariable(bind: Bind) extends VariableTrait {
        def name = bind.name
        def variableAnalysis = bind.getBindingAnalysis
    }

    var figuredAllBindRefAnalysis = !hasBinds // default value sets to true if no binds

    def hasBinds = topLevelBinds.nonEmpty
    def containsBind(bindId: String) = bindIds.contains(bindId)

    def getDefaultInstance = if (instances.nonEmpty) instances.head._2 else null

    // Represent a static <xf:bind> element
    class Bind(element: Element, parent: ElementAnalysis, preceding: Option[ElementAnalysis])
            extends SimpleElementAnalysis(staticStateContext, element, Some(parent), preceding, scope) {

        // Represent an individual MIP on an <xf:bind> element
        class MIP(val name: String) {
            val isCalculateComputedMIP = CalculateMIPNames.contains(name)
            val isValidateMIP = ValidateMIPNames.contains(name)
            val isCustomMIP = !isCalculateComputedMIP && !isValidateMIP
        }

        // Represent an XPath MIP
        class XPathMIP(name: String, val expression: String) extends MIP(name) {

            // Default to negative, analyzeXPath() can change that
            var analysis: XPathAnalysis = NegativeAnalysis(expression)

            def analyzeXPath() {

                def booleanOrStringExpression =
                    if (BooleanXPathMIPNames.contains(name)) "xs:boolean((" + expression + ")[1])" else "xs:string((" + expression + ")[1])"

                // Analyze and remember if figured out
                Bind.this.analyzeXPath(getChildrenContext, bindsVariablesSeq, booleanOrStringExpression) match {
                    case valueAnalysis if valueAnalysis.figuredOutDependencies ⇒ this.analysis = valueAnalysis
                    case _ ⇒ // NOP
                }
            }
        }

        // The type MIP is not an XPath expression
        class TypeMIP(name: String, val datatype: String) extends MIP(name)

        // Globally remember binds ids
        Model.this.bindIds.add(staticId)
        Model.this.bindsById.put(staticId, Bind.this)

        // Remember variables mappings
        val name = element.attributeValue(NAME_QNAME)
        if (name ne null)
            bindsByName.put(name, Bind.this)

        // Built-in XPath MIPs
        val mipNameToXPathMIP =
            for {
                (qName, mip) ← QNameToXPathMIP
                attributeValue = element.attributeValue(qName)
                if attributeValue ne null
            } yield
                mip.name → new XPathMIP(mip.name, attributeValue)

        // Type MIP is special as it is not an XPath expression
        val typeMIP = element.attributeValue(TYPE_QNAME) match {
            case value if value ne null ⇒ Some(new TypeMIP(TYPE, value))
            case _ ⇒ None
        }

        // Custom MIPs
        val customMIPNameToXPathMIP = Predef.Map((
                for {
                    attribute ← Dom4j.attributes(element) // check all the element's attributes

                    attributeQName = attribute.getQName
                    attributePrefix = attributeQName.getNamespacePrefix
                    attributeURI = attributeQName.getNamespaceURI

                    // Any QName-but-not-NCName which is not in the xforms or xxforms namespace or an XML attribute
                    // NOTE: Also allow for xxf:events-mode extension MIP
                    if attributePrefix.nonEmpty && !attributePrefix.startsWith("xml") &&
                            attributeURI != XFORMS_NAMESPACE_URI &&
                            (attributeURI != XXFORMS_NAMESPACE_URI || attributeQName == XXFORMS_EVENT_MODE_QNAME)

                    customMIPName = buildCustomMIPName(attribute.getQualifiedName)
                } yield
                    customMIPName → new XPathMIP(customMIPName, attribute.getValue)): _*)

        def customMIPs = customMIPNameToXPathMIP.asJava

        // All XPath MIPs
        val allMIPNameToXPathMIP = mipNameToXPathMIP ++ customMIPNameToXPathMIP

        // Create children binds
        val children: Seq[Bind] = Dom4jUtils.elements(element, XFORMS_BIND_QNAME).asScala map (new Bind(_, Bind.this, preceding))
        def jChildren = children.asJava

        def getMIP(mipName: String) = if (mipName == TYPE) typeMIP else allMIPNameToXPathMIP.get(mipName)

        // For Java callers (can return null)
        def getInitialValue = getMIPExpressionOrNull(InitialValue.name)
        def getCalculate = getMIPExpressionOrNull(Calculate.name)
        def getRelevant = getMIPExpressionOrNull(Relevant.name)
        def getReadonly = getMIPExpressionOrNull(Readonly.name)
        def getRequired = getMIPExpressionOrNull(Required.name)
        def getConstraint = getMIPExpressionOrNull(Constraint.name)
        def getType = typeMIP map (_.datatype) orNull
        def getCustom(mipName: String) = getMIPExpressionOrNull(mipName)

        def getMIPExpressionOrNull(mipName: String) = allMIPNameToXPathMIP.get(mipName) match {
            case Some(mip) ⇒ mip.expression
            case None ⇒ null
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
            case Some(_) if hasNodeBinding ⇒ Some(analyzeXPath(getChildrenContext, "xs:string(.[1])"))
            case _ ⇒ None
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
                                bindInstances ++= returnableInstances

                                if (hasCalculateComputedMIPs || hasCustomMIPs)
                                    computedBindExpressionsInstances ++= returnableInstances

                                if (hasValidateMIPs)
                                    validationBindInstances ++= returnableInstances

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
            val childrenSucceeded = (children map (_.analyzeXPathGather)).foldLeft(true)(_ && _)

            // Result
            refSucceeded && childrenSucceeded
        }

        def analyzeMIPs() {
            // Analyze local MIPs if there is a @ref
            ref match {
                case Some(_) ⇒
                    for (mip ← allMIPNameToXPathMIP.values)
                        mip.analyzeXPath()
                case None ⇒
            }

            // Analyze children
            children foreach (_.analyzeMIPs())
        }

        override def freeTransientState() {

            super.freeTransientState()

            for (mip ← allMIPNameToXPathMIP.values)
                mip.analysis.freeTransientState()

            for (child ← children)
                child.freeTransientState()
        }

        override def toXML(helper: ContentHandlerHelper, attributes: List[String] = Nil)(content: ⇒ Unit = ()) {
            super.toXML(helper, List("id", staticId, "context", context.orNull, "ref", ref.orNull)) {
                // @ref analysis is handled by superclass

                // MIP analysis
                for (mip ← allMIPNameToXPathMIP.values) {
                    helper.startElement("mip", Array("name", mip.name, "expression", mip.expression))
                    mip.analysis.toXML(helper)
                    helper.endElement()
                }

                // Children
                for (child ← children)
                    child.toXML(helper)()
            }
        }
    }

    // TODO: instances on which MIPs depend

    override def analyzeXPath() {
        // Analyze this
        super.analyzeXPath()

        // Variables
        for (variable ← variablesSeq)
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

    override def toXML(helper: ContentHandlerHelper, attributes: List[String] = Nil)(content: ⇒ Unit = ()) {

        super.toXML(helper, List(
            "scope", scope.scopeId,
            "prefixed-id", prefixedId,
            "default-instance-prefixed-id", defaultInstancePrefixedId.orNull,
            "analyzed-binds", figuredAllBindRefAnalysis.toString
        )) {
            // Output variable information
            for (variable ← variablesSeq)
                variable.toXML(helper, List())({})
            
            // TODO: output handlers

            // Output binds information
            if (topLevelBinds.nonEmpty) {
                helper.startElement("binds")
                for (bind ← topLevelBinds)
                    bind.toXML(helper)()
                helper.endElement()
            }

            // Output instances information
            def outputInstanceList(name: String, values: collection.Set[String]) {
                if (values.nonEmpty) {
                    helper.startElement(name)
                    for (value ← values)
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

        for (variable ← variablesSeq)
            variable.freeTransientState()

        for (bind ← topLevelBinds)
            bind.freeTransientState();
    }
}

object Model {

    // MIP enumeration
    sealed trait MIP { val name: String; def qName = QName.get(name) }

    trait ComputedMIP extends MIP
    trait ValidateMIP extends MIP
    trait XPathMIP    extends MIP
    trait BooleanMIP  extends XPathMIP
    trait StringMIP   extends XPathMIP

    // NOTE: "required" is special: it is evaluated during recalculate, but used during revalidate. In effect both
    // recalculate AND revalidate depend on it. Ideally maybe revalidate would depend on the the *value* of the
    // "required" MIP, not on the XPath of it. See also what we would need for xxf:valid(), etc. functions.
    case object Relevant     extends MIP with BooleanMIP with ComputedMIP { val name = "relevant" }
    case object Readonly     extends MIP with BooleanMIP with ComputedMIP { val name = "readonly" }
    case object Required     extends MIP with BooleanMIP with ComputedMIP with ValidateMIP { val name = "required" }
    case object Constraint   extends MIP with BooleanMIP with ValidateMIP { val name = "constraint" }
    case object Calculate    extends MIP with StringMIP  with ComputedMIP { val name = "calculate" }
    case object InitialValue extends MIP with StringMIP  with ComputedMIP { val name = "initial-value"; override def qName = XXFORMS_DEFAULT_QNAME }
    case object Type         extends MIP with ValidateMIP { val name = "type" }

    case class Custom(override val name: String) extends MIP with XPathMIP

    val AllMIPs                 = Set[MIP](Relevant, Readonly, Required, Constraint, Calculate, InitialValue, Type)
    val AllMIPsByName           = AllMIPs map (mip ⇒ mip.name → mip) toMap
    val AllMIPNames             = AllMIPs map (_.name)
    val MIPNameToAttributeQName = AllMIPs map (m ⇒ m.name → m.qName) toMap

    val QNameToXPathComputedMIP = AllMIPs collect { case m: XPathMIP with ComputedMIP ⇒ m.qName → m } toMap
    val QNameToXPathValidateMIP = AllMIPs collect { case m: XPathMIP with ValidateMIP ⇒ m.qName → m } toMap
    val QNameToXPathMIP         = QNameToXPathComputedMIP ++ QNameToXPathValidateMIP

    val CalculateMIPNames       = AllMIPs collect { case m: ComputedMIP ⇒ m.name }
    val ValidateMIPNames        = AllMIPs collect { case m: ValidateMIP ⇒ m.name }
    val BooleanXPathMIPNames    = AllMIPs collect { case m: XPathMIP with BooleanMIP ⇒ m.name }
    val StringXPathMIPNames     = AllMIPs collect { case m: XPathMIP with StringMIP ⇒ m.name }

    def buildCustomMIPName(qualifiedName: String) = qualifiedName.replace(':', '-')

    // Constants for Java callers
    val RELEVANT = Relevant.name
    val READONLY = Readonly.name
    val REQUIRED = Required.name
    val CONSTRAINT = Constraint.name
    val CALCULATE = Calculate.name
    val INITIAL_VALUE = InitialValue.name
    val TYPE = Type.name

    // MIP default values for Java callers
    val DEFAULT_RELEVANT = true
    val DEFAULT_READONLY = false
    val DEFAULT_REQUIRED = false
    val DEFAULT_VALID = true
}
