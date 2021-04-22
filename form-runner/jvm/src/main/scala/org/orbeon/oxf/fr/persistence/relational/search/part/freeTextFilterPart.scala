/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.search.part

import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Statement._
import org.orbeon.oxf.fr.persistence.relational.search.adt.SearchRequest

object freeTextFilterPart {

  def apply(request: SearchRequest): StatementPart =
    request.freeTextSearch match {

      case None =>
        NilPart

      case Some(freeTextSearch) =>
        StatementPart(
          sql =
            s"""|AND d.id = c.data_id
                |AND ${Provider.xmlContains(request.provider)}
                |""".stripMargin,
          setters = {
            val param = Provider.xmlContainsParam(request.provider, freeTextSearch)
            List(_.setString(_, param))
          }
        )

    }
}
