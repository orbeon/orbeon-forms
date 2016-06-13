package org.orbeon.oxf.fr

import javax.naming.{Context, InitialContext, NameAlreadyBoundException}

import org.apache.commons.dbcp.BasicDataSource
import org.orbeon.oxf.fr.persistence.relational.Provider.{DB2, _}
import org.orbeon.oxf.util.ScalaUtils._

import scala.collection.immutable

case class DatasourceDescriptor(name: String, driver: String, url: String, username: String, password: String)

object DatasourceDescriptor {

  import DataSourceSupport._

  def apply(provider: Provider, user: Option[String]): DatasourceDescriptor = {

    val (url, username, password) = connectionDetailsFromEnv(provider, user)

    DatasourceDescriptor(
      name     = provider.name,
      driver   = DriverClassNames(provider),
      url      = url,
      username = username,
      password = password
    )
  }

}

object DataSourceSupport {

  def withDatasources[T](datasources: immutable.Seq[DatasourceDescriptor])(thunk: ⇒ T): T = {
    val previousProperties = setupInitialContextForJDBC()
    datasources foreach bindDatasource
    val result = thunk
    datasources foreach unbindDatasource
    clearInitialContextForJDBC(previousProperties)
    result
  }

  val DriverClassNames = Map(
    Oracle     → "oracle.jdbc.OracleDriver",
    MySQL      → "com.mysql.jdbc.Driver",
    SQLServer  → "com.microsoft.sqlserver.jdbc.SQLServerDriver",
    PostgreSQL → "org.postgresql.Driver",
    DB2        → "com.ibm.db2.jcc.DB2Driver"
  )

  def connectionDetailsFromEnv(provider: Provider, user: Option[String]) = {

    val url = provider match {
      case Oracle     ⇒ System.getenv("ORACLE_URL")
      case MySQL      ⇒ System.getenv("MYSQL_URL")      + user.map("/" + _).getOrElse("")
      case SQLServer  ⇒ System.getenv("SQLSERVER_URL")  + user.map(";databaseName=" + _).getOrElse("")
      case PostgreSQL ⇒ System.getenv("POSTGRESQL_URL") + user.map("/" + _).getOrElse("/")
      case DB2        ⇒ System.getenv("DB2_URL")
    }

    val username = provider match {
      case Oracle ⇒ user.getOrElse("orbeon")
      case _      ⇒ "orbeon"
    }

    val password = System.getenv("RDS_PASSWORD")

    (url, username, password)
  }

  private val BuildNumber = System.getenv("TRAVIS_BUILD_NUMBER")

  def ddlUserFromBuildNumber    = s"orbeon_${BuildNumber}_ddl"
  def tomcatUserFromBuildNumber = s"orbeon_${BuildNumber}_tomcat"

  private val NamingPrefix = "java:comp/env/jdbc/"

  private def setupInitialContextForJDBC(): List[(String, Option[String])] = {

    val previousProperties = List(Context.INITIAL_CONTEXT_FACTORY, Context.URL_PKG_PREFIXES) map { name ⇒
      name → Option(System.getProperty(name))
    }

    System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory")
    System.setProperty(Context.URL_PKG_PREFIXES,        "org.apache.naming")

    try {
      new InitialContext                      |!>
        (_.createSubcontext("java:"))         |!>
        (_.createSubcontext("java:comp"))     |!>
        (_.createSubcontext("java:comp/env")) |!>
        (_.createSubcontext("java:comp/env/jdbc"))
    } catch {
      case e: NameAlreadyBoundException ⇒ // ignore
    }

    previousProperties
  }

  private def clearInitialContextForJDBC(previousProperties: List[(String, Option[String])]) = {
    previousProperties foreach {
      case (name, Some(value)) ⇒ System.setProperty(name, value)
      case (name, None)        ⇒ System.clearProperty(name)
    }
  }

  private def bindDatasource(ds: DatasourceDescriptor): Unit =
    (new InitialContext).rebind(
      NamingPrefix + ds.name,
      new BasicDataSource                   |!>
        (_.setDriverClassName(ds.driver))   |!>
        (_.setUrl            (ds.url))      |!>
        (_.setUsername       (ds.username)) |!>
        (_.setPassword       (ds.password))
    )

  private  def unbindDatasource(ds: DatasourceDescriptor): Unit =
    (new InitialContext).unbind(NamingPrefix + ds.name)
}
