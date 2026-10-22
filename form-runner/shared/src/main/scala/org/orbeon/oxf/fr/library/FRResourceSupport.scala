/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fr.library

import cats.syntax.option.*
import org.orbeon.oxf.fr.{AppForm, FormRunnerCommon, FormRunnerParamsOpt}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.xxforms.{ComponentParamSupport, XXFormsLangSupport}


object FRResourceSupport {

  def r(
    resourceKey        : String,
    instanceOpt        : Option[String],
    fallbackLangOpt    : Option[String] = None,
    propertySetOpt     : Option[PropertySet] = None
  )(implicit
    xfc                : XFormsFunction.Context
  ): Option[String] = {

    // xxx TODO: clarify where we should get appform from
    val appFormOpt = {

      def appFormFromMetadataOpt =
        xfc.container.partAnalysis.ancestorOrSelfIterator
          .flatMap(FRComponentParamSupport.findConstantMetadataRootElem)
          .flatMap(FRComponentParamSupport.nonBlankAppFormFromMetadata)
          .nextOption()

      def appFormFromParamsOpt =
        FormRunnerParamsOpt()
          .map(p => AppForm(p.app, p.form))
          .filter(appForm => appForm.app.nonAllBlank && appForm.form.nonAllBlank)

      appFormFromMetadataOpt.orElse(appFormFromParamsOpt)
    }

    val componentControlOpt = ComponentParamSupport.findSourceComponent(None)

    val isComponentLocal: Boolean =
      componentControlOpt.isDefined && (instanceOpt.isEmpty || instanceOpt.contains("orbeon-resources"))

    def noOverrideFallback =
      XXFormsLangSupport.r(
        resourceKey        = resourceKey,
        instanceOpt        = instanceOpt,
        javaNamedParamsOpt = None,
        fallbackLangOpt    = fallbackLangOpt
      )

    appFormOpt match {
      case Some(appForm) if isComponentLocal =>

        val directNameOpt = componentControlOpt.flatMap { cc =>
          cc.staticControl.commonBinding.directName.orElse(cc.staticControl.element.getQName.some)
        }
        val componentNameOpt =
          directNameOpt.map(_.qualifiedName)

        componentNameOpt.flatMap { compName =>

          def findRequestedLang: Option[String] =
            XFormsFunction.elementAnalysisForSource
              .flatMap(elementAnalysis => XXFormsLangSupport.resolveXMLangHandleAVTs(xfc.containingDocument, elementAnalysis))
              .orElse(fallbackLangOpt)

          val effectiveLang      = findRequestedLang.getOrElse(FormRunnerCommon.frc.getDefaultLang(appFormOpt))
          val propertyProfileOpt = xfc.containingDocument.staticState.propertyProfileOpt
          val properties         = propertySetOpt.getOrElse(CoreCrossPlatformSupport.properties)
          val propName           = s"oxf.fr.resource.${appForm.app}.${appForm.form}.$effectiveLang.xbl.$compName.$resourceKey"

          properties
            .getNonBlankString(propName, propertyProfileOpt)
            .orElse(noOverrideFallback)
        }
      case _ =>
        noOverrideFallback
    }
  }
}
