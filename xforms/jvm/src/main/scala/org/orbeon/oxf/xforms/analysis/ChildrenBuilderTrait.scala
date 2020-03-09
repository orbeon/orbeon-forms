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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xml.{XMLReceiverHelper, Dom4j}
import scala.collection.compat._

trait ChildrenBuilderTrait extends ElementAnalysis {

  type Builder = (ElementAnalysis, Option[ElementAnalysis], Element, Scope) => Option[ElementAnalysis]

  def findRelevantChildrenElements: Seq[(Element, Scope)] = findAllChildrenElements

  // Default implementation: return all children element with the same container scope as the parent element
  protected def findAllChildrenElements: Seq[(Element, Scope)] = Dom4j.elements(element) map ((_, containerScope))

  // This element's children (valid after build() has been called)
  private var _children = Seq[ElementAnalysis]()
  final def children: Seq[ElementAnalysis] = _children

  // NOTE: Should probably make it so that controls add themselves to their container upon creation
  final def addChildren(children: IterableOnce[ElementAnalysis]): Unit =
    _children ++= children

  final def removeChild(child: ElementAnalysis): Unit =
    _children = _children filterNot (_ eq child)

  // All this element's descendants (valid after build() has been called)
  final def descendants: Seq[ElementAnalysis] = {

    def nestedChildrenBuilderTraits =
      _children collect { case child: ChildrenBuilderTrait => child }

    _children ++ (nestedChildrenBuilderTraits flatMap (_.descendants))
  }

  // Some elements can create and index elements which are not processed as descendants above. To enable de-indexing,
  // they can override indexedElements to add elements to de-index.
  def indexedElements: Seq[ElementAnalysis] = {

    def nestedChildrenBuilderTraits =
      _children collect { case child: ChildrenBuilderTrait => child }

    _children ++ (nestedChildrenBuilderTraits flatMap (_.indexedElements))
  }

  // Build this element's children and its descendants
  final def build(builder: Builder): Unit = {

    def buildChildren() = {

      // NOTE: Making `preceding` hold a side effect here is a bit unclear and error-prone.
      var preceding: Option[ElementAnalysis] = None

      // Build and collect the children
      val childrenOptions =
        for ((childElement, childContainerScope) <- findRelevantChildrenElements)
          yield builder(this, preceding, childElement, childContainerScope) collect {
            // The element has children
            case newControl: ChildrenBuilderTrait =>
              newControl.build(builder)
              preceding = Some(newControl)
              newControl
            // The element does not have children
            case newControl =>
              preceding = Some(newControl)
              newControl
          }

      // Return the direct children built
      childrenOptions.flatten
    }

    // Build direct children
    _children = buildChildren()
  }

  override def toXMLContent(helper: XMLReceiverHelper): Unit = {
    super.toXMLContent(helper)
    children foreach (_.toXML(helper))
  }
}