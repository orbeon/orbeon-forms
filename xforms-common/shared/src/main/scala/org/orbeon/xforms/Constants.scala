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

  val RepeatSeparator                   = "\u2299" // ⊙ CIRCLED DOT OPERATOR
  val RepeatIndexSeparator              = "-"      // - (just has to not be a digit)
  val ComponentSeparator       : Char   = '\u2261' // ≡ IDENTICAL TO
  val ComponentSeparatorString : String = ComponentSeparator.toString
  val AbsoluteIdSeparator      : Char   = '|'      // | see https://github.com/orbeon/orbeon-forms/issues/551

  val YuiSkinSamClass                   = "yui-skin-sam"
  val XFormsIosClass                    = "xforms-ios"
  val XFormsMobileClass                 = "xforms-mobile"
  val FormClass                         = "xforms-form"
  val InitiallyHiddenClass              = "xforms-initially-hidden"

  val HtmlLangAttr                      = "lang"

  val DocumentId                        = "#document"
}
