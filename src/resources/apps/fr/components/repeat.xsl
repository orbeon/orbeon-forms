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
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Helper for repeats -->
    <xsl:template match="xhtml:body//fr:repeat | xxforms:dialog//fr:repeat | xbl:binding/xbl:template//fr:repeat">
        <xsl:variable name="fr-repeat" select="."/>
        <!-- TODO: handle @bind here, probably not relevant -->
        <xsl:variable name="tokenized-path" select="tokenize(if (@nodeset) then @nodeset else @ref, '/')"/>
        <xsl:variable name="has-origin" as="xs:boolean" select="exists(@origin)"/>
        <xsl:variable name="min-occurs" select="if (@minOccurs) then @minOccurs else 0"/>
        <xsl:variable name="max-occurs" select="if (@maxOccurs) then @maxOccurs else 'unbounded'"/>
        <xsl:variable name="readonly" as="xs:boolean" select="@readonly = 'true'"/>
        <xsl:variable name="remove-constraint" select="@remove-constraint"/>
        <!-- As of 2010-09-23 only the "table" appearance is properly working -->
        <!--<xsl:variable name="is-table-appearance" as="xs:boolean" select="@appearance = 'xxforms:table'"/>-->
        <xsl:variable name="is-table-appearance" as="xs:boolean" select="true()"/>

        <xsl:variable name="repeat-expression" select="if (@nodeset) then @nodeset else if (@ref) then @ref else concat('xxforms:bind(''', @bind, ''')')" as="xs:string"/>

        <xhtml:div class="yui-dt">
            <xhtml:div class="yui-dt-hd">
                <xxforms:variable name="fr-repeat-sequence" select="{$repeat-expression}"/>
                <xhtml:table class="yui-dt-table fr-repeat {if ($is-table-appearance) then 'fr-repeat-table' else 'fr-repeat-sections'} fr-grid {if (@columns) then concat('fr-grid-', @columns, '-columns') else ()}">
                    <xhtml:thead class="yui-dt-hd">
                        <!-- Row with column headers -->
                        <xhtml:tr class="fr-dt-master-row">
                            <!--<xsl:if test="$has-origin and not($readonly)">-->
                                <!--<xforms:group ref=".[not(exforms:readonly(.))]">-->
                                    <!--<xhtml:div class="yui-dt-liner">-->
                                        <!--<xhtml:td class="fr-repeat-column fr-repeat-column-number"/>-->
                                    <!--</xhtml:div>-->
                                <!--</xforms:group>-->
                            <!--</xsl:if>-->
                            <xsl:if test="$has-origin and not($readonly)">
                                <xforms:group ref=".[not(exforms:readonly(.))]">
                                    <xhtml:th class="fr-repeat-column fr-repeat-column-trigger">
                                        <xhtml:div class="yui-dt-liner">
                                            <xxforms:variable name="can-add" as="boolean"
                                                              select="{if ($max-occurs = 'unbounded') then 'true()'
                                                                       else concat('count($fr-repeat-sequence) lt ', $max-occurs)}"/>
                                            <xforms:trigger appearance="minimal" ref=".[$can-add]">
                                                <!-- TODO: i18n of alt/title -->
                                                <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/plus_16.png" alt="Add" title="Add"/></xforms:label>
                                                <!--/apps/fr/style/images/silk/add.png-->
                                                <xforms:insert ev:event="DOMActivate"
                                                               origin="{if (@origin) then @origin else concat('instance(''templates'')/', $tokenized-path[last()])}"
                                                               context="." nodeset="{if (@after) then @after else if (@nodeset) then @nodeset else '$fr-repeat-sequence'}"
                                                               at="index('{$fr-repeat/@id}')"/>
                                            </xforms:trigger>
                                            <xforms:group ref=".[not($can-add)]">
                                                <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/plus_16g.png" alt=""/>
                                            </xforms:group>
                                        </xhtml:div>
                                    </xhtml:th>
                                </xforms:group>
                            </xsl:if>
                            <xsl:for-each select="(fr:body | xhtml:body)/(xhtml:tr[1] | fr:tr[1])/(xhtml:td | fr:td)/*[1]">
                                <xhtml:th>
                                    <xhtml:div class="yui-dt-liner">
                                        <xforms:group>
                                            <xsl:copy-of select="xforms:label | xforms:help"/><!-- | xforms:hint -->
                                        </xforms:group>
                                    </xhtml:div>
                                </xhtml:th>
                            </xsl:for-each>
                        </xhtml:tr>
                        <!-- Optional row(s) shown before the repeated rows -->
                        <xsl:for-each select="fr:header">
                            <xsl:apply-templates select="xhtml:tr" mode="prepend-td"/>
                            <xsl:apply-templates select="fr:tr" mode="prepend-td"/>
                        </xsl:for-each>
                    </xhtml:thead>
                    <xhtml:tbody class="yui-dt-data">
                        <!-- Repeated rows -->
                        <xsl:for-each select="fr:body | xhtml:body">
                            <!-- NOTE: Duplicate $repeat-expression here because if we use the XForms variable $fr-repeat-sequence,
                                 upon inserting a new iteration, currently the variable does not re-evaluated and therefore
                                 the repeat binding does not re-evaluate either. -->
                            <!--<xforms:repeat id="{$fr-repeat/@id}" ref="$fr-repeat-sequence">-->
                            <xforms:repeat id="{$fr-repeat/@id}" ref="{$repeat-expression}">
                                <xxforms:variable name="repeat-position" select="position()" as="xs:integer"/>
                                <!-- First line with data -->
                                <xhtml:tr class="{{string-join((if ($repeat-position mod 2 = 1) then 'yui-dt-even' else 'yui-dt-odd', if ($repeat-position = 1) then 'yui-dt-first' else ()), ' ')}}">
                                    <!--<xsl:if test="$has-origin and not($readonly)">-->
                                        <!--<xhtml:th class="fr-repeat-column fr-repeat-column-number">-->
                                            <!--<xforms:output value="position()"/>-->
                                        <!--</xhtml:th>-->
                                    <!--</xsl:if>-->
                                    <xsl:if test="$has-origin and not($readonly)">
                                        <xforms:group ref=".[not(exforms:readonly(.))]">
                                            <xhtml:td class="fr-repeat-column fr-repeat-column-trigger">
                                                <xhtml:div class="yui-dt-liner">
                                                    <!-- Remove trigger -->
                                                    <xxforms:variable name="can-remove" as="boolean"
                                                                      select="{if ($remove-constraint) then concat($remove-constraint, ' and ') else ''} {concat('count($fr-repeat-sequence) gt ', $min-occurs)}"/>
                                                    <xforms:trigger appearance="minimal" ref=".[$can-remove]">
                                                        <!-- TODO: i18n of alt/title -->
                                                        <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/delete_16.png" alt="Remove" title="Remove"/></xforms:label>
                                                        <!--/apps/fr/style/images/silk/bin.png-->
                                                        <xforms:delete ev:event="DOMActivate" nodeset="."/>
                                                    </xforms:trigger>
                                                    <xforms:group ref=".[not($can-remove)]">
                                                        <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/delete_16g.png" alt=""/>
                                                    </xforms:group>
                                                </xhtml:div>
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
                    </xhtml:tbody>
                    <xsl:if test="fr:footer">
                        <xhtml:tfoot>
                            <!-- Optional row(s) shown after the repeated rows -->
                            <xsl:for-each select="fr:footer">
                                <xsl:apply-templates select="xhtml:tr" mode="prepend-td"/>
                                <xsl:apply-templates select="fr:tr" mode="prepend-td"/>
                            </xsl:for-each>
                        </xhtml:tfoot>
                    </xsl:if>
                </xhtml:table>
            </xhtml:div>
        </xhtml:div>
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

    <xsl:template match="fr:repeat//xhtml:td">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:attribute name="class" select="string-join((@class, 'fr-grid-td'), ' ')"/>
            <xhtml:div class="yui-dt-liner fr-grid-content">
                <xsl:apply-templates/>
            </xhtml:div>
        </xsl:copy>
    </xsl:template>

    <!-- TODO: This is not used by FR at the moment, needs a bit of work  -->
    <!--<xsl:template match="fr:optional-element">-->
        <!--<xforms:input ref="{@ref}">-->
            <!--<xsl:apply-templates select="xforms:label"/>-->
        <!--</xforms:input>-->
        <!--<xforms:trigger ref=".[not({@ref})]" appearance="minimal">-->
            <!--<xforms:label>-->
                <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/plus_16.png" alt=""/>-->
                <!--&lt;!&ndash; TODO: i18n &ndash;&gt;-->
                <!--Add <xsl:value-of select="lower-case(xforms:label)"/>-->
            <!--</xforms:label>-->
            <!--<xforms:insert ev:event="DOMActivate" origin="instance('templates')/{@ref}" nodeset="{@after}"/>-->
        <!--</xforms:trigger>-->
        <!--<xforms:trigger ref=".[{@ref}]" appearance="minimal">-->
            <!--&lt;!&ndash; TODO: i18n of title &ndash;&gt;-->
            <!--<xforms:label><xhtml:img width="16" height="16" src="/apps/fr/images/silk/delete.png" alt="Remove" title="Remove"/></xforms:label>-->
            <!--<xforms:delete ev:event="DOMActivate" nodeset="{@ref}"/>-->
        <!--</xforms:trigger>-->
    <!--</xsl:template>-->

</xsl:stylesheet>
