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

import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.util.{IndentedLogger, PathUtils}
import org.orbeon.oxf.xforms.XFormsGlobalProperties
import org.orbeon.xforms.{UrlType, XFormsCrossPlatformSupport}


// TODO: Consider making subclasses objects and not holding on to `XFormsModelSubmission`.
abstract class BaseSubmission(val submission: XFormsModelSubmission) extends Submission {

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
      submission.containingDocument,
      submission.staticSubmission.element,
      PathUtils.appendQueryString(resolvedActionOrResource, queryString),
      if (isNorewrite) UrlRewriteMode.AbsoluteNoContext else UrlRewriteMode.Absolute
    )
  }

  protected def getDetailsLogger(
    submissionParameters: SubmissionParameters
  ): IndentedLogger = submission.getDetailsLogger(submissionParameters)

  protected def getTimingLogger(
    submissionParameters: SubmissionParameters
  ): IndentedLogger = submission.getTimingLogger(submissionParameters)
}

object BaseSubmission {
  def isLogBody: Boolean =
    XFormsGlobalProperties.getDebugLogging.contains("submission-body")
}
