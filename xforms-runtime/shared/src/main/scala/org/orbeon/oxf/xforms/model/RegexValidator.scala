/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orbeon.oxf.xforms.model

import java.io.Serializable
import java.util.regex.Pattern
import java.{lang => jl}

import org.orbeon.oxf.util.StringUtils._


class RegexValidator(regexs: Array[String], caseSensitive: Boolean)
  extends Serializable {

  require((regexs ne null) && regexs.nonEmpty, "Regular expressions are missing")

  private val patterns = {
    val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
    regexs map { regex =>
      require(regex.nonAllBlank, s"Regular expression is missing")
      Pattern.compile(regex, flags)
    }
  }

  def this(regex: String, caseSensitive: Boolean) =
    this(Array[String](regex), caseSensitive)

  def this(regex: String) =
    this(regex, caseSensitive = true)

  def this(regexs: Array[String]) =
    this(regexs, true)

  // ORBEON: Unused.
  def isValid(value: String): Boolean =
    if (value eq null)
      false
    else
      patterns forall (_.matcher(value).matches)

  // ORBEON: Renamed from `match`.
  def matches(value: String): Array[String] = {
    if (value == null)
      null
    else
      patterns.iterator map (_.matcher(value)) collectFirst {
        case matcher if matcher.matches =>
          val count = matcher.groupCount
          val groups = new Array[String](count)
          for (j <- 0 until count)
            groups(j) = matcher.group(j + 1)
          groups
      } orNull
  }

  // ORBEON: Unused.
  def validate(value: String): String = {
    if (value == null)
      return null
    for (i <- patterns.indices) {
      val matcher = patterns(i).matcher(value)
      if (matcher.matches) {
        val count = matcher.groupCount
        if (count == 1)
          return matcher.group(1)
        val buffer = new jl.StringBuilder
        for (j <- 0 until count) {
          val component = matcher.group(j + 1)
          if (component != null)
            buffer.append(component)
        }
        return buffer.toString
      }
    }
    null
  }

  override def toString: String = {
    val buffer = new jl.StringBuilder
    buffer.append("RegexValidator{")
    for (i <- patterns.indices) {
      if (i > 0)
        buffer.append(",")
      buffer.append(patterns(i).pattern)
    }
    buffer.append("}")
    buffer.toString
  }
}