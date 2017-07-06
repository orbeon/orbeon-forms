/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.scaxon

import org.orbeon.dom.QName
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.{Item, ListIterator, NodeInfo, SequenceIterator}
import org.orbeon.scaxon.SimplePath.URIQualifiedName

import scala.collection.JavaConverters._
import scala.collection.{Iterator, Seq}

object Implicits {

  implicit def nodeInfoToNodeInfoSeq(node: NodeInfo): Seq[NodeInfo] = List(node ensuring (node ne null))

  implicit def stringSeqToSequenceIterator(seq: Seq[String]): SequenceIterator =
    new ListIterator(seq map SaxonUtils.stringToStringValue asJava)

  implicit def itemSeqToSequenceIterator[T <: Item](seq: Seq[T]): SequenceIterator =
    new ListIterator(seq.asJava)

  implicit def stringToQName(s: String): QName = QName.get(s ensuring ! s.contains(':'))
  implicit def tupleToQName(name: (String, String)): QName = QName.get(name._2, "", name._1)
  implicit def uriQualifiedNameToQName(uriQualifiedName: URIQualifiedName): QName = QName.get(uriQualifiedName.localName, "", uriQualifiedName.uri)

  implicit def saxonIteratorToItem(i: SequenceIterator): Item = i.next()

  implicit def asStringSequenceIterator(i: Iterator[String]): SequenceIterator =
    asSequenceIterator(i map SaxonUtils.stringToStringValue)

  implicit def asSequenceIterator(i: Iterator[Item]): SequenceIterator = new SequenceIterator {

    private var currentItem: Item = _
    private var _position = 0

    def next() = {
      if (i.hasNext) {
        currentItem = i.next()
        _position += 1
      } else {
        currentItem = null
        _position = -1
      }

      currentItem
    }

    def current = currentItem
    def position = _position
    def close() = ()
    def getAnother = null
    def getProperties = 0
  }

  implicit def asScalaIterator(i: SequenceIterator): Iterator[Item] = new Iterator[Item] {

    private var current = i.next()

    def next() = {
      val result = current
      current = i.next()
      result
    }

    def hasNext = current ne null
  }

  implicit def asScalaSeq(i: SequenceIterator): Seq[Item] = asScalaIterator(i).toSeq
}
