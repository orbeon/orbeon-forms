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
package org.orbeon.oxf.controller

import org.scalatest.funspec.AnyFunSpecLike

import java.util.regex.Pattern


class PageFlowFilesRegexTest extends AnyFunSpecLike {

  private lazy val filesRegexString: String = {
    val is =
      Option(getClass.getResourceAsStream("/page-flow.xml"))
        .getOrElse(throw new IllegalStateException("Resource `/page-flow.xml` not found on classpath"))
    try {
      val xml = scala.xml.XML.load(is)
      (xml \ "_")
        .find(_.label == "files")
        .flatMap(_.attribute("path"))
        .map(_.text)
        .getOrElse(throw new IllegalStateException("No `@path` attribute found on `<files>` in `page-flow.xml`"))
    } finally {
      is.close()
    }
  }

  private lazy val filesPattern: Pattern =
    Pattern.compile(filesRegexString)

  private def matches(path: String): Boolean =
    filesPattern.matcher(path).matches()

  describe("Page flow `<files>` regex from `page-flow.xml`") {

    it("is present in `page-flow.xml`") {
      assert(filesRegexString.nonEmpty)
    }

    describe("matches valid static asset paths") {
      val validPaths = Seq(
        // App assets
        "/fr/style/form-runner.css",
        "/fr/style/images/my.png",
        "/fr/style/images/my.jpg",
        "/fr/style/images/my.gif",
        "/fr/scripts/form-runner.js",
        "/fr/scripts/form-runner.js.map",
        "/fr/favicon.ico",
        "/fr/font/font.woff",
        "/fr/font/font.woff2",
        "/fr/font/font.ttf",
        "/fr/font/font.eot",
        "/fr/images/icon.svg",
        "/fr/schema.xsd",
        "/fr/readme.txt",
        "/fr/doc.pdf",
        "/fr/help.html",

        // Other app assets
        "/my-app/style/app.css",
        "/my-app/images/logo.png",
        "/my-app/scripts/custom.js",

        // Root-level assets
        "/favicon.ico",
        "/style.css",
        "/script.js",
        "/logo.svg",

        // Config theme assets (allowed under config/theme/)
        "/fr/config/theme/theme.css",
        "/fr/config/theme/sub/theme.png",
        "/my-app/config/theme/style.css",
        "/my-app/config/theme/sub/image.svg"
      )

      for (path <- validPaths)
        it(s"matches `$path`") {
          assert(matches(path))
        }
    }

    describe("does not match service paths") {
      val servicePaths = Seq(
        "/fr/service/persistence/crud/app/form/data/123.xml",
        "/fr/service/something.html",
        "/fr/service/custom.json",
        "/fr/service/test.txt",
        "/fr/service/doc.pdf",
        "/fr/service/script.js",
        "/my-app/service/api.json",
        "/my-app/service/file.css",
        "/my-app/service/deep/path/file.png"
      )

      for (path <- servicePaths)
        it(s"does not match `$path`") {
          assert(! matches(path))
        }
    }

    describe("does not match configuration assets outside `theme/`") {
      val configPaths = Seq(
        "/fr/config/form-runner-properties.xml",
        "/fr/config/properties.xml",
        "/fr/config/properties-local.xml",
        "/fr/config/properties-internal.xml",
        "/fr/config/properties-dev.xml",
        "/fr/config/properties-prod.xml",
        "/fr/config/test.json",
        "/fr/config/secret.txt",
        "/fr/config/schema.xsd",
        "/fr/config/sub/nested.txt",
        "/my-app/config/secret.txt",
        "/my-app/config/not-theme/file.css",
        "/my-app/config/theme-other/file.css",
        "/my-app/config/theme-other/image.png"
      )

      for (path <- configPaths)
        it(s"does not match `$path`") {
          assert(! matches(path))
        }
    }

    describe("does not match disallowed extensions or non-file paths") {
      val invalidPaths = Seq(
        // Disallowed extensions (even in public asset directories)
        "/fr/style/something.xml",
        "/fr/style/something.json",
        "/fr/style/something.jsp",
        "/fr/style/something.class",
        "/fr/style/something.properties",
        "/fr/style/something.exe",
        "/fr/style/something.sh",

        // Missing extension `/` directories
        "/fr/style/something",
        "/fr/service/",
        "/fr/config/",
        "/fr/config/theme/",
        ""
      )

      for (path <- invalidPaths)
        it(s"does not match `$path`") {
          assert(! matches(path))
        }
    }
  }
}
