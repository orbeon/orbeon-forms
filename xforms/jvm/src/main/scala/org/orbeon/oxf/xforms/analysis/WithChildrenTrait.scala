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

import scala.collection.compat._

trait WithChildrenTrait extends ElementAnalysis {

  // This element's children (valid after build() has been called)
  private var _children = Seq[ElementAnalysis]()
  final def children: Seq[ElementAnalysis] = _children

  // NOTE: Should probably make it so that controls add themselves to their container upon creation
  final def addChildren(children: IterableOnce[ElementAnalysis]): Unit =
    _children ++= children

  final def removeChild(child: ElementAnalysis): Unit =
    _children = _children filterNot (_ eq child)

  // All this element's descendants (valid after build() has been called)
  final def descendants: Iterator[ElementAnalysis] = {

    def nestedChildrenBuilderTraits =
      _children.iterator collect { case child: WithChildrenTrait => child }

    _children.iterator ++ (nestedChildrenBuilderTraits flatMap (_.descendants))
  }

  // Some elements can create and index elements which are not processed as descendants above. To enable de-indexing,
  // they can override indexedElements to add elements to de-index.
  // Overridden by `Model`
  def indexedElements: Iterator[ElementAnalysis] = {

    def nestedChildrenBuilderTraits =
      _children.iterator collect { case child: WithChildrenTrait => child }

    _children.iterator ++ (nestedChildrenBuilderTraits flatMap (_.indexedElements))
  }
}