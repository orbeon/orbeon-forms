package org.orbeon.oxf.xforms.model

import org.scalatest.funspec.AnyFunSpecLike


class EmailValidatorTest extends AnyFunSpecLike {

  describe("Default email validation") {

    val validator = EmailValidator.Default

    it("must accept valid email addresses") {
      val validAddresses = List(
        "test@example.com",
        "first.last@example.com",
        "user+tag@example.com",
        "user-name@sub.example.com",
        "123@example.com",
        "a@b.com",
        "a@b.brussels",
        "a@b.photography",
        "~my_mail+{nospam}$?@sub-domain.example.info"
      )
      validAddresses.foreach { address =>
        assert(validator.isValid(address))
      }
    }

    it("must reject invalid email addresses") {
      val invalidAddresses = List(
        null,
        "",
        "notanemail",
        "@example.com",
        "user@",
        "user@localhost",
        "user@example.c",
        "user@example..com",
        "user@.example.com",
        "user@example.com.",
        "user @example.com",
        "user@ example.com",
        "mailto:user@example.com",
        "a" * 65 + "@example.com"
      )
      invalidAddresses.foreach { address =>
        assert(! validator.isValid(address))
      }
    }
  }

  describe("Custom local-part regex validation") {

    it("must support disallowing trailing underscore before the at sign") {
      val validator = EmailValidator(localPartRegexOpt = Some(".*[^_]"), domainRegexOpt = None)

      assert(validator.isValid("john@example.com"))
      assert(validator.isValid("john_doe@example.com"))
      assert(! validator.isValid("john_@example.com"))
      assert(! validator.isValid("_@example.com"))
    }

    it("must support restricting local-part characters") {
      val validator = EmailValidator(localPartRegexOpt = Some("^[a-z0-9]+$"), domainRegexOpt = None)

      assert(validator.isValid("john123@example.com"))
      assert(! validator.isValid("john.doe@example.com"))
      assert(! validator.isValid("John@example.com"))
      assert(! validator.isValid("john+tag@example.com"))
    }

    it("must enforce built-in checks in addition to custom local-part regex") {
      val validator = EmailValidator(localPartRegexOpt = Some(".*"), domainRegexOpt = None)

      assert(! validator.isValid("user@localhost"))
      assert(! validator.isValid("user@example.c"))
      assert(! validator.isValid("a" * 65 + "@example.com"))
    }
  }

  describe("Custom domain regex validation") {

    it("must restrict allowed domain names") {
      val validator = EmailValidator(localPartRegexOpt = None, domainRegexOpt = Some("(?i)(example\\.com|orbeon\\.com)"))

      assert(validator.isValid("john@example.com"))
      assert(validator.isValid("john@orbeon.com"))
      assert(validator.isValid("john@ORBEON.COM"))
      assert(! validator.isValid("john@other.com"))
      assert(! validator.isValid("john@sub.example.com"))
    }

    it("must enforce built-in domain checks in addition to custom domain regex") {
      val validator = EmailValidator(localPartRegexOpt = None, domainRegexOpt = Some(".*"))

      assert(! validator.isValid("john@localhost"))
      assert(! validator.isValid("john@example.c"))
    }
  }

  describe("Both custom local-part and domain regexes combined") {

    it("must validate both parts against custom regexes") {
      val validator = EmailValidator(
        localPartRegexOpt = Some(".*[^_]"),
        domainRegexOpt    = Some("(?i)(example\\.com)")
      )

      assert(validator.isValid("john@example.com"))
      assert(! validator.isValid("john_@example.com"))
      assert(! validator.isValid("john@other.com"))
      assert(! validator.isValid("john_@other.com"))
    }
  }

  describe("Regex compilation error handling") {

    it("must throw IllegalArgumentException on invalid local-part regex") {
      assertThrows[IllegalArgumentException] {
        EmailValidator(localPartRegexOpt = Some("[a-z"), domainRegexOpt = None)
      }
    }

    it("must throw IllegalArgumentException on invalid domain regex") {
      assertThrows[IllegalArgumentException] {
        EmailValidator(localPartRegexOpt = None, domainRegexOpt = Some("[a-z"))
      }
    }
  }
}
