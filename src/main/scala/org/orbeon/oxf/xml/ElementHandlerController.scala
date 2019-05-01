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
package org.orbeon.oxf.xml

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.oxf.xml.dom.XmlLocationData
import org.orbeon.saxon.om.StructuredQName
import org.xml.sax.helpers.AttributesImpl
import org.xml.sax.{Attributes, Locator}

import scala.util.control.NonFatal


/**
  * This is the controller for the handlers system.
  *
  * The handler controller:
  *
  * - keeps a list of element handlers
  * - reacts to a stream of SAX events
  * - calls handlers when needed
  * - handles repeated content
  */
class ElementHandlerController[Ctx](
  pf        : PartialFunction[(String, String, String, Attributes, Ctx), ElementHandler[Ctx]],
  defaultPf : PartialFunction[(String, String, String, Attributes, Ctx), ElementHandler[Ctx]]
) extends ElementHandlerControllerXMLReceiver[Ctx]
     with ElementHandlerControllerHandlers[Ctx] {

  protected var findHandlerFn: (String, String, String, Attributes, Ctx) => Option[ElementHandler[Ctx]] = Function.untupled(pf.orElse(defaultPf).lift)

  def combinePf(newPf: PartialFunction[(String, String, String, Attributes, Ctx), ElementHandler[Ctx]]): Unit =
    findHandlerFn = Function.untupled(pf.orElse(newPf).orElse(defaultPf).lift)
}

object ElementHandlerController {

  type Matcher[Ctx, T] = (Attributes, Ctx) => Option[T]

  private[xml] case class HandlerInfo[Ctx](level: Int, elementHandler: ElementHandler[Ctx], locator: Locator) {

    val saxStore: Option[SAXStore] = elementHandler.isRepeating option new SAXStore

    // Set initial locator so that SAXStore can obtain location data if any
    if (locator ne null)
      saxStore foreach (_.setDocumentLocator(locator))
  }

  private[xml] def withWrapThrowable[T](thunk: => T)(implicit locator: OutputLocator): T =
    try {
      thunk
    } catch {
      case NonFatal(e) =>
        throw OrbeonLocationException.wrapException(e, XmlLocationData.createIfPresent(locator))
    }
}

trait ElementHandlerControllerHandlers[Ctx] extends XMLReceiver {

  import ElementHandlerController._

  private var _handlerInfos: List[HandlerInfo[Ctx]] = Nil

  def currentHandlerInfoOpt                     : Option[HandlerInfo[Ctx]] = _handlerInfos.headOption.flatMap(Option.apply)
  def pushHandler(handlerInfo: HandlerInfo[Ctx]): Unit = _handlerInfos ::= handlerInfo
  def popHandler ()                             : Unit = _handlerInfos = _handlerInfos.tail

  var output: DeferredXMLReceiver = _
  var handlerContext: Ctx = _

  protected def findHandlerFn: (String, String, String, Attributes, Ctx) => Option[ElementHandler[Ctx]]

  // Implemented by `ElementHandlerControllerXMLReceiver`
  implicit def locator: OutputLocator
  def level: Int

  // A repeated handler may call this 1 or more times to start handling the captured body.
  def repeatBody(): Unit = {

    // Replay content of current SAXStore

    val beforeLocatorCount = if (locator ne null) locator.size else 0

    currentHandlerInfoOpt flatMap(_.saxStore) foreach (_.replay(this))

    val afterLocatorCount = if (locator ne null) locator.size else 0

    if (beforeLocatorCount != afterLocatorCount) {
      // This means that the SAXStore replay called `setDocumentLocator()`
      assert(afterLocatorCount == beforeLocatorCount + 1, "incorrect locator stack state")
      locator.pop()
    }
  }

  // Used by `XFormsRepeatHandler`
  def findFirstHandlerOrElem: Option[ElementHandler[Ctx] Either StructuredQName] = {

    val breaks = new scala.util.control.Breaks
    import breaks._

    var result: Option[ElementHandler[Ctx] Either StructuredQName] = None

    breakable {
      currentHandlerInfoOpt flatMap (_.saxStore) foreach (_.replay(new XMLReceiverAdapter {

        override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
          findHandler(uri, localname, qName, attributes)  map (_.elementHandler) match {
            case Some(_: NullHandler[_] | _: TransparentHandler[_]) =>
            case Some(elementHandler) =>
              result = Some(Left(elementHandler))
              break()
            case None =>
              result = Some(Right(new StructuredQName(XMLUtils.prefixFromQName(qName), uri, localname)))
              break()
          }
        }
      }))
    }

    result
  }

  // A handler may call this to start providing new dynamic content to process
  // TODO: Not great: "just push null so that the content is not subject to the `isForwarding` test".
  def startBody(): Unit =
    pushHandler(null)

  // A handler may call this to end providing new dynamic content to process
  def endBody(): Unit =
    popHandler()

  // Used by `XFormsLHHAHandler.findTargetControlForEffectiveId`
  def findHandlerFromElem(element: Element): Option[ElementHandler[Ctx]] =
    findHandler(
      element.getNamespaceURI,
      element.getName,
      element.getQualifiedName,
      element.attributesAsSax
    ) map
      (_.elementHandler)

  def findHandler(
    uri            : String,
    localname      : String,
    qName          : String,
    attributes     : Attributes
  ): Option[HandlerInfo[Ctx]] =
    findHandlerFn(
      uri,
      localname,
      qName,
      if (attributes.getLength == 0) SAXUtils.EMPTY_ATTRIBUTES else new AttributesImpl(attributes), // Q: Why copy `Attributes`?
      handlerContext
    ) map
      (HandlerInfo(level, _, locator))
}

trait ElementHandlerControllerXMLReceiver[Ctx] extends XMLReceiver {

  import ElementHandlerController._

  private var _locator: OutputLocator = null
  implicit def locator: OutputLocator = _locator

  private val _namespaceContext = new NamespaceContext
  def namespaceContext: NamespaceContext = _namespaceContext

  private var _isFillingUpSAXStore = false
  private var _level = 0
  def level: Int = _level

  // Implemented by `ElementHandlerControllerHandlers`
  def output: XMLReceiver
  def currentHandlerInfoOpt: Option[HandlerInfo[Ctx]]
  def pushHandler(handlerInfo: HandlerInfo[Ctx]): Unit
  def popHandler(): Unit

  def findHandler(
    uri            : String,
    localname      : String,
    qName          : String,
    attributes     : Attributes
  ): Option[HandlerInfo[Ctx]]

  // NOTE: This is called by the outer caller. Then it can be called by repeat or component body replay, which
  // recursively hit this controller. The outer caller may or may not call `setDocumentLocator()` once. If there is
  // one, repeat body replay recursively calls `setDocumentLocator()`, which is pushed on the stack, and then popped
  // after the repeat body has been entirely replayed.
  def setDocumentLocator(locator: Locator): Unit =
    if (locator ne null) {
      if (_locator eq null) {
        // This is likely the source's initial `setDocumentLocator()` call
        // Use our own locator
        _locator = new OutputLocator
        _locator.push(locator)
        // We don't forward this (anyway nobody is listening initially)
      } else {
        // This is a repeat or component body replay (otherwise it's a bug)
        // Push the `SAXStore`'s locator
        _locator.push(locator)
        // But don't forward this! SAX prevents calls to `setDocumentLocator()` mid-course. Our own locator will do the job.
      }
    }

  def startDocument(): Unit =
    withWrapThrowable {
      output.startDocument()
    }

  def endDocument(): Unit =
    withWrapThrowable {
      output.endDocument()
    }

  def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
    withWrapThrowable {

      // Increment level before, so that if callees like `start()` and `startElement()` use us, the level is correct
      _level += 1

      _namespaceContext.startElement()

      currentHandlerInfoOpt match {
        case Some(currentHandlerInfo) if _isFillingUpSAXStore =>
          currentHandlerInfo.saxStore foreach (_.startElement(uri, localname, qName, attributes))
        case Some(currentHandlerInfo) if ! currentHandlerInfo.elementHandler.isForwarding =>
          // NOP
        case _ =>
          findHandler(uri, localname, qName, attributes) match {
            case Some(handlerInfo) =>
              pushHandler(handlerInfo)
              if (handlerInfo.elementHandler.isRepeating) {
                // Repeating handler will process its body later
                _isFillingUpSAXStore = true
              } else {
                // Non-repeating handler processes its body immediately
                handlerInfo.elementHandler.start()
              }
            case None =>
              // New handler not found, send to output
              output.startElement(uri, localname, qName, attributes)
          }
      }
    }

  def endElement(uri: String, localname: String, qName: String): Unit =
    withWrapThrowable {
      currentHandlerInfoOpt match {
        case Some(currentHandlerInfo) if currentHandlerInfo.level == _level =>
          // End of current handler
          if (_isFillingUpSAXStore) {
            // Was filling-up SAXStore
            _isFillingUpSAXStore = false
            // Process body once
            currentHandlerInfo.elementHandler.start()
            currentHandlerInfo.elementHandler.end()
          } else {
            // Signal end to current handler
            currentHandlerInfo.elementHandler.end()
          }
          popHandler()
        case Some(currentHandlerInfo) if _isFillingUpSAXStore =>
          currentHandlerInfo.saxStore foreach (_.endElement(uri, localname, qName))
        case Some(currentHandlerInfo) if ! currentHandlerInfo.elementHandler.isForwarding =>
          // NOP
        case _ =>
          // Just forward
          output.endElement(uri, localname, qName)
      }

      _namespaceContext.endElement()

      _level -= 1
    }

  def startPrefixMapping(prefix: String, uri: String): Unit =
    fillOrForward { r =>
      _namespaceContext.startPrefixMapping(prefix, uri)
      r.startPrefixMapping(prefix, uri)
    }

  def endPrefixMapping     (s: String)                                       : Unit = fillOrForward(_.endPrefixMapping(s))
  def characters           (chars: Array[Char], start: Int, length: Int)     : Unit = fillOrForward(_.characters(chars, start, length))
  def processingInstruction(target: String, data: String)                    : Unit = fillOrForward(_.processingInstruction(target, data))
  def comment              (ch: Array[Char], start: Int, length: Int)        : Unit = fillOrForward(_.comment(ch, start, length))

  // NOTE: We don't expect calls to any of the following
  def ignorableWhitespace  (ch: Array[Char], start: Int, length: Int)        : Unit = fillOrForward(_.ignorableWhitespace(ch, start, length))
  def skippedEntity        (name: String)                                    : Unit = fillOrForward(_.skippedEntity(name))
  def startDTD             (name: String, publicId: String, systemId: String): Unit = fillOrForward(_.startDTD(name, publicId, systemId))
  def endDTD               ()                                                : Unit = fillOrForward(_.endDTD())
  def startEntity          (name: String)                                    : Unit = fillOrForward(_.startEntity(name))
  def endEntity            (name: String)                                    : Unit = fillOrForward(_.endEntity(name))
  def startCDATA           ()                                                : Unit = fillOrForward(_.startCDATA())
  def endCDATA             ()                                                : Unit = fillOrForward(_.endCDATA())

  private def fillOrForward[T](thunk: XMLReceiver => T): Unit =
    withWrapThrowable {
      if (_isFillingUpSAXStore)
        currentHandlerInfoOpt flatMap (_.saxStore) foreach thunk
      else if (currentHandlerInfoOpt forall (_.elementHandler.isForwarding))
        thunk(output)
    }
}
