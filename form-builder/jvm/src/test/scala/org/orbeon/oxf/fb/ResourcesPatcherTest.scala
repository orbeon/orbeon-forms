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

  val formMetadata = <_/>.toDocument

  describe("Patching scenarios") {
    it(s"must patch resources as expected") {

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

      ResourcesPatcher.transform(initial, formMetadata, AppForm("*", "*"))(props)

      assertXMLDocumentsIgnoreNamespacesInScope(initial, expected)
    }
  }
}
