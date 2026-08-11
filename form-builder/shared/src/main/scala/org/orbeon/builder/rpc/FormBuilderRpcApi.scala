/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.builder.rpc

import org.orbeon.datatypes.Direction

import scala.concurrent.Future


trait FormBuilderRpcApi {

  def unsupportedBrowser(browserName: String, browserVersion: Double): Future[Unit]

  def controlUpdateLabelOrHintOrText (controlId: String, lhha: String, value: String, isHTML: Boolean): Future[Unit]

  def controlDelete       (controlId: String): Future[Unit]
  def controlEditDetails  (controlId: String): Future[Unit]
  def controlEditItems    (controlId: String): Future[Unit]
  def controlDnD          (controlId: String, destCellId: String, copy: Boolean): Future[Unit]

  def rowInsert           (controlId: String, position: Int, aboveBelowString: String): Future[Unit]
  def rowDelete           (controlId: String, position: Int): Future[Unit]

  def moveWall            (cellId: String, startSide: Direction, target: Int): Future[Unit]
  def splitY              (cellId: String): Future[Unit]
  def mergeRight          (cellId: String): Future[Unit]
  def mergeDown           (cellId: String): Future[Unit]
  def splitX              (cellId: String): Future[Unit]

  def sectionUpdateLabel  (sectionId: String, label: String): Future[Unit]
  def sectionMove         (sectionId: String, directionString: String): Future[Unit]

  def containerDelete     (containerId: String): Future[Unit]
  def containerEditDetails(containerId: String): Future[Unit]
  def containerCopy       (containerId: String): Future[Unit]
  def containerCut        (containerId: String): Future[Unit]
  def containerMerge      (containerId: String): Future[Unit]

  def pasteItemsetFromClipboard(tsvString: String): Future[Unit]
}
