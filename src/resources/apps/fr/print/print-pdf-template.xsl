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
<config xsl:version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">


    <xsl:variable name="data" select="/*" as="element()"/>
    <xsl:variable name="xhtml" select="doc('input:xhtml')/*" as="element(xhtml:html)"/>
    <xsl:variable name="request" select="doc('input:request')/*" as="element(request)"/>
    <xsl:variable name="parameters" select="doc('input:parameters')/*" as="element()"/>

    <!-- Reference components -->
    <!-- NOTE: Some of this logic for components is also in Form Builder -->
    <xsl:variable name="component-bindings" select="$xhtml//xbl:binding" as="element(xbl:binding)*"/>
    <xsl:variable name="components-qnames" select="for $t in $component-bindings//fb:template/*[1] return resolve-QName(name($t), $t)" as="xs:QName*"/>

    <!-- Get current resources -->
    <xsl:variable name="request-language" select="$request/parameters/parameter[name = 'fr-language']/value" as="xs:string"/>
    <xsl:variable name="resources-instance" select="$xhtml/xhtml:head/xforms:model[@id = 'fr-form-model']/xforms:instance[@id = 'fr-form-resources']/resources" as="element(resources)"/>
    <xsl:variable name="current-resources" select="$resources-instance/resource[@xml:lang = $request-language]" as="element(resource)"/>

    <!-- TODO: resources.xml should go through fr-resources.xpl so that overrides work -->
    <xsl:variable name="fr-resources" select="doc(pipeline:rewriteServiceURI('/fr/service/i18n/fr-resources/orbeon/dmv-14', true()))/*" as="element(resources)"/>
    <!--<xsl:variable name="fr-resources" select="doc('oxf:/apps/fr/i18n/resources.xml')/*" as="element(resources)"/>-->
    <xsl:variable name="fr-current-resources" select="($fr-resources/resource[@xml:lang = $request-language], $fr-resources/resource[1])[1]" as="element(resource)"/>

    <!-- Template -->
    <template href="input:template" show-grid="false"/>

    <!-- Barcode -->
    <!-- NOTE: Code 39 only take uppercase letters, hence we upper-case(…) -->
    <xsl:variable name="barcode-value" select="upper-case($parameters/document)" as="xs:string?"/>
    <xsl:if test="normalize-space($barcode-value) != '' and pipeline:property(string-join(('oxf.fr.detail.pdf.barcode', $parameters/app, $parameters/form), '.'))">
        <group ref="/*" font-pitch="15.9" font-family="Courier" font-size="12">
            <barcode left="50" top="780" height="15" value="'{$barcode-value}'"/>
        </group>
    </xsl:if>

    <group ref="/*">
        <!-- Iterate over top-level sections -->
        <xsl:for-each select="$xhtml/xhtml:body//fr:body//fr:section">

            <xsl:variable name="section-name" select="substring-before(@bind, '-bind')" as="xs:string"/>
            <xsl:variable name="section-holder" select="$data/*[local-name() = $section-name]" as="element()"/>

            <group ref="{$section-name}">

                <!-- Iterate over nested grids OR section templates -->
                <xsl:for-each select="fr:grid | fr:repeat | *[resolve-QName(name(), .) = $components-qnames]">

                    <!-- NOTE: Some of this logic for components is also in Form Builder -->
                    <!-- Current section content QName -->
                    <xsl:variable name="current-qname" select="resolve-QName(name(), .)" as="xs:QName"/>
                    <!-- Whether this is a component -->
                    <xsl:variable name="is-component" select="$current-qname = $components-qnames" as="xs:boolean"/>
                    <!-- Component template if this is a component (take first one) -->
                    <xsl:variable name="component-template" as="element(xbl:template)?"
                                  select="if ($is-component)
                                          then ($component-bindings[$current-qname = (for $t in .//fb:template/*[1] return resolve-QName(name($t), $t))]/xbl:template)[1]
                                          else ()"/>

                    <xsl:variable name="section-resources" as="element(resource)"
                                  select="if (not($is-component))
                                            then $current-resources
                                            else ($component-template//xforms:instance[@id = 'fr-form-resources']/resources/resource[@xml:lang = $request-language])[1]"/>

                    <xsl:variable name="is-repeat" select="$current-qname = xs:QName('fr:repeat')" as="xs:boolean"/>

                    <!-- Find grid -->
                    <!-- TODO: search for fr:repeat/fr:body in component too -->
                    <xsl:variable name="grid" as="element()"
                                      select="if ($is-component) then $component-template//fr:grid[1]
                                              else if ($is-repeat) then fr:body[1] else ."/>

                    <!-- Iterate over the grid's children XForms and XBL controls -->
                    <xsl:for-each select="$grid//(xforms:* | fr:*)[(@ref or @bind) and ends-with(@id, '-control')]">
                        <xsl:variable name="control" select="." as="element()"/>
                        <xsl:variable name="control-name" select="substring-before($control/@id, '-control')" as="xs:string"/>
                        <!-- Obtain the value directly from the data (makes it easier to deal with itemsets) -->
                        <!--<xsl:message>-->
                            <!--aaa <xsl:value-of select="$section-name"/>-->
                            <!--aaa <xsl:value-of select="$control-name"/>-->
                            <!--aaa <xsl:value-of select="$data/*[local-name() = $section-name]/*[local-name() = $control-name]"/>-->
                        <!--</xsl:message>-->

                        <xsl:variable name="bind" select="$xhtml/xhtml:head/xforms:model//xforms:bind[@id = $control/@bind]" as="element(xforms:bind)?"/>
                        <!--<xsl:variable name="path" select="if ($bind)-->
                            <!--then string-join(($bind/ancestor-or-self::xforms:bind/@nodeset)[position() gt 1], '/')-->
                            <!--else string-join(($control/ancestor-or-self::*/(@ref | @context)), '/')" as="xs:string"/>-->

                        <xsl:for-each select="if ($is-repeat) then $section-holder/* else $section-holder">
                            <xsl:variable name="iteration" select="position()" as="xs:integer"/>

                            <!-- Here use section$field$iteration pattern -->
                            <xsl:variable name="field-name" select="concat($section-name, '$', $control-name, if ($is-repeat) then concat('$', xs:string($iteration)) else '')"/>

                            <xsl:variable name="control-value" select="*[local-name() = $control-name]" as="xs:string?"/>

                            <xsl:choose>
                                <xsl:when test="local-name($control) = ('select', 'select1')">
                                    <!-- Selection control -->
                                    <xsl:variable name="control-resources" select="$section-resources/*[local-name() = $control-name]" as="element()"/>

                                    <xsl:choose>
                                        <xsl:when test="local-name($control) = 'select' and $control/@appearance = 'full'">
                                            <!-- Checkboxes: we expect to have a PDF field for each value for a name section$control$value, with a value of 'true' -->
                                            <xsl:for-each select="tokenize($control-value, '\s+')">
                                                <xsl:variable name="item-value" as="xs:string" select="."/>
                                                <field acro-field-name="'{$field-name}${$item-value}'" value="'true'"/>
                                            </xsl:for-each>
                                        </xsl:when>
                                        <xsl:when test="local-name($control) = 'select1' and $control/@appearance = 'full'">
                                            <!-- Radio buttons: use control value and match export values in PDF -->
                                            <field acro-field-name="'{$field-name}'" value="'{$control-value}'"/>
                                        </xsl:when>
                                        <xsl:when test="local-name($control) = 'select1'">
                                            <!-- Other single-selection controls: just use label -->
                                            <field acro-field-name="'{$field-name}'" value="'{$control-resources/item[value = $control-value]/label}'"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <!-- Other multiple-selection controls: just use the label -->
                                            <field acro-field-name="'{$field-name}'"
                                                   value="'{string-join(for $v in tokenize($control-value, '\s+')
                                                                return $control-resources/item[value = $v]/label, ' - ')}'"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xsl:when>
                                <xsl:when test="local-name($control) = ('image-attachment') and normalize-space($control-value) != ''">
                                    <!-- Image attachment -->
                                    <image acro-field-name="'{$field-name}'"
                                           href="{pipeline:rewriteServiceURI($control-value, true())}"/>
                                </xsl:when>
                                <xsl:when test="$bind/@type and substring-after($bind/@type, ':') = 'date'">
                                    <!-- Date -->
                                    <field acro-field-name="'{$field-name}'"
                                           value="'{if ($control-value castable as xs:date)
                                                    then format-date(xs:date($control-value), $fr-current-resources/print/formats/date, $request-language, (), ())
                                                    else $control-value}'"/>
                                </xsl:when>
                                <xsl:when test="$bind/@type and substring-after($bind/@type, ':') = 'time'">
                                    <!-- Time -->
                                    <field acro-field-name="'{$field-name}'"
                                           value="'{if ($control-value castable as xs:time)
                                                    then format-time(xs:time($control-value), $fr-current-resources/print/formats/time, $request-language, (), ())
                                                    else $control-value}'"/>
                                </xsl:when>
                                <xsl:when test="$bind/@type and substring-after($bind/@type, ':') = 'dateTime'">
                                    <!-- Date and time -->
                                    <field acro-field-name="'{$field-name}'"
                                           value="'{if ($control-value castable as xs:dateTime)
                                                    then format-dateTime(xs:dateTime($control-value), $fr-current-resources/print/formats/dateTime, $request-language, (), ())
                                                    else $control-value}'"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <!-- Other control -->
                                    <field acro-field-name="'{$field-name}'" value="'{replace($control-value, '''', '''''')}'"/>
                                </xsl:otherwise>
                            </xsl:choose>

                        </xsl:for-each>

                    </xsl:for-each>
                </xsl:for-each>
            </group>
        </xsl:for-each>
    </group>
</config>