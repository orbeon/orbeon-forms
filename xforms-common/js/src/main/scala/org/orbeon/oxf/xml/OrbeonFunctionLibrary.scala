package org.orbeon.oxf.xml

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.saxon.expr.parser.RetainedStaticContext
import org.orbeon.saxon.expr.{Expression, OperandUsage, StaticContext}
import org.orbeon.saxon.functions.registry.BuiltInFunctionSet.Entry
import org.orbeon.saxon.functions.{FunctionLibrary, OptionsParameter, SystemFunction}
import org.orbeon.saxon.lib.NamespaceConstant
import org.orbeon.saxon.model.ItemType
import org.orbeon.saxon.om.{Function, Sequence, StructuredQName}
import org.orbeon.saxon.trans.{SymbolicName, XPathException}
import org.orbeon.saxon.value.SequenceType

import java.{util => ju}


// Inspired from `BuiltInFunctionSet` and modified to support multiple namespaces.
abstract class OrbeonFunctionLibrary extends FunctionLibrary {

  import Private._

  // (uri, prefix)
  val namespaces: List[(String, String)]

  def isAvailable(symbolicName: SymbolicName.F): Boolean =
    findEntry(symbolicName.getComponentName, symbolicName.getArity).isDefined

  def bind(
    symbolicName : SymbolicName.F,
    staticArgs   : Array[Expression],
    env          : StaticContext,
    reasons      : ju.List[String]
  ): Expression =
    findFunctionItem(symbolicName, new RetainedStaticContext(env)) match {
      case Some(fn) =>
        val f = fn.makeFunctionCall(staticArgs.toIndexedSeq: _*)
        f.setRetainedStaticContext(fn.getRetainedStaticContext)
        f
      case None =>
        // Adding information to the `reasons` list is usually unneeded unless we are the last
        // function library in a list, and it is costly to do.
        null
    }

  def getFunctionItem(symbolicName: SymbolicName.F, staticContext: StaticContext): Function =
    getFunctionItemOrThrow(symbolicName, staticContext.makeRetainedStaticContext())

  def register(
    name       : String,
    arity      : Int,
    make       : () => SystemFunction,
    itemType   : ItemType,
    cardinality: Int,
    properties : Int
  ): Entries = {

    require(cardinality >= 0)

    new Entries(
      namespaces map { case (uri, prefix) =>

        val e = new Entry

        e.name        = new StructuredQName(prefix, uri, name)
        e.arity       = arity
        e.make        = make
        e.itemType    = itemType
        e.cardinality = cardinality
        e.properties  = properties

        e.argumentTypes = Array.ofDim[SequenceType](arity)
        e.resultIfEmpty = Array.ofDim[Sequence](arity)
        e.usage         = Array.ofDim[OperandUsage.OperandUsage](arity)

        entries += (uri, name, arity) -> e

        e
      }
    )
  }

  def copy(): FunctionLibrary = this

  class Entries private[OrbeonFunctionLibrary] (entries: Iterable[Entry]) {

    def arg(a: Int, itemType: ItemType, options: Int, resultIfEmpty: Sequence): Entries = {
      entries foreach (_.arg(a, itemType, options, resultIfEmpty))
      this
    }

    def optionDetails(details: OptionsParameter): Entries = {
      entries foreach (_.optionDetails(details))
      this
    }
  }

  private object Private {

    // (uri, localName, arity)
    var entries: Map[(String, String, Int), Entry] = Map.empty

    def findEntry(name: StructuredQName, arity: Int): Option[Entry] =
      arity >= 0 flatOption entries.get(name.getURI, name.getLocalPart, arity)

    def findFunctionItem(symbolicName: SymbolicName.F, rsc: => RetainedStaticContext): Option[SystemFunction] =
      findEntry(symbolicName.getComponentName, symbolicName.getArity) map { entry =>
        val fn = entry.make()
        fn.setDetails(entry)
        fn.setArity(symbolicName.getArity)
        fn.setRetainedStaticContext(rsc)
        fn
      }

    def getFunctionItemOrThrow(symbolicName: SymbolicName.F, rsc: => RetainedStaticContext): SystemFunction =
      findFunctionItem(symbolicName, rsc) getOrElse {

        val localName = symbolicName.getComponentName.getLocalPart

        val diagName =
          if (namespaces exists (_._1 == NamespaceConstant.FN))
            "System function " + localName
          else
            "Function Q{" + symbolicName.getComponentName.getURI + "}" + localName

        val possibleArities = 0 until 20 collect {
          case arity if findEntry(symbolicName.getComponentName, arity).isDefined => arity
        }

        val err =
          new XPathException(
            if (possibleArities.isEmpty)
              s"`$diagName()` does not exist or is not available in this environment"
            else
              s"`$diagName()` cannot be called with " + OrbeonFunctionLibrary.pluralArguments(symbolicName.arity) + s" but exists with arity ${possibleArities mkString ", "}"
          )
        err.setErrorCode("XPST0017")
        err.setIsStaticError(true)
        throw err
      }
  }
}

private object OrbeonFunctionLibrary {

  def pluralArguments(num: Int): String =
    if (num == 0)
      "zero arguments"
    else if (num == 1)
      "one argument"
    else
      s"$num arguments"
}