/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import org.orbeon.saxon.`type`.ItemType
import org.orbeon.saxon.value.{SequenceType, Value}
import org.orbeon.saxon.functions.StandardFunction._
import org.orbeon.saxon.om.{StructuredQName, NamespaceConstant}
import org.orbeon.saxon.expr.{StaticContext, Expression}
import org.orbeon.oxf.common.OXFException
import org.orbeon.saxon.functions.{FunctionLibrary, SystemFunction}
import scala.util.control.NonFatal

/**
 * Base class for Orbeon XPath function libraries.
 *
 * This provides facilities to register functions.
 */
abstract class OrbeonFunctionLibrary extends FunctionLibrary {

  protected object Namespace {
    private var _currentURI: Option[String] = None
    def currentURI = _currentURI

    def apply(uri: String)(block: => Any): Unit = {
      _currentURI = Some(uri)
      block
      _currentURI = None
    }

    def apply(uris: Seq[String])(block: => Any): Unit = {
      uris foreach  { uri =>
        _currentURI = Some(uri)
        block
        _currentURI = None
      }
    }
  }

  protected case class Arg(itemType: ItemType, arity: Int, resultIfEmpty: Value = null)

  protected object Fun {

    def apply(name: String, implementationClass: Class[_], op: Int, min: Int, itemType: ItemType, arity: Int, args: Arg*): Unit = {
      val uri = Namespace.currentURI getOrElse NamespaceConstant.FN
      apply(uri, name, implementationClass, op, min, args.length, itemType, arity,  args: _*)
    }

    def apply(name: String, implementationClass: Class[_], op: Int, min: Int, max: Int, itemType: ItemType, arity: Int, args: Arg*): Unit = {
      val uri = Namespace.currentURI getOrElse NamespaceConstant.FN
      apply(uri, name, implementationClass, op, min, max, itemType, arity,  args: _*)
    }

    def apply(uri: String, name: String, implementationClass: Class[_], op: Int, min: Int, max: Int, itemType: ItemType, arity: Int, args: Arg*): Unit = {

      assert(uri ne null)
      assert(name ne null)
      assert(itemType ne null)

      val functionName = FunctionName(uri, name)

      // Create function entry
      val e = new Entry
      e.name                = functionName.toString
      e.implementationClass = implementationClass
      e.opcode              = op
      e.minArguments        = min
      e.maxArguments        = max
      e.itemType            = itemType
      e.cardinality         = arity
      e.argumentTypes       = new Array[SequenceType](max)
      e.resultIfEmpty       = new Array[Value](max)

      // Add arguments
      args.zipWithIndex foreach { case (a, i) =>
        arg(e, i, a.itemType, a.arity, a.resultIfEmpty)
      }

      // Remember function
      functions += functionName -> e
    }
  }

  private case class FunctionName(uri: String, name: String)
  private val functions = collection.mutable.Map[FunctionName, Entry]()

  def getEntry(uri: String, name: String, arity: Int) =
    functions.get(FunctionName(uri, name)) filter
      (e => arity == -1 || arity >= e.minArguments && arity <= e.maxArguments)

  def isAvailable(functionName: StructuredQName, arity: Int) =
    getEntry(functionName.getNamespaceURI, functionName.getLocalName, arity) isDefined

  def bind(functionName: StructuredQName, staticArgs: Array[Expression], env: StaticContext) =
    getEntry(functionName.getNamespaceURI, functionName.getLocalName, staticArgs.length) match {
      case Some(entry) =>
        val functionClass = entry.implementationClass
        val f =
          try functionClass.getConstructor().newInstance().asInstanceOf[SystemFunction]
          catch {
            case NonFatal(t) => throw new OXFException("Failed to load function: " + t.getMessage, t)
          }
        f setDetails entry
        f setFunctionName functionName
        f setArguments staticArgs
        f
      case None =>
        null
    }

  def copy = this
}