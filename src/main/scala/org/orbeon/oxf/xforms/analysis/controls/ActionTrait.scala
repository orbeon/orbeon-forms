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
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.dom.{Namespace, QName}


trait ActionTrait extends SimpleElementAnalysis {

  import ActionTrait._

  private def find(qNames: Seq[QName]) = qNames.iterator map element.attributeValue find (_ ne null)

  val ifCondition     = find(IfQNames)
  val whileCondition  = find(WhileQNames)
  val iterate         = find(IterateQNames)

  def ifConditionJava    = ifCondition.orNull
  def whileConditionJava = whileCondition.orNull
  def iterateJava        = iterate.orNull
}

private object ActionTrait {

  val Namespaces    = Seq(Namespace.EmptyNamespace, XXFORMS_NAMESPACE, EXFORMS_NAMESPACE)
  def makeQName(s: String) = QName.get(s, _: Namespace)

  val IfQNames      = Namespaces map makeQName("if")
  val WhileQNames   = Namespaces map makeQName("while")
  val IterateQNames = Namespaces map makeQName("iterate")
}