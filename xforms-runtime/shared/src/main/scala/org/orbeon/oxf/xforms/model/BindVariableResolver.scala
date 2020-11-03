/**
* Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.oxf.xforms.analysis.model.StaticBind
import org.orbeon.saxon.om
import org.orbeon.saxon.value.SequenceExtent

import scala.jdk.CollectionConverters._

// Implement bind variable resolution, taking an optional context bind node as starting point
object BindVariableResolver {

  // Main resolution function
  def resolveClosestBind(
    modelBinds         : XFormsModelBinds,
    contextBindNodeOpt : Option[BindNode],
    targetStaticBind   : StaticBind
  ): Option[Iterator[om.Item]] =
    resolveAncestorOrSelf(
      contextBindNodeOpt,
      targetStaticBind
    ) map Iterator.single orElse {
      resolveNotAncestorOrSelf(
        modelBinds,
        contextBindNodeOpt,
        targetStaticBind
      ) map { runtimeBindsIt =>
        runtimeBindsIt flatMap (_.items.asScala.iterator)
      }
    }

  // Try to resolve an ancestor-or-self bind
  def resolveAncestorOrSelf(contextBindNodeOpt: Option[BindNode], targetStaticBind: StaticBind): Option[om.NodeInfo] =
    contextBindNodeOpt flatMap (findAncestorOrSelfWithName(_, targetStaticBind.nameOpt)) map (_.node)

  // Try to resolve a bind which is not an ancestor-or-self bind
  def resolveNotAncestorOrSelf(
    modelBinds         : XFormsModelBinds,
    contextBindNodeOpt : Option[BindNode],
    targetStaticBind   : StaticBind
  ): Option[Iterator[RuntimeBind]] = {

    val contextAncestorOrSelfOpt = contextBindNodeOpt map (_.staticBind.ancestorOrSelfBinds.reverse)
    val targetAncestorOrSelf     = targetStaticBind.ancestorOrSelfBinds.reverse

    findStaticAncestry(contextAncestorOrSelfOpt, targetAncestorOrSelf) flatMap {
      case (commonStaticAncestorOpt, childBindOnTargetBranch) =>

        // We found, from the root, the first static binds which are different. If both of
        // them are nested binds, they have a common parent. If at least one of them is a
        // top-level bind, then they don't have a common parent.

        // If they are nested binds and therefore have a common parent, we start search at
        // the closest common bind iteration. Otherwise, we start search at the top-level.

        // From here we search in two ways:
        // 1. If the target bind is indexed and is is not ambiguous, check whether it is the one we want
        // 2. Otherwise, we search recursively toward bind leaves, following the path of target ids, and for
        //    each concrete target return all the items found.

        val concreteAncestorIteration =
          for {
            commonStaticAncestor <- commonStaticAncestorOpt
            contextBindNode      <- contextBindNodeOpt
          } yield
            findConcreteAncestorOrSelfIteration(commonStaticAncestor, contextBindNode)

        resolveSingle(
          modelBinds,
          targetStaticBind.staticId,
          concreteAncestorIteration
        ) orElse
          resolveMultiple(
            modelBinds,
            targetAncestorOrSelf,
            concreteAncestorIteration,
            childBindOnTargetBranch.staticId
          )
    }
  }

  def findAncestorOrSelfWithName(bindNode: BindNode, nameOpt: Option[String]) =
    bindNode.ancestorOrSelfBindNodes find (_.staticBind.nameOpt == nameOpt)

  def findStaticAncestry(branch1Opt: Option[List[StaticBind]], branch2: List[StaticBind]) =
    branch1Opt match {
      case Some(branch1) =>

        // branch2 can start with branch1 but not the opposite
        require(! branch1.startsWith(branch2))

        // Zip all with nulls so we can support case where branch2 start with branch1 and test on that below
        val zipIterator =
          branch1.ensuring(_.nonEmpty).iterator zipAll (branch2.ensuring(_.nonEmpty).iterator, null, null)

        zipIterator collectFirst {
          case (bindOnBranch1, bindOnBranch2) if bindOnBranch1 ne bindOnBranch2 =>
            (bindOnBranch2.parentBind, bindOnBranch2)
        }
      case None =>
        Some(None, branch2.head)
    }


  // NOTE: This requires that descendantBindNode is a descendant of a runtime bind associated with ancestorStaticBind.
  def findConcreteAncestorOrSelfIteration(ancestorStaticBind: StaticBind, descendantBindNode: BindNode) =
    descendantBindNode.ancestorOrSelfBindNodes collectFirst {
      case iteration: BindIteration if iteration.forStaticId == ancestorStaticBind.staticId => iteration
    } get

  def hasAncestorIteration(ancestorIteration: BindIteration, descendantRuntimeBind: RuntimeBind) =
    descendantRuntimeBind.parentIteration.ancestorOrSelfBindNodes exists (_ eq ancestorIteration)

  // Try to resolve using a non-ambiguous, indexed single-node context bind
  def resolveSingle(
    modelBinds                : XFormsModelBinds,
    targetBindId              : String,
    concreteAncestorIteration : Option[BindIteration]
  ): Option[Iterator[RuntimeBind]] = {

    def isValidTarget(singleNodeTarget: RuntimeBind) =
      concreteAncestorIteration match {
        // The binds have a common static ancestor and the target is a descendant of the same iteration
        case Some(ancestorIteration) if hasAncestorIteration(ancestorIteration, singleNodeTarget) => true
        // The binds are disjoint so the target is valid
        case None => true
        // The binds have a common static ancestor but the runtime target is disjoint
        case _ => false
      }

    for {
      singleNodeTarget <- modelBinds.singleNodeContextBinds.get(targetBindId)
      if isValidTarget(singleNodeTarget)
    } yield
      Iterator(singleNodeTarget)
  }

  def searchDescendantRuntimeBinds(targetAncestorOrSelf: List[StaticBind], rootBinds: Seq[RuntimeBind], rootId: String): Iterator[RuntimeBind] = {

    def nextNodes(binds: Iterator[RuntimeBind], path: List[String]): Iterator[RuntimeBind] = {

      require(path.nonEmpty)

      val nextBind = {
        val nextId = path.head
        binds find (_.staticId == nextId) get
      }

      path.tail match {
        case Nil =>
          // We are at a target: return all items
          Iterator(nextBind)//.items.asScala.iterator
        case pathTail =>
          // We need to dig deeper to reach the target
          for {
            nextBindNode <- nextBind.bindNodes.iterator.asInstanceOf[Iterator[BindIteration]]
            targetItem   <- nextNodes(nextBindNode.childrenBinds.iterator, pathTail)
          } yield
            targetItem
      }
    }

    val pathList = targetAncestorOrSelf map (_.staticId) dropWhile (rootId !=)
    nextNodes(rootBinds.iterator, pathList)
  }

  // Try to resolve by searching descendants nodes
  def resolveMultiple(
    modelBinds                : XFormsModelBinds,
    targetAncestorOrSelf      : List[StaticBind],
    concreteAncestorIteration : Option[BindIteration],
    rootId                    : String
  ): Option[Iterator[RuntimeBind]] = {

    val rootBinds = (
      concreteAncestorIteration
      map       (_.childrenBinds)
      getOrElse modelBinds.topLevelBinds
    )

    Some(searchDescendantRuntimeBinds(targetAncestorOrSelf, rootBinds, rootId))
  }
}