<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:template match="/">
        <dummy-root>
            <xsl:apply-templates select="node()"/>
        </dummy-root>
    </xsl:template>

    <xsl:template match="*:a | *:b | *:i | *:ul | *:li | *:ol | *:p | *:span | *:u | *:div | *:br | *:strong | *:em" priority="2">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@*|node()"/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="*:script" priority="2"/>

    <xsl:template match="@*" priority="1">
        <xsl:copy-of select="."/>
    </xsl:template>

    <xsl:template match="*" priority="1">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="text()"><xsl:value-of select="."/></xsl:template>

 </xsl:stylesheet>