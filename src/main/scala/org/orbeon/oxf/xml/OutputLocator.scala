/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.xml.sax._

// This is the Locator object passed to the output. It supports a stack of input Locator objects in order to
// correctly report location information of the included documents.
class OutputLocator extends Locator {

  private var locators: List[Locator] = Nil
  private def currentLocator = locators.headOption flatMap Option.apply

  // locator can be null
  def push(locator: Locator): Unit = locators ::= locator
  def pop(): Unit                  = locators = locators.tail

  def getPublicId     = currentLocator map (_.getPublicId)     orNull
  def getSystemId     = currentLocator map (_.getSystemId)     orNull
  def getLineNumber   = currentLocator map (_.getLineNumber)   getOrElse -1
  def getColumnNumber = currentLocator map (_.getColumnNumber) getOrElse -1

  def size = locators.size
}
