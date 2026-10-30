/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml

import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.xml.NamespaceMapping


/**
 * Holds function whitelisting/blacklisting rules for user-defined XPath expressions in forms.
 *
 * Resolution precedence in `isAllowed(uri, localName)`:
 * 1. Denied specific QNames: `deniedQNames.contains((uri, localName))` -> `false`
 * 2. Allowed specific QNames: `allowedQNames.contains((uri, localName))` -> `true`
 * 3. Denied specific namespaces: `deniedNamespaces.contains(uri)` -> `false`
 * 4. Allowed specific namespaces: `allowedNamespaces.contains(uri)` -> `true`
 * 5. Deny Java: `denyJava && isJavaUri(uri)` -> `false`
 * 6. `allowAll` -> `true`
 * 7. Allow Java: `allowJava && isJavaUri(uri)` -> `true`
 * 8. Default -> `false`
 */
case class AllowedFunctions(
  allowAll          : Boolean,
  allowedNamespaces : Set[String],
  allowedQNames     : Set[(String, String)],
  deniedNamespaces  : Set[String],
  deniedQNames      : Set[(String, String)],
  denyJava          : Boolean = false,
  allowJava         : Boolean = false
) {

  def isAllowed(uri: String, localName: String): Boolean = {
    val nonNullUri = if (uri eq null) "" else uri
    val nonNullLocalName = if (localName eq null) "" else localName

    if (deniedQNames.contains((nonNullUri, nonNullLocalName)))
      false
    else if (allowedQNames.contains((nonNullUri, nonNullLocalName)))
      true
    else if (deniedNamespaces.contains(nonNullUri))
      false
    else if (allowedNamespaces.contains(nonNullUri))
      true
    else if (denyJava && AllowedFunctions.isJavaUri(nonNullUri))
      false
    else if (allowAll)
      true
    else if (allowJava && AllowedFunctions.isJavaUri(nonNullUri))
      true
    else
      false
  }

  /**
   * Return an `AllowedFunctions` with Form Runner compiler internal helper functions allowed.
   */
  def withCompilerHelpers: AllowedFunctions =
    copy(
      allowedQNames = allowedQNames ++ AllowedFunctions.CompilerHelperFunctions,
      deniedQNames  = deniedQNames -- AllowedFunctions.CompilerHelperFunctions
    )

  /**
   * Return an updated `AllowedFunctions` by parsing `updatesSpec` tokens on top of this instance.
   */
  def update(updatesSpec: String, ns: NamespaceMapping = NamespaceMapping.EmptyMapping): AllowedFunctions =
    AllowedFunctions.parse(updatesSpec, ns, base = Some(this))
}

object AllowedFunctions {

  private val Logger = LoggerFactory.createLogger(getClass)

  val DefaultFunctionNamespace = "http://www.w3.org/2005/xpath-functions"

  val CompilerHelperFunctions: Set[(String, String)] = Set(
    ("java:org.orbeon.oxf.fr.FormRunner", "controlVariableValue"),          // safe, internal
    ("java:org.orbeon.oxf.fr.FormRunner", "controlVariableValueForAction"), // safe, internal
    ("java:org.orbeon.oxf.fr.FormRunner", "buildLinkBackToFormRunner"),     // safe, internal
    ("java:org.orbeon.oxf.fr.FormRunner", "controlNameFromId"),             // safe, internal, used by choice validation

    ("http://orbeon.org/oxf/xml/form-builder", "lang"),          // safe

    ("http://orbeon.org/oxf/xml/xforms", "evaluate-avt"),       // checks whitelisted functions at runtime
    ("http://orbeon.org/oxf/xml/xforms", "r"),                  // safe
    ("http://orbeon.org/oxf/xml/xforms", "escape-xml-minimal"), // safe
  )

  val Empty: AllowedFunctions = AllowedFunctions(
    allowAll          = false,
    allowedNamespaces = Set.empty,
    allowedQNames     = Set.empty,
    deniedNamespaces  = Set.empty,
    deniedQNames      = Set.empty,
    denyJava          = false,
    allowJava         = false
  )

  val DefaultSpec: String =
    """Q{http://www.w3.org/2005/xpath-functions/math}*
      |Q{http://www.w3.org/2005/xpath-functions}*
      |Q{http://www.w3.org/2002/xforms}*
      |Q{http://www.w3.org/2001/XMLSchema}*
      |Q{http://www.w3.org/2005/xpath-functions/map}*
      |Q{http://www.w3.org/2005/xpath-functions/array}*
      |Q{http://orbeon.org/oxf/xml/xforms}*
      |Q{http://www.exforms.org/exf/1-0}*
      |Q{http://orbeon.org/oxf/xml/form-runner}*
      |-Q{http://www.w3.org/2005/xpath-functions}doc
      |-Q{http://www.w3.org/2005/xpath-functions}doc-available
      |-Q{http://www.w3.org/2005/xpath-functions}collection
      |-Q{http://www.w3.org/2005/xpath-functions}unparsed-text
      |-Q{http://www.w3.org/2005/xpath-functions}unparsed-text-available
      |-Q{http://www.w3.org/2005/xpath-functions}unparsed-text-lines
      |-Q{http://www.w3.org/2005/xpath-functions}uri-collection
      |-Q{http://www.w3.org/2005/xpath-functions}environment-variable
      |-Q{http://www.w3.org/2005/xpath-functions}available-environment-variables
      |-Q{http://www.w3.org/2005/xpath-functions}load-xquery-module
      |-Q{http://www.w3.org/2005/xpath-functions}transform
      |-Q{http://orbeon.org/oxf/xml/xforms}evaluate
      |-Q{http://orbeon.org/oxf/xml/xforms}evaluate-in-context
      |-Q{http://orbeon.org/oxf/xml/xforms}call-xpl
      |-Q{http://orbeon.org/oxf/xml/xforms}instance
      |-Q{http://orbeon.org/oxf/xml/xforms}doc
      |-Q{http://orbeon.org/oxf/xml/xforms}doc-base64
      |-Q{http://orbeon.org/oxf/xml/xforms}doc-base64-available
      |-Q{http://orbeon.org/oxf/xml/xforms}image-metadata
      |-Q{http://orbeon.org/oxf/xml/xforms}property
      |-Q{http://orbeon.org/oxf/xml/xforms}properties-start-with
      |-Q{http://orbeon.org/oxf/xml/xforms}get-session-attribute
      |-Q{http://orbeon.org/oxf/xml/xforms}set-session-attribute
      |-Q{http://orbeon.org/oxf/xml/xforms}get-request-attribute
      |-Q{http://orbeon.org/oxf/xml/xforms}set-request-attribute
      |-Q{http://orbeon.org/oxf/xml/xforms}get-document-attribute
      |-Q{http://orbeon.org/oxf/xml/xforms}set-document-attribute
      |-Q{http://orbeon.org/oxf/xml/xforms}remove-document-attribute
      |-Q{http://orbeon.org/oxf/xml/xforms}remove-document-attributes
      |-Q{http://orbeon.org/oxf/xml/form-runner}evaluate-from-property
      |-Q{http://orbeon.org/oxf/xml/form-runner}run-process
      |-Q{http://orbeon.org/oxf/xml/form-runner}save-state
      |-Q{http://orbeon.org/oxf/xml/form-runner}transform-uploaded-image""".stripMargin

  lazy val Default: AllowedFunctions = parse(DefaultSpec)

  def isJavaUri(uri: String): Boolean =
    (uri ne null) && uri.startsWith("java:")

  /**
   * Parse a whitespace-separated specification of allowed/denied function tokens.
   */
  def parse(
    spec: String,
    ns  : NamespaceMapping         = NamespaceMapping.EmptyMapping,
    base: Option[AllowedFunctions] = None
  ): AllowedFunctions = {
    var allowAll          = base.exists(_.allowAll)
    var allowedNamespaces = base.map(_.allowedNamespaces).getOrElse(Set.empty[String])
    var allowedQNames     = base.map(_.allowedQNames).getOrElse(Set.empty[(String, String)])
    var deniedNamespaces  = base.map(_.deniedNamespaces).getOrElse(Set.empty[String])
    var deniedQNames      = base.map(_.deniedQNames).getOrElse(Set.empty[(String, String)])
    var denyJava          = base.exists(_.denyJava)
    var allowJava         = base.exists(_.allowJava)

    val mapping = if (ns ne null) ns.mapping else Map.empty[String, String]

    def resolvePrefix(prefix: String): Option[String] =
      mapping.get(prefix)

    val tokens = Option(spec).map(_.splitTo[List]()).getOrElse(Nil)

    for (token <- tokens) {
      val (sign, rawName) =
        if (token.startsWith("+"))
          ('+', token.substring(1))
        else if (token.startsWith("-"))
          ('-', token.substring(1))
        else
          ('+', token)

      if (rawName == "*" || rawName == "*:*") {
        if (sign == '+') {
          allowAll = true
        } else {
          allowAll          = false
          allowedNamespaces = Set.empty
          allowedQNames     = Set.empty
          deniedNamespaces  = Set.empty
          deniedQNames      = Set.empty
          denyJava          = false
          allowJava         = false
        }
      } else if (rawName == "java:*" || rawName == "java") {
        if (sign == '+') {
          allowJava = true
          denyJava  = false
        } else {
          denyJava  = true
          allowJava = false
        }
      } else if (rawName.startsWith("java:")) {
        // e.g. java:java.lang.Math:* or java:java.lang.Math:abs
        if (rawName.endsWith(":*")) {
          val javaClassUri = rawName.dropRight(2)
          if (sign == '+') {
            allowedNamespaces += javaClassUri
            deniedNamespaces  -= javaClassUri
          } else {
            deniedNamespaces  += javaClassUri
            allowedNamespaces -= javaClassUri
          }
        } else {
          val lastColon = rawName.lastIndexOf(':')
          if (lastColon > 4) { // "java:" is 5 chars, so lastColon must be after "java:"
            val javaClassUri = rawName.substring(0, lastColon)
            val localName    = rawName.substring(lastColon + 1)
            val pair         = (javaClassUri, localName)
            if (sign == '+') {
              allowedQNames += pair
              deniedQNames  -= pair
            } else {
              deniedQNames  += pair
              allowedQNames -= pair
            }
          } else {
            Logger.warn(s"Invalid Java function token: '$token'")
          }
        }
      } else if (rawName.startsWith("Q{")) {
        val closingBraceIdx = rawName.indexOf('}')
        if (closingBraceIdx >= 2) {
          val uri       = rawName.substring(2, closingBraceIdx)
          val localName = rawName.substring(closingBraceIdx + 1)
          if (localName == "*") {
            if (sign == '+') {
              allowedNamespaces += uri
              deniedNamespaces  -= uri
            } else {
              deniedNamespaces  += uri
              allowedNamespaces -= uri
            }
          } else if (localName.nonEmpty) {
            val effectiveUri = if (uri.isEmpty) DefaultFunctionNamespace else uri
            val pair = (effectiveUri, localName)
            if (sign == '+') {
              allowedQNames += pair
              deniedQNames  -= pair
            } else {
              deniedQNames  += pair
              allowedQNames -= pair
            }
          } else {
            Logger.warn(s"Invalid URI-qualified name token with empty local name: '$token'")
          }
        } else {
          Logger.warn(s"Invalid URI-qualified name token missing closing brace: '$token'")
        }
      } else {
        val colonIdx = rawName.indexOf(':')
        if (colonIdx > 0) {
          val prefix    = rawName.substring(0, colonIdx)
          val localName = rawName.substring(colonIdx + 1)

          if (localName == "*") {
            // prefix:*
            if (prefix == "java") {
              if (sign == '+') {
                allowJava = true
                denyJava  = false
              } else {
                denyJava  = true
                allowJava = false
              }
            } else {
              resolvePrefix(prefix) match {
                case Some(uri) =>
                  if (sign == '+') {
                    allowedNamespaces += uri
                    deniedNamespaces  -= uri
                  } else {
                    deniedNamespaces  += uri
                    allowedNamespaces -= uri
                  }
                case None =>
                  Logger.warn(s"Unknown prefix '$prefix' in `allowed-functions` token '$token'")
              }
            }
          } else {
            // prefix:localName
            resolvePrefix(prefix) match {
              case Some(uri) =>
                val pair = (uri, localName)
                if (sign == '+') {
                  allowedQNames += pair
                  deniedQNames  -= pair
                } else {
                  deniedQNames  += pair
                  allowedQNames -= pair
                }
              case None =>
                Logger.warn(s"Unknown prefix '$prefix' in `allowed-functions` token '$token'")
            }
          }
        } else {
          // Unprefixed name, e.g. "concat" or "-doc"
          val pair = (DefaultFunctionNamespace, rawName)
          if (sign == '+') {
            allowedQNames += pair
            deniedQNames  -= pair
          } else {
            deniedQNames  += pair
            allowedQNames -= pair
          }
        }
      }
    }

    AllowedFunctions(
      allowAll          = allowAll,
      allowedNamespaces = allowedNamespaces,
      allowedQNames     = allowedQNames,
      deniedNamespaces  = deniedNamespaces,
      deniedQNames      = deniedQNames,
      denyJava          = denyJava,
      allowJava         = allowJava
    )
  }

  /**
   * Convert qualified name tokens in a specification to UriQualifiedName (`Q{uri}localName`)
   * using the provided in-scope namespace mapping.
   *
   * Tokens that are already `UriQualifiedName`s, wildcards (`*`), or Java tokens (`java:*`, `java:...`)
   * are left unchanged. Unprefixed names (e.g. `concat`) are also left unchanged.
   */
  def convertToUriQualifiedNames(spec: String, ns: NamespaceMapping = NamespaceMapping.EmptyMapping): String = {
    val mapping = if (ns ne null) ns.mapping else Map.empty[String, String]

    def resolvePrefix(prefix: String): Option[String] =
      mapping.get(prefix)

    val tokens = Option(spec).map(_.splitTo[List]()).getOrElse(Nil)
    val converted = tokens.map { token =>
      val (signStr, rawName) =
        if (token.startsWith("+"))
          ("+", token.substring(1))
        else if (token.startsWith("-"))
          ("-", token.substring(1))
        else
          ("", token)

      if (rawName == "*" || rawName == "*:*" || rawName == "java" || rawName == "java:*" || rawName.startsWith("java:") || rawName.startsWith("Q{")) {
        token
      } else {
        val colonIdx = rawName.indexOf(':')
        if (colonIdx > 0) {
          val prefix    = rawName.substring(0, colonIdx)
          val localName = rawName.substring(colonIdx + 1)
          if (prefix == "java") {
            token
          } else {
            resolvePrefix(prefix) match {
              case Some(uri) =>
                s"${signStr}Q{$uri}$localName"
              case None =>
                Logger.warn(s"Unknown prefix '$prefix' in allowed-functions token '$token'")
                token
            }
          }
        } else {
          // Unprefixed name like `concat` or `-doc`
          token
        }
      }
    }
    converted.mkString(" ")
  }
}
