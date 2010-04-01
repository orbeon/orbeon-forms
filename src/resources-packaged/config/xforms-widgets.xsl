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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms" xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:widget="http://orbeon.org/oxf/xml/widget"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner" xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:xi="http://www.w3.org/2001/XInclude"
    xmlns:xxi="http://orbeon.org/oxf/xml/xinclude" xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Automatic inspector inclusion if configured so -->
    <xsl:variable name="has-inspector" as="xs:boolean" select="exists(//fr:xforms-inspector | //widget:xforms-instance-inspector)"/>

    <xsl:template match="xhtml:body[pipeline:property('oxf.epilogue.xforms.inspector') and not($has-inspector)]">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <fr:xforms-inspector id="orbeon-xforms-inspector"/>
        </xsl:copy>
    </xsl:template>

    <!-- Legacy: support for widget:tabs -->
    <xsl:template match="widget:tabs">
        <fr:tabview>
            <xsl:apply-templates select="@*|node()"/>
        </fr:tabview>
    </xsl:template>

    <xsl:template match="widget:tab">
        <fr:tab>
            <xsl:apply-templates select="@*|node()"/>
        </fr:tab>
    </xsl:template>

    <xsl:template match="widget:label">
        <fr:label>
            <xsl:apply-templates select="@*|node()"/>
        </fr:label>
    </xsl:template>

    <!-- Legacy: support for image appearance -->
    <xsl:template match="xforms:trigger[@appearance = 'xxforms:image'] | xforms:submit[@appearance = 'xxforms:image']">
        <xsl:copy>
            <!-- Copy all attributes but replace the appearance with "minimal" -->
            <xsl:copy-of select="@* except @appearance"/>
            <xsl:attribute name="appearance" select="'minimal'"/>
            <!-- Create label with embedded image -->
            <xforms:label>
                <xhtml:img>
                    <xsl:copy-of select="xxforms:img/@*"/>
                </xhtml:img>
            </xforms:label>
            <!-- Process the rest of the stuff -->
            <xsl:apply-templates select="node() except (xforms:label, xxforms:img)"/>
        </xsl:copy>
    </xsl:template>

    <!-- Legacy: support for widget:xforms-instance-inspector -->
    <xsl:template match="widget:xforms-instance-inspector">
        <fr:xforms-inspector>
            <xsl:apply-templates select="@*|node()"/>
        </fr:xforms-inspector>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-100">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
