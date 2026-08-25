package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.contentfilter.ProfanityFilter
import org.orbeon.oxf.xforms.contentfilter.api.java.{ContentFilterProvider, ContentFilterResult}
import org.slf4j.{Logger, LoggerFactory}

import java.util as ju
import scala.jdk.CollectionConverters.*


class ProfanityContentFilterProvider extends ContentFilterProvider {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private var profanityFilter: ProfanityFilter = _

  logger.info("Instantiating ProfanityContentFilterProvider")

  override def init(): Unit = {
    logger.info("Initializing ProfanityContentFilterProvider with ProfanityFilter")
    profanityFilter = ProfanityFilter.defaultFilter()
  }

  override def destroy(): Unit = {
    logger.info("Destroying ProfanityContentFilterProvider")
    profanityFilter = null
  }

  override def filterContent(
    text     : String,
    language : String,
    extension: ju.Map[String, AnyRef]
  ): ContentFilterResult = {

    if (text.isAllBlank) {
      new ContentFilterResult.AcceptResult()
    } else {

      val filter = if (profanityFilter != null) profanityFilter else ProfanityFilter.defaultFilter()

      filter.findAllMatches(text) match {
        case Nil =>
          new ContentFilterResult.AcceptResult()
        case matches =>
          new ContentFilterResult.RejectResult(
            "The text contains forbidden/profane words.",
            matches.map { m => new ContentFilterResult.Match(m.start, m.end, m.word) }.asJava
          )
      }
    }
  }
}
