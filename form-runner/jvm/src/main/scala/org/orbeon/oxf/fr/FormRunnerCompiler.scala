package org.orbeon.oxf.fr

import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.fr.Names.FormInstance
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.util._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.XFormsStaticStateSerializer.ResourcesToInclude
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.model.XFormsInstanceSupport
import org.orbeon.oxf.xforms.processor.XFormsCompiler
import org.orbeon.oxf.xml.XMLReceiver

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
          val formVersion = params.getRootElement.element("form-version").getText

          val (jsonString, staticState) = XFormsCompiler.compile(formDocument)

          val attachments =
            staticState.topLevelPart.findControlAnalysis(FormInstance) collect {
              case instance: Instance =>

                val basePath = FormRunner.createFormDefinitionBasePath(appName, formName)

                val dataDoc =
                  XFormsInstanceSupport.extractDocument(
                    instance.inlineRootElemOpt.get, // FIXME: `get`
                    instance.excludeResultPrefixes,
                    instance.readonly,
                    instance.exposeXPathTypes,
                    removeInstanceData = false
                  )

                FormRunner.collectAttachments(
                  data             = dataDoc,
                  fromBasePath     = basePath,
                  toBasePath       = basePath,
                  forceAttachments = true
                ) map (_.holder.getStringValue.trimAllToEmpty)
            }

          println(s"xxxx attachments: ${attachments mkString ", "}")

          val useZipFormat =
            ec.getRequest.getFirstParamAsString("format").contains("zip")

          if (useZipFormat) {
            val chos = new ContentHandlerOutputStream(xmlReceiver, true)
            val zos  = new ZipOutputStream(chos)

            chos.setContentType("application/zip")

            // Write form JSON
            locally {
              val entry = new ZipEntry(s"/$appName/$formName/$formVersion/form/form.json")
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
            attachments.iterator.flatten ++ ResourcesToInclude foreach { pathOrUrl =>
              ConnectionResult.withSuccessConnection(connect(pathOrUrl), closeOnSuccess = true) { is =>
                val entry = new ZipEntry(new URI(pathOrUrl).normalize.getPath)
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