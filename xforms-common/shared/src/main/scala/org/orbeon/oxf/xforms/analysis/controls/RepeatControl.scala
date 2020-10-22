/**
 *  Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

class RepeatControl(
  part             : PartAnalysisImpl,
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ContainerControl(part, index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
   with AppearanceTrait { // for separator appearance

  val iterationElement: Element = element.element(XFORMS_REPEAT_ITERATION_QNAME) ensuring (_ ne null)

  lazy val iteration: Option[RepeatIterationControl] = children collectFirst { case i: RepeatIterationControl => i }

  val isAroundTableOrListElement: Boolean = appearances(XXFORMS_SEPARATOR_APPEARANCE_QNAME)

  def isDnD: Boolean =
    element.attributeValueOpt(XXFORMS_DND_QNAME) exists (_ != "none")

  val dndClasses: Option[String] =
    element.attributeValueOpt(XXFORMS_DND_QNAME) filter
      Set("vertical", "horizontal")              map
      (dndType => s"xforms-dnd xforms-dnd-$dndType")

  private def findPositiveInt(attName: QName): Option[Int] =
    element.attributeValueOpt(attName) flatMap (_.trimAllToOpt) map (_.toInt) filter (_ > 0)

  val startIndex: Int =
    findPositiveInt(XXFORMS_REPEAT_STARTINDEX_QNAME) getOrElse  1

  override protected def externalEventsDef: Set[String] = super.externalEventsDef + XXFORMS_DND
  override val externalEvents             : Set[String] = externalEventsDef
}
