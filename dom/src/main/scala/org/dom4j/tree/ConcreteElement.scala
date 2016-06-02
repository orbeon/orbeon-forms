package org.dom4j.tree

import java.{lang ⇒ jl, util ⇒ ju}

import org.dom4j.Node._
import org.dom4j._
import org.xml.sax.Attributes

private object ConcreteElement {

  val DefaultContentListSize = 2 // ORBEON: default was 5

  def appendAttributes(src: Element, dst: Element): Unit = {
    for (i ← 0 until src.attributeCount) {
      val att = src.attribute(i)
      dst.add(att.clone().asInstanceOf[Attribute])
    }
  }
}

/**
  * 4/7/2005 d : Under JDK 1.5 the fact that dom4j isn't thread safe by default became apparent.
  * In particular DefaultElement ( and sub-classes thereof ) are not thread safe because of the
  * following :
  *
  * - DefaultElement has a single field, private Object content, by which it refers to all of its
  *   child nodes.  If there is a single child node then content points to it.  If there are more
  *   then content points to a java.util.List which in turns points to all the children.
  * - However, if you do almost anything with an instance of DefaultElement, i.e. iterate over
  *   children, it will first create and fill a list before completing the operation.  This even
  *   if there was only a single child.
  *   The consequence of the above is that DefaultElement and its sub-classes aren't thread safe,
  *   even if all of the threads are just readers.
  *
  * The 'usual' solution is to use dom4j's NonLazyElement and NonLazyElementDocumentFactory.
  * However in our case we were using a sub-class of DefaultElement, UserDataElement, whose
  * functionality is unmatched by NonLazyElement.  Hence this class, a subclass of NonLazyElement
  * with the safe functionality as UserDataElement.
  *
  * Btw ConcreteElement also tries to be smart wrt to cloning and parent specifying.  That
  * is, if you clone the clone will have parent eq null but will have all of the requisite
  * namespace declarations and if you setParent( notNull ) then any redundant namespace declarations
  * are removed.
  */
class ConcreteElement(var qname: QName)
  extends AbstractBranch with Element with WithData{

  import ConcreteElement._

  def this(name: String) =
    this(DocumentFactory.createQName(name))

  def getQName: QName = qname
  def setQName(name: QName) = this.qname = name

  protected var _attributes     = new ju.ArrayList[Attribute](DefaultContentListSize)
  private var _internalContent  = new ju.ArrayList[Node](DefaultContentListSize)
  protected def internalContent = _internalContent

  def content = new ContentListFacade[Node](this, internalContent)

  override def getNodeType: Short = Node.ELEMENT_NODE

  /**
   * Stores the parent branch of this node which is either a Document if this
   * element is the root element in a document, or another Element if it is a
   * child of the root document, or null if it has not been added to a
   * document yet.
   */
  private var _parentBranch: Branch = _

  override def getParent: Element =
    _parentBranch match {
      case element: Element ⇒ element
      case _                ⇒ null
    }

  override def setParent(parent: Element): Unit = {

    if (_parentBranch.isInstanceOf[Element] || (parent ne null))
      _parentBranch = parent

    if (parent ne null) {
      // Check with ancestors and removes any redundant namespace declarations

      val nyNamespace = getNamespace
      if (nyNamespace != Namespace.EmptyNamespace) {
        val myPrefix = nyNamespace.getPrefix
        val parentNamespace = parent.getNamespaceForPrefix(myPrefix)
        if (myPrefix == parentNamespace) { // ORBEON TODO: comparing unrelated!
          val myNm = nyNamespace.getName
          val newNm = new QName(myNm)
          setQName(newNm)
        }
      }

      val contentIt = internalContent.iterator
      while (contentIt.hasNext) {
        contentIt.next() match {
          case ns: Namespace ⇒
            val prefix = ns.getPrefix
            val parentNamespace = parent.getNamespaceForPrefix(prefix)
            if (ns == parentNamespace)
              contentIt.remove()
          case _ ⇒
        }
      }
    }
  }

  override def getDocument: Document =
    _parentBranch match {
      case document: Document ⇒ document
      case parent: Element    ⇒ parent.getDocument
      case _                  ⇒ null
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

  override def setName(name: String): Unit = {
    setQName(DocumentFactory.createQName(name))
  }

  def setNamespace(namespace: Namespace): Unit = {
    setQName(DocumentFactory.createQName(getName, namespace))
  }

  def accept(visitor: Visitor): Unit = {
    visitor.visit(this)
    for (i ← 0 until attributeCount)
      visitor.visit(attribute(i))

    for (i ← 0 until nodeCount)
      node(i).accept(visitor)
  }

  override def toString: String = {
    val uri = getNamespaceURI
    val result =
      if ((uri ne null) && (uri.length > 0)) {
        super.toString + " [Element: <" + getQualifiedName + " uri: " + uri + " attributes: " + attributeList + "/>]"
      } else {
        super.toString + " [Element: <" + getQualifiedName + " attributes: " + attributeList + "/>]"
      }
     result + " userData: " + getData
  }

  override def getName   : String    = getQName.getName

  def getNamespace       : Namespace = getQName.getNamespace
  def getNamespacePrefix : String    = getQName.getNamespacePrefix
  def getNamespaceURI    : String    = getQName.getNamespaceURI
  def getQualifiedName   : String    = getQName.getQualifiedName

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

  def element(name: String): Element = {
    val list = internalContent
    val size = list.size
    for (i ← 0 until size) {
      list.get(i) match {
        case element: Element if name == element.getName ⇒ return element
        case _ ⇒
      }
    }
    null
  }

  def element(qName: QName): Element = {
    val list = internalContent
    val size = list.size
    for (i ← 0 until size) {
      list.get(i) match {
        case element: Element if qName == element.getQName ⇒ return element
        case _ ⇒
      }
    }
    null
  }

  def element(name: String, namespace: Namespace): Element =
    element(DocumentFactory.createQName(name, namespace))

  def elements: ju.List[Element] = {
    val list = internalContent
    val answer = new ju.ArrayList[Element]()
    val size = list.size
    for (i ← 0 until size) {
      list.get(i) match {
        case element: Element ⇒
          answer.add(element)
        case _ ⇒
      }
    }
    ju.Collections.unmodifiableList(answer)
  }

  def elements(name: String): ju.List[Element] = {
    val list = internalContent
    val answer = new ju.ArrayList[Element]()
    val size = list.size
    for (i ← 0 until size) {
      list.get(i) match {
        case element: Element ⇒
          if (name == element.getName) {
            answer.add(element)
          }
        case _ ⇒
      }
    }
    ju.Collections.unmodifiableList(answer)
  }

  def elements(qName: QName): ju.List[Element] = {
    val list = internalContent
    val answer = new ju.ArrayList[Element]()
    val size = list.size
    for (i ← 0 until size) {
      list.get(i) match {
        case element: Element ⇒
          if (qName == element.getQName) {
            answer.add(element)
          }
        case _ ⇒
      }
    }
    ju.Collections.unmodifiableList(answer)
  }

  def elements(name: String, namespace: Namespace): ju.List[Element] = {
    elements(DocumentFactory.createQName(name, namespace))
  }

  def elementIterator(): ju.Iterator[Element] = elements.iterator()
  def elementIterator(name: String): ju.Iterator[Element] = elements(name).iterator()
  def elementIterator(qName: QName): ju.Iterator[Element] = elements(qName).iterator()

  def attributes: ju.List[Attribute] = {
    new ContentListFacade[Attribute](this, attributeList)
  }

  def attributeIterator: ju.Iterator[Attribute] = attributeList.iterator()

  def attribute(index: Int): Attribute = attributeList.get(index)
  def attributeCount: Int = attributeList.size

  def attribute(name: String): Attribute = {
    val list = attributeList
    val size = list.size
    for (i ← 0 until size) {
      val attribute = list.get(i)
      if (name == attribute.getName) {
        return attribute
      }
    }
    null
  }

  def attribute(qName: QName): Attribute = {
    val list = attributeList
    val size = list.size
    for (i ← 0 until size) {
      val attribute = list.get(i)
      if (qName == attribute.getQName) {
        return attribute
      }
    }
    null
  }

  def attribute(name: String, namespace: Namespace): Attribute = {
    attribute(DocumentFactory.createQName(name, namespace))
  }

  /**
   * This method provides a more optimal way of setting all the attributes on
   * an Element particularly for use in .
   */
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
          add(DocumentFactory.createAttribute(this, attributeQName, attributeValue))
        }
      } else {
        val list = attributeList(size)
        list.clear()
        for (i ← 0 until size) {
          val attributeName = attributes.getQName(i)
          if (noNamespaceAttributes || !attributeName.startsWith("xmlns")) {
            val attributeURI = attributes.getURI(i)
            val attributeLocalName = attributes.getLocalName(i)
            val attributeValue = attributes.getValue(i)
            val attributeQName = namespaceStack.getAttributeQName(attributeURI, attributeLocalName, attributeName)
            val attribute = DocumentFactory.createAttribute(this, attributeQName, attributeValue)
            list.add(attribute)
            childAdded(attribute)
          }
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

  def attributeValue(name: String, defaultValue: String): String = {
    val answer = attributeValue(name)
    if (answer ne null) answer else defaultValue
  }

  def attributeValue(qName: QName, defaultValue: String): String = {
    val answer = attributeValue(qName)
    if (answer ne null) answer else defaultValue
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
      attributeList.add(att)
      childAdded(att)
    }
  }

  def remove(att: Attribute): Boolean = {
    val list = attributeList
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
    for (i ← 0 until size) {
      list.get(i) match {
        case pi: ProcessingInstruction ⇒
          if (target == pi.getName) {
            return pi
          }
        case _ ⇒
      }
    }
    null
  }

  def addAttribute(name: String, value: String): Element = {
    val att = attribute(name)
    if (value ne null) {
      if (att eq null) {
        add(DocumentFactory.createAttribute(this, name, value))
      } else {
        att.setValue(value)
      }
    } else if (att ne null) {
      remove(att)
    }
    this
  }

  def addAttribute(qName: QName, value: String): Element = {
    val att = attribute(qName)
    if (value ne null) {
      if (att eq null) {
        add(DocumentFactory.createAttribute(this, qName, value))
      } else {
        att.setValue(value)
      }
    } else if (att ne null) {
      remove(att)
    }
    this
  }

  def addCDATA(cdata: String): Element = {
    val node = DocumentFactory.createCDATA(cdata)
    addNewNode(node)
    this
  }

  def addComment(comment: String): Element = {
    val node = DocumentFactory.createComment(comment)
    addNewNode(node)
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
    var node: Element = null
    if (namespace ne null) {
      val qname = DocumentFactory.createQName(localName, namespace)
      node = DocumentFactory.createElement(qname)
    } else {
      node = DocumentFactory.createElement(name)
    }
    addNewNode(node)
    node
  }

  def addEntity(name: String, text: String): Element = {
    val node = DocumentFactory.createEntity(name, text)
    addNewNode(node)
    this
  }

  def addNamespace(prefix: String, uri: String): Element = {
    val node = DocumentFactory.createNamespace(prefix, uri)
    addNewNode(node)
    this
  }

  def addProcessingInstruction(target: String, data: String): Element = {
    val node = DocumentFactory.createProcessingInstruction(target, data)
    addNewNode(node)
    this
  }

  def addText(text: String): Element = {
    val node = DocumentFactory.createText(text)
    addNewNode(node)
    this
  }

  override def add(node: Node) = node.getNodeType match {
    case ATTRIBUTE_NODE              ⇒ add(node.asInstanceOf[Attribute])
    case TEXT_NODE                   ⇒ add(node.asInstanceOf[Text])
    case CDATA_SECTION_NODE          ⇒ add(node.asInstanceOf[CDATA])
    case ENTITY_REFERENCE_NODE       ⇒ add(node.asInstanceOf[Entity])
    case NAMESPACE_NODE              ⇒ add(node.asInstanceOf[Namespace])
    case _                           ⇒ super.add(node)
  }

  override def remove(node: Node): Boolean = node.getNodeType match {
    case ATTRIBUTE_NODE              ⇒ remove(node.asInstanceOf[Attribute])
    case TEXT_NODE                   ⇒ remove(node.asInstanceOf[Text])
    case CDATA_SECTION_NODE          ⇒ remove(node.asInstanceOf[CDATA])
    case ENTITY_REFERENCE_NODE       ⇒ remove(node.asInstanceOf[Entity])
    case NAMESPACE_NODE              ⇒ remove(node.asInstanceOf[Namespace])
    case _                           ⇒ super.remove(node)
  }

  def add(cdata: CDATA)            : Unit    = addNode(cdata)
  def add(entity: Entity)          : Unit    = addNode(entity)
  def add(namespace: Namespace)    : Unit    = addNode(namespace)
  def add(text: Text)              : Unit    = addNode(text)

  def remove(cdata: CDATA)         : Boolean = removeNode(cdata)
  def remove(entity: Entity)       : Boolean = removeNode(entity)
  def remove(namespace: Namespace) : Boolean = removeNode(namespace)
  def remove(text: Text)           : Boolean = removeNode(text)

  override def setText(text: String): Unit = {
    val allContent = internalContent
    if (allContent ne null) {
      val it = allContent.iterator()
      while (it.hasNext) {
        it.next().getNodeType match {
          case CDATA_SECTION_NODE | ENTITY_REFERENCE_NODE | TEXT_NODE ⇒ it.remove()
          case _ ⇒
        }
      }
    }
    addText(text)
  }

  override def getStringValue: String = {
    val list = internalContent
    val size = list.size
    if (size > 0) {
      if (size == 1) {
        return getContentAsStringValue(list.get(0))
      } else {
        val buffer = new jl.StringBuilder
        for (i ← 0 until size) {
          val node = list.get(i)
          val string = getContentAsStringValue(node)
          if (string.length > 0) {
            buffer.append(string)
          }
        }
        return buffer.toString
      }
    }
    ""
  }

  /**
   * Puts all `Text` nodes in the full depth of the sub-tree
   * underneath this `Node`, including attribute nodes, into a
   * "normal" form where only structure (e.g., elements, comments, processing
   * instructions, CDATA sections, and entity references) separates
   * `Text` nodes, i.e., there are neither adjacent
   * `Text` nodes nor empty `Text` nodes. This can
   * be used to ensure that the DOM view of a document is the same as if it
   * were saved and re-loaded, and is useful when operations (such as XPointer
   * lookups) that depend on a particular document tree structure are to be
   * used.In cases where the document contains `CDATASections`,
   * the normalize operation alone may not be sufficient, since XPointers do
   * not differentiate between `Text` nodes and
   * `CDATASection` nodes.
   */
  def normalize(): Unit = {
    val list = internalContent
    var previousText: Text = null
    var i = 0
    while (i < list.size) {
      val node = list.get(i)
      node match {
        case text: Text ⇒
          if (previousText ne null) {
            previousText.appendText(text.getText)
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
        case _ ⇒
          node match {
            case element: Element ⇒
              element.normalize()
            case _ ⇒
          }
          previousText = null
          i += 1
      }
    }
  }

  // Doesn't try to grab namespace declarations from ancestors.
  private def cloneInternal: ConcreteElement = {
    val clone = super.clone().asInstanceOf[ConcreteElement]
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
    for (i ← 0 until branch.nodeCount) {
      val clone =
        branch.node(i) match {
          case elem: ConcreteElement ⇒ elem.cloneInternal
          case node                         ⇒ node.clone().asInstanceOf[Node]
        }
      add(clone: Node)
    }
  }

  // The clone will have parent eq null but will have any necessary namespace declarations this element's ancestors.
  override def clone(): AnyRef = {

    val clone = cloneInternal
    var ancestor = getParent

    if (ancestor ne null) {

      class NamespaceNodeComparator extends ju.Comparator[Namespace] {

        def compare(n1: Namespace, n2: Namespace): Int = {
          var answer = compare(n1.getURI, n2.getURI)
          if (answer == 0) {
            answer = compare(n1.getPrefix, n2.getPrefix)
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
            case namespace: Namespace ⇒ namespaceSet.add(namespace)
            case _ ⇒
          }
        }
        ancestor = ancestor.getParent
      } while (ancestor ne null)

      // Add all non-existing mappings to the cloned element
      val it = namespaceSet.iterator
      while (it.hasNext) {
        val ns = it.next()
        val prefix = ns.getPrefix
        if (clone.getNamespaceForPrefix(prefix) eq null)
          clone.add(ns)
      }
    }
    clone
  }

  def createCopy: Element = {
    val clone = DocumentFactory.createElement(getQName)
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
      DocumentFactory.createQName(localName, namespace)
    } else {
      DocumentFactory.createQName(localName)
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
      for (i ← 0 until size) {
        list.get(i) match {
          case namespace: Namespace ⇒
            if (prefix == namespace.getPrefix) {
              return namespace
            }
          case _ ⇒
        }
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

  def getNamespaceForURI(uri: String): Namespace = {
    if ((uri eq null) || (uri.length <= 0)) {
      Namespace.EmptyNamespace
    } else if (uri == getNamespaceURI) {
      getNamespace
    } else {
      val list = internalContent
      val size = list.size
      for (i ← 0 until size) {
        list.get(i) match {
          case namespace: Namespace ⇒
            if (uri == namespace.getURI) {
              return namespace
            }
          case _ ⇒
        }
      }
      null
    }
  }

  def additionalNamespaces: ju.List[Namespace] = {
    val list = internalContent
    val size = list.size
    val answer = new ju.ArrayList[Namespace]
    for (i ← 0 until size) {
      list.get(i) match {
        case namespace: Namespace if namespace != getNamespace ⇒
          answer.add(namespace)
        case _ ⇒
      }
    }
    answer
  }

  def declaredNamespaces: ju.List[Namespace] = {
    val answer = new ju.ArrayList[Namespace]()
    val list = internalContent
    val size = list.size
    for (i ← 0 until size) {
      list.get(i) match {
        case namespace: Namespace ⇒ answer.add(namespace)
        case _ ⇒
      }
    }
    answer
  }

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
      val message = "The Node already has an existing parent of \"" + node.getParent.getQualifiedName +
        "\""
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
    if (answer) {
      childRemoved(node)
    }
    answer
  }

  // TODO ORBEON review: this also clears namespaces, check usages!
  // 1 external usage
  def clearContent(): Unit =
    internalContent.clear()

  /**
   * Called when a new child node is added to create any parent relationships
   */
  protected[dom4j] def childAdded(node: Node): Unit = {
    if (node ne null) {
      node.setParent(this)
    }
  }

  protected[dom4j] def childRemoved(node: Node): Unit = {
    if (node ne null) {
      node.setParent(null)
      node.setDocument(null)
    }
  }

  private def attributeList: ju.List[Attribute] = _attributes
  private def attributeList(size: Int): ju.List[Attribute] = _attributes
}
