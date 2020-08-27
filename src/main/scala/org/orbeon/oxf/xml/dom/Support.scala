/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.xml.dom

import org.orbeon.dom.{Element, QName}

import scala.annotation.tailrec


object Support {

  // Ensure that a path to an element exists by creating missing elements if needed
  def ensurePath(root: Element, path: Seq[QName]): Element = {

    @tailrec def insertIfNeeded(parent: Element, qNames: Iterator[QName]): Element =
      if (qNames.hasNext) {
        val qName = qNames.next()
        val existing = parent.elements(qName)

        val existingOrNew =
          existing.headOption getOrElse {
            val newElement = Element(qName)
            parent.add(newElement)
            newElement
          }

        insertIfNeeded(existingOrNew, qNames)
      } else
        parent

    insertIfNeeded(root, path.iterator)
  }
}
