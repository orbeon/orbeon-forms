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
        xmlns:f="http://orbeon.org/oxf/xml/formatting">

    <xsl:template match="fr:refresh-button">
        <!-- Display a "refresh" button only in noscript mode -->
        <xf:group ref=".[property('xxf:noscript')]">
            <xf:trigger id="fr-refresh-button">
                <xsl:copy-of select="@appearance"/>
                <xf:label>
                    <xh:img width="11" height="16" src="/apps/fr/style/images/silk/arrow_refresh.png" alt="{{$fr-resources/summary/labels/refresh}}"/>
                </xf:label>
                <xf:action ev:event="DOMActivate">
                    <!-- NOP -->
                </xf:action>
            </xf:trigger>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:close-button">
        <xf:trigger id="fr-back-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
            <xsl:copy-of select="@appearance"/>
            <xf:label>
                <xh:img width="11" height="16" src="/apps/fr/style/close.gif" alt=""/>
                <xf:output value="$fr-resources/detail/labels/close"/>
            </xf:label>
            <xf:action ev:event="DOMActivate">
                <xf:dispatch targetid="fr-navigation-model" name="fr-goto-summary"/>
            </xf:action>
        </xf:trigger>
        <!-- Trigger shown to go back if the data is dirty -->
        <!--<xf:trigger ref="instance('fr-persistence-instance')[data-status = 'dirty']">-->
            <!--<xf:label>-->
                <!--<xh:img width="16" height="16" src="/apps/fr/style/images/silk/house.png" alt=""/>-->
                <!--<xf:output value="$fr-resources/detail/labels/discard"/>-->
            <!--</xf:label>-->
            <!--<xf:action ev:event="DOMActivate">-->
                <!--<xf:dispatch targetid="fr-navigation-model" name="fr-goto-summary"/>-->
            <!--</xf:action>-->
        <!--</xf:trigger>-->
        <!-- Trigger shown to go back if the data is clean -->
        <!--<xf:trigger ref="instance('fr-persistence-instance')[data-status = 'clean']">-->
            <!--<xf:label>-->
                <!--<xh:img width="16" height="16" src="/apps/fr/style/images/silk/house.png" alt=""/>-->
                <!--<xf:output value="$fr-resources/detail/labels/return"/>-->
            <!--</xf:label>-->
            <!--<xf:action ev:event="DOMActivate">-->
                <!--<xf:dispatch targetid="fr-navigation-model" name="fr-goto-summary"/>-->
            <!--</xf:action>-->
        <!--</xf:trigger>-->
    </xsl:template>

    <xsl:template match="fr:clear-button">
        <xf:trigger id="fr-clear-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
            <xsl:copy-of select="@appearance"/>
            <xf:label><xh:img width="16" height="16" src="/apps/fr/style/clear.gif" alt=""/>
                <xf:output value="$fr-resources/detail/labels/clear"/>
            </xf:label>
            <xf:action ev:event="DOMActivate">
                <xf:setvalue ref="xxf:instance('errors-state')/submitted">true</xf:setvalue>
                <!-- Open confirmation dialog -->
                <xxf:show if="not(property('xxf:noscript'))" dialog="fr-clear-confirm-dialog"/>
                <!-- Restore directly -->
                <xf:dispatch if="property('xxf:noscript')" name="fr-clear" targetid="fr-persistence-model"/>
            </xf:action>
        </xf:trigger>
    </xsl:template>

    <xsl:template match="fr:print-button">
        <!-- TODO: bind to strict-submit, but maybe fr-print-submission should check validity instead -->
        <xf:trigger id="fr-print-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/strict-submit">
            <xf:label>
                <xh:img width="16" height="16" src="/apps/fr/style/images/silk/printer.png" alt=""/>
                <xf:output value="$fr-resources/detail/labels/print"/>
            </xf:label>
            <xf:action ev:event="DOMActivate">
                <xf:send submission="fr-print-submission"/>
            </xf:action>
        </xf:trigger>
    </xsl:template>

    <!-- NOTE: This is the detail page's PDF button (not the summary page's) -->
    <xsl:template match="fr:pdf-button">
        <!-- TODO: bind to strict-submit, but maybe fr-pdf-submission should check validity instead -->
        <!-- NOTE: Only the XForms document id is strictly needed. Keep app/form/document for filtering purposes. -->
        <!-- Don't show the PDF template button in CE (also on summary page) -->
        <xsl:if test="not($has-pdf-template and not($is-pe))">
            <fr:href-button
                    model="fr-persistence-model"
                    ref="instance('fr-triggers-instance')/strict-submit"
                    href="/fr/service/{$app}/{$form}/pdf/{{xxf:instance('fr-parameters-instance')/document}}/{{xxf:document-id()}}.pdf">
                <xsl:copy-of select="@appearance"/>
                <xf:label>
                    <xh:img width="16" height="16" src="/apps/fr/style/pdf.png" alt=""/>
                    <xf:output value="$fr-resources/detail/labels/print-pdf"/>
                </xf:label>
            </fr:href-button>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:save-button">
        <xf:trigger id="fr-save-button" xxf:modal="true" model="fr-persistence-model" ref="instance('fr-triggers-instance')/save">
            <xsl:copy-of select="@appearance"/>
            <xf:label>
                <xh:img width="16" height="16" src="/apps/fr/style/images/silk/database_save.png" alt=""/>
                <xf:output value="$fr-resources/detail/labels/save-document"/>
            </xf:label>
        </xf:trigger>
    </xsl:template>

    <xsl:template match="fr:save-locally-button">
        <xf:trigger id="fr-save-locally-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
            <xf:label>
                <xh:img width="16" height="16" src="/apps/fr/style/images/silk/disk.png" alt=""/>
                <xf:output value="$fr-resources/detail/labels/save-locally"/>
            </xf:label>
        </xf:trigger>
    </xsl:template>

    <xsl:template match="fr:submit-button">
        <xf:trigger xxf:modal="true" id="fr-submit-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/submit">
            <xsl:copy-of select="@appearance"/>
            <xf:label>
                <xh:img width="16" height="16" src="/apps/fr/style/images/silk/application_go.png" alt=""/>
                <xf:output value="$fr-resources/detail/labels/submit-document"/>
            </xf:label>
        </xf:trigger>
    </xsl:template>

    <xsl:template match="fr:workflow-review-button">
        <xf:trigger xxf:modal="true" id="fr-workflow-review-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/workflow-review">
            <xsl:copy-of select="@appearance"/>
            <xf:label>
                <xh:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/right_16.png" alt=""/>
                <xf:output value="$fr-resources/detail/labels/workflow-review"/>
            </xf:label>
        </xf:trigger>
    </xsl:template>

    <xsl:template match="fr:workflow-edit-button">
        <xf:trigger xxf:modal="true" id="fr-workflow-edit-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/workflow-edit">
            <xsl:copy-of select="@appearance"/>
            <xf:label>
                <xh:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/left_16.png" alt=""/>
                <xf:output value="$fr-resources/detail/labels/workflow-edit"/>
            </xf:label>
        </xf:trigger>
    </xsl:template>

    <xsl:template match="fr:workflow-send-button">
        <xf:trigger xxf:modal="true" id="fr-workflow-send-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/workflow-send">
            <xsl:copy-of select="@appearance"/>
            <xf:label>
                <xh:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/right_16.png" alt=""/>
                <xf:output value="$fr-resources/detail/labels/workflow-send"/>
            </xf:label>
        </xf:trigger>
    </xsl:template>

    <xsl:template match="fr:email-button">
        <!-- Don't show this button in noscript mode -->
        <!-- TODO: bind to strict-submit, but maybe fr-email-service-submission should check validity instead -->
        <xf:trigger xxf:modal="true" id="fr-email-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/strict-submit">
            <xsl:copy-of select="@appearance"/>
            <xf:label>
                <!--<xh:img width="16" height="16" src="/apps/fr/style/images/silk/email.png" alt=""/>-->
                <xh:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/letter_16.png" alt=""/>
                <xf:output value="$fr-resources/detail/labels/email"/>
            </xf:label>
            <xf:action ev:event="DOMActivate">
                <xf:send submission="fr-email-service-submission"/>
            </xf:action>
        </xf:trigger>
    </xsl:template>

    <xsl:template match="fr:collapse-all-button">
        <xsl:if test="$is-section-collapse">
            <xf:trigger id="fr-collapse-all-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
                <xsl:copy-of select="@appearance"/>
                <xf:label>
                    <xh:img width="16" height="16" src="/apps/fr/style/images/silk/arrow_in.png" alt="{{$fr-resources/detail/labels/collapse-all}}"/>
                </xf:label>
                <xf:dispatch ev:event="DOMActivate" name="fr-collapse-all" targetid="fr-sections-model"/>
            </xf:trigger>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:expand-all-button">
        <xsl:if test="$is-section-collapse">
            <xf:trigger id="fr-expand-all-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
                <xsl:copy-of select="@appearance"/>
                <xf:label>
                    <xh:img width="16" height="16" src="/apps/fr/style/images/silk/arrow_out.png" alt="{{$fr-resources/detail/labels/expand-all}}"/>
                </xf:label>
                <xf:dispatch ev:event="DOMActivate" name="fr-expand-all" targetid="fr-sections-model"/>
            </xf:trigger>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
