<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:stylesheet version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:exf="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:f="http://orbeon.org/oxf/xml/formatting">

    <!-- 2015-08-07: This is probably here for backward compatibility only. -->

    <!-- Buttons that translate to fr:process-button -->
    <!-- NOTE: Lower priority so that rules below match. -->
    <!-- NOTE: Be sure to exclude existing fr:*-button XBL components. This is not ideal. Maybe we can skip this step
         whereby we first generate fr:*-button elements and then match on them here. Would remain the case of manually
         placing buttons like fr:buttons/fr:save. -->
    <xsl:template match="fr:*[ends-with(local-name(), '-button') and not(local-name() = ('href-button', 'process-button', 'select1-button'))]" priority="-20">
        <xsl:variable name="button-name" select="substring-before(local-name(), '-button')"/>
        <!-- FIXME: We need a better way to configure button visibility/readonly. Currently, this is hardcoded below.
             For the wizard, we even rely on the fact that internal wizard controls are in XBL outer scope. See also:
             https://github.com/orbeon/orbeon-forms/issues/940 -->
        <fr:process-button
            id="fr-button-{$button-name}"
            name="{$button-name}"
            ref="xxf:instance('fr-triggers-instance')/{if ($button-name = 'summary')
                                                       then 'can-access-summary'
                                                       else if ($button-name = 'wizard-prev')
                                                       then 'xxf:binding(''fr-wizard-prev'')'
                                                       else if ($button-name = 'wizard-next')
                                                       then 'xxf:binding(''fr-wizard-next'')'
                                                       else 'other'}">
            <xsl:copy-of select="@appearance | @model | @context | @ref | @bind"/>
        </fr:process-button>
    </xsl:template>

    <!-- NOTE: This is the detail page's PDF button (not the summary page's) -->
    <xsl:template match="fr:pdf-button">
        <!-- NOTE: Only the XForms document id is strictly needed. Keep app/form/document for filtering purposes. -->
        <fr:href-button
            xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
            model="fr-persistence-model"
            ref="instance('fr-triggers-instance')/pdf"
            href=
                "/fr/service/{
                    $app
                }/{
                    $form
                }/pdf/{{
                    string-join(
                        (
                            xxf:instance('fr-parameters-instance')/document/string(),
                            xxf:document-id(),
                            frf:filenameOrNull('pdf')[. != '']
                        ),
                        '/'
                    )
                }}.pdf">
            <xsl:copy-of select="@appearance | @ref | @class" />
            <xf:label mediatype="text/html" model="fr-form-model" value="$fr-resources/buttons/pdf"/>
        </fr:href-button>
    </xsl:template>

</xsl:stylesheet>
