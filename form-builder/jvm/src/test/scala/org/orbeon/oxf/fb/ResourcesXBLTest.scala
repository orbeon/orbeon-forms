/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

import java.io.{File, FileInputStream}


class ResourcesXBLTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  import Private.*

  describe("Localization of XBL files") {
    it("must provide a `display-name` for every language we support in Form Builder") {
      checkAllLangs(fbResourceLangs, "<display-name>", fbDisplayNameLangs)
    }
    it("must provide a `<resource>` for every language we support in Form Builder") {
      checkAllLangs(fbResourceLangs, "<resource>", fbControlDetailsFormResourcesLangs)
    }
    it("must provide a `<resource>` for every language we support in Form Runner") {
      checkAllLangs(frResourceLangs, "<resource>", frOrbeonResourcesLangs)
    }
  }

  private object Private {

    private val xblFiles: List[File] =
      collectFiles(new File("."),_.getName.endsWith(".xbl")).sortBy(_.getPath)

    val fbResourceLangs: Set[String] =
      langsFromResourceFiles(
        new File("form-builder/jvm/src/main/resources/forms/orbeon/builder/form"),
        "fb-resources-"
      )

    val frResourceLangs: Set[String] =
      langsFromResourceFiles(
        new File("form-runner/jvm/src/main/resources/apps/fr/i18n"),
        "fr-resources-"
      )

    def checkAllLangs(expectedLangs: Set[String], missingElement: String, langsFor: DocumentInfo => Set[String]): Unit = {
      val problems =
        for {
          xblFile <- xblFiles
          langs   = langsFor(readXbl(xblFile))
          if langs.nonEmpty
          missing = expectedLangs -- langs
          if missing.nonEmpty
        } yield
          s"${relativePath(xblFile)}: missing `$missingElement` for ${langsString(missing)}"
      assert(problems.isEmpty, problems.mkString("\n", "\n", "\n"))
    }

    private def readXbl(file: File): DocumentInfo =
      useAndClose(new FileInputStream(file)) { is =>
        TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, file.toURI.toString, false, false)
      }

    def fbDisplayNameLangs(doc: DocumentInfo): Set[String] =
      doc.descendant("display-name").attValue("*:lang").filter(_.nonEmpty).toSet

    def frOrbeonResourcesLangs(doc: DocumentInfo): Set[String] =
      doc.descendant("instance").find(_.attValue("id") == "orbeon-resources")
        .map(_.descendant("resource").attValue("*:lang").filter(_.nonEmpty).toSet)
        .getOrElse(Set.empty)

    def fbControlDetailsFormResourcesLangs(doc: DocumentInfo): Set[String] =
      (for {
        controlDetails <- doc.descendant("control-details").headOption
        instance       <- controlDetails.descendant("instance").find(_.attValue("id") == "fr-form-resources")
      } yield
        instance.descendant("resource").attValue("*:lang").filter(_.nonEmpty).toSet)
        .getOrElse(Set.empty)

    private def relativePath(file: File): String =
      new File(".").toURI.relativize(file.toURI).getPath

    private def langsString(langs: Set[String]): String =
      langs.toList.sorted.mkString(", ")

    private def collectFiles(dir: File,p: File => Boolean): List[File] =
      Option(dir.listFiles).toList.flatten.flatMap {
        case f if f.isDirectory && f.getName != "target" => collectFiles(f,p)
        case f if f.isFile && p(f)                       => List(f)
        case _                                           => Nil
      }

    private def langsFromResourceFiles(dir: File, prefix: String): Set[String] =
      Option(dir.listFiles).toList.flatten.collect {
        case f if f.getName.startsWith(prefix) && f.getName.endsWith(".xml") =>
          f.getName.stripPrefix(prefix).stripSuffix(".xml")
      }.toSet
  }
}
