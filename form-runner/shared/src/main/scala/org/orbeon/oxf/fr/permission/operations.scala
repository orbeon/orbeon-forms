/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.permission

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.StringUtils._


sealed trait                                                        Operations
case object AnyOperation                                    extends Operations
case class  SpecificOperations(operations: List[Operation]) extends Operations {

  implicit object OperationsOrdering extends Ordering[Operation] {
    def compare(x: Operation, y: Operation): Int = x.entryName.compare(y.entryName)
  }

  override def equals(otherAny: Any) = otherAny match {
    case otherOperations: SpecificOperations => operations.sorted == otherOperations.operations.sorted
    case _                                   => false
  }
}

sealed trait Operation extends EnumEntry with Lowercase

object Operation extends Enum[Operation] {

  val values = findValues

  case object Create extends Operation
  case object Read   extends Operation
  case object Update extends Operation
  case object Delete extends Operation
  case object List   extends Operation
}

object Operations {

  def None = SpecificOperations(Nil)
  def All  = List(
    Operation.Create,
    Operation.Read,
    Operation.Update,
    Operation.Delete,
    Operation.List
  )

  def parseFromHeaders(headers: Map[String, List[String]]): Option[Operations] =
    Headers.firstItemIgnoreCase(headers, FormRunnerPersistence.OrbeonOperations)
      .map(v => parse(v.splitTo[List]()))

  def parse(stringOperations: List[String]): Operations =
    stringOperations match {
      case List("*") =>
        AnyOperation
      case _   =>
        val operations = stringOperations.map { operationName =>
          val operationOpt = All.find(_.entryName == operationName)
          operationOpt.getOrElse(throw new IllegalArgumentException(s"Unknown operation `$operationName`"))
        }
        SpecificOperations(operations)
    }

  def serialize(operations: Operations): List[String] =
    operations match {
      case AnyOperation =>
        List("*")
      case SpecificOperations(specificOperations) =>
        specificOperations.map(_.entryName)
     }

  def combine(left: Operations, right: Operations): Operations =
    (left, right) match {
      case (SpecificOperations(leftOps), SpecificOperations(rightOps)) =>
        SpecificOperations((leftOps ++ rightOps).distinct)
      case _ =>
        AnyOperation
    }

  def combine(operations: List[Operations]): Operations =
    operations.foldLeft[Operations](None)(combine)

  def allows(granted: Operations, requested: Operation): Boolean =
    granted match {
      case AnyOperation                          => true
      case SpecificOperations(grantedOperations) => grantedOperations.contains(requested)
    }

  def allowsAny(granted: Operations, mightHave: List[Operation]): Boolean =   mightHave.exists(  allows(granted, _))
  def allowsAll(granted: Operations, mustHave : List[Operation]): Boolean = ! mustHave .exists(! allows(granted, _))
}