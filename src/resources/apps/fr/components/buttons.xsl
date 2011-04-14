<?xml version="1.0" encoding="UTF-8"?>
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
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:exforms="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:f="http://orbeon.org/oxf/xml/formatting"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <xsl:template match="fr:refresh-button">
        <!-- Display a "refresh" button only in noscript mode -->
        <xforms:group ref=".[property('xxforms:noscript')]">
            <fr:button id="fr-refresh-button">
                <xforms:label>
                    <xhtml:img width="11" height="16" src="/apps/fr/style/images/silk/arrow_refresh.png" alt=""/>
                    <xforms:output value="$fr-resources/summary/labels/refresh"/>
                </xforms:label>
                <xforms:action ev:event="DOMActivate">
                    <!-- NOP -->
                </xforms:action>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:close-button">
        <fr:button id="fr-back-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
            <xforms:label>
                <xhtml:img width="11" height="16" src="/apps/fr/style/close.gif" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/close"/></xhtml:span>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:dispatch target="fr-navigation-model" name="fr-goto-summary"/>
            </xforms:action>
        </fr:button>
        <!-- Trigger shown to go back if the data is dirty -->
        <!--<fr:button ref="instance('fr-persistence-instance')[data-status = 'dirty']">-->
            <!--<xforms:label>-->
                <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/house.png" alt=""/>-->
                <!--<xhtml:span><xforms:output value="$fr-resources/detail/labels/discard"/></xhtml:span>-->
            <!--</xforms:label>-->
            <!--<xforms:action ev:event="DOMActivate">-->
                <!--<xforms:dispatch target="fr-navigation-model" name="fr-goto-summary"/>-->
            <!--</xforms:action>-->
        <!--</fr:button>-->
        <!-- Trigger shown to go back if the data is clean -->
        <!--<fr:button ref="instance('fr-persistence-instance')[data-status = 'clean']">-->
            <!--<xforms:label>-->
                <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/house.png" alt=""/>-->
                <!--<xhtml:span><xforms:output value="$fr-resources/detail/labels/return"/></xhtml:span>-->
            <!--</xforms:label>-->
            <!--<xforms:action ev:event="DOMActivate">-->
                <!--<xforms:dispatch target="fr-navigation-model" name="fr-goto-summary"/>-->
            <!--</xforms:action>-->
        <!--</fr:button>-->
    </xsl:template>

    <xsl:template match="fr:clear-button">
        <fr:button id="fr-clear-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
            <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/clear.gif" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/clear"/></xhtml:span>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:setvalue ref="xxforms:instance('errors-state')/submitted">true</xforms:setvalue>
                <xforms:action if="not(property('xxforms:noscript'))">
                    <!-- Open confirmation dialog -->
                    <xxforms:show dialog="fr-clear-confirm-dialog"/>
                </xforms:action>
                <xforms:action if="property('xxforms:noscript')">
                    <!-- Restore directly -->
                    <xforms:dispatch name="fr-clear" targetid="fr-persistence-model"/>
                    <!-- Perform refresh (fr-clear sets RRR flags already) so that after that we can clear error summary -->
                    <xforms:refresh/>
                    <!-- Clear error summary -->
                    <xforms:dispatch name="fr-unvisit-all" targetid="fr-error-summary-model"/>
                </xforms:action>
            </xforms:action>
        </fr:button>
    </xsl:template>

    <xsl:template match="fr:print-button">
        <!-- TODO: bind to strict-submit, but maybe fr-print-submission should check validity instead -->
        <fr:button id="fr-print-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/strict-submit">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/printer.png" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/print"/></xhtml:span>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:send submission="fr-print-submission"/>
            </xforms:action>
        </fr:button>
    </xsl:template>

    <!-- NOTE: This is the detail page's PDF button (not the summary page's) -->
    <xsl:template match="fr:pdf-button">
        <!-- TODO: bind to strict-submit, but maybe fr-pdf-submission should check validity instead -->
        <xforms:group id="fr-pdf-button-group">

            <!-- Create the link so that the URL gets rewritten -->
            <!-- NOTE: Only the XForms document id is strictly needed. Keep app/form/document for filtering purposes. -->
            <xhtml:a style="display:none" class="fr-pdf-anchor" target="_blank" f:url-type="resource"
                     href="/fr/service/{$app}/{$form}/pdf/{{xxforms:instance('fr-parameters-instance')/document}}/{{xxforms:document-id()}}.pdf"/>

            <!-- In script mode, intercept client-side click and open window directly -->
            <xxforms:script ev:event="xforms-enabled" ev:target="#observer">
                var button = ORBEON.util.Dom.getElementsByName(this, "button")[0];
                YAHOO.util.Event.addListener(button, "click", function(_dummy, group) {
                    var a = YAHOO.util.Dom.getElementsByClassName("fr-pdf-anchor", null, group)[0];
                    window.open(a.href, a.target)
                }, this);
            </xxforms:script>

            <fr:button id="fr-pdf-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/strict-submit">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/pdf.png" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/print-pdf"/></xhtml:span>
                </xforms:label>
                <!-- Only call submission directly in noscript mode -->
                <xforms:dispatch ev:event="DOMActivate" if="property('xxforms:noscript')" name="fr-open-pdf" targetid="fr-navigation-model"/>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:save-button">
        <fr:button id="fr-save-button" xxforms:modal="true" model="fr-persistence-model" ref="instance('fr-triggers-instance')/save">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/database_save.png" alt=""/>
                <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/save_16.png" alt=""/>-->
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/save-document"/></xhtml:span>
            </xforms:label>
        </fr:button>
    </xsl:template>

    <xsl:template match="fr:save-locally-button">
        <fr:button id="fr-save-locally-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/disk.png" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/save-locally"/></xhtml:span>
            </xforms:label>
        </fr:button>
    </xsl:template>

    <xsl:template match="fr:submit-button">
        <fr:button xxforms:modal="true" id="fr-submit-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/submit">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/application_go.png" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/submit-document"/></xhtml:span>
            </xforms:label>
        </fr:button>
    </xsl:template>

    <xsl:template match="fr:workflow-review-button">
        <fr:button xxforms:modal="true" id="fr-workflow-review-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/workflow-review">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/right_16.png" alt=""/>
                <!--<xhtml:span>→ </xhtml:span>-->
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/workflow-review"/></xhtml:span>
            </xforms:label>
        </fr:button>
    </xsl:template>

    <xsl:template match="fr:workflow-edit-button">
        <fr:button xxforms:modal="true" id="fr-workflow-edit-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/workflow-edit">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/left_16.png" alt=""/>
                <!--<xhtml:span>← </xhtml:span>-->
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/workflow-edit"/></xhtml:span>
            </xforms:label>
        </fr:button>
    </xsl:template>

    <xsl:template match="fr:workflow-send-button">
        <fr:button xxforms:modal="true" id="fr-workflow-send-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/workflow-send">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/right_16.png" alt=""/>
                <!--<xhtml:span>→ </xhtml:span>-->
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/workflow-send"/></xhtml:span>
            </xforms:label>
        </fr:button>
    </xsl:template>

    <xsl:template match="fr:email-button">
        <!-- Don't show this button in noscript mode -->
        <!-- TODO: bind to strict-submit, but maybe fr-email-service-submission should check validity instead -->
        <fr:button xxforms:modal="true" id="fr-email-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/strict-submit">
            <xforms:label>
                <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/email.png" alt=""/>-->
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/letter_16.png" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/email"/></xhtml:span>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:send submission="fr-email-service-submission"/>
            </xforms:action>
        </fr:button>
    </xsl:template>

    <xsl:template match="fr:collapse-all-button">
        <xsl:if test="$is-section-collapse">
            <fr:button id="fr-collapse-all-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/arrow_in.png" alt=""/>
                    <!--<xhtml:span><xforms:output value="$fr-resources/detail/labels/collapse-all"/>-->
                </xforms:label>
                <xforms:dispatch ev:event="DOMActivate" name="fr-collapse-all" target="fr-sections-model"/>
            </fr:button>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:expand-all-button">
        <xsl:if test="$is-section-collapse">
            <fr:button id="fr-expand-all-button" model="fr-persistence-model" ref="instance('fr-triggers-instance')/other">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/arrow_out.png" alt=""/>
                    <!--<xhtml:span><xforms:output value="$fr-resources/detail/labels/expand-all"/>-->
                </xforms:label>
                <xforms:dispatch ev:event="DOMActivate" name="fr-expand-all" target="fr-sections-model"/>
            </fr:button>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
