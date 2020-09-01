package org.orbeon.dom.tree

import java.{lang => jl, util => ju}

import org.orbeon.dom._
import org.xml.sax.Attributes

import scala.jdk.CollectionConverters._

private object ConcreteElement {

  val DefaultContentListSize = 2 // ORBEON: default was 5
  val XmlNamespace = Namespace("xml", "http://www.w3.org/XML/1998/namespace")

  def appendAttributes(src: Element, dst: Element): Unit = {

    val size = src.attributeCount

    // `for (i <- 0 until size)` is inefficient and shows in the profiler
    var i = 0
    while (i < size) {
      val att = src.attribute(i)
      dst.add(att.deepCopy.asInstanceOf[Attribute])
      i += 1
    }
  }
}

class ConcreteElement(var qname: QName)
  extends AbstractBranch with Element with WithData {

  import ConcreteElement._

  def this(name: String) =
    this(QName(name))

  def getQName: QName = qname

  protected var _attributes     = new ju.ArrayList[Attribute](DefaultContentListSize)
  private var _internalContent  = new ju.ArrayList[Node](DefaultContentListSize)
  protected def internalContent = _internalContent

  def jContent = new ContentListFacade[Node](this, internalContent)

  /**
   * Stores the parent branch of this node which is either a Document if this
   * element is the root element in a document, or another Element if it is a
   * child of the root document, or null if it has not been added to a
   * document yet.
   */
  private var _parentBranch: Branch = _

  override def getParent: Element =
    _parentBranch match {
      case element: Element => element
      case _                => null
    }

  override def setParent(parent: Element): Unit = {

    // 2019-05-16: Not sure that this test does: in which case would we *not* set `_parentBranch`?
    if (_parentBranch.isInstanceOf[Element] || (parent ne null))
      _parentBranch = parent

    if (parent ne null) {
      // Check with ancestors and removes any redundant namespace declarations

      val nyNamespace = getNamespace
      if (nyNamespace != Namespace.EmptyNamespace) {
        val myPrefix = nyNamespace.prefix
        val parentNamespace = parent.getNamespaceForPrefix(myPrefix)
        if (myPrefix == parentNamespace)// ORBEON TODO: comparing unrelated!
          this.qname = QName(nyNamespace.getName)
      }

      val contentIt = internalContent.iterator
      while (contentIt.hasNext) {
        contentIt.next() match {
          case ns: Namespace =>
            val prefix = ns.prefix
            val parentNamespace = parent.getNamespaceForPrefix(prefix)
            if (ns == parentNamespace)
              contentIt.remove()
          case _ =>
        }
      }
    }
  }

  override def getDocument: Document =
    _parentBranch match {
      case document: Document => document
      case parent: Element    => parent.getDocument
      case _                  => null
    }

  override def setDocument(document: Document): Unit =
    if (_parentBranch.isInstanceOf[Document] || (document ne null))
      _parentBranch = document


  def isRootElement: Boolean = {
    val document = getDocument
    if (document ne null) {
      val root = document.getRootElement
      if (root == this) {
        return true
      }
    }
    false
  }

  def accept(visitor: Visitor): Unit = {
    visitor.visit(this)
    for (i <- 0 until attributeCount)
      visitor.visit(attribute(i))

    for (i <- 0 until nodeCount)
      node(i).accept(visitor)
  }

  override def toString: String = {
    val uri = getNamespaceURI
    val result =
      if ((uri ne null) && (uri.length > 0)) {
        super.toString + " [Element: <" + getQualifiedName + " uri: " + uri + " attributes: " + _attributes + "/>]"
      } else {
        super.toString + " [Element: <" + getQualifiedName + " attributes: " + _attributes + "/>]"
      }
     result + " userData: " + getData
  }

  override def getName   : String    = getQName.localName

  def getNamespace       : Namespace = getQName.namespace
  def getNamespacePrefix : String    = getQName.namespace.prefix
  def getNamespaceURI    : String    = getQName.namespace.uri
  def getQualifiedName   : String    = getQName.qualifiedName

  override def node(index: Int): Node = {
    if (index >= 0) {
      val list = internalContent
      if (index >= list.size) {
        return null
      }
      val node = list.get(index)
      if (node ne null) {
        return node
      }
    }
    null
  }

  override def containsElement: Boolean = {
    internalContent.iterator.asScala exists {
      case _: Element => true
      case _          => false
    }
  }

  def element(name: String): Element = {
    val list = internalContent
    val size = list.size

    // `for (i <- 0 until size)` is inefficient and shows in the profiler
    var i = 0
    while (i < size) {
      list.get(i) match {
        case element: Element if name == element.getName => return element
        case _ =>
      }
      i +=1
    }

    null
  }

  def element(qName: QName): Element = {
    val list = internalContent
    val size = list.size

    // `for (i <- 0 until size)` is inefficient and shows in the profiler
    var i = 0
    while (i < size) {
      list.get(i) match {
        case element: Element if qName == element.getQName => return element
        case _ =>
      }
      i +=1
    }
    null
  }

  def jElements: ju.List[Element] = {
    val list = internalContent
    val answer = new ju.ArrayList[Element]()
    val size = list.size

    // `for (i <- 0 until size)` is inefficient and shows in the profiler
    var i = 0
    while (i < size) {
      list.get(i) match {
        case element: Element => answer.add(element)
        case _ =>
      }
      i += 1
    }
    ju.Collections.unmodifiableList(answer)
  }

  def jElements(name: String): ju.List[Element] = {
    val list = internalContent
    val answer = new ju.ArrayList[Element]()
    val size = list.size

    // `for (i <- 0 until size)` is inefficient and shows in the profiler
    var i = 0
    while (i < size) {
      list.get(i) match {
        case element: Element =>
          if (name == element.getName)
            answer.add(element)
        case _ =>
      }
      i += 1
    }
    ju.Collections.unmodifiableList(answer)
  }

  def jElements(qName: QName): ju.List[Element] = {
    val list = internalContent
    val answer = new ju.ArrayList[Element]()
    val size = list.size

    // `for (i <- 0 until size)` is inefficient and shows in the profiler
    var i = 0
    while (i < size) {
      list.get(i) match {
        case element: Element =>
          if (qName == element.getQName)
            answer.add(element)
        case _ =>
      }
      i += 1
    }
    ju.Collections.unmodifiableList(answer)
  }

  def elements(name: String, namespace: Namespace): ju.List[Element] = {
    jElements(QName(name, namespace))
  }

  //  FIXME: These all make copies of the content. But some callers rely on this to prevent concurrent changes.
  def jElementIterator(): ju.Iterator[Element] = jElements.iterator()
  def jElementIterator(name: String): ju.Iterator[Element] = jElements(name).iterator()

  def jAttributes: ju.List[Attribute] =
    new ContentListFacade[Attribute](this, _attributes)

  def jAttributeIterator: ju.Iterator[Attribute] = _attributes.iterator()

  def attribute(index: Int): Attribute = _attributes.get(index)
  def attributeCount: Int = _attributes.size

  def attribute(name: String): Attribute = {

    val list = _attributes

    var i = 0
    val length = list.size()
    while (i < length) {
      val attribute = list.get(i)
      if (name == attribute.getName)
        return attribute
      else
        i += 1
    }

    null
  }

  def attribute(qName: QName): Attribute = {

    val list = _attributes

    var i = 0
    val length = list.size()
    while (i < length) {
      val attribute = list.get(i)
      if (qName == attribute.getQName)
        return attribute
      else
        i += 1
    }

    null
  }

  def attribute(name: String, namespace: Namespace): Attribute = {
    attribute(QName(name, namespace))
  }

  def setAttributes(attributes: Attributes, namespaceStack: NamespaceStack, noNamespaceAttributes: Boolean): Unit = {
    val size = attributes.getLength
    if (size > 0) {
      if (size == 1) {
        val name = attributes.getQName(0)
        if (noNamespaceAttributes || !name.startsWith("xmlns")) {
          val attributeURI = attributes.getURI(0)
          val attributeLocalName = attributes.getLocalName(0)
          val attributeValue = attributes.getValue(0)
          val attributeQName = namespaceStack.getAttributeQName(attributeURI, attributeLocalName, name)
          add(Attribute(attributeQName, attributeValue))
        }
      } else {
        val list = _attributes
        list.clear()
        // `for (i <- 0 until size)` is inefficient and shows in the profiler
        var i = 0
        while (i < size) {
          val attributeName = attributes.getQName(i)
          if (noNamespaceAttributes || !attributeName.startsWith("xmlns")) {
            val attributeURI = attributes.getURI(i)
            val attributeLocalName = attributes.getLocalName(i)
            val attributeValue = attributes.getValue(i)
            val attributeQName = namespaceStack.getAttributeQName(attributeURI, attributeLocalName, attributeName)
            val attribute = Attribute(attributeQName, attributeValue)
            list.add(attribute)
            childAdded(attribute)
          }
          i += 1
        }
      }
    }
  }

  def attributeValue(name: String): String = {
    val attrib = attribute(name)
    if (attrib eq null) {
      null
    } else {
      attrib.getValue
    }
  }

  def attributeValue(qName: QName): String = {
    val attrib = attribute(qName)
    if (attrib eq null) {
      null
    } else {
      attrib.getValue
    }
  }

  def add(att: Attribute): Unit = {
    if (att.getParent ne null) {
      val message = "The Attribute already has an existing parent \"" + att.getParent.getQualifiedName + "\""
      throw new IllegalAddException(this, att, message)
    }
    if (att.getValue eq null) {
      val oldAttribute = attribute(att.getQName)
      if (oldAttribute ne null) {
        remove(oldAttribute)
      }
    } else {
      _attributes.add(att)
      childAdded(att)
    }
  }

  def remove(att: Attribute): Boolean = {
    val list = _attributes
    var answer = list.remove(att)
    if (answer) {
      childRemoved(att)
    } else {
      val copy = attribute(att.getQName)
      if (copy ne null) {
        list.remove(copy)
        answer = true
      }
    }
    answer
  }

  def processingInstruction(target: String): ProcessingInstruction = {
    val list = internalContent
    val size = list.size

    // `for (i <- 0 until size)` is inefficient and shows in the profiler
    var i = 0
    while (i < size) {
      list.get(i) match {
        case pi: ProcessingInstruction =>
          if (target == pi.getName)
            return pi
        case _ =>
      }
      i += 1
    }
    null
  }

  def addAttribute(name: String, value: String): Element = {
    val att = attribute(name)
    if (att eq null) {
      add(Attribute(name, value))
    } else {
      att.setValue(value)
    }
    this
  }

  def addAttribute(qName: QName, value: String): Element = {
    val att = attribute(qName)
    if (att eq null) {
      add(Attribute(qName, value))
    } else {
      att.setValue(value)
    }
    this
  }

  def removeAttribute(qName: QName): Element = {
    val att = attribute(qName)
    if (att ne null)
      remove(att)
    this
  }

  override def addElement(name: String): Element = {
    val index = name.indexOf(":")
    var prefix = ""
    var localName = name
    var namespace: Namespace = null
    if (index > 0) {
      prefix = name.substring(0, index)
      localName = name.substring(index + 1)
      namespace = getNamespaceForPrefix(prefix)
      if (namespace eq null) {
        throw new IllegalAddException("No such namespace prefix: " + prefix + " is in scope on: " +
          this +
          " so cannot add element: " +
          name)
      }
    } else {
      namespace = getNamespaceForPrefix("")
    }
    val node =
      if (namespace ne null) {
        Element(QName(localName, namespace))
      } else {
        Element(name)
      }
    addNewNode(node)
    node
  }

  def addNamespace(prefix: String, uri: String): Element = {
    val node = Namespace(prefix, uri)
    addNewNode(node)
    this
  }

  def addText(text: String): Element = {
    val node = Text(text)
    addNewNode(node)
    this
  }

  override def add(node: Node): Unit = node match {
    case n: Attribute => add(n)
    case n: Text      => add(n)
    case n: Namespace => add(n)
    case n            => super.add(n)
  }

  override def remove(node: Node): Boolean = node match {
    case n: Attribute => remove(n)
    case n: Text      => remove(n)
    case n: Namespace => remove(n)
    case n            => super.remove(n)
  }

  def add(namespace: Namespace)    : Unit    = addNode(namespace)
  def add(text: Text)              : Unit    = addNode(text)

  def remove(namespace: Namespace) : Boolean = removeNode(namespace)
  def remove(text: Text)           : Boolean = removeNode(text)

  override def setText(text: String): Unit = {
    val allContent = internalContent
    if (allContent ne null) { // TODO: can this ever be null?
      val it = allContent.iterator()
      while (it.hasNext) {
        it.next() match {
          case _: Text => it.remove()
          case _       =>
        }
      }
    }
    addText(text)
  }

  override def getStringValue: String = {

    def getContentAsStringValue(content: Node): String =
      content match {
        case _: Text | _: Element => content.getStringValue
        case _ => ""
      }

    val list = internalContent
    val size = list.size
    if (size > 0) {
      if (size == 1) {
        getContentAsStringValue(list.get(0))
      } else {
        val buffer = new jl.StringBuilder

        // `for (i <- 0 until size)` is inefficient and shows in the profiler
        var i = 0
        while (i < size) {
          val node = list.get(i)
          val string = getContentAsStringValue(node)
          if (string.length > 0)
            buffer.append(string)
          i += 1
        }
        buffer.toString
      }
    } else {
      ""
    }
  }

  def normalize(): Unit = {
    val list = internalContent
    var previousText: Text = null
    var i = 0
    while (i < list.size) {
      val node = list.get(i)
      node match {
        case text: Text =>
          if (previousText ne null) {
            previousText.setText(previousText.getText + text.getText)
            remove(text)
          } else {
            val value = text.getText
            if ((value eq null) || (value.length <= 0)) {
              remove(text)
            } else {
              previousText = text
              i += 1
            }
          }
        case _ =>
          node match {
            case element: Element =>
              element.normalize()
            case _ =>
          }
          previousText = null
          i += 1
      }
    }
  }

  // Doesn't try to grab namespace declarations from ancestors.
  private def cloneInternal: ConcreteElement = {
    val clone = super.deepCopy.asInstanceOf[ConcreteElement]
    if (clone ne this) {
      clone._internalContent = new ju.ArrayList[Node](DefaultContentListSize)
      clone._attributes      = new ju.ArrayList[Attribute](DefaultContentListSize)
      appendAttributes(this, clone)
      clone.appendContent(this)
      clone.setData(getData)
    }
    clone
  }

  override def appendContent(branch: Branch): Unit = {

    val size = branch.nodeCount

    // `for (i <- 0 until size)` is inefficient and shows in the profiler
    var i = 0
    while (i < size) {
      val clone =
        branch.node(i) match {
          case elem: ConcreteElement => elem.cloneInternal
          case node                  => node.deepCopy
        }
      add(clone: Node)
      i += 1
    }
  }

  // The clone will have parent eq null but will have any necessary namespace declarations this element's ancestors.
  override def deepCopy: Element = {

    val clone = cloneInternal
    var ancestor = getParent

    if (ancestor ne null) {

      class NamespaceNodeComparator extends ju.Comparator[Namespace] {

        def compare(n1: Namespace, n2: Namespace): Int = {
          var answer = compare(n1.uri, n2.uri)
          if (answer == 0) {
            answer = compare(n1.prefix, n2.prefix)
          }
          answer
        }

        private def compare(s1: String, s2: String): Int = {
          if (s1 == s2) {
            return 0
          } else if (s1 eq null) {
            return -1
          } else if (s2 eq null) {
            return 1
          }
          s1.compareTo(s2)
        }
      }

      // Set of namespaces ordered by URI then prefix
      val namespaceSet = new ju.TreeSet[Namespace](new NamespaceNodeComparator)

      // Add all unique namespaces prefix/URI to the set
      do {
        val ancestorContentIt = ancestor.nodeIterator
        while (ancestorContentIt.hasNext) {
          // ORBEON TODO: This is wrong if there are more than one mapping for a prefix in the ancestor chain, because the one closer from the root wins, and it should be the opposite.
          ancestorContentIt.next() match {
            case namespace: Namespace => namespaceSet.add(namespace)
            case _ =>
          }
        }
        ancestor = ancestor.getParent
      } while (ancestor ne null)

      // Add all non-existing mappings to the cloned element
      val it = namespaceSet.iterator
      while (it.hasNext) {
        val ns = it.next()
        val prefix = ns.prefix
        if (clone.getNamespaceForPrefix(prefix) eq null)
          clone.add(ns)
      }
    }
    clone
  }

  override def createCopy: Element = {
    val clone = Element(getQName)
    appendAttributes(this, clone)
    clone.appendContent(this)
    clone.setData(getData)
    clone
  }

  def getQName(qualifiedName: String): QName = {
    var prefix = ""
    var localName = qualifiedName
    val index = qualifiedName.indexOf(":")
    if (index > 0) {
      prefix = qualifiedName.substring(0, index)
      localName = qualifiedName.substring(index + 1)
    }
    val namespace = getNamespaceForPrefix(prefix)
    if (namespace ne null) {
      QName(localName, namespace)
    } else {
      QName(localName)
    }
  }

  def getNamespaceForPrefix(_prefix: String): Namespace = {

    val prefix = if (_prefix eq null) "" else _prefix

    if (prefix == getNamespacePrefix) {
      return getNamespace
    } else if (prefix == "xml") {
      return Namespace.XMLNamespace
    } else {
      val list = internalContent
      val size = list.size

      // `for (i <- 0 until size)` is inefficient and shows in the profiler
      var i = 0
      while (i < size) {
        list.get(i) match {
          case namespace: Namespace if prefix == namespace.prefix => return namespace
          case _ =>
        }
        i+= 1
      }
    }
    val parent = getParent
    if (parent ne null) {
      val answer = parent.getNamespaceForPrefix(prefix)
      if (answer ne null) {
        return answer
      }
    }
    if ((prefix eq null) || (prefix.length <= 0)) {
      return Namespace.EmptyNamespace
    }
    null
  }

  def declaredNamespacesIterator: Iterator[Namespace] =
    internalContent.iterator().asScala collect {
      case ns: Namespace => ns
    }

  private def allInScopeNamespacesAs[T](map: Namespace => T): Map[String, T] = {

    var result = Map[String, T]()

    var currentElem: Element = this

    while (currentElem ne null) {
      for (namespace <- currentElem.declaredNamespacesIterator) {
        if (! result.contains(namespace.prefix))
          result += namespace.prefix -> map(namespace)
      }
      currentElem = currentElem.getParent
    }

    // It seems that by default this may not be declared. However, it should be: "The prefix xml is by definition
    // bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared, and MUST
    // NOT be bound to any other namespace name. Other prefixes MUST NOT be bound to this namespace name, and it
    // MUST NOT be declared as the default namespace."
    result += XmlNamespace.prefix -> map(XmlNamespace)
    result
  }

  def allInScopeNamespacesAsNodes: Map[String, Namespace] =
    allInScopeNamespacesAs(identity)

  def allInScopeNamespacesAsStrings: Map[String, String] =
    allInScopeNamespacesAs(_.uri)

  def add(element: Element)    : Unit    = addNode(element)
  def remove(element: Element) : Boolean = removeNode(element)

  protected def addNode(node: Node): Unit = {
    if (node.getParent ne null) {
      val message = "The Node already has an existing parent of \"" + node.getParent.getQualifiedName + "\""
      throw new IllegalAddException(this, node, message)
    }
    addNewNode(node)
  }

  protected def addNode(index: Int, node: Node): Unit = {
    if (node.getParent ne null) {
      val message = "The Node already has an existing parent of \"" + node.getParent.getQualifiedName + "\""
      throw new IllegalAddException(this, node, message)
    }
    addNewNode(index, node)
  }

  /**
   * Like addNode() but does not require a parent check
   */
  private def addNewNode(node: Node): Unit = {
    internalContent.add(node)
    childAdded(node)
  }

  private def addNewNode(index: Int, node: Node): Unit = {
    internalContent.add(index, node)
    childAdded(node)
  }

  protected def removeNode(node: Node): Boolean = {
    val answer = internalContent.remove(node)
    if (answer)
      childRemoved(node)
    answer
  }

  // 1 external usage
  def clearContent(): Unit = {

    val it = internalContent.iterator.asScala filter (_.isInstanceOf[Namespace])

    if (it.hasNext) {
      _internalContent = new ju.ArrayList[Node](DefaultContentListSize)
      while (it.hasNext)
        _internalContent.add(it.next())
    } else {
      internalContent.clear()
    }
  }

  /**
   * Called when a new child node is added to create any parent relationships
   */
  protected[dom] def childAdded(node: Node): Unit =
    if (node ne null)
      node.setParent(this)

  protected[dom] def childRemoved(node: Node): Unit =
    if (node ne null) {
      node.setParent(null)
      node.setDocument(null)
    }
}
