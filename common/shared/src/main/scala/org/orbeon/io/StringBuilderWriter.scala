/**
  * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.io

import java.io.{Serializable, Writer}

class StringBuilderWriter(val builder: java.lang.StringBuilder = new java.lang.StringBuilder)
  extends Writer with Serializable {

  override def append(value: Char): Writer = {
    builder.append(value)
    this
  }

  override def append(value: CharSequence): Writer = {
    builder.append(value)
    this
  }

  override def append(value: CharSequence, start: Int, end: Int): Writer = {
    builder.append(value, start, end)
    this
  }

  def close(): Unit = ()
  def flush(): Unit = ()

  override def write(value: String): Unit =
    if (value ne null) builder.append(value)

  def write(value: Array[Char], offset: Int, length: Int): Unit =
    if (value ne null) builder.append(value, offset, length)

  override def toString: String = builder.toString
}