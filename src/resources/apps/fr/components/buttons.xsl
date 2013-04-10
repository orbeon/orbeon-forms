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

    <!-- Buttons that translate to fr:process-button -->
    <xsl:template match="fr:home-button
                       | fr:summary-button
                       | fr:close-button
                       | fr:save-final-button
                       | fr:save-draft-button
                       | fr:validate-button
                       | fr:review-button
                       | fr:edit-button
                       | fr:send-button
                       | fr:email-button
                       | fr:collapse-all-button
                       | fr:expand-all-button
                       | fr:save-button
                       | fr:submit-button
                       | fr:workflow-review-button
                       | fr:workflow-edit-button
                       | fr:workflow-send-button
                       | fr:*[starts-with(local-name(), 'custom-')]">
        <xsl:variable name="button-name" select="substring-before(local-name(), '-button')"/>
        <fr:process-button
            name="{$button-name}"
            ref="xxf:instance('fr-triggers-instance')/{if ($button-name = 'workflow-edit') then 'workflow-edit' else 'other'}">
            <xsl:copy-of select="@appearance"/>
        </fr:process-button>
    </xsl:template>

    <xsl:template match="fr:refresh-button">
        <!-- Display a "refresh" button only in noscript mode -->
        <xf:group ref=".[property('xxf:noscript')]">
            <xf:trigger class="fr-refresh-button">
                <xsl:copy-of select="@appearance"/>
                <xf:label mediatype="text/html" value="$fr-resources/detail/buttons/refresh"/>
                <!-- NOP -->
            </xf:trigger>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:clear-button">
        <xf:trigger id="fr-clear-button" class="fr-clear-button" model="fr-persistence-model" ref="xxf:instance('fr-triggers-instance')/other">
            <xsl:copy-of select="@appearance"/>
            <xf:label mediatype="text/html" value="$fr-resources/detail/buttons/clear"/>
            <xf:action ev:event="DOMActivate">
                <!-- Open confirmation dialog -->
                <xxf:show if="not(property('xxf:noscript'))" dialog="fr-clear-confirm-dialog"/>
                <!-- Restore directly -->
                <xf:dispatch if="property('xxf:noscript')" name="fr-clear" targetid="fr-persistence-model"/>
            </xf:action>
        </xf:trigger>
    </xsl:template>

    <!-- NOTE: This is the detail page's PDF button (not the summary page's) -->
    <xsl:template match="fr:pdf-button">
        <!-- NOTE: Only the XForms document id is strictly needed. Keep app/form/document for filtering purposes. -->
        <!-- Don't show the PDF template button in CE (also on summary page) -->
        <xsl:if test="not($has-pdf-template and not($is-pe))">
            <xsl:variable name="pdf-disable-if-invalid" select="p:property(string-join(('oxf.fr.detail.pdf.disable-if-invalid', $app, $form), '.'))" as="xs:boolean"/>
            <fr:href-button
                    model="fr-persistence-model"
                    href="/fr/service/{$app}/{$form}/pdf/{{xxf:instance('fr-parameters-instance')/document}}/{{xxf:document-id()}}.pdf">
                <xsl:copy-of select="@appearance"/>
                <xsl:if test="$pdf-disable-if-invalid">
                    <xsl:attribute name="ref">instance('fr-triggers-instance')/strict-submit</xsl:attribute>
                </xsl:if>
                <xf:label mediatype="text/html" value="$fr-resources/detail/buttons/pdf"/>
            </fr:href-button>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:save-locally-button">
        <xf:trigger id="fr-save-locally-button" class="fr-save-locally-button" ref="xxf:instance('fr-triggers-instance')/other">
            <xf:label mediatype="text/html" value="$fr-resources/detail/buttons/save-locally"/>
        </xf:trigger>
    </xsl:template>

</xsl:stylesheet>
