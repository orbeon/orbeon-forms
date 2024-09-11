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


trait ElemListener {
  def startElement(element: ElementAnalysis): Unit
  def endElement(element: ElementAnalysis): Unit
}

trait WithChildrenTrait extends ElementAnalysis {

  // This element's children (valid after build() has been called)
  private var _children = Vector[ElementAnalysis]()
  final def children: Iterable[ElementAnalysis] = _children

  // NOTE: Should probably make it so that controls add themselves to their container upon creation
  final def addChildren(children: IterableOnce[ElementAnalysis]): Unit =
    _children ++= children

  final def removeChild(child: ElementAnalysis): Unit =
    _children = _children filterNot (_ eq child)

  final def descendantsOrSelf: Iterator[ElementAnalysis] =
    Iterator.single(this) ++ descendants

  // All this element's descendants (valid after build() has been called)
  final def descendants: Iterator[ElementAnalysis] = {

    def nestedChildrenBuilderTraits =
      _children.iterator collect { case child: WithChildrenTrait => child }

    _children.iterator ++ (nestedChildrenBuilderTraits flatMap (_.descendants))
  }

  final def visitDescendants(visitorListener: ElemListener): Unit = {

    def visitChildren(elem: WithChildrenTrait): Unit =
      for (childElem <- elem.children) {
        visitorListener.startElement(childElem)
        childElem match {
          case c: WithChildrenTrait => visitChildren(c)
          case _ =>
        }
        visitorListener.endElement(childElem)
      }

    visitChildren(this)
  }
}