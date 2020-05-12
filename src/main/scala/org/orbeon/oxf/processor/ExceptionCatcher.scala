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
package org.orbeon.oxf.processor

import org.orbeon.errorified.Exceptions
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.ExceptionGenerator
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.{SAXStore, XMLReceiver, XMLReceiverHelper}
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.scaxon

import scala.util.control.NonFatal

/**
 * This processor has a data input and data output and behaves like the identity processor except if an exception is
 * thrown while reading its input, in which case it outputs the exception like the ExceptionGenerator does.
 */
class ExceptionCatcher extends ProcessorImpl {

  import ProcessorImpl._

  addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

  override def createOutput(name: String) =
    addOutput(name, new ProcessorOutputImpl(ExceptionCatcher.this, name) {
      def readImpl(context: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

        // Try to read config
        val logStackTrace =
          if (getConnectedInputs.get(INPUT_CONFIG) ne null) {
            readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader[Boolean] {
              def read(pipelineContext: PipelineContext, input: ProcessorInput) = {
                val doc = readInputAsTinyTree(context, input, XPath.GlobalConfiguration)
                scaxon.XPath.evalOne(doc, "not(/*/stack-trace = 'false')").asInstanceOf[BooleanValue].getBooleanValue
              }
            })
          } else
            true

        try {
          // Try to read input in SAX store
          val dataInput = new SAXStore
          readInputAsSAX(context, INPUT_DATA, dataInput)
          // No exception: output what was read
          dataInput.replay(xmlReceiver)
        } catch {
          case NonFatal(t) =>
            // Exception was thrown while reading input: generate a document with that exception

            // It is up to the caller to decide what to do with the exception
            if (logger.isDebugEnabled)
              logger.debug("oxf:exception-catcher caught:\n" + OrbeonFormatter.format(t))

            val helper = new XMLReceiverHelper(xmlReceiver)

            helper.startDocument()
            helper.startElement("exceptions")

            val innerMostThrowable = Exceptions.getRootThrowable(t)
            ExceptionGenerator.addThrowable(helper, innerMostThrowable, logStackTrace)

            helper.endElement()
            helper.endDocument()
        }
      }
    })
}