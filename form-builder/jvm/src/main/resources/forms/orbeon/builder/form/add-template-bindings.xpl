<!--
  Copyright (C) 2012 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input"  name="data"/>
    <p:param type="input"  name="bindings"/>
    <p:param type="input"  name="for-form-builder"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data"             href="#data"/>
        <p:input name="bindings"         href="#bindings"/>
        <p:input name="for-form-builder" href="#for-form-builder"/>
        <p:input name="config">
            <xsl:stylesheet
                    version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:xh="http://www.w3.org/1999/xhtml"
                    xmlns:xf="http://www.w3.org/2002/xforms"
                    xmlns:xs="http://www.w3.org/2001/XMLSchema"
                    xmlns:xbl="http://www.w3.org/ns/xbl">

                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <!--
                    When publishing, only embed XBL for section templates that are in use. On the other hand, if we're
                    adding the section template XBL for Form Builder we embed all available section template XBL, so
                    they go through `annotate.xpl`, so they are already there and processed in case form authors add
                    a new section template after the form is loaded.
                 -->
                <xsl:variable
                    name="available-section-bindings"
                    as="element(xbl:binding)*"
                    select="doc('input:bindings')/*/xbl:xbl/xbl:binding[p:has-class('fr-section-component')]"/>
                <xsl:variable
                    name="possible-section-element-qnames"
                    as="xs:QName*"
                    select="for $e in /*/xh:body//*:section/*
                            return QName(namespace-uri($e), name($e))"/>
                <xsl:variable
                    name="for-form-builder"
                    as="xs:boolean"
                    select="doc('input:for-form-builder') = 'true'"/>
                <xsl:variable
                    name="bindings-to-insert"
                    select="
                        $available-section-bindings[
                            $for-form-builder or
                            resolve-QName(translate(@element, '|', ':'), .) = $possible-section-element-qnames
                        ]"/>

                <!-- Remove XBL for section templates, which might be present if we load a published form -->
                <xsl:template match="/*/xh:head/xbl:xbl[xbl:binding[@class = 'fr-section-component']]"/>

                <xsl:template match="/*/xh:head/xf:model[last()]">
                    <xsl:next-match/>
                    <xsl:if test="exists($bindings-to-insert)">
                        <xbl:xbl>
                            <xsl:apply-templates select="$bindings-to-insert"/>
                        </xbl:xbl>
                    </xsl:if>
                </xsl:template>

                <!-- Don't include FB metadata as it's unneeded -->
                <xsl:template match="xbl:xbl/fb:metadata | xbl:binding/fb:metadata" xmlns:fb="http://orbeon.org/oxf/xml/form-builder"/>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>