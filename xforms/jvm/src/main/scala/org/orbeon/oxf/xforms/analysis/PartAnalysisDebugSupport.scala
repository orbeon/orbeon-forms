/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom.io.XMLWriter
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.XPathAnalysis.ConstantXPathAnalysis
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Model, StaticBind}
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult
import org.orbeon.oxf.xml.{TransformerUtils, XMLReceiver}
import org.orbeon.xforms.Constants

object PartAnalysisDebugSupport {

  import Private._

  def writePart(part: PartAnalysisImpl)(implicit receiver: XMLReceiver): Unit =
    withDocument {
      recurse(part.controlAnalysisMap(part.startScope.prefixedIdForStaticId(Constants.DocumentId)))
    }

  def partAsXmlString(part: PartAnalysisImpl): String = {

    implicit val identity: TransformerXMLReceiver = TransformerUtils.getIdentityTransformerHandler
    val result = new LocationDocumentResult
    identity.setResult(result)

    writePart(part)

    result.getDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat)
  }

  def printPartAsXml(part: PartAnalysisImpl): Unit =
    println(partAsXmlString(part))

  private object Private {

    def writeElementAnalysis(a: ElementAnalysis)(implicit receiver: XMLReceiver): Unit = {

      if (a.bindingAnalyzed)
        a.getBindingAnalysis match {
          case Some(bindingAnalysis) if a.hasBinding =>
            // For now there can be a binding analysis even if there is no binding on the control
            // (hack to simplify determining which controls to update)
            withElement("binding") {
              writeXPathAnalysis(bindingAnalysis)
            }
          case _ => // NOP
        }

      if (a.valueAnalyzed)
        a.getValueAnalysis foreach { valueAnalysis =>
          withElement("value") {
            writeXPathAnalysis(valueAnalysis)
          }
        }
    }

    def writeModel(a: Model)(implicit receiver: XMLReceiver): Unit = {

      writeElementAnalysis(a)
      a.children foreach recurse

      for (variable <- a.variablesSeq)
        recurse(variable)

      if (a.topLevelBinds.nonEmpty)
        withElement("binds") {
          for (bind <- a.topLevelBinds)
            recurse(bind)
        }

      def writeInstanceList(name: String, values: collection.Set[String]): Unit =
        if (values.nonEmpty)
          withElement(name) {
            for (value <- values)
              element("instance", text = value)
          }

      writeInstanceList("bind-instances",             a.bindInstances)
      writeInstanceList("computed-binds-instances",   a.computedBindExpressionsInstances)
      writeInstanceList("validation-binds-instances", a.validationBindInstances)

      a.eventHandlers foreach recurse
    }

    def writeStaticBind(a: StaticBind)(implicit receiver: XMLReceiver): Unit = {

      writeElementAnalysis(a)

      // `@ref` analysis is handled by superclass

      // MIP analysis
      for {
        (_, mips) <- a.allMIPNameToXPathMIP.toList.sortBy(_._1)
        mip <- mips
      } locally {
        withElement("mip", atts = List("name" -> mip.name, "expression" -> mip.compiledExpression.string)) {
          writeXPathAnalysis(mip.analysis)
        }
      }

      // Children binds
      a.children foreach recurse
    }

    def writeChildrenBuilder(a: WithChildrenTrait)(implicit receiver: XMLReceiver): Unit = {
      writeElementAnalysis(a)
      a.children foreach recurse
    }

    def writeSelectionControl(a: SelectionControlTrait)(implicit receiver: XMLReceiver): Unit = {
      writeElementAnalysis(a)
      if (a.itemsetAnalyzed)
        a.getItemsetAnalysis foreach { analysis =>
          withElement("itemset") {
            writeXPathAnalysis(analysis)
          }
        }
    }

    def writeXPathAnalysis(xpa: XPathAnalysis)(implicit receiver: XMLReceiver): Unit =
      xpa match {
        case a: ConstantXPathAnalysis =>
          element("analysis", atts = List("expression" -> a.xpathString, "analyzed" -> a.figuredOutDependencies.toString))
        case a =>
          withElement("analysis", atts = List("expression" -> a.xpathString, "analyzed" -> a.figuredOutDependencies.toString)) {

            def write(iterable: Iterable[String], enclosingElemName: String, elemName: String): Unit =
              if (iterable.nonEmpty)
                withElement(enclosingElemName) {
                  for (value <- iterable)
                    element(elemName, text = PathMapXPathAnalysis.getDisplayPath(value))
                }

            def mapSetToSet(mapSet: MapSet[String, String]) =
              mapSet map (entry => PathMapXPathAnalysis.buildInstanceString(entry._1) + "/" + entry._2)

            write(mapSetToSet(a.valueDependentPaths), "value-dependent",      "path")
            write(mapSetToSet(a.returnablePaths),     "returnable",           "path")

            write(a.dependentModels,                  "dependent-models",     "model")
            write(a.dependentInstances,               "dependent-instances",  "instance")
            write(a.returnablePaths.map.keys,         "returnable-instances", "instance")
          }
      }

    def recurse(ea: ElementAnalysis)(implicit receiver: XMLReceiver): Unit = {

      val atts =
        ea match {
          case m: Model =>
            List(
              "scope"                        -> m.scope.scopeId,
              "prefixed-id"                  -> m.prefixedId,
              "default-instance-prefixed-id" -> m.defaultInstancePrefixedId.orNull,
              "analyzed-binds"               -> m.figuredAllBindRefAnalysis.toString
            )
          case b: StaticBind =>
            List(
              "id"      -> b.staticId,
              "context" -> b.context.orNull,
              "ref"     -> b.ref.orNull
            )
          case e =>
            List(
              "scope"             -> e.scope.scopeId,
              "prefixed-id"       -> e.prefixedId,
              "model-prefixed-id" -> (e.model map (_.prefixedId) orNull),
              "binding"           -> e.hasBinding.toString,
              "value"             -> e.isInstanceOf[ValueTrait].toString,
              "name"              -> e.element.attributeValue("name")
            )
        }

      withElement(ea.localName, atts = atts) {
        ea match {
          case a: Model                 => writeModel(a)
          case a: StaticBind            => writeStaticBind(a)
          case a: SelectionControlTrait => writeSelectionControl(a)
          case a: WithChildrenTrait     => writeChildrenBuilder(a)
          case a                        => writeElementAnalysis(a)
        }
      }
    }
  }
}