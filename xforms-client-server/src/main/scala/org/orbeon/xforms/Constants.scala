/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms


object Constants {

  val UuidFieldName                     = "$uuid"
  val SubmissionIdFieldName             = "$submission-id"

  val RepeatSeparator                   = '\u2299' // ⊙ CIRCLED DOT OPERATOR
  val RepeatSeparatorString    : String = RepeatSeparator.toString

  val RepeatIndexSeparator              = '-'      // - (just has to not be a digit)
  val RepeatIndexSeparatorString: String = RepeatIndexSeparator.toString

  val ComponentSeparator       : Char   = '\u2261' // ≡ IDENTICAL TO
  val ComponentSeparatorString : String = ComponentSeparator.toString

  val LhhacSeparator           : String = ComponentSeparatorString + ComponentSeparatorString

  val AbsoluteIdSeparator      : Char   = '|'      // | see https://github.com/orbeon/orbeon-forms/issues/551

  val YuiSkinSamClass                   = "yui-skin-sam"
  val XFormsIosClass                    = "xforms-ios"
  val XFormsMobileClass                 = "xforms-mobile"
  val FormClass                         = "xforms-form"
  val InitiallyHiddenClass              = "xforms-initially-hidden"

  val HtmlLangAttr                      = "lang"

  val DocumentId                        = "#document"

  // NOTE: We could use a short SVG image but it's not guaranteed to be 1x1 IIUC.
  //     data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg'/%3E
  val DUMMY_IMAGE_URI  = "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==" // smallest GIF 1x1 transparent image

  val NamespacePrefix           = "o"
  val XFormServerPrefix         = "/xforms-server/"
  val XFormsServerSubmit        = "/xforms-server-submit"
  val FormDynamicResourcesPath  = XFormServerPrefix + "form/dynamic/"
  val FormDynamicResourcesRegex = s"$FormDynamicResourcesPath(.+).js".r

  val EmbeddingNamespaceParameter = "orbeon-embedding-namespace"
  val EmbeddingContextParameter   = "orbeon-embedding-context"
  val UpdatesParameter            = "updates"
}
