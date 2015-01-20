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
package org.orbeon.oxf.xforms.function

import java.util.{Iterator ⇒ JIterator}

import org.dom4j.{Namespace, QName ⇒ Dom4jQName}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.{PooledXPathExpression, XPath, XPathCache}
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis
import org.orbeon.oxf.xforms.model.BindNode
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{BindingContext, XFormsModel, XFormsUtils}
import org.orbeon.saxon.expr._
import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.sxpath.IndependentContext
import org.orbeon.saxon.value.{AtomicValue, QNameValue}
import org.orbeon.scaxon.XML

import scala.collection.JavaConverters._

/**
 * Base class for all XForms functions.
 *
 * TODO: context should contain PropertyContext directly
 * TODO: context should contain BindingContext directly if any
 */
abstract class XFormsFunction extends SystemFunction {

    import XFormsFunction._

    // Public accessor for Scala traits
    def arguments: Seq[Expression] = argument
    def functionOperation: Int = operation

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     *
     * NOTE: A few functions would benefit from not having this, but it is always safe.
     */
    override def preEvaluate(visitor: ExpressionVisitor) = this

    def bindingContext = context.bindingContext

    def getSourceEffectiveId =
        context.sourceEffectiveId ensuring (_ ne null, "Source effective id not available for resolution.")

    def elementAnalysisForSource = {
        val prefixedId = XFormsUtils.getPrefixedId(getSourceEffectiveId)
        context.container.partAnalysis.getControlAnalysisOption(prefixedId)
    }

    def elementAnalysisForStaticId(staticId: String)(implicit xpathContext: XPathContext) = {
        val prefixedId = sourceScope.prefixedIdForStaticId(staticId)
        context.container.partAnalysis.getControlAnalysisOption(prefixedId)
    }

    def sourceScope(implicit xpathContext: XPathContext) =
        context.container.partAnalysis.scopeForPrefixedId(XFormsUtils.getPrefixedId(getSourceEffectiveId))

    def getContainingDocument(implicit xpathContext: XPathContext) =
        Option(context) map (_.container.getContainingDocument) orNull

    protected def getQNameFromExpression(xpathContext: XPathContext, qNameExpression: Expression): Dom4jQName = {

        val evaluatedExpression =
            qNameExpression.evaluateItem(xpathContext)

        evaluatedExpression match {
            case qName: QNameValue ⇒
                // Directly got a QName so there is no need for namespace resolution
                qNameFromQNameValue(qName)
            case atomic: AtomicValue ⇒
                // Must resolve prefix if present
                qNameFromStringValue(atomic.getStringValue, bindingContext)
            case other ⇒
                throw new OXFException(s"Cannot create QName from non-atomic item of class '${other.getClass.getName}'")
        }
    }

    // The following is inspired by saxon:evaluate()
    protected def prepareExpression(initialXPathContext: XPathContext, parameterExpression: Expression, isAVT: Boolean): PooledXPathExpression = {

        // Evaluate parameter into an XPath string
        val xpathString = parameterExpression.evaluateItem(initialXPathContext).asInstanceOf[AtomicValue].getStringValue

        // Copy static context information
        val staticContext = this.staticContext.copy
        staticContext.setFunctionLibrary(initialXPathContext.getController.getExecutable.getFunctionLibrary)

        // Propagate in-scope variable definitions since they are not copied automatically
        val inScopeVariables = bindingContext.getInScopeVariables
        val variableDeclarations =
            for {
                (name, _) ← inScopeVariables.asScala.toList
                variable = staticContext.declareVariable("", name)
            } yield
                name → variable

        // Create expression
        val pooledXPathExpression =
            XPathCache.createPoolableXPathExpression(staticContext, xpathString, isAVT, null, variableDeclarations)

        // Set context items and position for use at runtime
        pooledXPathExpression.setContextItem(initialXPathContext.getContextItem, initialXPathContext.getContextPosition)

        // Set variables for use at runtime
        pooledXPathExpression.setVariables(inScopeVariables)

        pooledXPathExpression
    }

    // See comments in Saxon Evaluate.java
    private var staticContext: IndependentContext = null

    // The following copies all the StaticContext information into a new StaticContext
    def copyStaticContextIfNeeded(visitor: ExpressionVisitor) {
        // See same method in Saxon Evaluate.java
        if (staticContext eq null) { // only do this once
            val env = visitor.getStaticContext
            super.checkArguments(visitor)

            val namespaceResolver = env.getNamespaceResolver

            staticContext = new IndependentContext(env.getConfiguration)

            staticContext.setBaseURI(env.getBaseURI)
            staticContext.setImportedSchemaNamespaces(env.getImportedSchemaNamespaces)
            staticContext.setDefaultFunctionNamespace(env.getDefaultFunctionNamespace)
            staticContext.setDefaultElementNamespace(env.getDefaultElementNamespace)
            staticContext.setFunctionLibrary(env.getFunctionLibrary)

            for {
                prefix ← namespaceResolver.iteratePrefixes.asInstanceOf[JIterator[String]].asScala
                if prefix.nonEmpty
                uri = namespaceResolver.getURIForPrefix(prefix, true)
            } locally {
                staticContext.declareNamespace(prefix, uri)
            }
        }
    }

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
        // By default, all XForms function invalidate the map. Subclasses can override this behavior. This ensures that
        // we do not, by default, produce invalid maps.
        pathMap.setInvalidated(true)
        null
    }

    // Access to Saxon's default implementation
    protected def saxonAddToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet) =
        super.addToPathMap(pathMap, pathMapNodeSet)
}

object XFormsFunction {

    case class Context(
        container         : XBLContainer,
        bindingContext    : BindingContext,
        sourceEffectiveId : String,
        model             : XFormsModel,
        data              : Any
    ) extends XPath.FunctionContext {
        def containingDocument = container.containingDocument
    }

    def sourceElementAnalysis(pathMap: PathMap) =
        pathMap.getPathMapContext match {
            case context: SimpleElementAnalysis#SimplePathMapContext ⇒ context.element
            case _ ⇒ throw new IllegalStateException("Can't process PathMap because context is not of expected type.")
        }

    def context =
        XPath.functionContext map (_.asInstanceOf[XFormsFunction.Context]) orNull

    // This ADT or something like it should be defined somewhere else
    sealed trait QNameType
    case   class UnprefixedName(local: String) extends QNameType
    case   class PrefixedName(prefix: String, local: String) extends QNameType

    def parseQName(lexicalQName: String): QNameType =
        XML.parseQName(lexicalQName) match {
            case ("", local)     ⇒ UnprefixedName(local)
            case (prefix, local) ⇒ PrefixedName(prefix, local)
        }

    def qNameFromQNameValue(value: QNameValue): Dom4jQName =
        parseQName(value.getStringValue) match {
            case PrefixedName(prefix, local) ⇒ new Dom4jQName(local, new Namespace(prefix, value.getNamespaceURI))
            case UnprefixedName(local)       ⇒ new Dom4jQName(local)
        }

    def qNameFromStringValue(value: String, bindingContext: BindingContext): Dom4jQName =
        parseQName(value) match {
            case PrefixedName(prefix, local) ⇒

                def prefixNotInScope() =
                    throw new OXFException(s"Namespace prefix not in scope for QName `$value`")

                val namespaceMapping = context.data match {
                    case Some(bindNode: BindNode) ⇒
                        // Function was called from a bind
                        bindNode.parentBind.staticBind.namespaceMapping
                    case _ if bindingContext.controlElement ne null ⇒
                        // Function was called from a control
                        // `controlElement` is mainly used in `BindingContext` to handle repeats and context.
                        context.container.getNamespaceMappings(bindingContext.controlElement)
                    case _ ⇒
                        // Unclear which cases reach here!
                        // TODO: The context should simply have an `ElementAnalysis` or a `NamespaceMapping`.
                        prefixNotInScope()
                }

                val qNameURI = namespaceMapping.mapping.get(prefix)
                if (qNameURI eq null)
                    prefixNotInScope()

                new Dom4jQName(local, new Namespace(prefix, qNameURI))
            case UnprefixedName(local) ⇒
                new Dom4jQName(local)
        }
}