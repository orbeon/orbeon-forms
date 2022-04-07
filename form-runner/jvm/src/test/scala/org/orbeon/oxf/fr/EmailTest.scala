/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.dom.io.XMLWriter
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport.readTinyTreeFromUrl
import org.scalatest.funspec.AnyFunSpecLike

import java.net.URI


class EmailTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private val FormWithEmailControls = URI.create("oxf:/org/orbeon/oxf/fr/form-with-email-controls.xhtml")

  describe("Email address extraction from form definition") {

    val formDoc = readTinyTreeFromUrl(FormWithEmailControls)

    val head     = formDoc.rootElement / XHHeadTest head
    val model    = head / XFModelTest head
    val instance = model descendant XFInstanceTest filter (_.id == "fr-form-instance") head
    val body     = formDoc.rootElement / XHBodyTest head

    val data =
      TransformerUtils.extractAsMutableDocument(instance child * head)

    def valuesForSearch(search: => Seq[ControlBindPathHoldersResources]) = {

      val searchResult = search

      val distinctPaths =
        searchResult map { case ControlBindPathHoldersResources(_, _, path, _, _) => path map (_.value) mkString "/" } distinct

      val values =
        searchResult.flatMap(_.holders).flatten.map(_.getStringValue).toList

      (values, distinctPaths)
    }

    describe("without section templates") {

      val expectedForClassName = List(
        ("fr-email-recipient"               , List("info+toplevel@orbeon.com")              , List("section-1/control-1")),
        ("fr-email-subject"                 , Nil                                           , Nil),
        ("fr-attachment"                    , List("attachment-13.bin", "attachment-14.bin"), List("section-1/control-13", "section-1/control-14")),
        ("fr-attachment fr-email-attachment", List("attachment-14.bin")                     , List("section-1/control-14"))
      )

      for ((classNames, expectedValues, expectedPath) <- expectedForClassName)
        it(s"must pass with $classNames") {

          val (actualValues, actualPaths) = valuesForSearch {
            frc.searchControlsTopLevelOnly(
              Some(data),
              FormRunner.hasAllClassesPredicate(classNames.splitTo[List]())
            )(new InDocFormRunnerDocContext(body))
          }

          assert(expectedValues === actualValues)
          assert(expectedPath   === actualPaths)
        }
    }

    describe("within section templates") {

      val expectedForClassName = List(
        ("fr-email-recipient"               , List("info+0@orbeon.com", "info+1@orbeon.com", "info+2@orbeon.com"), List("section-3/control-1", "section-3/section-3/grid-4/grid-4-iteration/control-6")),
        ("fr-email-subject"                 , List("Abc", "Def", "Ghi")                                          , List("section-3/control-8", "section-3/section-3/grid-4/grid-4-iteration/control-7")),
        ("fr-attachment"                    , List("attachment-10.bin", "attachment-11.bin")                     , List("section-3/control-10","section-3/control-11")),
        ("fr-attachment fr-email-attachment", List("attachment-11.bin")                                          , List("section-3/control-11"))
      )

      for ((classNames, expectedValues, expectedPath) <- expectedForClassName)
        it(s"must pass with $classNames") {

          val (actualValues, actualPaths) = valuesForSearch {
            frc.searchControlsUnderSectionTemplates(
              head,
              Some(data),
              FormRunner.hasAllClassesPredicate(classNames.splitTo[List]())
            )(new InDocFormRunnerDocContext(body))
          }

          assert(expectedValues === actualValues)
          assert(expectedPath   === actualPaths)
        }
    }
  }

  describe("Email metadata parsing") {

    val MetadataCurrent = NodeConversions.elemToNodeInfo(
      <email>
        <templates>
          <template name="default" xml:lang="en">
            <form-fields>
              <recipient name="control-1"/>
              <cc name="control-2"/>
              <bcc name="control-3"/>
              <sender name="control-4"/>
              <attachment name="control-14"/>
            </form-fields>
            <subject>My subject {{$my-param-1}}</subject>
            <body mediatype="text/html">My body: {{$my-param-2}}</body>
          </template>
          <template name="default" xml:lang="fr">
            <form-fields>
              <recipient name="control-1"/>
              <cc name="control-2"/>
              <bcc name="control-3"/>
              <sender name="control-4"/>
              <attachment name="control-14"/>
            </form-fields>
            <subject>Mon sujet {{$my-param-1}}</subject>
            <body mediatype="text/html">Mon message: {{$my-param-2}}</body>
          </template>
        </templates>
        <parameters>
          <fr:param type="ExpressionParam" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
              <fr:name>my-param-1</fr:name>
              <fr:expr>42</fr:expr>
          </fr:param>
          <fr:param type="ControlValueParam" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
              <fr:name>my-param-2</fr:name>
              <fr:controlName>43</fr:controlName>
          </fr:param>
        </parameters>
      </email>
    )

    val MetadataLegacy2021 = NodeConversions.elemToNodeInfo(
      <email xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <subject>
            <template xml:lang="en">My subject {{$my-param}}</template>
            <template xml:lang="fr">Mon sujet {{$my-param}}</template>
            <fr:param type="ExpressionParam">
                <fr:name>my-param</fr:name>
                <fr:expr>42</fr:expr>
            </fr:param>
        </subject>
        <body>
            <template xml:lang="en" mediatype="text/html">My body: {{$my-param}}</template>
            <template xml:lang="fr" mediatype="text/html">Mon message: {{$my-param}}</template>
            <fr:param type="ControlValueParam">
                <fr:name>my-param</fr:name>
                <fr:controlName>43</fr:controlName>
            </fr:param>
        </body>
      </email>
    )

    val formDoc  = readTinyTreeFromUrl(FormWithEmailControls)
    val formBody = formDoc.rootElement / XHBodyTest head

    describe("parse both old and new format") {

      assert(! FormRunnerEmail.isLegacy2021EmailMetadata(MetadataCurrent   ))
      assert(  FormRunnerEmail.isLegacy2021EmailMetadata(MetadataLegacy2021))

      assert(
        FormRunnerEmail.parseEmailMetadata(MetadataCurrent, null) ===
        FormRunnerEmail.parseEmailMetadata(MetadataLegacy2021, formBody)
      )
    }

    describe("serialize metadata") {

      def prettyPrint(nodeInfo: NodeInfo): String =
        TransformerUtils.tinyTreeToOrbeonDom(nodeInfo).getRootElement.serializeToString(XMLWriter.PrettyFormat)

      val originalMetadata            = MetadataCurrent.pipe(prettyPrint)
      val parsedAndSerializedMetadata = MetadataCurrent
        .pipe(FormRunnerEmail.parseEmailMetadata(_, formBody))
        .pipe(FormRunnerEmail.serializeEmailMetadata)
        .pipe(NodeConversions.elemToNodeInfo)
        .pipe(prettyPrint)

      assert(parsedAndSerializedMetadata === originalMetadata)
    }

  }
}
