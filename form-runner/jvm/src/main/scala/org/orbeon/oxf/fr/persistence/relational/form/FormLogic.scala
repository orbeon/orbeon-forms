/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.form

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.persistence.relational.Statement.{Setter, StatementPart, executeQuery}
import org.orbeon.oxf.fr.persistence.relational.form.adt.*
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion}
import org.orbeon.oxf.util.CollectionUtils.fromIteratorExt
import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.collection.immutable.ListMap

object FormLogic {

  private val MaxAppFormsPerQuery = 400

  def forms(
    provider       : Provider,
    formRequest    : FormRequest
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): FormResponse = {

    val appFormChunks: List[Option[Seq[AppForm]]] =
      formRequest.appForms.distinct match {
        case Seq()    => List(None)
        case nonEmpty => nonEmpty.grouped(MaxAppFormsPerQuery).map(Some.apply).toList
      }

    RelationalUtils.withConnection(provider) { connection =>
      val allForms = appFormChunks.flatMap { appFormsChunkOpt =>

        // At the moment, the only things we filter at the SQL level are exact app/form names and batched (app, form) pairs
        val innerWhereClauses: List[(String, List[Setter])] = List(
          formRequest.exactAppOpt .map(app  => (s"app =  ?", List[Setter](_.setString(_, app )))),
          formRequest.exactFormOpt.map(form => (s"form = ?", List[Setter](_.setString(_, form)))),
          appFormsChunkOpt.map { chunk =>
            val sql     = chunk.map(_ => "(app = ? AND form = ?)").mkString("(", " OR ", ")")
            val setters = chunk.toList.flatMap { af =>
              List[Setter]((_.setString(_, af.app)), (_.setString(_, af.form)))
            }
            (sql, setters)
          }
        ).flatten

        val setters = innerWhereClauses.flatMap(_._2)

        val innerWhere = Some(innerWhereClauses).filter(_.nonEmpty).map(_.map(_._1).mkString(" AND ")).map { innerWhere =>
          s"""
             |WHERE
             |    $innerWhere
             |""".stripMargin
        }.mkString

        val query =
          s"""SELECT
             |    d.app                application_name,
             |    d.form               form_name,
             |    d.form_version       form_version,
             |    d.form_metadata      form_metadata,
             |    d.last_modified_time last_modified_time,
             |    d.last_modified_by   last_modified_by,
             |    d.created            created
             |FROM
             |    orbeon_form_definition d,
             |    (
             |        SELECT
             |            app,
             |            form,
             |            form_version,
             |            MAX(last_modified_time) last_modified_time
             |        FROM
             |            orbeon_form_definition
             |        $innerWhere
             |        GROUP BY
             |            app, form, form_version
             |    ) app_form_version_last_time
             |WHERE
             |    d.app                = app_form_version_last_time.app                AND
             |    d.form               = app_form_version_last_time.form               AND
             |    d.form_version       = app_form_version_last_time.form_version       AND
             |    d.last_modified_time = app_form_version_last_time.last_modified_time AND
             |    d.deleted = 'N'
             |""".stripMargin

        val statementPart = StatementPart(query, setters)

        executeQuery(connection, statementPart.sql, List(statementPart)) { resultSet =>
          Iterator.iterateWhile(
            cond = resultSet.next(),
            elem = {
              val (title, available, permissions) =
                Option(resultSet.getString("form_metadata")).map(_.trim).filter(_.nonEmpty).map { formMetadata =>
                  val xml = XFormsCrossPlatformSupport.stringToTinyTree(
                    configuration  = XPath.GlobalConfiguration,
                    string         = formMetadata,
                    handleXInclude = false,
                    handleLexical  = false
                  ).rootElement

                  // Extract title, availability, and permissions from form metadata
                  val title       = ListMap.from((xml / "title").map { title =>
                    title.attValue("*:lang") -> title.stringValue
                  })
                  val available   = xml.elemValueOpt("available").map(_.toBoolean).getOrElse(true)
                  val permissions = xml.child("permissions").headOption

                  (title, available, permissions)
                } getOrElse {
                  (ListMap.empty[String, String], true, None)
                }

              val appForm = AppForm(
                app  = resultSet.getString("application_name"),
                form = resultSet.getString("form_name")
              )

              val localMetadataOpt = FormMetadata(
                title             = title,
                lastModifiedTime  = resultSet.getTimestamp("last_modified_time").toInstant,
                lastModifiedByOpt = Option(resultSet.getString("last_modified_by")),
                created           = Some(resultSet.getTimestamp("created").toInstant),
                available         = available,
                permissionsOpt    = permissions,
                operations        = OperationsList(Nil)
              ).some

              Form(
                appForm          = appForm,
                version          = FormDefinitionVersion.Specific(resultSet.getInt("form_version")),
                localMetadataOpt = localMetadataOpt,
                remoteMetadata   = Map.empty
              )
            }
          ).toList
        }
      }

      // No paging for now, return all results
      FormResponse(allForms, searchTotal = allForms.size)
    }
  }
}
