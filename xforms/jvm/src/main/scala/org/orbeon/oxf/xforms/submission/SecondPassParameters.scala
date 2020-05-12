package org.orbeon.oxf.xforms.submission

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.http.{Credentials, HttpMethod}
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.submission.SubmissionUtils._

case class SecondPassParameters(

  actionOrResource   : String,
  isAsynchronous     : Boolean,
  credentialsOpt     : Option[Credentials],

  // Serialization
  separator          : String,
  encoding           : String,

  // XML serialization
  versionOpt         : Option[String],
  indent             : Boolean,
  omitXmlDeclaration : Boolean,
  standaloneOpt      : Option[Boolean],

  // Response
  isHandleXInclude   : Boolean,
  isReadonly         : Boolean,
  applyDefaults      : Boolean,
  isCache            : Boolean,
  timeToLive         : Long
)

object SecondPassParameters {

  private val DefaultSeparator = "&" // XForms 1.1 changes back the default to `&` as of February 2009
  private val DefaultEncoding  = CharsetNames.Utf8
  private val Asynchronous     = "asynchronous"
  private val Application      = "application"
  private val CacheableMethods = Set(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT): Set[HttpMethod]

  def amendForJava(p: SecondPassParameters, isAsynchronous: Boolean, isReadonly: Boolean): SecondPassParameters =
    p.copy(isAsynchronous = isAsynchronous, isReadonly = isReadonly)

  def apply(dynamicSubmission: XFormsModelSubmission, p: SubmissionParameters): SecondPassParameters = {

    val staticSubmission = dynamicSubmission.staticSubmission

    implicit val containingDocument = dynamicSubmission.containingDocument
    implicit val refContext         = p.refContext

    // Maybe: See if we can resolve `xml:base` early to detect absolute URLs early as well.
    // actionOrResource = XFormsUtils.resolveXMLBase(containingDocument, getSubmissionElement(), NetUtils.encodeHRRI(temp, true)).toString();
    val actionOrResource =
      stringAvtTrimmedOpt(staticSubmission.avtActionOrResource) match {
        case Some(resolved) =>
          NetUtils.encodeHRRI(resolved, true)
        case None =>
          throw new XFormsSubmissionException(
            submission  = dynamicSubmission,
            message     = s"xf:submission: mandatory `resource` or `action` evaluated to an empty sequence for attribute value: `${staticSubmission.avtActionOrResource}`",
            description = "resolving resource URI"
          )
      }

    val credentialsOpt =
      staticSubmission.avtXxfUsernameOpt flatMap
        stringAvtTrimmedOpt              map { username =>

        Credentials(
          username       = username,
          password       =    staticSubmission.avtXxfPasswordOpt       flatMap stringAvtTrimmedOpt,
          preemptiveAuth = ! (staticSubmission.avtXxfPreemptiveAuthOpt flatMap stringAvtTrimmedOpt contains false.toString),
          domain         =    staticSubmission.avtXxfDomainOpt         flatMap stringAvtTrimmedOpt
        )
      }

    val isReadonly =
      staticSubmission.avtXxfReadonlyOpt flatMap booleanAvtOpt getOrElse false

    val isCache =
      staticSubmission.avtXxfCacheOpt flatMap booleanAvtOpt getOrElse {
        // For backward compatibility
        staticSubmission.avtXxfSharedOpt flatMap stringAvtTrimmedOpt contains Application
      }

    // Check read-only and cache hints
    if (isCache) {

      if (! CacheableMethods(p.httpMethod))
        throw new XFormsSubmissionException(
          submission  = dynamicSubmission,
          message     = """xf:submission: `xxf:cache="true"` or `xxf:shared="application"` can be set only with `method="get|post|put"`.""",
          description = "checking read-only and shared hints"
        )

      if (p.replaceType != ReplaceType.Instance)
        throw new XFormsSubmissionException(
          submission  = dynamicSubmission,
          message     = """xf:submission: `xxf:cache="true"` or `xxf:shared="application"` can be set only with `replace="instance"`.""",
          description = "checking read-only and shared hints"
        )

    } else if (isReadonly && p.replaceType != ReplaceType.Instance)
      throw new XFormsSubmissionException(
        submission  = dynamicSubmission,
        message     = """xf:submission: `xxf:readonly="true"` can be `true` only with `replace="instance"`.""",
        description = "checking read-only and shared hints"
      )

    // NOTE: XForms 1.1 default to async, but we don't fully support async so we default to sync instead
    val isAsynchronous = {

      val isRequestedAsynchronousMode =
        staticSubmission.avtModeOpt flatMap stringAvtTrimmedOpt contains Asynchronous

      // For now we don't support replace="all"
      if (isRequestedAsynchronousMode && p.replaceType == ReplaceType.All)
        throw new XFormsSubmissionException(
          submission  = dynamicSubmission,
          message     = """xf:submission: `mode="asynchronous"` cannot be `true` with `replace="all"`.""",
          description = "checking asynchronous mode"
        )

      p.replaceType != ReplaceType.All && isRequestedAsynchronousMode
    }

    SecondPassParameters(

      actionOrResource   = actionOrResource,
      isAsynchronous     = isAsynchronous,
      credentialsOpt     = credentialsOpt,

      separator          = staticSubmission.avtSeparatorOpt          flatMap stringAvtTrimmedOpt getOrElse DefaultSeparator,
      encoding           = staticSubmission.avtEncodingOpt           flatMap stringAvtTrimmedOpt getOrElse DefaultEncoding,

      versionOpt         = staticSubmission.avtVersionOpt            flatMap stringAvtTrimmedOpt,
      indent             = staticSubmission.avtIndentOpt             flatMap booleanAvtOpt       getOrElse false,
      omitXmlDeclaration = staticSubmission.avtOmitXmlDeclarationOpt flatMap booleanAvtOpt       getOrElse false,
      standaloneOpt      = staticSubmission.avtStandalone            flatMap booleanAvtOpt,

      isHandleXInclude   = staticSubmission.avtXxfHandleXInclude     flatMap booleanAvtOpt       getOrElse false,
      isReadonly         = isReadonly,
      applyDefaults      = staticSubmission.avtXxfDefaultsOpt        flatMap booleanAvtOpt       getOrElse false,
      isCache            = isCache,
      timeToLive         = staticSubmission.timeToLive
    )
  }
}