/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.process

import cats.effect.IO
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.fr.XMLNames
import org.orbeon.oxf.fr.process.ProcessParser._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, Logging}
import org.orbeon.oxf.xml.XMLConstants.{XHTML_PREFIX, XHTML_SHORT_PREFIX, XSD_PREFIX}
import org.orbeon.oxf.xml.{XMLConstants, XMLUtils}
import org.orbeon.oxf.{util => u}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.scaxon.XPath._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xml.NamespaceMapping

import scala.annotation.tailrec
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}


// Independent process interpreter
trait ProcessInterpreter extends Logging {

  import ProcessInterpreter._

  val EmptyActionParams: ActionParams = Map.empty

  // Must be overridden by implementation
  def findProcessByName(scope: String, name: String): Option[String]
  def processError(t: Throwable): Unit
  def xpathContext: Item
  implicit def xpathFunctionLibrary: FunctionLibrary
  def xpathFunctionContext: u.FunctionContext
  def clearSuspendedProcess(): Unit
  def writeSuspendedProcess(processId: String, process: String): Unit
  def readSuspendedProcess: Try[(String, String)]
  def submitContinuation[T](computation: IO[T], continuation: Try[T] => Unit): Unit
  def createUniqueProcessId: String = CoreCrossPlatformSupport.randomHexId
  def transactionStart(): Unit
  def transactionRollback(): Unit
  def withRunProcess[T](scope: String, name: String)(body: => T): T
  implicit def logger: IndentedLogger

  // May be overridden by implementation
  def extensionActions: Iterable[(String, Action)] = Nil
  def beforeProcess(): Try[Any] = Try(())
//  def afterProcess():  Try[Any] = Try(())

  private object ProcessRuntime {

    import org.orbeon.oxf.util.DynamicVariable

    private val StandardActions: Map[String, Action] = Map(
      "success"  -> trySuccess,
      "failure"  -> tryFailure,
      "process"  -> tryProcess,
      "suspend"  -> trySuspend,
      "resume"   -> tryResume,
      "abort"    -> tryAbort,
      "rollback" -> tryRollback,
      "nop"      -> tryNop
    )

    val AllAllowedActions = StandardActions ++ extensionActions

    // Keep stack frames for the execution of action. They can nest with sub-processes.
    val processStackDyn = new DynamicVariable[Process]

    // Used to interrupt a process
    case class ProcessFailure() extends Throwable with NoStackTrace

    // Scope an empty stack around a process execution
    def withEmptyStack[T](scope: String)(body: => T): T = {
      processStackDyn.withValue(Process(scope, createUniqueProcessId, Nil)) {
        body
      }
    }

    // Push a stack frame, run the body, and pop the frame
    private def withStackFrame[T](group: GroupNode, programCounter: Int)(body: => T): T = {
      processStackDyn.value.get.frames = StackFrame(group, programCounter) :: processStackDyn.value.get.frames
      try body
      finally processStackDyn.value.get.frames = processStackDyn.value.get.frames.tail
    }

    // Return a process string which contains the continuation of the process after the current action
    def serializeContinuation: (String, String) = {

      val process = processStackDyn.value.get

      // Find the continuation, which is the concatenation of the continuation of all the sub-processes up to the
      // top-level process.
      val continuation =
        process.frames flatMap {
          case StackFrame(group, pos) =>
            group.rest drop pos flatMap { case (combinator, expr) =>
              List(combinator.name, expr.serialize)
            }
        }

      // Continuation is either empty or starts with a combinator. We prepend the (always successful) "nop".
      val serializedContinuation = "nop" :: continuation mkString ("(", " ", ")")
      (process.processId, serializedContinuation)
    }

    case class Process(scope: String, processId: String, var frames: List[StackFrame])
    case class StackFrame(group: GroupNode, actionCounter: Int)

    def runSubProcess(process: String, initialTry: Try[Any]): InternalActionResult = {

      def runAction(action: ActionNode): InternalActionResult =
        withDebug("process: running action", List("action" -> action.toString)) {

          val actionResult = (
            AllAllowedActions
            getOrElse (action.name, (_: ActionParams) => tryProcess(Map(Some("name") -> action.name)))
            apply     action.params
          )

          actionResult match {
            case r @ ActionResult.Sync(Success(_)) =>
              debugResults(List("result" -> "successful action"))
              r
            case r @ ActionResult.Sync(Failure(_)) =>
              debugResults(List("result" -> "failed action"))
              r
            case ActionResult.Async(failure @ Failure(_)) =>
              ActionResult.Sync(failure)
            case ActionResult.Async(Success((computation, continuation))) =>
              debugResults(List("result" -> "suspended asynchronous action"))
              val serializedContinuation = serializeContinuation
              // TODO: we don't support concurrent processes yet so if someone starts another process in the meanwhile,
              //  some state will be lost.
              val processScope = processStackDyn.value.get.scope
              submitContinuation(computation, continuation.andThen(initialTry => runProcess(processScope, serializedContinuation._2, initialTry)))
              ActionResult.Interrupt(None, None)
            case r @ ActionResult.Interrupt(_, _) =>
              debugResults(List("result" -> "interrupted action"))
              r
          }
        }

      def runGroup(group: GroupNode): InternalActionResult = {
        val GroupNode(expr, rest) = group
        runGroupRest(group, 1, withStackFrame(group, 0) { runExpr(expr) }, rest)
      }

      @tailrec def runGroupRest(
        group               : GroupNode,
        pos                 : Int,
        previousActionResult: InternalActionResult,
        rest                : List[(Combinator, ExprNode)]
      ): InternalActionResult =
        (previousActionResult, rest) match {
          case (ActionResult.Sync(tried), (nextCombinator, nextExpr) :: tail) =>

            val newActionResult =
              withStackFrame(group, pos) {
                nextCombinator match {
                  case ThenCombinator =>
                    debug("process: combining with then", List("action" -> nextExpr.toString))
                    tried match {
                      case Success(_) =>
                        runExpr(nextExpr)
                      case Failure(_) =>
                        previousActionResult
                    }
                  case RecoverCombinator =>
                    debug("process: combining with recover", List("action" -> nextExpr.toString))
                    tried match {
                      case Success(_) =>
                        previousActionResult
                      case Failure(t) =>
                        debug("process: recovering", List("throwable" -> OrbeonFormatter.format(t)))
                        runExpr(nextExpr)
                    }
                }
              }

            runGroupRest(group, pos + 1, newActionResult, tail)
          case _ =>
            previousActionResult
        }

      def runCondition(condition: ConditionNode): InternalActionResult =
        (Try(evaluateBoolean(condition.xpath)), condition.elseBranch) match {
          case (Success(true), _) =>
            runExpr(condition.thenBranch)
          case (Success(false), Some(elseBranch)) =>
            runExpr(elseBranch)
          case (Success(false), None) =>
            ActionResult.Sync(Success(()))
          case (Failure(t), _) =>
            debug("process: condition failed", List("throwable" -> OrbeonFormatter.format(t)))
            ActionResult.Sync(Failure(t))
        }

      def runExpr(expr: ExprNode): InternalActionResult =
        expr match {
          case e: ActionNode    => runAction(e)
          case e: GroupNode     => runGroup(e)
          case e: ConditionNode => runCondition(e)
        }

      def parseProcess(process: String): Option[GroupNode] =
        process.nonAllBlank option ProcessParser.parse(process)

      (parseProcess(process), initialTry) match {
        case (Some(groupNode), Success(_)) =>
          // Normal process run
          runGroup(groupNode)
        case (Some(groupNode @ GroupNode(_, rest)), failure @ Failure(_)) =>
          // Process run starting with a `Failure` (for continuations)
          runGroupRest(groupNode, 1, ActionResult.Sync(failure), rest)
        case (None, _) =>
          debug("process: empty process, canceling process")
          ActionResult.Sync(Success(()))
      }
    } // end `runSubProcess()`
  }

  import ProcessRuntime._

  private def rawProcessByName(scope: String, name: String): String =
    findProcessByName(scope, name) getOrElse
    (throw new IllegalArgumentException(s"Non-existing process: $name in scope $scope"))

  // Main entry point for starting a process associated with a named button
  def runProcessByName(scope: String, name: String): Try[Any] =
    withRunProcess(scope, name) {
      runProcess(scope, rawProcessByName(scope, name))
    }

  // Main entry point for starting a literal process
  def runProcess(scope: String, process: String, initialTry: Try[Any] = Success(())): Try[Any] =
    withDebug("process: running", List("process" -> process)) {
      transactionStart()
      // Scope the process (for suspend/resume)
      withEmptyStack(scope) {
        beforeProcess() flatMap { _ =>
          runSubProcess(process, initialTry) match {
            case ActionResult.Sync(tried) =>
              tried
            case ActionResult.Interrupt(message, Some(success @ Success(_))) =>
              debug(s"process: process interrupted with `success` action with message `$message`")
              success
            case ActionResult.Interrupt(message, Some(failure @ Failure(_))) =>
              debug(s"process: process interrupted with `failure` action with message `$message`")
              failure
            case ActionResult.Interrupt(message, None) =>
              debug(s"process: process interrupted due to asynchronous action with message `$message`")
              Success(())
          }
//        } doEitherWay {
//          afterProcess()
        } recoverWith { case t =>
          // Log and send a user error if there is one
          // NOTE: In the future, it would be good to provide the user with an error id.
          error(OrbeonFormatter.format(t))
          Try(processError(t))
          Failure(t)
        }
      }
      // TODO: `transactionEnd()` to clean transient state?
    }

  // Id of the currently running process
  def runningProcessId: Option[String] = processStackDyn.value map (_.processId)

  // Interrupt the process and complete with a success
  private def trySuccess(params: ActionParams): ActionResult =
    ActionResult.Interrupt(paramByName(params, "message"), Option(Success(())))

  // Interrupt the process and complete with a failure
  private def tryFailure(params: ActionParams): ActionResult =
    ActionResult.Interrupt(paramByName(params, "message"), Option(Failure(ProcessFailure())))

  // Run a sub-process
  private def tryProcess(params: ActionParams): ActionResult =
    Try(paramByNameOrDefault(params, "name").get)
      .map(rawProcessByName(processStackDyn.value.get.scope, _)) match {
      case Success(process) =>
        runSubProcess(process, Success(()))
      case failure @ Failure(_) =>
        ActionResult.Sync(failure)
    }

  // Suspend the process
  private def trySuspend(params: ActionParams): ActionResult =
    Try((writeSuspendedProcess _).tupled(serializeContinuation)) match {
      case Success(_) =>
        trySuccess(EmptyActionParams) // this will not be caught by `Try.apply()`
        ActionResult.Interrupt(None, Option(Success(())))
      case failure @ Failure(t) =>
        error(s"error suspending process: `${t.getMessage}`")
        ActionResult.Sync(failure)
    }

  // Resume a process
  private def tryResume(params: ActionParams): ActionResult =
    readSuspendedProcess match {
      case Success((processId, continuation)) =>
        // TODO: Restore processId
        clearSuspendedProcess()
        runSubProcess(continuation, Success(()))
      case failure @ Failure(t) =>
        error(s"error suspending process: `${t.getMessage}`")
        ActionResult.Sync(failure)
    }

  // Abort a suspended process
  private def tryAbort(params: ActionParams): ActionResult =
    ActionResult.trySync(clearSuspendedProcess())

  // Rollback the current transaction
  private def tryRollback(params: ActionParams): ActionResult =
    ActionResult.trySync {
      val tokens = paramByNameOrDefault(params, "changes") map (_.tokenizeToSet) getOrElse Set.empty

      if (tokens != Set("in-memory-form-data"))
        throw new IllegalArgumentException(s"""`rollback` action must have a `changes = "in-memory-form-data"` parameter""")

      transactionRollback()
    }

  // Don't do anything
  private def tryNop(params: ActionParams): ActionResult =
    ActionResult.Sync(Success(()))

  private def evaluateBoolean(expr: String, item: Item = xpathContext): Boolean =
    evaluateOne(
      expr = u.StaticXPath.makeBooleanExpression(expr),
      item = item
    ).asInstanceOf[BooleanValue].getBooleanValue

  def evaluateString(
    expr    : String,
    item    : Item             = xpathContext,
    mapping : NamespaceMapping = ProcessInterpreter.StandardNamespaceMapping
  ): String =
    evaluateOne(
      expr    = u.StaticXPath.makeStringExpression(expr),
      item    = item,
      mapping = mapping
    ).getStringValue

  def evaluateOne(
    expr    : String,
    item    : Item             = xpathContext,
    mapping : NamespaceMapping = ProcessInterpreter.StandardNamespaceMapping
  ): Item =
    evalOne(
      item            = item,
      expr            = expr,
      namespaces      = mapping,
      functionContext = xpathFunctionContext
    )

  def evaluate(expr: String, item: Item = xpathContext): collection.Seq[Any] =
    eval(
      item            = item,
      expr            = expr,
      namespaces      = ProcessInterpreter.StandardNamespaceMapping,
      functionContext = xpathFunctionContext
    )

  def evaluateValueTemplate(valueTemplate: String): String =
    if (! XMLUtils.maybeAVT(valueTemplate))
      valueTemplate
    else
      evalValueTemplate(
        item            = xpathContext,
        expr            = valueTemplate,
        namespaces      = ProcessInterpreter.StandardNamespaceMapping,
        functionContext = xpathFunctionContext
      )
}

object ProcessInterpreter {

  val StandardNamespaceMapping =
    NamespaceMapping(
      Map(
        XSD_PREFIX           -> XMLConstants.XSD_URI,
        XFORMS_PREFIX        -> XFORMS_NAMESPACE_URI,
        XFORMS_SHORT_PREFIX  -> XFORMS_NAMESPACE_URI,
        XXFORMS_PREFIX       -> XXFORMS_NAMESPACE_URI,
        XXFORMS_SHORT_PREFIX -> XXFORMS_NAMESPACE_URI,
        XHTML_PREFIX         -> XMLConstants.XHTML_NAMESPACE_URI,
        XHTML_SHORT_PREFIX   -> XMLConstants.XHTML_NAMESPACE_URI,
        XHTML_PREFIX         -> XMLConstants.XHTML_NAMESPACE_URI,
        XHTML_SHORT_PREFIX   -> XMLConstants.XHTML_NAMESPACE_URI,
        XMLNames.FRPrefix    -> XMLNames.FR,
        "grid-migration"     -> "java:org.orbeon.oxf.fr.GridDataMigration" // TODO: should be from properties file
      )
    )

  sealed trait ActionResult
  sealed trait InternalActionResult extends ActionResult
  object ActionResult {
    case class Sync(value: Try[Any])                                       extends InternalActionResult
    case class Async[T](value: Try[(IO[T], Try[T] => Try[Any])])           extends ActionResult
    case class Interrupt(message: Option[String], value: Option[Try[Any]]) extends InternalActionResult

    def trySync(body: => Any): ActionResult = ActionResult.Sync(Try(body))
    def tryAsync[T](body: => (IO[T], Try[T] => Try[Any])): ActionResult = ActionResult.Async(Try(body))
  }

  type ActionParams = Map[Option[String], String]
  type Action       = ActionParams => ActionResult

  def paramByName(params: ActionParams, name: String): Option[String] =
    params.get(Some(name))

  def booleanParamByName(params: ActionParams, name: String, default: Boolean): Boolean =
    params.get(Some(name)) map (_ == "true") getOrElse default

  def paramByNameOrDefault(params: ActionParams, name: String): Option[String] =
    params.get(Some(name)) orElse params.get(None)

  def requiredParamByName(params: ActionParams, action: String, name: String): String =
    params.getOrElse(Some(name), missingArgument(action, name))

  // TODO: Obtain action name automatically.
  private def missingArgument(action: String, name: String) =
    throw new IllegalArgumentException(s"$action: `$name` parameter is required")
}