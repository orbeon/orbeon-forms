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

import java.lang.reflect.Constructor
import java.{lang ⇒ jl, util ⇒ ju}

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData}
import org.xml.sax.{Attributes, Locator}

import scala.collection.JavaConverters._
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
  *
  * Q: Should use pools of handlers to reduce memory consumption?
  */
object ElementHandlerController {

  type Matcher[T <: AnyRef] = (Attributes, AnyRef) ⇒ T

  // For Java callers
  abstract class Function2Base[V1, V2, R] extends ((V1, V2) ⇒ R)

  private case class HandlerInfo(level: Int, elementHandler: ElementHandler, locator: Locator) {

    val saxStore: Option[SAXStore] = elementHandler.isRepeating option new SAXStore

    // Set initial locator so that SAXStore can obtain location data if any
    if (locator ne null)
      saxStore foreach (_.setDocumentLocator(locator))
  }

  private val AllMatcher: Matcher[jl.Boolean] = (_: Attributes, _: AnyRef) ⇒ jl.Boolean.TRUE

  private type HandlerAndMatcher = (String, Matcher[_ <: AnyRef])

  // `Class.forName` is expensive, so we cache mappings
  private val classNameToHandlerClass = new ju.concurrent.ConcurrentHashMap[String, Constructor[ElementHandler]]

  private def getHandlerByClassName(
    handlerClassName : String,
    uri              : String,
    localname        : String,
    qName            : String,
    attributes       : Attributes,
    matched          : AnyRef,
    handlerContext   : AnyRef)(implicit
    locator          : OutputLocator
  ): ElementHandler = {

    // Atomically get or create the constructor
    val constructor =
      classNameToHandlerClass.computeIfAbsent(
        handlerClassName,
        // For Scala 2.11
        new java.util.function.Function[String, Constructor[ElementHandler]] {
          def apply(t: String): Constructor[ElementHandler] =
            withWrapThrowable {
              Class.forName(handlerClassName).asInstanceOf[Class[ElementHandler]]
                .getConstructor(classOf[String], classOf[String], classOf[String], classOf[Attributes], classOf[AnyRef], classOf[AnyRef])
            }
        }
      )

    withWrapThrowable {
      constructor.newInstance(uri, localname, qName, attributes, matched, handlerContext)
    }
  }

  private def withWrapThrowable[T](thunk: ⇒ T)(implicit locator: OutputLocator): T =
    try {
      thunk
    } catch {
      case NonFatal(e) ⇒
        throw OrbeonLocationException.wrapException(e, LocationData.createIfPresent(locator))
    }
}

class ElementHandlerController extends XMLReceiver {

  import ElementHandlerController._

  private val _handlerMatchers = new ju.HashMap[String, ju.List[HandlerAndMatcher]]
  private val _uriHandlers     = new ju.HashMap[String, String]
  private val _customMatchers  = new ju.ArrayList[HandlerAndMatcher]

  private val _handlerInfos = new ju.Stack[HandlerInfo]
  private var _currentHandlerInfo: HandlerInfo = null

  private var _isFillingUpSAXStore = false
  private var _level = 0

  private var _elementHandlerContext: AnyRef = null
  def setElementHandlerContext(elementHandlerContext: AnyRef): Unit = _elementHandlerContext = elementHandlerContext

  private var _output: DeferredXMLReceiver = null
  def getOutput: DeferredXMLReceiver = _output
  def setOutput(output: DeferredXMLReceiver): Unit = _output = output

  private val _namespaceContext = new NamespaceContext
  def namespaceContext: NamespaceContext = _namespaceContext

  private implicit var _locator: OutputLocator = null
  def locator: Locator = _locator

  // Register a handler. The handler can match, in order:
  //
  // - URI + localname + custom matcher
  // - URI + localname
  // - URI only
  //
  def registerHandler(handlerClassName: String, uri: String, localnameOrNull: String, matcherOrNull: Matcher[_ <: AnyRef]): Unit = {
    if (localnameOrNull ne null) {
      // Match on URI + localname and optionally custom matcher
      val key = XMLUtils.buildExplodedQName(uri, localnameOrNull)
      var handlerMatchers = _handlerMatchers.get(key)
      if (handlerMatchers eq null) {
        handlerMatchers = new ju.ArrayList[HandlerAndMatcher]
        _handlerMatchers.put(key, handlerMatchers)
      }
      handlerMatchers.add(
        (handlerClassName, if (matcherOrNull ne null) matcherOrNull else AllMatcher)
      )
    } else {
      // Match on URI only
      _uriHandlers.put(uri, handlerClassName)
    }
  }

  // TODO: Just pass in a `List`?
  def registerHandler(handlerClassName: String, matcher: Matcher[_ <: AnyRef]): Unit =
    _customMatchers.add((handlerClassName, matcher))

  // A repeated handler may call this 1 or more times to start handling the captured body.
  def repeatBody(): Unit = {

    // Replay content of current SAXStore

    val beforeLocatorCount = if (_locator ne null) _locator.size else 0

    _currentHandlerInfo.saxStore foreach (_.replay(this))

    val afterLocatorCount = if (_locator ne null) _locator.size else 0

    if (beforeLocatorCount != afterLocatorCount) {
      // This means that the SAXStore replay called `setDocumentLocator()`
      assert(afterLocatorCount == beforeLocatorCount + 1, "incorrect locator stack state")
      _locator.pop()
    }
  }

  // A handler may call this to start providing new dynamic content to process.
  def startBody(): Unit = {
    // Just push null so that the content is not subject to the `isForwarding` test.
    _handlerInfos.push(null)
    _currentHandlerInfo = null
  }

  // A handler may call this to end providing new dynamic content to process.
  def endBody(): Unit = {
    _handlerInfos.pop()
    _currentHandlerInfo = if (! _handlerInfos.isEmpty) _handlerInfos.peek else null
  }

  def startDocument(): Unit = {
    withWrapThrowable {
      _output.startDocument()
    }
  }

  def endDocument(): Unit = {
    withWrapThrowable {
      _output.endDocument()
    }
  }

  def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
    withWrapThrowable {

      // Increment level before, so that if callees like start() and startElement() use us, the level is correct
      _level += 1

      _namespaceContext.startElement()

      if (_isFillingUpSAXStore) {
        _currentHandlerInfo.saxStore foreach (_.startElement(uri, localname, qName, attributes))
      } else if ((_currentHandlerInfo ne null) && !_currentHandlerInfo.elementHandler.isForwarding) {
        // The current handler doesn't want forwarding
        // Just ignore content
      } else { // Look for a new handler
        findHandler(uri, localname, qName, attributes, _elementHandlerContext) match {
          case Some(handlerInfo) ⇒
            // New handler found
            val elementHandler = handlerInfo.elementHandler
            // Push current handler
            _currentHandlerInfo = handlerInfo
            _handlerInfos.push(_currentHandlerInfo)
            if (elementHandler.isRepeating) {
              // Repeating handler will process its body later
              _isFillingUpSAXStore = true
            } else {
              // Non-repeating handler processes its body immediately
              // Signal init/start to current handler
              elementHandler.start()
            }
          case None ⇒
            // New handler not found, send to output
            _output.startElement(uri, localname, qName, attributes)
        }
      }
    }

  def endElement(uri: String, localname: String, qName: String): Unit =
    withWrapThrowable {
      if ((_currentHandlerInfo ne null) && _currentHandlerInfo.level == _level) {
        // End of current handler
        if (_isFillingUpSAXStore) {
          // Was filling-up SAXStore
          _isFillingUpSAXStore = false
          // Process body once
          _currentHandlerInfo.elementHandler.start()
          _currentHandlerInfo.elementHandler.end()
        } else {
          // Signal end to current handler
          _currentHandlerInfo.elementHandler.end()
        }
        // Pop current handler
        _handlerInfos.pop()
        _currentHandlerInfo = if (! _handlerInfos.isEmpty) _handlerInfos.peek else null
      } else if (_isFillingUpSAXStore) {
        _currentHandlerInfo.saxStore foreach (_.endElement(uri, localname, qName))
      } else if ((_currentHandlerInfo ne null) && ! _currentHandlerInfo.elementHandler.isForwarding) {
        // NOP
      } else {
        // Just forward
        _output.endElement(uri, localname, qName)
      }

      _namespaceContext.endElement()

      _level -= 1
    }

  def startPrefixMapping(prefix: String, uri: String): Unit =
    fillOrForward { r ⇒
      _namespaceContext.startPrefixMapping(prefix, uri)
      r.startPrefixMapping(prefix, uri)
    }

  def endPrefixMapping     (s: String)                                  : Unit = fillOrForward(_.endPrefixMapping(s))
  def characters           (chars: Array[Char], start: Int, length: Int): Unit = fillOrForward(_.characters(chars, start, length))
  def ignorableWhitespace  (ch: Array[Char], start: Int, length: Int)   : Unit = fillOrForward(_.ignorableWhitespace(ch, start, length))
  def processingInstruction(target: String, data: String)               : Unit = fillOrForward(_.processingInstruction(target, data))
  def skippedEntity(name: String)                                       : Unit = fillOrForward(_.skippedEntity(name))
  def startDTD   (name: String, publicId: String, systemId: String)     : Unit = fillOrForward(_.startDTD(name, publicId, systemId))
  def endDTD     ()                                                     : Unit = fillOrForward(_.endDTD())
  def startEntity(name: String)                                         : Unit = fillOrForward(_.startEntity(name))
  def endEntity  (name: String)                                         : Unit = fillOrForward(_.endEntity(name))
  def startCDATA ()                                                     : Unit = fillOrForward(_.startCDATA())
  def endCDATA   ()                                                     : Unit = fillOrForward(_.endCDATA())
  def comment    (ch: Array[Char], start: Int, length: Int)             : Unit = fillOrForward(_.comment(ch, start, length))

  // NOTE: This is called by the outer caller. Then it can be called by repeat or component body replay, which
  // recursively hit this controller. The outer caller may or may not call setDocumentLocator() once. If there is
  // one, repeat body replay recursively calls setDocumentLocator(), which is pushed on the stack, and then popped
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
        // Push the SAXStore's locator
        _locator.push(locator)
        // But don't forward this! SAX prevents calls to `setDocumentLocator()` mid-course. Our own locator will do the job.
      }
    }

  def findHandlerFromElem(element: Element, handlerContext: AnyRef): Option[ElementHandler] =
    findHandler(
      element.getNamespaceURI,
      element.getName,
      element.getQualifiedName,
      Dom4jUtils.getSAXAttributes(element),
      handlerContext
    ) map
      (_.elementHandler)

  private def findHandler(
    uri            : String,
    localname      : String,
    qName          : String,
    attributes     : Attributes,
    handlerContext : AnyRef
  ): Option[HandlerInfo] = {

    def fromCustomMatchers =
      findWithMatchers(_customMatchers, uri, localname, qName, attributes, handlerContext)

    def fromFullMatchers =
      Option(_handlerMatchers.get(XMLUtils.buildExplodedQName(uri, localname))) flatMap { handlerMatchers ⇒
        findWithMatchers(handlerMatchers, uri, localname, qName, attributes, handlerContext)
      }

    def fromUriBasedHandler =
      Option(_uriHandlers.get(uri)) map { uriHandlerClassName ⇒
        HandlerInfo(
          _level,
          getHandlerByClassName(uriHandlerClassName, uri, localname, qName, attributes, null, handlerContext),
          _locator
        )
      }

    fromCustomMatchers orElse fromFullMatchers orElse fromUriBasedHandler
  }

  private def findWithMatchers(
    matchers       : ju.List[HandlerAndMatcher],
    uri            : String,
    localname      : String,
    qName          : String,
    attributes     : Attributes,
    handlerContext : AnyRef
  ): Option[HandlerInfo] =
    matchers.asScala.iterator map {
      case (handlerClassName, matcher) ⇒
        handlerClassName → matcher(attributes, _elementHandlerContext)
    } collectFirst {
      case (handlerClassName, matched) if matched ne null ⇒
        HandlerInfo(
          _level,
          getHandlerByClassName(handlerClassName, uri, localname, qName, attributes, matched, handlerContext),
          _locator
        )
    }

  private def fillOrForward[T](thunk: XMLReceiver ⇒ T): Unit =
    withWrapThrowable {
      if (_isFillingUpSAXStore)
        _currentHandlerInfo.saxStore foreach thunk
      else if ((_currentHandlerInfo eq null) || _currentHandlerInfo.elementHandler.isForwarding)
        thunk(_output)
    }
}