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
package org.orbeon.oxf.xml

import org.orbeon.saxon.expr.*
import org.orbeon.saxon.om


//trait DefaultFunctionSupport
//  extends FunctionSupport
//    with NoPathMapDependencies
//    with NoPreEvaluate
//
//trait RuntimeDependentFunction extends DefaultFunctionSupport {
//  // TODO: Saxon 10
////  override def getIntrinsicDependencies: Int =
////    super.getIntrinsicDependencies | StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT
//}
//
//trait DependsOnContextItem extends FunctionSupport {
//  // TODO: Saxon 10
////  override def getIntrinsicDependencies: Int =
////    super.getIntrinsicDependencies | StaticProperty.DEPENDS_ON_CONTEXT_ITEM
//}
//
//// Mix-in for functions which use the context if single optional argument is missing
//trait DependsOnContextItemIfSingleArgumentMissing extends FunctionSupport {
//  // TODO: Saxon 10
////  override def getIntrinsicDependencies: Int =
////    super.getIntrinsicDependencies | (if (arguments.isEmpty) StaticProperty.DEPENDS_ON_CONTEXT_ITEM else 0)
//}
//
//trait NoPathMapDependencies extends SystemFunction {
//  // TODO: Saxon 10
////  override def addToPathMap(
////    pathMap        : PathMap,
////    pathMapNodeSet : PathMapNodeSet
////  ): PathMapNodeSet = {
////    pathMap.setInvalidated(true)
////    null
////  }
//}
//
///**
// * preEvaluate: this method suppresses compile-time evaluation by doing nothing
// * (because the value of the expression depends on the runtime context)
// *
// * NOTE: A few functions would benefit from not having this, but it is always safe.
// */
//trait NoPreEvaluate extends SystemFunction {
//  // TODO: Saxon 10
////  override def preEvaluate(visitor: ExpressionVisitor): Expression = this
//}

object FunctionSupport {

  // We make a distinction between `null` and `None`: `null` correspond to the absence of an argument, in which case
  // we use the context, while `None` corresponds to an empty sequence, which we leave unchanged.
  def stringArgumentOrContextOpt(s: Option[String])(implicit xpc: XPathContext): Option[String] =
    if (s eq null) Option(xpc.getContextItem) map (_.getStringValue) else s
  def itemArgumentOrContextOpt(i: Option[om.Item])(implicit xpc: XPathContext): Option[om.Item] =
    if (i eq null) Option(xpc.getContextItem) else i
}
