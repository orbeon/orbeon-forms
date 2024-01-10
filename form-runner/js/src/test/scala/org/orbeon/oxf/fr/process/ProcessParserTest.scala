package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.process.ProcessParser._
import org.scalatest.funspec.AnyFunSpec


class ProcessParserTest extends AnyFunSpec {

  describe("Basic process parsing") {

    val Expected = List(
      (
        """validate then save""",
        GroupNode(
          ActionNode("validate", Map()),
          List(
            (ThenCombinator, ActionNode("save", Map()))
          )
        )
      ),
      (
        """require-uploads
          |then validate-all
          |then save
          |then new-to-edit
          |then success-message("save-success")
          |recover error-message("database-error")""".stripMargin,
        GroupNode(
          ActionNode("require-uploads", Map()),
          List(
            (ThenCombinator,    ActionNode("validate-all",    Map())),
            (ThenCombinator,    ActionNode("save",            Map())),
            (ThenCombinator,    ActionNode("new-to-edit",     Map())),
            (ThenCombinator,    ActionNode("success-message", Map(None -> "save-success"))),
            (RecoverCombinator, ActionNode("error-message",   Map(None -> "database-error")))
          )
        )
      ),
      (
        """if ("xxf:get-request-method() = 'GET'")
          |then navigate(uri = "/fr/{fr:app-name()}/{fr:form-name()}/edit/{fr:document-id()}")
          |else edit""".stripMargin,
        GroupNode(
          ConditionNode(
            "xxf:get-request-method() = 'GET'",
            ActionNode("navigate", Map(Some("uri") -> "/fr/{fr:app-name()}/{fr:form-name()}/edit/{fr:document-id()}")),
            Some(ActionNode("edit",Map()))
          ),
          Nil
        )
      ),
      ( // NOTE: Also contains a "tab" character
        """success-message   ("save-success")
          |recover error-message
          |  ("database-error")""".stripMargin,
        GroupNode(
          ActionNode("success-message", Map(None -> "save-success")),
          List(
            (RecoverCombinator, ActionNode("error-message",   Map(None -> "database-error")))
          )
        )
      ),
    )

    for ((process, expected) <- Expected)
      it(s"must parse `$process`") {
        val r = ProcessParser.parse(process)
        assert(expected == r)
      }
  }
}