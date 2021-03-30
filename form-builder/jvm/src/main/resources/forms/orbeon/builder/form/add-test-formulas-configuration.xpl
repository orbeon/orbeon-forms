<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input"  name="data"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#data"/>
        <p:input name="config">
            <xsl:stylesheet
                    version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:xh="http://www.w3.org/1999/xhtml"
                    xmlns:xf="http://www.w3.org/2002/xforms">

                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <xsl:template match="/*/xh:head/xf:model[@id = 'fr-form-model']/xf:instance[@id = 'fr-form-metadata']/*">
                    <xsl:copy>
                        <xsl:apply-templates select="@* | (node() except (wizard | html-page-layout | formula-debugger))"/>
                        <formula-debugger>true</formula-debugger>
                        <html-page-layout>fluid</html-page-layout>
                    </xsl:copy>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>