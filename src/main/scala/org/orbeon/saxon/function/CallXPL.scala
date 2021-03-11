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
package org.orbeon.saxon.function

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Document, Element, QName}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.{PipelineContext, ProcessorDefinition}
import org.orbeon.oxf.processor.{DOMSerializer, ProcessorImpl, XPLConstants}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.util.PipelineUtils
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.{FunctionSupportJava, TransformerUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.om._
import org.orbeon.saxon.trans.XPathException
import org.orbeon.scaxon.Implicits._


/**
 * xxf:call-xpl() function.
 */
class CallXPL extends FunctionSupportJava {
  @throws[XPathException]
  override def iterate(xpathContext: XPathContext): SequenceIterator =
    try {

      val xplURL = {
        val xplURIExpression = argument(0)
        if (getSystemId == null)
          URLFactory.createURL(xplURIExpression.evaluateAsString(xpathContext).toString)
        else
          URLFactory.createURL(getSystemId, xplURIExpression.evaluateAsString(xpathContext).toString)
      }

      val inputNames =
        asScalaIterator(argument(1).iterate(xpathContext)).map(_.getStringValue).toList

      val inputNodes =
        asScalaIterator(argument(2).iterate(xpathContext)).collect{ case n: om.NodeInfo => n }.toList

      if (inputNames.size != inputNodes.size)
        throw new OXFException(s"The length of sequence of input names (${inputNames.size}) must be equal to the length of the sequence of input nodes (${inputNodes.size}).") //getDisplayName()

      val outputNames =
        asScalaIterator(argument(3).iterate(xpathContext)).map(_.getStringValue).toList

      // Create processor definition and processor
      val processorDefinition = new ProcessorDefinition(QName("pipeline", XPLConstants.OXF_PROCESSORS_NAMESPACE))
      processorDefinition.addInput(ProcessorImpl.INPUT_CONFIG, xplURL.toExternalForm)
      for ((inputName, inputNodeInfo) <- inputNames.zip(inputNodes)) {

        def throwInputError() =
          throw new OXFException(s"Input node must be a document or element for input name: `$inputName`")

        if (! (inputNodeInfo.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE || inputNodeInfo.getNodeKind == org.w3c.dom.Node.DOCUMENT_NODE))
          throwInputError()

        // TODO: We should be able to just pass inputNodeInfo to addInput() and avoid the conversions, but that doesn't work!
        inputNodeInfo match {
          case vn: VirtualNode =>
            // Get reference to DOM node
            val inputElement =
              vn.getUnderlyingNode match {
                case doc: Document                           => doc.getRootElement
                case elem: Element if elem.getParent == null => elem
                case elem: Element                           => elem.createDocumentCopyParentNamespaces(detach = false).getRootElement
                case _                                       => throwInputError()
              }
            processorDefinition.addInput(inputName, inputElement)
          case _ =>
            // Copy to dom4j
            //                            final DocumentInfo inputDocumentInfo = TransformerUtils.readTinyTree(inputNodeInfo);
            //                            processorDefinition.addInput(inputName, inputDocumentInfo);
            val inputDocument = TransformerUtils.tinyTreeToDom4j(inputNodeInfo)
            processorDefinition.addInput(inputName, inputDocument.getRootElement)
        }
      }

      val processor = InitUtils.createProcessor(processorDefinition)
      val pipelineContext = PipelineContext.get
      processor.reset(pipelineContext)
      if (outputNames.isEmpty) {
        // Just run the processor
        processor.start(pipelineContext)
        EmptyIterator.getInstance
      } else {

        val outputs =
          outputNames map processor.createOutput

        val domSerializers =
          outputs map { output =>
            new DOMSerializer |!>
              (PipelineUtils.connect(processor, output.getName, _, ProcessorImpl.OUTPUT_DATA))
          }

        val results =
          domSerializers map { domSerializer =>
            new DocumentWrapper(
              domSerializer.runGetDocument(pipelineContext).normalizeTextNodes,
              null,
              xpathContext.getConfiguration
            )
          }

        itemSeqToSequenceIterator(results)
      }
    } catch {
      case e: XPathException =>
        throw e
      case e: Exception =>
        throw new OXFException(e)
    }
}