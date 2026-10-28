package org.orbeon.oxf.xforms.model

import org.orbeon.xforms.XFormsNames
import org.scalatest.funspec.AnyFunSpecLike


class XFormsModelSchemaValidatorJsTest extends AnyFunSpecLike {

  describe("Built-in xforms-types.xsd validation in JS environment") {

    lazy val xformsValidator = {
      val validator = new XFormsModelSchemaValidator("oxf:/org/orbeon/oxf/xforms/xforms-types.xsd")
      validator.loadSchemas(null)
      validator
    }

    it("must validate xf:card-number") {
      assert(
        xformsValidator.validateDatatype(
          value            = "541234567890125",
          typeNamespaceURI = XFormsNames.XFORMS_NAMESPACE_URI,
          typeLocalname    = "card-number",
          typeQName        = "xf:card-number",
          locationData     = null
        ) eq null
      )

      assert(
        xformsValidator.validateDatatype(
          value            = "123",
          typeNamespaceURI = XFormsNames.XFORMS_NAMESPACE_URI,
          typeLocalname    = "card-number",
          typeQName        = "xf:card-number",
          locationData     = null
        ) ne null
      )
    }

    it("must validate xf:dayTimeDuration and xf:yearMonthDuration") {
      assert(
        xformsValidator.validateDatatype(
          value            = "P1DT2H",
          typeNamespaceURI = XFormsNames.XFORMS_NAMESPACE_URI,
          typeLocalname    = "dayTimeDuration",
          typeQName        = "xf:dayTimeDuration",
          locationData     = null
        ) eq null
      )

      assert(
        xformsValidator.validateDatatype(
          value            = "invalid-duration",
          typeNamespaceURI = XFormsNames.XFORMS_NAMESPACE_URI,
          typeLocalname    = "dayTimeDuration",
          typeQName        = "xf:dayTimeDuration",
          locationData     = null
        ) ne null
      )

      assert(
        xformsValidator.validateDatatype(
          value            = "P2Y6M",
          typeNamespaceURI = XFormsNames.XFORMS_NAMESPACE_URI,
          typeLocalname    = "yearMonthDuration",
          typeQName        = "xf:yearMonthDuration",
          locationData     = null
        ) eq null
      )

      assert(
        xformsValidator.validateDatatype(
          value            = "P1D",
          typeNamespaceURI = XFormsNames.XFORMS_NAMESPACE_URI,
          typeLocalname    = "yearMonthDuration",
          typeQName        = "xf:yearMonthDuration",
          locationData     = null
        ) ne null
      )
    }
  }
}
