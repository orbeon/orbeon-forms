/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.itemset

import org.xml.sax.SAXException

/**
 * Callback interface for visiting itemsets.
 */
trait ItemsetListener[T] {
  // NOTE: @throws because of Java callers.
  @throws(classOf[SAXException]) def startLevel(o: T, item: Item)
  @throws(classOf[SAXException]) def endLevel(o: T)
  @throws(classOf[SAXException]) def startItem(o: T, item: Item, first: Boolean)
  @throws(classOf[SAXException]) def endItem(o: T, item: Item)
}