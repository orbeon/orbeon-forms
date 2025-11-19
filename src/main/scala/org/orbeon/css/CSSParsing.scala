/**
 * Copyright (C) 2025 Orbeon, Inc.
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
package org.orbeon.css

import com.helger.css.ECSSVersion
import com.helger.css.decl.*
import com.helger.css.decl.visit.{CSSVisitor, DefaultCSSVisitor}
import com.helger.css.reader.{CSSReader, CSSReaderSettings}
import com.helger.css.writer.{CSSWriter, CSSWriterSettings}
import org.orbeon.io.IOUtils
import org.orbeon.oxf.util.ContentTypes.CssContentType
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.util.{IndentedLogger, StringUtils}
import org.w3c.dom.{Document, Element, Node}

import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala


object CSSParsing {

  private def parsedCss(css: String): Option[CascadingStyleSheet] = {
    // bBrowserCompliantMode = true for lenient parsing
    val settings = (new CSSReaderSettings).setCSSVersion(ECSSVersion.CSS30).setBrowserCompliantMode(true)

    Option(CSSReader.readFromStringReader(css, settings))
  }

  def variableDefinitions(
    resources         : List[CSSResource],
    inputStreamFromURI: URI => InputStream
  )(implicit
    indentedLogger    : IndentedLogger
  ): VariableDefinitions =
    resources.foldLeft(VariableDefinitions(Nil)) { case (previousVariableDefinitions, resource) =>
      // Merge variables definitions from all resources
      previousVariableDefinitions.merged(variableDefinitions(resource, inputStreamFromURI))
    }

  def variableDefinitions(
    resource          : CSSResource,
    inputStreamFromURI: URI => InputStream
  )(implicit
    indentedLogger    : IndentedLogger
  ): VariableDefinitions = {
    val (css, cssSource) = resource match {
      case Style(css, _) =>
        (css, s"Inline CSS: ${StringUtils.truncateWithEllipsis(css, 20, 1)}")

      case Link (uri, _) =>
        (IOUtils.readStreamAsStringAndClose(inputStreamFromURI(uri), charset = None), s"URI: ${uri.toString}")
    }

    val mediaQueries = resource.mediaQueries

    variableDefinitions(css, mediaQueries, cssSource)
  }

  def variableDefinitions(
    css           : String,
    mediaQueries  : List[MediaQuery],
    cssSource     : String
  )(implicit
    indentedLogger: IndentedLogger
  ): VariableDefinitions =
    parsedCss(css) match {
      case None =>
        error(s"Could not parse CSS for variable definition parsing, source: $cssSource")
        VariableDefinitions(Nil)

      case Some(css) =>
        val variableVisitor = new VariableVisitor(mediaQueries)
        CSSVisitor.visitCSS(css, variableVisitor)
        VariableDefinitions(variableVisitor.variableBuffer.toList)
    }

  private class VariableVisitor(mediaQueries: List[MediaQuery]) extends DefaultCSSVisitor {
    val variableBuffer: mutable.ArrayBuffer[VariableDefinition] = mutable.ArrayBuffer[VariableDefinition]()

    private val mediaQueriesStack = mutable.ArrayDeque[List[MediaQuery]]()
    private val selectorStack     = mutable.ArrayDeque[List[Selector]]()

    override def onDeclaration(aDeclaration: CSSDeclaration): Unit =
      variableBuffer.append(
        VariableDefinition(
          name         = aDeclaration.getProperty,
          value        = aDeclaration.getExpression.getAsCSSString,
          // Combine media query lists using AND operator
          mediaQueries = mediaQueriesStack.toList.foldLeft(mediaQueries)((left, right) => MediaQuery.and(left, right)),
          // Keep top selectors only (not supporting nested selectors)
          selectors    = selectorStack.lastOption.getOrElse(Nil)
        )
      )

    override def onBeginStyleRule(aStyleRule: CSSStyleRule): Unit =
      selectorStack.append(aStyleRule.getAllSelectors.asScala.toList.map(s => Selector(s.getAsCSSString)))

    override def onEndStyleRule(aStyleRule: CSSStyleRule): Unit =
      selectorStack.removeLast()

    override def onBeginMediaRule(aMediaRule: CSSMediaRule): Unit =
      mediaQueriesStack.append(aMediaRule.getAllMediaQueries.asScala.toList.map(mq => MediaQuery(mq.getAsCSSString)))

    override def onEndMediaRule(aMediaRule: CSSMediaRule): Unit =
      mediaQueriesStack.removeLast()
  }

  def cssResources(document: Document): List[CSSResource] = {

    def cssResourcesFromNodeRecursive(node: Node): List[CSSResource] = {
      // From local <script> or <style> element
      val cssResourcesFromCurrentNode = node match {
        case element: Element => cssResourcesFromElementNonRecursive(element)
        case _                => Nil
      }

      val childNodes = node.getChildNodes

      // From child nodes (recursion)
      val cssResourcesFromChildNodes = (0 until childNodes.getLength).toList.flatMap { i =>
        cssResourcesFromNodeRecursive(childNodes.item(i))
      }

      cssResourcesFromCurrentNode ++ cssResourcesFromChildNodes
    }

    def cssResourcesFromElementNonRecursive(element: Element): List[CSSResource] = {

      def attOpt(name: String): Option[String] = Option(element.getAttribute(name)).flatMap(_.trimAllToOpt)

      lazy val mediaQueries = attOpt("media").map(MediaQuery.mediaQueries).getOrElse(List(MediaQuery.AllMediaQuery))
      lazy val hasCssType   = attOpt("type") .forall(_ == CssContentType)
      lazy val isStylesheet = attOpt("rel")  .contains("stylesheet")

      if (element.getTagName.equalsIgnoreCase("link") && hasCssType && isStylesheet) {
        attOpt("href").toList.map(href => Link(new URI(href), mediaQueries))
      } else if (element.getTagName.equalsIgnoreCase("style") && hasCssType) {
        Option(element.getTextContent).flatMap(_.trimAllToOpt).toList.map(Style(_, mediaQueries))
      } else {
        Nil
      }
    }

    cssResourcesFromNodeRecursive(document)
  }

  private val VarEvaluation = """var\(\s*(--[a-zA-Z0-9_-]+)(?:\s*,\s*([^()]+|\([^)]*\))*)?\s*\)""".r

  @annotation.tailrec
  def injectVariablesIntoDeclaration(
    declarationValue   : String,
    variableDefinitions: VariableDefinitions,
    mediaQuery         : MediaQuery,
    selectors          : List[Selector]
  ): String =
    VarEvaluation.findAllMatchIn(declarationValue).toList.headOption match {
      case None =>
        // No variable evaluation
        declarationValue

      case Some(m) =>
        val fullMatch    = m.group(0)
        val variableName = m.group(1)
        val fallbackOpt  = Option(m.group(2))

        val variableValueOpt = variableDefinitions.variableValue(
          variableName = variableName,
          mediaQuery   = mediaQuery,
          selectors    = selectors
        )

        variableValueOpt orElse fallbackOpt match {
          case None =>
            // No variable value found
            declarationValue

          case Some(variableValue) =>
            // Inject variable value and look for other variable evaluations
            injectVariablesIntoDeclaration(
              declarationValue    = declarationValue.replace(fullMatch, variableValue),
              variableDefinitions = variableDefinitions,
              mediaQuery          = mediaQuery,
              selectors           = selectors
            )
        }
    }

  def injectVariablesIntoCss(
    originalCss        : String,
    variableDefinitions: VariableDefinitions,
    mediaQuery         : MediaQuery,
    cssSource          : String
  )(implicit
    indentedLogger     : IndentedLogger
  ): String = {

    val cssOpt = parsedCss(originalCss)

    cssOpt.toList.flatMap(_.getAllRules.asScala).foreach {
      case styleRule: CSSStyleRule =>
        val selectors = styleRule.getAllSelectors.asScala.toList.map(s => Selector(s.getAsCSSString))

        for (declaration <- styleRule.getAllDeclarations.asScala) {
          val originalValue = declaration.getExpression.getAsCSSString()
          val modifiedValue = injectVariablesIntoDeclaration(originalValue, variableDefinitions, mediaQuery, selectors)

          if (modifiedValue != originalValue)
            declaration.setExpression(CSSExpression.createSimple(modifiedValue))
        }

      case _ =>
    }

    cssOpt match {
      case Some(css) =>
        // Serialize CSS
        val writer = new CSSWriter(new CSSWriterSettings(ECSSVersion.CSS30))
        writer.setContentCharset(StandardCharsets.UTF_8.name)
        writer.getCSSAsString(css)

      case None =>
        error(s"Could not parse CSS for variable injection, source: $cssSource")
        originalCss
    }
  }
}
