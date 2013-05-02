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
import org.orbeon.oxf.xforms._

import action.XFormsActions
import analysis._
import event.EventHandlerImpl
import collection.JavaConverters._
import org.orbeon.oxf.xforms.XFormsConstants._
import java.lang.String
import collection.immutable.List
import collection.mutable.{LinkedHashSet, LinkedHashMap}
import org.orbeon.oxf.xml.{ShareableXPathStaticContext, Dom4j, ContentHandlerHelper}
import Model._
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.util.{XPath ⇒ OrbeonXPath}
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.common.ValidationException

/**
 * Static analysis of an XForms model <xf:model> element.
 */
class Model(val staticStateContext: StaticStateContext, elem: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], val scope: Scope)
        extends ElementAnalysis(staticStateContext.partAnalysis, elem, parent, preceding)
        with ChildrenBuilderTrait
        with ModelInstances
        with ModelVariables
        with ModelSubmissions
        with ModelEventHandlers
        with ModelBinds {

    require(staticStateContext ne null)
    require(scope ne null)

    type Bind = BindTree#Bind

    val namespaceMapping = part.metadata.getNamespaceMapping(prefixedId)

    // NOTE: It is possible to imagine a model having a context and binding, but this is not supported now
    protected def computeContextAnalysis = None
    protected def computeValueAnalysis = None
    protected def computeBindingAnalysis = None
    val model = Some(this)

    // NOTE: Same code is in SimpleElementAnalysis, which is not optimal → maybe think about passing the container scope to constructors
    def containerScope = part.containingScope(prefixedId)

    override def getChildrenContext = defaultInstancePrefixedId map { defaultInstancePrefixedId ⇒ // instance('defaultInstanceId')
        PathMapXPathAnalysis(part, PathMapXPathAnalysis.buildInstanceString(defaultInstancePrefixedId),
            null, None, Map.empty[String, VariableTrait], null, scope, Some(defaultInstancePrefixedId), locationData, element, avt = false)
    }
    
    // For now this only checks actions and submissions, in the future should also build rest of content
    override def findRelevantChildrenElements =
        findAllChildrenElements collect
            { case (e, s) if XFormsActions.isAction(e.getQName) || Set(XFORMS_SUBMISSION_QNAME, XFORMS_INSTANCE_QNAME)(e.getQName) ⇒ (e, s) }

    // Above we only create actions, submissions and instances as children. But binds are also indexed so add them.
    override def indexedElements = super.indexedElements ++ bindsById.values

    override def analyzeXPath() {
        // Analyze this
        super.analyzeXPath()

        analyzeVariablesXPath()
        analyzeBindsXPath()
    }

    override def toXMLAttributes = Seq(
        "scope"                        → scope.scopeId,
        "prefixed-id"                  → prefixedId,
        "default-instance-prefixed-id" → defaultInstancePrefixedId.orNull,
        "analyzed-binds"               → figuredAllBindRefAnalysis.toString
    )

    override def toXMLContent(helper: ContentHandlerHelper): Unit = {
        super.toXMLContent(helper)
        variablesToXML(helper)
        bindsToXML(helper)
        instancesToXML(helper)
        handlersToXML(helper)
    }

    override def freeTransientState(): Unit = {
        super.freeTransientState()
        freeVariablesTransientState()
        freeBindsTransientState()
    }
}

trait ModelInstances {

    self: Model ⇒

    // Instance objects
    lazy val instances: collection.Map[String, Instance] = LinkedHashMap(children collect { case instance: Instance ⇒ instance.staticId → instance }: _*)

    def instancesMap = instances.asJava

    // General info about instances
    lazy val hasInstances = instances.nonEmpty
    lazy val defaultInstance = instances.headOption map (_._2)
    lazy val defaultInstanceStaticId = instances.headOption map (_._1) orNull
    lazy val defaultInstancePrefixedId = Option(if (hasInstances) scope.fullPrefix + defaultInstanceStaticId else null)
    // TODO: instances on which MIPs depend

    def instancesToXML(helper: ContentHandlerHelper): Unit = {
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

trait ModelVariables {

    self: Model ⇒

    // NOTE: It is possible to imagine a model having in-scope variables, but this is not supported now
    val inScopeVariables = Map.empty[String, VariableTrait]

    // Get *:variable/*:var elements
    private val variableElements = Dom4j.elements(self.element) filter (e ⇒ ControlAnalysisFactory.isVariable(e.getQName)) asJava

    // Handle variables
    val variablesSeq: Seq[VariableAnalysisTrait] = {

        // NOTE: For now, all top-level variables in a model are visible first, then only are binds variables visible.
        // In the future, we might want to change that to use document order between variables and binds, but some
        // more thinking is needed wrt the processing model.

        // Iterate and resolve all variables in order
        var preceding: Option[SimpleElementAnalysis with VariableAnalysisTrait] = None

        for {
            variableElement ← variableElements.asScala
            analysis: VariableAnalysisTrait = {
                val result = new SimpleElementAnalysis(staticStateContext, variableElement, Some(self), preceding, scope) with VariableAnalysisTrait
                preceding = Some(result)
                result
            }
        } yield
            analysis
    }

    def jVariablesSeq = variablesSeq.asJava

    val variablesMap: Map[String, VariableAnalysisTrait] = variablesSeq map (variable ⇒ variable.name → variable) toMap
    val jVariablesMap = variablesMap.asJava

    def analyzeVariablesXPath(): Unit =
        for (variable ← variablesSeq)
            variable.analyzeXPath()

    def variablesToXML(helper: ContentHandlerHelper): Unit =
        // Output variable information
        for (variable ← variablesSeq)
            variable.toXML(helper)

    def freeVariablesTransientState(): Unit =
        for (variable ← variablesSeq)
            variable.freeTransientState()
}

trait ModelSubmissions {

    self: Model ⇒

    // Submissions (they are all direct children)
    lazy val submissions = children collect { case s: Submission ⇒ s }
    def jSubmissions = submissions.asJava
}

trait ModelEventHandlers {

    self: Model ⇒

    // Event handlers, including on submissions and within nested actions
    lazy val eventHandlers = descendants collect { case e: EventHandlerImpl ⇒ e }
    def jEventHandlers = eventHandlers.asJava

    def handlersToXML(helper: ContentHandlerHelper) =
        eventHandlers foreach (_.toXML(helper))
}

class BindTree(model: Model, bindElements: Seq[Element], isCustomMIP: QName ⇒ Boolean) {

    bindTree ⇒

    // All bind ids
    val bindIds = new LinkedHashSet[String]

    // All binds by static id
    val bindsById = new LinkedHashMap[String, Bind]

    // Binds by name (for binds with a name)
    val bindsByName = new LinkedHashMap[String, Bind]

    // Types of binds we have
    var hasDefaultValueBind = false
    var hasCalculateBind = false
    var hasTypeBind = false
    var hasRequiredBind = false
    var hasConstraintBind = false

    var hasCalculateComputedCustomBind = false // default
    var hasValidateBind = false // default

    // Instances affected by binding XPath expressions
    val bindInstances = new LinkedHashSet[String]                       // instances to which binds apply (i.e. bind/@ref point to them)
    val computedBindExpressionsInstances = new LinkedHashSet[String]    // instances to which computed binds apply
    val validationBindInstances = new LinkedHashSet[String]             // instances to which validation binds apply

    // Create static binds hierarchy and yield top-level binds
    val topLevelBinds: Seq[Bind] = {
        // NOTE: For now, do as if binds follow all top-level variables
        val preceding = if (model.variablesSeq.isEmpty) None else Some(model.variablesSeq.last)
        for (bindElement ← bindElements)
            yield new Bind(bindElement, model, preceding)
    }

    def hasBinds = topLevelBinds.nonEmpty

    // Destroy the tree of binds
    def destroy(): Unit =
        bindsById.values foreach model.part.unmapScopeIds

    // Add a new bind
    def addBind(rawBindElement: Element, parentId: String, precedingId: Option[String]): Unit = {

        assert(! model.part.isTopLevel)

        // First annotate tree
        val (annotatedTree, _) =
            model.part.xblBindings.annotateSubtree(
                None,
                Dom4jUtils.createDocumentCopyParentNamespaces(rawBindElement),
                model.scope,
                model.scope,
                XXBLScope.inner,
                model.containerScope,
                hasFullUpdate = false,
                ignoreRoot = false,
                needCompact = false)

        // Add new bind to parent
        bindsById(parentId).addBind(annotatedTree.getRootElement, precedingId)

        // NOTE: We are not in a top-level part, so for now XPath analysis doesn't need to be updated
    }

    // Remove an existing bind
    def removeBind(bind: Bind): Unit = {

        assert(! model.part.isTopLevel)

        bind.parent match {
            case Some(parentBind: Bind) ⇒ parentBind.removeBind(bind)
            case _ ⇒ throw new IllegalArgumentException // for now, cannot remove top-level binds
        }

        // TODO: update has*
        // NOTE: We are not in a top-level part, so for now XPath analysis doesn't need to be updated
    }

    // In-scope variable on binds include variables implicitly declared with bind/@name
    // Used by XPath analysis
    private lazy val allBindVariables = model.variablesMap ++ (bindsByName map { case (k, v) ⇒ k → new BindAsVariable(v) })

    class BindAsVariable(bind: Bind) extends VariableTrait {
        def name = bind.name
        def variableAnalysis = bind.getBindingAnalysis
    }

    // Whether we figured out all XPath ref analysis
    var figuredAllBindRefAnalysis = ! hasBinds // default value sets to true if no binds

    // Represent a static <xf:bind> element
    class Bind(element: Element, parent: ElementAnalysis, preceding: Option[ElementAnalysis])
            extends SimpleElementAnalysis(model.staticStateContext, element, Some(parent), preceding, model.scope) {

        // Represent an individual MIP on an <xf:bind> element
        class MIP(val name: String) {
            val isCalculateComputedMIP = CalculateMIPNames(name)
            val isValidateMIP          = ValidateMIPNames(name)
            val isCustomMIP            = ! isCalculateComputedMIP && ! isValidateMIP
        }

        // Represent an XPath MIP
        class XPathMIP(name: String, expression: String) extends MIP(name) {

            // Compile the expression right away
            val compiledExpression = {
                val booleanOrStringExpression =
                    if (BooleanXPathMIPNames(name)) "boolean(" + expression + ")" else "string((" + expression + ")[1])"

                OrbeonXPath.compileExpression(booleanOrStringExpression, Bind.this.namespaceMapping, Bind.this.locationData, XFormsFunctionLibrary, avt = false)
            }

            // Default to negative, analyzeXPath() can change that
            var analysis: XPathAnalysis = NegativeAnalysis(expression)

            def analyzeXPath() {

                val allBindVariablesInScope = allBindVariables

                // Saxon: "In the case of free-standing XPath expressions it will be the StaticContext object"
                val staticContext  = compiledExpression.expression.getInternalExpression.getContainer.asInstanceOf[ShareableXPathStaticContext]
                if (staticContext ne null) {
                    // NOTE: The StaticContext can be null if the expression is a constant such as BooleanValue
                    val usedVariables = staticContext.referencedVariables

                    // Check whether all variables used by the expression are actually in scope, throw otherwise
                    usedVariables find (name ⇒ ! allBindVariablesInScope.contains(name.getLocalName)) foreach { name ⇒
                        throw new ValidationException("Undeclared variable in XPath expression: $" + name.getClarkName, Bind.this.locationData)
                    }
                }

                // Analyze and remember if figured out
                Bind.this.analyzeXPath(getChildrenContext, allBindVariablesInScope, compiledExpression) match {
                    case valueAnalysis if valueAnalysis.figuredOutDependencies ⇒ this.analysis = valueAnalysis
                    case _ ⇒ // NOP
                }
            }
        }

        // The type MIP is not an XPath expression
        class TypeMIP(name: String, val datatype: String) extends MIP(name)

        // Globally remember binds ids
        bindTree.bindIds += staticId
        bindTree.bindsById += staticId → Bind.this

        // Remember variables mappings
        val name = element.attributeValue(NAME_QNAME)
        if (name ne null)
            bindsByName += name → Bind.this

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
                attribute       ← Dom4j.attributes(element) // check all the element's attributes
                if isCustomMIP(attribute.getQName)
                customMIPName   = buildCustomMIPName(attribute.getQualifiedName)
            } yield
                customMIPName → new XPathMIP(customMIPName, attribute.getValue)): _*)

        def customMIPs = customMIPNameToXPathMIP.asJava

        // All XPath MIPs
        val allMIPNameToXPathMIP = mipNameToXPathMIP ++ customMIPNameToXPathMIP

        // Create children binds
        private var _children: Seq[Bind] = Dom4j.elements(element, XFORMS_BIND_QNAME) map (new Bind(_, Bind.this, None))// NOTE: preceding not handled for now
        def children  = _children
        def jChildren = _children.asJava

        def addBind(bindElement: Element, precedingId: Option[String]): Unit =
            _children = _children :+ new Bind(bindElement, Bind.this, None)// NOTE: preceding not handled for now

        def removeBind(bind: Bind): Unit = {
            bindTree.bindIds -= bind.staticId
            bindTree.bindsById -= bind.staticId
            if (bind.name ne null)
                bindsByName -= bind.name

            _children = _children filterNot (_ eq bind)

            part.unmapScopeIds(bind)
        }

        def getMIP(mipName: String) = if (mipName == TYPE) typeMIP else allMIPNameToXPathMIP.get(mipName)

        // For Java callers (can return null)
        def getDefaultValue = getMIPOrNull(Default.name)
        def getCalculate    = getMIPOrNull(Calculate.name)
        def getRelevant     = getMIPOrNull(Relevant.name)
        def getReadonly     = getMIPOrNull(Readonly.name)
        def getRequired     = getMIPOrNull(Required.name)
        def getConstraint   = getMIPOrNull(Constraint.name)
        def getType         = typeMIP map (_.datatype) orNull
        def getCustom(mipName: String) = getMIPOrNull(mipName)

        def getMIPOrNull(mipName: String) = allMIPNameToXPathMIP.get(mipName).orNull

        def hasCalculateComputedMIPs = mipNameToXPathMIP exists (_._2 isCalculateComputedMIP)
        def hasValidateMIPs = typeMIP.isDefined || (mipNameToXPathMIP exists (_._2 isValidateMIP))
        def hasCustomMIPs = customMIPNameToXPathMIP.nonEmpty
        def hasMIPs = hasCalculateComputedMIPs || hasValidateMIPs || hasCustomMIPs

        // Globally remember if we have seen these categories of binds
        bindTree.hasDefaultValueBind ||= getDefaultValue ne null
        bindTree.hasCalculateBind ||= getCalculate ne null
        bindTree.hasTypeBind ||= getType ne null
        bindTree.hasRequiredBind ||= getRequired ne null
        bindTree.hasConstraintBind ||= getConstraint ne null

        bindTree.hasCalculateComputedCustomBind ||= hasCalculateComputedMIPs || hasCustomMIPs
        bindTree.hasValidateBind ||= hasValidateMIPs

        // Compute value analysis if we have a type bound, otherwise don't bother
        override protected def computeValueAnalysis: Option[XPathAnalysis] = typeMIP match {
            case Some(_) if hasBinding ⇒ Some(analyzeXPath(getChildrenContext, "string(.)"))
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
                    for (mip ← allMIPNameToXPathMIP.values)
                        mip.analyzeXPath()
                case None ⇒
            }

            // Analyze children
            _children foreach (_.analyzeMIPs())
        }

        override def freeTransientState() {

            super.freeTransientState()

            for (mip ← allMIPNameToXPathMIP.values)
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
            for ((_, mip) ← allMIPNameToXPathMIP.to[List].sortBy(_._1)) {
                helper.startElement("mip", Array("name", mip.name, "expression", mip.compiledExpression.string))
                mip.analysis.toXML(helper)
                helper.endElement()
            }

            // Children binds
            for (child ← _children)
                child.toXML(helper)
        }
    }

    def analyzeBindsXPath(): Unit = {
        // Analyze all binds and return whether all of them were successfully analyzed
        figuredAllBindRefAnalysis = (topLevelBinds map (_.analyzeXPathGather)).foldLeft(true)(_ && _)

        // Analyze all MIPs
        // NOTE: Do this here, because MIPs can depend on bind/@name, which requires all bind/@ref to be analyzed first
        topLevelBinds foreach (_.analyzeMIPs())

        if (! figuredAllBindRefAnalysis) {
            bindInstances.clear()
            computedBindExpressionsInstances.clear()
            validationBindInstances.clear()
            // keep bindAnalysis as those can be used independently from each other
        }
    }

    def bindsToXML(helper: ContentHandlerHelper): Unit =
        // Output binds information
        if (topLevelBinds.nonEmpty) {
            helper.startElement("binds")
            for (bind ← topLevelBinds)
                bind.toXML(helper)
            helper.endElement()
        }

    def freeBindsTransientState(): Unit =
        for (bind ← topLevelBinds)
            bind.freeTransientState()
}


trait ModelBinds {

    selfModel: Model ⇒

    // FIXME: Unhappy with the complexity of this: we want the tree to be lazily evaluated yet be re-assignable. The
    // laziness is desired because of initialization order issues with the superclass. There has to be a simpler way!
    private class LazyBindTree(bindElements: Seq[Element]) extends (() ⇒ BindTree) {

        import ElementAnalysis.attQNameSet

        private val canBeCustomMIP: QName ⇒ Boolean = qName ⇒
            qName.getNamespacePrefix.nonEmpty &&
            ! qName.getNamespacePrefix.startsWith("xml") &&
            (StandardCustomMIPsQNames(qName) || ! NeverCustomMIPsURIs(qName.getNamespaceURI))

        private def isCustomMIP: QName ⇒ Boolean = Option(selfModel.element.attribute(XXFORMS_CUSTOM_MIPS_QNAME)) match {
            case Some(_) ⇒
                // If the attribute is present, allow all specified QNames if valid, plus standard MIP QNames
                attQNameSet(selfModel.element, XXFORMS_CUSTOM_MIPS_QNAME, namespaceMapping) ++ StandardCustomMIPsQNames filter canBeCustomMIP
            case None    ⇒
                // Attribute not present: backward-compatible behavior
                canBeCustomMIP
        }

        private lazy val result = new BindTree(selfModel, bindElements, isCustomMIP)

        def apply() = result
    }

    private var bindTree = new LazyBindTree(Dom4j.elements(selfModel.element, XFORMS_BIND_QNAME))

    private def annotateSubTree(rawElement: Element) = {
        val (annotatedTree, _) =
            part.xblBindings.annotateSubtree(
                None,
                Dom4jUtils.createDocumentCopyParentNamespaces(rawElement),
                scope,
                scope,
                XXBLScope.inner,
                containerScope,
                hasFullUpdate = false,
                ignoreRoot = false,
                needCompact = false)

        annotatedTree
    }

    def rebuildBinds(rawModelElement: Element): Unit = {

        assert(! selfModel.part.isTopLevel)

        bindTree().destroy()
        bindTree = new LazyBindTree(Dom4j.elements(rawModelElement, XFORMS_BIND_QNAME) map (annotateSubTree(_).getRootElement))
    }

    def bindsById = bindTree().bindsById
    def jBindsByName = bindTree().bindsByName.asJava

    def hasDefaultValueBind = bindTree().hasDefaultValueBind
    def hasCalculateBind = bindTree().hasCalculateBind
    def hasTypeBind = bindTree().hasTypeBind
    def hasRequiredBind = bindTree().hasRequiredBind
    def hasConstraintBind = bindTree().hasConstraintBind

    def hasCalculateComputedCustomBind = bindTree().hasCalculateComputedCustomBind
    def hasValidateBind = bindTree().hasValidateBind

    def bindInstances = bindTree().bindInstances
    def computedBindExpressionsInstances = bindTree().computedBindExpressionsInstances
    def validationBindInstances = bindTree().validationBindInstances

    // TODO: use and produce variables introduced with xf:bind/@name

    def topLevelBinds = bindTree().topLevelBinds
    def topLevelBindsJava = topLevelBinds.asJava

    def hasBinds = bindTree().hasBinds
    def containsBind(bindId: String) = bindTree().bindIds(bindId)

    def figuredAllBindRefAnalysis = bindTree().figuredAllBindRefAnalysis

    def analyzeBindsXPath() = bindTree().analyzeBindsXPath()
    def bindsToXML(helper: ContentHandlerHelper) = bindTree().bindsToXML(helper)
    def freeBindsTransientState() = bindTree().freeBindsTransientState()
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
    case object Default      extends MIP with StringMIP  with ComputedMIP { val name = "default"; override val qName = XXFORMS_DEFAULT_QNAME }
    case object Type         extends MIP with ValidateMIP { val name = "type" }

    case class Custom(override val name: String) extends MIP with XPathMIP

    val AllMIPs                  = Set[MIP](Relevant, Readonly, Required, Constraint, Calculate, Default, Type)
    val AllMIPsByName            = AllMIPs map (mip ⇒ mip.name → mip) toMap
    val AllMIPNames              = AllMIPs map (_.name)
    val MIPNameToAttributeQName  = AllMIPs map (m ⇒ m.name → m.qName) toMap

    val QNameToXPathComputedMIP  = AllMIPs collect { case m: XPathMIP with ComputedMIP ⇒ m.qName → m } toMap
    val QNameToXPathValidateMIP  = AllMIPs collect { case m: XPathMIP with ValidateMIP ⇒ m.qName → m } toMap
    val QNameToXPathMIP          = QNameToXPathComputedMIP ++ QNameToXPathValidateMIP

    val CalculateMIPNames        = AllMIPs collect { case m: ComputedMIP ⇒ m.name }
    val ValidateMIPNames         = AllMIPs collect { case m: ValidateMIP ⇒ m.name }
    val BooleanXPathMIPNames     = AllMIPs collect { case m: XPathMIP with BooleanMIP ⇒ m.name }
    val StringXPathMIPNames      = AllMIPs collect { case m: XPathMIP with StringMIP ⇒ m.name }

    val StandardCustomMIPsQNames = Set(XXFORMS_EVENT_MODE_QNAME)
    val NeverCustomMIPsURIs      = Set(XFORMS_NAMESPACE_URI, XXFORMS_NAMESPACE_URI)

    def buildCustomMIPName(qualifiedName: String) = qualifiedName.replace(':', '-')

    // Constants for Java callers
    val RELEVANT   = Relevant.name
    val READONLY   = Readonly.name
    val REQUIRED   = Required.name
    val CONSTRAINT = Constraint.name
    val CALCULATE  = Calculate.name
    val DEFAULT    = Default.name
    val TYPE       = Type.name

    // MIP default values for Java callers
    val DEFAULT_RELEVANT = true
    val DEFAULT_READONLY = false
    val DEFAULT_REQUIRED = false
    val DEFAULT_VALID    = true

    val XFormsSchemaTypeNames = Set(
        "dayTimeDuration",
        "yearMonthDuration",
        "email",
        "card-number"
    )

    val jXFormsSchemaTypeNames = XFormsSchemaTypeNames.asJava

    val XFormsVariationTypeNames = Set(
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
        "QName",

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

    val jXFormsVariationTypeNames = XFormsVariationTypeNames.asJava
}

