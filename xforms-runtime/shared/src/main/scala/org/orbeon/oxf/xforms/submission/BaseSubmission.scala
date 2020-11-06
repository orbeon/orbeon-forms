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

import cats.Eval
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.util.{IndentedLogger, PathUtils}
import org.orbeon.oxf.xforms.XFormsGlobalProperties
import org.orbeon.xforms.{UrlType, XFormsCrossPlatformSupport}

trait SubmissionProcess {
  def process(request: ExternalContext.Request, response: ExternalContext.Response)
}

abstract class BaseSubmission(val submission: XFormsModelSubmission) extends Submission {

  val containingDocument = submission.containingDocument

  protected def getAbsoluteSubmissionURL(
    resolvedActionOrResource : String,
    queryString              : String,
    isNorewrite              : Boolean,
    urlType                  : UrlType
  ): String = {

    // NOTE: For resolveServiceURL: If the resource or service URL does not start with a protocol or with '/', the
    // URL is resolved against  the request path, then against the service base. Example in servlet environment:
    //
    // - action path: my/service
    // - request URL: http://orbeon.com/orbeon/myapp/mypage
    // - request path: /myapp/mypage
    // - service base: http://services.com/myservices/
    // - resulting service URL: http://services.com/myservices/myapp/my/service

    val resolve =
      if (urlType == UrlType.Resource)
        XFormsCrossPlatformSupport.resolveResourceURL _
      else
        XFormsCrossPlatformSupport.resolveServiceURL _

    resolve(
      containingDocument,
      submission.staticSubmission.element,
      PathUtils.appendQueryString(resolvedActionOrResource, queryString),
      if (isNorewrite) URLRewriter.REWRITE_MODE_ABSOLUTE_NO_CONTEXT else URLRewriter.REWRITE_MODE_ABSOLUTE
    )
  }

  /**
   * Submit the `Eval` for synchronous or asynchronous execution.
   */
  protected def submitEval(
    p    : SubmissionParameters,
    p2   : SecondPassParameters,
    eval : Eval[SubmissionResult]
  ): Option[SubmissionResult] =
    if (p2.isAsynchronous) {
      // Tell XFCD that we have one more async submission
      containingDocument.getAsynchronousSubmissionManager(true).addAsynchronousSubmission(eval)
      // Tell caller he doesn't need to do anything
      None
    }  else if (p.isDeferredSubmissionSecondPass) {
      // Tell XFCD that we have a submission replace="all" ready for a second pass
      // Tell caller he doesn't need to do anything
      containingDocument.setReplaceAllEval(eval)
      None
    }  else {
      // Just run it now
      Option(eval.value)
    }

  protected def getDetailsLogger(
    p  : SubmissionParameters,
    p2 : SecondPassParameters
  ): IndentedLogger = submission.getDetailsLogger(p, p2)

  protected def getTimingLogger(
    p  : SubmissionParameters,
    p2 : SecondPassParameters
  ): IndentedLogger = submission.getTimingLogger(p, p2)
}

object BaseSubmission {
  def isLogBody: Boolean =
    XFormsGlobalProperties.getDebugLogging.contains("submission-body")
}
