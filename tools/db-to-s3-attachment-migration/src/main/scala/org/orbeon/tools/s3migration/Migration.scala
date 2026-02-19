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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.s3.{S3, S3Config}
import org.orbeon.oxf.util.{Mediatypes, SecureUtils}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, PutObjectRequest}

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Using


case class MigrationStats(
  uploaded : Int = 0,
  skipped  : Int = 0,
  nullified: Int = 0
) {
  def +(other: MigrationStats): MigrationStats =
    MigrationStats(uploaded + other.uploaded, skipped + other.skipped, nullified + other.nullified)

  def summary: String =
    s"Migration complete: $uploaded uploaded, $skipped already on S3 (skipped), $nullified nullified in DB."
}

object Migration {

  def migrate(config: MigrationConfig): MigrationStats = {
    implicit val s3Config: S3Config = config.s3

    val provider = config.db.provider

    if (config.parallelism > 1 && provider == Provider.SQLite)
      sys.error("Parallelism > 1 is not supported with SQLite (SQLite does not support concurrent writes).")

    // Pool size: 1 connection for streaming keys + `parallelism` connections for processing.
    // For SQLite (parallelism=1), WAL mode enables concurrent read + write on separate connections.
    Using(new ConnectionPool(config.db, config.parallelism + 1)) { pool =>
      S3.withS3Client { implicit s3Client =>
        pool.withConnection { queryConnection =>

          val totalRows =
            DbOperations.countDataRows      (queryConnection) +
            DbOperations.countDefinitionRows(queryConnection)

          val processed  = new AtomicInteger(0)
          val totalWidth = totalRows.toString.length

          streamAllRowKeys(queryConnection)
            .parEvalMap(config.parallelism) { key =>
              IO.blocking {
                val current  = processed.incrementAndGet()
                val progress = s"[${s"%${totalWidth}d".format(current)}/$totalRows]"

                if (config.dryRun) {
                  processRowDryRun(config, key, progress)
                } else {
                  pool.withConnection { connection =>
                    processRow(connection, s3Client, provider, config, key, progress)
                  }
                }
              }
            }
            .compile.fold(MigrationStats())(_ + _)
            .unsafeRunSync()
        }
      }
    }.get
  }

  // Lazily streams all row keys from both attachment tables, printing counts as each table completes
  private def streamAllRowKeys(connection: Connection): fs2.Stream[IO, RowKey] =
    withCountReport("orbeon_form_data_attach",       DbOperations.streamDataRowKeys(connection)) ++
    withCountReport("orbeon_form_definition_attach", DbOperations.streamDefinitionRowKeys(connection))

  // Wraps a row-key stream to print the table name before fetching and the row count after
  private def withCountReport(tableName: String, stream: fs2.Stream[IO, RowKey]): fs2.Stream[IO, RowKey] = {
    val counter = new AtomicInteger(0)
    (fs2.Stream.eval(IO(println(s"Fetching rows from $tableName..."))) >>
      stream.evalTap(_ => IO(counter.incrementAndGet()))) ++
      fs2.Stream.eval(IO(println(s"  Found ${counter.get()} rows with non-null file_content."))).drain
  }

  private def processRowDryRun(
    config  : MigrationConfig,
    key     : RowKey,
    progress: String
  ): MigrationStats = {
    val s3Key = key.s3Key(config.s3BasePath)
    println(s"  $progress [DRY RUN]  ${key.tableName}: $s3Key")
    MigrationStats(uploaded = 1)
  }

  private def processRow(
    connection: Connection,
    s3Client  : S3Client,
    provider  : Provider,
    config    : MigrationConfig,
    key       : RowKey,
    progress  : String
  ): MigrationStats = {

    val s3Key    = key.s3Key(config.s3BasePath)
    val s3Exists = S3.objectMetadata(config.s3.bucket, s3Key)(s3Client).isSuccess

    // Upload if needed, and compute DB hash + size
    val (dbHash, dbSize, rowStats) = if (s3Exists) {
      println(s"  $progress [SKIP]     ${key.tableName}: $s3Key")

      // Stream DB content to compute hash for verification
      val (hash, size) = DbOperations.withContentStream(connection, provider, key) { (dbStream, contentLength) =>
        (SecureUtils.digestStream(dbStream, "SHA-256"), contentLength)
      }.getOrElse(
        sys.error(s"file_content disappeared for key: $s3Key")
      )

      (hash, size, MigrationStats(skipped = 1))
    } else {
      println(s"  $progress [UPLOAD]   ${key.tableName}: $s3Key")

      // Stream content from DB, upload to S3, and compute SHA-256 on-the-fly
      val (hash, size) = DbOperations.withContentStream(connection, provider, key) { (dbStream, contentLength) =>
        val digest       = MessageDigest.getInstance("SHA-256")
        val digestStream = new DigestInputStream(dbStream, digest)
        val contentType  = Mediatypes.findMediatypeForPath(key.filename)

        upload(s3Client, config.s3.bucket, s3Key, digestStream, contentLength, contentType)

        (SecureUtils.byteArrayToHex(digest.digest()), contentLength)
      }.getOrElse(
        sys.error(s"file_content disappeared for key: $s3Key")
      )

      (hash, size, MigrationStats(uploaded = 1))
    }

    // Verify integrity by streaming S3 object and comparing size + SHA-256 hash
    val (s3Hash, s3Size) = s3ObjectHashAndSize(s3Client, config.s3.bucket, s3Key)

    if (dbSize != s3Size) {
      sys.error(
        s"Size mismatch for $s3Key (DB: $dbSize, S3: $s3Size). Aborting to avoid data loss."
      )
    }
    if (dbHash != s3Hash) {
      sys.error(
        s"SHA-256 mismatch for $s3Key (DB: $dbHash, S3: $s3Hash). Aborting to avoid data loss."
      )
    }

    // Nullify in DB
    DbOperations.nullifyRow(connection, key)
    connection.commit()
    println(s"  $progress [NULLIFY]  ${key.tableName}: $s3Key")
    rowStats.copy(nullified = 1)
  }

  private def upload(
    client       : S3Client,
    bucket       : String,
    key          : String,
    stream       : InputStream,
    contentLength: Long,
    contentType  : Option[String]
  ): Unit = {
    val builder = PutObjectRequest.builder().bucket(bucket).key(key)
    contentType.foreach(builder.contentType)
    client.putObject(builder.build(), RequestBody.fromInputStream(stream, contentLength))
  }

  private def s3ObjectHashAndSize(client: S3Client, bucket: String, key: String): (String, Long) = {
    val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
    Using(client.getObject(request)) { response =>
      val hash = SecureUtils.digestStream(response, "SHA-256")
      val size = response.response().contentLength().longValue()
      (hash, size)
    }.get
  }
}
