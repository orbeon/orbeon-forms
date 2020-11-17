package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.xforms.XFormsContainingDocument


trait XFormsStateManagerTrait extends XFormsStateLifecycle {

  /**
    * Return the delay for the session heartbeat event.
    *
    * @return delay in ms, or -1 is not applicable
    */
  def getHeartbeatDelay(containingDocument: XFormsContainingDocument, externalContext: ExternalContext): Long =
    if (containingDocument.staticState.isClientStateHandling || ! containingDocument.isSessionHeartbeat) {
      -1L
    } else {
      // 80% of session expiration time, in ms
      externalContext.getRequest.getSession(ForceSessionCreation).getMaxInactiveInterval * 800
    }

  // Ideally we wouldn't want to force session creation, but it's hard to implement the more elaborate expiration
  // strategy without session.
  protected val ForceSessionCreation = true

}
