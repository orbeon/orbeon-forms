package org.orbeon.oxf.xml

import java.{util => ju}

import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.util.{IndentedLogger, Logging, StaticXPath}
import org.orbeon.saxon.expr._
import org.orbeon.saxon.expr.instruct.SlotManager
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om.{NamespaceResolver, Sequence, StructuredQName}
import org.orbeon.saxon.s9api.{HostLanguage, Location}
import org.orbeon.saxon.sxpath.{AbstractStaticContext, XPathStaticContext}
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.utils.Configuration
import org.orbeon.saxon.value.{IntegerValue, QNameValue, SequenceType}
import org.orbeon.xml.NamespaceMapping

import scala.collection.mutable
import scala.jdk.CollectionConverters._


// Similar to Saxon JAXPXPathStaticContext. JAXPXPathStaticContext holds a reference to an XPathVariableResolver, which
// is not desirable as variable resolution occurs at runtime. So here instead we create a fully shareable context.
class ShareableXPathStaticContext(
  config           : Configuration,
  namespaceMapping : NamespaceMapping,
  functionLibrary  : FunctionLibrary)(implicit
  logger           : IndentedLogger
) extends AbstractStaticContext
   with XPathStaticContext
   with Logging {

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
  }

  // Add function library
  setDefaultFunctionLibrary()
  getFunctionLibrary.asInstanceOf[FunctionLibraryList].libraryList.add(0, functionLibrary)

  // This should be unused as we handle global variables differently
  private val stackFrameMap = config.makeSlotManager
  def getStackFrameMap: SlotManager = stackFrameMap

  private val variables = mutable.HashMap[StructuredQName, VariableBinding]()
  def referencedVariables: Iterable[StructuredQName] = variables.keys

  // NOTE: We do the same that `IndependentContext` does, to make sure only one `LocalBinding` is created.
  // It's unclear if it's absolutely necessary but it shouldn't hrut.
  private def declareVariableIfNeeded(qName: StructuredQName): VariableBinding =
    variables.getOrElseUpdate(qName,
      new VariableBinding(qName) |!>
        (_.setLocalSlotNumber(variables.size))
    )

  def bindVariable(qName: StructuredQName): VariableReference =
    new LocalVariableReference(declareVariableIfNeeded(qName))

  def declareVariable(qname: QNameValue) = throw new IllegalStateException                       // shouldn't be called in our case
  def declareVariable(namespaceURI: String, localName: String) = throw new IllegalStateException // never used in Saxon XPath
  def isContextItemParentless: Boolean = false                                                   // never set to `true` in Saxon XPath

  // Per Saxon: "used to represent the run-time properties and methods associated with a variable: specifically, a
  // method to get the value of the variable".
  class VariableBinding(qName: StructuredQName) extends LocalBinding {

    def isGlobal = true
    def isAssignable = false

    private var slotNumber: Int = -1
    def getLocalSlotNumber: Int = slotNumber
    def setLocalSlotNumber(slot: Int): Unit = slotNumber = slot

    def getVariableQName: StructuredQName = qName
    def getRequiredType: SequenceType = SequenceType.ANY_SEQUENCE

    // Saxon does something similar but different in XPathVariable, where it stores variables in the the dynamic
    // context. That uses slots however, which means we cannot resolve variables fully dynamically. So I think
    // our approach below is ok.
    def evaluateVariable(context: XPathContext): Sequence = {

      if (context.getController eq null)
        throw new NullPointerException

      val variableResolver =
        context.getController.getUserData(
          classOf[ShareableXPathStaticContext].getName,
          "variableResolver"
        ).asInstanceOf[StaticXPath.VariableResolver]

      variableResolver(qName, context)
    }

    // Same as in `XPathVariable`
    def addReference(ref: VariableReference, isLoopingReference: Boolean): Unit = ()
    def getIntegerBoundsForVariable: Array[IntegerValue] = null
    def setIndexedVariable(): Unit = ()
    def isIndexedVariable: Boolean = false
  }

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

  def getURIForPrefix(prefix: String): String = {
    val uri = NSResolver.getURIForPrefix(prefix, useDefault = false)
    if (uri == null)
      throw new XPathException("Prefix " + prefix + " has not been declared")
    uri
  }

  def getNamespaceResolver: NamespaceResolver = NSResolver
  def setNamespaceResolver(resolver: NamespaceResolver): Unit = throw new IllegalStateException

  // Schema stuff which we don't support
  def isImportedSchema(namespace: String): Boolean = getConfiguration.isSchemaAvailable(namespace)
  def getImportedSchemaNamespaces: ju.Set[String] = getConfiguration.getImportedNamespaces

  override def issueWarning(s: String, locator: Location): Unit =
    if (logger ne null)
      debug(s)
}
