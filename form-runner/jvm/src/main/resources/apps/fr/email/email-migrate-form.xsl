<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:xf="http://www.w3.org/2002/xforms">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <xsl:template
        match="xf:instance[@id = 'fr-form-metadata']/metadata">
        <xsl:copy>
            <xsl:apply-templates select="@* | node() except email"/>
            <xsl:copy-of select="frf:serializeEmailMetadata(frf:parseEmailMetadata(email, /*))"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
