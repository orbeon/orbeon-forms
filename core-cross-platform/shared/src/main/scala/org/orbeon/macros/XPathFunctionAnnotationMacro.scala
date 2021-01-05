package org.orbeon.macros

import cats.syntax.option._
import org.orbeon.oxf.util.CoreUtils.BooleanOps

import scala.annotation.{StaticAnnotation, compileTimeOnly, tailrec}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox


// The purpose of this macro is to create bridges to the Saxon XPath library simply using
// the `@XPathFunction` annotation placed on a method. The macro analyzes the method's
// signature, and:
//
// - builds a call to a `register()` function that must be in scope, typically because the
//   methods are in a trait or class extending `OrbeonFunctionLibrary` or `BuiltInFunctionSet`.
//     - create one `register()` call for each arity of the function, where there can be
//       multiple arities depending on whether the method has one more more parameters with
//       default values
// - creates a new local class that extends `FunctionSupport2` and implements the `call()`
//   method
//     - parameters and the return value are converted using support in `FunctionSupport2`
// - locally scopes implicits for `XPathContext` and `XFormsFunction.Context`
//     - these are inserted only if there is at least one implicit parameter list
//
// The original methods are not removed from the original scope. (TODO)
//
// The XPath function is named by kebab-casing the Scala method name. You can override this
// by passing a `name` parameter to the annotation.
//
// Supported types so far:
//
// - `Int`
// - `Long`
// - `String`
// - `Boolean`
// - `Item`
// - `NodeInfo`
// - `AtomicValue`
// - `Instant`
// - `Option`
// - `Iterable`
//
// This allows writing new XPath functions in a very lightweight and safe way.
//
// The only drawback we see if that macro annotations are not supported by Scala 3. But moving
// to Scala 3 is still quite a way off, and we hope that a solution will be available at some
// point. In the worst case scenario, XPath functions interfaces will have to be remain written
// with Scala 2 for longer.
//
// TODO:
//
// - [ ] support `Map` types
// - [ ] rename `FunctionSupport2`
// - [ ] rename `XFormsFunction.Context`
// - [ ] avoid copying the method!
// - [ ] function flags (focus, etc.)
// - [ ] allow specifying super trait like `InstanceTrait` for `PathMapXPathAnalysisBuilder`
//
@compileTimeOnly("enable macro paradise to expand macro annotations")
class XPathFunction(name: String = null) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro XPathFunctionAnnotationMacro.impl
}

object XPathFunctionAnnotationMacro {

  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    import c.universe._

    val results = {

      val (methodName, tpes, regularArgs, implicitArgs, returnType, body) =
        annottees.map(_.tree).toList match {
          case q"$mods def $methodName[..$tpes](..$args)(implicit ..$implicitArgs): $returnType = { ..$body }" :: Nil if implicitArgs.nonEmpty =>
            // Not sure why I am getting a match sometimes, but not always, with `implicitArgs.isEmpty`.
            // So adding above the check for `implicitArgs.nonEmpty`.
            (methodName, tpes, args, implicitArgs.some, returnType, body)
          case q"$mods def $methodName[..$tpes](...$args): $returnType = { ..$body }" :: Nil =>
            (methodName, tpes, args.flatten, None, returnType, body)
          case _ => c.abort(c.enclosingPosition, "Annotation `@XPathFunction` can be used only on methods")
        }

      val kebabMethodNameString = camelToKebab(methodName.toString)

      // See https://stackoverflow.com/questions/32631372/getting-parameters-from-scala-macro-annotation
      val resolvedFunctionName =
        c.prefix.tree match {
          case q"new XPathFunction(name = $nameArg)" => nameArg
          case q"new XPathFunction($nameArg)"        => nameArg
          case q"new XPathFunction()"                => q"""$kebabMethodNameString"""
          case _                                     => c.abort(c.enclosingPosition, "Annotation `@XPathFunction` has incorrect parameters")
        }

      val (returnTypeIsOption, returnTypeIsIterable, returnTypeString) =
        returnType match {
          case tq"Option[Int]"                 => (true,  false, "int")
          case tq"Option[String]"              => (true,  false, "string")
          case tq"Option[Boolean]"             => (true,  false, "boolean")
          case tq"Option[java.time.Instant]"   => (true,  false, "instant")
          case tq"Option[$tpe]"                => (true,  false, "item")
          case tq"Iterable[Int]"               => (false, true,  "int")
          case tq"Iterable[String]"            => (false, true,  "string")
          case tq"Iterable[Boolean]"           => (false, true,  "boolean")
          case tq"Iterable[java.time.Instant]" => (false, true,  "instant")
          case tq"Iterable[$tpe]"              => (false, true,  "item")
          case tq"List[Int]"                   => (false, true,  "int")
          case tq"List[String]"                => (false, true,  "string")
          case tq"List[Boolean]"               => (false, true,  "boolean")
          case tq"List[java.time.Instant]"     => (false, true,  "instant")
          case tq"List[$tpe]"                  => (false, true,  "item")
          case tq"Int"                         => (false, false, "int")
          case tq"String"                      => (false, false, "string")
          case tq"Boolean"                     => (false, false, "boolean")
          case tq"java.time.Instant"           => (false, false, "instant")
          case tq"$tpe"                        => (false, false, "item")
        }

      val returnTypeCard =
        if (returnTypeIsOption)
          q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.OPT"""
        else if (returnTypeIsIterable)
          q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.STAR"""
        else
          q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.ONE"""

      val argumentsDetails =
        regularArgs.zipWithIndex map {
          case (arg: ValDef, argPosInList) =>

            val (isOption, isIterable, typeString) =
              arg match {
                case q"$mods val $name: Option[Int]                 = $rhs" => (true,  false, "int")
                case q"$mods val $name: Option[String]              = $rhs" => (true,  false, "string")
                case q"$mods val $name: Option[Boolean]             = $rhs" => (true,  false, "boolean")
                case q"$mods val $name: Option[java.time.Instant]   = $rhs" => (true,  false, "instant")
                case q"$mods val $name: Option[$tpe]                = $rhs" => (true,  false, "item")
                case q"$mods val $name: Iterable[Int]               = $rhs" => (false, true,  "int")
                case q"$mods val $name: Iterable[String]            = $rhs" => (false, true,  "string")
                case q"$mods val $name: Iterable[Boolean]           = $rhs" => (false, true,  "boolean")
                case q"$mods val $name: Iterable[java.time.Instant] = $rhs" => (false, true,  "instant")
                case q"$mods val $name: Iterable[$tpe]              = $rhs" => (false, true,  "item")
                case q"$mods val $name: List[Int]                   = $rhs" => (false, true,  "int")
                case q"$mods val $name: List[String]                = $rhs" => (false, true,  "string")
                case q"$mods val $name: List[Boolean]               = $rhs" => (false, true,  "boolean")
                case q"$mods val $name: List[java.time.Instant]     = $rhs" => (false, true,  "instant")
                case q"$mods val $name: List[$tpe]                  = $rhs" => (false, true,  "item")
                case q"$mods val $name: Int                         = $rhs" => (false, false, "int")
                case q"$mods val $name: String                      = $rhs" => (false, false, "string")
                case q"$mods val $name: Boolean                     = $rhs" => (false, false, "boolean")
                case q"$mods val $name: java.time.Instant           = $rhs" => (false, false, "instant")
                case q"$mods val $name: $tpe                        = $rhs" => (false, false, "item")
              }

            val (decodeCall, defaultOpt) = {
              val q"$mods val $name: $tpt = $rhs" = arg
              (q"""decodeSaxonArg[$tpt](args($argPosInList))""", rhs != EmptyTree option rhs)
            }

            (argPosInList, isOption, isIterable, typeString, decodeCall, defaultOpt)

          case _ => throw new IllegalArgumentException
      }

      val minArity = argumentsDetails.count(_._6.isEmpty)
      val maxArity = argumentsDetails.size

      if (argumentsDetails.takeWhile(_._6.isEmpty).size != minArity)
        throw new IllegalArgumentException(s"arguments with default values must be last")

      def getSaxonType(typeString: String) =
        typeString match {
          case "int"     => q"""org.orbeon.saxon.model.BuiltInAtomicType.INTEGER"""
          case "string"  => q"""org.orbeon.saxon.model.BuiltInAtomicType.STRING"""
          case "boolean" => q"""org.orbeon.saxon.model.BuiltInAtomicType.BOOLEAN"""
          case "instant" => q"""org.orbeon.saxon.model.BuiltInAtomicType.DATE_TIME"""
          case "item"    => q"""org.orbeon.saxon.model.AnyItemType"""
        }

      // Register one entry per distinct arity
      for (arity <- minArity to maxArity) yield {

        val classNameWithArity          = methodName.toString + "_xpathFunction" + arity
        val flattenedArgumentsUpToArity = argumentsDetails.take(arity)

        val register =
          q"""
            register(
              $resolvedFunctionName,
              $arity,
              () => new ${TypeName(classNameWithArity)},
              ${getSaxonType(returnTypeString)},
              $returnTypeCard,
              org.orbeon.saxon.functions.registry.BuiltInFunctionSet.CITEM | org.orbeon.saxon.functions.registry.BuiltInFunctionSet.LATE
            )
           """
         // TODO: flags must be configurable

        val registerWithArgs =
          flattenedArgumentsUpToArity.foldLeft(register) { case (result, (pos, isOption, isIterable, typeString, _, _)) =>

            val card =
              if (isOption)
                q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.OPT"""
              else if (isIterable)
                q"""org.orbeon.saxon.functions.registry.BuiltInFunctionSet.STAR"""
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

        val flattenedTrees   = flattenedArgumentsUpToArity.map(_._5)
        val defaultArgsTrees = argumentsDetails.drop(arity).flatMap(_._6)

        val encodedCall =
          if (regularArgs.isEmpty) { // no parameter list!
            q"""
              encodeSaxonArg[$returnType](
                fn[..$tpes]
              )
             """
          } else {
            q"""
              encodeSaxonArg[$returnType](
                fn[..$tpes](
                  ..$flattenedTrees,..$defaultArgsTrees
                )
              )
            """
          }

        // We use `...$args` which means `Iterable[Iterable[Tree]]`
        implicitArgs match {
          case None =>
            q"""
              $registerWithArgs

              class ${TypeName(classNameWithArity)} extends org.orbeon.oxf.xml.FunctionSupport2 {
                def call(
                  context: org.orbeon.saxon.expr.XPathContext,
                  args   : Array[org.orbeon.saxon.om.Sequence]
                ): org.orbeon.saxon.om.Sequence = {

                  def fn[..$tpes](..$regularArgs): $returnType = {..$body}

                  import org.orbeon.oxf.xml.FunctionSupport2._

                  $encodedCall
                }
              }
            """
          case Some(implicitArgs) =>
            q"""
              $registerWithArgs

              class ${TypeName(classNameWithArity)} extends org.orbeon.oxf.xml.FunctionSupport2 {
                def call(
                  context: org.orbeon.saxon.expr.XPathContext,
                  args   : Array[org.orbeon.saxon.om.Sequence]
                ): org.orbeon.saxon.om.Sequence = {

                  def fn[..$tpes](..$regularArgs)(..$implicitArgs): $returnType = {..$body}

                  locally {
                    implicit val xpathContext : org.orbeon.saxon.expr.XPathContext = context
                    implicit val xformsContext: org.orbeon.oxf.xforms.function.XFormsFunction.Context =
                      org.orbeon.oxf.xforms.function.XFormsFunction.context

                    import org.orbeon.oxf.xml.FunctionSupport2._

                    $encodedCall
                  }
                }
              }
            """
        }
      }
    }

    // TODO: Would like to keep `..$annottees` but that gets scope into a block?
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
