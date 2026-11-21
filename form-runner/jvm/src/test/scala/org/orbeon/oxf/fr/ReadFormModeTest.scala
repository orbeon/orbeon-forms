package org.orbeon.oxf.fr

import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.scalatest.funspec.AnyFunSpecLike


class ReadFormModeTest
  extends DocumentTestBase
    with ResourceManagerSupport
    with AnyFunSpecLike
    with FormRunnerSupport {

  describe("Early mode validation in ReadFormProcessor") {

    it("must return 404 without creating an XForms document for unsupported modes") {
      for (mode <- List("default", "nonexistent", "landing")) {
        val (_, docOpt, _, response) = runFormRunnerReturnAll("issue", "5699", mode)
        assert(response.statusCode == StatusCode.NotFound)
        assert(docOpt.isEmpty)
      }
    }

    it("must allow supported detail modes") {
      val (_, docOpt, _, response) = runFormRunnerReturnAll("issue", "5699", "new")
      assert(response.statusCode == StatusCode.Ok)
      assert(docOpt.isDefined)
    }
  }
}
