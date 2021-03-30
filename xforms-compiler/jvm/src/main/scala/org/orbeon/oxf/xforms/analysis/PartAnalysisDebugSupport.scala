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
import org.orbeon.oxf.xforms.analysis.XPathAnalysis.writeXPathAnalysis
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Model, StaticBind}
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult
import org.orbeon.oxf.xml.{TransformerUtils, XMLReceiver}
import org.orbeon.xforms.Constants


object PartAnalysisDebugSupport {

  import Private._

  def writePart(partAnalysis: PartAnalysisRuntimeOps)(implicit receiver: XMLReceiver): Unit =
    withDocument {
      partAnalysis.findControlAnalysis(partAnalysis.startScope.prefixedIdForStaticId(Constants.DocumentId)) foreach recurse
    }

  def printPartAsXml(partAnalysis: PartAnalysisRuntimeOps): Unit =
    println(partAsXmlString(partAnalysis))

  def printElementAnalysis(a: ElementAnalysis): Unit = {

    implicit val identity: TransformerXMLReceiver = TransformerUtils.getIdentityTransformerHandler
    val result = new LocationDocumentResult
    identity.setResult(result)

    withDocument {
      withElement("root") {
        writeElementAnalysis(a)
      }
    }

    println(result.getDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat))
  }

  def printXPathAnalysis(xpa: XPathAnalysis): Unit = {

    implicit val identity: TransformerXMLReceiver = TransformerUtils.getIdentityTransformerHandler
    val result = new LocationDocumentResult
    identity.setResult(result)

    withDocument {
      writeXPathAnalysis(xpa)
    }

    println(result.getDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat))
  }

  private object Private {

    def partAsXmlString(partAnalysis: PartAnalysisRuntimeOps): String = {

      implicit val identity: TransformerXMLReceiver = TransformerUtils.getIdentityTransformerHandler
      val result = new LocationDocumentResult
      identity.setResult(result)

      writePart(partAnalysis)

      result.getDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat)
    }

    def writeElementAnalysis(a: ElementAnalysis)(implicit receiver: XMLReceiver): Unit = {

      a.bindingAnalysis match {
        case Some(bindingAnalysis) if a.hasBinding =>
          // For now there can be a binding analysis even if there is no binding on the control
          // (hack to simplify determining which controls to update)
          withElement("binding") {
            writeXPathAnalysis(bindingAnalysis)
          }
        case _ => // NOP
      }

      a.valueAnalysis foreach { valueAnalysis =>
        withElement("value") {
          writeXPathAnalysis(valueAnalysis)
        }
      }
    }

    def writeModel(a: Model)(implicit receiver: XMLReceiver): Unit = {

      writeElementAnalysis(a)
      a.children.iterator filterNot
        (e => e.isInstanceOf[StaticBind] || e.isInstanceOf[VariableAnalysisTrait]) foreach
        recurse

      a.variablesSeq foreach recurse

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
      a.itemsetAnalysis foreach { analysis =>
        withElement("itemset") {
          writeXPathAnalysis(analysis)
        }
      }
    }

    // Don't output nested value if any as everything is already contained in the enclosing variable
    def writeVariableControl(a: VariableAnalysisTrait)(implicit receiver: XMLReceiver): Unit = {
      writeElementAnalysis(a)
      a.children filterNot (_.isInstanceOf[VariableValueTrait]) foreach recurse
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
          case a: VariableAnalysisTrait => writeVariableControl(a)
          case a: WithChildrenTrait     => writeChildrenBuilder(a)
          case a                        => writeElementAnalysis(a)
        }
      }
    }
  }
}