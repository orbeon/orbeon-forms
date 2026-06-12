package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.permission.Operation
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.test.ResourceManagerSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Duration
import scala.util.{Failure, Success}


class FormRunnerCryptoTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  describe("FormRunnerAccessToken") {

    val app      = "acme"
    val form     = "order"
    val version  = 1
    val docOpt   = Some("abc123")
    val ops      = Set[Operation](Operation.Read, Operation.Update)
    val tokenHmac = FormRunnerAccessToken.TokenHmac(app, form, version, docOpt)

    it("must encrypt and decrypt successfully, preserving operations") {
      val token = FormRunnerAccessToken.encryptToken(app, form, version, docOpt, Duration.ofMinutes(10), ops)
      assert(token.isDefined)
      FormRunnerAccessToken.decryptToken(tokenHmac, token.get) match {
        case Success(payload) => assert(payload.ops.toSet == ops)
        case Failure(ex)      => fail(s"Decryption failed: $ex")
      }
    }

    it("must fail decryption when app in HMAC is wrong") {
      val token     = FormRunnerAccessToken.encryptToken(app, form, version, docOpt, Duration.ofMinutes(10), ops)
      val wrongHmac = FormRunnerAccessToken.TokenHmac("wrong-app", form, version, docOpt)
      assert(FormRunnerAccessToken.decryptToken(wrongHmac, token.get).isFailure)
    }

    it("must fail decryption when form in HMAC is wrong") {
      val token     = FormRunnerAccessToken.encryptToken(app, form, version, docOpt, Duration.ofMinutes(10), ops)
      val wrongHmac = FormRunnerAccessToken.TokenHmac(app, "wrong-form", version, docOpt)
      assert(FormRunnerAccessToken.decryptToken(wrongHmac, token.get).isFailure)
    }

    it("must fail decryption when document in HMAC is wrong") {
      val token     = FormRunnerAccessToken.encryptToken(app, form, version, docOpt, Duration.ofMinutes(10), ops)
      val wrongHmac = FormRunnerAccessToken.TokenHmac(app, form, version, Some("wrong-doc"))
      assert(FormRunnerAccessToken.decryptToken(wrongHmac, token.get).isFailure)
    }

    it("must fail decryption for a malformed token string") {
      assert(FormRunnerAccessToken.decryptToken(tokenHmac, "notavalidtoken").isFailure)
    }

    it("must pass expiration check for a valid token") {
      val token = FormRunnerAccessToken.encryptToken(app, form, version, docOpt, Duration.ofMinutes(10), ops)
      assert(FormRunnerAccessToken.decryptTokenPayloadCheckExpiration(tokenHmac, token.get).isSuccess)
    }

    it("must fail expiration check for an expired token") {
      val token = FormRunnerAccessToken.encryptToken(app, form, version, docOpt, Duration.ofSeconds(-1), ops)
      FormRunnerAccessToken.decryptTokenPayloadCheckExpiration(tokenHmac, token.get) match {
        case Failure(ex: HttpStatusCodeException) => assert(ex.code == StatusCode.Forbidden)
        case other                                => fail(s"Expected Failure with ${StatusCode.Forbidden}, got: $other")
      }
    }
  }

  describe("FormRunnerAdminToken") {

    it("must encrypt and decrypt with isInternalAdminUser = true") {
      val token = FormRunnerAdminToken.encryptToken(Duration.ofMinutes(10), isInternalAdminUser = true)
      assert(token.isDefined)
      FormRunnerAdminToken.decryptToken((), token.get) match {
        case Success(payload) => assert(payload.ops == true)
        case Failure(ex)      => fail(s"Decryption failed: $ex")
      }
    }

    it("must encrypt and decrypt with isInternalAdminUser = false") {
      val token = FormRunnerAdminToken.encryptToken(Duration.ofMinutes(10), isInternalAdminUser = false)
      assert(token.isDefined)
      FormRunnerAdminToken.decryptToken((), token.get) match {
        case Success(payload) => assert(payload.ops == false)
        case Failure(ex)      => fail(s"Decryption failed: $ex")
      }
    }

    it("must fail expiration check for an expired token") {
      val token = FormRunnerAdminToken.encryptToken(Duration.ofSeconds(-1), isInternalAdminUser = true)
      FormRunnerAdminToken.decryptTokenPayloadCheckExpiration((), token.get) match {
        case Failure(ex: HttpStatusCodeException) => assert(ex.code == StatusCode.Forbidden)
        case other                                => fail(s"Expected Failure with ${StatusCode.Forbidden}, got: $other")
      }
    }
  }

  describe("FormRunnerExternalModeToken") {

    it("must encrypt and decrypt successfully") {
      val token = FormRunnerExternalModeToken.encryptToken("acme", "order", 1, Some("doc123"), Duration.ofMinutes(10))
      assert(token.isDefined)
      FormRunnerExternalModeToken.decryptToken((), token.get) match {
        case Success(payload) => assert(payload.ops == (()))
        case Failure(ex)      => fail(s"Decryption failed: $ex")
      }
    }

    it("must fail expiration check for an expired token") {
      val token = FormRunnerExternalModeToken.encryptToken("acme", "order", 1, Some("doc123"), Duration.ofSeconds(-1))
      FormRunnerExternalModeToken.decryptTokenPayloadCheckExpiration((), token.get) match {
        case Failure(ex: HttpStatusCodeException) => assert(ex.code == StatusCode.Forbidden)
        case other                                => fail(s"Expected Failure with ${StatusCode.Forbidden}, got: $other")
      }
    }
  }

  describe("FormRunnerMcpToken") {

    val ops = Set[Operation](Operation.Read, Operation.Create)

    it("must encrypt and decrypt successfully, preserving operations") {
      val token = FormRunnerMcpToken.encryptToken(Duration.ofMinutes(10), ops)
      assert(token.isDefined)
      FormRunnerMcpToken.decryptToken((), token.get) match {
        case Success(payload) => assert(payload.ops.toSet == ops)
        case Failure(ex)      => fail(s"Decryption failed: $ex")
      }
    }

    it("must fail expiration check for an expired token") {
      val token = FormRunnerMcpToken.encryptToken(Duration.ofSeconds(-1), ops)
      FormRunnerMcpToken.decryptTokenPayloadCheckExpiration((), token.get) match {
        case Failure(ex: HttpStatusCodeException) => assert(ex.code == StatusCode.Forbidden)
        case other                                => fail(s"Expected Failure with ${StatusCode.Forbidden}, got: $other")
      }
    }
  }

  describe("FormRunnerOperationsEncryption") {

    it("must encrypt to a value different from the original") {
      val original  = "hello world"
      val encrypted = FormRunnerOperationsEncryption.encryptString(original)
      assert(encrypted != original)
    }

    it("must decrypt back to the original string") {
      val original  = "hello world"
      val encrypted = FormRunnerOperationsEncryption.encryptString(original)
      assert(FormRunnerOperationsEncryption.decryptString(encrypted).contains(original))
    }

    it("must return None for a blank string") {
      assert(FormRunnerOperationsEncryption.decryptString("").isEmpty)
      assert(FormRunnerOperationsEncryption.decryptString("   ").isEmpty)
    }
  }
}
