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
import org.orbeon.saxon.value._
import org.orbeon.scaxon.SimplePath.URIQualifiedName

import scala.collection.compat._
import scala.jdk.CollectionConverters._
import scala.{collection => coll}


object Implicits {

  implicit def stringToQName                       (v: String)                   : QName              = QName(v ensuring ! v.contains(':'))
  implicit def tupleToQName                        (v: (String, String))         : QName              = QName(v._2, "", v._1)
  implicit def uriQualifiedNameToQName             (v: URIQualifiedName)         : QName              = QName(v.localName, "", v.uri)

  implicit def intToIntegerValue                   (v: Int)                      : IntegerValue       = Int64Value.makeIntegerValue(v)
  implicit def doubleToDoubleValue                 (v: Double)                   : DoubleValue        = new DoubleValue(v)
  implicit def stringToStringValue                 (v: String)                   : StringValue        = StringValue.makeStringValue(v)
  implicit def booleanToBooleanValue               (v: Boolean)                  : BooleanValue       = BooleanValue.get(v)

  implicit def itemToSequenceIterator              (v: Item)                     : SequenceIterator   = SingletonIterator.makeIterator(v)
  implicit def stringSeqToSequenceIterator         (v: coll.Seq[String])         : SequenceIterator   = new ListIterator(v map stringToStringValue asJava)
  implicit def itemSeqToSequenceIterator[T <: Item](v: coll.Seq[T])              : SequenceIterator   = new ListIterator(v.asJava)
  implicit def itemSeqOptToSequenceIterator        (v: Option[coll.Seq[Item]])   : SequenceIterator   = v map itemSeqToSequenceIterator   getOrElse EmptyIterator.getInstance
  implicit def stringSeqOptToSequenceIterator      (v: Option[coll.Seq[String]]) : SequenceIterator   = v map stringSeqToSequenceIterator getOrElse EmptyIterator.getInstance

  implicit def stringOptToStringValue              (v: Option[String])           : StringValue        = v map stringToStringValue   orNull
  implicit def booleanOptToBooleanValue            (v: Option[Boolean])          : BooleanValue       = v map booleanToBooleanValue orNull

  implicit def nodeInfoToNodeInfoSeq               (v: NodeInfo)                 : coll.Seq[NodeInfo] = List(v ensuring (v ne null))

  implicit def asScalaIterator(i: SequenceIterator): coll.Iterator[Item] = new coll.Iterator[Item] {

    private var current = i.next()

    def next(): Item = {
      val result = current
      current = i.next()
      result
    }

    def hasNext: Boolean = current ne null
  }

  def asScalaSeq(i: SequenceIterator): Seq[Item] = asScalaIterator(i).to(List)
}
