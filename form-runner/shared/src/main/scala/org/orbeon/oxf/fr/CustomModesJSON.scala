package org.orbeon.oxf.fr

import io.circe.parser.parse
import org.orbeon.oxf.fr.definitions.{FormRunnerDetailMode, ModeType}
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.xml.NamespaceMapping

import scala.util.{Success, Try}


object CustomModesJSON {

  private val DefaultModeType    = ModeType.Readonly
  private val DefaultPersistence = false

  // Example JSON:
  // ```json
  // [
  //   {
  //     "public-name": "sign", // optional
  //     "name": "fr:sign",
  //     "mode-type": "readonly",
  //     "persistence": false
  //   }
  // ]```

  private val AllowedMapKeys = Set(
    "public-name",
    "name",
    "mode-type",
    "persistence",
  )

  def parseString(jsonString: String, ns: NamespaceMapping): Try[Seq[FormRunnerDetailMode.Custom]] =
    jsonString.trimAllToOpt match {
      case Some(trimmedJsonString) =>

        def error(): Nothing =
          throw new IllegalArgumentException(trimmedJsonString)

        parse(trimmedJsonString).toTry.map { topLevelJson =>
          topLevelJson.asArray.getOrElse(error()).map { jsonArrayItem =>
            val map = jsonArrayItem.asObject.getOrElse(error()).toMap

            if (! map.keys.forall(AllowedMapKeys))
              error()

            val modeQName =
              map
                .get("name")
                .flatMap(_.asString)
                .flatMap(Extensions.resolveValidateQNameOrThrow(ns.mapping.get, _, unprefixedIsNoNamespace = true))
                .getOrElse(error())

            FormRunnerDetailMode.Custom(
              publicName  = map.get("public-name").flatMap(_.asString).getOrElse(modeQName.qualifiedName),
              name        = modeQName,
              modeType    = map.get("mode-type").flatMap(_.asString).map(ModeType.fromModeTypeString(_).getOrElse(error())).getOrElse(DefaultModeType),
              persistence = map.get("persistence").flatMap(_.asBoolean).getOrElse(DefaultPersistence)
            )
          }
        }

      case None =>
        Success(Nil)
    }
}
