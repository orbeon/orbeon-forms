package org.orbeon.dom

import java.{util => ju}

import scala.jdk.CollectionConverters._

/**
 * `Element` interface defines an XML element. An element can have
 * declared namespaces, attributes, child nodes and textual content.
 */
trait Element extends Branch {

  def getQName: QName
  def getNamespace: Namespace
  def getQName(qualifiedName: String): QName
  def getNamespaceForPrefix(prefix: String): Namespace
  def getNamespaceForURI(uri: String): Namespace
  def getNamespacePrefix: String
  def getNamespaceURI: String
  def getQualifiedName: String

  /**
   * Returns any additional namespaces declarations for this element other
   * than namespace returned via the `getNamespace` method. If no
   * additional namespace declarations are present for this element then an
   * empty list will be returned.
   */
  def additionalNamespaces: ju.List[Namespace]

  /**
   * Returns all the namespaces declared by this element. If no namespaces are
   * declared for this element then an empty list will be returned.
   */
  def declaredNamespaces: ju.List[Namespace]

  /**
   * Adds the attribute value of the given local name. If an attribute already
   * exists for the given name it will be replaced.
   */
  def addAttribute(name: String, value: String): Element

  /**
   * Adds the attribute value of the given fully qualified name. If an
   * attribute already exists for the given name it will be replaced.
   */
  def addAttribute(qName: QName, value: String): Element
  def removeAttribute(qName: QName): Element

  def addNamespace(prefix: String, uri: String): Element
  def addText(text: String): Element

  def add(att: Attribute): Unit
  def add(text: Text): Unit
  def add(namespace: Namespace): Unit

  def remove(att: Attribute): Boolean
  def remove(namespace: Namespace): Boolean
  def remove(text: Text): Boolean

  /**
   * Returns the text value of this element without recursing through child
   * elements. This method iterates through all Text
   * nodes that this element contains and appends the text
   * values together.
   */
  def getText: String

  /**
   * @return the trimmed text value where whitespace is trimmed and normalised
   *         into single spaces. This method does not return null.
   */
  // TODO: review as trimming is ok, but normalization should follow standard semantic, and method renamed if kept
  def getTextTrim: String

  /**
   * Returns the XPath string-value of this node. The behaviour of this method
   * is defined in the XPath specification. This method returns the string-value of all the
   * contained Text and Element nodes all appended together.
   */
  def getStringValue: String

  def getData: AnyRef
  def setData(data: AnyRef): Unit

  /**
   *
   * Returns the instances this element contains as a backed
   * so that the attributes may be modified directly using the
   * interface. The `List` is backed by the
   * `Element` so that changes to the list are reflected in the
   * element and vice versa.
   */
  def jAttributes: ju.List[Attribute]
  def attributes: Seq[Attribute] = jAttributes.asScala

  def attributeCount: Int
  def jAttributeIterator: ju.Iterator[Attribute]
  def attributeIterator: Iterator[Attribute] = jAttributeIterator.asScala

  /**
   * @return the attribute at the specified index where index <= 0 and
   *         index > number of attributes or throws an
   *         IndexOutOfBoundsException if the index is not within the
   *         allowable range
   */
  def attribute(index: Int): Attribute

  /**
   * @return the attribute for the given local name in any namespace. If there
   *         are more than one attributes with the given local name in
   *         different namespaces then the first one is returned.
   */
  def attribute(name: String): Attribute
  def attributeOpt(name: String): Option[Attribute] = Option(attribute(name))

  /**
   * @return the attribute for the given fully qualified name or null if it
   *         could not be found.
   */
  def attribute(qName: QName): Attribute
  def attributeOpt(qName: QName): Option[Attribute] = Option(attribute(qName))

  /**
   * This returns the attribute value for the attribute with the given name
   * and any namespace or null if there is no such attribute or the empty
   * string if the attribute value is empty.
   */
  def attributeValue(name: String): String
  def attributeValueOpt(name: String): Option[String] = Option(attributeValue(name))

  /**
   *
   * This returns the attribute value for the attribute with the given fully
   * qualified name or null if there is no such attribute or the empty string
   * if the attribute value is empty.
   */
  def attributeValue(qName: QName): String
  def attributeValueOpt(qName: QName): Option[String] = Option(attributeValue(qName))

  def containsElement: Boolean

  /**
   * Returns the first element for the given local name and any namespace.
   */
  def element(name: String): Element
  def elementOpt(name: String): Option[Element] = Option(element(name))

  /**
   * Returns the first element for the given fully qualified name.
   */
  def element(qName: QName): Element
  def elementOpt(name: QName): Option[Element] = Option(element(name))

  /**
   * Returns the elements contained in this element. If this element does not
   * contain any elements then this method returns an empty list.
   */
  def jElements: ju.List[Element]
  def elements: Seq[Element] = jElements.asScala

  /**
   * Returns the elements contained in this element with the given local name
   * and any namespace. If no elements are found then this method returns an
   * empty list.
   */
  def jElements(name: String): ju.List[Element]
  def elements(name: String): Seq[Element] = jElements(name).asScala

  /**
   * Returns the elements contained in this element with the given fully
   * qualified name. If no elements are found then this method returns an
   * empty list.
   */
  def jElements(qName: QName): ju.List[Element]
  def elements(qName: QName): Seq[Element] = jElements(qName).asScala

  /**
   * Returns an iterator over all this elements child elements.
   */
  def jElementIterator: ju.Iterator[Element]
  def elementIterator(): Iterator[Element] = jElementIterator.asScala

  /**
   * Returns an iterator over the elements contained in this element which
   * match the given local name and any namespace.
   */
  def jElementIterator(name: String): ju.Iterator[Element]
  def elementIterator(name: String): Iterator[Element] = jElementIterator(name).asScala

  /**
   * @return true if this element is the root element of a document and this
   *         element supports the parent relationship else false.
   */
  def isRootElement: Boolean

  /**
   * Creates a deep copy of this element The new element is detached from its
   * parent, and getParent() on the clone will return null.
   */
  def createCopy: Element
}
