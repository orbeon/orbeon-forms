<?xml version="1.0" encoding="UTF-8"?>
<!--
  Copyright (C) 2009 Orbeon, Inc.

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

        <xforms:group>
            <fr:button id="fr-back-button">
                <xforms:label>
                    <xhtml:img width="11" height="16" src="/apps/fr/style/close.gif" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/close"/></xhtml:span>
                </xforms:label>
                <xforms:action ev:event="DOMActivate">
                    <xforms:dispatch target="fr-navigation-model" name="fr-goto-summary"/>
                </xforms:action>
            </fr:button>
        </xforms:group>
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
        <xforms:group>
            <fr:button id="fr-clear-button">
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
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:print-button">
        <!-- TODO: bind to strict-submit, but maybe fr-print-submission should check validity instead -->
        <xforms:group ref="instance('fr-triggers-instance')/strict-submit">
            <fr:button id="fr-print-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/printer.png" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/print"/></xhtml:span>
                </xforms:label>
                <xforms:action ev:event="DOMActivate">
                    <xforms:send submission="fr-print-submission"/>
                </xforms:action>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:pdf-button">
        <!-- TODO: bind to strict-submit, but maybe fr-pdf-submission should check validity instead -->
        <xforms:group model="fr-persistence-model" ref="instance('fr-triggers-instance')/strict-submit">
            <fr:button id="fr-pdf-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/pdf.png" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/print-pdf"/></xhtml:span>
                </xforms:label>
                <xforms:action ev:event="DOMActivate">
                    <xforms:send submission="fr-pdf-submission"/>
                </xforms:action>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:save-button">
        <xforms:group model="fr-persistence-model" ref="instance('fr-triggers-instance')/save">
            <fr:button id="fr-save-button" xxforms:modal="true">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/database_save.png" alt=""/>
                    <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/save_16.png" alt=""/>-->
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/save-document"/></xhtml:span>
                </xforms:label>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:save-locally-button">
        <xforms:group>
            <fr:button id="save-locally-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/disk.png" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/save-locally"/></xhtml:span>
                </xforms:label>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:submit-button">
        <xforms:group model="fr-persistence-model" ref="instance('fr-triggers-instance')/submit" >
            <fr:button xxforms:modal="true" id="fr-submit-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/application_go.png" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/submit-document"/></xhtml:span>
                </xforms:label>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:workflow-review-button">
        <xforms:group ref="xxforms:instance('fr-triggers-instance')/workflow-review">
            <fr:button xxforms:modal="true" id="fr-workflow-review-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/right_16.png" alt=""/>
                    <!--<xhtml:span>→ </xhtml:span>-->
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/workflow-review"/></xhtml:span>
                </xforms:label>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:workflow-edit-button">
        <xforms:group ref="xxforms:instance('fr-triggers-instance')/workflow-edit">
            <fr:button xxforms:modal="true" id="fr-workflow-edit-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/left_16.png" alt=""/>
                    <!--<xhtml:span>← </xhtml:span>-->
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/workflow-edit"/></xhtml:span>
                </xforms:label>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:workflow-send-button">
        <xforms:group ref="xxforms:instance('fr-triggers-instance')/workflow-send">
            <fr:button xxforms:modal="true" id="fr-workflow-send-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/right_16.png" alt=""/>
                    <!--<xhtml:span>→ </xhtml:span>-->
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/workflow-send"/></xhtml:span>
                </xforms:label>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:email-button">
        <!-- Don't show this button in noscript mode -->
        <!-- TODO: bind to strict-submit, but maybe fr-email-service-submission should check validity instead -->
        <xforms:group ref="xxforms:instance('fr-triggers-instance')/strict-submit" >
            <fr:button xxforms:modal="true" id="fr-email-button">
                <xforms:label>
                    <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/email.png" alt=""/>-->
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/letter_16.png" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/email"/></xhtml:span>
                </xforms:label>
                <xforms:action ev:event="DOMActivate">
                    <xforms:send submission="fr-email-service-submission"/>
                </xforms:action>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:collapse-all-button">
        <xsl:if test="$is-section-collapse">
            <xforms:group>
                <fr:button id="fr-collapse-all-button">
                    <xforms:label>
                        <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/arrow_in.png" alt=""/>
                        <!--<xhtml:span><xforms:output value="$fr-resources/detail/labels/collapse-all"/>-->
                    </xforms:label>
                    <xforms:action ev:event="DOMActivate">
                        <xsl:for-each select="$input-data//fr:section/@id">
                            <xforms:toggle case="case-{.}-closed"/>
                            <xforms:toggle case="case-button-{.}-closed"/>
                        </xsl:for-each>
                    </xforms:action>
                </fr:button>
            </xforms:group>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:expand-all-button">
        <xsl:if test="$is-section-collapse">
            <xforms:group>
                <fr:button id="fr-expand-all-button">
                    <xforms:label>
                        <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/arrow_out.png" alt=""/>
                        <!--<xhtml:span><xforms:output value="$fr-resources/detail/labels/expand-all"/>-->
                    </xforms:label>
                    <xforms:action ev:event="DOMActivate">
                        <xsl:for-each select="$input-data//fr:section/@id">
                            <xforms:toggle case="case-{.}-open"/>
                            <xforms:toggle case="case-button-{.}-open"/>
                        </xsl:for-each>
                    </xforms:action>
                </fr:button>
            </xforms:group>
        </xsl:if>
    </xsl:template>

    <!-- === Offline Buttons (alpha code, not supported/documented) === -->

    <xsl:template match="fr:take-offline-button">
        <xforms:group ref="if (xxforms:instance('fr-offline-instance')/is-online = 'true') then . else ()">
            <fr:button id="take-offline-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/take-offline.gif" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/offline"/></xhtml:span>
                </xforms:label>
                <xxforms:offline ev:event="DOMActivate"/>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:take-online-button">
        <xforms:group ref="if (xxforms:instance('fr-offline-instance')/is-online = 'false') then . else ()">
            <fr:button id="take-online-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/take-online.gif" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/online"/></xhtml:span>
                </xforms:label>
                <xxforms:online ev:event="DOMActivate"/>
            </fr:button>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:save-offline-button">
        <xforms:group ref="if (xxforms:instance('fr-offline-instance')/is-online = 'false') then . else ()">
            <fr:button id="save-offline-button">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/save.gif" alt=""/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/save-offline"/></xhtml:span>
                </xforms:label>
                <xxforms:offline-save ev:event="DOMActivate"/>
            </fr:button>
        </xforms:group>
    </xsl:template>


</xsl:stylesheet>
