package org.orbeon.oxf.fr

import org.log4s.Logger
import org.orbeon.css.CSSSelectorParser.AttributePredicate
import org.orbeon.dom.QName
import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.util.CoreUtils.{BooleanOps, PipeOps}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{ByteSizeUtils, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunctionNames
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xforms.xbl.{BindingAttributeDescriptor, BindingDescriptor}
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.XFormsNames.APPEARANCE_QNAME
import org.orbeon.xml.NamespaceMapping


trait FormRunnerComponentsCompileTime {

  // Called from XSLT only
  //@XPathFunction
  def bindingClassNameForToolbox(bindingElem: om.NodeInfo): String = {

    val relatedDescriptors = BindingDescriptor.getAllRelevantDescriptors(List(bindingElem))

    def buildName(name: QName, datatype: Option[QName], appearance: Option[String]): String = {

      def replaceColon(s: String): String = s.replace(':', '-')
      def replaceXsXfPrefix(qName: QName): String = {
        val uri = qName.namespace.uri
        if (uri == XMLConstants.XSD_URI || uri == XFormsNames.XFORMS_NAMESPACE_URI)
          qName.localName
        else
          replaceColon(qName.qualifiedName)
      }

      val nameToken       = replaceColon(name.qualifiedName)
      val datatypeToken   = datatype.map(replaceXsXfPrefix)
      val appearanceToken = appearance.map(replaceColon).map("appearance-" + _)

      (nameToken :: datatypeToken.toList ::: appearanceToken.toList ::: Nil).mkString("-")
    }

    def findByNameAndDatatypeAndAppearance: Option[String] =
      relatedDescriptors collectFirst {
        case BindingDescriptor(
            Some(name),
            d @ Some(_),
            Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Equal(appearance)))
          ) => buildName(name, d, Some(appearance))
        case BindingDescriptor(
            Some(name),
            d @ Some(_),
            Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Token(appearance)))
          ) => buildName(name, d, Some(appearance))
      }

    def findByNameAndDatatype: Option[String] =
      relatedDescriptors collectFirst {
        case BindingDescriptor(
            Some(name),
            d @ Some(_),
            None
          ) => buildName(name, d, None)
      }

  def findByNameAndAppearance: Option[String] =
      relatedDescriptors collectFirst {
        case BindingDescriptor(
            Some(name),
            None,
            Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Equal(appearance)))
          ) => buildName(name, None, Some(appearance))
        case BindingDescriptor(
            Some(name),
            None,
            Some(BindingAttributeDescriptor(APPEARANCE_QNAME, AttributePredicate.Token(appearance)))
          ) => buildName(name, None, Some(appearance))
      }

    def findDirect: Option[String] =
      relatedDescriptors collectFirst {
        case BindingDescriptor(
            Some(name),
            None,
            None
          ) => buildName(name, None, None)
      }

    (
      findByNameAndDatatypeAndAppearance orElse
        findByNameAndDatatype            orElse
        findByNameAndAppearance          orElse
        findDirect
    ).orNull
  }

  // Called from XSLT only
  // In an XPath expression, replace non-local variable references.
  //@XPathFunction
  def replaceVarReferencesWithFunctionCallsFromString(
    elemOrAtt   : NodeInfo,
    xpathString : String,
    avt         : Boolean,
    libraryName : String,
    norewrite   : Array[String]
  ): String =
    FormRunnerRename.replaceVarReferencesWithFunctionCalls(
      xpathString      = xpathString,
      namespaceMapping = namespaceMappingForNode(elemOrAtt),
      library          = componentsFunctionLibrary,
      avt              = avt,
      libraryNameOpt   = libraryName.trimAllToOpt,
      norewrite        = norewrite.toSet
    )(newIndentedLogger)

  // Called from XSLT only
  // https://github.com/orbeon/orbeon-forms/issues/6837
  //@XPathFunction
  def replaceVarReferencesWithFunctionCallsForAction(
    elemOrAtt   : NodeInfo,
    xpathString : String,
    avt         : Boolean,
    libraryName : String,
    norewrite   : Array[String]
  ): String =
    FormRunnerRename.replaceVarReferencesWithFunctionCallsForAction(
      xpathString      = xpathString,
      namespaceMapping = namespaceMappingForNode(elemOrAtt),
      library          = componentsFunctionLibrary,
      avt              = avt,
      libraryNameOpt   = libraryName.trimAllToOpt,
      norewrite        = norewrite.toSet
    )(newIndentedLogger)

  // Called from XSLT only
  //@XPathFunction
  def replaceVarReferencesWithFunctionCallsFromPropertyAsString(
    propertyName : String,
    avt          : Boolean,
    libraryName  : String,
    norewrite    : Array[String]
  ): String =
  FormRunnerRename.replaceVarReferencesWithFunctionCallsFromPropertyAsString(
    propertyName    = propertyName,
    avt             = avt,
    libraryName     = libraryName.trimAllToOpt,
    norewrite       = norewrite.toSet,
    functionLibrary = componentsFunctionLibrary
  )(newIndentedLogger).getOrElse("") // xxx should be null, so result is empty sequence, right?

  // Called from XSLT only
  //@XPathFunction
  def knownConstraintsToAutomaticHint(
    attributesWithXPath : Array[NodeInfo]
  ): String = {

    val constraints = attributesWithXPath.toList.flatMap (attributeWithXPath =>
      FormRunnerCommonConstraint.analyzeKnownConstraint(
        attributeWithXPath.getStringValue,
        namespaceMappingForNode(attributeWithXPath),
        componentsFunctionLibrary
      )(newIndentedLogger)
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
        val displaySizeOpt = value.toLongOption.map(ByteSizeUtils.byteCountToDisplaySize)
        displaySizeOpt.map { displaySize =>
          hintMessageXPath(ValidationFunctionNames.currentName(constraintName), s"'$displaySize'")
        }

      case (constraintName @ ValidationFunctionNames.UploadMaxFilesPerControl, Some(max)) =>
        Some(hintMessageXPath(ValidationFunctionNames.currentName(constraintName), s"'$max'"))

      case (constraintName @ ValidationFunctionNames.UploadMediatypes, Some(mediatypesStr)) =>
        val mediatypes        = mediatypesStr.splitTo[List]()
        val displayMediatypes = mediatypes.flatMap { mediatype =>

          val slashPosition     = mediatype.indexOf('/')
          val isProperMediatype = slashPosition > 1 && slashPosition < mediatype.length - 1
          isProperMediatype.flatOption {
            val mediatypeLeft   = mediatype.substring(0, slashPosition)
            val mediatypeRight  = mediatype.substring(slashPosition + 1)
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
          }
        }

        displayMediatypes match {
          case Nil => None
          case _   =>
            val combined = displayMediatypes.mkString(", ")
            Some(hintMessageXPath(constraintName, s"string-join(($combined), ', ')"))
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

  def componentsFunctionLibrary: FunctionLibrary =
    new FunctionLibraryList                         |!>
      (_.addFunctionLibrary(XFormsFunctionLibrary)) |!>
      (_.addFunctionLibrary(FormRunnerFunctionLibrary))

  def newIndentedLogger: IndentedLogger =
    new IndentedLogger(FormRunnerLogger)

  private val FormRunnerLogger: Logger = LoggerFactory.createLogger(FormRunner.getClass)

  private def namespaceMappingForNode(elemOrAtt: NodeInfo): NamespaceMapping =
    NamespaceMapping(elemOrAtt.namespaceMappings.toMap)
}
