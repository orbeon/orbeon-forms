package org.orbeon.oxf.fr.permission

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.StringUtils._

import scala.collection.immutable


sealed trait                                                        Operations
case object AnyOperation                                    extends Operations
case class  SpecificOperations(operations: List[Operation]) extends Operations {

  implicit object OperationsOrdering extends Ordering[Operation] {
    def compare(x: Operation, y: Operation): Int = x.entryName.compare(y.entryName)
  }

  override def equals(otherAny: Any): Boolean = otherAny match {
    case otherOperations: SpecificOperations => operations.sorted == otherOperations.operations.sorted
    case _                                   => false
  }
}

sealed trait Operation extends EnumEntry with Lowercase

object Operation extends Enum[Operation] {

  val values: immutable.IndexedSeq[Operation] = findValues

  case object Create extends Operation
  case object Read   extends Operation
  case object Update extends Operation
  case object Delete extends Operation
  case object List   extends Operation
}

object Operations {

  private val MinusListToken: String = s"-${Operation.List.entryName}"

  val None: Operations = SpecificOperations(Nil)
  val All: List[Operation] = Operation.values.toList

  def parseFromHeaders(headers: Map[String, List[String]]): Option[Operations] =
    Headers.firstItemIgnoreCase(headers, FormRunnerPersistence.OrbeonOperations)
      .map(v => parse(v.splitTo[List]()))

  def parse(stringOperations: List[String]): Operations =
    stringOperations match {
      case List("*") =>
        AnyOperation
      case _ =>
        SpecificOperations(stringOperations.map(Operation.withName))
    }

  def normalizeOperations(operations: String): Set[String] = {

    val tokens = operations.tokenizeToSet
    val hasMinusListToken = tokens(MinusListToken) // https://github.com/orbeon/orbeon-forms/issues/5397

    if (! hasMinusListToken && tokens(Operation.Read.entryName))
      tokens + Operation.List.entryName
    else if (hasMinusListToken)
      tokens.filter(_ != MinusListToken)
    else
      tokens
  }

  def denormalizeOperations(operationsTokens: Set[Operation]): List[String] = {

    // The UI format now no longer includes `read` if there is a `list` or `update`
    // `list` => `read` and `update` => `read`
    val withRead =
      if (operationsTokens(Operation.List) || operationsTokens(Operation.Update))
        operationsTokens + Operation.Read
      else
        operationsTokens

    // See https://github.com/orbeon/orbeon-forms/issues/5397
    if (withRead(Operation.Read) && ! withRead(Operation.List))
      inDefinitionOrder(withRead).map(_.entryName) :+ MinusListToken // `-list` indicates permission is explicitly removed
    else
      inDefinitionOrder(withRead - Operation.List).map(_.entryName)
  }

  def inDefinitionOrder(operations: Iterable[Operation]): List[Operation] = {
    val operationsSet = operations.toSet
    Operations.All.filter(operationsSet)
  }

  def serialize(operations: Operations, normalized: Boolean): List[String] =
    operations match {
      case AnyOperation =>
        List("*")
      case SpecificOperations(operations) if normalized =>
        inDefinitionOrder(operations).map(_.entryName)
      case SpecificOperations(operations) =>
        denormalizeOperations(operations.toSet)
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