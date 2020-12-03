package org.orbeon.macros

import scala.annotation.{StaticAnnotation, compileTimeOnly, tailrec}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

// TODO: `Iterable[]` return param/type
// TODO: `XPathContext` and `XFormsFunction.Context` implicits (or explicits)
// TODO: function flags (focus, etc.)

@compileTimeOnly("enable macro paradise to expand macro annotations")
class XPathFunction extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro XPathFunctionAnnotationMacro.impl
}

object XPathFunctionAnnotationMacro {

  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    import c.universe._

    println(s"xxxx running macro impl")

    val result = {
      annottees.map(_.tree).toList match {
        case q"$mods def $methodName[..$tpes](...$args): $returnType = { ..$body }" :: Nil =>

          // We use `...$args` which means `Iterable[Iterable[Tree]]`
          val totalCardinality = args.flatten.size

          val (returnTypeIsOption, returnTypeString) =
            returnType match {
              case tq"Option[Int]"     => (true,  "int")
              case tq"Option[String]"  => (true,  "string")
              case tq"Option[Boolean]" => (true,  "boolean")
              case tq"Option[$tpe]"    => (true,  "item")
              case tq"Int"             => (false, "int")
              case tq"String"          => (false, "string")
              case tq"Boolean"         => (false, "boolean")
              case tq"$tpe"            => (false, "item")
            }

          val returnTypeCard =
            if (returnTypeIsOption)
              q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.OPT"""
            else
              q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.ONE"""

          val arguments =
            (args: Iterable[Iterable[Tree]]).zipWithIndex map { case (argList, argListPos) =>
              argList.zipWithIndex map {
                case (arg: ValDef, argPosInList) =>

                  val (isOption, typeString) =
                    arg match {
                      case q"$mods val $name: Option[Int]     = $rhs" => (true,  "int")
                      case q"$mods val $name: Option[String]  = $rhs" => (true,  "string")
                      case q"$mods val $name: Option[Boolean] = $rhs" => (true,  "boolean")
                      case q"$mods val $name: Option[$tpe]    = $rhs" => (true,  "item")
                      case q"$mods val $name: Int             = $rhs" => (false, "int")
                      case q"$mods val $name: String          = $rhs" => (false, "string")
                      case q"$mods val $name: Boolean         = $rhs" => (false, "boolean")
                      case q"$mods val $name: $tpe            = $rhs" => (false, "item")
                    }

                  val decodeCall = {
                    val q"$mods val $name: $tpt = $rhs" = arg
                    q"""decodeSaxonArg[$tpt](args($argPosInList)) """
                  }

                  (argPosInList, isOption, typeString, decodeCall)

                case _ => throw new IllegalArgumentException
              }
            }

          val flattenedTrees = arguments.flatten.map(_._4)

          val kebabMethodNameString = camelToKebab(methodName.toString)

          def getSaxonType(typeString: String) =
            typeString match {
              case "int"     => q"""org.orbeon.saxon.model.BuiltInAtomicType.INTEGER"""
              case "string"  => q"""org.orbeon.saxon.model.BuiltInAtomicType.STRING"""
              case "boolean" => q"""org.orbeon.saxon.model.BuiltInAtomicType.BOOLEAN"""
              case "item"    => q"""org.orbeon.saxon.pattern.AnyNodeTest"""
            }

          val register =
            q"""
              register(
                $kebabMethodNameString,
                0,
                () => new ${TypeName(methodName.toString + "_xpathFunction")},
                ${getSaxonType(returnTypeString)},
                $returnTypeCard,
                0
              )
             """

          val registerWithArgs =
            arguments.flatten.foldLeft(register) { case (result, (pos, isOption, typeString, _)) =>

              val card =
                if (isOption)
                  q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.OPT"""
                else
                  q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.ONE"""

              q"""
                 ${result}.arg(
                   $pos,
                   ${getSaxonType(typeString)},
                   $card,
                   null
                 )
               """
            }

          val encodedCall =
            if (args.isEmpty) // no parameter list!
              q"""
                encodeSaxonArg[$returnType](
                  $methodName[..$tpes]
                )
               """
            else
              q"""
                encodeSaxonArg[$returnType](
                  $methodName[..$tpes](
                    ..$flattenedTrees
                  )
                )
              """

          q"""
            $registerWithArgs

            class ${TypeName(methodName.toString + "_xpathFunction")} extends org.orbeon.oxf.xml.FunctionSupport2 {
              def call(
                context: org.orbeon.saxon.expr.XPathContext,
                args   : Array[org.orbeon.saxon.om.Sequence]
              ): org.orbeon.saxon.om.Sequence = {

                def $methodName[..$tpes](...$args): $returnType = {..$body}

                import org.orbeon.oxf.xml.FunctionSupport2._

                $encodedCall
              }
            }
          """
        case _ => c.abort(c.enclosingPosition, "Annotation `@XPathFunction` can be used only on methods")
      }
    }

    println("xxx XPathFunctionAnnotationMacro result:")
    println(result)

    c.Expr[Any](result)
  }

  // Inspired from function found online
  private def camelToKebab(name: String): String = {
    @tailrec
    def recurse(accDone: List[Char], acc: List[Char]): List[Char] = acc match {
      case Nil => accDone
      case a :: b :: c :: tail if a.isUpper && b.isUpper && c.isLower => recurse(accDone ++ List(a, '-', b, c), tail)
      case      a :: b :: tail if a.isLower && b.isUpper              => recurse(accDone ++ List(a, '-', b),    tail)
      case           a :: tail                                        => recurse(accDone :+ a,                  tail)
    }
    recurse(Nil, name.toList).mkString.toLowerCase
  }
}
