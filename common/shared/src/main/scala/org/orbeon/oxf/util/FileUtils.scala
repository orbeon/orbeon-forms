/**
 * Copyright (C) 2025 Orbeon, Inc.
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


object FileUtils {

  def baseNameAndExtension(filename: String): (String, Option[String]) =
    filename.lastIndexOf(".") match {
      case -1    => (filename,                     None)
      case index => (filename.substring(0, index), Some(filename.substring(index + 1)))
    }

  def sanitizedFilename(filename: String): String =
    filename
      .replaceAll("[<>:\"/\\\\|?*]", "_")                         // Invalid characters
      .replaceAll("[\u0000-\u001f\u007f-\u009f]", "_")            // Control characters
      .replaceAll("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$", "_$1") // Windows reserved names
      .replaceAll("\\.$", "")                                     // Trailing dots
      .trim

  // Add "-1", "-2", etc. suffixes to the base name to make the filenames unique if needed
  def uniqueFilenames(filenames: List[String]): List[String] = {

    val allFilenames = filenames.toSet
    val duplicates   = filenames.groupBy(identity).collect {
      case (filename, occurrences) if occurrences.size > 1 => filename
    }.toSet

    def candidates(filename: String): LazyList[String] = {
      val (baseName, extensionOpt) = baseNameAndExtension(filename)
      val extension                = extensionOpt.map("." + _).getOrElse("")
      LazyList.from(1).map(i => s"$baseName-$i$extension")
    }

    filenames.foldLeft((List.empty[String], Set.empty[String])) { case ((result, used), filename) =>
      val unique =
        if (duplicates(filename))
          candidates(filename).collectFirst {
            case candidate if ! used(candidate) && ! allFilenames(candidate) => candidate
          }.get
        else
          filename
      (unique :: result, used + unique)
    }._1.reverse
  }
}
