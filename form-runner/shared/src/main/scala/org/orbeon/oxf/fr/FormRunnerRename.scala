package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.XMLNames.{FR, FRPrefix}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, StaticXPath, XPath}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.analysis.model.DependencyAnalyzer
import org.orbeon.oxf.xml.{SaxonUtils, ShareableXPathStaticContext}
import org.orbeon.saxon.expr.Expression
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.{NodeInfo, StructuredQName}
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xml.NamespaceMapping

import java.util.regex.Matcher


object FormRunnerRename {

  def replaceVarReferencesWithFunctionCallsFromString(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean,
    replace          : String => String
  )(implicit
    logger           : IndentedLogger
  ): String = {

    val expr =
      compileExpression(
        xpathString      = xpathString,
        namespaceMapping = namespaceMapping,
        functionLibrary  = functionLibrary,
        avt              = avt
      )

    // `sortBy` and `reverse` were introduced to avoid substring matches in variable  references. But we also use a
    // regex to prevent matching a substring, so they are probably not needed.
    val varRefsByLengthDesc =
      SaxonUtils.iterateExternalVariableReferences(expr).toList.sortBy(_.length).reverse

    varRefsByLengthDesc.foldLeft(xpathString) { case (xpathString, oldName) =>
      replaceVarReferenceUseRegex(
        xpathString,
        oldName,
        replace(oldName)
      )
    }
  }

  def replaceVarAndFnReferences(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean,
    oldName          : String,
    newName          : String
  )(implicit
    logger          : IndentedLogger
  ): Option[String] = {

    val expr =
      compileExpression(
        xpathString      = xpathString,
        namespaceMapping = namespaceMapping,
        functionLibrary  = functionLibrary,
        avt              = avt
      )

    val withVarReplacement =
      xpathString.contains(s"$$$oldName") && DependencyAnalyzer.containsVariableReference(expr, oldName) option {
        replaceVarReferenceUseRegex(
          xpathString,
          oldName,
          s"$$$newName"
        )
      }

    // Here we have to use a heuristic because we don't have exact information about position of the function
    // parameters. In addition, if an expression contains a mix of functions of different arity, we can't know for sure
    // which function we are dealing with. So we just replace and hope for the best! We could try to be smarter and
    // detect that we are in a most likely unsupported case, and not perform the replacement in that case, but then
    // we would need to have some UI to report the problem to the user.
    val withFunctionReplacement = {
      val xpathStringMaybeWithVarReplacement = withVarReplacement.getOrElse(xpathString)
      (
        xpathStringMaybeWithVarReplacement.contains(s"""'$oldName'""") ||
        xpathStringMaybeWithVarReplacement.contains(s""""$oldName""")
      ) && (
        SaxonUtils.containsFnWithParam(expr, FRControlStringValueName, 2, oldName, 0) ||
        SaxonUtils.containsFnWithParam(expr, FRControlTypedValueName,  2, oldName, 0) ||
        SaxonUtils.containsFnWithParam(expr, FRControlStringValueName, 3, oldName, 2) ||
        SaxonUtils.containsFnWithParam(expr, FRControlTypedValueName,  3, oldName, 2)
      ) option {
        replaceQuotedStringUseRegex(
          xpathStringMaybeWithVarReplacement,
          oldName,
          newName
        )
      }
    }

    withFunctionReplacement.orElse(withVarReplacement)
  }

  def findUnresolvedVariableReferences(
    elemOrAtt     : NodeInfo,
    avt           : Boolean,
    validBindNames: => String => Boolean // by-name so we can avoid costly computation if not needed
  )(implicit
    logger        : IndentedLogger
  ): Iterator[String] =
    elemOrAtt.stringValue.trimAllToOpt match {
      case Some(xpathString) if ! xpathString.contains("$") =>
        // Optimization
        Iterator.empty
      case Some(xpathString) =>

        val expr =
          compileExpression(
            xpathString      = xpathString,
            namespaceMapping = NamespaceMapping(elemOrAtt.namespaceMappings.toMap),
            functionLibrary  = inScopeContainingDocument.partAnalysis.functionLibrary, // or could just use the Form Runner function library
            avt              = avt
          )

        DependencyAnalyzer.findMissingVariableReferences(expr, validBindNames)
      case None =>
        Iterator.empty
    }

  // Return `None` if the property doesn't exist or is blank
  def replaceVarReferencesWithFunctionCallsFromPropertyAsString(
    propertyName   : String,
    avt            : Boolean,
    libraryName    : Option[String],
    norewrite      : Set[String],
    functionLibrary: FunctionLibrary
  )(implicit
    logger         : IndentedLogger
  ): Option[String] =
    for {
      property <- CoreCrossPlatformSupport.properties.getPropertyOpt(propertyName)
      value    <- property.nonBlankStringValue
    } yield
      property.associatedValue { _ =>
        replaceVarReferencesWithFunctionCalls(
          xpathString      = value,
          namespaceMapping = property.namespaceMapping,
          library          = functionLibrary,
          avt              = avt,
          libraryNameOpt   = libraryName,
          norewrite        = norewrite
        )
      }

  def replaceVarReferencesWithFunctionCallsFromPropertyAsExpr(
    propertyName   : String,
    avt            : Boolean,
    libraryName    : Option[String],
    norewrite      : Set[String],
    functionLibrary: FunctionLibrary
  )(implicit
    logger         : IndentedLogger
  ): Option[CompiledExpression] =
    for {
      property <- CoreCrossPlatformSupport.properties.getPropertyOpt(propertyName)
      value    <- property.nonBlankStringValue
    } yield
      property.associatedValue { _ =>
        val newValue =
          replaceVarReferencesWithFunctionCalls(
            xpathString      = value,
            namespaceMapping = property.namespaceMapping,
            library          = functionLibrary,
            avt              = avt,
            libraryNameOpt   = libraryName,
            norewrite        = norewrite
          )
        StaticXPath.compileExpression(
          xpathString      = newValue,
          namespaceMapping = property.namespaceMapping.amend("frf", "java:org.orbeon.oxf.fr.FormRunner"), // TODO: we also amend in `FormRunnerEmailBackend`
          locationData     = null,
          functionLibrary  = functionLibrary,
          avt              = avt
        )
      }

    private val DefaultNorewriteSet : Set[String] = Set("fr-lang", "fr-mode", "form-resources")

    def replaceVarReferencesWithFunctionCalls(
      xpathString     : String,
      namespaceMapping: NamespaceMapping,
      library         : FunctionLibrary,
      avt             : Boolean,
      libraryNameOpt  : Option[String],
      norewrite       : Set[String]
    )(implicit
      logger          : IndentedLogger
    ): String = {
      val combinedNorewrite : Set[String]       = DefaultNorewriteSet ++ norewrite
      val nameMapping       : String => String  = name =>
        if (combinedNorewrite.contains(name))
          s"$$$name"
        else
          s"frf:controlVariableValue('$name', ${libraryNameOpt.flatMap(_.trimAllToOpt).map("'" + _ + "'").getOrElse("()")})"

      replaceVarReferencesWithFunctionCallsFromString(
        xpathString,
        namespaceMapping,
        library,
        avt,
        nameMapping
      )
    }

    // https://github.com/orbeon/orbeon-forms/issues/6837
    def replaceVarReferencesWithFunctionCallsForAction(
      xpathString     : String,
      namespaceMapping: NamespaceMapping,
      library         : FunctionLibrary,
      avt             : Boolean,
      libraryNameOpt  : Option[String],
      norewrite       : Set[String]
    )(implicit
      logger          : IndentedLogger
    ): String = {
      val combinedNorewrite : Set[String]       = DefaultNorewriteSet ++ norewrite
      val nameMapping       : String => String  = name =>
        if (combinedNorewrite.contains(name))
          s"$$$name"
        else
          s"frf:controlVariableValueForAction(xxf:get-document-attribute($$current-action-id, 'action-source'), '$name', ${libraryNameOpt.flatMap(_.trimAllToOpt).map("'" + _ + "'").getOrElse("()")})"

      replaceVarReferencesWithFunctionCallsFromString(
        xpathString,
        namespaceMapping,
        library,
        avt,
        nameMapping
      )
    }

  private val FRControlStringValueName = new StructuredQName(FRPrefix, FR, "control-string-value")
  private val FRControlTypedValueName  = new StructuredQName(FRPrefix, FR, "control-typed-value")

  // What follows is a heuristic, as we don't have exact positions provided by the XPath parser. So we try to avoid
  // matching subsequences by saying that the name must not be followed by a character that can be part of an NCName.
  private def replaceVarReferenceUseRegex(
    xpathString      : String,
    name             : String,
    replacement      : String
  ): String =
    xpathString.replaceAll(s"\\$$$name(?![\\p{IsLetter}\\p{IsDigit}.·\\-_])", Matcher.quoteReplacement(replacement))

  private def replaceQuotedStringUseRegex(
    xpathString      : String,
    name             : String,
    replacement      : String
  ): String =
    xpathString
      .replaceAll(s"""'$name'""", Matcher.quoteReplacement(s"""'$replacement'"""))
      .replaceAll(s""""$name"""", Matcher.quoteReplacement(s""""$replacement""""))

  // Compile expression but don't `typeCheck()` it so that we don't throw static XPath errors at this point, but later
  // when we have the option to gather XPath errors in the static state or the running form.
  private def compileExpression(
    xpathString     : String,
    namespaceMapping: NamespaceMapping,
    functionLibrary : FunctionLibrary,
    avt             : Boolean
  )(implicit
    logger          : IndentedLogger
  ): Expression =
    XPath.compileExpressionMinimal(
      staticContext = new ShareableXPathStaticContext(
          XPath.GlobalConfiguration,
          namespaceMapping,
          functionLibrary
        ),
      xpathString   = xpathString,
      avt           = avt
    )
}