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
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.{Pattern, PatternSyntaxException}


// Based on a script by Sandeep V. Tamhankar.
object EmailValidator {

  private val SPECIAL_CHARS     = "\\p{Cntrl}\\(\\)<>@,;:'\\\\\\\"\\.\\[\\]"
  private val VALID_CHARS       = "(\\\\.)|[^\\s" + SPECIAL_CHARS + "]"
  private val QUOTED_USER       = "(\"(\\\\\"|[^\"])*\")"
  private val WORD              = "((" + VALID_CHARS + "|')+|" + QUOTED_USER + ")"
  private val EMAIL_REGEX       = "^(.+)@(\\S+)$"
  private val USER_REGEX        = "^" + WORD + "(\\." + WORD + ")*$"
  private val EMAIL_PATTERN     = Pattern.compile(EMAIL_REGEX)
  private val USER_PATTERN      = Pattern.compile(USER_REGEX)
  private val MAX_USERNAME_LEN  = 64

  private val DomainLabelRegex  = "\\p{Alnum}(?>[\\p{Alnum}-]*\\p{Alnum})*"
  private val TopLabelRegex     = "\\p{Alpha}{2,}"
  private val DomainNameRegex   = "^(?:" + DomainLabelRegex + "\\.)+" + "(" + TopLabelRegex + ")$"
  private val DOMAIN_PATTERN    = Pattern.compile(DomainNameRegex)

  val Default = new EmailValidator(None, None)

  private val cache = new ConcurrentHashMap[(Option[String], Option[String]), EmailValidator]()

  def apply(localPartRegexOpt: Option[String] = None, domainRegexOpt: Option[String] = None): EmailValidator =
    (localPartRegexOpt, domainRegexOpt) match {
      case (None, None) => Default
      case key          => cache.computeIfAbsent(key, { case (l, d) => new EmailValidator(l, d) })
    }

  def getInstance: EmailValidator = Default

  def getInstance(allowLocal: Boolean, allowTld: Boolean): EmailValidator = Default

  def getInstance(allowLocal: Boolean): EmailValidator = Default
}

class EmailValidator(
  val localPartRegexOpt: Option[String] = None,
  val domainRegexOpt   : Option[String] = None
) extends Serializable {

  def this(allowLocal: Boolean, allowTld: Boolean) = this(None, None)
  def this(allowLocal: Boolean) = this(None, None)

  private val localPartPatternOpt: Option[Pattern] =
    localPartRegexOpt.map(compileRegex("local part", _))

  private val domainPatternOpt: Option[Pattern] =
    domainRegexOpt.map(compileRegex("domain", _))

  private def compileRegex(partName: String, regex: String): Pattern =
    try Pattern.compile(regex)
    catch {
      case e: PatternSyntaxException =>
        throw new IllegalArgumentException(s"Invalid regular expression '$regex' configured for email $partName validation: ${e.getMessage}", e)
    }

  def isValid(email: String): Boolean = {
    if (email == null)
      return false

    if (email.endsWith("."))
      return false

    val emailMatcher = EmailValidator.EMAIL_PATTERN.matcher(email)
    if (! emailMatcher.matches)
      false
    else if (! isValidUser(emailMatcher.group(1)))
      false
    else if (! isValidDomain(emailMatcher.group(2)))
      false
    else
      true
  }

  protected def isValidDomain(domain: String): Boolean =
    isValidBuiltInDomain(domain) &&
      domainPatternOpt.forall(_.matcher(domain).matches())

  private def isValidBuiltInDomain(domain: String): Boolean =
    domain != null &&
      EmailValidator.DOMAIN_PATTERN.matcher(domain).matches()

  protected def isValidUser(user: String): Boolean =
    isValidBuiltInUser(user) &&
      localPartPatternOpt.forall(_.matcher(user).matches())

  private def isValidBuiltInUser(user: String): Boolean =
    user != null &&
      user.length <= EmailValidator.MAX_USERNAME_LEN &&
      EmailValidator.USER_PATTERN.matcher(user).matches()
}
