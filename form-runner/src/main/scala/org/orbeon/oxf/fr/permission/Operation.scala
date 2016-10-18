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

sealed trait                                                        Operations
case object AnyOperation                                    extends Operations
case class  SpecificOperations(operations: List[Operation]) extends Operations

sealed trait               Operation { val name : String }
case object Create extends Operation { val name = "create" }
case object Read   extends Operation { val name = "read" }
case object Update extends Operation { val name = "update" }
case object Delete extends Operation { val name = "delete" }

object Operations {

  def All  = List(Create, Read, Update, Delete)
  def None = SpecificOperations(Nil)

  def parse(stringOperations: List[String]): Operations =
    stringOperations match {
      case List("*") ⇒
        AnyOperation
      case _   ⇒
        val operations      = stringOperations.map { operationName ⇒
          val operationOpt = All.find(_.name == operationName)
          operationOpt.getOrElse(throw new IllegalArgumentException(s"Unknown operation `$operationName`"))
        }
        SpecificOperations(operations)
    }

  def serialize(operations: Operations): List[String] =
    operations match {
      case AnyOperation ⇒
        List("*")
      case SpecificOperations(specificOperations) ⇒
        specificOperations.map(_.name)
     }

  def combine(left: Operations, right: Operations): Operations =
    (left, right) match {
      case (SpecificOperations(leftOps), SpecificOperations(rightOps)) ⇒
        SpecificOperations((leftOps ++ rightOps).distinct)
      case _ ⇒
        AnyOperation
    }

  def combine(operations: List[Operations]): Operations =
    operations.foldLeft[Operations](None)(combine)

  def allows(granted: Operations, requested: Operation): Boolean =
    granted match {
      case AnyOperation ⇒ true
      case SpecificOperations(grantedOperations) ⇒ grantedOperations.contains(requested)
    }

  def allowsAny(granted: Operations, mightHave: List[Operation]): Boolean =   mightHave.exists(  allows(granted, _))
  def allowsAll(granted: Operations, mustHave : List[Operation]): Boolean = ! mustHave .exists(! allows(granted, _))
}