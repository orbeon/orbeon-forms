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

  val UuidFieldName            : String = "$uuid"
  val ServerEventsFieldName    : String = "$server-events"

  val RepeatSeparator          : String = "\u2299" // ⊙ CIRCLED DOT OPERATOR
  val RepeatIndexSeparator     : String = "-"      // - (just has to not be a digit)
  val ComponentSeparator       : Char   = '\u2261' // ≡ IDENTICAL TO
  val ComponentSeparatorString : String = ComponentSeparator.toString
  val AbsoluteIdSeparator      : Char   = '|'      // | see https://github.com/orbeon/orbeon-forms/issues/551

  val YuiSkinSamClass          : String = "yui-skin-sam"
  val XFormsIosClass           : String = "xforms-ios"
  val XFormsMobileClass        : String = "xforms-mobile"
  val FormClass                : String = "xforms-form"
  val InitiallyHiddenClass     : String = "xforms-initially-hidden"

  val HtmlLangAttr             : String = "lang"

  val DocumentId               : String = "#document"
}
