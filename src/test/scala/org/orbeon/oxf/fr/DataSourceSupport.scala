package org.orbeon.oxf.fr

import javax.naming.{Context, InitialContext, NameAlreadyBoundException}

import org.apache.commons.dbcp.BasicDataSource
import org.orbeon.oxf.fr.persistence.relational.Provider._
import org.orbeon.oxf.util.ScalaUtils._

import scala.collection.immutable

case class DatasourceDescriptor(name: String, driver: String, url: String, username: String, password: String)

object DatasourceDescriptor {

  def apply(provider: Provider, user: Option[String]): DatasourceDescriptor = {

    val url = provider match {
      case MySQL      ⇒ System.getenv("MYSQL_URL")      + user.map("/" + _).getOrElse("")
      case PostgreSQL ⇒ System.getenv("POSTGRESQL_URL") + user.map("/" + _).getOrElse("/")
    }

    val username = provider match {
      case _      ⇒ "orbeon"
    }

    val password = System.getenv("RDS_PASSWORD")

    DatasourceDescriptor(
      name     = provider.name,
      driver   = DriverClassNames(provider),
      url      = url,
      username = username,
      password = password
    )
  }

  private val DriverClassNames = Map(
    MySQL      → "com.mysql.jdbc.Driver",
    PostgreSQL → "org.postgresql.Driver"
  )
}

// Utility to setup datasources outside of a servlet container environment, such as when running tests.
object DataSourceSupport {

  // Run the given thunk in the context of the datasources specified by the given descriptors.
  // This sets a JNDI context, binds the datasources, runs the thunk, unbind the datasources, and
  // does some JNDI context cleanup.
  def withDatasources[T](datasources: immutable.Seq[DatasourceDescriptor])(thunk: ⇒ T): T = {
    val originalProperties = setupInitialContextForJDBC()
    datasources foreach bindDatasource
    val result = thunk
    datasources foreach unbindDatasource
    clearInitialContextForJDBC(originalProperties)
    result
  }

  private val BuildNumber = System.getenv("TRAVIS_BUILD_NUMBER")

  def ddlUserFromBuildNumber    = s"orbeon_${BuildNumber}_ddl"
  def tomcatUserFromBuildNumber = s"orbeon_${BuildNumber}_tomcat"

  private val NamingPrefix = "java:comp/env/jdbc/"

  private def setupInitialContextForJDBC(): List[(String, Option[String])] = {

    val originalProperties = List(Context.INITIAL_CONTEXT_FACTORY, Context.URL_PKG_PREFIXES) map { name ⇒
      name → Option(System.getProperty(name))
    }

    System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory")
    System.setProperty(Context.URL_PKG_PREFIXES,        "org.apache.naming")

    def createSubcontextIgnoreIfBound(ic: InitialContext, name: String) =
      try {
        ic.createSubcontext(name)
      } catch {
        case e: NameAlreadyBoundException ⇒ // ignore
      }

    new InitialContext                                         |!>
      (createSubcontextIgnoreIfBound(_, "java:"             )) |!>
      (createSubcontextIgnoreIfBound(_, "java:comp"         )) |!>
      (createSubcontextIgnoreIfBound(_, "java:comp/env"     )) |!>
      (createSubcontextIgnoreIfBound(_, "java:comp/env/jdbc"))

    originalProperties
  }

  private def clearInitialContextForJDBC(originalProperties: List[(String, Option[String])]) =
    originalProperties foreach {
      case (name, Some(value)) ⇒ System.setProperty(name, value)
      case (name, None)        ⇒ System.clearProperty(name)
    }

  private def bindDatasource(ds: DatasourceDescriptor): Unit =
    (new InitialContext).rebind(
      NamingPrefix + ds.name,
      new BasicDataSource                   |!>
        (_.setDriverClassName(ds.driver  )) |!>
        (_.setUrl            (ds.url     )) |!>
        (_.setUsername       (ds.username)) |!>
        (_.setPassword       (ds.password))
    )

  private  def unbindDatasource(ds: DatasourceDescriptor): Unit =
    (new InitialContext).unbind(NamingPrefix + ds.name)
}
