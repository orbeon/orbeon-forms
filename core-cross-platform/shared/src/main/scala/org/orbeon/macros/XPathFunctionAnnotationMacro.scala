package org.orbeon.macros

import org.orbeon.oxf.util.CoreUtils.BooleanOps

import scala.annotation.{StaticAnnotation, compileTimeOnly, tailrec}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox


// TODO: `Iterable[]` return param/type
// TODO: `XPathContext` and `XFormsFunction.Context` implicits (or explicits)
// TODO: function flags (focus, etc.)

@compileTimeOnly("enable macro paradise to expand macro annotations")
class XPathFunction(name: String = null) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro XPathFunctionAnnotationMacro.impl
}

object XPathFunctionAnnotationMacro {

  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    import c.universe._

    val results = {
      annottees.map(_.tree).toList match {
        case q"$mods def $methodName[..$tpes](...$args): $returnType = { ..$body }" :: Nil =>

          val kebabMethodNameString = camelToKebab(methodName.toString)

          // See https://stackoverflow.com/questions/32631372/getting-parameters-from-scala-macro-annotation
          val resolvedMethodName =
            c.prefix.tree match {
              case q"new XPathFunction(name = $nameArg)" => nameArg
              case q"new XPathFunction($nameArg)"        => nameArg
              case q"new XPathFunction()"                => q"""$kebabMethodNameString"""
              case _                                     => c.abort(c.enclosingPosition, "Annotation `@XPathFunction` has incorrect parameters")
            }

          val (returnTypeIsOption, returnTypeString) =
            returnType match {
              case tq"Option[Int]"               => (true,  "int")
              case tq"Option[String]"            => (true,  "string")
              case tq"Option[Boolean]"           => (true,  "boolean")
              case tq"Option[java.time.Instant]" => (true,  "instant")
              case tq"Option[$tpe]"              => (true,  "item")
              case tq"Int"                       => (false, "int")
              case tq"String"                    => (false, "string")
              case tq"Boolean"                   => (false, "boolean")
              case tq"java.time.Instant"         => (false, "instant")
              case tq"$tpe"                      => (false, "item")
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
                      case q"$mods val $name: Option[Int]               = $rhs" => (true,  "int")
                      case q"$mods val $name: Option[String]            = $rhs" => (true,  "string")
                      case q"$mods val $name: Option[Boolean]           = $rhs" => (true,  "boolean")
                      case q"$mods val $name: Option[java.time.Instant] = $rhs" => (true,  "instant")
                      case q"$mods val $name: Option[$tpe]              = $rhs" => (true,  "item")
                      case q"$mods val $name: Int                       = $rhs" => (false, "int")
                      case q"$mods val $name: String                    = $rhs" => (false, "string")
                      case q"$mods val $name: Boolean                   = $rhs" => (false, "boolean")
                      case q"$mods val $name: java.time.Instant         = $rhs" => (false, "instant")
                      case q"$mods val $name: $tpe                      = $rhs" => (false, "item")
                    }

                  val (decodeCall, defaultOpt) = {
                    val q"$mods val $name: $tpt = $rhs" = arg
                    (q"""decodeSaxonArg[$tpt](args($argPosInList))""", rhs != EmptyTree option rhs)
                  }

                  (argPosInList, isOption, typeString, decodeCall, defaultOpt)

                case _ => throw new IllegalArgumentException
              }
            }

          val flattenedArguments = arguments.flatten

          val minArity = flattenedArguments.count(_._5.isEmpty)
          val maxArity = flattenedArguments.size

          if (flattenedArguments.takeWhile(_._5.isEmpty).size != minArity)
            throw new IllegalArgumentException(s"arguments with default values must be last")

          def getSaxonType(typeString: String) =
            typeString match {
              case "int"     => q"""org.orbeon.saxon.model.BuiltInAtomicType.INTEGER"""
              case "string"  => q"""org.orbeon.saxon.model.BuiltInAtomicType.STRING"""
              case "boolean" => q"""org.orbeon.saxon.model.BuiltInAtomicType.BOOLEAN"""
              case "instant" => q"""org.orbeon.saxon.model.BuiltInAtomicType.DATE_TIME"""
              case "item"    => q"""org.orbeon.saxon.pattern.AnyNodeTest"""
            }

          // Register one entry per distinct arity
          for (arity <- minArity to maxArity) yield {

            val classNameWithArity          = methodName.toString + "_xpathFunction" + arity
            val flattenedArgumentsUpToArity = flattenedArguments.take(arity)

            val register =
              q"""
                register(
                  $resolvedMethodName,
                  $arity,
                  () => new ${TypeName(classNameWithArity)},
                  ${getSaxonType(returnTypeString)},
                  $returnTypeCard,
                  0
                )
               """

            val registerWithArgs =
              flattenedArgumentsUpToArity.foldLeft(register) { case (result, (pos, isOption, typeString, _, _)) =>

                val card =
                  if (isOption)
                    q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.OPT"""
                  else
                    q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.ONE"""

                q"""
                   $result.arg(
                     $pos,
                     ${getSaxonType(typeString)},
                     $card,
                     null
                   )
                 """
              }

            val flattenedTrees   = flattenedArgumentsUpToArity.map(_._4)
            val defaultArgsTrees = flattenedArguments.drop(arity).flatMap(_._5)

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
                      ..$flattenedTrees,..$defaultArgsTrees
                    )
                  )
                """

            // We use `...$args` which means `Iterable[Iterable[Tree]]`
            q"""
              $registerWithArgs

              class ${TypeName(classNameWithArity)} extends org.orbeon.oxf.xml.FunctionSupport2 {
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
          }
        case _ => c.abort(c.enclosingPosition, "Annotation `@XPathFunction` can be used only on methods")
      }
    }

    val result = q"""..$results"""

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
