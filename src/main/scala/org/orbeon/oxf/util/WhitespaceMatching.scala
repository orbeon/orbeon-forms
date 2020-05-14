/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.orbeon.css.CSSSelectorParser
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.Whitespace._
import org.xml.sax.Attributes

import scala.collection.mutable

object WhitespaceMatching  {

  import CSSSelectorParser._

  // Only support a small subset of selectors for now
  sealed trait Matcher
  case class ElementMatcher(name: (String, String)) extends Matcher
  case class AnyElementChildOfMatcher(name: (String, String)) extends Matcher
  case class ElementAttributeValueMatcher(name: (String, String), attName: String, attValue: String, negate: Boolean) extends Matcher

  type PolicyMatcher = (Policy, (String, String), Attributes, Option[(String, String)]) => Policy

  def defaultBasePolicy: Policy =
    defaultPolicy(BaseScope, Policy.Preserve)

  def defaultHTMLPolicy: Policy =
    defaultPolicy(HTMLScope, Policy.Preserve)

  def basePolicyMatcher: PolicyMatcher =
    policyMatcherFromProperties(None, BaseScope)

  def htmlPolicyMatcher: PolicyMatcher =
    policyMatcherFromProperties(Some(policyMatcherFromProperties(None, BaseScope)), HTMLScope)

  private class PolicyMatcherImpl(val matchers: List[(Matcher, Policy)]) extends PolicyMatcher {

    // Index matchers for efficiency
    // FIXME: for now assume distinct elements names, e.g. xf|foo conflicts w/ xf|foo:not([bar=baz])
    private val (selfElementIndex, parentElementIndex) = {
      val selfMap   = mutable.Map[(String, String), (Matcher, Policy)]()
      val parentMap = mutable.Map[(String, String), (Matcher, Policy)]()
      matchers foreach {
        case (matcher @ ElementMatcher(name), policy) =>
          selfMap += name -> (matcher, policy)
        case (matcher @ ElementAttributeValueMatcher(name, _, _, _), policy) =>
          selfMap += name -> (matcher, policy)
        case (matcher @ AnyElementChildOfMatcher(name), policy) =>
          parentMap += name -> (matcher, policy)
      }
      (selfMap.toMap, parentMap.toMap)
    }

    def apply(current: Policy, name: (String, String), attrs: Attributes, parentName: Option[(String, String)]): Policy = {

      def fromCurrentElement =
        selfElementIndex.get(name) collect {
          case (ElementMatcher(_), policy) =>
            policy
          case (ElementAttributeValueMatcher(_, attName, attValue, negate), policy) if negate ^ (attrs.getValue(attName) == attValue) =>
            policy
        }

      def fromParentElement =
        parentName flatMap parentElementIndex.get map {
          case (_, policy) => policy
        }

      fromCurrentElement orElse fromParentElement getOrElse current
    }
  }

  private val BaseScope = "oxf.xforms.whitespace.base"
  private val HTMLScope = "oxf.xforms.whitespace.html"

  private def defaultPolicy(scope: String, default: Policy) = {
    val name = Properties.instance.getPropertySet.getString(scope + ".default", default.entryName)
    Policy.withNameOption(name) getOrElse default
  }

  private def policyMatcherFromProperties(base: Option[PolicyMatcherImpl], scope: String): PolicyMatcherImpl = {

    val propertySet = Properties.instance.getPropertySet

    def whitespacePropertyDontAssociate(scope: String, policy: String) = (
      propertySet.getPropertyOpt(scope + '.' + policy)
      map (property => property.namespaces -> property.value.toString.trimAllToEmpty)
    )

    // NOTE: Not ideal if no whitespace property is present, there won't be any caching associated with properties.
    def whitespacePolicyAssociateIfPossible[T](scope: String, evaluate: => T): T = (
      propertySet.propertiesStartsWith(scope, matchWildcards = false).headOption
      map       propertySet.getPropertyOrThrow
      map       (_.associatedValue(_ => evaluate))
      getOrElse evaluate
    )

    def matchersForPolicy(policy: Policy): List[Matcher] =
      whitespacePropertyDontAssociate(scope, policy.entryName) map {
        case (ns, value) =>
          CSSSelectorParser.parseSelectors(value) collect {
            case Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some(prefix)), localname)), Nil), Nil) =>
              ElementMatcher(ns(prefix) -> localname)
            case Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some(prefix)), localname)), Nil),
                List((ChildCombinator, ElementWithFiltersSelector(Some(UniversalSelector(None)), Nil)))) =>
              AnyElementChildOfMatcher(ns(prefix) -> localname)
            case Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some(prefix)), localname)),
                List(NegationFilter(AttributeFilter(None, attrName, AttributePredicate.Equal(attrValue))))), Nil) =>
              ElementAttributeValueMatcher(ns(prefix) -> localname, attrName, attrValue, negate = true)
            case Selector(ElementWithFiltersSelector(Some(TypeSelector(Some(Some(prefix)), localname)),
                List(AttributeFilter(None, attrName, AttributePredicate.Equal(attrValue)))), Nil) =>
              ElementAttributeValueMatcher(ns(prefix) -> localname, attrName, attrValue, negate = false)
            case _ =>
              throw new IllegalArgumentException(s"Unrecognized whitespace policy: $value")
          }
      } getOrElse
        Nil

    def baseMatchers =
      base.toList flatMap (_.matchers)

    def newMatchers =
      Policy.valuesList flatMap (p => matchersForPolicy(p) map (_ -> p))

    def createPolicyMatcher =
      new PolicyMatcherImpl(baseMatchers ::: newMatchers)

    whitespacePolicyAssociateIfPossible(scope, createPolicyMatcher)
  }
}