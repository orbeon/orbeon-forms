package org.orbeon.oxf.fb.mcp

import org.orbeon.oxf.xforms.XFormsContainingDocument


private[mcp] final case class BuilderSession(
  documentId : String,
  document   : XFormsContainingDocument,
  var lastAccess: Long
)
