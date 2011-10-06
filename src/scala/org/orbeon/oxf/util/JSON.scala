/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util


object JSON {
    // This function from lift-json
    // Copyright 2009-2010 WorldWide Conferencing, LLC
    // "Licensed under the Apache License, Version 2.0 (the "License")"
    // https://github.com/lift/lift/blob/master/framework/lift-base/lift-json/src/main/scala/net/liftweb/json/JsonAST.scala
    def quoteValue(s: String): String = {
        val buf = new StringBuilder
        for (i ← 0 until s.length) {
            val c = s.charAt(i)
            buf.append(c match {
                case '"' ⇒ "\\\""
                case '\\' ⇒ "\\\\"
                case '\b' ⇒ "\\b"
                case '\f' ⇒ "\\f"
                case '\n' ⇒ "\\n"
                case '\r' ⇒ "\\r"
                case '\t' ⇒ "\\t"
                case c if ((c >= '\u0000' && c < '\u001f') || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) ⇒ "\\u%04x".format(c: Int)
                case c ⇒ c
            })
        }
        buf.toString
    }
}