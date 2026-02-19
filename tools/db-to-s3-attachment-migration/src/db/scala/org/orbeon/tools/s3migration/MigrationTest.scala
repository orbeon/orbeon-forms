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

import org.orbeon.oxf.fr.S3Tag
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.s3.{S3, S3Config}
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.scalatest.Tag
import org.scalatest.funspec.AnyFunSpecLike
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{DeleteObjectRequest, GetObjectRequest, ListObjectsRequest, PutObjectRequest}

import java.io.File
import java.sql.Connection
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Using


object DBToS3MigrationJarTag extends Tag("DBToS3MigrationJar")

class MigrationTest extends AnyFunSpecLike with ResourceManagerSupport {

  private implicit val Logger: IndentedLogger =
    new IndentedLogger(LoggerFactory.createLogger(classOf[MigrationTest]), true)

  private def requireJarPath: String = {
    val path = sys.props.get("db.to.s3.migration.jar").getOrElse(
      fail("System property 'db.to.s3.migration.jar' not set: run 'sbt dbToS3AttachmentMigration/proguard' first.")
    )
    assert(new File(path).exists(), s"Fat JAR not found at '$path': run 'sbt dbToS3AttachmentMigration/proguard' first.")
    path
  }

  describe("DB-to-S3 attachment migration") {

    it("uploads attachments to S3 and nullifies file_content in the database", S3Tag, DBToS3MigrationJarTag) {
      implicit val s3Config: S3Config = s3ConfigFromEnvOpt.get

      val jarPath    = requireJarPath
      val s3BasePath = s"migration-test/${UUID.randomUUID()}"

      val dataContent      : Array[Byte] = "data attachment content".getBytes("UTF-8")
      val definitionContent: Array[Byte] = "definition attachment content".getBytes("UTF-8")

      Connect.withOrbeonTablesProvideDatasourceDescriptor("db-to-s3 attachment migration") { (connection, _, ds) =>

        val dataKey = insertDataAttachmentRow(
          s3BasePath  = s3BasePath,
          connection  = connection,
          app         = "acme",
          form        = "sales",
          formVersion = 1,
          documentId  = "eb02ff0bc20a3c16b05eb67c1c52ebea0ce6812f",
          draft       = false,
          filename    = "file.bin",
          content     = dataContent
        )

        val definitionKey = insertDefinitionAttachmentRow(
          s3BasePath  = s3BasePath,
          connection  = connection,
          app         = "acme",
          form        = "sales",
          formVersion = 1,
          filename    = "file.bin",
          content     = definitionContent
        )

        S3.withS3Client { implicit s3Client =>
          try {
            val exitCode = runMigrationJar(jarPath, ds.url, ds.username, ds.password, ds.switchDB, s3Config, s3BasePath)
            assert(exitCode == 0, s"Migration JAR exited with code $exitCode")

            // Read back S3 objects contents
            assert(
              s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(s3Config.bucket).key(dataKey).build()).asByteArray() sameElements dataContent,
              "Data attachment content in S3 does not match original"
            )
            assert(
              s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(s3Config.bucket).key(definitionKey).build()).asByteArray() sameElements definitionContent,
              "Definition attachment content in S3 does not match original"
            )

            // Verify file_content is nullified in the database
            assertNullified(
              connection  = connection,
              tableName   = "orbeon_form_data_attach",
              whereClause = "app='acme' AND form='sales' AND form_version=1 AND document_id='eb02ff0bc20a3c16b05eb67c1c52ebea0ce6812f' AND file_name='file.bin'",
              nullified   = true,
              error       = "file_content should be NULL in orbeon_form_data_attach after migration"
            )
            assertNullified(
              connection  = connection,
              tableName   = "orbeon_form_definition_attach",
              whereClause = "app='acme' AND form='sales' AND form_version=1 AND file_name='file.bin'",
              nullified   = true,
              error       = "file_content should be NULL in orbeon_form_definition_attach after migration"
            )

          } finally {
            deleteS3Objects(s3Client, s3Config.bucket, s3BasePath)
          }
        }
      }
    }

    it("does not upload to S3 or nullify database rows in dry run mode", S3Tag, DBToS3MigrationJarTag) {

      implicit val s3Config: S3Config = s3ConfigFromEnvOpt.get

      val jarPath    = requireJarPath
      val s3BasePath = s"migration-test/${UUID.randomUUID()}"

      val dataContent      : Array[Byte] = "dry run data content".getBytes("UTF-8")
      val definitionContent: Array[Byte] = "dry run definition content".getBytes("UTF-8")

      Connect.withOrbeonTablesProvideDatasourceDescriptor("db-to-s3 dry run test") { (connection, _, ds) =>

        insertDataAttachmentRow(
          s3BasePath  = s3BasePath,
          connection  = connection,
          app         = "acme",
          form        = "dryrun",
          formVersion = 1,
          documentId  = "ae271e26bf1b4cccb396ac81bf93b74ab11d3e4e",
          draft       = false,
          filename    = "file.bin",
          content     = dataContent
        )

        insertDefinitionAttachmentRow(
          s3BasePath  = s3BasePath,
          connection  = connection,
          app         = "acme",
          form        = "dryrun",
          formVersion = 1,
          filename    = "file.bin",
          content     = definitionContent
        )

        S3.withS3Client { implicit s3Client =>
          try {
            val exitCode = runMigrationJar(
              jarPath, ds.url, ds.username, ds.password, ds.switchDB, s3Config, s3BasePath, dryRun = true
            )
            assert(exitCode == 0, s"Dry run migration JAR exited with code $exitCode")

            // Verify no S3 objects were created
            val s3Objects = s3Client
              .listObjects(ListObjectsRequest.builder().bucket(s3Config.bucket).prefix(s3BasePath).build())
              .contents()
            assert(s3Objects.isEmpty, s"Expected no S3 objects under '$s3BasePath' after dry run, but found ${s3Objects.size()}")

            // Verify file_content is NOT nullified in the database
            assertNullified(
              connection  = connection,
              tableName   = "orbeon_form_data_attach",
              whereClause = "app='acme' AND form='dryrun' AND form_version=1 AND document_id='ae271e26bf1b4cccb396ac81bf93b74ab11d3e4e' AND file_name='file.bin'",
              nullified   = false,
              error       = "file_content should NOT be NULL in orbeon_form_data_attach after dry run"
            )
            assertNullified(
              connection  = connection,
              tableName   = "orbeon_form_definition_attach",
              whereClause = "app='acme' AND form='dryrun' AND form_version=1 AND file_name='file.bin'",
              nullified   = false,
              error       = "file_content should NOT be NULL in orbeon_form_definition_attach after dry run"
            )

          } finally {
            deleteS3Objects(s3Client, s3Config.bucket, s3BasePath)
          }
        }
      }
    }

    it("aborts with error and preserves DB content when S3 object differs from database", S3Tag, DBToS3MigrationJarTag) {

      implicit val s3Config: S3Config = s3ConfigFromEnvOpt.get

      val jarPath    = requireJarPath
      val s3BasePath = s"migration-test/${UUID.randomUUID()}"

      val dbContent = "correct database content".getBytes("UTF-8")
      val s3Content = "wrong S3 content".getBytes("UTF-8")

      Connect.withOrbeonTablesProvideDatasourceDescriptor("db-to-s3 mismatch test") { (connection, _, ds) =>

        val s3Key = insertDataAttachmentRow(
          s3BasePath  = s3BasePath,
          connection  = connection,
          app         = "acme",
          form        = "orders",
          formVersion = 1,
          documentId  = "6ff8a963cda2e1f5009b6edcc364615c4b63ccec",
          draft       = false,
          filename    = "file.bin",
          content     = dbContent
        )

        S3.withS3Client { implicit s3Client =>
          try {
            // Pre-upload different content to S3 at the same key
            s3Client.putObject(
              PutObjectRequest.builder().bucket(s3Config.bucket).key(s3Key).build(),
              RequestBody.fromBytes(s3Content)
            )

            val exitCode = runMigrationJar(jarPath, ds.url, ds.username, ds.password, ds.switchDB, s3Config, s3BasePath)
            assert(exitCode != 0, "Migration should have failed due to S3/DB content mismatch")

            // Verify file_content is NOT nullified in the database
            assertNullified(
              connection  = connection,
              tableName   = "orbeon_form_data_attach",
              whereClause = "app='acme' AND form='orders' AND form_version=1 AND document_id='6ff8a963cda2e1f5009b6edcc364615c4b63ccec' AND file_name='file.bin'",
              nullified   = false,
              error       = "file_content should NOT be NULL after failed migration"
            )

          } finally {
            deleteS3Objects(s3Client, s3Config.bucket, s3BasePath)
          }
        }
      }
    }

    it("migrates many rows correctly with various parallelism levels", S3Tag, DBToS3MigrationJarTag) {

      implicit val s3Config: S3Config = s3ConfigFromEnvOpt.get

      val jarPath = requireJarPath

      Connect.withOrbeonTablesProvideDatasourceDescriptor("db-to-s3 parallelism test") { (connection, provider, ds) =>
        S3.withS3Client { implicit s3Client =>

          if (provider == Provider.SQLite) {
            // SQLite doesn't support concurrent connections; verify the tool rejects parallelism > 1
            val s3BasePath = s"migration-test/${UUID.randomUUID()}"
            val exitCode = runMigrationJar(
              jarPath, ds.url, ds.username, ds.password, ds.switchDB, s3Config, s3BasePath, parallelism = 2
            )
            assert(exitCode != 0, "Migration with parallelism > 1 should fail for SQLite")
          } else {
            for (parallelism <- List(2, 4, 8)) {

              val s3BasePath = s"migration-test/${UUID.randomUUID()}"

              try {
                // Insert 15 data attachment rows with unique document IDs
                val dataEntries = (1 to 15).map { i =>
                  val content = s"data-content-p$parallelism-$i".getBytes("UTF-8")
                  val s3Key = insertDataAttachmentRow(
                    s3BasePath  = s3BasePath,
                    connection  = connection,
                    app         = "acme",
                    form        = "parallel",
                    formVersion = 1,
                    documentId  = s"b6dbba7e331ea1e7a1fa3d193a26e778493d54bd",
                    draft       = false,
                    filename    = s"file-p$parallelism-$i.bin",
                    content     = content
                  )
                  (s3Key, content)
                }

                // Insert 5 definition attachment rows with unique form versions
                val defEntries = (1 to 5).map { i =>
                  val content = s"def-content-p$parallelism-$i".getBytes("UTF-8")
                  val s3Key = insertDefinitionAttachmentRow(
                    s3BasePath  = s3BasePath,
                    connection  = connection,
                    app         = "acme",
                    form        = "parallel",
                    formVersion = 1,
                    filename    = s"file-p$parallelism-$i.bin",
                    content     = content
                  )
                  (s3Key, content)
                }

                val allEntries = dataEntries ++ defEntries

                // Run migration with the given parallelism
                val exitCode = runMigrationJar(
                  jarPath, ds.url, ds.username, ds.password, ds.switchDB, s3Config, s3BasePath, parallelism
                )
                assert(exitCode == 0, s"Migration with parallelism=$parallelism exited with code $exitCode")

                // Verify all S3 objects exist with correct content
                allEntries.foreach { case (s3Key, expectedContent) =>
                  val actual = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(s3Config.bucket).key(s3Key).build()
                  ).asByteArray()
                  assert(
                    actual sameElements expectedContent,
                    s"S3 content mismatch for $s3Key (parallelism=$parallelism)"
                  )
                }

                // Verify all rows inserted in this iteration are nullified in DB
                assertRowCounts(
                  connection        = connection,
                  tableName         = "orbeon_form_data_attach",
                  whereClause       = s"app='acme' AND form='parallel' AND document_id='b6dbba7e331ea1e7a1fa3d193a26e778493d54bd' AND file_name LIKE 'file-p$parallelism-%'",
                  totalRowCount     = 15,
                  nullifiedRowCount = 15,
                  error             = s"Some data attachment rows not nullified (parallelism=$parallelism)"
                )
                assertRowCounts(
                  connection        = connection,
                  tableName         = "orbeon_form_definition_attach",
                  whereClause       = s"app='acme' AND form='parallel' AND file_name LIKE 'file-p$parallelism-%'",
                  totalRowCount     = 5,
                  nullifiedRowCount = 5,
                  error             = s"Some definition attachment rows not nullified (parallelism=$parallelism)"
                )

              } finally {
                deleteS3Objects(s3Client, s3Config.bucket, s3BasePath)
              }
            }
          }
        }
      }
    }
  }

  private def insertDataAttachmentRow(
    s3BasePath : String,
    connection : Connection,
    app        : String,
    form       : String,
    formVersion: Int,
    documentId : String,
    draft      : Boolean,
    filename   : String,
    content    : Array[Byte]
  ): String = {

    val now = new java.sql.Timestamp(System.currentTimeMillis())

    Using.Manager { use =>
      val stmt = use(connection.prepareStatement(
        "INSERT INTO orbeon_form_data_attach (created, last_modified_time, app, form, form_version, document_id, deleted, draft, file_name, file_content) " +
        "VALUES (?, ?, ?, ?, ?, ?, 'N', ?, ?, ?)"
      ))

      stmt.setTimestamp(1, now)
      stmt.setTimestamp(2, now)
      stmt.setString   (3, app)
      stmt.setString   (4, form)
      stmt.setInt      (5, formVersion)
      stmt.setString   (6, documentId)
      stmt.setString   (7, if (draft) "Y" else "N")
      stmt.setString   (8, filename)
      stmt.setBytes    (9, content)

      stmt.executeUpdate()
    }.get

    List(s3BasePath, app, form, if (draft) "draft" else "data", documentId, formVersion.toString, filename).mkString("/")
  }

  private def insertDefinitionAttachmentRow(
    s3BasePath : String,
    connection : Connection,
    app        : String,
    form       : String,
    formVersion: Int,
    filename   : String,
    content    : Array[Byte]
  ): String = {

    val now = new java.sql.Timestamp(System.currentTimeMillis())

    Using.Manager { use =>
      val stmt = use(connection.prepareStatement(
        "INSERT INTO orbeon_form_definition_attach (created, last_modified_time, app, form, form_version, deleted, file_name, file_content) " +
        "VALUES (?, ?, ?, ?, ?, 'N', ?, ?)"
      ))

      stmt.setTimestamp(1, now)
      stmt.setTimestamp(2, now)
      stmt.setString   (3, app)
      stmt.setString   (4, form)
      stmt.setInt      (5, formVersion)
      stmt.setString   (6, filename)
      stmt.setBytes    (7, content)

      stmt.executeUpdate()
    }.get

    List(s3BasePath, app, form, "form", formVersion.toString, filename).mkString("/")
  }

  private def driverJarPath(driverClassName: String): Option[String] = {
    val clazz = Class.forName(driverClassName)
    Option(clazz.getProtectionDomain.getCodeSource).map(_.getLocation.getPath)
  }

  private def runMigrationJar(
    jarPath     : String,
    dbUrl       : String,
    dbUser      : String,
    dbPassword  : String,
    dbInitSqlOpt: Option[String],
    s3Config    : S3Config,
    s3BasePath  : String,
    parallelism : Int     = 1,
    dryRun      : Boolean = false
  ): Int = {

    val provider    = Provider.fromJdbcUrl(dbUrl)
    val driverClass = Provider.driverClass(provider)
    val extraCp     = driverJarPath(driverClass).map(p => s":$p").getOrElse("")

    val command = List(
      "java", "-cp", s"$jarPath$extraCp", "org.orbeon.tools.s3migration.Main",
      "--db-url",               dbUrl,
      "--db-user",              dbUser,
      "--db-password",          dbPassword,
      "--s3-endpoint",          s3Config.endpoint,
      "--s3-region",            s3Config.region.id(),
      "--s3-bucket",            s3Config.bucket,
      "--s3-access-key",        s3Config.accessKey,
      "--s3-secret-access-key", s3Config.secretAccessKey,
      "--s3-force-path-style",  s3Config.forcePathStyle.toString,
      "--s3-base-path",         s3BasePath,
      "--parallelism",          parallelism.toString
    ) ++
      dbInitSqlOpt.toList.flatMap(List("--db-init-sql", _)) ++
      dryRun.list("--dry-run")

    new ProcessBuilder(command*).inheritIO().start().waitFor()
  }

  private def deleteS3Objects(s3Client: S3Client, bucket: String, prefix: String): Unit =
    s3Client
      .listObjects(ListObjectsRequest.builder().bucket(bucket).prefix(prefix).build())
      .contents().asScala
      .foreach { obj =>
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(obj.key()).build())
      }

  private def assertNullified(
    connection : Connection,
    tableName  : String,
    whereClause: String,
    nullified  : Boolean,
    error      : String
  ): Unit =
    Using.Manager { use =>
      val stmt = use(connection.createStatement())
      val rs   = use(stmt.executeQuery(s"SELECT file_content FROM $tableName WHERE $whereClause"))

      // getBytes is fine for small test files
      assert(rs.next() && Option(rs.getBytes("file_content")).isEmpty == nullified, error)
    }.get

  private def assertRowCounts(
    connection       : Connection,
    tableName        : String,
    whereClause      : String,
    totalRowCount    : Int,
    nullifiedRowCount: Int,
    error            : String
  ): Unit =
    Using.Manager { use =>
      def count(sql: String): Int = {
        val stmt = use(connection.createStatement())
        val rs   = use(stmt.executeQuery(sql))
        rs.next()
        rs.getInt(1)
      }

      assert(
        count(s"SELECT COUNT(*) FROM $tableName WHERE $whereClause")                          == totalRowCount &&
        count(s"SELECT COUNT(*) FROM $tableName WHERE $whereClause AND file_content IS NULL") == nullifiedRowCount,
        error
      )
    }.get

  private def s3ConfigFromEnvOpt: Option[S3Config] =
    for {
      region          <- Option(System.getenv("S3_TEST_REGION"))
      accessKey       <- Option(System.getenv("S3_TEST_ACCESSKEY"))
      secretAccessKey <- Option(System.getenv("S3_TEST_SECRETACCESSKEY"))
    } yield S3Config(
      endpoint        = Option(System.getenv("S3_TEST_ENDPOINT")).filter(_.nonEmpty).getOrElse(S3Config.DefaultEndpoint),
      region          = Region.of(region),
      bucket          = Option(System.getenv("S3_TEST_BUCKET")).filter(_.nonEmpty).getOrElse("orbeon-test"),
      accessKey       = accessKey,
      secretAccessKey = secretAccessKey,
      forcePathStyle  = Option(System.getenv("S3_TEST_FORCE_PATH_STYLE")).exists(_.toBoolean)
    )
}
