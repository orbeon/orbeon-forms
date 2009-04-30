<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:template match="/">
        <dummy-root>
            <xsl:apply-templates select="node()"/>
        </dummy-root>
    </xsl:template>

    <!-- Add safe elements to this list -->
    <xsl:template match="*:a | *:b | *:i | *:ul | *:li | *:ol | *:p | *:span | *:u | *:div | *:br | *:strong | *:em
                            | *:img | *:h1 | *:h2 | *:h3 | *:h4 | *:h5 | *:font
                            | *:table | *:tbody | *:tr | *:td | *:th" priority="2">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@*|node()"/>
        </xsl:element>
    </xsl:template>

    <!-- Remove unsafe scripts -->
    <xsl:template match="*:script" priority="2"/>

    <!-- Remove everything that looks like a JavaScript event handler or attribute -->
    <xsl:template match="@*[not(starts-with(local-name(), 'on')) and not(starts-with(., 'javascript:'))]" priority="2">
        <xsl:copy-of select="."/>
    </xsl:template>

    <!-- Remove all the other attributes -->
    <xsl:template match="@*" priority="1"/>

    <!-- Copy everything else -->
    <xsl:template match="*" priority="1">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="text()"><xsl:value-of select="."/></xsl:template>

 </xsl:stylesheet>
