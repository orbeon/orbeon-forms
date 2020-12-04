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

import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.xforms.XFormsNames._


trait ActionTrait extends ElementAnalysis {

  import ActionTrait._

  private def find(qNames: Seq[QName]): Option[String] = qNames.iterator map element.attributeValue find (_ ne null)

  var ifCondition   : Option[String] = find(IfQNames)
  var whileCondition: Option[String] = find(WhileQNames)
  var iterate       : Option[String] = find(IterateQNames)
}

private object ActionTrait {

  val Namespaces: Seq[Namespace] = Seq(Namespace.EmptyNamespace, XXFORMS_NAMESPACE, EXFORMS_NAMESPACE)

  def makeQName(s: String): Namespace => QName = QName(s, _)

  val IfQNames      : Seq[QName] = Namespaces map makeQName("if")
  val WhileQNames   : Seq[QName] = Namespaces map makeQName("while")
  val IterateQNames : Seq[QName] = Namespaces map makeQName("iterate")
}