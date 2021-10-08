<xsl:stylesheet
    version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:xbl="http://www.w3.org/ns/xbl"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:array="http://www.w3.org/2005/xpath-functions/array">


    <xsl:template match="
            xh:body//xf:output[exists(xf:label) and empty(@appearance)] |
            xbl:binding/xbl:template//xf:output[exists(xf:label) and empty(@appearance)]">
        <xsl:copy>
            <xsl:for-each select="$calculated-value-appearance[. != 'full']"><!-- `full` is the default so don't bother adding the attribute in this case -->
                <xsl:attribute name="appearance" select="."/>
            </xsl:for-each>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Add a default xf:alert for those fields which don't have one. Only do this within grids and dialogs. -->
    <!-- Q: Do we really need this? -->
    <xsl:template
        match="
            xh:body//fr:grid//xf:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload', 'secret') and not(xf:alert)]
          | xh:body//xxf:dialog//xf:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload', 'secret') and not(xf:alert)]">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xf:alert ref="xxf:r('detail.labels.alert', '|fr-fr-resources|')"/>
        </xsl:copy>
    </xsl:template>

    <!-- Process dynamic LHHA with parameters -->
    <xsl:template
        match="
            xf:label          [exists(fr:param)] |
            xf:help           [exists(fr:param)] |
            xf:hint           [exists(fr:param)] |
            xf:alert          [exists(fr:param)] |
            fr:text           [exists(fr:param)] |
            fr:iteration-label[exists(fr:param)]">

        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <xsl:variable
                name="expr"
                select="
                    concat(
                        'xxf:r(''',
                            frf:controlNameFromId(../@id),
                            '.',
                            local-name(),
                            ''',''fr-form-resources'',',
                            'map:merge((',
                                string-join(
                                    for $p in fr:param
                                    return
                                        concat(
                                            'map:entry(''',
                                                $p/fr:name[1],
                                                ''',',
                                                if ($p/@type = 'ExpressionParam') then
                                                    concat(
                                                        'string((',
                                                        $p/fr:expr,
                                                        ')[1])'
                                                    )
                                                else if ($p/@type = 'ControlValueParam') then
                                                    concat(
                                                        'for $a in fr:control-typed-value(''',
                                                        $p/fr:controlName,
                                                        ''', false()) return if (array:size($a) = 0) then () else array:get($a, 1)'
                                                    )
                                                else if (
                                                    $p/@type = (
                                                        'LinkToEditPageParam',
                                                        'LinkToViewPageParam',
                                                        'LinkToNewPageParam',
                                                        'LinkToSummaryPageParam',
                                                        'LinkToHomePageParam',
                                                        'LinkToPdfParam'
                                                    )
                                                ) then
                                                    concat(
                                                        'frf:buildLinkBackToFormRunner(''',
                                                         $p/@type,
                                                        ''')'
                                                    )
                                                else
                                                    error(),
                                            ')'
                                        ),
                                    ','
                                ),
                            '))',
                        ')'
                    )
                "/>

            <xsl:attribute
                name="ref"
                select="$expr"/>

        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
