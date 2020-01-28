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

import java.util.{Locale, Iterator => JIterator}

import org.orbeon.dom
import org.orbeon.dom.Namespace
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.{PooledXPathExpression, XPath, XPathCache}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, SimpleElementAnalysis}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.function.xxforms.XXFormsLang
import org.orbeon.oxf.xforms.model.{BindNode, XFormsModel}
import org.orbeon.oxf.xforms.xbl.{Scope, XBLContainer}
import org.orbeon.oxf.xml.{DefaultFunctionSupport, SaxonUtils}
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.`type`.AtomicType
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.expr._
import org.orbeon.saxon.sxpath.IndependentContext
import org.orbeon.saxon.value.{AtomicValue, QNameValue}
import org.orbeon.xforms.XFormsId

import scala.collection.JavaConverters._
import scala.collection.{mutable => m}

/**
 * Base class for all XForms functions.
 *
 * TODO: context should contain PropertyContext directly
 * TODO: context should contain BindingContext directly if any
 */
abstract class XFormsFunction extends DefaultFunctionSupport {

  import XFormsFunction._

  // Resolve the relevant control by argument expression
  // TODO: Check callers and consider using `relevantControls`.
  def relevantControl(i: Int)(implicit xpathContext: XPathContext): Option[XFormsControl] =
    findRelevantControls(arguments(i).evaluateAsString(xpathContext).toString, followIndexes = true).headOption

  def relevantControls(
    i             : Int,
    followIndexes : Boolean)(implicit
    xpathContext  : XPathContext
  ): List[XFormsControl] =
    findRelevantControls(
      arguments(i).evaluateAsString(xpathContext).toString,
      followIndexes
    )

  // Resolve a relevant control by id
  def findRelevantControls(
    staticOrAbsoluteId : String,
    followIndexes      : Boolean)(implicit
    xpathContext       : XPathContext
  ): List[XFormsControl] =
    findControlsByStaticOrAbsoluteId(staticOrAbsoluteId, followIndexes) collect
      { case control: XFormsControl if control.isRelevant => control }

  def findControls(i: Int, followIndexes: Boolean)(implicit xpathContext: XPathContext): List[XFormsControl] =
    findControlsByStaticOrAbsoluteId(arguments(i).evaluateAsString(xpathContext).toString, followIndexes)

  // Resolve a control by id
  def findControlsByStaticOrAbsoluteId(
    staticOrAbsoluteId : String,
    followIndexes      : Boolean)(implicit
    xpathContext       : XPathContext
  ): List[XFormsControl] = {

    val sourceEffectiveId        = getSourceEffectiveId
    val sourcePrefixedId         = XFormsId.getPrefixedId(sourceEffectiveId)
    val resolutionScopeContainer = context.container.findScopeRoot(sourcePrefixedId)

    resolutionScopeContainer.resolveObjectsById(sourceEffectiveId, staticOrAbsoluteId, contextItemOpt = None, followIndexes) collect { case c: XFormsControl => c }
  }

  // Resolve an object by id
  def resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId: String)(implicit xpathContext: XPathContext): Option[XFormsObject] =
    context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticOrAbsoluteId)

  def resolveStaticOrAbsoluteId(staticIdExpr: Option[Expression])(implicit xpathContext: XPathContext): Option[String] =
    staticIdExpr match {
      case None =>
        // If no argument is supplied, return the closest id (source id)
        Option(getSourceEffectiveId)
      case Some(expr) =>
        // Otherwise resolve the id passed against the source id
        val staticOrAbsoluteId = expr.evaluateAsString(xpathContext).toString
        resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId) map
          (_.getEffectiveId)
    }

  def bindingContext: BindingContext = context.bindingContext

  def getSourceEffectiveId: String =
    context.sourceEffectiveId ensuring (_ ne null, "Source effective id not available for resolution.")

  def elementAnalysisForSource: Option[ElementAnalysis] = {
    val prefixedId = XFormsId.getPrefixedId(getSourceEffectiveId)
    context.container.partAnalysis.findControlAnalysis(prefixedId)
  }

  def elementAnalysisForStaticId(staticId: String)(implicit xpathContext: XPathContext): Option[ElementAnalysis] = {
    val prefixedId = sourceScope.prefixedIdForStaticId(staticId)
    context.container.partAnalysis.findControlAnalysis(prefixedId)
  }

  def sourceScope(implicit xpathContext: XPathContext): Scope =
    context.container.partAnalysis.scopeForPrefixedId(XFormsId.getPrefixedId(getSourceEffectiveId))

  def getContainingDocument(implicit xpathContext: XPathContext): XFormsContainingDocument =
    Option(context) map (_.container.getContainingDocument) orNull

  def setProperty(name: String, value: Option[String]): Unit =
    context.setProperty(name, value)

  def currentLangOpt(implicit xpathContext: XPathContext): Option[String] =
    elementAnalysisForSource flatMap (XXFormsLang.resolveXMLangHandleAVTs(getContainingDocument, _))

  def currentLocale(implicit xpathContext: XPathContext): Locale =
    currentLangOpt match {
      case Some(lang) =>
        // Not sure how xml:lang should be parsed, see:
        //
        // XML spec points to:
        //
        // - http://tools.ietf.org/html/rfc4646
        // - http://tools.ietf.org/html/rfc4647
        //
        // NOTES:
        //
        // - IETF BCP 47 replaces RFC 4646 (and includes RFC 5646 and RFC 4647)
        // - Java 7 has an improved Locale class which supports parsing BCP 47
        //
        // http://docs.oracle.com/javase/7/docs/api/java/util/Locale.html#forLanguageTag(java.lang.String)
        // http://www.w3.org/International/articles/language-tags/
        // http://sites.google.com/site/openjdklocale/design-specification
        // IETF BCP 47: http://www.rfc-editor.org/rfc/bcp/bcp47.txt

        // Use Saxon utility for now
        Configuration.getLocale(lang)
      case None =>
        Locale.getDefault(Locale.Category.FORMAT) // NOTE: Using defaults is usually bad.
  }

  protected def getQNameFromExpression(qNameExpression: Expression)(implicit xpathContext: XPathContext): dom.QName = {

    val evaluatedExpression =
      qNameExpression.evaluateItem(xpathContext)

    evaluatedExpression match {
      case qName: QNameValue =>
        // Directly got a QName so there is no need for namespace resolution
        qNameFromQNameValue(qName)
      case atomic: AtomicValue =>
        // Must resolve prefix if present
        qNameFromStringValue(atomic.getStringValue, bindingContext)
      case other =>
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
        (name, _) <- inScopeVariables.asScala.toList
        variable = staticContext.declareVariable("", name)
      } yield
        name -> variable

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
  def copyStaticContextIfNeeded(visitor: ExpressionVisitor): Unit = {
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
        prefix <- namespaceResolver.iteratePrefixes.asInstanceOf[JIterator[String]].asScala
        if prefix.nonEmpty
        uri = namespaceResolver.getURIForPrefix(prefix, true)
      } locally {
        staticContext.declareNamespace(prefix, uri)
      }
    }
  }

  // Default implementation which adds child expressions (here function arguments) to the pathmap
  protected def addSubExpressionsToPathMap(
    pathMap        : PathMap,
    pathMapNodeSet : PathMapNodeSet
  ): PathMapNodeSet  = {

    val attachmentPoint = pathMapAttachmentPoint(pathMap, pathMapNodeSet)

    val result = new PathMapNodeSet
    iterateSubExpressions.asScala.asInstanceOf[Iterator[Expression]] foreach { child =>
      result.addNodeSet(child.addToPathMap(pathMap, attachmentPoint))
    }

    val th = getExecutable.getConfiguration.getTypeHierarchy
    if (getItemType(th).isInstanceOf[AtomicType])
      null
    else
      result
  }

  protected def pathMapAttachmentPoint(
    pathMap        : PathMap,
    pathMapNodeSet : PathMapNodeSet
  ): PathMapNodeSet  =
    if ((getDependencies & StaticProperty.DEPENDS_ON_FOCUS) != 0) {
      Option(pathMapNodeSet) getOrElse {
        val cie = new ContextItemExpression
        cie.setContainer(getContainer)
        new PathMapNodeSet(pathMap.makeNewRoot(cie))
      }
    } else {
      null
    }
}

object XFormsFunction {

  case class Context(
    container         : XBLContainer,
    bindingContext    : BindingContext,
    sourceEffectiveId : String,
    modelOpt          : Option[XFormsModel],
    data              : Any
  ) extends XPath.FunctionContext {

    def containingDocument = container.containingDocument

    private var _properties: Option[m.Map[String, Option[String]]] = None
    def properties = _properties

    def setProperty(name: String, value: Option[String]) = {
      if (_properties.isEmpty)
        _properties = Some(m.Map.empty[String, Option[String]])
      _properties foreach (_ += name -> value)
    }
  }

  def sourceElementAnalysis(pathMap: PathMap): SimpleElementAnalysis =
    pathMap.getPathMapContext match {
      case context: SimpleElementAnalysis#SimplePathMapContext => context.element
      case _ => throw new IllegalStateException("Can't process PathMap because context is not of expected type.")
    }

  def context: Context =
    XPath.functionContext map (_.asInstanceOf[XFormsFunction.Context]) orNull

  // This ADT or something like it should be defined somewhere else
  sealed trait QNameType
  case   class UnprefixedName(local: String) extends QNameType
  case   class PrefixedName(prefix: String, local: String) extends QNameType

  def parseQNameToQNameType(lexicalQName: String): QNameType =
    SaxonUtils.parseQName(lexicalQName) match {
      case ("", local)     => UnprefixedName(local)
      case (prefix, local) => PrefixedName(prefix, local)
    }

  def qNameFromQNameValue(value: QNameValue): dom.QName =
    parseQNameToQNameType(value.getStringValue) match {
      case PrefixedName(prefix, local) => dom.QName(local, Namespace(prefix, value.getNamespaceURI))
      case UnprefixedName(local)       => dom.QName(local)
    }

  def qNameFromStringValue(value: String, bindingContext: BindingContext): dom.QName =
    parseQNameToQNameType(value) match {
      case PrefixedName(prefix, local) =>

        def prefixNotInScope() =
          throw new OXFException(s"Namespace prefix not in scope for QName `$value`")

        val namespaceMapping = context.data match {
          case Some(bindNode: BindNode) =>
            // Function was called from a bind
            bindNode.parentBind.staticBind.namespaceMapping
          case _ if bindingContext.controlElement ne null =>
            // Function was called from a control
            // `controlElement` is mainly used in `BindingContext` to handle repeats and context.
            context.container.getNamespaceMappings(bindingContext.controlElement)
          case _ =>
            // Unclear which cases reach here!
            // TODO: The context should simply have an `ElementAnalysis` or a `NamespaceMapping`.
            prefixNotInScope()
        }

        val qNameURI = namespaceMapping.mapping.getOrElse(prefix, prefixNotInScope())

        dom.QName(local, Namespace(prefix, qNameURI))
      case UnprefixedName(local) =>
        dom.QName(local)
    }
}