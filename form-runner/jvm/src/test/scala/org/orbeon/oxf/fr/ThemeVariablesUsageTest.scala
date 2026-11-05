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
package org.orbeon.oxf.fr

import org.scalatest.funspec.AnyFunSpec

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*


// Every `--orbeon-*` variable read by a stylesheet must be declared by each built-in theme, and never carry a fallback
// value: a fallback hides a missing declaration (the fallback is what shows, in both color schemes), and the themes are
// the single place where the look is defined. The LESS sources are not on the classpath, so this test reads them from
// the source tree, located by walking up from the working directory to the repository root (`build.sbt`).
class ThemeVariablesUsageTest extends AnyFunSpec {

  private val BuiltInThemes = List("2025", "2026")
  private val ThemesDir     = "form-runner/jvm/src/main/assets/apps/fr/themes"
  private val AssetsDirs    = List(
    "form-runner/jvm/src/main/assets",
    "form-builder/jvm/src/main/assets",
    "xforms-web/src/main/assets",
    "src/main/assets"
  )
  private val ExcludedDirs  = Set("themes", "theme-explorer")

  // Set at run time by JavaScript, not by a theme, so a fallback is the real default
  private val RuntimeVariables = Set("--orbeon-card-columns")

  private val VariableUse         = """var\(\s*(--orbeon-[a-z0-9-]+)\s*(,)?""".r
  private val VariableDeclaration = """(?m)^\s*(--orbeon-[a-z0-9-]+)\s*:""".r

  @tailrec
  private def findRepoRoot(dir: File): Option[File] =
    if (dir == null)
      None
    else if (new File(dir, "build.sbt").isFile)
      Some(dir)
    else
      findRepoRoot(dir.getParentFile)

  private def stripComments(text: String): String =
    text
      .replaceAll("""(?s)/\*.*?\*/""", "")
      .replaceAll("""(?m)//.*$""", "")

  private def read(file: File): String =
    stripComments(new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8))

  private def lessFiles(dir: File): List[File] =
    if (! dir.isDirectory)
      Nil
    else
      Files.walk(dir.toPath).iterator.asScala
        .map(_.toFile)
        .filter(f => f.isFile && f.getName.endsWith(".less"))
        .filterNot(f => f.getPath.split(File.separatorChar).exists(ExcludedDirs))
        .toList
        .sortBy(_.getPath)

  describe("Theme variables read by the stylesheets") {

    val repoRoot = findRepoRoot(new File(".").getAbsoluteFile).getOrElse(fail("repository root not found"))

    val sources = AssetsDirs.flatMap(d => lessFiles(new File(repoRoot, d)))
    assert(sources.nonEmpty, "no LESS source found")

    val declaredByTheme: Map[String, Set[String]] =
      BuiltInThemes.map { theme =>
        theme -> VariableDeclaration.findAllMatchIn(read(new File(repoRoot, s"$ThemesDir/$theme/theme.less"))).map(_.group(1)).toSet
      }.toMap

    // Variables a stylesheet declares for its own use, for example the landing page's column counts
    val declaredBySources: Set[String] =
      sources.flatMap(f => VariableDeclaration.findAllMatchIn(read(f)).map(_.group(1))).toSet

    val uses: List[(File, String, Boolean)] =
      for {
        file <- sources
        m    <- VariableUse.findAllMatchIn(read(file)).toList
      } yield
        (file, m.group(1), m.group(2) != null)

    it("must not use fallback values") {
      val withFallback =
        uses.collect { case (file, name, true) if ! RuntimeVariables(name) => s"${repoRoot.toPath.relativize(file.toPath)}: $name" }
      assert(withFallback.isEmpty, s"\nfallback values found, declare the variable in both themes instead:\n${withFallback.mkString("\n")}")
    }

    for (theme <- BuiltInThemes)
      it(s"must only read variables declared by the $theme theme") {
        val undeclared =
          uses.collect {
            case (file, name, _) if ! declaredByTheme(theme)(name) && ! declaredBySources(name) && ! RuntimeVariables(name) =>
              s"${repoRoot.toPath.relativize(file.toPath)}: $name"
          }.distinct
        assert(undeclared.isEmpty, s"\nvariables read but not declared by the $theme theme:\n${undeclared.mkString("\n")}")
      }
  }
}
