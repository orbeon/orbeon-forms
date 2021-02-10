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


/**
 * Perform email validations.
 *
 * Based on a script by Sandeep V. Tamhankar.
 *
 * This implementation is not guaranteed to catch all possible errors in an email address.
 */
object EmailValidator {

  private val SPECIAL_CHARS     = "\\p{Cntrl}\\(\\)<>@,;:'\\\\\\\"\\.\\[\\]"
  private val VALID_CHARS       = "(\\\\.)|[^\\s" + SPECIAL_CHARS + "]"
  private val QUOTED_USER       = "(\"(\\\\\"|[^\"])*\")"
  private val WORD =            "((" + VALID_CHARS + "|')+|" + QUOTED_USER + ")"
  private val EMAIL_REGEX       = "^(.+)@(\\S+)$"
// ORBEON: Remove support for `DomainValidator`.
//  private val IP_DOMAIN_REGEX   = "^\\[(.*)\\]$"
  private val USER_REGEX        = "^" + WORD + "(\\." + WORD + ")*$"
  private val EMAIL_PATTERN     = Pattern.compile(EMAIL_REGEX)
// ORBEON: Remove support for `DomainValidator`.
//  private val IP_DOMAIN_PATTERN = Pattern.compile(IP_DOMAIN_REGEX)
  private val USER_PATTERN      = Pattern.compile(USER_REGEX)
  private val MAX_USERNAME_LEN  = 64

  /**
   * Singleton instance of this class, which
   * doesn't consider local addresses as valid.
   */
  private val EMAIL_VALIDATOR = new EmailValidator(false, false)
  private val EMAIL_VALIDATOR_WITH_TLD = new EmailValidator(false, true)
  /**
   * Singleton instance of this class, which does
   * consider local addresses valid.
   */
  private val EMAIL_VALIDATOR_WITH_LOCAL = new EmailValidator(true, false)
  private val EMAIL_VALIDATOR_WITH_LOCAL_WITH_TLD = new EmailValidator(true, true)

  /**
   * Returns the Singleton instance of this validator.
   *
   * @return singleton instance of this validator.
   */
  def getInstance: EmailValidator = EMAIL_VALIDATOR

  /**
   * Returns the Singleton instance of this validator,
   * with local validation as required.
   *
   * @param allowLocal Should local addresses be considered valid?
   * @param allowTld   Should TLDs be allowed?
   * @return singleton instance of this validator
   */
  def getInstance(allowLocal: Boolean, allowTld: Boolean): EmailValidator =
    if (allowLocal)
      if (allowTld)
        EMAIL_VALIDATOR_WITH_LOCAL_WITH_TLD
      else
        EMAIL_VALIDATOR_WITH_LOCAL
    else if (allowTld)
      EMAIL_VALIDATOR_WITH_TLD
    else
      EMAIL_VALIDATOR

  /**
   * Returns the Singleton instance of this validator,
   * with local validation as required.
   *
   * @param allowLocal Should local addresses be considered valid?
   * @return singleton instance of this validator
   */
  def getInstance(allowLocal: Boolean): EmailValidator = getInstance(allowLocal, false)
}

class EmailValidator extends Serializable {

  final private var allowTld = false
// ORBEON: Remove support for `DomainValidator`.
//  final private var domainValidator: DomainValidator = null

//  /**
//   * constructor for creating instances with the specified domainValidator
//   *
//   * @param allowLocal      Should local addresses be considered valid?
//   * @param allowTld        Should TLDs be allowed?
//   * @param domainValidator allow override of the DomainValidator.
//   *                        The instance must have the same allowLocal setting.
//   * @since 1.7
//   */
//  def this(allowLocal: Boolean, allowTld: Boolean, domainValidator: DomainValidator) {
//    this()
//    this.allowTld = allowTld
//    if (domainValidator == null)
//      throw new IllegalArgumentException("DomainValidator cannot be null")
//    else {
//      if (domainValidator.isAllowLocal != allowLocal)
//        throw new IllegalArgumentException("DomainValidator must agree with allowLocal setting")
//      this.domainValidator = domainValidator
//    }
//  }

  /**
   * Protected constructor for subclasses to use.
   *
   * @param allowLocal Should local addresses be considered valid?
   * @param allowTld   Should TLDs be allowed?
   */
  def this(allowLocal: Boolean, allowTld: Boolean) {
    this()
    this.allowTld = allowTld
//    this.domainValidator = DomainValidator.getInstance(allowLocal)
  }

  /**
   * Protected constructor for subclasses to use.
   *
   * @param allowLocal Should local addresses be considered valid?
   */
  def this(allowLocal: Boolean) {
    this(allowLocal, allowTld = false)
  }

  /**
   * <p>Checks if a field has a valid e-mail address.</p>
   *
   * @param email The value validation is being performed on.  A <code>null</code>
   *              value is considered invalid.
   * @return true if the email address is valid.
   */
  def isValid(email: String): Boolean = {

    if (email == null)
      return false

    if (email.endsWith("."))  // check this first - it's cheap!
      return false

    // Check the whole email address structure
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

  /**
   * Returns true if the domain component of an email address is valid.
   *
   * @param domain being validated, may be in IDN format
   * @return true if the email address's domain is valid.
   */
  protected def isValidDomain(domain: String): Boolean = {
    throw new NotImplementedError("isValidDomain")
// ORBEON: Remove support for `DomainValidator`.
//    // see if domain is an IP address in brackets
//    val ipDomainMatcher = EmailValidator.IP_DOMAIN_PATTERN.matcher(domain)
//    if (ipDomainMatcher.matches) {
//      val inetAddressValidator = InetAddressValidator.getInstance
//      return inetAddressValidator.isValid(ipDomainMatcher.group(1))
//    }
//    // Domain is symbolic name
//    if (allowTld)
//      domainValidator.isValid(domain) || (!domain.startsWith(".") && domainValidator.isValidTld(domain))
//    else
//      domainValidator.isValid(domain)
  }

  /**
   * Returns true if the user component of an email address is valid.
   *
   * @param user being validated
   * @return true if the user name is valid.
   */
  protected def isValidUser(user: String): Boolean = {
    if (user == null || user.length > EmailValidator.MAX_USERNAME_LEN)
      return false
    EmailValidator.USER_PATTERN.matcher(user).matches
  }
}