/**
 * Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.msv

import org.orbeon.dom
import org.orbeon.msv.driver.textui.Debug
import org.orbeon.msv.grammar.xmlschema.{ElementDeclExp, IdentityConstraint, KeyRefConstraint}
import org.orbeon.msv.relaxng.datatype.{Datatype, ValidationContext}
import org.orbeon.msv.util.{LightStack, StartTagInfo}
import org.orbeon.msv.verifier.Acceptor
import org.orbeon.oxf.util.{MessageFormatCache, MessageFormatter}
import org.orbeon.xforms.runtime.ErrorInfo

import scala.collection.mutable

/**
 * Verifier with XML Schema-related enforcement.
 *
 * This class checks XML Schema's identity constraints.
 */
class IDConstraintChecker extends ValidationContext {

  /** Active matchers. */
  protected val matchers = new mutable.ArrayBuffer[Matcher]

  def add(matcher: Matcher): Unit =
    matchers += matcher

  def remove(matcher: Matcher): Unit =
    matchers -= matcher

  /**
   * A map from SelectorMatcher to set of KeyValues.
   * One SelectorMatcher corresponds to one scope of the identity constraint.
   */
  private val keyValues = mutable.Map.empty[SelectorMatcher, mutable.Set[KeyValue]]

  /**
   * A map from keyref SelectorMatcher to key/unique SelectorMatcher.
   * Given a keyref scope, this map stores which key scope should it refer to.
   */
  private val referenceScope = mutable.Map.empty[SelectorMatcher, SelectorMatcher]

  /**
   * A map from IdentityConstraint to a LightStack of SelectorMatcher.
   * Each stack top keeps the currently active scope for the given IdentityConstraint.
   */
  private val activeScopes = mutable.Map.empty[IdentityConstraint, LightStack]

  def getActiveScope(c: IdentityConstraint): SelectorMatcher =
    activeScopes.get(c) match {
      case Some(s) if s.size() != 0 => s.top().asInstanceOf[SelectorMatcher]
      case _                        => null
    }

  def pushActiveScope(c: IdentityConstraint, matcher: SelectorMatcher): Unit = {
    val s = activeScopes.getOrElseUpdate(c, new LightStack)
    s.push(matcher)
  }

  def popActiveScope(c: IdentityConstraint, matcher: SelectorMatcher): Unit = {
    val s = activeScopes.getOrElse(c, throw new Error("stack empty"))
    if (s.pop() != matcher)
      throw new Error("trying to pop non-active scope")
  }

  /**
   * Adds a new KeyValue to the value set.
   * @return true if this is a new value.
   */
  def addKeyValue(scope: SelectorMatcher, value: KeyValue): Boolean = {
    val keys = keyValues.getOrElseUpdate(scope, mutable.Set.empty[KeyValue])
    keys.add(value)
  }

  /**
   * Gets all KeyValues that were added within the specified scope.
   */
  def getKeyValues(scope: SelectorMatcher): Array[KeyValue] =
    keyValues.get(scope).map(_.toArray).getOrElse(Array.empty)

  def startDocument(): Unit =
    keyValues.clear()

  def endDocument(): Unit = {
    if (Debug.debug)
      println(s"key/keyref check: there are ${keyValues.size} scope(s)")

    for ((key, value) <- keyValues) {
      if (key.idConst.isInstanceOf[KeyRefConstraint]) {
        val keysOpt = referenceScope.get(key).flatMap(keyValues.get)
        for (keyref <- value) {
          if (!keysOpt.exists(_.contains(keyref))) {
            reportError(
              keyref.element,
              IDConstraintChecker.ERR_UNDEFINED_KEY,
              Array(key.idConst.namespaceURI, key.idConst.localName)
            )
          }
        }
      }
    }
  }

  def onNextAcceptorReady(sti: StartTagInfo, next: Acceptor, elt: dom.Element): Unit = {
    // Call matchers
    var i = 0
    val len = matchers.size
    while (i < len) {
      matchers(i).startElement(elt)
      i += 1
    }

    // Introduce newly found identity constraints
    next.getOwnerType match {
      case exp: ElementDeclExp#XSElementExp =>
        if (exp.identityConstraints != null) {
          val idConstraints = exp.identityConstraints
          val m = idConstraints.size
          var j = 0
          while (j < m) {
            add(new SelectorMatcher(this, idConstraints.get(j).asInstanceOf[IdentityConstraint], elt))
            j += 1
          }

          j = 0
          while (j < m) {
            idConstraints.get(j) match {
              case keyRef: KeyRefConstraint =>
                val keyScope = getActiveScope(keyRef.key)
                referenceScope.put(getActiveScope(keyRef), keyScope)
              case _ =>
            }
            j += 1
          }
        }
      case _ =>
    }
  }

  def feedAttribute(child: Acceptor, att: dom.Attribute, result: Array[Datatype]): Unit = {
    val dt = if (result == null || result.length == 0) null else result(0)
    var i = 0
    val len = matchers.size
    while (i < len) {
      matchers(i).onAttribute(att, dt)
      i += 1
    }
  }

  def endElement(elt: dom.Element, lastType: Array[Datatype]): Unit = {
    val dt = if (lastType == null || lastType.length == 0) null else lastType(0)
    var i = matchers.size - 1
    while (i >= 0) {
      matchers(i).endElement(dt)
      i -= 1
    }
  }

  private val errorInfoList = new mutable.ArrayDeque[ErrorInfo]

  def clearErrorInfo(): ErrorInfo =
    if (errorInfoList.isEmpty) null else errorInfoList.removeLast()

  def reportError(elt: dom.Element, propKey: String, args: Array[AnyRef]): Unit = {
    val suffix = if (elt == null) "" else " at " + elt.getQualifiedName
    val msg = IDConstraintChecker.localizeMessage(propKey, args) + suffix
    errorInfoList.append(ErrorInfo(elt, msg))
  }

  def resolveNamespacePrefix(arg0: String): String = null
  def getBaseUri: String = null
  def isUnparsedEntity(arg0: String): Boolean = false
  def isNotation(arg0: String): Boolean = false
}

object IDConstraintChecker {

  val ERR_UNMATCHED_KEY_FIELD = "IdentityConstraint.UnmatchedKeyField"
  val ERR_NOT_UNIQUE          = "IdentityConstraint.NotUnique"
  val ERR_NOT_UNIQUE_DIAG     = "IdentityConstraint.NotUnique.Diag"
  val ERR_DOUBLE_MATCH        = "IdentityConstraint.DoubleMatch"
  val ERR_UNDEFINED_KEY       = "IdentityConstraint.UndefinedKey"

  private val Messages = Map(
    ERR_UNMATCHED_KEY_FIELD -> """field #{2} of the key constraint "{1}" is not found""",
    ERR_NOT_UNIQUE          -> """this element does not satisfy the uniqueness imposed by "{1}"""",
    ERR_NOT_UNIQUE_DIAG     -> """The same key value is used""",
    ERR_DOUBLE_MATCH        -> """more than one elements/attributes match the field #{2} of the identity constraint "{1}"""",
    ERR_UNDEFINED_KEY       -> """keyref "{1}" does not have a corresponding key"""
  )

  def localizeMessage(propertyName: String, arg: AnyRef): String =
    localizeMessage(propertyName, Array(arg))

  def localizeMessage(propertyName: String, args: Array[AnyRef]): String =
    MessageFormatter.format(MessageFormatCache(Messages(propertyName)), args.toIndexedSeq)
}
