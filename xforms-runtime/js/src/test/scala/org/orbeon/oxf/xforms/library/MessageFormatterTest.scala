package org.orbeon.oxf.xforms.library

import org.scalatest.funspec.AnyFunSpec


class MessageFormatterTest extends AnyFunSpec {

  import MessageFormatter._

  val Expected = List(
    (
      "Simple number format",
      """Import of {0,number,integer}.""",
      Message(Vector(LiteralFormat("Import of "), NumberFormat(0), LiteralFormat("."))),
      List(
        (
          Vector(42),
          """Import of 42.""",
        )
      )
    ),
    (
      "Quotes, string format, number format, and quote strings",
      """Voici l''éléphant! Nom: {0}; Âge: {1, number, integer}. This is 'quoted, with a '' inside, and two quotes at the end: '''''!""",
      Message(
        Vector(
          LiteralFormat("Voici l'éléphant! Nom: "),
          StringFormat(0),
          LiteralFormat("; Âge: "),
          NumberFormat(1),
          LiteralFormat(". This is quoted, with a ' inside, and two quotes at the end: ''!")
        )
      ),
      List(
        (
          Vector("Dumbo", 5),
          """Voici l'éléphant! Nom: Dumbo; Âge: 5. This is quoted, with a ' inside, and two quotes at the end: ''!"""
        )
      )
    ),
    (
      "Simple choices",
      """Import of {0,number,integer} {0,choice,1#document|1<documents} is complete. {1,number,integer} invalid {1,choice,1#document was|1<documents were} skipped.""",
      Message(
        Vector(
          LiteralFormat("Import of "),
          NumberFormat(0),
          LiteralFormat(" "),
          ChoiceFormat(
            0,
            Vector(
              (
                ExactChoice(1),
                Message(Vector(LiteralFormat("document")))
              ),
              (
                ComparisonChoice(1),
                Message(Vector(LiteralFormat("documents")))
              )
            )
          ),
          LiteralFormat(" is complete. "),
          NumberFormat(1),
          LiteralFormat(" invalid "),
          ChoiceFormat(
            1
            ,Vector(
              (
                ExactChoice(1),
                Message(Vector(LiteralFormat("document was")))
              ),
              (
                ComparisonChoice(1),
                Message(Vector(LiteralFormat("documents were")))
              )
            )
          ),
          LiteralFormat(" skipped.")
        )
      ),
      List(
        (
          Vector(1, 1),
          """Import of 1 document is complete. 1 invalid document was skipped.""",
        ),
        (
          Vector(2, 1),
          """Import of 2 documents is complete. 1 invalid document was skipped.""",
        ),
        (
          Vector(12, 3),
          """Import of 12 documents is complete. 3 invalid documents were skipped.""",
        ),
      )
    ),
    (
      "Nested choices",
      """Your form {
        |  1,choice,
        |    0#does not contain any messages|
        |    1#contains the following '{0,choice,0#error|1#warning|2#informational message|3#message}'|
        |    1<contains the following '{0,choice,0#errors|1#warnings|2#informational messages|3#messages}'}""".stripMargin,
      Message(
        Vector(
          LiteralFormat("Your form "),
          ChoiceFormat(
            1,
            Vector(
              (
                ExactChoice(0),
                Message(Vector(LiteralFormat("does not contain any messages")))
              ),
              (
                ExactChoice(1),
                Message(
                  Vector(
                    LiteralFormat("contains the following "),
                    ChoiceFormat(
                      0,
                      Vector(
                        (
                          ExactChoice(0),
                          Message(Vector(LiteralFormat("error")))
                        ),
                        (
                          ExactChoice(1),
                          Message(Vector(LiteralFormat("warning")))
                        ),
                        (
                          ExactChoice(2),
                          Message(Vector(LiteralFormat("informational message")))
                        ),
                        (
                          ExactChoice(3),
                          Message(Vector(LiteralFormat("message")))
                        )
                      )
                    )
                  )
                )
              ),
              (
                ComparisonChoice(1),
                Message(
                  Vector(
                    LiteralFormat("contains the following "),
                    ChoiceFormat(
                      0,
                      Vector(
                        (
                          ExactChoice(0),
                          Message(Vector(LiteralFormat("errors")))
                        ),
                        (
                          ExactChoice(1),
                          Message(Vector(LiteralFormat("warnings")))
                        ),
                        (
                          ExactChoice(2),
                          Message(Vector(LiteralFormat("informational messages")))
                        ),
                        (
                          ExactChoice(3),
                          Message(Vector(LiteralFormat("messages")))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      ),
      List(
        (
          Vector(0, 0),
          """Your form does not contain any messages"""
        ),
        (
          Vector(0, 1),
          """Your form contains the following error"""
        ),
        (
          Vector(1, 1),
          """Your form contains the following warning"""
        ),
        (
          Vector(2, 1),
          """Your form contains the following informational message"""
        ),
        (
          Vector(3, 1),
          """Your form contains the following message"""
        ),
        (
          Vector(0, 2),
          """Your form contains the following errors"""
        ),
        (
          Vector(1, 2),
          """Your form contains the following warnings"""
        ),
        (
          Vector(2, 2),
          """Your form contains the following informational messages"""
        ),
        (
          Vector(3, 2),
          """Your form contains the following messages"""
        )
      )
    )
  )

  describe("Parse message formats") {

    for ((desc, string, expected, _) <- Expected)
      it(s"must pass `$desc`") {
        val parsed = MessageFormatter.parse(string)
        assert(expected == parsed)
      }
  }

  describe("Format using the given formats") {

    for ((desc, string, _, valuesWithExpected) <- Expected) {
      for ((values, expected) <- valuesWithExpected)
        it(s"must pass `$desc` for `$expected`") {
          val parsed    = MessageFormatter.parse(string)
          val formatted = MessageFormatter.format(parsed, values)
          assert(expected == formatted)
        }
    }
  }
}
