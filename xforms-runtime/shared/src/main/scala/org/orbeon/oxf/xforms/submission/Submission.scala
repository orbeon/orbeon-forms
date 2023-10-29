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
package org.orbeon.oxf.xforms.submission

import scala.concurrent.Future

trait Submission {
  def getType: String
  def isMatch(p: SubmissionParameters, p2: SecondPassParameters, sp: SerializationParameters): Boolean
  def connect(p: SubmissionParameters, p2: SecondPassParameters, sp: SerializationParameters): Option[ConnectResult Either Future[ConnectResult]]
}