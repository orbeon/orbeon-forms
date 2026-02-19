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

import cats.data.ValidatedNel
import cats.implicits.*
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.s3.S3Config
import software.amazon.awssdk.regions.Region


case class DbConfig(
  url       : String,
  user      : String,
  password  : String,
  initSqlOpt: Option[String]
) {
  val provider   : Provider = Provider.fromJdbcUrl(url)
  val driverClass: String   = Provider.driverClass(provider)
}

case class MigrationConfig(
  db         : DbConfig,
  s3         : S3Config,
  s3BasePath : String,
  parallelism: Int,
  dryRun     : Boolean
)

object Config {

  // Derive the env var name from a CLI flag name, e.g. "db-url" -> "ORBEON_DB_URL"
  private def envVarName(flagName: String): String =
    s"ORBEON_${flagName.toUpperCase.replace('-', '_')}"

  def fromArgsAndEnv(args: Array[String]): ValidatedNel[String, MigrationConfig] = {
    val argMap = parseArgs(args)

    // CLI args take precedence over environment variables

    def keyValueOpt(key: String): Option[String] =
      argMap.get(key).orElse(Option(System.getenv(envVarName(key)))).filter(_.nonEmpty)

    def require(key: String): ValidatedNel[String, String] =
      keyValueOpt(key).toValidNel(s"Missing required parameter: --$key (or env var ${envVarName(key)})")

    (
      require("db-url"),
      require("s3-bucket"),
      require("s3-access-key"),
      require("s3-secret-access-key")
    ).mapN { (dbUrl, s3Bucket, s3AccessKey, s3SecretKey) =>
      MigrationConfig(
        db = DbConfig(
          url        = dbUrl,
          user       = keyValueOpt("db-user").getOrElse(""),
          password   = keyValueOpt("db-password").getOrElse(""),
          initSqlOpt = keyValueOpt("db-init-sql")
        ),
        s3 = S3Config(
          endpoint        = keyValueOpt("s3-endpoint").getOrElse(S3Config.DefaultEndpoint),
          region          = Region.of(keyValueOpt("s3-region").getOrElse(S3Config.DefaultRegion)),
          bucket          = s3Bucket,
          accessKey       = s3AccessKey,
          secretAccessKey = s3SecretKey,
          forcePathStyle  = keyValueOpt("s3-force-path-style").flatMap(_.toBooleanOption).getOrElse(false)
        ),
        s3BasePath  = keyValueOpt("s3-base-path").getOrElse(""),
        parallelism = keyValueOpt("parallelism").flatMap(_.toIntOption).getOrElse(1),
        dryRun      = keyValueOpt("dry-run").flatMap(_.toBooleanOption).getOrElse(false)
      )
    }
  }

  // Parse --key=value and --key value style arguments
  private def parseArgs(args: Array[String]): Map[String, String] = {
    val result = collection.mutable.Map[String, String]()
    var i = 0
    while (i < args.length) {
      val arg = args(i)
      if (arg.startsWith("--")) {
        val withoutDashes = arg.drop(2)
        if (withoutDashes.contains("=")) {
          val eqIdx = withoutDashes.indexOf("=")
          result(withoutDashes.take(eqIdx)) = withoutDashes.drop(eqIdx + 1)
        } else if (i + 1 < args.length && !args(i + 1).startsWith("--")) {
          result(withoutDashes) = args(i + 1)
          i += 1
        } else {
          result(withoutDashes) = "true"
        }
      }
      i += 1
    }
    result.toMap
  }

  val UsageMessage: String =
    """Usage: java -jar db-to-s3-attachment-migration.jar [options]
      |
      |Required options (can also be set via environment variables):
      |  --db-url               JDBC URL for the database
      |  --s3-bucket            S3 bucket name
      |  --s3-access-key        AWS access key ID
      |  --s3-secret-access-key AWS secret access key
      |
      |Optional options:
      |  --db-user             Database username                                      (default: empty)
      |  --db-password         Database password                                      (default: empty)
      |  --db-init-sql         SQL to execute after connecting                        (default: empty)
      |  --s3-endpoint         S3 endpoint URL                                        (default: s3.amazonaws.com)
      |  --s3-region           S3 region                                              (default: aws-global)
      |  --s3-base-path        S3 key prefix                                          (default: empty)
      |  --s3-force-path-style Use path-style S3 URLs                                 (default: false)
      |  --parallelism         Number of rows to process concurrently                 (default: 1)
      |  --dry-run             Preview what would be migrated, without making changes (default: false)
      |""".stripMargin
}
