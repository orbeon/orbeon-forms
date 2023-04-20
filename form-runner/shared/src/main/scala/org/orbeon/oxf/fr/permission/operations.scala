package org.orbeon.oxf.fr.permission

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.StringUtils._

import scala.collection.immutable


sealed trait                                                       Operations
// TODO: move to inside `object Operations`
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

  val None: SpecificOperations = SpecificOperations(Set.empty)
  private val AllList: List[Operation] = Operation.values.toList

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

  // Return `None` iif there are not at least one possible operation
  def parseFromString(stringOperations: String): Option[Operations] =
    parseFromStringTokens(stringOperations.splitTo[List]()) match {
      case Operations.None => scala.None
      case operations      => Some(operations)
    }

  def parseFromStringTokensNoWildcard(stringOperations: String): SpecificOperations = {

    val stringTokensSet = stringOperations.splitTo[Set]()

    if (stringTokensSet("*"))
      throw new IllegalArgumentException(stringOperations)
    else
      SpecificOperations(stringTokensSet.map(Operation.withName))
  }

  // `operations` contains tokens.
  // 2023-04-17: We exclude `*` as this is called by `PermissionsXML` only for parsing
  // permissions set in form definitions, and there the `*` is not allowed.
  def normalizeAndParseSpecificOperations(operations: String): SpecificOperations = {

    val tokens = operations.tokenizeToSet

    if (tokens("*")) {
      throw new IllegalArgumentException(operations)
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

  private def denormalizeOperations(operationsTokens: Set[Operation]): List[String] = {

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
    operations.foldLeft[Operations](Operations.None)(combine)

  def allows(granted: Operations, requested: Operation): Boolean =
    granted match {
      case AnyOperation                          => true
      case SpecificOperations(grantedOperations) => grantedOperations(requested)
    }

  def allowsAny(granted: Operations, mightHave: Set[Operation]): Boolean = mightHave.exists(allows(granted, _))
  def allowsAll(granted: Operations, mustHave : Set[Operation]): Boolean = mustHave .forall(allows(granted, _))
}