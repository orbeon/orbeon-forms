/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.relational

import java.sql.{ResultSet, Connection, PreparedStatement}
import org.orbeon.oxf.properties.Properties
import javax.naming.{Context, InitialContext}
import javax.sql.DataSource
import org.orbeon.oxf.util.ScalaUtils._

object RelationalUtils {

    def withConnection[T](provider: String)(block: Connection ⇒ T): T = {
        // Get connection to the database
        val dataSource = {
            val propertySet = Properties.instance.getPropertySet
            val datasourceProperty = Seq("oxf.fr.persistence", provider, "datasource") mkString "."
            val datasource = propertySet.getString(datasourceProperty)
            val jndiContext = new InitialContext().lookup("java:comp/env/jdbc").asInstanceOf[Context]
            jndiContext.lookup(datasource).asInstanceOf[DataSource]
        }
        useAndClose(dataSource.getConnection)(block)
    }

    /**
     * For cases where we can't use `setString` on a prepared statement
     * - Apache Commons Lang had a `StringEscapeUtils.escapeSql` [1] but it has been deprecated in Commons Lang 3 [2]
     * - just escaping the quote is enough for safety but might return the wrong result if the string is in a
     *   LIKE; in that case more characters should be escaped [3]
     *
     *   [1]: http://javasourcecode.org/html/open-source/commons-lang/commons-lang-2.6/org/apache/commons/lang/StringEscapeUtils.java.html
     *   [2]: http://commons.apache.org/proper/commons-lang/article3_0.html
     *   [3]: http://www.jguru.com/faq/view.jsp?EID=8881
     */
    def sqlString(text: String) = "'" + text.replaceAllLiterally("'", "''") + "'"

}
