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

    <xsl:variable
        name="controls-roots-for-pdf-appearance"
        select="
            if ($mode = ('pdf', 'test-pdf')) then
                ($body, /xh:html/xh:head/xbl:xbl/xbl:binding[p:has-class('fr-section-component')]/xbl:template)
            else
                ()"/>

    <xsl:function name="fr:direct-name-for-select1-element" as="xs:string">
        <xsl:param name="elem" as="element()"/>
        <xsl:value-of select="
            if (local-name($elem) = 'select1' and $elem/@appearance = 'dropdown') then
                'dropdown-select1'
            else if (local-name($elem) = 'select1' and $elem/@appearance = 'search') then
                'dropdown-select1-search'
            else
                local-name($elem)"/>
    </xsl:function>

    <!-- For now only support `xf:select1[appearance ~= dropdown]` and `xf:select1[appearance ~= search]`. The databound
         controls (`fr:databound-select1` and `fr:databound-select1`) don't currently have an `xf:select1` representation. -->
    <xsl:variable
        name="controls-to-check-for-pdf-appearance"
        select="$controls-roots-for-pdf-appearance//(
            fr:dropdown-select1         | (: output as `fr:dropdown-select1` or `xf:select1[appearance ~= dropdown]` :)
            fr:dropdown-select1-search  | (: output as `xf:select1[appearance ~= search]`                            :)
            xf:select1[
                @appearance = ('dropdown', 'search')
            ][
                exists(@fr:pdf-appearance) or
                exists(
                    map:get(
                        $select1-pdf-appearances,
                        fr:direct-name-for-select1-element(.)
                    )
                )
            ]
        )"/>

    <xsl:variable
        name="controls-to-check-for-pdf-appearance-ids"
        select="$controls-to-check-for-pdf-appearance/generate-id()"/>

    <!-- NOTE: The `xf:label` rule below matches `select1` controls and if it matches this one won't match. But we shouldn't have
         common cases where the `xf:label` is missing as Form Builder adds that element. -->
    <xsl:template
        match="
            fr:*      [generate-id() = $controls-to-check-for-pdf-appearance-ids] |
            xf:select1[generate-id() = $controls-to-check-for-pdf-appearance-ids]"
        mode="within-controls">
        <!-- For now this only applies to controls that have an `xf:select1` binding -->
        <xsl:element name="xf:select1">
            <xsl:apply-templates select="@* except (@appearance, @fr:pdf-appearance)" mode="#current"/>
            <xsl:attribute name="appearance" select="(@fr:pdf-appearance, map:get($select1-pdf-appearances, fr:direct-name-for-select1-element(.)))[1]"/>
            <xsl:apply-templates select="node()" mode="#current"/>
        </xsl:element>
    </xsl:template>

    <xsl:template
        match="xf:output[exists(xf:label) and empty(@appearance)]"
        mode="within-controls">
        <xsl:copy>
            <xsl:for-each select="$calculated-value-appearance[. != 'full']"><!-- `full` is the default so don't bother adding the attribute in this case -->
                <xsl:attribute name="appearance" select="."/>
            </xsl:for-each>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <!-- Add a default xf:alert for those fields which don't have one. Only do this within grids and dialogs. -->
    <!-- Q: Do we really need this? -->
    <xsl:template
        match="fr:grid//xf:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload', 'secret') and not(xf:alert)]"
        mode="within-controls">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" mode="#current"/>
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
            fr:iteration-label[exists(fr:param)]"
        mode="within-controls">

        <xsl:copy>
            <xsl:apply-templates select="@*" mode="#current"/>

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
