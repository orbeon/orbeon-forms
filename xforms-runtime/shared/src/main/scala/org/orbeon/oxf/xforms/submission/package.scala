package org.orbeon.oxf.xforms

import org.orbeon.oxf.xforms.event.XFormsEvent.ActionPropertyGetter

package object submission {
  type SubmissionResult = (ConnectResult, Option[ActionPropertyGetter])
}