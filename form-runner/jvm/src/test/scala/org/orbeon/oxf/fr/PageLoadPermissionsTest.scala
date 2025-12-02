package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.orbeon.connection.StreamedContent
import org.orbeon.oxf.externalcontext.{Credentials, SimpleRole, UserAndGroup}
import org.orbeon.oxf.http.{HttpMethod, StatusCode}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.CoreUtils.*
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.charset.StandardCharsets


class PageLoadPermissionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Form Runner permissions on page load") {

    describe("#7373: New page still shows for form with no `create` permission") {

      val expectedCodeForParams = (_: (Option[Credentials], HttpMethod, Option[String], Boolean)) match {
        case (_,       HttpMethod.GET, _,       true) => StatusCode.Found     // TODO: should be `NotFound`, as this redirects to `/orbeon/not-found`!
        case (None, _, _, _)                          => StatusCode.Forbidden // no credentials, so no role can match
        case (Some(_), HttpMethod.GET, Some(_), _)    => StatusCode.Forbidden // special case, see `persistence-model.xml`
        case (Some(_), _, _, _)                       => StatusCode.Ok        // credentials provided with role
      }

      for {
        method              <- List(HttpMethod.GET, HttpMethod.POST)
        documentIdOpt       <- List(None, "12345".some)
        credentialsOpt      <- List(None, Credentials(UserAndGroup("my-user", None), List(SimpleRole("my-role")), Nil).some)
        background          <- List(false, true)
        expectedStatusCode  = expectedCodeForParams((credentialsOpt, method, documentIdOpt, background))
        contentOpt          =
          (method == HttpMethod.POST) option
            StreamedContent.fromBytes(
              "<form/>".getBytes(StandardCharsets.UTF_8),
              "application/xml;charset=UTF-8".some
            )
      } locally {

        it(s"must return status code $expectedStatusCode when calling with credentials $credentialsOpt ($documentIdOpt/$method/$background)") {

          val (_, docOpt, _, response) =
            runFormRunnerReturnAll("issue", "7373", "new", documentId = documentIdOpt, content = contentOpt, background = background, credentials = credentialsOpt)

          assert(response.statusCode == expectedStatusCode)
          assert(if (StatusCode.isSuccessCode(response.statusCode) && ! background) docOpt.isDefined else docOpt.isEmpty)
        }
      }
    }
  }
}
