<?xml version="1.0" encoding="utf-8"?>
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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Unrolled XHTML+XForms -->
    <p:param type="input" name="xforms"/>
    <!-- Request parameters -->
    <p:param type="input" name="parameters"/>
    <!-- PDF document -->
    <p:param type="output" name="data"/>

    <!-- Get form data if it exists -->
    <!-- This will be the case if somebody has POSTed the data and detail-model.xpl has stored it into the request -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>fr-form-data</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:output name="data" id="request-form-data"/>
    </p:processor>

    <!-- If no request form data found, get form data -->
    <p:choose href="#request-form-data">
        <p:when test="not(/null/@xsi:nil='true')">
            <!-- Data found, just forward -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="#request-form-data"/>
                <p:output name="data" id="form-data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Retrieve data from persistence layer -->
            <!-- This is necessary in the case of PDF template only, because in the other cases the data is loaded by XForms -->
            <p:processor name="oxf:url-generator">
            <p:input name="config" transform="oxf:unsafe-xslt" href="#parameters">
                <config xsl:version="2.0">

                    <!-- Create URI based on properties -->
                    <xsl:variable name="resource"
                                  select="concat(pipeline:property(string-join(('oxf.fr.persistence.app.uri', /*/app, /*/form, 'data'), '.')),
                                            '/crud/', /*/app, '/', /*/form, '/data/', /*/document, '/data.xml')" as="xs:string"/>
                    <url>
                        <xsl:value-of select="pipeline:rewriteServiceURI($resource, true())"/>
                    </url>
                    <!-- Forward the same headers that the XForms engine forwards -->
                    <forward-headers><xsl:value-of select="pipeline:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
                </config>
            </p:input>
            <p:output name="data" id="form-data"/>
        </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- Obtain original form document -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>fr-form-definition</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:output name="data" id="form-document"/>
    </p:processor>

    <!-- Call up persistence layer to obtain the PDF file -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#form-document">
            <config xsl:version="2.0">
                <url>
                    <xsl:value-of select="pipeline:rewriteServiceURI(//xforms:instance[@id = 'fr-form-attachments']/*/pdf, true())"/>
                </url>
                <!-- Forward the same headers that the XForms engine forwards -->
                <forward-headers><xsl:value-of select="pipeline:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
                <!-- Produce binary so we do our own XML parsing -->
                <mode>binary</mode>
            </config>
        </p:input>
        <p:output name="data" id="pdf-template"/>
    </p:processor>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Create mapping file -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#form-data"/>
        <p:input name="xhtml" href="#form-document"/>
        <p:input name="request" href="#request"/>
        <p:input name="parameters" href="#parameters"/>
        <p:input name="config">
            <config xsl:version="2.0"
                    xmlns:xforms="http://www.w3.org/2002/xforms"
                    xmlns:xhtml="http://www.w3.org/1999/xhtml"
                    xmlns:fr="http://orbeon.org/oxf/xml/form-runner">


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
                <xsl:variable name="fr-resources" select="doc('oxf:/apps/fr/i18n/resources.xml')/*" as="element(resources)"/>
                <xsl:variable name="fr-current-resources" select="($fr-resources/resource[@xml:lang = $request-language], $fr-resources/resource[1])[1]" as="element(resource)"/>

                <!-- Template -->
                <template href="input:template" show-grid="false"/>

                <!-- Barcode -->
                <xsl:variable name="barcode-value" select="$request/parameters/parameter[name = 'document']/value" as="xs:string?"/>
                <xsl:if test="normalize-space($barcode-value) != '' and pipeline:property(string-join(('oxf.fr.detail.pdf.barcode', $parameters/app, $parameters/form), '.'))">
                    <group ref="/*" font-pitch="15.9" font-family="Courier" font-size="12">
                        <barcode left="50" top="780" height="15" value="'{$barcode-value}'"/>
                    </group>
                </xsl:if>

                <group ref="/*">
                    <!-- Iterate over sections -->
                    <xsl:for-each select="$xhtml/xhtml:body//fr:body//fr:section">
                        <xsl:variable name="section-name" select="substring-before(@bind, '-bind')" as="xs:string"/>
                        
                        <group ref="{$section-name}">

                            <!-- Iterate over nested grids OR section templates -->
                            <xsl:for-each select="fr:grid | *[resolve-QName(name(), .) = $components-qnames]">

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

                                <!-- Find grid -->
                                <xsl:variable name="grid" as="element(fr:grid)"
                                              select="if ($is-component) then $component-template//fr:grid[1] else ."/>

                                <!-- Iterate over the grid's children XForms controls -->
                                <xsl:for-each select="$grid//xforms:*[(@ref or @bind) and ends-with(@id, '-control')]">
                                    <xsl:variable name="control" select="." as="element()"/>
                                    <xsl:variable name="control-name" select="substring-before($control/@id, '-control')" as="xs:string"/>
                                    <!-- Obtain the value directly from the data (makes it easier to deal with itemsets) -->
                                    <!--<xsl:message>-->
                                        <!--aaa <xsl:value-of select="$section-name"/>-->
                                        <!--aaa <xsl:value-of select="$control-name"/>-->
                                        <!--aaa <xsl:value-of select="$data/*[local-name() = $section-name]/*[local-name() = $control-name]"/>-->
                                    <!--</xsl:message>-->
                                    <xsl:variable name="control-value" select="$data/*[local-name() = $section-name]/*[local-name() = $control-name]" as="xs:string?"/>

                                    <xsl:variable name="bind" select="$xhtml/xhtml:head/xforms:model//xforms:bind[@id = $control/@bind]" as="element(xforms:bind)?"/>
                                    <!--<xsl:variable name="path" select="if ($bind)-->
                                        <!--then string-join(($bind/ancestor-or-self::xforms:bind/@nodeset)[position() gt 1], '/')-->
                                        <!--else string-join(($control/ancestor-or-self::*/(@ref | @context)), '/')" as="xs:string"/>-->

                                    <!-- Here use section$field pattern -->
                                    <xsl:variable name="field-name" select="concat($section-name, '$', $control-name)"/>
                                    <xsl:choose>
                                        <xsl:when test="local-name($control) = ('select', 'select1')">
                                            <!-- Selection control -->
                                            <xsl:variable name="control-resources" select="$section-resources/*[local-name() = $control-name]" as="element()"/>

                                            <xsl:choose>
                                                <xsl:when test="local-name($control) = 'select' and $control/@appearance = 'full'">
                                                    <!-- Checkboxes: use control value and match export values in PDF -->
                                                    <!-- TODO: This doesn't work as Acrobat doesn't handle space-separated values -->
                                                    <field acro-field-name="'{$field-name}'" value="'{$control-value}'"/>
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
                        </group>
                    </xsl:for-each>
                </group>
            </config>
        </p:input>
        <p:output name="data" id="mapping"/>
    </p:processor>

    <!-- Produce PDF document -->
    <p:processor name="oxf:pdf-template">
        <p:input name="data" href="#form-data"/>
        <p:input name="model" href="#mapping"/>
        <p:input name="template" href="#pdf-template"/>
        <p:output name="data" ref="data"/>
    </p:processor>

    <!-- TODO: example of oxf:add-attribute processor adding content-disposition information -->
    <!-- TODO: build file name dynamically using requested document id? -->
    <!--<p:processor name="oxf:add-attribute">-->
        <!--<p:input name="data" href="#pdf-data"/>-->
        <!--<p:input name="config">-->
            <!--<config>-->
                <!--<match>/*</match>-->
                <!--<attribute-name>content-disposition</attribute-name>-->
                <!--<attribute-value>attachment; filename=form.pdf</attribute-value>-->
            <!--</config>-->
        <!--</p:input>-->
        <!--<p:output name="data" ref="data"/>-->
    <!--</p:processor>-->

</p:config>
