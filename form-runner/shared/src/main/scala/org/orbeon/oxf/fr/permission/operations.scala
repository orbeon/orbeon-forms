package org.orbeon.oxf.fr.permission

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.StringUtils._

import scala.collection.immutable


sealed trait                                                       Operations
case object AnyOperation                                   extends Operations
case class  SpecificOperations(operations: Set[Operation]) extends Operations {

  implicit object OperationsOrdering extends Ordering[Operation] {
    def compare(x: Operation, y: Operation): Int = x.entryName.compare(y.entryName)
  }

  override def equals(otherAny: Any): Boolean = otherAny match {
    case otherOperations: SpecificOperations => operations == otherOperations.operations
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

  val None   : Operations      = SpecificOperations(Set.empty)
  val AllList: List[Operation] = Operation.values.toList
  val AllSet : Set[Operation]  = AllList.toSet

  def parseFromHeaders(headers: Map[String, List[String]]): Option[Operations] =
    Headers.firstItemIgnoreCase(headers, FormRunnerPersistence.OrbeonOperations)
      .map(v => parseFromStringTokens(v.splitTo[List]()))

  private def parseFromStringTokens(stringTokens: List[String]): Operations =
    stringTokens match {
      case List("*") =>
        AnyOperation
      case _ =>
        SpecificOperations(stringTokens.toSet.map(Operation.withName))
    }

  def parseFromString(stringOperations: String): Option[Operations] =
    parseFromStringTokens(stringOperations.splitTo[List]()) match {
      case o @ AnyOperation                            => Some(o)
      case o @ SpecificOperations(ops) if ops.nonEmpty => Some(o)
      case SpecificOperations(_)                       => scala.None
    }

  // `operations` contains tokens, including possibly `*`. This is the value of the `operations` attribute in the
  // serialized form definition format for permissions.
  def normalizeAndParseOperations(operations: String): Operations = {

    val tokens = operations.tokenizeToSet

    if (tokens("*")) {
      // We shouldn't have `* read`, for example, but in case we do then `*` wins
      AnyOperation
    } else {
      val hasMinusListToken = tokens(MinusListToken) // https://github.com/orbeon/orbeon-forms/issues/5397

      val updatedTokens =
        if (! hasMinusListToken) // 2022-10-03: decided that `list` does not imply `read`
          tokens + Operation.List.entryName
        else
          tokens.filter(_ != MinusListToken) // Q: what if `list -list`?

      SpecificOperations(updatedTokens.map(Operation.withName))
    }
  }

  def denormalizeOperations(operationsTokens: Set[Operation]): List[String] = {

    // The UI format now no longer includes `read` if there is `update`
    // `update` => `read`
    val withRead =
      if (operationsTokens(Operation.Update)) // 2022-10-03: decided that `list` does not imply `read`
        operationsTokens + Operation.Read
      else
        operationsTokens

    // See https://github.com/orbeon/orbeon-forms/issues/5397
    if (! withRead(Operation.List))
      inDefinitionOrder(withRead).map(_.entryName) :+ MinusListToken // `-list` indicates permission is explicitly removed
    else
      inDefinitionOrder(withRead - Operation.List).map(_.entryName)
  }

  def inDefinitionOrder(operations: Iterable[Operation]): List[Operation] =
    Operations.AllList.filter(operations.toSet)

  // `normalized == false` only when called from `PermissionsUiTest`. Not sure if that's correct.
  def serialize(operations: Operations, normalized: Boolean): List[String] =
    operations match {
      case AnyOperation =>
        List("*")
      case SpecificOperations(operations) if normalized =>
        inDefinitionOrder(operations).map(_.entryName)
      case SpecificOperations(operations) =>
        denormalizeOperations(operations)
     }

  def combine(left: Operations, right: Operations): Operations =
    (left, right) match {
      case (SpecificOperations(leftOps), SpecificOperations(rightOps)) =>
        SpecificOperations(leftOps ++ rightOps)
      case _ =>
        AnyOperation
    }

  def combine(operations: List[Operations]): Operations =
    operations.foldLeft[Operations](None)(combine)

  def allows(granted: Operations, requested: Operation): Boolean =
    granted match {
      case AnyOperation                          => true
      case SpecificOperations(grantedOperations) => grantedOperations(requested)
    }

  def allowsAny(granted: Operations, mightHave: Set[Operation]): Boolean = mightHave.exists(allows(granted, _))
  def allowsAll(granted: Operations, mustHave : Set[Operation]): Boolean = mustHave .forall(allows(granted, _))
}