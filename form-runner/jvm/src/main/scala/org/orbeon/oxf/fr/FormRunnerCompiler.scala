package org.orbeon.oxf.fr

import cats.syntax.option._
import io.circe.generic.auto._
import io.circe.syntax._
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataPath, FormPath}
import org.orbeon.oxf.fr.Names.{FormInstance, FormResources, MetadataInstance}
import org.orbeon.oxf.fr.library.FRComponentParamSupport
import org.orbeon.oxf.fr.persistence.proxy.Export
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
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

          implicit val rcv                      = xmlReceiver
          implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

          val params       = readCacheInputAsOrbeonDom(pipelineContext, "instance")
          val formDocument = readCacheInputAsOrbeonDom(pipelineContext, "data")

          val appName     = params.getRootElement.element("app").getText
          val formName    = params.getRootElement.element("form").getText
          val formVersion = params.getRootElement.element("form-version").getText.trimAllToOpt.getOrElse("1")

          val (jsonString, staticState) = XFormsCompiler.compile(formDocument)

          val cacheableResourcesToIncludeManifests = {

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
                ManifestEntry(URI.create(uri), ContentTypes.XmlContentType.some)
              }

            entriesIt.toList
          }

          val (orbeonLibraryVersionOpt, appLibraryVersionOpt) = {

            val libraryVersionsOpt =
              staticState.topLevelPart.findControlAnalysis(MetadataInstance) collect {
                case instance: Instance => instance.constantContent
              } collect {
                case Some(metadataRoot) => FRComponentParamSupport.findLibraryVersions(metadataRoot.rootElement)
              }

            libraryVersionsOpt match {
              case Some(versions) => versions
              case None           => (None, None)
            }
          }

          // This also looks for attachments in datasets. Usually, dataset instances are not available
          // in the form definition. However, it is possible to manually create a form definition with
          // an embedded dataset. We might even provide facilities to do this in the future. And if that
          // is the case, we allow datasets referring to attachments, in which case we collect those as
          // well in addition to attachments referred to by the main form data.
          val formDataAndDatasetInstanceAttachmentManifests = {

            val manifestsIt =
              staticState.topLevelPart.iterateModels flatMap (_.instances.valuesIterator) collect {
                case  i if i.staticId == FormInstance           => i
                case  i if i.staticId.startsWith("fr-dataset-") => i
              } map { instance =>

                val basePaths =
                  List(
                    FormRunner.createFormDataBasePath(AppForm.FormBuilder.app, AppForm.FormBuilder.form, isDraft = false, documentIdOrEmpty = ""),
                    FormRunner.createFormDefinitionBasePath(appName, formName),
                    FormRunner.createFormDefinitionBasePath(appName, Names.LibraryFormName),
                    FormRunner.createFormDefinitionBasePath(Names.GlobalLibraryAppName, Names.LibraryFormName)
                  )

                val dataDoc =
                  XFormsInstanceSupport.extractDocument(
                    instance.inlineRootElemOpt.get, // FIXME: `get`
                    instance.excludeResultPrefixes,
                    instance.readonly,
                    instance.exposeXPathTypes,
                    removeInstanceData = false
                  )

                FormRunner.collectAttachments(
                  data            = dataDoc,
                  attachmentMatch = FormRunner.AttachmentMatch.BasePaths(includes = basePaths, excludes = Nil)
                ) map { case FormRunner.AttachmentWithHolder(fromPath, holder) =>
                  ManifestEntry(URI.create(fromPath), holder.attValueOpt("mediatype").flatMap(_.trimAllToOpt))
                }
            }

            manifestsIt.flatten.toList
          }

          // NOTE: Some resources could be the same but with different of missing `contentType`. We must ensure that
          // the zip paths are unique, so we keep distinct resources based on that criteria.
          val distinctResources =
            (formDataAndDatasetInstanceAttachmentManifests ::: cacheableResourcesToIncludeManifests).keepDistinctBy(_.zipPath)

          val useZipFormat =
            coreCrossPlatformSupport.externalContext.getRequest.getFirstParamAsString("format").contains("zip")

          if (useZipFormat) {
            val chos = new ContentHandlerOutputStream(xmlReceiver, true)
            val zos  = new ZipOutputStream(chos)

            chos.setContentType("application/zip")

            val jsonFormPath = s"_forms/$appName/$formName/$formVersion/form/form.json"

            // Generate and write manifest
            locally {

              val manifest =
                (
                  ManifestEntry(jsonFormPath, jsonFormPath, ContentTypes.XmlContentType.some) :: distinctResources
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

            // Write static attachments and other resources
            distinctResources.iterator foreach { manifestEntry =>
              Export.readPersistenceContentToZip(
                zos         = zos,
                formVersionOpt =
                  manifestEntry.uri match {
                    case FormPath(_, Names.GlobalLibraryAppName, Names.LibraryFormName, _)               => orbeonLibraryVersionOpt
                    case FormPath(_, _,                          Names.LibraryFormName, _)               => appLibraryVersionOpt
                    case DataPath(_, AppForm.FormBuilder.app,    AppForm.FormBuilder.form, "data", _, _) => formVersion.toInt.some
                    case _                                                                               => None
                  },
                fromPath    = manifestEntry.uri,
                zipPath     = manifestEntry.zipPath,
                debugAction = "compiling"
              )
            }

            zos.close()
          } else {
            XFormsCompiler.outputJson(jsonString)
          }
        }
      }
    )
}
