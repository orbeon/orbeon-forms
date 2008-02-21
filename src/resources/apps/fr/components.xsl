<?xml version="1.0" encoding="UTF-8"?>
<!--
    Copyright (C) 2008 Orbeon, Inc.

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
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <xsl:template match="xhtml:body//fr:view">
        <!--  Hidden field to communicate to the client the current section to collapse or expand -->
        <xforms:input model="fr-sections-model" ref="instance('fr-current-section-instance')" id="fr-current-section-input" class="xforms-disabled"/>
        <!-- Hidden field to communicate to the client whether the data is clean or dirty -->
        <xforms:input model="fr-persistence-model" ref="instance('fr-persistence-instance')/data-status" id="fr-data-status-input" class="xforms-disabled"/>

        <xhtml:div id="doc4" class="yui-t5xxx">
            <xhtml:div id="hd" class="fr-top"/>
            <xhtml:div id="bd" class="fr-container">
                <xhtml:div id="yui-main">
                    <xhtml:div class="yui-b">
                        <xhtml:div class="yui-g fr-logo">
                            <xsl:if test="xhtml:img">
                                <xhtml:img src="{xhtml:img/@src}" alt="Logo"/>
                            </xsl:if>
                            <xhtml:h1>
                                <xsl:choose>
                                    <xsl:when test="xforms:label">
                                        <!-- TODO: Create xforms:output instead -->
                                        <xsl:value-of select="xforms:label"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <!-- HTML title is static -->
                                        <xsl:value-of select="/xhtml:html/xhtml:head/xhtml:title"/>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xhtml:h1>
                        </xhtml:div>
                        <xhtml:div class="yui-g fr-separator">
                        </xhtml:div>
                        <xhtml:div class="yui-g fr-body">
                            <xforms:group model="fr-form-model" ref="instance('fr-form-instance')">
                                <!-- Error summary: handle xforms-invalid event -->
                                <xforms:action ev:event="xforms-invalid" if="normalize-space(event('label')) != ''">
                                    <xforms:action if="not(xxforms:instance('fr-errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')])">
                                        <xforms:insert context="xxforms:instance('fr-errors-instance')" nodeset="error" origin="xxforms:instance('fr-error-template')"/>
                                        <xforms:setvalue ref="xxforms:instance('fr-errors-instance')/error[index('fr-errors-repeat')]/@id" value="event('target')"/>
                                        <xforms:setvalue ref="xxforms:instance('fr-errors-instance')/error[index('fr-errors-repeat')]/@indexes" value="string-join(event('repeat-indexes'), '-')"/>
                                    </xforms:action>
                                    <xforms:setvalue ref="xxforms:instance('fr-errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')]/@alert" value="event('alert')"/>
                                    <xforms:setvalue ref="xxforms:instance('fr-errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')]/@label" value="event('label')"/>
                                </xforms:action>
                                <!-- Error summary: handle xforms-valid -->
                                <xforms:action ev:event="xforms-valid" if="xxforms:instance('fr-errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')]">
                                    <xforms:delete nodeset="xxforms:instance('fr-errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')]"/>
                                </xforms:action>

                                <xforms:group appearance="xxforms:internal">
                                    <!-- Clear message upon user interaction -->
                                    <xforms:setvalue ev:event="DOMFocusIn" model="fr-persistence-model" ref="instance('fr-persistence-instance')/message"/>
                                    
                                    <!-- Mark status as dirty if data changes in fr-form-instance instance only -->
                                    <xforms:setvalue ev:event="xforms-value-changed"
                                                     if="event('target-ref')/ancestor::*[last()] is xxforms:instance('fr-form-instance')"
                                                     model="fr-persistence-model"
                                                     ref="instance('fr-persistence-instance')/data-status">dirty</xforms:setvalue>

                                    <!-- Take action upon xforms-help -->
                                    <!--<xforms:action ev:event="xforms-help" ev:defaultAction="cancel">-->
                                        <!--<xforms:setvalue model="fr-help-model" ref="instance('fr-help-instance')/label" value="event('label')"/>-->
                                        <!--<xforms:setvalue model="fr-help-model" ref="instance('fr-help-instance')/help" value="event('help')"/>-->
                                    <!--</xforms:action>-->

                                    <!-- Main form content -->
                                    <xsl:apply-templates select="fr:body/node()"/>
                                </xforms:group>
                            </xforms:group>
                        </xhtml:div>
                        <xhtml:div class="yui-g fr-separator">
                        </xhtml:div>
                        <xhtml:div class="yui-g fr-buttons-block">
                            <xsl:choose>
                                <xsl:when test="fr:buttons">
                                    <xsl:apply-templates select="fr:buttons/node()"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <!-- Display the toolbar and errors -->
                                    <xi:include href="oxf:/apps/fr/includes/toolbar-and-errors-view.xml" xxi:omit-xml-base="true"/>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xhtml:div>
                    </xhtml:div>
                </xhtml:div>
                <xhtml:div class="yui-b">
                    <!--<xhtml:div class="yui-g fr-logo">-->
                    <!--</xhtml:div>-->
                    <!--<xhtml:div class="yui-g fr-separator">-->
                    <!--</xhtml:div>-->
                    <!--<xhtml:h2><xforms:output model="fr-help-model" value="instance('fr-help-instance')/label"/></xhtml:h2>-->
                    <!--<xhtml:div>-->
                        <!--<xforms:output model="fr-help-model" value="instance('fr-help-instance')/help"/>-->
                    <!--</xhtml:div>-->
                </xhtml:div>
            </xhtml:div>
            <xhtml:div id="ft" class="fr-bottom">
                <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version" name="orbeon-forms-version" select="version:getVersion()" as="xs:string"/>
                <xhtml:div class="fr-orbeon-version">Orbeon Forms <xsl:value-of select="$orbeon-forms-version"/></xhtml:div>
            </xhtml:div>

        </xhtml:div>
    </xsl:template>

    <xsl:template match="xhtml:body//xforms:input[@appearance='fr:in-place']">
        <xforms:switch id="{@id}">
            <xsl:attribute name="class" select="string-join(('fr-inplace-input', @class), ' ')"/>
            <xforms:case id="fr-inplace-{@id}-view">
                <xhtml:div>
                    <xhtml:span class="fr-inplace-content">
                        <xforms:output value="{@ref}" class="fr-inplace-value">
                            <xsl:copy-of select="xforms:label"/>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:toggle case="fr-inplace-{@id}-edit"/>
                                <xforms:setfocus control="fr-inplace-{@id}-input"/>
                            </xforms:action>
                        </xforms:output>
                        <xhtml:span class="fr-inplace-buttons">
                            <xforms:trigger id="fr-inplace-{@id}-delete" appearance="minimal" class="fr-inplace-delete">
                                <xforms:label><xhtml:img src="../../../../apps/fr/style/trash.gif" alt="Delete" title="Delete {lower-case(xforms:label)}"/></xforms:label>
                                <!-- Dispatch custom event to trigger -->
                                <xforms:dispatch ev:event="DOMActivate" target="fr-inplace-{@id}-delete" name="fr-delete"/>
                            </xforms:trigger>
                            <!--<xforms:trigger appearance="minimal" class="fr-inplace-edit">-->
                                <!--<xforms:label>Change</xforms:label>-->
                                <!--<xforms:action ev:event="DOMActivate">-->
                                    <!--<xforms:toggle case="fr-inplace-{@id}-edit"/>-->
                                    <!--<xforms:setfocus control="fr-inplace-{@id}-input"/>-->
                                <!--</xforms:action>-->
                            <!--</xforms:trigger>-->
                        </xhtml:span>
                    </xhtml:span>
                </xhtml:div>
            </xforms:case>
            <xforms:case id="fr-inplace-{@id}-edit">
                <xhtml:div>
                    <xhtml:span class="fr-inplace-content">
                        <xforms:input id="fr-inplace-{@id}-input" ref="{@ref}" class="fr-inplace-value">
                            <xsl:copy-of select="xforms:label"/>
                            <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                        </xforms:input>
                        <xhtml:span class="fr-inplace-buttons">
                            <xforms:trigger class="fr-inplace-rename">
                                <xforms:label>Change <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                            or
                            <xforms:trigger appearance="minimal" class="fr-inplace-cancel">
                                <xforms:label>Cancel</xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                        </xhtml:span>
                    </xhtml:span>
                </xhtml:div>
            </xforms:case>
            <!-- Copy other children elements, including event handlers -->
            <xsl:apply-templates select="* except xforms:label"/>
        </xforms:switch>
    </xsl:template>

    <xsl:template match="xhtml:body//xforms:textarea[@appearance='fr:in-place']">
        <xforms:switch id="{@id}">
            <xsl:attribute name="class" select="string-join(('fr-inplace-input', @class), ' ')"/>
            <xforms:case id="fr-inplace-{@id}-view">
                <xhtml:div>
                    <xhtml:span class="fr-inplace-content">
                        <xforms:output value="{@ref}" class="fr-inplace-value">
                            <xsl:copy-of select="xforms:label"/>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:toggle case="fr-inplace-{@id}-edit"/>
                                <xforms:setfocus control="fr-inplace-{@id}-input"/>
                            </xforms:action>
                        </xforms:output>
                        <!--<xhtml:span class="fr-inplace-buttons">-->
                            <!--<xforms:trigger appearance="minimal" class="fr-inplace-delete">-->
                                <!--<xforms:label><xhtml:img src="../../../../apps/fr/style/trash.gif" alt="Delete" title="Delete Section"/></xforms:label>-->
                            <!--</xforms:trigger>-->
                            <!--<xforms:trigger appearance="minimal" class="fr-inplace-edit">-->
                                <!--<xforms:label>Change</xforms:label>-->
                                <!--<xforms:action ev:event="DOMActivate">-->
                                    <!--<xforms:toggle case="fr-inplace-{@id}-edit"/>-->
                                    <!--<xforms:setfocus control="fr-inplace-{@id}-input"/>-->
                                <!--</xforms:action>-->
                            <!--</xforms:trigger>-->
                        <!--</xhtml:span>-->
                    </xhtml:span>
                </xhtml:div>
            </xforms:case>
            <xforms:case id="fr-inplace-{@id}-edit">
                <xhtml:div>
                    <xhtml:span class="fr-inplace-content">
                        <xforms:textarea id="fr-inplace-{@id}-input" ref="{@ref}" class="fr-inplace-value" appearance="xxforms:autosize">
                            <xsl:copy-of select="xforms:label"/>
                            <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                        </xforms:textarea>
                        <xhtml:span class="fr-inplace-buttons">
                            <xforms:trigger class="fr-inplace-rename">
                                <xforms:label>Change <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                            or
                            <xforms:trigger appearance="minimal" class="fr-inplace-cancel">
                                <xforms:label>Cancel</xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                        </xhtml:span>
                    </xhtml:span>
                </xhtml:div>
            </xforms:case>
        </xforms:switch>
    </xsl:template>

    <xsl:template match="xhtml:body//fr:section">
        <xhtml:div id="{@id}">
            <xsl:attribute name="class" select="string-join(('fr-section-container', @class), ' ')"/>
            <xforms:switch id="switch-{@id}" context="{if (@context) then @context else '.'}" xxforms:readonly-appearance="dynamic">
                <xforms:case id="case-{@id}-closed" selected="{if (@open = 'false') then 'true' else 'false'}">
                    <xhtml:div>
                        <xhtml:h2>
                            <xforms:group appearance="xxforms:internal">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xhtml:img src="../../../../apps/fr/style/plus.png" alt="Open section" title="Open section"/>
                                    </xforms:label>
                                </xforms:trigger>
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xsl:apply-templates select="xforms:label"/>
                                    </xforms:label>
                                </xforms:trigger>
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')"
                                                     value="concat('{@id}', if (empty(event('repeat-indexes'))) then '' else concat('·', string-join(event('repeat-indexes'), '-')))"/>
                                    <xforms:dispatch target="fr-sections-model" name="fr-expand"/>
                                </xforms:action>
                            </xforms:group>
                        </xhtml:h2>
                    </xhtml:div>
                </xforms:case>
                <xforms:case id="case-{@id}-open" selected="{if (not(@open = 'false')) then 'true' else 'false'}">
                    <xhtml:div>
                        <xhtml:h2>
                            <xforms:group appearance="xxforms:internal">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xhtml:img src="../../../../apps/fr/style/minus.png" alt="Close section" title="Close section"/>
                                    </xforms:label>
                                </xforms:trigger>
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xsl:apply-templates select="xforms:label"/>
                                    </xforms:label>
                                </xforms:trigger>
                                <xforms:action ev:event="DOMActivate">
                                    <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')"
                                                     value="concat('{@id}', if (empty(event('repeat-indexes'))) then '' else concat('·', string-join(event('repeat-indexes'), '-')))"/>
                                    <xforms:dispatch target="fr-sections-model" name="fr-collapse" />
                                </xforms:action>
                            </xforms:group>
                        </xhtml:h2>
                        <xhtml:div class="fr-collapsible">
                            <!-- Section content -->
                            <xsl:apply-templates select="* except xforms:label"/>
                        </xhtml:div>
                    </xhtml:div>
                </xforms:case>
            </xforms:switch>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="xhtml:body//fr:grid">
        <xhtml:table class="fr-grid fr-grid-{@columns}-columns">
            <!-- Grid content -->
            <xsl:apply-templates select="* except xforms:label"/>
        </xhtml:table>
    </xsl:template>

    <xsl:template match="fr:optional-element">
        <xforms:input ref="{@ref}">
            <xsl:apply-templates select="xforms:label"/>
        </xforms:input>
        <xforms:trigger ref=".[not({@ref})]" appearance="minimal">
            <xforms:label>
                <xhtml:img src="../../../../apps/fr/style/add.gif"/>
                Add <xsl:value-of select="lower-case(xforms:label)"/>
            </xforms:label>
            <xforms:insert ev:event="DOMActivate" origin="instance('templates')/{@ref}" nodeset="{@after}"/>
        </xforms:trigger>
        <xforms:trigger ref=".[{@ref}]" appearance="minimal">
            <xforms:label><xhtml:img src="../../../../apps/fr/style/remove.gif"/></xforms:label>
            <!--<xforms:label>Remove <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>-->
            <xforms:delete ev:event="DOMActivate" nodeset="{@ref}"/>
        </xforms:trigger>
    </xsl:template>

    <!-- Helper for repeats -->
    <xsl:template match="xhtml:body//fr:repeat">
        <xsl:variable name="tokenized-path" select="tokenize(@nodeset, '/')"/>
        <xsl:variable name="min-occurs" select="if (@minOccurs) then @minOccurs else 0"/>
        <xsl:variable name="max-occurs" select="if (@maxOccurs) then @maxOccurs else 'unbounded'"/>
        <xhtml:table class="fr-repeat-table">
            <xforms:group appearance="xxforms:internal">
                <xhtml:tr>
                    <xhtml:td/>
                    <xhtml:td>
                        <xforms:trigger appearance="minimal" ref=".[{if ($max-occurs = 'unbounded') then 'true()' else concat('count(', @nodeset, ') lt ', $max-occurs)}]">
                            <xforms:label><xhtml:img src="../../../../apps/fr/style/add.gif"/></xforms:label>
                        </xforms:trigger>
                    </xhtml:td>
                    <xhtml:td style="width: 100%" colspan="{max(for $tr in xhtml:tr return count($tr/xhtml:td))}">
                        <xforms:trigger appearance="minimal" ref=".[{if ($max-occurs = 'unbounded') then 'true()' else concat('count(', @nodeset, ') lt ', $max-occurs)}]">
                            <xforms:label>Add <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>
                        </xforms:trigger>
                    </xhtml:td>
                </xhtml:tr>
                <xforms:insert ev:event="DOMActivate"
                               origin="{if (@origin) then @origin else concat('instance(''templates'')/', $tokenized-path[last()])}"
                               context="."
                               nodeset="{if (@after) then @after else @nodeset}"/>
                <!-- TODO: handle @at -->
                <!-- at="index('{@id}')" position="after" -->
            </xforms:group>
            <xforms:repeat nodeset="{@nodeset}" id="{@id}">
                <xhtml:tbody>
                    <xhtml:tr>
                        <xhtml:td>
                            <xforms:output value="count(preceding-sibling::{$tokenized-path[last()]}) + 1"/>
                        </xhtml:td>
                        <xhtml:td>
                            <xforms:group>
                                <xforms:trigger appearance="minimal" ref=".[count(../{@nodeset}) gt {$min-occurs}]">
                                    <xforms:label><xhtml:img src="../../../../apps/fr/style/remove.gif"/></xforms:label>
                                </xforms:trigger>
                                <xforms:delete ev:event="DOMActivate" nodeset="."/>
                            </xforms:group>
                        </xhtml:td>
                        <xsl:apply-templates select="xhtml:tr[1]/xhtml:td"/>
                    </xhtml:tr>
                </xhtml:tbody>
                <xhtml:tbody>
                    <xsl:apply-templates select="xhtml:tr except xhtml:tr[1] | xhtml:td" mode="prepend-td"/>
                </xhtml:tbody>
            </xforms:repeat>
        </xhtml:table>
    </xsl:template>

    <!-- This not a component really, but  -->
    <xsl:template match="/xhtml:html/xhtml:head/xforms:model[1]">

        <!-- This model handles form sections -->
        <xforms:model id="fr-sections-model" xxforms:external-events="fr-after-collapse" xxforms:readonly-appearance="{if (doc('input:instance')/*/mode = 'view') then 'static' else 'dynamic'}">
            <!-- Contain section being currently expanded/collapsed -->
            <!-- TODO: This probably doesn't quite work for sections within repeats -->
            <xforms:instance id="fr-current-section-instance">
                <section xmlns=""/>
            </xforms:instance>

            <!-- Handle section collapse -->
            <xforms:action ev:event="fr-after-collapse">
                <xforms:toggle case="case-{{instance('fr-current-section-instance')}}-closed"/>
            </xforms:action>

            <!-- Close section -->
            <xforms:action ev:event="fr-collapse">
                <xxforms:script>document.body.blur(); frCollapse();</xxforms:script>
            </xforms:action>
            
            <!-- Open section -->
            <xforms:action ev:event="fr-expand">
                <xforms:toggle case="case-{{instance('fr-current-section-instance')}}-open"/>
                <xxforms:script>document.body.blur(); frExpand();</xxforms:script>
            </xforms:action>
        </xforms:model>

        <xforms:model id="fr-help-model">
            <xforms:instance id="fr-help-instance">
                <help xmlns="">
                    <label/>
                    <help/>
                </help>
            </xforms:instance>
        </xforms:model>

        <!-- Handle document persistence -->
        <xi:include href="includes/persistence-model.xml" xxi:omit-xml-base="true"/>
        <!-- Handle error summary -->
        <xi:include href="includes/error-summary-model.xml" xxi:omit-xml-base="true"/>

        <!-- Copy existing model -->
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>

            <!-- Mark status as dirty if data changes -->
            <xforms:setvalue ev:observer="fr-form-instance" ev:event="xforms-insert" ref="xxforms:instance('fr-persistence-instance')/data-status">dirty</xforms:setvalue>
            <xforms:setvalue ev:observer="fr-form-instance" ev:event="xforms-delete" ref="xxforms:instance('fr-persistence-instance')/data-status">dirty</xforms:setvalue>

        </xsl:copy>

        <!-- Handle collapsible sections -->
        <xi:include href="includes/collapse-script.xhtml" xxi:omit-xml-base="true"/>
        <!-- Handle checking dirty status -->
        <xi:include href="includes/check-dirty-script.xhtml" xxi:omit-xml-base="true"/>

    </xsl:template>

    <xsl:template match="xhtml:tr" mode="prepend-td">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xhtml:td/>
            <xhtml:td/>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
