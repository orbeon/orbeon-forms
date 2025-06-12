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
package org.orbeon.oxf.fr.persistence.test

import cats.implicits.catsSyntaxOptionId
import org.log4s.Logger
import org.orbeon.dom.Document
import org.orbeon.oxf.externalcontext.{Credentials, ExternalContext, SafeRequestContext, UserAndGroup}
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.HttpCall
import org.orbeon.oxf.fr.persistence.http.HttpCall.Check
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.instantFromString
import org.orbeon.oxf.fr.persistence.relational.form.adt.*
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, Version}
import org.orbeon.oxf.http.HttpMethod.POST
import org.orbeon.oxf.http.StatusCode.isSuccessCode
import org.orbeon.oxf.http.{BasicCredentials, StatusCode}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, WithResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.StaticXPath.orbeonDomToTinyTree
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.scalatest.funspec.AnyFunSpecLike

import java.io.ByteArrayInputStream
import java.nio.file.{Files, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit


// We're writing dynamic properties to a file, to have a single provider active at a time at the proxy level. We could
// probably do this in-memory, but at the time it's easier to write to a file and reload the properties, especially
// since we're including (xi:include) existing test properties defined in a file.
case class ActiveProviderResourceManagerSupport(
  var activeProviderOpt: Option[Provider] = None,
  applicationCounts: Int
) extends WithResourceManagerSupport {

  // TODO: before/after in ResourceManagerSupport don't seem to work as expected, don't delete generated properties file for now

  override lazy val logger: Logger = LoggerFactory.createLogger(ActiveProviderResourceManagerSupport.getClass)

  private lazy val (propertiesDirectory, propertiesFilename) = {
    @annotation.tailrec
    def propertiesDirectory(index: Int = 1): String = {
      val systemProperty = s"oxf.resources.priority.$index.oxf.resources.filesystem.sandbox-directory"

      Option(System.getProperty(systemProperty)) match {
        case None       => throw new Exception("Couldn't find an existing resource directory")
        case Some(path) => if (Files.exists(Paths.get(path))) path else propertiesDirectory(index + 1)
      }
    }

    (propertiesDirectory(), "properties-form-metadata-api-test.xml")
  }

  override lazy val propertiesUrl: String = {
    writePropertiesFile(reloadProperties = false)

    s"oxf:/$propertiesFilename"
  }

  def setActiveProvider(provider: Provider): Unit = {
    activeProviderOpt = provider.some
    writePropertiesFile(reloadProperties = true)
  }

  private def writePropertiesFile(reloadProperties: Boolean): Unit = {
    Files.write(Paths.get(s"$propertiesDirectory/$propertiesFilename"), propertiesAsString().getBytes)

    if (reloadProperties) {
      // Force reload of properties
      org.orbeon.oxf.properties.Properties.invalidate()
      org.orbeon.oxf.properties.Properties.init(propertiesUrl)
    }
  }

  private def propertiesAsString(): String = {

    // Application name to provider mapping
    val providerSpecificApplicationsProperties =
      activeProviderOpt match {
        case Some(activeProvider) =>
          applicationNames.map { applicationName =>
            s"""<property as="xs:string" name="oxf.fr.persistence.provider.$applicationName.*.*" value="${activeProvider.entryName}"/>"""
          }.mkString("\n")

        case None =>
          ""
      }

    // TODO: We should list all possible providers from property files here instead of hardcoding them, but this is
    //       made complicated by the fact that the properties are global and we're currently initializing test
    //       properties at this point. This is needed for CE as properties exist for providers that are not present
    //       in the Provider enum.
    val providersToMakeInactive =
      Set("DB2", "MySQL", "Oracle", "PostgreSQL", "SQLite", "SQLServer").map(_.toLowerCase) ++
      Provider.values.map(_.entryName)

    // Active and inactive providers (we test one provider at a time)
    val activeProvidersProperties = providersToMakeInactive.map { providerEntryName =>
      val active = activeProviderOpt.map(_.entryName).forall(_ == providerEntryName)
      s"""<property as="xs:boolean" name="oxf.fr.persistence.$providerEntryName.active" value="$active"/>"""
    }.mkString("\n")

    s"""<properties xmlns:xs="http://www.w3.org/2001/XMLSchema"
       |            xmlns:oxf="http://www.orbeon.com/oxf/processors"
       |            xmlns:xi="http://www.w3.org/2001/XInclude">
       |
       |    <xi:include href="oxf:/ops/unit-tests/properties.xml"/>
       |
       |    $providerSpecificApplicationsProperties
       |    $activeProvidersProperties
       |</properties>""".stripMargin
  }

  def applicationNames: Seq[String] =
    for {
      activeProvider <- activeProviderOpt.toSeq
      index          <- 1 to applicationCounts
    } yield s"${activeProvider.entryName}-$index"
}

/*
 TODO: tests to add
 - availability/status filtering/sorting
 - multiple filtering/sorting queries
 - pagination + filtering/sorting queries
 - more serialization/deserialization tests (e.g. raw permissions XML)
 */

class FormMetadataApiTest
  extends DocumentTestBase
    with XFormsSupport
    with ResourceManagerSupport
    with AnyFunSpecLike {

  // Number of application names that will be generated and associated with the active provider
  private val applicationCounts = 4

  override def resourceManagerSupportInitializer: ActiveProviderResourceManagerSupport =
    ActiveProviderResourceManagerSupport(applicationCounts = applicationCounts)

  private implicit val Logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[SearchTest]), true)

  val FormMetadataPostApiURL = "form"

  def withProvider(test: Provider => ExternalContext => Unit): Unit =
    withTestExternalContext { implicit externalContext =>
      Connect.withOrbeonTables("form definition") { (connection, provider) =>

        resourceManagerSupportInitializer.setActiveProvider(provider)
        test(provider)(externalContext)
      }
    }

  def xmlResponseFilter(doc: Document) = {
    val modifiedDoc = doc.deepCopy

    for {
      formElem     <- modifiedDoc.getDocument.getRootElement.elements("form")
      elemToDetach <- formElem.elements("last-modified-time") ++ formElem.elements("created")
    } locally {
      elemToDetach.detach()
    }

    modifiedDoc.getDocument
  }

  def assertCall(
    searchRequest     : Document,
    searchResult      : Document,
    statusCode        : Int                  = StatusCode.Ok,
    contentType       : Check[String]        = Check.Ignore,
    xmlResponseFilter : Document => Document = identity
  )(implicit
    safeRequestCtx: SafeRequestContext
  ): Unit =
    HttpCall.assertCall(
      HttpCall.SolicitedRequest(
        path              = FormMetadataPostApiURL,
        method            = POST,
        body              = HttpCall.XML(searchRequest).some,
        xmlResponseFilter = xmlResponseFilter.some
      ),
      HttpCall.ExpectedResponse(
        code        = statusCode,
        contentType = contentType,
        body        = HttpCall.XML(searchResult).some
      )
    )

  case class PartialFormMetadata(formName: String, created: Option[Instant], modified: Instant)

  private def partialFormMetadata(body: Array[Byte]): Seq[PartialFormMetadata] = {
    val root = orbeonDomToTinyTree(IOSupport.readOrbeonDom(new ByteArrayInputStream(body))).rootElement

    (root / "form").toList map { formElem =>
      PartialFormMetadata(
        formName = (formElem / "form-name").head.stringValue,
        created  = (formElem / "created").headOption.map(_.stringValue).map(instantFromString),
        modified = instantFromString((formElem / "last-modified-time").head.stringValue)
      )
    }
  }

  private def solicitedRequest(searchRequest: Document): HttpCall.SolicitedRequest =
    HttpCall.SolicitedRequest(
      path   = FormMetadataPostApiURL,
      method = POST,
      body   = HttpCall.XML(searchRequest).some
    )

  private def assert(
    searchRequest     : Document,
    expectedStatusCode: Int,
    assertFunction    : Seq[String] => Unit
  )(implicit
   safeRequestCtx: SafeRequestContext
  ): Unit = {
    HttpCall.assertCall(
      actualRequest  = solicitedRequest(searchRequest),
      assertResponse = response => {
        assert(response.code == expectedStatusCode)

        if (isSuccessCode(response.code)) {
          // Extract form names from response
          val formNames = partialFormMetadata(response.body).map(_.formName)

          assertFunction(formNames)
        }
      }
    )
  }

  private def assertFilter(
    metadata          : String,
    requestLanguageOpt : Option[String] = None,
    queryLanguageOpt   : Option[String] = None,
    matchType          : String,
    value              : String,
    expectedFormNames  : Set[String] = Set.empty,
    expectedStatusCode : Int = StatusCode.Ok
  )(implicit
    safeRequestCtx: SafeRequestContext
  ): Unit = {

    val searchRequest =
      <search xml:lang={requestLanguageOpt.orNull}>
        <filter metadata={metadata} match={matchType} xml:lang={queryLanguageOpt.orNull}>{value}</filter>
      </search>.toDocument

    assert(
      searchRequest      = searchRequest,
      expectedStatusCode = expectedStatusCode,
      assertFunction     = formNames => assert(formNames.toSet == expectedFormNames)
    )
  }

  private def assertSort(
    metadata               : String,
    requestLanguageOpt     : Option[String] = None,
    queryLanguageOpt       : Option[String] = None,
    direction              : String,
    expectedSortedFormNames: Seq[String] = Seq.empty,
    expectedStatusCode     : Int = StatusCode.Ok
  )(implicit
    safeRequestCtx: SafeRequestContext
  ): Unit = {

    val searchRequest =
      <search xml:lang={requestLanguageOpt.orNull}>
        <sort metadata={metadata} direction={direction} xml:lang={queryLanguageOpt.orNull}/>
      </search>.toDocument

    assert(
      searchRequest      = searchRequest,
      expectedStatusCode = expectedStatusCode,
      assertFunction     = formNames => assert(formNames == expectedSortedFormNames)
    )
  }

  private def assertPagination(
    pageNumber                : Int,
    pageSize                  : Int,
    expectedPaginatedFormNames: Seq[String] = Seq.empty,
    expectedStatusCode        : Int = StatusCode.Ok
  )(implicit
    safeRequestCtx            : SafeRequestContext
  ): Unit =
    assertPaginationWithStrings(
      pageNumber                 = pageNumber.toString,
      pageSize                   = pageSize.toString,
      expectedPaginatedFormNames = expectedPaginatedFormNames,
      expectedStatusCode         = expectedStatusCode
    )

  private def assertPaginationWithStrings(
    pageNumber                : String,
    pageSize                  : String,
    expectedPaginatedFormNames: Seq[String] = Seq.empty,
    expectedStatusCode        : Int = StatusCode.Ok
  )(implicit
    safeRequestCtx            : SafeRequestContext
  ): Unit = {

    val searchRequest =
      <search>
        <sort metadata="form-name" direction="asc"/>
        <pagination page-number={pageNumber} page-size={pageSize}/>
      </search>.toDocument

    assert(
      searchRequest      = searchRequest,
      expectedStatusCode = expectedStatusCode,
      assertFunction     = formNames => assert(formNames == expectedPaginatedFormNames)
    )
  }

  // Create multiple form definitions with metadata all having different orders
  def createTestForms(provider: Provider)(implicit safeRequestCtx: SafeRequestContext): Seq[PartialFormMetadata] = {

    val testForm1 = TestForm(
      appForm          = AppForm(s"${provider.entryName}-3", "test-form-2"),
      titlesByLanguage = Map("en" -> "English title 1", "fr" -> "Titre français 3"),
      controls         = Seq.empty,
      operations       = "delete list".some
    )

    val testForm2 = TestForm(
      appForm          = AppForm(s"${provider.entryName}-1", "test-form-4"),
      titlesByLanguage = Map("en" -> "English title 3", "fr" -> "Titre français 2"),
      controls         = Seq.empty,
      operations       = "create list".some
    )

    val testForm3 = TestForm(
      appForm          = AppForm(s"${provider.entryName}-4", "test-form-1"),
      titlesByLanguage = Map("en" -> "English title 2", "fr" -> "Titre français 4"),
      controls         = Seq.empty,
      operations       = "list".some
    )

    val testForm4 = TestForm(
      appForm          = AppForm(s"${provider.entryName}-2", "test-form-3"),
      titlesByLanguage = Map("en" -> "English title 4", "fr" -> "Titre français 1"),
      controls         = Seq.empty,
      operations       = "read list".some
    )

    def credentials(username: String): Option[Credentials] =
      Credentials(
        userAndGroup  = UserAndGroup(username = username, groupname = None),
        roles         = Nil,
        organizations = Nil
      ).some

    // Creation order
    testForm4.putFormDefinition(version = Version.Specific(1), credentials = credentials("user-2"))
    testForm1.putFormDefinition(version = Version.Specific(4), credentials = credentials("user-1"))
    testForm2.putFormDefinition(version = Version.Specific(2), credentials = credentials("user-4"))
    testForm3.putFormDefinition(version = Version.Specific(3), credentials = credentials("user-3"))

    // Modification order
    testForm3.putFormDefinition(version = Version.Specific(3), credentials = credentials("user-3"), update = true)
    testForm4.putFormDefinition(version = Version.Specific(1), credentials = credentials("user-2"), update = true)
    testForm2.putFormDefinition(version = Version.Specific(2), credentials = credentials("user-4"), update = true)
    testForm1.putFormDefinition(version = Version.Specific(4), credentials = credentials("user-1"), update = true)

    HttpCall.request(solicitedRequest(<search/>.toDocument), response => partialFormMetadata(response.body))
  }

  describe("Form Metadata API") {

    it("returns an empty result when there are no form definition") {
      withProvider { _ => externalContext =>

        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        val searchRequest = <search/>.toDocument
        val searchResult  = <forms search-total="0"/>.toDocument

        assertCall(
          searchRequest = searchRequest,
          searchResult  = searchResult,
          contentType   = Check.Some(ContentTypes.XmlContentType)
        )
      }
    }

    it("returns a single result when there is a single form definition") {
      withProvider { provider => externalContext =>

        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        val testForm = TestForm(provider, controls = Seq.empty, formName = "test-form-1")
        testForm.putFormDefinition(version = Version.Specific(1))

        val searchRequest = <search/>.toDocument

        val searchResult =
          <forms search-total="1">
            <form operations="*">
              <application-name>{provider.entryName}</application-name>
              <form-name>test-form-1</form-name>
              <form-version>1</form-version>
              <title xml:lang="en">test-form-1</title>
              <available>true</available>
            </form>
          </forms>.toDocument

        assertCall(
          searchRequest     = searchRequest,
          searchResult      = searchResult,
          contentType       = Check.Some(ContentTypes.XmlContentType),
          xmlResponseFilter = xmlResponseFilter
        )
      }
    }

    it("returns forms filtered according to filter query") {
      withProvider { provider => externalContext =>

        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        val partialFormMetadata = createTestForms(provider).sortBy(_.formName)

        // Application name (test metadata/direction case insensitivity as well)

        for (metadata <- Seq("application-name", "APPLICATION-NAME"); matchType <- Seq("exact", "EXACT", "eXaCt")) {
          assertFilter(
            metadata          = metadata,
            matchType         = matchType,
            value             = s"${provider.entryName}-4",
            expectedFormNames = Set("test-form-1")
          )
        }

        assertFilter(
          metadata          = "application-name",
          matchType         = "exact",
          value             = "-4",
          expectedFormNames = Set.empty
        )

        assertFilter(
          metadata          = "application-name",
          matchType         = "substring",
          value             = "-4",
          expectedFormNames = Set("test-form-1")
        )

        // Unexpected metadata and match type

        assertFilter(metadata = "app-name"        , matchType = "exact", value = s"${provider.entryName}-4", expectedStatusCode = StatusCode.BadRequest)
        assertFilter(metadata = "application-name", matchType = "exct" , value = s"${provider.entryName}-4", expectedStatusCode = StatusCode.BadRequest)

        // Form name

        assertFilter(
          metadata          = "form-name",
          matchType         = "exact",
          value             = "test-form-3",
          expectedFormNames = Set("test-form-3")
        )

        assertFilter(
          metadata          = "form-name",
          matchType         = "exact",
          value             = "-3",
          expectedFormNames = Set.empty
        )

        assertFilter(
          metadata          = "form-name",
          matchType         = "substring",
          value             = s"-3",
          expectedFormNames = Set("test-form-3")
        )

        // Form version

        assertFilter(
          metadata          = "form-version",
          matchType         = "exact",
          value             = "3",
          expectedFormNames = Set("test-form-1")
        )

        assertFilter(
          metadata          = "form-version",
          matchType         = "exact",
          value             = "5",
          expectedFormNames = Set.empty
        )

        assertFilter(
          metadata          = "form-version",
          matchType         = "gte",
          value             = "3",
          expectedFormNames = Set("test-form-1", "test-form-2")
        )

        assertFilter(
          metadata          = "form-version",
          matchType         = "gte",
          value             = "5",
          expectedFormNames = Set.empty
        )

        assertFilter(
          metadata          = "form-version",
          matchType         = "gt",
          value             = "3",
          expectedFormNames = Set("test-form-2")
        )

        assertFilter(
          metadata          = "form-version",
          matchType         = "gt",
          value             = "4",
          expectedFormNames = Set.empty
        )

        assertFilter(
          metadata          = "form-version",
          matchType         = "lt",
          value             = "3",
          expectedFormNames = Set("test-form-3", "test-form-4")
        )

        assertFilter(
          metadata          = "form-version",
          matchType         = "lt",
          value             = "1",
          expectedFormNames = Set.empty
        )

        // Creation date

        val minCreated = partialFormMetadata.flatMap(_.created).min
        val maxCreated = partialFormMetadata.flatMap(_.created).max

        assertFilter(
          metadata          = "created",
          matchType         = "gte",
          value             = minCreated.toString,
          expectedFormNames = Set("test-form-1", "test-form-2", "test-form-3", "test-form-4")
        )

        assertFilter(
          metadata          = "created",
          matchType         = "gt",
          value             = minCreated.toString,
          expectedFormNames = Set("test-form-1", "test-form-2", "test-form-4")
        )

        assertFilter(
          metadata          = "created",
          matchType         = "lt",
          value             = maxCreated.toString,
          expectedFormNames = Set("test-form-2", "test-form-3", "test-form-4")
        )

        assertFilter(
          metadata          = "created",
          matchType         = "exact",
          value             = minCreated.toString,
          expectedFormNames = Set("test-form-3")
        )

        assertFilter(
          metadata          = "created",
          matchType         = "exact",
          value             = maxCreated.toString,
          expectedFormNames = Set("test-form-1")
        )

        // Last modification date

        val minModified = partialFormMetadata.map(_.modified).min
        val maxModified = partialFormMetadata.map(_.modified).max

        assertFilter(
          metadata          = "last-modified",
          matchType         = "gte",
          value             = minModified.toString,
          expectedFormNames = Set("test-form-1", "test-form-2", "test-form-3", "test-form-4")
        )

        assertFilter(
          metadata          = "last-modified",
          matchType         = "gt",
          value             = minModified.toString,
          expectedFormNames = Set("test-form-2", "test-form-3", "test-form-4")
        )

        assertFilter(
          metadata          = "last-modified",
          matchType         = "lt",
          value             = maxModified.toString,
          expectedFormNames = Set("test-form-1", "test-form-3", "test-form-4")
        )

        assertFilter(
          metadata          = "last-modified",
          matchType         = "exact",
          value             = minModified.toString,
          expectedFormNames = Set("test-form-1")
        )

        assertFilter(
          metadata          = "last-modified",
          matchType         = "exact",
          value             = maxModified.toString,
          expectedFormNames = Set("test-form-2")
        )

        // Last modification by

        assertFilter(
          metadata          = "last-modified-by",
          matchType         = "exact",
          value             = "user-2",
          expectedFormNames = Set("test-form-3")
        )

        assertFilter(
          metadata          = "last-modified-by",
          matchType         = "exact",
          value             = "user-",
          expectedFormNames = Set.empty
        )

        assertFilter(
          metadata          = "last-modified-by",
          matchType         = "substring",
          value             = "user-",
          expectedFormNames = Set("test-form-1", "test-form-2", "test-form-3", "test-form-4")
        )

        // Title (English as default language, request language, and sort query language)

        for ((requestLanguageOpt, queryLanguageOpt) <- Seq((None, None), ("en".some, None), (None, "en".some), ("en".some, "en".some))) {
          assertFilter(
            metadata          = "title",
            requestLanguageOpt = requestLanguageOpt,
            queryLanguageOpt   = queryLanguageOpt,
            matchType         = "exact",
            value             = "English title 2",
            expectedFormNames = Set("test-form-1")
          )

          assertFilter(
            metadata          = "title",
            matchType         = "exact",
            value             = "English title",
            expectedFormNames = Set.empty
          )

          assertFilter(
            metadata          = "title",
            matchType         = "substring",
            value             = "English title",
            expectedFormNames = Set("test-form-1", "test-form-2", "test-form-3", "test-form-4")
          )
        }
        // Title (French as request language and sort query language)

        for ((requestLanguageOpt, queryLanguageOpt) <- Seq(("fr".some, None), (None, "fr".some), ("fr".some, "fr".some))) {
          assertFilter(
            metadata          = "title",
            requestLanguageOpt = requestLanguageOpt,
            queryLanguageOpt   = queryLanguageOpt,
            matchType         = "exact",
            value             = "Titre français 2",
            expectedFormNames = Set("test-form-4")
          )

          assertFilter(
            metadata           = "title",
            requestLanguageOpt = requestLanguageOpt,
            queryLanguageOpt   = queryLanguageOpt,
            matchType          = "exact",
            value              = "Titre français",
            expectedFormNames  = Set.empty
          )

          assertFilter(
            metadata           = "title",
            requestLanguageOpt = requestLanguageOpt,
            queryLanguageOpt   = queryLanguageOpt,
            matchType          = "substring",
            value              = "Titre français",
            expectedFormNames  = Set("test-form-1", "test-form-2", "test-form-3", "test-form-4")
          )
        }

        // Operations

        assertFilter(
          metadata          = "operations",
          matchType         = "exact",
          value             = "read list",
          expectedFormNames = Set("test-form-3")
        )

        // Exact + operations => operation order will make a difference (is this really useful?)
        assertFilter(
          metadata          = "operations",
          matchType         = "exact",
          value             = "list read",
          expectedFormNames = Set.empty
        )

        // Token + operations => order doesn't matter (but all tokens must be present)
        assertFilter(
          metadata          = "operations",
          matchType         = "token",
          value             = "read list",
          expectedFormNames = Set("test-form-3")
        )

        // One token is enough to match all form definitions
        assertFilter(
          metadata          = "operations",
          matchType         = "token",
          value             = "list",
          expectedFormNames = Set("test-form-1", "test-form-2", "test-form-3", "test-form-4")
        )

        // Only one form definition has the exact operation 'list'
        assertFilter(
          metadata          = "operations",
          matchType         = "exact",
          value             = "list",
          expectedFormNames = Set("test-form-1")
        )
      }
    }

    it("returns forms sorted according to sort query") {
      withProvider { provider => externalContext =>

        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        createTestForms(provider)

        // Application name (test metadata/direction case insensitivity as well)

        for (metadata <- Seq("application-name", "APPLICATION-NAME"); direction <- Seq("asc", "ASC", "AsC", "aSc")) {
          assertSort(
            metadata                = metadata,
            direction               = direction,
            expectedSortedFormNames = Seq("test-form-4", "test-form-3", "test-form-2", "test-form-1")
          )
        }

        for (metadata <- Seq("application-name", "APPLICATION-NAME"); direction <- Seq("desc", "DESC", "DeSc", "dEsC")) {
          assertSort(
            metadata                = metadata,
            direction               = direction,
            expectedSortedFormNames = Seq("test-form-1", "test-form-2", "test-form-3", "test-form-4")
          )
        }

        // Unexpected metadata and direction

        assertSort(metadata = "app-name"        , direction = "asc", expectedStatusCode = StatusCode.BadRequest)
        assertSort(metadata = "application-name", direction = "dsc", expectedStatusCode = StatusCode.BadRequest)

        // Form name

        assertSort(
          metadata                = "form-name",
          direction               = "asc",
          expectedSortedFormNames = Seq("test-form-1", "test-form-2", "test-form-3", "test-form-4")
        )

        assertSort(
          metadata                = "form-name",
          direction               = "desc",
          expectedSortedFormNames = Seq("test-form-4", "test-form-3", "test-form-2", "test-form-1")
        )

        // Form version

        assertSort(
          metadata                = "form-version",
          direction               = "asc",
          expectedSortedFormNames = Seq("test-form-3", "test-form-4", "test-form-1", "test-form-2")
        )

        assertSort(
          metadata                = "form-version",
          direction               = "desc",
          expectedSortedFormNames = Seq("test-form-2", "test-form-1", "test-form-4", "test-form-3")
        )

        // Creation date

        assertSort(
          metadata                = "created",
          direction               = "asc",
          expectedSortedFormNames = Seq("test-form-3", "test-form-2", "test-form-4", "test-form-1")
        )

        assertSort(
          metadata                = "created",
          direction               = "desc",
          expectedSortedFormNames = Seq("test-form-1", "test-form-4", "test-form-2", "test-form-3")
        )

        // Last modification date

        assertSort(
          metadata                = "last-modified",
          direction               = "asc",
          expectedSortedFormNames = Seq("test-form-1", "test-form-3", "test-form-4", "test-form-2")
        )

        assertSort(
          metadata                = "last-modified",
          direction               = "desc",
          expectedSortedFormNames = Seq("test-form-2", "test-form-4", "test-form-3", "test-form-1")
        )

        // Last modification by

        assertSort(
          metadata                = "last-modified-by",
          direction               = "asc",
          expectedSortedFormNames = Seq("test-form-2", "test-form-3", "test-form-1", "test-form-4")
        )

        assertSort(
          metadata                = "last-modified-by",
          direction               = "desc",
          expectedSortedFormNames = Seq("test-form-4", "test-form-1", "test-form-3", "test-form-2")
        )

        // Title (English as default language, request language, and sort query language)

        for ((requestLanguageOpt, queryLanguageOpt) <- Seq((None, None), ("en".some, None), (None, "en".some), ("en".some, "en".some))) {
          assertSort(
            metadata                = "title",
            requestLanguageOpt      = requestLanguageOpt,
            queryLanguageOpt        = queryLanguageOpt,
            direction               = "asc",
            expectedSortedFormNames = Seq("test-form-2", "test-form-1", "test-form-4", "test-form-3")
          )

          assertSort(
            metadata                = "title",
            requestLanguageOpt      = requestLanguageOpt,
            queryLanguageOpt        = queryLanguageOpt,
            direction               = "desc",
            expectedSortedFormNames = Seq("test-form-3", "test-form-4", "test-form-1", "test-form-2")
          )
        }

        // Title (French as request language and sort query language)

        for ((requestLanguageOpt, queryLanguageOpt) <- Seq(("fr".some, None), (None, "fr".some), ("fr".some, "fr".some))) {
          assertSort(
            metadata                = "title",
            requestLanguageOpt      = requestLanguageOpt,
            queryLanguageOpt        = queryLanguageOpt,
            direction               = "asc",
            expectedSortedFormNames = Seq("test-form-3", "test-form-4", "test-form-2", "test-form-1")
          )

          assertSort(
            metadata                = "title",
            requestLanguageOpt      = requestLanguageOpt,
            queryLanguageOpt        = queryLanguageOpt,
            direction               = "desc",
            expectedSortedFormNames = Seq("test-form-1", "test-form-2", "test-form-4", "test-form-3")
          )
        }

        // Operations (sorted by alphabetical order)

        assertSort(
          metadata                = "operations",
          direction               = "asc",
          expectedSortedFormNames = Seq("test-form-4", "test-form-2", "test-form-1", "test-form-3")
        )

        assertSort(
          metadata                = "operations",
          direction               = "desc",
          expectedSortedFormNames = Seq("test-form-3", "test-form-1", "test-form-2", "test-form-4")
        )
      }
    }

    it("returns forms paginated according to pagination query") {
      withProvider { provider => implicit externalContext =>

        implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

        createTestForms(provider)

        assertPagination(
          pageNumber                 = 1,
          pageSize                   = 1000,
          expectedPaginatedFormNames = Seq("test-form-1", "test-form-2", "test-form-3", "test-form-4")
        )

        assertPagination(
          pageNumber                 = 1,
          pageSize                   = 4,
          expectedPaginatedFormNames = Seq("test-form-1", "test-form-2", "test-form-3", "test-form-4")
        )

        assertPagination(
          pageNumber                 = 1,
          pageSize                   = 3,
          expectedPaginatedFormNames = Seq("test-form-1", "test-form-2", "test-form-3")
        )

        assertPagination(
          pageNumber                 = 2,
          pageSize                   = 3,
          expectedPaginatedFormNames = Seq("test-form-4")
        )

        assertPagination(
          pageNumber                 = 1,
          pageSize                   = 2,
          expectedPaginatedFormNames = Seq("test-form-1", "test-form-2")
        )

        assertPagination(
          pageNumber                 = 2,
          pageSize                   = 2,
          expectedPaginatedFormNames = Seq("test-form-3", "test-form-4")
        )

        assertPagination(
          pageNumber                 = 1,
          pageSize                   = 1,
          expectedPaginatedFormNames = Seq("test-form-1")
        )

        assertPagination(
          pageNumber                 = 2,
          pageSize                   = 1,
          expectedPaginatedFormNames = Seq("test-form-2")
        )

        assertPagination(
          pageNumber                 = 3,
          pageSize                   = 1,
          expectedPaginatedFormNames = Seq("test-form-3")
        )

        assertPagination(
          pageNumber                 = 4,
          pageSize                   = 1,
          expectedPaginatedFormNames = Seq("test-form-4")
        )

        // Incorrect page number and page size
        assertPagination(pageNumber =  0, pageSize =  1, expectedStatusCode = StatusCode.BadRequest)
        assertPagination(pageNumber = -1, pageSize =  1, expectedStatusCode = StatusCode.BadRequest)
        assertPagination(pageNumber =  1, pageSize =  0, expectedStatusCode = StatusCode.BadRequest)
        assertPagination(pageNumber =  1, pageSize = -1, expectedStatusCode = StatusCode.BadRequest)

        assertPaginationWithStrings(pageNumber = "string", pageSize =  "1"     , expectedStatusCode = StatusCode.BadRequest)
        assertPaginationWithStrings(pageNumber = "1"     , pageSize =  "string", expectedStatusCode = StatusCode.BadRequest)
      }
    }

    it("serializes and deserializes requests and responses") {
      withProvider { provider => implicit externalContext =>

        // Form request

        val formRequest = FormRequest(
          compatibilityMode       = false,
          allForms                = true,
          ignoreAdminPermissions  = false,
          languageOpt             = "en".some,
          remoteServerCredentials = Map(
            "https://localhost:8081" -> BasicCredentials("user-1", "pass-1".some, preemptiveAuth = true, None),
            "https://localhost:8082" -> BasicCredentials("user-2", "pass-2".some, preemptiveAuth = true, None)
          ),
          filterQueries           = Seq(FilterQuery.latestVersionsQuery),
          sortQuery               = SortQuery.defaultSortQuery,
          paginationOpt           = Pagination(pageNumber = 1, pageSize = 10).some
        )

        val deserializedFormRequest = FormRequest(formRequest.toXML)

        assert(deserializedFormRequest == formRequest)

        // Form response

        val formResponse = FormResponse(
          forms = List(Form(
            appForm          = AppForm("app", "form"),
            version          = FormDefinitionVersion.Specific(42),
            localMetadataOpt = FormMetadata(
              lastModifiedTime  = Instant.now,
              lastModifiedByOpt = "user-3".some,
              created           = Some(Instant.now.plus(1, ChronoUnit.HOURS)),
              title             = Map("en" -> "Title"),
              available         = true,
              permissionsOpt    = None, // assert won't compare NodeInfo correctly
              operations        = OperationsList(ops = List("list", "read"))
            ).some,
            remoteMetadata   = Map("https://localhost:8083" -> FormMetadata(
              lastModifiedTime  = Instant.now.plus(2, ChronoUnit.HOURS),
              lastModifiedByOpt = "user-4".some,
              created           = Some(Instant.now.plus(3, ChronoUnit.HOURS)),
              title             = Map("fr" -> "Titre"),
              available         = false,
              permissionsOpt    = None, // assert won't compare NodeInfo correctly
              operations        = OperationsList(ops = List("delete", "read"))
            ))
          )),
          searchTotal = 99
        )

        val deserializedFormResponse = FormResponse(formResponse.toXML)

        assert(deserializedFormResponse == formResponse)
      }
    }
  }
}
