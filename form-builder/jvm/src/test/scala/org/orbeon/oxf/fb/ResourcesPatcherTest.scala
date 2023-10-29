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
package org.orbeon.oxf.fb

import org.orbeon.io.IOUtils._
import org.orbeon.oxf.fr.{AppForm, ResourcesPatcher}
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.properties.{PropertySet, PropertyStore}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike


// NOTE: Test this in the `form-builder` module as we depend on Form Builder's `resources.xml`.
class ResourcesPatcherTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  val emptyFormMetadata = <_/>.toDocument

  describe("Patching scenarios") {
    it(s"must patch resources as expected (properties only)") {

      val propertySet = {
        val properties =
          <properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <property as="xs:string"  name="oxf.fr.resource.*.*.en.detail.buttons.existing" value="Existing"/>
            <property as="xs:string"  name="oxf.fr.resource.*.*.fr.detail.buttons.existing" value="Existant"/>
            <property as="xs:string"  name="oxf.fr.resource.*.*.de.detail.buttons.existing" value="Vorhanden"/>
            <property as="xs:string"  name="oxf.fr.resource.*.*.en.detail.labels.missing"   value="Missing"/>
            <property as="xs:string"  name="oxf.fr.resource.*.*.fr.detail.labels.missing"   value="Manquant"/>
            <property as="xs:string"  name="oxf.fr.resource.*.*.de.detail.labels.missing"   value="Vermisst"/>
            <property as="xs:string"  name="oxf.fr.resource.*.*.*.detail.buttons.acme"      value="Acme Existing"/>
            <property as="xs:string"  name="oxf.fr.resource.*.*.*.detail.labels.acme"       value="Acme Missing"/>
          </properties>.toDocument

        PropertyStore.parse(properties).getGlobalPropertySet
      }

      def newDoc =
        <resources>
          <resource xml:lang="en">
            <buttons>
              <acme>OVERRIDE ME</acme>
              <existing>OVERRIDE ME</existing>
            </buttons>
          </resource>
          <resource xml:lang="fr">
            <buttons>
              <acme>OVERRIDE ME</acme>
              <existing>OVERRIDE ME</existing>
            </buttons>
          </resource>
        </resources>.toDocument

      val expected =
        <resources>
          <resource xml:lang="en">
            <buttons>
              <acme>Acme Existing</acme>
              <existing>Existing</existing>
            </buttons>
            <detail>
              <labels>
                <acme>Acme Missing</acme>
                <missing>Missing</missing>
              </labels>
            </detail>
          </resource>
          <resource xml:lang="fr">
            <buttons>
              <acme>Acme Existing</acme>
              <existing>Existant</existing>
            </buttons>
            <detail>
              <labels>
                <acme>Acme Missing</acme>
                <missing>Manquant</missing>
              </labels>
            </detail>
          </resource>
          <resource xml:lang="de">
            <buttons>
              <acme>OVERRIDE ME</acme>
              <existing>Vorhanden</existing>
            </buttons>
            <detail>
              <labels>
                <missing>Vermisst</missing>
              </labels>
            </detail>
          </resource>
        </resources>.toDocument

      val initial = newDoc

      ResourcesPatcher.transform(initial, emptyFormMetadata, AppForm("*", "*"))(propertySet)

      assertXMLDocumentsIgnoreNamespacesInScope(expected, initial)
    }

    it(s"must patch resources as expected (properties and metadata)") {

      val propertySet = {
        val properties =
          <properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <property as="xs:string" name="oxf.fr.resource.*.*.*.detail.messages.prop-wildcard-meta-absent" value="prop-wildcard-meta-absent (property)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.*.detail.messages.prop-wildcard-meta-wildcard" value="prop-wildcard-meta-wildcard (property)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.*.detail.messages.prop-wildcard-meta-en" value="prop-wildcard-meta-en (property)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.*.detail.messages.prop-wildcard-meta-wildcard-en" value="prop-wildcard-meta-wildcard-en (property)"/>

            <property as="xs:string" name="oxf.fr.resource.*.*.en.detail.messages.prop-en-meta-absent" value="prop-en-meta-absent (property)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.en.detail.messages.prop-en-meta-wildcard" value="prop-en-meta-wildcard (property)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.en.detail.messages.prop-en-meta-en" value="prop-en-meta-en (property)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.en.detail.messages.prop-en-meta-wildcard-en" value="prop-en-meta-wildcard-en (property)"/>

            <property as="xs:string" name="oxf.fr.resource.*.*.*.detail.messages.prop-wildcard-en-meta-absent" value="prop-wildcard-en-meta-absent (property, *)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.*.detail.messages.prop-wildcard-en-meta-wildcard" value="prop-wildcard-en-meta-wildcard (property, *)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.*.detail.messages.prop-wildcard-en-meta-en" value="prop-wildcard-en-meta-en (property, *)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.*.detail.messages.prop-wildcard-en-meta-wildcard-en" value="prop-wildcard-en-meta-wildcard-en (property, *)"/>

            <property as="xs:string" name="oxf.fr.resource.*.*.en.detail.messages.prop-wildcard-en-meta-absent" value="prop-wildcard-en-meta-absent (property, en)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.en.detail.messages.prop-wildcard-en-meta-wildcard" value="prop-wildcard-en-meta-wildcard (property, en)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.en.detail.messages.prop-wildcard-en-meta-en" value="prop-wildcard-en-meta-en (property, en)"/>
            <property as="xs:string" name="oxf.fr.resource.*.*.en.detail.messages.prop-wildcard-en-meta-wildcard-en" value="prop-wildcard-en-meta-wildcard-en (property, en)"/>
          </properties>.toDocument

        PropertyStore.parse(properties).getGlobalPropertySet
      }

      val formMetadata =
        <resources>
          <resource xml:lang="*">
            <messages>
              <message name="prop-absent-meta-wildcard">prop-absent-meta-wildcard (metadata)</message>
              <message name="prop-absent-meta-wildcard-en">prop-absent-meta-wildcard-en (metadata, *)</message>
              <message name="prop-wildcard-meta-wildcard">prop-wildcard-meta-wildcard (metadata)</message>
              <message name="prop-wildcard-meta-wildcard-en">prop-wildcard-meta-wildcard-en (metadata, *)</message>
              <message name="prop-en-meta-wildcard">prop-en-meta-wildcard (metadata)</message>
              <message name="prop-en-meta-wildcard-en">prop-en-meta-wildcard-en (metadata, *)</message>
              <message name="prop-wildcard-en-meta-wildcard">prop-wildcard-en-meta-wildcard (metadata)</message>
              <message name="prop-wildcard-en-meta-wildcard-en">prop-wildcard-en-meta-wildcard-en (metadata, *)</message>
            </messages>
          </resource>
          <resource xml:lang="en">
            <messages>
              <message name="prop-absent-meta-en">prop-absent-meta-en (metadata)</message>
              <message name="prop-absent-meta-wildcard-en">prop-absent-meta-wildcard-en (metadata, en)</message>
              <message name="prop-wildcard-meta-en">prop-wildcard-meta-en (metadata)</message>
              <message name="prop-wildcard-meta-wildcard-en">prop-wildcard-meta-wildcard-en (metadata, en)</message>
              <message name="prop-en-meta-en">prop-en-meta-en (metadata)</message>
              <message name="prop-en-meta-wildcard-en">prop-en-meta-wildcard-en (metadata, en)</message>
              <message name="prop-wildcard-en-meta-en">prop-wildcard-en-meta-en (metadata)</message>
              <message name="prop-wildcard-en-meta-wildcard-en">prop-wildcard-en-meta-wildcard-en (metadata, en)</message>
            </messages>
          </resource>
        </resources>.toDocument

      def newDoc =
        <resources>
          <resource xml:lang="en">
            <detail>
              <messages>
                <prop-absent-meta-absent>OVERRIDE ME</prop-absent-meta-absent>
                <prop-absent-meta-wildcard>OVERRIDE ME</prop-absent-meta-wildcard>
                <prop-absent-meta-en>OVERRIDE ME</prop-absent-meta-en>
                <prop-absent-meta-wildcard-en>OVERRIDE ME</prop-absent-meta-wildcard-en>
                <prop-wildcard-meta-absent>OVERRIDE ME</prop-wildcard-meta-absent>
                <prop-wildcard-meta-wildcard>OVERRIDE ME</prop-wildcard-meta-wildcard>
                <prop-wildcard-meta-en>OVERRIDE ME</prop-wildcard-meta-en>
                <prop-wildcard-meta-wildcard-en>OVERRIDE ME</prop-wildcard-meta-wildcard-en>
                <prop-en-meta-absent>OVERRIDE ME</prop-en-meta-absent>
                <prop-en-meta-wildcard>OVERRIDE ME</prop-en-meta-wildcard>
                <prop-en-meta-en>OVERRIDE ME</prop-en-meta-en>
                <prop-en-meta-wildcard-en>OVERRIDE ME</prop-en-meta-wildcard-en>
                <prop-wildcard-en-meta-absent>OVERRIDE ME</prop-wildcard-en-meta-absent>
                <prop-wildcard-en-meta-wildcard>OVERRIDE ME</prop-wildcard-en-meta-wildcard>
                <prop-wildcard-en-meta-en>OVERRIDE ME</prop-wildcard-en-meta-en>
                <prop-wildcard-en-meta-wildcard-en>OVERRIDE ME</prop-wildcard-en-meta-wildcard-en>
              </messages>
            </detail>
          </resource>
          <resource xml:lang="fr">
            <detail>
              <messages>
                <prop-absent-meta-absent>OVERRIDE ME</prop-absent-meta-absent>
                <prop-absent-meta-wildcard>OVERRIDE ME</prop-absent-meta-wildcard>
                <prop-absent-meta-en>OVERRIDE ME</prop-absent-meta-en>
                <prop-absent-meta-wildcard-en>OVERRIDE ME</prop-absent-meta-wildcard-en>
                <prop-wildcard-meta-absent>OVERRIDE ME</prop-wildcard-meta-absent>
                <prop-wildcard-meta-wildcard>OVERRIDE ME</prop-wildcard-meta-wildcard>
                <prop-wildcard-meta-en>OVERRIDE ME</prop-wildcard-meta-en>
                <prop-wildcard-meta-wildcard-en>OVERRIDE ME</prop-wildcard-meta-wildcard-en>
                <prop-en-meta-absent>OVERRIDE ME</prop-en-meta-absent>
                <prop-en-meta-wildcard>OVERRIDE ME</prop-en-meta-wildcard>
                <prop-en-meta-en>OVERRIDE ME</prop-en-meta-en>
                <prop-en-meta-wildcard-en>OVERRIDE ME</prop-en-meta-wildcard-en>
                <prop-wildcard-en-meta-absent>OVERRIDE ME</prop-wildcard-en-meta-absent>
                <prop-wildcard-en-meta-wildcard>OVERRIDE ME</prop-wildcard-en-meta-wildcard>
                <prop-wildcard-en-meta-en>OVERRIDE ME</prop-wildcard-en-meta-en>
                <prop-wildcard-en-meta-wildcard-en>OVERRIDE ME</prop-wildcard-en-meta-wildcard-en>
              </messages>
            </detail>
          </resource>
        </resources>.toDocument

      val expected =
        <resources>
          <resource xml:lang="en">
            <detail>
              <messages>
                <prop-absent-meta-absent>OVERRIDE ME</prop-absent-meta-absent>
                <prop-absent-meta-wildcard>prop-absent-meta-wildcard (metadata)</prop-absent-meta-wildcard>
                <prop-absent-meta-en>prop-absent-meta-en (metadata)</prop-absent-meta-en>
                <prop-absent-meta-wildcard-en>prop-absent-meta-wildcard-en (metadata, en)</prop-absent-meta-wildcard-en>
                <prop-wildcard-meta-absent>prop-wildcard-meta-absent (property)</prop-wildcard-meta-absent>
                <prop-wildcard-meta-wildcard>prop-wildcard-meta-wildcard (metadata)</prop-wildcard-meta-wildcard>
                <prop-wildcard-meta-en>prop-wildcard-meta-en (metadata)</prop-wildcard-meta-en>
                <prop-wildcard-meta-wildcard-en>prop-wildcard-meta-wildcard-en (metadata, en)</prop-wildcard-meta-wildcard-en>
                <prop-en-meta-absent>prop-en-meta-absent (property)</prop-en-meta-absent>
                <prop-en-meta-wildcard>prop-en-meta-wildcard (metadata)</prop-en-meta-wildcard>
                <prop-en-meta-en>prop-en-meta-en (metadata)</prop-en-meta-en>
                <prop-en-meta-wildcard-en>prop-en-meta-wildcard-en (metadata, en)</prop-en-meta-wildcard-en>
                <prop-wildcard-en-meta-absent>prop-wildcard-en-meta-absent (property, en)</prop-wildcard-en-meta-absent>
                <prop-wildcard-en-meta-wildcard>prop-wildcard-en-meta-wildcard (metadata)</prop-wildcard-en-meta-wildcard>
                <prop-wildcard-en-meta-en>prop-wildcard-en-meta-en (metadata)</prop-wildcard-en-meta-en>
                <prop-wildcard-en-meta-wildcard-en>prop-wildcard-en-meta-wildcard-en (metadata, en)</prop-wildcard-en-meta-wildcard-en>
              </messages>
            </detail>
          </resource>
          <resource xml:lang="fr">
            <detail>
              <messages>
                <prop-absent-meta-absent>OVERRIDE ME</prop-absent-meta-absent>
                <prop-absent-meta-wildcard>prop-absent-meta-wildcard (metadata)</prop-absent-meta-wildcard>
                <prop-absent-meta-en>OVERRIDE ME</prop-absent-meta-en>
                <prop-absent-meta-wildcard-en>prop-absent-meta-wildcard-en (metadata, *)</prop-absent-meta-wildcard-en>
                <prop-wildcard-meta-absent>prop-wildcard-meta-absent (property)</prop-wildcard-meta-absent>
                <prop-wildcard-meta-wildcard>prop-wildcard-meta-wildcard (metadata)</prop-wildcard-meta-wildcard>
                <prop-wildcard-meta-en>prop-wildcard-meta-en (property)</prop-wildcard-meta-en>
                <prop-wildcard-meta-wildcard-en>prop-wildcard-meta-wildcard-en (metadata, *)</prop-wildcard-meta-wildcard-en>
                <prop-en-meta-absent>OVERRIDE ME</prop-en-meta-absent>
                <prop-en-meta-wildcard>prop-en-meta-wildcard (metadata)</prop-en-meta-wildcard>
                <prop-en-meta-en>OVERRIDE ME</prop-en-meta-en>
                <prop-en-meta-wildcard-en>prop-en-meta-wildcard-en (metadata, *)</prop-en-meta-wildcard-en>
                <prop-wildcard-en-meta-absent>prop-wildcard-en-meta-absent (property, *)</prop-wildcard-en-meta-absent>
                <prop-wildcard-en-meta-wildcard>prop-wildcard-en-meta-wildcard (metadata)</prop-wildcard-en-meta-wildcard>
                <prop-wildcard-en-meta-en>prop-wildcard-en-meta-en (property, *)</prop-wildcard-en-meta-en>
                <prop-wildcard-en-meta-wildcard-en>prop-wildcard-en-meta-wildcard-en (metadata, *)</prop-wildcard-en-meta-wildcard-en>
              </messages>
            </detail>
          </resource>
        </resources>.toDocument

      val initial = newDoc

      ResourcesPatcher.transform(initial, formMetadata, AppForm("*", "*"))(propertySet)

      assertXMLDocumentsIgnoreNamespacesInScope(expected, initial)
    }
  }

  describe("Resources consistency") {
    it(s"must ensure that resources in multiple languages are consistent") {

      def hasLang(lang: String)(e: NodeInfo) = (e attValue "*:lang") == lang

      val urls = Seq(
        "oxf:/apps/fr/i18n/resources.xml",
        "oxf:/forms/orbeon/builder/form/resources.xml"
      )

      // - allow "item" and "choices" because we use this for itemsets
      // - allow "type" because it's used for the FB list of types
      val AllowedDuplicateNames = Set("item", "choices", "type")

      for (url <- urls) {

        val doc =
          useAndClose(URLFactory.createURL(url).openStream()) { is =>
            TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, null, false, false)
          }

        // Baseline is "en"
        val englishResource = doc / * / "resource" filter hasLang("en") head

        // Recursively compare element presence and order. All other nodes, including text and attribute nodes, are
        // ignored.
        def compareElements(left: NodeInfo, right: NodeInfo, lang: String): Boolean = (left, right) match {
          case (left: DocumentInfo, right: DocumentInfo) =>
            compareElements(left.rootElement, right.rootElement, lang)
          case (left: NodeInfo, right: NodeInfo) if left.isElement =>

            def commonMessageSuffix = s" (url=$url and lang=$lang)"

            assert(left.name === right.name, s"different names$commonMessageSuffix")

            // Ignore children of "div" because it can contain XHTML which is different per language
            left.name == right.name && (left.name == "div" || {
              val leftChildren  = left  / *
              val rightChildren = right / *

              val duplicates = (leftChildren map (_.name)).toList.findDuplicates filterNot AllowedDuplicateNames

              assert(
                duplicates.isEmpty,
                s"duplicate names under `${left.name}`: ${duplicates mkString ", "}$commonMessageSuffix"
              )

              def elemNames(elems: Seq[NodeInfo]) =
                elems map (_.name) mkString ("[", ", ", "]")

              def errorSuffix =
                s"$commonMessageSuffix (left=${elemNames(leftChildren)}, right=${elemNames(rightChildren)}"

              assert(leftChildren.size === rightChildren.size, s"different sizes$errorSuffix")

              leftChildren.size == rightChildren.size && {
                (leftChildren zip rightChildren) forall {
                  case (l, r) => compareElements(l, r, lang)
                }
              }
            })
          case _ =>
            // Ignore all other nodes
            true
        }

        for {
          resource <- doc / * / "resource" filterNot hasLang("en")
          lang     = resource attValue "*:lang"
        } locally {
          assert(compareElements(englishResource, resource, lang))
        }
      }
    }
  }

  describe("Untranslated resources") {
    it(s"must bracket as needed") {

      def newDoc =
        <resources>
          <resource xml:lang="en">
            <authentication>
              <menu>
                  <logged-in-as>Logged in as {0}</logged-in-as>
                  <logout>Logout</logout>
                  <login>Login</login>
                  <register>Register</register>
              </menu>
              <login>
                  <title>Form Runner Login</title>
                  <username>Username</username>
                  <password>Password</password>
                  <login>Login</login>
              </login>
          </authentication>
          </resource>
          <resource xml:lang="fr">
            <authentication>
              <menu>
                  <logged-in-as todo="true">[Logged in as {0}]</logged-in-as>
                  <logout todo="true">[Logout]</logout>
                  <login todo="true">[Login]</login>
                  <register todo="true">[Register]</register>
              </menu>
              <login>
                  <title todo="true">Form Runner Login</title>
                  <username todo="true">Username</username>
                  <password todo="true">Password</password>
                  <login todo="true">Login</login>
              </login>
            </authentication>
          </resource>
        </resources>.toDocument

      val expected =
        <resources>
          <resource xml:lang="en">
            <authentication>
              <menu>
                  <logged-in-as>Logged in as {0}</logged-in-as>
                  <logout>Logout</logout>
                  <login>Login</login>
                  <register>Register</register>
              </menu>
              <login>
                  <title>Form Runner Login</title>
                  <username>Username</username>
                  <password>Password</password>
                  <login>Login</login>
              </login>
          </authentication>
          </resource>
          <resource xml:lang="fr">
            <authentication>
              <menu>
                  <logged-in-as todo="true">[Logged in as {0}]</logged-in-as>
                  <logout todo="true">[Logout]</logout>
                  <login todo="true">[Login]</login>
                  <register todo="true">[Register]</register>
              </menu>
              <login>
                  <title>[Form Runner Login]</title>
                  <username>[Username]</username>
                  <password>Mot de passe</password>
                  <login>[Login]</login>
              </login>
            </authentication>
          </resource>
        </resources>.toDocument

      val initial = newDoc

      val props =
        PropertySet(
          List(
            PropertyParams(Map.empty, "oxf.fr.resource.*.*.fr.authentication.login.password", XS_STRING_QNAME, "Mot de passe")
          )
        )

      ResourcesPatcher.transform(initial, emptyFormMetadata, AppForm("*", "*"))(props)

      assertXMLDocumentsIgnoreNamespacesInScope(initial, expected)
    }
  }
}
