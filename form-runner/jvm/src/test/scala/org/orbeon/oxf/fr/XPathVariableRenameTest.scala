package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.XMLConstants.{XSD_PREFIX, XSD_URI}
import org.orbeon.xml.NamespaceMapping
import org.scalatest.funspec.AnyFunSpec


class XPathVariableRenameTest extends AnyFunSpec {

  private val Logger = LoggerFactory.createLogger(classOf[XPathVariableRenameTest])
  private implicit val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  describe("Correctly replace variable references in supported cases") {

    val Expected = List(
      (false, """$foo""",                               "(fr:control-string-value('foo'))"),
      (false, """$foo, $foobar""",                      "(fr:control-string-value('foo')), (fr:control-string-value('foobar'))"),
      (false, """$foobar, $foo""",                      "(fr:control-string-value('foobar')), (fr:control-string-value('foo'))"),
      (false, """for $foo in 1 return $foo, $foobar""", """for $foo in 1 return $foo, (fr:control-string-value('foobar'))"""),
//      (false, """$foo, '$foo'""",                       "(fr:control-string-value('foo')), '$foo'"), // unsupported!
      (false, """$foo, '$foobar'""",                    "(fr:control-string-value('foo')), '$foobar'"),
      (false, """$x_, $x""",                            "(fr:control-string-value('x_')), (fr:control-string-value('x'))"),
      (false, """$x-, $x""",                            "(fr:control-string-value('x-')), (fr:control-string-value('x'))"),
      (false, """$x., $x""",                            "(fr:control-string-value('x.')), (fr:control-string-value('x'))"),
      (false, """$x·, $x""",                            "(fr:control-string-value('x·')), (fr:control-string-value('x'))"),
      (false, """concat($x, $y, $z)""",                 "concat((fr:control-string-value('x')), (fr:control-string-value('y')), (fr:control-string-value('z')))"),
      (true,  """This contains {$foo}""",               "This contains {(fr:control-string-value('foo'))}"),
      (true,  """{xs:integer($min) + 1}""",             "{xs:integer((fr:control-string-value('min'))) + 1}"),
      (true,  """{$min}""",                             "{(fr:control-string-value('min'))}"),
    )

    for ((avt, xpath, expected) <- Expected) {
      it(s"must pass for `$xpath`") {
        val result =
          FormRunnerRename.replaceVarReferencesWithFunctionCalls(
            xpath,
            NamespaceMapping(Map(XSD_PREFIX -> XSD_URI)),
            FormRunnerFunctionLibrary,
            avt,
            name => s"(fr:control-string-value('$name'))"
          )

        assert(result == expected)
      }
    }
  }
}
