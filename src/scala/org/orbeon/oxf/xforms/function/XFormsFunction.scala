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
import org.dom4j.Element
import org.dom4j.Namespace
import org.dom4j.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.PooledXPathExpression
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsContextStack
import org.orbeon.oxf.xforms.XFormsModel
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.saxon.expr._
import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.sxpath.IndependentContext
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.saxon.value.QNameValue
import scala.collection.JavaConverters._

/**
 * Base class for all XForms functions.
 *
 * TODO: context should contain PropertyContext directly
 * TODO: context should contain BindingContext directly if any
 */
abstract class XFormsFunction extends SystemFunction {

    // Public accessor for Scala traits
    def arguments: Array[Expression] = argument

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     *
     * NOTE: A few functions would benefit from not having this, but it is always safe.
     */
    override def preEvaluate(visitor: ExpressionVisitor) = this

    def context(implicit xpathContext: XPathContext) =
        PooledXPathExpression.getFunctionContext(xpathContext).asInstanceOf[XFormsFunction.Context]

    def getControls(implicit xpathContext: XPathContext)      = context.getControls
    def getXBLContainer(implicit xpathContext: XPathContext)  = context.getXBLContainer
    def getSourceElement(implicit xpathContext: XPathContext) = context.getSourceElement
    def getModel(implicit xpathContext: XPathContext)         = context.getModel
    def getContextStack(implicit xpathContext: XPathContext)  = context.getContextStack

    def getSourceEffectiveId(implicit xpathContext: XPathContext) =
        context.getSourceEffectiveId ensuring (_ ne null, "Source effective id not available for resolution.")

    def getContainingDocument(implicit xpathContext: XPathContext) =
        Option(context) map (_.getXBLContainer.getContainingDocument) orNull

    protected def getQNameFromExpression(xpathContext: XPathContext, qNameExpression: Expression): QName = {

        def createQName(qNameString: String, qNameURI: String, colonIndex: Int, prefix: String) =
            if (colonIndex == -1)
                // NCName (this assumes that if there is no prefix, the QName is in no namespace)
                new QName(qNameString)
            else
                // QName-but-not-NCName
                new QName(qNameString.substring(colonIndex + 1), new Namespace(prefix, qNameURI))

        Option(qNameExpression) map (_.evaluateItem(xpathContext)) match {
            case Some(qName: QNameValue) ⇒
                // Directly got a QName
                val qNameString = qName.getStringValue
                val qNameURI = qName.getNamespaceURI
                val colonIndex = qNameString.indexOf(':')
                val prefix = qNameString.substring(0, colonIndex)

                createQName(qNameString, qNameURI, colonIndex, prefix)
            case Some(qName: AtomicValue) ⇒
                val qNameString = qName.getStringValue
                val colonIndex = qNameString.indexOf(':')
                if (colonIndex == -1)
                    // NCName
                    createQName(qNameString, null, colonIndex, null)
                else {
                    // QName-but-not-NCName
                    val prefix = qNameString.substring(0, colonIndex)
                    val contextStack = getContextStack(xpathContext)
                    val namespaceMapping = getXBLContainer(xpathContext).getNamespaceMappings(contextStack.getCurrentBindingContext.getControlElement)

                    // Get QName URI
                    val qNameURI = namespaceMapping.mapping.get(prefix)
                    if (qNameURI eq null)
                        throw new OXFException("Namespace prefix not in space for QName: " + qNameString)
                    createQName(qNameString, qNameURI, colonIndex, prefix)
                }
            case _ ⇒ null
        }
    }

    // The following is inspired by saxon:evaluate()
    protected def prepareExpression(initialXPathContext: XPathContext, parameterExpression: Expression, isAVT: Boolean): PooledXPathExpression = {

        // Evaluate parameter into an XPath string
        val xpathString = (parameterExpression.evaluateItem(initialXPathContext).asInstanceOf[AtomicValue]).getStringValue

        // Copy static context information
        val staticContext = this.staticContext.copy
        staticContext.setFunctionLibrary(initialXPathContext.getController.getExecutable.getFunctionLibrary)

        // Propagate in-scope variable definitions since they are not copied automatically
        val contextStack = getContextStack(initialXPathContext)

        val inScopeVariables = contextStack.getCurrentBindingContext.getInScopeVariables
        val variableDeclarations =
            for {
                (name, _) ← inScopeVariables.asScala
                variable = staticContext.declareVariable("", name)
            } yield
                name → variable

        // Create expression
        val pooledXPathExpression =
            XPathCache.createPoolableXPathExpression(null, staticContext, xpathString, variableDeclarations.asJava, isAVT)

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
            } staticContext.declareNamespace(prefix, uri)
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

    // NOTE: This is always constructed in XFormsContextStack
    // Constructor for XBLContainer and XFormsActionInterpreter
    class Context(val container: XBLContainer, val contextStack: XFormsContextStack) extends XPathCache.FunctionContext {

        // Constructor for XFormsModel
        def this(containingModel: XFormsModel, contextStack: XFormsContextStack) =
            this(containingModel.container, contextStack)

        private var sourceEffectiveId: String = _
        private var sourceElement: Element = _
        private var model: XFormsModel = _

        def getXBLContainer = container
        def getContextStack = contextStack

        def getContainingDocument = container.getContainingDocument
        def getControls = getContainingDocument.getControls

        def getModel = model
        def setModel(model: XFormsModel) = this.model = model

        def getSourceEffectiveId = sourceEffectiveId
        def setSourceEffectiveId(sourceEffectiveId: String) = this.sourceEffectiveId = sourceEffectiveId

        def getSourceElement = sourceElement
        def setSourceElement(sourceElement: Element) = this.sourceElement = sourceElement
    }
}