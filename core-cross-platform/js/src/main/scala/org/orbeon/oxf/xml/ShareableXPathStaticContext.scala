package org.orbeon.oxf.xml

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.VariableResolver
import org.orbeon.saxon.expr.Expression.ITERATE_METHOD
import org.orbeon.saxon.expr._
import org.orbeon.saxon.expr.instruct.SlotManager
import org.orbeon.saxon.expr.parser.RebindingMap
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.model.{AnyItemType, ItemType}
import org.orbeon.saxon.om.{NamespaceResolver, Sequence, SequenceIterator, StructuredQName}
import org.orbeon.saxon.s9api.{HostLanguage, Location}
import org.orbeon.saxon.sxpath.{AbstractStaticContext, XPathStaticContext, XPathVariable}
import org.orbeon.saxon.trace.ExpressionPresenter
import org.orbeon.saxon.utils.Configuration
import org.orbeon.saxon.value.QNameValue
import org.orbeon.xml.NamespaceMapping

import java.{util => ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters._


// Similar to Saxon JAXPXPathStaticContext. JAXPXPathStaticContext holds a reference to an XPathVariableResolver, which
// is not desirable as variable resolution occurs at runtime. So here instead we create a fully shareable context.
// Updated to take https://saxonica.plan.io/issues/2554 into account for Saxon 10.
class ShareableXPathStaticContext(
  config           : Configuration,
  namespaceMapping : NamespaceMapping,
  functionLibrary  : FunctionLibrary)(implicit
  logger           : IndentedLogger
) extends AbstractStaticContext
   with XPathStaticContext {

  // Primary constructor
  locally {
    setConfiguration(config) // also sets `defaultCollationName`

    this.packageData = {
      val pd = new PackageData(config)
      pd.setHostLanguage(HostLanguage.XPATH)
      pd.setSchemaAware(false)
      pd
    }

    this.usingDefaultFunctionLibrary = true

    // Add function library
    setDefaultFunctionLibrary()
    getFunctionLibrary.asInstanceOf[FunctionLibraryList].libraryList.add(0, functionLibrary)
  }

  private val variables = mutable.HashMap[StructuredQName, Expression]()
  def referencedVariables: Iterable[StructuredQName] = variables.keys

  // NOTE: We do the same that `IndependentContext` does, to make sure only one `LocalBinding` is created.
  // It's unclear if it's absolutely necessary but it shouldn't hurt.
  private def declareVariableIfNeeded(qName: StructuredQName): Expression =
    variables.getOrElseUpdate(qName, new OrbeonVariableReference(qName))

  def bindVariable(qName: StructuredQName): Expression =
    declareVariableIfNeeded(qName)

  // Namespace resolver
  object NSResolver extends NamespaceResolver {
    def getURIForPrefix(prefix: String, useDefault: Boolean): String =
      if (prefix == "") {
        if (useDefault)
          getDefaultElementNamespace
        else
          ""
      } else
        namespaceMapping.mapping.getOrElse(prefix, null)

    def iteratePrefixes: ju.Iterator[String] =
      namespaceMapping.mapping.keySet.iterator.asJava
  }

  def getNamespaceResolver: NamespaceResolver = NSResolver

  override def issueWarning(s: String, locator: Location): Unit =
    if (logger ne null)
      debug(s)

  // Schema stuff which we don't support
  def isImportedSchema(namespace: String): Boolean = getConfiguration.isSchemaAvailable(namespace)
  def getImportedSchemaNamespaces: ju.Set[String] = getConfiguration.getImportedNamespaces

  // Should not be used
  def setNamespaceResolver(resolver: NamespaceResolver): Unit = ???
  def declareVariable(qname: QNameValue): XPathVariable = ???
  def declareVariable(namespaceURI: String, localName   : String): XPathVariable = ???
  val getStackFrameMap: SlotManager = config.makeSlotManager
  def isContextItemParentless: Boolean = false
}

class OrbeonVariableReference(val name: StructuredQName) extends Expression with Callable {

  def getImplementationMethod            : Int      = ITERATE_METHOD
  def getItemType                        : ItemType = AnyItemType
  def computeCardinality()               : Int      = StaticProperty.ALLOWS_ZERO_OR_MORE
  override def computeSpecialProperties(): Int      = StaticProperty.NO_NODES_NEWLY_CREATED // since we are returning an already-evaluated expression

  def call(context  : XPathContext, arguments: Array[Sequence]): Sequence = {

    val variableResolver =
      context.getController.getUserData(
        classOf[ShareableXPathStaticContext].getName,
        "variableResolver"
      ).asInstanceOf[VariableResolver]

    variableResolver(name, context)
  }

  override def iterate(context: XPathContext): SequenceIterator =
    call(context, null).iterate()

  override def equals(other: Any): Boolean =
    other match {
      case o: OrbeonVariableReference => name == o.name // && resolver eq other.resolver
      case _ => false
    }

  override def computeHashCode: Int = name.hashCode

  override def getExpressionName: String = "$" + name.getDisplayName

  def copy(rebindings: RebindingMap) =
    new OrbeonVariableReference(name)

  def `export`(destination: ExpressionPresenter): Unit = {
    destination.startElement("orbeonVar", this)
    destination.emitAttribute("name", name)
    destination.endElement()
  }
}