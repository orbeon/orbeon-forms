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

import org.orbeon.dom
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.function.xxforms.XXFormsLang
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om
import org.orbeon.saxon.sxpath.IndependentContext
import org.orbeon.saxon.value.{AtomicValue, QNameValue}
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.runtime.XFormsObject
import org.orbeon.xforms.xbl.Scope

import java.util.Locale


/**
 * Base class for all XForms functions.
 *
 * TODO: context should contain PropertyContext directly
 * TODO: context should contain BindingContext directly if any
 */
object XFormsFunction extends CommonFunctionSupport {

  // Resolve the relevant control by argument expression
  // TODO: Check callers and consider using `relevantControls`.
  def relevantControl(
    staticOrAbsoluteId : String)(implicit
    xpc                : XPathContext,
    xfc                : XFormsFunction.Context
  ): Option[XFormsControl] =
    findRelevantControls(staticOrAbsoluteId, followIndexes = true).headOption

  // Resolve a relevant control by id
  def findRelevantControls(
    staticOrAbsoluteId : String,
    followIndexes      : Boolean)(implicit
    xpc                : XPathContext,
    xfc                : XFormsFunction.Context
  ): List[XFormsControl] =
    findControlsByStaticOrAbsoluteId(staticOrAbsoluteId, followIndexes) collect
      { case control: XFormsControl if control.isRelevant => control }

  // Resolve a control by id
  def findControlsByStaticOrAbsoluteId(
    staticOrAbsoluteId : String,
    followIndexes      : Boolean)(implicit
    xpc                : XPathContext,
    xfc                : XFormsFunction.Context
  ): List[XFormsControl] = {

    val sourceEffectiveId        = getSourceEffectiveId
    val sourcePrefixedId         = XFormsId.getPrefixedId(sourceEffectiveId)
    val resolutionScopeContainer = context.container.findScopeRoot(sourcePrefixedId)

    resolutionScopeContainer.resolveObjectsById(
      sourceEffectiveId,
      staticOrAbsoluteId,
      contextItemOpt = None,
      followIndexes
    ) collect {
      case c: XFormsControl => c
    }
  }

  // Resolve an object by id
  def resolveOrFindByStaticOrAbsoluteId(
    staticOrAbsoluteId : String)(implicit
    xpc                : XPathContext,
    xfc                : XFormsFunction.Context
  ): Option[XFormsObject] =
    context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticOrAbsoluteId)

  def resolveStaticOrAbsoluteId(
    staticIdExpr : Option[Expression])(implicit
    xpc          : XPathContext,
    xfc          : XFormsFunction.Context
  ): Option[String] =
    staticIdExpr match {
      case None =>
        // If no argument is supplied, return the closest id (source id)
        Option(getSourceEffectiveId)
      case Some(expr) =>
        // Otherwise resolve the id passed against the source id
        val staticOrAbsoluteId = expr.evaluateAsString(xpc).toString
        resolveOrFindByStaticOrAbsoluteId(staticOrAbsoluteId) map
          (_.getEffectiveId)
    }

  def bindingContext: BindingContext = context.bindingContext

  def getSourceEffectiveId(implicit xfc: XFormsFunction.Context): String =
    xfc.sourceEffectiveId ensuring (_ ne null, "Source effective id not available for resolution.")

  def elementAnalysisForSource(implicit xfc: XFormsFunction.Context): Option[ElementAnalysis] = {
    val prefixedId = XFormsId.getPrefixedId(getSourceEffectiveId)
    xfc.container.partAnalysis.findControlAnalysis(prefixedId)
  }

  def sourceScope(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Scope =
    xfc.container.partAnalysis.scopeForPrefixedId(XFormsId.getPrefixedId(getSourceEffectiveId))

  def getContainingDocument(implicit xpc: XPathContext): XFormsContainingDocument =
    Option(context) map (_.container.getContainingDocument) orNull

  def setProperty(name: String, value: Option[String]): Unit =
    context.setProperty(name, value)

  def currentLangOpt(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    elementAnalysisForSource flatMap (XXFormsLang.resolveXMLangHandleAVTs(getContainingDocument, _))

  def currentLocale(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Locale =
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

        def getLocale(lang: String) = {
          val hyphen = lang.indexOf("-")
          val (language, country) =
            if (hyphen < 1)
              (lang, "")
            else
              (lang.substring(1, hyphen), lang.substring(hyphen + 1))
          new Locale(language, country)
        }

        getLocale(lang)
      case None =>
        Locale.getDefault(Locale.Category.FORMAT) // NOTE: Using defaults is usually bad.
  }

  // TODO: Saxon 10
//  def currentLangOpt(implicit xpathContext: XPathContext): Option[String] =
//    elementAnalysisForSource flatMap (XXFormsLang.resolveXMLangHandleAVTs(getContainingDocument, _))

  // TODO: Saxon 10
//  def currentLocale(implicit xpathContext: XPathContext): Locale =
//    currentLangOpt match {
//      case Some(lang) =>
//        // Not sure how xml:lang should be parsed, see:
//        //
//        // XML spec points to:
//        //
//        // - http://tools.ietf.org/html/rfc4646
//        // - http://tools.ietf.org/html/rfc4647
//        //
//        // NOTES:
//        //
//        // - IETF BCP 47 replaces RFC 4646 (and includes RFC 5646 and RFC 4647)
//        // - Java 7 has an improved Locale class which supports parsing BCP 47
//        //
//        // http://docs.oracle.com/javase/7/docs/api/java/util/Locale.html#forLanguageTag(java.lang.String)
//        // http://www.w3.org/International/articles/language-tags/
//        // http://sites.google.com/site/openjdklocale/design-specification
//        // IETF BCP 47: http://www.rfc-editor.org/rfc/bcp/bcp47.txt
//
//        // Use Saxon utility for now
//        Configuration.getLocale(lang)
//      case None =>
//        Locale.getDefault(Locale.Category.FORMAT) // NOTE: Using defaults is usually bad.
//  }

//  def getQNameFromExpression(qNameExpression: Expression)(implicit xpathContext: XPathContext): dom.QName =
//    getQNameFromItem(qNameExpression.evaluateItem(xpathContext))

  def getQNameFromItem(evaluatedExpression: om.Item)(implicit xpathContext: XPathContext): dom.QName =
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

  // See comments in Saxon Evaluate.java
  private var staticContext: IndependentContext = null

  // TODO: Saxon 10
  // Default implementation which adds child expressions (here function arguments) to the pathmap
//  protected def addSubExpressionsToPathMap(
//    pathMap        : PathMap,
//    pathMapNodeSet : PathMapNodeSet
//  ): PathMapNodeSet  = {
//
//    val attachmentPoint = pathMapAttachmentPoint(pathMap, pathMapNodeSet)
//
//    val result = new PathMapNodeSet
//    iterateSubExpressions.asScala.asInstanceOf[Iterator[Expression]] foreach { child =>
//      result.addNodeSet(child.addToPathMap(pathMap, attachmentPoint))
//    }
//
//    val th = getExecutable.getConfiguration.getTypeHierarchy
//    if (getItemType(th).isInstanceOf[AtomicType])
//      null
//    else
//      result
//  }

//  protected def pathMapAttachmentPoint(
//    pathMap        : PathMap,
//    pathMapNodeSet : PathMapNodeSet
//  ): PathMapNodeSet  =
//    if ((getDependencies & StaticProperty.DEPENDS_ON_FOCUS) != 0) {
//      Option(pathMapNodeSet) getOrElse {
//        val cie = new ContextItemExpression
//        cie.setContainer(getContainer)
//        new PathMapNodeSet(pathMap.makeNewRoot(cie))
//      }
//    } else {
//      null
//    }
}