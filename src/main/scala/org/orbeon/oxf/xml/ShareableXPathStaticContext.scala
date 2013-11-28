package org.orbeon.oxf.xml

import java.util.{List ⇒ JList}
import javax.xml.transform.{SourceLocator, Source}
import org.orbeon.oxf.util.XPath._
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.expr._
import org.orbeon.saxon.functions.{FunctionLibraryList, FunctionLibrary}
import org.orbeon.saxon.om.{NamespaceResolver, StructuredQName}
import org.orbeon.saxon.sxpath.{XPathStaticContext, AbstractStaticContext}
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.value.{SequenceType, QNameValue}
import org.orbeon.oxf.util.{Logging, IndentedLogger}

// Similar to Saxon JAXPXPathStaticContext. JAXPXPathStaticContext holds a reference to an XPathVariableResolver, which
// is not desirable as variable resolution occurs at runtime. So here instead we create a fully shareable context.
class ShareableXPathStaticContext(config: Configuration, namespaceMapping: NamespaceMapping, functionLibrary: FunctionLibrary)(implicit logger: IndentedLogger)
        extends AbstractStaticContext
        with XPathStaticContext
        with Logging {

    // This also creates an Executable
    setConfiguration(config)

    // Add function library
    setDefaultFunctionLibrary()
    getFunctionLibrary.asInstanceOf[FunctionLibraryList].libraryList.asInstanceOf[JList[FunctionLibrary]].add(0, functionLibrary)

    // This should be unused as we handle global variables differently
    private val stackFrameMap = config.makeSlotManager
    def getStackFrameMap = stackFrameMap

    // Return the names of global variables referenced by the expression after it has been parsed
    private var boundVariables = Set.empty[StructuredQName]
    def referencedVariables: Iterable[StructuredQName] = boundVariables

    def declareVariable(qname: QNameValue) = throw new IllegalStateException                       // never used in Saxon
    def declareVariable(namespaceURI: String, localName: String) = throw new IllegalStateException // shouldn't be called in our case

    def bindVariable(qName: StructuredQName): VariableReference = {
        // Q: Can this be called multiple time with the same name, and if so should we return the same VariableReference?
        boundVariables += qName
        new VariableReference(new VariableBinding(qName))
    }

    // Per Saxon: "used to represent the run-time properties and methods associated with a variable: specifically, a
    // method to get the value of the variable".
    class VariableBinding(qName: StructuredQName) extends Binding {
        def isGlobal = true
        def isAssignable = false
        def getLocalSlotNumber = -1 // "If this is a local variable held on the local stack frame"

        def getVariableQName = qName
        def getRequiredType = SequenceType.ANY_SEQUENCE

        // Saxon does something similar but different in XPathVariable, where it stores variables in the the dynamic
        // context. That uses slots however, which means we cannot resolve variables fully dynamically. So I think
        // our approach below is ok.
        def evaluateVariable(context: XPathContext) = {
            val variableResolver = context.getController.getUserData(classOf[ShareableXPathStaticContext].getName, "variableResolver").asInstanceOf[VariableResolver]
            variableResolver(qName, context)
        }
    }

    // Namespace resolver
    object NSResolver extends NamespaceResolver {
        def getURIForPrefix(prefix: String, useDefault: Boolean) =
            if (prefix == "") {
                if (useDefault)
                    getDefaultElementNamespace
                else
                    ""
            } else
                namespaceMapping.mapping.get(prefix)

        def iteratePrefixes =
            namespaceMapping.mapping.keySet.iterator
    }

    def getURIForPrefix(prefix: String): String = {
        val uri = NSResolver.getURIForPrefix(prefix, useDefault = false)
        if (uri == null)
            throw new XPathException("Prefix " + prefix + " has not been declared")
        uri
    }

    def getNamespaceResolver = NSResolver
    def setNamespaceResolver(resolver: NamespaceResolver) = throw new IllegalStateException

    // Schema stuff which we don't support
    def importSchema(source: Source)        = getConfiguration.addSchemaSource(source, getConfiguration.getErrorListener)
    def isImportedSchema(namespace: String) = getConfiguration.isSchemaAvailable(namespace)
    def getImportedSchemaNamespaces         = getConfiguration.getImportedNamespaces

    override def issueWarning(s: String, locator: SourceLocator) =
        if (logger ne null)
            debug(s)
}
