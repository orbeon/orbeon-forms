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


object NoneReplacer extends Replacer {

  type DeserializeType = Unit

  def deserialize(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters
  ): DeserializeType = ()

  // Just notify that processing is terminated by dispatching `xforms-submit-done`
  def replace(
    submission          : XFormsModelSubmission,
    cxr                 : ConnectionResult,
    submissionParameters: SubmissionParameters,
    value               : DeserializeType
  )(implicit
    refContext          : RefContext
  ): ReplaceResult =
    ReplaceResult.SendDone(cxr, submissionParameters.tunnelProperties)
}