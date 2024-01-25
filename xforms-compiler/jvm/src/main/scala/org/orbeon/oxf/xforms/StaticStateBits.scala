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
package org.orbeon.oxf.xforms

import cats.syntax.option._
import org.orbeon.dom.Document
import org.orbeon.io.StringBuilderWriter
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{IndentedLogger, NumberUtils, WhitespaceMatching}
import org.orbeon.oxf.xforms.analysis.{IdGenerator, Metadata, XFormsAnnotator, XFormsExtractor}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.LocationDocumentResult
import org.orbeon.xforms.XXBLScope

import java.{lang => jl}
import javax.xml.transform.stream.StreamResult


class StaticStateBits(
  val metadata            : Metadata,
  val template            : AnnotatedTemplate,
  val staticStateDocument : Document,
  val staticStateDigest   : String
)

object StaticStateBits {

  def fromXmlReceiver(
    existingStaticStateDigest : Option[String],
    read                      : XMLReceiver => Unit
  )(implicit
    logger                    : IndentedLogger
  ): StaticStateBits =
    withDebug("reading input", List("existing digest" -> existingStaticStateDigest.orNull)) {

      val isLogStaticStateInput = Loggers.isDebugEnabled("html-static-state")

      val existingStaticStateDigestOrReceiver =
        (if (isLogStaticStateInput) None else existingStaticStateDigest).toLeft(new DigestContentHandler)

      val documentResult = new LocationDocumentResult
      val extractorOutput = {

        val debugReceiverOpt = isLogStaticStateInput option {
          val identity = TransformerUtils.getIdentityTransformerHandler
          val writer = new StringBuilderWriter(new jl.StringBuilder)
          identity.setResult(new StreamResult(writer))
          new ForwardingXMLReceiver(identity) {
            override def endDocument(): Unit = {
              super.endDocument()
              // Log out at end of document
              debug("static state input", List("input" -> writer.result))
            }
          }
        }

        val documentReceiver =
          TransformerUtils.getIdentityTransformerHandler |!> (_.setResult(documentResult))

        (debugReceiverOpt, existingStaticStateDigestOrReceiver) match {
          case (Some(debugReceiver), Right(digestReceiver)) => new TeeXMLReceiver(documentReceiver, digestReceiver, debugReceiver)
          case (Some(debugReceiver), Left(_))               => new TeeXMLReceiver(documentReceiver, debugReceiver)
          case (None,                Right(digestReceiver)) => new TeeXMLReceiver(documentReceiver, digestReceiver)
          case (None,                Left(_))               => documentReceiver
        }
      }

      // Read the input through the annotator and gather namespace mappings
      //
      // Output of annotator is:
      // - annotated page template (TODO: this should not include model elements)
      // - extractor
      // Output of extractor is:
      // - static state document
      // - optionally: digest
      // - optionally: debug output
      val template = AnnotatedTemplate(new SAXStore)
      val metadata = Metadata(new IdGenerator(1), isTopLevelPart = true)

      read(
        new WhitespaceXMLReceiver(
          new XFormsAnnotator(
            template.saxStore,
            new XFormsExtractor(
              new WhitespaceXMLReceiver(
                extractorOutput,
                WhitespaceMatching.defaultBasePolicy,
                WhitespaceMatching.basePolicyMatcher
              ).some,
              metadata,
              template.some,
              ".",
              XXBLScope.Inner,
              isTopLevel           = true,
              outputSingleTemplate = false
            ),
            metadata,
            true,
            logger
          ),
          WhitespaceMatching.defaultHTMLPolicy,
          WhitespaceMatching.htmlPolicyMatcher
        )
      )

      val newStaticStateDigest =
        existingStaticStateDigestOrReceiver match {
          case Left(digest)    => digest
          case Right(receiver) => NumberUtils.toHexString(receiver.getResult)
        }

      assert(! isLogStaticStateInput || existingStaticStateDigest.isEmpty || existingStaticStateDigest.contains(newStaticStateDigest))

      debugResults(List("resulting digest" -> newStaticStateDigest))

      new StaticStateBits(
        metadata,
        template,
        documentResult.getDocument,
        newStaticStateDigest
      )
    }
}