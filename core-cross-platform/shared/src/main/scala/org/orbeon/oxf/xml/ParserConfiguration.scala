package org.orbeon.oxf.xml

import org.orbeon.oxf.http.URIReferences


object ParserConfiguration {

  val Plain       : ParserConfiguration = ParserConfiguration(validating = false, handleXInclude = false, externalEntities = false)
  val XIncludeOnly: ParserConfiguration = ParserConfiguration(validating = false, handleXInclude = true,  externalEntities = false)

  // Used by `URLGenerator` only
  def apply(parserConfiguration: ParserConfiguration, uriReferences: URIReferences): ParserConfiguration =
    ParserConfiguration(
      parserConfiguration.validating,
      parserConfiguration.handleXInclude,
      parserConfiguration.externalEntities,
      uriReferences
    )
}

case class ParserConfiguration(
  validating       : Boolean,
  handleXInclude   : Boolean,
  externalEntities : Boolean,
  uriReferences    : URIReferences = null
) {
  def getKey: String =
    (if (validating) "1" else "0") + (if (handleXInclude) "1" else "0") + (if (externalEntities) "1" else "0")
}