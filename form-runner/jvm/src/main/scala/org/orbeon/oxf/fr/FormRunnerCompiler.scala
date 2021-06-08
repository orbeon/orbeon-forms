package org.orbeon.oxf.fr

import cats.syntax.option._
import io.circe.generic.auto._
import io.circe.syntax._
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.fr.Names.{FormInstance, FormResources}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.util._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.model.{Instance, Submission}
import org.orbeon.oxf.xforms.model.XFormsInstanceSupport
import org.orbeon.oxf.xforms.processor.XFormsCompiler
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.ManifestEntry

import java.net.URI
import java.util.zip.{ZipEntry, ZipOutputStream}


class FormRunnerCompiler extends ProcessorImpl {

  private val Logger = LoggerFactory.createLogger(classOf[FormRunnerCompiler])
  private implicit val indentedLogger: IndentedLogger = new IndentedLogger(Logger)

  override def createOutput(outputName: String): ProcessorOutput =
    addOutput(
      outputName,
      new ProcessorOutputImpl(FormRunnerCompiler.this, outputName) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          implicit val rcv = xmlReceiver
          implicit val ec  = CoreCrossPlatformSupport.externalContext

          val params       = readCacheInputAsDOM4J(pipelineContext, "instance")
          val formDocument = readCacheInputAsDOM4J(pipelineContext, "data")

          val appName     = params.getRootElement.element("app").getText
          val formName    = params.getRootElement.element("form").getText
          val formVersion = params.getRootElement.element("form-version").getText.trimAllToOpt.getOrElse("1")

          val (jsonString, staticState) = XFormsCompiler.compile(formDocument)

          val cacheableResourcesToInclude = {

            // Find all the languages used and return a comma-separated list of them
            val usedLangsOpt =
              staticState.topLevelPart.findControlAnalysis(FormResources) collect {
                case instance: Instance =>
                  instance.inlineRootElemOpt.get.elementIterator() map
                    (_.attributeValue(XMLNames.XMLLangQName))      mkString
                    ","
              }

            val urlOptIt =
              staticState.topLevelPart.iterateControls collect {
                case instance: Instance
                  if instance.readonly &&
                     instance.cache    &&
                     instance.dependencyURL.isDefined =>
                  instance.dependencyURL
                case submission: Submission
                  if submission.avtXxfReadonlyOpt.contains("true") &&
                     submission.avtXxfCacheOpt.contains("true")    &&
                     submission.avtMethod.exists(s => HttpMethod.withNameInsensitiveOption(s).contains(HttpMethod.GET)) =>

                  if (submission.avtActionOrResource == "/fr/service/i18n/fr-resources/{$app}/{$form}")
                    PathUtils.recombineQuery("/fr/service/i18n/fr-resources/orbeon/offline", usedLangsOpt.map("langs" ->)).some
                  else
                    submission.avtActionOrResource.some
              }

            val entriesIt =
              urlOptIt.flatten map { uri =>
                ManifestEntry(new URI(uri), ContentTypes.XmlContentType)
              }

            entriesIt.toList
          }

          val formInstanceDataAttachments = {

            val opt =
              staticState.topLevelPart.findControlAnalysis(FormInstance) collect {
                case instance: Instance =>

                  val fbBasePath             = "/fr/service/persistence/crud/orbeon/builder/data/"
                  val topLevelBasePath       = FormRunner.createFormDefinitionBasePath(appName, formName)
                  val appLibraryBasePath     = FormRunner.createFormDefinitionBasePath(appName, "library")
                  val orbeonLibraryBasePath  = FormRunner.createFormDefinitionBasePath("orbeon", "library")

                  val dataDoc =
                    XFormsInstanceSupport.extractDocument(
                      instance.inlineRootElemOpt.get, // FIXME: `get`
                      instance.excludeResultPrefixes,
                      instance.readonly,
                      instance.exposeXPathTypes,
                      removeInstanceData = false
                    )

                  def collectAttachmentsFor(basePath: String) =
                    FormRunner.collectAttachments(
                      data             = dataDoc,
                      fromBasePath     = basePath,
                      toBasePath       = basePath,
                      forceAttachments = true
                    )

                  (
                    collectAttachmentsFor(fbBasePath)         :::
                    collectAttachmentsFor(topLevelBasePath)   :::
                    collectAttachmentsFor(appLibraryBasePath) :::
                    collectAttachmentsFor(orbeonLibraryBasePath)
                  ) map { awh =>
                    val holder    = awh.holder
                    val uri       = holder.getStringValue.trimAllToEmpty
                    val mediatype = awh.holder.attValue("mediatype")
                    ManifestEntry(new URI(uri), mediatype)
                  }
              }

            opt.toList.flatten
          }

          val distinctResources = (formInstanceDataAttachments ::: cacheableResourcesToInclude).distinct

          val useZipFormat =
            ec.getRequest.getFirstParamAsString("format").contains("zip")

          if (useZipFormat) {
            val chos = new ContentHandlerOutputStream(xmlReceiver, true)
            val zos  = new ZipOutputStream(chos)

            chos.setContentType("application/zip")

            val jsonFormPath = s"_forms/$appName/$formName/$formVersion/form/form.json"

            // Generate and write manifest
            locally {

              val manifest =
                (
                  ManifestEntry(jsonFormPath, jsonFormPath, ContentTypes.XmlContentType) :: distinctResources
                ).asJson.noSpaces

              val entry = new ZipEntry(ManifestEntry.JsonFilename)
              zos.putNextEntry(entry)
              zos.write(manifest.getBytes(CharsetNames.Utf8))
            }

            // Write form JSON
            locally {
              val entry = new ZipEntry(jsonFormPath)
              zos.putNextEntry(entry)
              zos.write(jsonString.getBytes(CharsetNames.Utf8))
            }

            def connect(path: String): ConnectionResult = {

              val resolvedAbsoluteUrl =
                URLRewriterUtils.rewriteServiceURL(
                  ec.getRequest,
                  path,
                  URLRewriter.REWRITE_MODE_ABSOLUTE
                )

              Connection.connectNow(
                method          = GET,
                url             = new URI(resolvedAbsoluteUrl),
                credentials     = None,
                content         = None,
                headers         = Map(), // TODO: form version for Form Runner attachments
                loadState       = true,
                saveState       = true,
                logBody         = false
              )
            }

            // Write static attachments and other resources
            distinctResources.iterator foreach { manifestEntry =>
              ConnectionResult.withSuccessConnection(connect(manifestEntry.uri), closeOnSuccess = true) { is =>
                val entry = new ZipEntry(manifestEntry.zipPath)
                zos.putNextEntry(entry)
                IOUtils.copyStreamAndClose(is, zos, doCloseOut = false)
              }
            }

            zos.close()
          } else {
            XFormsCompiler.outputJson(jsonString)
          }
        }
      }
    )
}