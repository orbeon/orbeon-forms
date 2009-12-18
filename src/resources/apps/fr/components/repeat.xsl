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
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Helper for repeats -->
    <xsl:template match="xhtml:body//fr:repeat | xxforms:dialog//fr:repeat | xbl:binding/xbl:template//fr:repeat">
        <xsl:variable name="fr-repeat" select="."/>
        <!-- TODO: handle @bind here, probably not relevant -->
        <xsl:variable name="tokenized-path" select="tokenize(@nodeset, '/')"/>
        <xsl:variable name="min-occurs" select="if (@minOccurs) then @minOccurs else 0"/>
        <xsl:variable name="max-occurs" select="if (@maxOccurs) then @maxOccurs else 'unbounded'"/>
        <xsl:variable name="readonly" as="xs:boolean" select="if (@readonly) then @readonly = 'true' else false()"/>
        <xsl:variable name="remove-constraint" select="@remove-constraint"/>
        <xsl:variable name="is-table-appearance" as="xs:boolean" select="@appearance = 'xxforms:table'"/>
        <xhtml:table class="fr-repeat {if ($is-table-appearance) then 'fr-repeat-table' else 'fr-repeat-sections'} {if (@columns) then concat('fr-grid-', @columns, '-columns') else ()}">
            <!-- Row with column headers -->
            <xhtml:tr>
                <xsl:if test="not($readonly)">
                    <xforms:group ref=".[not(exforms:readonly(.))]">
                        <xhtml:td class="fr-repeat-column fr-repeat-column-number"/>
                    </xforms:group>
                </xsl:if>
                <xhtml:td class="fr-repeat-column fr-repeat-column-trigger">
                    <xsl:if test="not($readonly)">
                        <xforms:group ref=".[not(exforms:readonly(.))]">
                            <xforms:trigger appearance="minimal" ref=".[{if ($max-occurs = 'unbounded') then 'true()' else concat('count(', if (@nodeset) then @nodeset else concat('xxforms:bind(''', @bind, ''')'), ') lt ', $max-occurs)}]">
                                <!-- TODO: i18n of title -->
                                <!--<xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/add.png" alt="Add" title="Add"/></xforms:label>-->
                                <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/plus_16.png" alt="Add" title="Add"/></xforms:label>
                            </xforms:trigger>
                            <xforms:insert ev:event="DOMActivate"
                                           origin="{if (@origin) then @origin else concat('instance(''templates'')/', $tokenized-path[last()])}"
                                           context="." nodeset="{if (@after) then @after else if (@nodeset) then @nodeset else concat('xxforms:bind(''', @bind, ''')')}"/>
                            <!-- TODO: handle @at -->
                            <!-- at="index('{@id}')" position="after" -->
                        </xforms:group>
                    </xsl:if>
                </xhtml:td>
                <xsl:for-each select="fr:body/(xhtml:tr[1] | fr:tr[1])/(xhtml:td | fr:td)/*[1]">
                    <xhtml:th>
                        <xforms:output value="''" class="fr-hidden"><!-- hide the actual output control -->
                            <xsl:copy-of select="xforms:label | xforms:help | xforms:hint"/>
                        </xforms:output>
                    </xhtml:th>
                </xsl:for-each>
            </xhtml:tr>
            <!-- Optional row(s) shown before the repeated rows -->
            <xsl:for-each select="fr:header">
                <xsl:apply-templates select="xhtml:tr" mode="prepend-td"/>
                <xsl:apply-templates select="fr:tr" mode="prepend-td"/>
            </xsl:for-each>
            <!-- Repeated rows -->
            <xsl:for-each select="fr:body">
                <xforms:repeat id="{$fr-repeat/@id}">
                    <xsl:if test="$fr-repeat/@nodeset"><xsl:attribute name="nodeset" select="$fr-repeat/@nodeset"/></xsl:if>
                    <xsl:if test="$fr-repeat/@bind"><xsl:attribute name="bind" select="$fr-repeat/@bind"/></xsl:if>
                    <xxforms:variable name="repeat-position" select="position()"/>
                    <!-- First line with data -->
                    <xhtml:tr>
                        <xhtml:th class="fr-repeat-column fr-repeat-column-number">
                            <xforms:output value="position()"/>
                        </xhtml:th>
                        <xsl:if test="not($readonly)">
                            <xforms:group ref=".[not(exforms:readonly(.))]">
                                <xhtml:td class="fr-repeat-column fr-repeat-column-trigger">
                                    <xforms:group>
                                        <!-- Remove trigger -->
                                        <xforms:trigger appearance="minimal" ref="if (
                                                {if ($remove-constraint) then concat($remove-constraint, ' and ') else ''}
                                                    count(xxforms:repeat-nodeset()) gt {$min-occurs}) then . else ()">
                                            <!-- TODO: i18n of title -->
                                            <!--<xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/bin.png" alt="Remove" title="Remove"/></xforms:label>-->
                                            <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/delete_16.png" alt="Remove" title="Remove"/></xforms:label>
                                        </xforms:trigger>
                                        <xforms:delete ev:event="DOMActivate" nodeset="."/>
                                    </xforms:group>
                                </xhtml:td>
                            </xforms:group>
                        </xsl:if>
                        <xsl:apply-templates select="(xhtml:tr[1] | fr:tr[1])/(xhtml:td | fr:td)"/>
                    </xhtml:tr>
                    <!-- Following lines with data if any -->

                    <xsl:apply-templates select="xhtml:tr except xhtml:tr[1] | xhtml:td" mode="prepend-td"/>
                    <xsl:apply-templates select="fr:tr except fr:tr[1] | fr:td" mode="prepend-td"/>
                </xforms:repeat>
                <!-- IE display HACK -->
                <xhtml:tr class="fr-repeat-last-line"><xhtml:td/></xhtml:tr>
            </xsl:for-each>
            <!-- Optional row(s) shown after the repeated rows -->
            <xsl:for-each select="fr:footer">
                <xsl:apply-templates select="xhtml:tr" mode="prepend-td"/>
                <xsl:apply-templates select="fr:tr" mode="prepend-td"/>
            </xsl:for-each>
        </xhtml:table>
    </xsl:template>

    <xsl:template match="fr:tr" mode="prepend-td">
        <xhtml:tr>
            <xsl:copy-of select="@*"/>
            <xhtml:td/>
            <xhtml:td/>
            <xsl:apply-templates/>
        </xhtml:tr>
    </xsl:template>

    <xsl:template match="xhtml:tr" mode="prepend-td">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xhtml:td/>
            <xhtml:td/>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <!-- TODO: This is not used by FR at the moment, needs a bit of work  -->
    <xsl:template match="fr:optional-element">
        <xforms:input ref="{@ref}">
            <xsl:apply-templates select="xforms:label"/>
        </xforms:input>
        <xforms:trigger ref=".[not({@ref})]" appearance="minimal">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/plus_16.png" alt=""/>
                <!-- TODO: i18n -->
                Add <xsl:value-of select="lower-case(xforms:label)"/>
            </xforms:label>
            <xforms:insert ev:event="DOMActivate" origin="instance('templates')/{@ref}" nodeset="{@after}"/>
        </xforms:trigger>
        <xforms:trigger ref=".[{@ref}]" appearance="minimal">
            <!-- TODO: i18n of title -->
            <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/images/silk/delete.png" alt="Remove" title="Remove"/></xforms:label>
            <xforms:delete ev:event="DOMActivate" nodeset="{@ref}"/>
        </xforms:trigger>
    </xsl:template>

</xsl:stylesheet>
