package org.orbeon.oxf.fr

import java.{util => ju}
import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.util.CoreUtils.{BooleanOps, PipeOps}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{FileUtils, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunctionNames
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xml.NamespaceMapping

import scala.collection.compat._
import scala.jdk.CollectionConverters.mapAsJavaMapConverter

trait FormRunnerComponents {

  private val Logger = LoggerFactory.createLogger(FormRunner.getClass)
  private val indentedLogger: IndentedLogger = new IndentedLogger(Logger)
  private val DefaultNorewriteSet = Set("fr-lang")

  // In an XPath expression, replace non-local variable references.
  //@XPathFunction
  def replaceVarReferencesWithFunctionCalls(
    elemOrAtt   : NodeInfo,
    xpathString : String,
    avt         : Boolean,
    libraryName : String,
    norewrite   : Array[String]
  ): String =
    FormRunnerRename.replaceVarReferencesWithFunctionCalls(
      xpathString,
      Private.namespaceMappingForNode(elemOrAtt),
      Private.componentsFunctionLibrary,
      avt,
      name =>
        if ((DefaultNorewriteSet ++ norewrite)(name))
          s"$$$name"
        else
          s"frf:controlVariableValue('$name', ${libraryName.trimAllToOpt.map("'" + _ + "'").getOrElse("()")})"
    )(indentedLogger)

  //@XPathFunction
  def knownConstraintsToAutomaticHint(
    attributesWithXPath : Array[NodeInfo]
  ): String = {

    val constraints = attributesWithXPath.toList.flatMap (attributeWithXPath =>
      FormRunnerCommonConstraint.analyzeKnownConstraint(
        attributeWithXPath.getStringValue,
        Private.namespaceMappingForNode(attributeWithXPath),
        Private.componentsFunctionLibrary
      )(indentedLogger)
    )

    def hintResourceXPath(key: String): String =
      s"xxf:r('detail.hints.$key', '|fr-fr-resources|')"
    def hintMessageXPath(constraintName: String, displayValueXPath: String): String = {
      val template = hintResourceXPath(constraintName)
      s"xxf:format-message($template, $displayValueXPath)"
    }

    val hints = constraints.flatMap {
      case (constraintName @ ValidationFunctionNames.UploadMaxSize, Some(value)) =>
        val displaySizeOpt = value.toLongOption.map(FileUtils.byteCountToDisplaySize)
        displaySizeOpt.map(displaySize => hintMessageXPath(constraintName, s"'$displaySize'"))
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

    def namespaceMappingForNode(elemOrAtt: NodeInfo): NamespaceMapping =
      NamespaceMapping(elemOrAtt.namespaceMappings.toMap)

    def componentsFunctionLibrary: FunctionLibrary =
        new FunctionLibraryList                         |!>
          (_.addFunctionLibrary(XFormsFunctionLibrary)) |!>
          (_.addFunctionLibrary(FormRunnerFunctionLibrary))
  }
}
