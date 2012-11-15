<!--
  Copyright (C) 2011 Orbeon, Inc.

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
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- Import components -->
    <xsl:import href="view.xsl"/>
    <xsl:import href="buttons.xsl"/>
    <xsl:import href="section.xsl"/><!-- pass global section properties to fr:section -->
    <xsl:import href="repeat.xsl"/> <!-- convert legacy fr:repeat to fr:grid -->

    <!-- Global variables -->
    <xsl:variable name="app" select="doc('input:instance')/*/app" as="xs:string"/>
    <xsl:variable name="form" select="doc('input:instance')/*/form" as="xs:string"/>
    <xsl:variable name="mode" select="doc('input:instance')/*/mode" as="xs:string?"/>

    <xsl:variable name="is-detail" select="not($mode = ('summary', ''))" as="xs:boolean"/>
    <xsl:variable name="is-form-builder" select="$app = 'orbeon' and $form = 'builder'" as="xs:boolean"/>
    <xsl:variable name="is-noscript-support" select="not(/xh:html/xh:head/xf:model[1]/@xxf:noscript-support = 'false')" as="xs:boolean"/>
    <xsl:variable name="is-noscript" select="doc('input:request')/request/parameters/parameter[name = 'fr-noscript']/value = 'true'
                                                and $is-noscript-support" as="xs:boolean"/>
    <xsl:variable name="input-data" select="/*" as="element(xh:html)"/>
    <xsl:variable name="has-pdf-template" select="normalize-space(/xh:html/xh:head//xf:instance[@id = 'fr-form-attachments']/*/pdf) != ''"/>

    <!-- Properties -->
    <xsl:variable name="has-version" select="xpl:property(string-join(('oxf.fr.version', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="has-noscript-link" select="xpl:property(string-join(('oxf.fr.noscript-link', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="is-noscript-table" select="not(not(xpl:property(string-join(('oxf.fr.detail.noscript.table', $app, $form), '.'))) = false())" as="xs:boolean"/>
    <xsl:variable name="is-noscript-section-collapse" select="not(xpl:property(string-join(('oxf.fr.detail.noscript.section.collapse', $app, $form), '.')) = false())" as="xs:boolean"/>
    <xsl:variable name="min-toc" select="(xpl:property(string-join(('oxf.fr.detail.toc', $app, $form), '.')), -1)[1]" as="xs:integer"/>
    <xsl:variable name="has-toc" select="$min-toc ge 0" as="xs:boolean"/>
    <xsl:variable name="error-summary" select="xpl:property(string-join(('oxf.fr.detail.error-summary', $app, $form), '.'))" as="xs:string?"/>
    <xsl:variable name="is-ajax-section-collapse" select="not(xpl:property(string-join(('oxf.fr.detail.ajax.section.collapse', $app, $form), '.')) = false())" as="xs:boolean"/>
    <xsl:variable name="default-logo-uri" select="xpl:property(string-join(('oxf.fr.default-logo.uri', $app, $form), '.'))" as="xs:string?"/>
    <xsl:variable name="hide-logo" select="xpl:property(string-join(('oxf.fr.detail.hide-logo', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="hide-header" select="xpl:property(string-join(('oxf.fr.detail.hide-header', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="hide-footer" select="xpl:property(string-join(('oxf.fr.detail.hide-footer', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="hide-top" select="xpl:property(string-join(('oxf.fr.detail.hide-top', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="hide-buttons-bar" select="xpl:property(string-join(('oxf.fr.detail.hide-buttons-bar', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="css-uri" select="tokenize(normalize-space(xpl:property(string-join(('oxf.fr.css.uri', $app, $form), '.'))), '\s+')" as="xs:string*"/>
    <xsl:variable name="buttons-property" select="if ($mode = 'view') then 'oxf.fr.detail.buttons.view' else 'oxf.fr.detail.buttons'"/>
    <xsl:variable name="buttons" select="tokenize(xpl:property(string-join(($buttons-property, $app, $form), '.')), '\s+')" as="xs:string*"/>
    <xsl:variable name="has-alfresco" select="xpl:property(string-join(('oxf.fr.detail.send.alfresco', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="is-show-explanation" select="xpl:property(string-join(('oxf.fr.detail.view.show-explanation', $app, $form), '.')) = true()" as="xs:boolean"/>
    <xsl:variable name="is-inline-hints" select="not(xpl:property(string-join(('oxf.fr.detail.hints.inline', $app, $form), '.')) = false())" as="xs:boolean"/>
    <xsl:variable name="is-animate-sections" select="not($is-noscript) and not(xpl:property(string-join(('oxf.fr.detail.ajax.section.animate', $app, $form), '.')) = false())" as="xs:boolean"/>
    <xsl:variable name="captcha" as="xs:string" select="xpl:property(string-join(('oxf.fr.detail.captcha', $app, $form), '.'))"/>
    <xsl:variable name="has-captcha" as="xs:boolean" select="$captcha = ('reCAPTCHA', 'SimpleCaptcha')"/>

    <xsl:variable name="is-section-collapse" select="(not($is-noscript) and $is-ajax-section-collapse) or $is-noscript-section-collapse" as="xs:boolean"/>

    <xsl:variable name="error-summary-top" select="normalize-space($error-summary) = ('top', 'both')" as="xs:boolean"/>
    <xsl:variable name="error-summary-bottom" select="normalize-space($error-summary) = ('', 'bottom', 'both')" as="xs:boolean"/>

    <xsl:variable name="is-rendered" select="doc('input:request')/request/request-path = '/xforms-renderer'" as="xs:boolean"/>
    <xsl:variable name="url-base-for-requests" select="if ($is-rendered) then substring-before(doc('input:request')/request/request-uri, '/xforms-renderer') else ''" as="xs:string"/>

    <xsl:template match="/xh:html">
        <!-- Handle document language -->
        <xh:html lang="{{xxf:instance('fr-language-instance')}}"
                    xml:lang="{{xxf:instance('fr-language-instance')}}">
            <xsl:apply-templates select="@*"/>

            <!-- Scope variable with Form Runner resources -->
            <xf:var name="fr-resources" model="fr-resources-model" value="$fr-fr-resources" as="element(resource)?"/>
            <!-- Scope form resources -->
            <xf:var name="form-resources" model="fr-resources-model" value="$fr-form-resources" as="element(resource)?"/>

            <!-- Title from the current form's metadata -->
            <xf:var
                name="title-from-metadata"
                value="xxf:instance('fr-form-metadata')/title[@xml:lang = xxf:instance('fr-language-instance')], xxf:instance('fr-form-metadata')/title"/>

            <!-- Title from the current page's xf:output under HTML title -->
             <xsl:choose>
                <xsl:when test="xh:head/xh:title/xf:output">
                    <xf:var
                        name="title-from-output"
                        model="fr-form-model"
                        value="string()">
                        <xsl:apply-templates select="(xh:head/xh:title/xf:output)[1]/(@bind | @model | @context | @ref | @value)"/>
                    </xf:var>
                </xsl:when>
                <xsl:otherwise>
                    <xf:var name="title-from-output"/>
                </xsl:otherwise>
            </xsl:choose>

            <!-- Form title based on metadata or HTML title -->
            <xf:var name="title" value="($title-from-metadata, $title-from-output, '{replace(xh:head/xh:title, '''', '''''')}')[normalize-space()][1]"/>

            <xsl:apply-templates select="node()"/>
        </xh:html>
    </xsl:template>

    <!-- Insert stylesheets -->
    <xsl:template match="xh:head">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- Form Runner CSS stylesheets -->
            <xsl:for-each select="$css-uri">
                <xh:link rel="stylesheet" href="{.}" type="text/css" media="all"/>
            </xsl:for-each>

            <!-- Handle existing stylesheets -->
            <xsl:for-each select="xh:link | xh:style">
                <xsl:element name="xh:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:apply-templates select="@*|node()"/>
                </xsl:element>
            </xsl:for-each>

            <!-- Process the rest -->
            <xsl:apply-templates select="node() except (xh:link | xh:style)"/>

            <xh:script language="javascript" type="text/javascript" src="/apps/fr/style/bootstrap/js/bootstrap.js"/>

            <!-- For IE debugging -->
            <!--<xh:script language="javascript" type="text/javascript" src="/ops/firebug/firebug.js"/>-->
            <!--<xh:script language="javascript" type="text/javascript" src="http://getfirebug.com/releases/lite/1.2/firebug-lite-compressed.js"/>-->

            <!-- Script to enable scrolling within iframes with Firefox >= 4, due to this bug:
                 https://bugzilla.mozilla.org/show_bug.cgi?id=638598 -->
            <!-- TODO XXX: form-runner.js -->
            <xsl:if test="$has-toc and not($is-noscript)">
                <xh:script type="text/javascript">
                    <![CDATA[
                    if (YAHOO.env.ua.gecko >= 2) { // Firefox 4 and higher
                        YAHOO.util.Event.onDOMReady(function() {

                            // Only initialize if there is a parent window with same origin
                            var parentWithSameOrigin = false;
                            try {
                                if (window.parent != window && window.parent.scrollTo)
                                    parentWithSameOrigin = true;
                            } catch (e) {}

                            if (parentWithSameOrigin) {
                                // Find toc
                                var toc = document.getElementsByClassName("fr-toc")[0];
                                if (toc) {

                                    // Listener for clicks on toc links
                                    var onClick = function(event) {
                                        var eventObserver = this; // "the Event Utility automatically adjusts the execution scope so that this refers to the DOM element to which the event was attached"
                                        var linkTarget = document.getElementById(eventObserver.getAttribute("href").substring(1));
                                        if (linkTarget)
                                            window.parent.scrollTo(0, YAHOO.util.Dom.getY(linkTarget) + YAHOO.util.Dom.getY(window.frameElement));
                                    };

                                    // Find all toc links starting with a non-empty fragment, and add the listener to them
                                    var as = toc.getElementsByTagName("a");
                                    for (var i = 0; i < as.length; i++) {
                                        var a = as[i];
                                        var href = a.getAttribute("href");
                                        if (href && href[0] == "#" && href[1])
                                            YAHOO.util.Event.addListener(a, "click", onClick);
                                    }
                                }
                            }
                        });
                    }
                    ]]>
                </xh:script>
            </xsl:if>
        </xsl:copy>
    </xsl:template>

    <!-- Set XHTML title -->
    <xsl:template match="xh:head/xh:title">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- Display localized errors count and form title -->
            <xf:output model="fr-error-summary-model"
                           value="for $c in visible-errors-count return
                                    if ($c castable as xs:integer and xs:integer($c) > 0)
                                    then concat($c, ' ', $fr-resources/summary/titles/(if (xs:integer($c) = 1) then error-count else errors-count), ' - ', $title)
                                    else $title"/>
        </xsl:copy>
    </xsl:template>

    <!-- Add Form Runner models and scripts -->
    <xsl:template match="/xh:html/xh:head/xf:model[1]">

        <!-- Model receiving input parameters -->
        <xf:model id="fr-parameters-model"
                      xxf:external-events="{@xxf:external-events}{if ($mode = ('new', 'view', 'edit')) then ' fr-open-pdf' else ''}"
                      xxf:readonly-appearance="{if ($mode = ('view', 'email') or ($mode = 'pdf' and not($has-pdf-template))) then 'static' else 'dynamic'}"
                      xxf:encrypt-item-values="{not($mode = 'pdf' and $has-pdf-template)}"
                      xxf:order="{if ($is-noscript) then 'label control alert hint help' else 'help label control alert hint'}"
                      xxf:offline="false"
                      xxf:noscript="{$is-noscript}"
                      xxf:noscript-support="{$is-noscript-support}"
                      xxf:xforms11-switch="false"
                      xxf:xpath-analysis="true">

            <!-- Don't enable client events filtering for FB -->
            <xsl:if test="$is-form-builder">
                <xsl:attribute name="xxf:client.events.filter"/>
            </xsl:if>
            <!-- Override if specified -->
            <xsl:copy-of select="@xxf:xpath-analysis"/>

            <!-- Parameters passed to this page -->
            <!-- NOTE: the <document> element may be modified, so we don't set this as read-only -->
            <xf:instance id="fr-parameters-instance" src="input:instance"/>

        </xf:model>

        <xf:var name="url-base-for-requests" value="'{$url-base-for-requests}'"/>

        <!-- This model handles Form Builder roles and permissions -->
        <!-- NOTE: We could remove this if not($is-form-builder), except for the use of the $fr-roles variable -->
        <xi:include href="oxf:/apps/fr/includes/roles-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles i18n resources -->
        <xi:include href="oxf:/apps/fr/i18n/resources-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles global actions on form sections -->
        <xf:model id="fr-sections-model">

            <xsl:variable name="section-ids" select="$input-data//fr:section/@id" as="xs:string*"/>
            <xsl:variable name="section-ids-sequence" select="concat('(', string-join(for $s in $section-ids return concat('''', $s, ''''), ','), ')')" as="xs:string*"/>

            <!-- Collapse or expand all sections -->
            <xf:dispatch ev:event="fr-collapse-all" iterate="{$section-ids-sequence}" name="fr-collapse" targetid="{{.}}"/>
            <xf:dispatch ev:event="fr-expand-all"   iterate="{$section-ids-sequence}" name="fr-expand"   targetid="{{.}}"/>
        </xf:model>
        <!-- This model handles global error summary information -->
        <xf:model id="fr-error-summary-model">
            <xf:instance id="fr-error-summary-instance">
                <error-summary>
                    <xsl:choose>
                        <!-- For form builder we disable the error summary and say that the form is always valid -->
                        <xsl:when test="$is-form-builder">
                            <valid>true</valid>
                            <errors-count>0</errors-count>
                            <visible-errors-count>0</visible-errors-count>
                            <trigger/>
                        </xsl:when>
                        <xsl:otherwise>
                            <valid/>
                            <errors-count/>
                            <visible-errors-count/>
                            <trigger/>
                        </xsl:otherwise>
                    </xsl:choose>
                </error-summary>
            </xf:instance>
            <xf:bind ref="trigger" readonly="not(../valid = 'true')"/>

            <!-- Verify captcha and mark all controls as visited when certain buttons are activated -->
            <xf:action ev:event="DOMActivate" ev:observer="fr-save-button fr-workflow-review-button fr-workflow-send-button fr-print-button fr-pdf-button fr-email-button fr-refresh-button fr-submit-button">

                <!-- Do captcha verification, if needed -->
                <!-- NOTE: The code would be shorter here if we were to use the XSLT app/form variables, but we want to move away from XSLT for FR -->
                <xf:var name="parameters" value="xxf:instance('fr-parameters-instance')" as="element()"/>
                <xf:var name="app" value="$parameters/app" as="xs:string"/>
                <xf:var name="form" value="$parameters/form" as="xs:string"/>
                <xsl:if test="$has-captcha">
                    <xf:action if="xxf:instance('fr-persistence-instance')/captcha = 'false'">
                        <xf:dispatch targetid="captcha" name="fr-verify"/>
                    </xf:action>
                </xsl:if>

                <!-- Show all error in error summaries -->
                <xsl:if test="$error-summary-top"><xf:dispatch name="fr-visit-all" targetid="error-summary-control-top"/></xsl:if>
                <xf:dispatch name="fr-visit-all" targetid="error-summary-control-bottom"/>

            </xf:action>

            <!-- Mark all controls as un-visited when certain buttons are activated -->
            <xf:action ev:event="fr-unvisit-all">
                <!-- Dispatch to the appropriate error summaries -->
                <!-- Don't dispatch to top error-summary if not present; but always dispatch to bottom error summary as it is always included -->
                <xsl:if test="$error-summary-top">
                    <xf:dispatch name="fr-unvisit-all" targetid="error-summary-control-top"/>
                </xsl:if>
                <xf:dispatch name="fr-unvisit-all" targetid="error-summary-control-bottom"/>
            </xf:action>
        </xf:model>
        <!-- This model handles document persistence -->
        <xi:include href="oxf:/apps/fr/includes/persistence/persistence-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles navigation functionality -->
        <xi:include href="oxf:/apps/fr/includes/navigation-model.xml" xxi:omit-xml-base="true"/>
        <xsl:if test="$has-alfresco">
            <!-- This model handles Alfresco integration -->
            <xi:include href="oxf:/apps/fr/alfresco/alfresco-model.xml" xxi:omit-xml-base="true"/>
        </xsl:if>

        <!-- This model supports Form Runner rendered through the xforms-renderer -->
        <xf:model id="fr-xforms-renderer-model">
            <xf:instance id="fr-xforms-renderer-instance">
                <xforms-renderer>
                    <is-rendered>
                        <xsl:value-of select="$is-rendered"/>
                    </is-rendered>
                    <url-base-for-requests>
                        <xsl:value-of select="$url-base-for-requests"/>
                    </url-base-for-requests>
                </xforms-renderer>
            </xf:instance>
        </xf:model>

        <!-- Copy and annotate existing main model -->
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- If the first model doesn't have an id, add fr-form-model -->
            <xsl:if test="not(@id)">
                <xsl:attribute name="id" select="'fr-form-model'"/>
            </xsl:if>

            <xsl:apply-templates select="node()"/>

            <!-- Variable exposing all the user roles -->
            <xf:var name="fr-roles" value="tokenize(xxf:instance('fr-permissions')/@all-roles, '\s+')" as="xs:string*"/>
            <!-- Variable exposing the form mode -->
            <xf:var name="fr-mode" value="xxf:instance('fr-parameters-instance')/mode"/>

            <!-- Bind to set the form instance read-only when necessary -->
            <xf:bind ref="instance('fr-form-instance')" readonly="$fr-mode = ('view', 'pdf', 'email')"/>

            <!-- Focus to the first control supporting input on load -->
            <xf:setfocus ev:event="xforms-ready" control="fr-form-group" input-only="true"/>

        </xsl:copy>

        <xsl:if test="not($is-noscript)">
            <!-- Handle checking dirty status -->
            <xi:include href="oxf:/apps/fr/includes/check-dirty-script.xhtml" xxi:omit-xml-base="true"/>
        </xsl:if>

    </xsl:template>

    <xsl:template match="/xh:html/xh:head/xf:model[1]/xf:instance[1]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- If there is no instance with fr-form-instance consider the first one is it -->
            <xsl:if test="not(exists(../xf:instance[@id = 'fr-form-instance']))">
                <xsl:attribute name="id" select="'fr-form-instance'"/>
            </xsl:if>

            <xsl:apply-templates select="node()"/>

        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
