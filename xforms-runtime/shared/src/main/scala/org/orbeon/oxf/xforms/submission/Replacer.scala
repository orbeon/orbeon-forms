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
package org.orbeon.oxf.xforms.submission

import org.orbeon.connection.ConnectionResult


trait Replacer {

  type DeserializeType

  def deserialize(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters
  ): DeserializeType

  // NOTE: This is allowed to throw exceptions, including `XFormsSubmissionException`.
  def replace(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters,
    value               : DeserializeType
  )(implicit
    refContext          : RefContext
  ): ReplaceResult
}