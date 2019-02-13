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

import org.orbeon.oxf.util.ConnectionResult

case class SubmissionResult(
  submissionEffectiveId : String,
  replacerOrThrowable   : Replacer Either Throwable,
  connectionResult      : ConnectionResult
) {

  def close(): Unit =
    Option(connectionResult) foreach (_.close())

  // For Java callers
  def this(submissionEffectiveId: String, replacer: Replacer, connectionResult: ConnectionResult) =
    this(
      submissionEffectiveId,
      Left(replacer),
      connectionResult
    )

  def this(submissionEffectiveId: String, throwable: Throwable, connectionResult: ConnectionResult) =
    this(
      submissionEffectiveId,
      Right(throwable),
      connectionResult
    )

  def getReplacer: Replacer = replacerOrThrowable.left.getOrElse(null)
  def getThrowable: Throwable = replacerOrThrowable.right.getOrElse(null)
}