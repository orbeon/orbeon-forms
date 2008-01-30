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

    <xsl:template match="fr:view">
        <xforms:input model="fr-sections-model" ref="instance('fr-sections-instance')/@current" id="fr-current-section-input" class="xforms-disabled"/>

        <xhtml:img id="fr-top" src="/apps/fr/style/top.png" alt=""/>

        <xhtml:div id="fr-container">

            <xhtml:div class="fr-logo">
                <xhtml:h1><xsl:value-of select="xforms:label"/></xhtml:h1>
                <!-- Configurable logo -->
                <xhtml:img src="/forms/logo.gif" alt="Logo"/>
            </xhtml:div>
            <xhtml:div class="fr-separator"/>
            <xhtml:div class="fr-body">

                <xforms:group ref="/*">
                    <!-- Error summary: handle xforms-invalid event -->
                    <xforms:action ev:event="xforms-invalid" if="normalize-space(event('label')) != ''">
                        <xforms:action if="not(xxforms:instance('errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')])">
                            <xforms:insert context="xxforms:instance('errors-instance')" nodeset="error" origin="xxforms:instance('error-template')"/>
                            <xforms:setvalue ref="xxforms:instance('errors-instance')/error[index('errors-repeat')]/@id" value="event('target')"/>
                            <xforms:setvalue ref="xxforms:instance('errors-instance')/error[index('errors-repeat')]/@indexes" value="string-join(event('repeat-indexes'), '-')"/>
                        </xforms:action>
                        <xforms:setvalue ref="xxforms:instance('errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')]/@alert" value="event('alert')"/>
                        <xforms:setvalue ref="xxforms:instance('errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')]/@label" value="event('label')"/>
                    </xforms:action>
                    <!-- Error summary: handle xforms-valid -->
                    <xforms:action ev:event="xforms-valid" if="xxforms:instance('errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')]">
                        <xforms:delete nodeset="xxforms:instance('errors-instance')/error[@id = event('target') and @indexes = string-join(event('repeat-indexes'), '-')]"/>
                    </xforms:action>

                    <xforms:group appearance="xxforms:internal">
                        <!-- Clear message upon user interaction -->
                        <xforms:setvalue ev:event="DOMFocusIn" model="fr-persistence-model" ref="instance('persistence-instance')/message"/>
                        <!-- Main form content -->
                        <xsl:apply-templates select="* except xforms:label"/>
                    </xforms:group>

                </xforms:group>
            </xhtml:div>
            <xhtml:div class="fr-separator"/>
            <xhtml:div class="fr-footer">
                <!-- Display the toolbar and errors -->
                <xi:include href="oxf:/apps/fr/includes/toolbar-and-errors-view.xml" xxi:omit-xml-base="true"/>
            </xhtml:div>
        </xhtml:div>
        <xhtml:img id="fr-bottom" src="/apps/fr/style/bottom.png" alt=""/>
    </xsl:template>

    <xsl:template match="fr:section">
        <xhtml:div class="section-container">
            <xforms:group ref="{if (@ref) then @ref else '.'}">
                <xhtml:h2>
                    <xforms:group model="fr-sections-model" ref="instance('fr-sections-instance')/{@id}">
                        <xforms:group>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:setvalue ref="instance('fr-sections-instance')/@current" value="xxforms:context()/local-name()"/>
                                <xforms:dispatch target="fr-sections-model" name="fr-collapse-expand" />
                            </xforms:action>
                            <xforms:group ref="if (@open = 'true') then . else ()">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label><xhtml:img src="../../../../apps/fr/style/minus.png" alt=""/></xforms:label>
                                </xforms:trigger>
                            </xforms:group>
                            <xforms:group ref="if (@open = 'false') then . else ()">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label><xhtml:img src="../../../../apps/fr/style/plus.png" alt=""/></xforms:label>
                                </xforms:trigger>
                            </xforms:group>
                            <xforms:trigger appearance="minimal">
                                <xsl:apply-templates select="xforms:label"/>
                            </xforms:trigger>
                        </xforms:group>
                    </xforms:group>
                </xhtml:h2>
                <xforms:group ref=".[xxforms:instance('fr-sections-instance')/{@id}/@open = 'true']" class="section-{@id}" id="section-{@id}">

                    <xhtml:table class="fr-grid fr-grid-{@columns}-columns">
                        <!-- Section content -->
                        <xsl:apply-templates select="* except xforms:label"/>
                    </xhtml:table>
                </xforms:group>
            </xforms:group>
        </xhtml:div>
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
    <xsl:template match="fr:repeat">
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
    <xsl:template match="xforms:model[1]">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>

        <xforms:model id="fr-sections-model" xxforms:external-events="fr-after-collapse">
            <xforms:instance id="fr-sections-instance">
                <sections current="{(//fr:section)[1]/@id}" xmlns="">
                    <xsl:for-each select="//fr:section">
                        <xsl:element name="{@id}" namespace="">
                            <xsl:attribute name="open">true</xsl:attribute>
                        </xsl:element>
                    </xsl:for-each>
                </sections>
            </xforms:instance>

            <!-- Handle section collapse -->
            <xforms:action ev:event="fr-after-collapse">
                <xforms:setvalue ref="saxon:evaluate(concat('instance(''fr-sections-instance'')/', instance('fr-sections-instance')/@current, '/@open'))">false</xforms:setvalue>
            </xforms:action>
            <xforms:action ev:event="fr-collapse-expand">
                <!-- Close section -->
                <xforms:action if="saxon:evaluate(concat('instance(''fr-sections-instance'')/', instance('fr-sections-instance')/@current, '/@open')) = 'true'">
                    <xxforms:script>document.body.blur(); frCollapse();</xxforms:script>
                </xforms:action>
                <!-- Open section -->
                <xforms:action if="saxon:evaluate(concat('instance(''fr-sections-instance'')/', instance('fr-sections-instance')/@current, '/@open')) = 'false'">
                    <xforms:setvalue ref="saxon:evaluate(concat('instance(''fr-sections-instance'')/', instance('fr-sections-instance')/@current, '/@open'))">true</xforms:setvalue>
                    <xxforms:script>document.body.blur(); frExpand();</xxforms:script>
                </xforms:action>
            </xforms:action>
        </xforms:model>

        <!-- Handle document persistence -->
        <xi:include href="oxf:/apps/fr/includes/persistence-model.xml" xxi:omit-xml-base="true"/>
        <!-- Handle error summary -->
        <xi:include href="oxf:/apps/fr/includes/error-summary-model.xml" xxi:omit-xml-base="true"/>
        <!-- Handle collapsable sections -->
        <xi:include href="oxf:/apps/fr/includes/collapse-script.xhtml" xxi:omit-xml-base="true"/>

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
