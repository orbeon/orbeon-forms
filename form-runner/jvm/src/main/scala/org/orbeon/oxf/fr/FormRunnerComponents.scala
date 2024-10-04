package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils.{BooleanOps, PipeOps}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{FileUtils, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.Loggers
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunctionNames
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xml.NamespaceMapping



trait FormRunnerComponents {


  // In an XPath expression, replace non-local variable references.
  //@XPathFunction
  def replaceVarReferencesWithFunctionCallsFromString( // xxx FromString
    elemOrAtt   : NodeInfo,
    xpathString : String,
    avt         : Boolean,
    libraryName : String,
    norewrite   : Array[String]
  ): String =
    Private.replaceVarReferencesWithFunctionCalls(
      xpathString      = xpathString,
      namespaceMapping = Private.namespaceMappingForNode(elemOrAtt),
      library          = Private.componentsFunctionLibrary,
      avt              = avt,
      libraryName      = libraryName,
      norewrite        = norewrite
    )

  //@XPathFunction
  def replaceVarReferencesWithFunctionCallsFromProperty(
    propertyName : String,
    avt          : Boolean,
    libraryName  : String,
    norewrite    : Array[String]
  ): String =
    Properties.instance.getPropertySet.getPropertyOpt(propertyName) match {
      case Some(property) =>
        Private.replaceVarReferencesWithFunctionCalls(
          xpathString      = property.stringValue,
          namespaceMapping = property.namespaceMapping,
          library          = Private.componentsFunctionLibrary,
          avt              = avt,
          libraryName      = libraryName,
          norewrite        = norewrite
        )
      case None => ""
    }

  //@XPathFunction
  def knownConstraintsToAutomaticHint(
    attributesWithXPath : Array[NodeInfo]
  ): String = {

    val constraints = attributesWithXPath.toList.flatMap (attributeWithXPath =>
      FormRunnerCommonConstraint.analyzeKnownConstraint(
        attributeWithXPath.getStringValue,
        Private.namespaceMappingForNode(attributeWithXPath),
        Private.componentsFunctionLibrary
      )(Private.newIndentedLogger)
    )

    def hintResourceXPath(key: String): String =
      s"xxf:r('detail.hints.$key', '|fr-fr-resources|')"
    def hintMessageXPath(constraintName: String, displayValueXPath: String): String = {
      val template = hintResourceXPath(constraintName)
      s"xxf:format-message($template, $displayValueXPath)"
    }

    val hints = constraints.flatMap {
      case (constraintName @ (ValidationFunctionNames.UploadMaxSizePerFile |
                              ValidationFunctionNames.UploadMaxSizeAggregatePerControl |
                              // Backward compatibility
                              ValidationFunctionNames.UploadMaxSize), Some(value)) =>
        val displaySizeOpt = value.toLongOption.map(FileUtils.byteCountToDisplaySize)
        displaySizeOpt.map { displaySize =>
          hintMessageXPath(ValidationFunctionNames.currentName(constraintName), s"'$displaySize'")
        }

      case (constraintName @ ValidationFunctionNames.UploadMediatypes,Some(mediatype)) =>
        val slashPosition     = mediatype.indexOf('/')
        val isProperMediatype = slashPosition > 1 && slashPosition < mediatype.length - 1
        isProperMediatype.flatOption {
          val mediatypeLeft   = mediatype.substring(0, slashPosition)
          val mediatypeRight  = mediatype.substring(slashPosition + 1)
          val displayMediatypeOpt =
            if (mediatypeRight == "*") {
              // Localized name for common wildcard media types
              Set("image", "video", "audio")(mediatypeLeft).option(hintResourceXPath(s"upload-$mediatypeLeft"))
            } else {
              // Heuristic: take the part before the plus sign, and uppercase it, e.g. `image/svg+xml` → `SVG`
              // We might want to handle few special cases
              // - E.g. `application/msword` and
              //        `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
              //        → `Word`
              // - Those could be listed in some JSON in a property file (can we avoid having to localize them,
              //   e.g. does "Word" work in most languages?)
              val beforePlus = mediatypeRight.substringBeforeOpt("+").getOrElse(mediatypeRight)
              val toUpper    = beforePlus.toUpperCase
              val toXPath    = s"'$toUpper'"
              Some(toXPath)
            }
          displayMediatypeOpt.map(hintMessageXPath(constraintName, _))
        }
      case _ => None
    }

    hints match {
      case Nil         => ""
      case hint :: Nil => hint
      case _           =>
        val params = hints.mkString(", ' ', ")
        s"concat($params)"
    }
  }

  private object Private {

    val Logger = LoggerFactory.createLogger(FormRunner.getClass)
    val DefaultNorewriteSet = Set("fr-lang", "fr-mode")

    // Worker method to avoid code duplication
    def replaceVarReferencesWithFunctionCalls(
      xpathString      : String,
      namespaceMapping : NamespaceMapping,
      library          : FunctionLibrary,
      avt              : Boolean,
      libraryName      : String,
      norewrite        : Array[String]
    ): String = {
      // Construct the nameMapping function internally
      val combinedNorewrite = DefaultNorewriteSet ++ norewrite.toSet
      val nameMapping: String => String = name =>
        if (combinedNorewrite.contains(name))
          s"$$$name"
        else
          s"frf:controlVariableValue('$name', ${libraryName.trimAllToOpt.map("'" + _ + "'").getOrElse("()")})"

      FormRunnerRename.replaceVarReferencesWithFunctionCallsFromString(
        xpathString,
        namespaceMapping,
        library,
        avt,
        nameMapping
      )(newIndentedLogger)
    }

    def newIndentedLogger: IndentedLogger =
      new IndentedLogger(Logger)

    def namespaceMappingForNode(elemOrAtt: NodeInfo): NamespaceMapping =
      NamespaceMapping(elemOrAtt.namespaceMappings.toMap)

    def componentsFunctionLibrary: FunctionLibrary =
        new FunctionLibraryList                         |!>
          (_.addFunctionLibrary(XFormsFunctionLibrary)) |!>
          (_.addFunctionLibrary(FormRunnerFunctionLibrary))
  }
}
