package org.orbeon.web

import cats.syntax.option.*
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.web.DomEventNames.*
import org.scalajs.dom
import org.scalajs.dom.*

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.scalajs.js


object DomSupport {

  case class Offset(left: Double, top: Double)

  implicit class DomElemOps[T >: html.Element <: dom.Element](private val elem: T) extends AnyVal {

    def querySelectorAllT(selectors: String): collection.Seq[T] =
      elem.querySelectorAll(selectors).asInstanceOf[dom.NodeList[T]]

    def querySelectorT(selectors: String): T =
      elem.querySelector(selectors).asInstanceOf[T]

    def querySelectorOpt(selectors: String): Option[T] =
      Option(elem.querySelector(selectors).asInstanceOf[T])

    def hasClass(cls: String): Boolean =
      elem.classList.contains(cls)

    def hasAllClasses(classes: String*): Boolean =
      classes.forall(elem.classList.contains)

    def hasAnyClass(classes: String*): Boolean =
      classes.exists(elem.classList.contains)

    def previousElementSiblingT: T =
      elem.previousElementSibling.asInstanceOf[T]

    def previousElementSiblings: Iterator[T] =
      Iterator.iterate(elem.previousElementSibling.asInstanceOf[T])(_.previousElementSibling.asInstanceOf[T]).takeWhile(_ ne null)

    def previousElementSiblings(selector: String): Iterator[T] =
      elem.previousElementSiblings.filter(_.matches(selector))

    def previousElementOpt: Option[T] =
      elem.previousElementSiblings.nextOption()

    def nextElementSiblingT: T =
      elem.nextElementSibling.asInstanceOf[T]

    def nextElementSiblings: Iterator[T] =
      Iterator.iterate(elem.nextElementSibling.asInstanceOf[T])(_.nextElementSibling.asInstanceOf[T]).takeWhile(_ ne null)

    def nextElementSiblings(selector: String): Iterator[T] =
      elem.nextElementSiblings.filter(_.matches(selector))

    def nextElementOrThrow: T =
      nextElementOpt.getOrElse(throw new NoSuchElementException("No next element sibling"))

    def nextElementOpt: Option[T] =
      elem.nextElementSiblings.nextOption()

    def nextSiblings: Iterator[dom.Node] =
      Iterator.iterate(elem.nextSibling)(_.nextSibling).takeWhile(_ ne null)

    def closestT(selector: String): T =
      elem.closest(selector).asInstanceOf[T]

    def closestOpt(selector: String): Option[T] =
      Option(elem.closestT(selector))

    def childrenT: collection.Seq[T] =
      elem.children.asInstanceOf[HTMLCollection[T]]

    def childrenT(selector: String): collection.Seq[T] =
      elem.childrenT.filter(_.matches(selector))

    def parentElement: T =
      elem.asInstanceOf[js.Dynamic].parentElement.asInstanceOf[T]

    def parentElementOpt: Option[T] =
      Option(parentElement)

    def ancestorOrSelfElem(includeSelf: Boolean = false): Iterator[T] =
      Iterator.iterate(
        if (includeSelf)
          elem
        else
          elem.parentElement
      )(
        _.parentElement
      ).takeWhile(_ ne null)

    def ancestorOrSelfElem(selector: String, includeSelf: Boolean): Iterator[T] =
      ancestorOrSelfElem(includeSelf).filter(_.matches(selector))

    def appendChildT[U <: dom.Node](newChild: U): U =
      elem.appendChild(newChild).asInstanceOf[U]

    def index: Int =
      parentElementOpt match {
        case Some(parent) => parent.childrenT.indexOf(elem)
        case None         => -1
      }

    def trigger(eventName: String): Unit = {
      val e = elem.asInstanceOf[html.Element]
      eventName match {
        case "focus" => e.focus()
        case "blur"  => e.blur()
        case _       => dispatchCustomEvent(e, eventName)
      }
    }

    def childrenWithLocalName(name: String): Iterator[T] =
      elem.childNodes.iterator collect {
        case n: dom.Element if n.localName == name => n.asInstanceOf[T]
      }

    def firstChildWithLocalNameOpt(name: String): Option[dom.Element] =
      childrenWithLocalName(name).nextOption()

    def firstChildWithLocalNameOrThrow(name: String): dom.Element =
      firstChildWithLocalNameOpt(name).getOrElse(throw new NoSuchElementException(name))

    // Just in case, normalize following:
    // https://developer.mozilla.org/en-US/docs/Web/API/Element/getAttribute#non-existing_attributes
    def attValueOpt(name: String): Option[String] =
      if (elem.hasAttribute(name))
        Option(elem.getAttribute(name)) // `Some()` should be ok but just in case...
      else
        None

    def attValueOrThrow(name: String): String =
      attValueOpt(name).getOrElse(throw new NoSuchElementException(name))

    def booleanAttValueOpt(name: String): Option[Boolean] =
      attValueOpt(name).map(_.toBoolean)

    def queryNestedElems[U <: html.Element : ClassTag](selector: String, includeSelf: Boolean = false): LazyList[U] = {

      def fromDescendants =
        elem
          .querySelectorAllT(selector)
          .view
          .collect { case e: U => e }

      if (includeSelf)
        elem match {
          case e: U if e.matches(selector) => LazyList(e).lazyAppendedAll(fromDescendants)
          case _                           => fromDescendants.to(LazyList)
        }
      else
        fromDescendants.to(LazyList)
    }

    // This implements the jQuery way of determining visibility with the `:visible` pseudo-class
    def isVisible(implicit ev: T <:< html.Element): Boolean =
      elem.offsetWidth != 0 || elem.offsetHeight != 0 || elem.getClientRects().length != 0

    def getOffset: Offset = {
      val rect = elem.getBoundingClientRect()
      Offset(
        left = rect.left + dom.window.pageXOffset,
        top  = rect.top  + dom.window.pageYOffset,
      )
    }

    // Set an offset following the jQuery `offset()` function, which sets the position of the element relative to the
    // document, by adjusting its CSS `top` and `left` properties.
    def setOffset(offset: Offset)(implicit ev: T <:< html.Element): Unit = {

      val curStyle = dom.window.getComputedStyle(elem)
      val curPosition = curStyle.position

      // 1. If static, make it relative so it can be moved
      if (curPosition == "static")
        elem.style.position = "relative"

      // 2. Get current document-relative position
      val curOffset = getOffset

      // 3. Get current CSS top/left (handling `auto`)
      def findPosition(cssPos: String): Option[Double] =
        if ((curPosition == "absolute" || curPosition == "fixed") && cssPos == "auto")
          0d.some
        else
          parseDoubleIgnoreTail(cssPos)

      // 4. Calculate and apply the new values
      elem.style.top =  s"${(offset.top  - curOffset.top)  + findPosition(curStyle.top) .getOrElse(0d)}px"
      elem.style.left = s"${(offset.left - curOffset.left) + findPosition(curStyle.left).getOrElse(0d)}px"
    }

    def setWidth(targetWidth: Double)(implicit ev: T <:< html.Element): Unit = {
      val style = dom.window.getComputedStyle(elem)
      if (style.boxSizing == "border-box") {
        val paddingLeft  = parseDoubleIgnoreTail(style.paddingLeft).getOrElse(0d)
        val paddingRight = parseDoubleIgnoreTail(style.paddingRight).getOrElse(0d)
        val borderLeft   = parseDoubleIgnoreTail(style.borderLeftWidth).getOrElse(0d)
        val borderRight  = parseDoubleIgnoreTail(style.borderRightWidth).getOrElse(0d)
        elem.style.width = s"${targetWidth + paddingLeft + paddingRight + borderLeft + borderRight}px"
      } else {
        elem.style.width = s"${targetWidth}px"
      }
    }

    def setHeight(targetHeight: Double)(implicit ev: T <:< html.Element): Unit = {
      val style = dom.window.getComputedStyle(elem)
      if (style.boxSizing == "border-box") {
        val paddingTop    = parseDoubleIgnoreTail(style.paddingTop).getOrElse(0d)
        val paddingBottom = parseDoubleIgnoreTail(style.paddingBottom).getOrElse(0d)
        val borderTop     = parseDoubleIgnoreTail(style.borderTopWidth).getOrElse(0d)
        val borderBottom  = parseDoubleIgnoreTail(style.borderBottomWidth).getOrElse(0d)
        elem.style.height = s"${targetHeight + paddingTop + paddingBottom + borderTop + borderBottom}px"
      } else {
        elem.style.height = s"${targetHeight}px"
      }
    }

    def setOuterWidth(targetWidth: Double)(implicit ev: T <:< html.Element): Unit = {
      val style = dom.window.getComputedStyle(elem)
      if (style.boxSizing == "border-box") {
        elem.style.width = s"${targetWidth}px"
      } else {
        val paddingLeft  = parseDoubleIgnoreTail(style.paddingLeft).getOrElse(0d)
        val paddingRight = parseDoubleIgnoreTail(style.paddingRight).getOrElse(0d)
        val borderLeft   = parseDoubleIgnoreTail(style.borderLeftWidth).getOrElse(0d)
        val borderRight  = parseDoubleIgnoreTail(style.borderRightWidth).getOrElse(0d)
        elem.style.width = s"${targetWidth - paddingLeft - paddingRight - borderLeft - borderRight}px"
      }
    }

    def setOuterHeight(targetHeight: Double)(implicit ev: T <:< html.Element): Unit = {
      val style = dom.window.getComputedStyle(elem)
      if (style.boxSizing == "border-box") {
        elem.style.height = s"${targetHeight}px"
      } else {
        val paddingTop    = parseDoubleIgnoreTail(style.paddingTop).getOrElse(0d)
        val paddingBottom = parseDoubleIgnoreTail(style.paddingBottom).getOrElse(0d)
        val borderTop     = parseDoubleIgnoreTail(style.borderTopWidth).getOrElse(0d)
        val borderBottom  = parseDoubleIgnoreTail(style.borderBottomWidth).getOrElse(0d)
        elem.style.height = s"${targetHeight - paddingTop - paddingBottom - borderTop - borderBottom}px"
      }
    }

    def toggleClass(clazz: String, add: Boolean): Unit =
      if (add)
        elem.classList.add(clazz)
      else
        elem.classList.remove(clazz)

    def show()(implicit ev: T <:< html.Element): Unit =
      elem.style.display = "block"

    def hide()(implicit ev: T <:< html.Element): Unit =
      elem.style.display = "none"

    def contentWidth: Option[Double] =
      parseDoubleIgnoreTail(dom.window.getComputedStyle(elem).width)

    def contentWidthOrZero: Double =
      contentWidth.getOrElse(0d)

    def contentHeight: Option[Double] =
      parseDoubleIgnoreTail(dom.window.getComputedStyle(elem).height)

    def contentHeightOrZero: Double =
      contentHeight.getOrElse(0d)

    // Includes content + padding + border, should be similar to jQuery's `outerWidth()`
    def outerWidth: Double =
      elem.getBoundingClientRect().width

    // Includes content + padding + border, should be similar to jQuery's `outerHeight()`
    def outerHeight: Double =
      elem.getBoundingClientRect().height
  }

  // `parseFloat()` ignores trailing "px" and returns a `NaN` if the value is not parseable as a number
  def parseDoubleIgnoreTail(s: String): Option[Double] = {
    val doubleMaybeNaN = dom.window.asInstanceOf[js.Dynamic].parseFloat(s).asInstanceOf[Double]
    (! doubleMaybeNaN.isNaN).option(doubleMaybeNaN)
  }

  implicit class DomDocOps(private val doc: html.Document) extends AnyVal {

    def documentElementT: html.Element =
      doc.documentElement.asInstanceOf[html.Element]

    def activeElementOpt: Option[html.Element] =
      Option(doc.activeElement.asInstanceOf[html.Element])

    def getElementByIdT(elementId: String): html.Element =
      doc.getElementById(elementId).asInstanceOf[html.Element]

    def getElementByIdOpt(elementId: String): Option[html.Element] =
      Option(doc.getElementById(elementId).asInstanceOf[html.Element])

    def createElementT(tagName: String): html.Element =
      doc.createElement(tagName).asInstanceOf[html.Element]

    def querySelectorAllT(selectors: String): collection.Seq[html.Element] =
      doc.querySelectorAll(selectors).asInstanceOf[dom.NodeList[html.Element]]

    def querySelectorT(selectors: String): html.Element =
      doc.querySelector(selectors).asInstanceOf[html.Element]

    def querySelectorOpt(selectors: String): Option[html.Element] =
      Option(querySelectorT(selectors))

    def createScriptElement: html.Script =
      doc.createElement("script").asInstanceOf[html.Script]

    def createLinkElement: html.Link =
      doc.createElement("link").asInstanceOf[html.Link]

    def createFormElement: html.Form =
      doc.createElement("form").asInstanceOf[html.Form]

    def createInputElement: html.Input =
      doc.createElement("input").asInstanceOf[html.Input]

    def createOptionElement: html.Option =
      doc.createElement("option").asInstanceOf[html.Option]

    def createOptGroupElement: html.OptGroup =
      doc.createElement("optgroup").asInstanceOf[html.OptGroup]

    def createButtonElement: html.Button =
      doc.createElement("button").asInstanceOf[html.Button]

    def createDivElement: html.Div =
      doc.createElement("div").asInstanceOf[html.Div]
  }

  trait DateTimeFormatPart extends js.Object {
    val `type`: String
    val value : String
  }

  implicit class DateTimeFormatOps(private val dateTimeFormat: dom.intl.DateTimeFormat) extends AnyVal {

    def formatToParts(date: js.Date): js.Array[DateTimeFormatPart] =
      dateTimeFormat.asInstanceOf[js.Dynamic].formatToParts(date).asInstanceOf[js.Array[DateTimeFormatPart]]
  }

  implicit class DomEventOps(private val event: dom.Event) extends AnyVal {

    def targetT: html.Element =
      event.target.asInstanceOf[html.Element]

    def targetOpt: Option[html.Element] =
      Option(event.targetT)
  }

  private var lastUsedSuffix: Int = 0

  def shallowClone[T <: js.Object](obj: T): T = {
    val newObj = new js.Object()
    js.Object.assign(newObj, obj).asInstanceOf[T]
  }

  private val AtLeastDomInteractiveStates = Set(DocumentReadyState.interactive, DocumentReadyState.complete)
  private val DomCompleteStates           = Set(DocumentReadyState.complete)

  sealed trait DomReadyState
  case object DomReadyState {
    case object Interactive extends DomReadyState // doc parsed but scripts, images, stylesheets and frames are still loading
    case object Complete    extends DomReadyState // doc and all sub-resources have finished loading, `load` about to fire
  }

  private def interactiveReadyState(doc: html.Document, state: DomReadyState): Boolean =
    state == DomReadyState.Interactive && AtLeastDomInteractiveStates(doc.readyState) ||
    state == DomReadyState.Complete    && DomCompleteStates(doc.readyState)

  def atLeastDomReadyStateF(doc: html.Document, state: DomReadyState): Future[Unit] = {

    val promise = Promise[Unit]()

    if (interactiveReadyState(doc, state)) {

      // Because yes, even if the document is interactive, JavaScript placed after us might not have run yet.
      // Although if we do everything in an async way, that should be changed.
      // TODO: Review once full order of JavaScript is determined in `App` doc.
      js.timers.setTimeout(0) {
        promise.success(())
      }: Unit
    } else {

      lazy val readyStateChanged: js.Function1[dom.Event, ?] = (_: dom.Event) =>
        if (interactiveReadyState(doc, state)) {
          doc.removeEventListener(ReadystateChange, readyStateChanged)
          promise.success(())
        }

      doc.addEventListener(ReadystateChange, readyStateChanged)
    }

    promise.future
  }

  def findCommonAncestor(elems: List[html.Element]): Option[html.Element] = {

    def findFirstCommonAncestorForPair(elem1: html.Element, elem2: html.Element): Option[html.Element] =
      elem1.ancestorOrSelfElem(includeSelf = false).toList.reverseIterator
        .zip(elem2.ancestorOrSelfElem(includeSelf = false).toList.reverseIterator)
        .takeWhile { case (e1, e2) => e1.isSameNode(e2) }
        .lastOption()
        .map(_._1)

    @tailrec
    def recurse(elems: List[html.Element]): Option[html.Element] = {
      elems match {
        case Nil =>
          None
        case elem1 :: Nil =>
          Some(elem1)
        case elem1 :: elem2 :: rest =>
          findFirstCommonAncestorForPair(elem1, elem2) match {
            case Some(elem) => recurse(elem :: rest)
            case None       => None
          }
        case _ =>
          None
      }
    }

    recurse(elems)
  }

  def generateIdIfNeeded(element: dom.Element): String = {
    if (element.id == "") {
      def id(suffix: Int)       = s"xf-client-$suffix"
      def isUnused(suffix: Int) = document.getElementById(id(suffix)) == null
      val suffix                = Iterator.from(lastUsedSuffix + 1).find(isUnused).get
      element.id                = id(suffix)
      lastUsedSuffix            = suffix
    }
    element.id
  }

  def moveIntoViewIfNeeded(
    containerElem : html.Element,
    innerContainer: html.Element,
    itemElem      : html.Element,
    margin        : Int
  ): Unit = {
    val containerRect       = containerElem.getBoundingClientRect()
    val itemRect            = itemElem.getBoundingClientRect()
    val isEntirelyContained =
      itemRect.left   >= containerRect.left   &&
      itemRect.top    >= containerRect.top    &&
      itemRect.bottom <= containerRect.bottom &&
      itemRect.right  <= containerRect.right
    if (! isEntirelyContained) {

      val overflowsBelow = itemRect.bottom > containerRect.bottom

      val mainInnerRect = innerContainer.getBoundingClientRect()
      val scrollTop =
        if (overflowsBelow)
          containerRect.top - mainInnerRect.top + itemRect.bottom - containerRect.bottom + margin
        else
          containerRect.top - mainInnerRect.top - (containerRect.top - itemRect.top + margin)

      containerElem.asInstanceOf[js.Dynamic].scrollTo(
        js.Dynamic.literal(top = scrollTop, behavior = "smooth")
      )
    }
  }

  def onAttributeChange(
    element       : dom.Element,
    attributeName : String,
    listener      : () => Unit
  ): MutationObserver = {
    val observer = new MutationObserver((_, _) => listener())
    observer.observe(element, new MutationObserverInit {
      attributes      = true
      attributeFilter = js.Array(attributeName)
    })
    observer
  }

  def onElementFoundOrAdded(
    container : html.Element,
    selector  : String,
    listener  : html.Element => Unit
  ): MutationObserver = {
    container.querySelectorAllT(selector).foreach(listener)
    val observer = new MutationObserver((mutations, _) => {
      mutations.foreach { mutation =>
        mutation.addedNodes.foreach {
          case element: html.Element =>
            element.queryNestedElems(selector, includeSelf = true).foreach(listener)
          case _ =>
        }
      }
    })
    val config = new MutationObserverInit { childList = true ; subtree = true }
    observer.observe(container, config)
    observer
  }

  def replaceStateLogError(statedata: js.Any, title: String, url: String): Unit =
    try {
      dom.window.history.replaceState(statedata, title, url)
    } catch {
      case e: Throwable =>
        dom.console.log(s"error replacing state with url `$url`: ${e.getMessage}")
    }

  def dispatchChange(target: dom.EventTarget): Unit =
    target.dispatchEvent(
      new dom.Event("change", new dom.EventInit {
        bubbles    = true
        cancelable = true
      })
    )

  def dispatchCustomEvent(
    target: dom.EventTarget,
    name  : String
  ): Unit =
    target.dispatchEvent(
      new dom.CustomEvent(name, new dom.CustomEventInit {
        bubbles = true
      })
    )
}
