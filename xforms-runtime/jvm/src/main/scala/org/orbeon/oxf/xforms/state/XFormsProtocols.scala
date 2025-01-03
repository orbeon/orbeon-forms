/**
*  Copyright (C) 2012 Orbeon, Inc.
*
*  This program is free software; you can redistribute it and/or modify it under the terms of the
*  GNU Lesser General Public License as published by the Free Software Foundation; either version
*  2.1 of the License, or (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
*  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
*  See the GNU Lesser General Public License for more details.
*
*  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
*/
package org.orbeon.oxf.xforms.state

import org.orbeon.connection.BufferedContent
import org.orbeon.dom.{Document, Namespace, QName}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.PathMatcher
import org.orbeon.oxf.xforms.model.{InstanceCaching, XFormsInstance}
import org.orbeon.oxf.xforms.state.XFormsCommonBinaryFormats.*
import org.orbeon.oxf.xml.SBinaryDefaultFormats.*
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom.LocationDocumentSource
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.xforms.runtime.{DelayedEvent, SimplePropertyValue}
import sbinary.*
import sbinary.Operations.*

import javax.xml.transform.OutputKeys
import javax.xml.transform.stream.StreamResult


// TODO: Almost none of this should be needed, as case classes, etc. should be handled automatically.
object XFormsOperations {

  // NOTE: We use immutable.Seq instead of Array to indicate immutability

  def toByteSeq[T: Writes](t: T): Seq[Byte] =
    toByteArray(t).toSeq // actually a `WrappedArray`

  def fromByteSeq[T: Reads](bytes: Seq[Byte]): T =
    fromByteArray(bytes.toArray)(implicitly[Reads[T]]) // TODO: inefficient copy to array -> implement Input instead
}

object XFormsProtocols {

  implicit object DynamicStateFormat extends SerializableFormat[DynamicState] {
    def allowedClass: Class[DynamicState] = classOf[DynamicState]
  }

  implicit object OrbeonDomFormat extends Format[Document] {
    def writes(output: Output, document: Document): Unit = {
      val identity = TransformerUtils.getXMLIdentityTransformer
      identity.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
      identity.transform(new LocationDocumentSource(document), new StreamResult(new JavaOutputStream(output)))
    }

    def reads(input: Input): Document =
      TransformerUtils.readOrbeonDom(new JavaInputStream(input), null, false, false)
  }

  implicit object ControlFormat extends Format[ControlState] {

    def writes(output: Output, control: ControlState): Unit = {
      write(output, control.effectiveId)
      write(output, control.visited)
      write(output, control.keyValues)
    }

    def reads(input: Input): ControlState =
      ControlState(read[String](input), read[Boolean](input), read[Map[String, String]](input))
  }

  implicit object SimplePropertyValueFormat extends Format[SimplePropertyValue] {

    def writes(output: Output, value: SimplePropertyValue): Unit = {
      write(output, value.name)
      (value.value: Any) match {
        // These are simple serializable types we support from `evaluateKeepNodeInfo()`
        case v: String         => write[Byte](output, 0); write(output, v)
        case v: Boolean        => write[Byte](output, 1); write(output, v)
        case v: BigDecimal     => write[Byte](output, 2); write(output, v)
        case v: Long           => write[Byte](output, 3); write(output, v)
        case v: Double         => write[Byte](output, 4); write(output, v)
        case v: Float          => write[Byte](output, 5); write(output, v)
        case v: java.util.Date => write[Byte](output, 6); write(output, v.getTime)
        case v: Array[Byte]    => write[Byte](output, 7); write(output, v)
        case v                 => throw new IllegalArgumentException(s"Unsupported property type: ${v.getClass.getName}")
      }
      write(output, value.tunnel)
    }

    def reads(input: Input): SimplePropertyValue =
      SimplePropertyValue(
        read[String](input),
        {
          read[Byte](input) match {
            case 0 => read[String](input)
            case 1 => read[Boolean](input)
            case 2 => read[BigDecimal](input)
            case 3 => read[Long](input)
            case 4 => read[Double](input)
            case 5 => read[Float](input)
            case 6 => new java.util.Date(read[Long](input))
            case 7 => read[Array[Byte]](input)
            case _ => throw new IllegalArgumentException("Unsupported property type")
          }
        },
        read[Boolean](input)
      )
  }

  implicit object DelayedEventFormat extends Format[DelayedEvent] {

    def writes(output: Output, delayedEvent: DelayedEvent): Unit = {
      write(output, delayedEvent.eventName)
      write(output, delayedEvent.targetEffectiveId)
      write(output, delayedEvent.bubbles)
      write(output, delayedEvent.cancelable)
      write(output, delayedEvent.time)
      write(output, delayedEvent.showProgress)
      write(output, delayedEvent.browserTarget)
      write(output, delayedEvent.submissionId)
      write(output, delayedEvent.isResponseResourceType)
      write(output, delayedEvent.stringProperties)
      // Explicitly don't write `submissionParameters` here, as it's used only for two-pass submissions, and we don't
      // expect serialization to happen between the two passes.
    }

    def reads(input: Input): DelayedEvent =
      DelayedEvent(
        read[String](input),
        read[String](input),
        read[Boolean](input),
        read[Boolean](input),
        read[Option[Long]](input),
        read[Boolean](input),
        read[Option[String]](input),
        read[Option[String]](input),
        read[Boolean](input),
        read[List[SimplePropertyValue]](input),
        submissionParameters = None // see comment in `writes()`
      )
  }

  implicit object BufferedContentFormat extends Format[BufferedContent] {

    def writes(output: Output, content: BufferedContent): Unit = {
      write(output, content.body)
      write(output, content.contentType)
      write(output, content.title)
    }

    def reads(in: Input): BufferedContent =
      BufferedContent(
        read[Array[Byte]](in),
        read[Option[String]](in),
        read[Option[String]](in),
      )
  }

  implicit object InstanceCachingFormat extends Format[InstanceCaching] {

    def writes(output: Output, instance: InstanceCaching): Unit = {
      write(output, instance.timeToLive)
      write(output, instance.handleXInclude)
      write(output, instance.pathOrAbsoluteURI)
      write(output, instance.method.toString)
      write(output, instance.requestContent)
    }

    def reads(in: Input): InstanceCaching =
      InstanceCaching(
        read[Long](in),
        read[Boolean](in),
        read[String](in),
        HttpMethod.withName(read[String](in)),
        read[Option[BufferedContent]](in)
      )
  }

  implicit object InstanceFormat extends Format[InstanceState] {

    def writes(output: Output, instance: InstanceState): Unit = {
      write(output, instance.effectiveId)
      write(output, instance.modelEffectiveId)
      instance.cachingOrDocument match {
        case Left (caching)        => write[Byte](output, 0); write(output, caching)
        case Right(doc @ Left(_))  => write[Byte](output, 1); write(output, XFormsInstance.serializeInstanceDocumentToString(doc))
        case Right(doc @ Right(_)) => write[Byte](output, 2); write(output, XFormsInstance.serializeInstanceDocumentToString(doc))
      }
      write(output, instance.readonly)
      write(output, instance.modified)
      write(output, instance.valid)
    }

    def reads(in: Input): InstanceState = {

      def readCachingOrContent: Either[InstanceCaching, Either[Document, DocumentInfo]] = read[Byte](in) match {
        case 0 => Left(read[InstanceCaching](in))
        case 1 => Right(XFormsInstance.deserializeInstanceDocumentFromString(read[String](in), readonly = false))
        case 2 => Right(XFormsInstance.deserializeInstanceDocumentFromString(read[String](in), readonly = true))
      }

      InstanceState(
        read[String](in),
        read[String](in),
        readCachingOrContent,
        read[Boolean](in),
        read[Boolean](in),
        read[Boolean](in)
      )
    }
  }

  implicit object QNameFormat extends Format[QName] {
    def writes(out: Output, value: QName): Unit = {
      write(out, value.localName)
      write(out, value.namespace.prefix)
      write(out, value.namespace.uri)
      write(out, value.qualifiedName)
    }

    def reads(in: Input): QName =
      QName(
        read[String](in),
        Namespace(read[String](in), read[String](in)),
        read[String](in)
      )
  }

  implicit object PathMatcherFormat extends Format[PathMatcher] {
    def writes(output: Output, value: PathMatcher): Unit = {
      write(output, value.regexp)
      write(output, Option(value.mimeType))
      write(output, value.versioned)
    }

    def reads(in: Input): PathMatcher =
      PathMatcher(
        read[String](in),
        read[Option[String]](in).orNull,
        read[Boolean](in)
      )
  }
}
