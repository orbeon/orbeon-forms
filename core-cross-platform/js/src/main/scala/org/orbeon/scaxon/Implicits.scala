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
import org.orbeon.saxon.om._
import org.orbeon.saxon.tree.iter.ListIterator
import org.orbeon.saxon.value._
import org.orbeon.scaxon.SimplePath.URIQualifiedName

import scala.jdk.CollectionConverters._
import scala.{collection => coll}


object Implicits {

  // 2023-10-12: The following implicit conversions are commented out because they are unused. However, we are leaving
  // them here for now in case we need them for the move to Saxon 11 on the JVM side. See also:
  //
  // - https://github.com/orbeon/orbeon-forms/issues/5107
  // - https://github.com/orbeon/orbeon-forms/issues/6016
  //
  // More of those might be unneeded once the move to Saxon 11 is complete, and all the conversions can be done in
  // the code that handles the macro annotation.

//  implicit def doubleToDoubleValue                 (v: Double)                   : DoubleValue        = new DoubleValue(v)
//  implicit def itemToSequenceIterator              (v: Item)                     : SequenceIterator   = SingletonIterator.makeIterator(v)
//  implicit def itemSeqOptToSequenceIterator        (v: Option[coll.Seq[Item]])   : SequenceIterator   = v map itemSeqToSequenceIterator   getOrElse EmptyIterator.getInstance
//  implicit def stringSeqOptToSequenceIterator      (v: Option[coll.Seq[String]]) : SequenceIterator   = v map stringSeqToSequenceIterator getOrElse EmptyIterator.getInstance
//  implicit def stringOptToStringValue              (v: Option[String])           : StringValue        = v map stringToStringValue   orNull
//  implicit def booleanOptToBooleanValue            (v: Option[Boolean])          : BooleanValue       = v map booleanToBooleanValue orNull]
//  implicit def stringArrayToSequenceIterator       (v: Array[String])            : SequenceIterator   = new ArrayIterator(v map stringToStringValue)

  implicit def stringToQName                       (v: String)                   : QName              = QName(v ensuring ! v.contains(':'))
  implicit def tupleToQName                        (v: (String, String))         : QName              = QName(v._2, "", v._1)
  implicit def uriQualifiedNameToQName             (v: URIQualifiedName)         : QName              = QName(v.localName, "", v.uri)

  implicit def intToIntegerValue                   (v: Int)                      : IntegerValue       = Int64Value.makeIntegerValue(v)
  implicit def stringToStringValue                 (v: String)                   : StringValue        = StringValue.makeStringValue(v)
  implicit def booleanToBooleanValue               (v: Boolean)                  : BooleanValue       = BooleanValue.get(v)

  implicit def stringSeqToSequenceIterator         (v: coll.Seq[String])         : SequenceIterator   = new ListIterator (v map stringToStringValue asJava)
  implicit def itemSeqToSequenceIterator[T <: Item](v: coll.Seq[T])              : SequenceIterator   = new ListIterator (v.asJava)

  implicit def nodeInfoToNodeInfoSeq               (v: NodeInfo)                 : coll.Seq[NodeInfo] = List(v ensuring (v ne null))

  def asSequenceIterator(i: coll.Iterator[Item]): SequenceIterator = new SequenceIterator {

    private var currentItem: Item = _
    private var _position = 0

    def next(): Item = {
      if (i.hasNext) {
        currentItem = i.next()
        _position += 1
      } else {
        currentItem = null
        _position = -1
      }

      currentItem
    }
  }

  def asScalaIterator(i: SequenceIterator): coll.Iterator[Item] = new coll.Iterator[Item] {

    private var current = i.next()

    def next(): Item = {
      val result = current
      current = i.next()
      result
    }

    def hasNext: Boolean = current ne null
  }
}
