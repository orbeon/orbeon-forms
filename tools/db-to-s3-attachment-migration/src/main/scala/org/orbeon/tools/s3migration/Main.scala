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
package org.orbeon.tools.s3migration

import cats.data.Validated.{Invalid, Valid}
import org.orbeon.oxf.resources.ResourceManagerWrapper


object Main {

  private def initResourceManager(): Unit = {
    val props = new java.util.HashMap[String, AnyRef]
    // Mediatypes accesses mime-types.xml using an oxf: URL
    props.put("oxf.resources.factory", "org.orbeon.oxf.resources.ClassLoaderResourceManagerFactory")
    ResourceManagerWrapper.init(props)
  }

  def main(args: Array[String]): Unit = {

    if (args.contains("--help") || args.contains("-h")) {
      println(Config.UsageMessage)
      sys.exit(0)
    }

    initResourceManager()

    Config.fromArgsAndEnv(args) match {
      case Invalid(errors) =>
        errors.toList.foreach(e => System.err.println(s"Error: $e"))
        System.err.println()
        System.err.println(Config.UsageMessage)
        sys.exit(1)

      case Valid(config) =>
        println(s"Starting migration to S3 bucket '${config.s3.bucket}' (endpoint: ${config.s3.endpoint})...")
        println(s"Database: ${config.db.url}")
        println(s"Parallelism: ${config.parallelism}")
        if (config.dryRun)
          println(s"Dry run: true")
        println()

        try {
          val stats = Migration.migrate(config)
          println()
          println(stats.summary)
        } catch {
          case e: Exception =>
            System.err.println(s"Migration failed: ${e.getMessage}")
            sys.exit(1)
        }
    }
  }
}
