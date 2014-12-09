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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the form -->
    <p:param type="input" name="data"/>
    <!-- Schema -->
    <p:param type="output" name="data"/>

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../unroll-form.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="unrolled-form-definition"/>
    </p:processor>

    <!-- Pre-process form -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#unrolled-form-definition"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <!-- Remove @relevant so can know about itemsets also for non-relevant controls -->
                <xsl:template match="xf:bind/@relevant | xf:validation/@relevant">
                    <xsl:attribute name="fr:has-relevant">true</xsl:attribute>
                </xsl:template>
                <!-- See https://github.com/orbeon/orbeon-forms/issues/1623 -->
                <xsl:template match="fr:dropdown-select1">
                    <xf:select1 appearance="minimal">
                        <xsl:apply-templates select="@* except @appearance | node()"/>
                    </xf:select1>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="form-without-relevant"/>
    </p:processor>

    <p:processor name="fr:xforms-to-schema">
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#form-without-relevant"/>
        <p:input name="annotated-document" href="#form-without-relevant"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
