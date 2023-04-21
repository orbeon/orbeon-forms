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
         controls (`fr:databound-select1` and `fr:databound-select1-search`) don't currently have an `xf:select1`
         representation. -->
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

    <!-- Convert `xf:input` of type `date` and `time` to `fr:date` and `fr:time` -->
    <xsl:template
        mode="within-controls"
        match="xf:input[@bind]">
        <xsl:param name="binds-root" tunnel="yes"/>
        <xsl:variable name="bind-id" select="@bind"/>
        <xsl:choose>
            <xsl:when test="
                exists(
                    $binds-root//xf:bind[
                        @id   = $bind-id and
                        @type = ('xf:date', 'xs:date')
                    ]
                )">
                <fr:date>
                    <xsl:apply-templates select="@* | node()" mode="#current"/>
                    <!-- See other comment further "Q: Do we really need this?" -->
                    <xsl:if test="empty(xf:alert)">
                        <xf:alert ref="xxf:r('detail.labels.alert', '|fr-fr-resources|')"/>
                    </xsl:if>
                </fr:date>
            </xsl:when>
            <xsl:when test="
                exists(
                    $binds-root//xf:bind[
                        @id   = $bind-id and
                        @type = ('xf:time', 'xs:time')
                    ]
                )">
                <fr:time>
                    <xsl:apply-templates select="@* | node()" mode="#current"/>
                    <!-- See other comment further "Q: Do we really need this?" -->
                    <xsl:if test="empty(xf:alert)">
                        <xf:alert ref="xxf:r('detail.labels.alert', '|fr-fr-resources|')"/>
                    </xsl:if>
                </fr:time>
            </xsl:when>
            <xsl:otherwise>
                <xsl:next-match/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

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

    <!-- See also `fb.ControlOps` for the renaming part -->
    <xsl:template
        match="fr:number[exists(@prefix | @suffix)] | fr:currency[exists(@prefix | @suffix)]"
        mode="within-controls">
        <xsl:param name="library-name" as="xs:string?" tunnel="yes"/>
        <xsl:copy>
            <xsl:for-each select="@prefix | @suffix">
                <xsl:attribute name="{name(.)}" select="frf:replaceVarReferencesWithFunctionCalls(., ., true(), $library-name, ())"/>
            </xsl:for-each>
            <xsl:apply-templates select="@* except (@prefix | @suffix) | node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <!-- See also `fb.ControlOps` for the renaming part -->
    <xsl:template
        match="fr:databound-select1 | fr:databound-select1-search"
        mode="within-controls">
        <xsl:param name="library-name" as="xs:string?" tunnel="yes"/>
        <xsl:copy>
            <xsl:for-each select="@resource">
                <!-- Because this can generate `frf:controlVariableValue()` -->
                <xsl:namespace name="frf" select="'java:org.orbeon.oxf.fr.FormRunner'"/>
                <xsl:attribute name="{name(.)}" select="frf:replaceVarReferencesWithFunctionCalls(., ., true(), $library-name, ('fr-search-value', 'fr-search-page'))"/>
            </xsl:for-each>
            <xsl:apply-templates select="@* except (@resource) | node() except (xf:itemset, xf:item, xf:choices)" mode="#current"/>
            <xsl:apply-templates select="xf:itemset | xf:item | xf:choices" mode="within-databound-itemset"/>
        </xsl:copy>
    </xsl:template>

    <!-- See also `fb.ControlOps` for the renaming part -->
    <xsl:template
        match="@ref | @value | @label | @hint"
        mode="within-databound-itemset">
        <xsl:param name="library-name" as="xs:string?" tunnel="yes"/>
        <xsl:attribute name="{name(.)}" select="frf:replaceVarReferencesWithFunctionCalls(., ., false(), $library-name, ())"/>
    </xsl:template>

    <!-- Add a default xf:alert for those fields which don't have one. Only do this within grids and dialogs. -->
    <!-- Q: Do we really need this? -->
    <!-- NOTE: Lower priority so that `xf:input[@bind]` rules match. -->
    <xsl:template
        priority="-20"
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
            xf:label              [exists(fr:param)] |
            xf:help               [exists(fr:param)] |
            xf:hint               [exists(fr:param)] |
            xf:alert              [exists(fr:param)] |
            fr:text               [exists(fr:param)] |
            fr:iteration-label    [exists(fr:param)] |
            fr:add-iteration-label[exists(fr:param)]"
        mode="within-controls">

        <xsl:param name="library-name" as="xs:string?" tunnel="yes"/>

        <xsl:copy>
            <xsl:apply-templates select="@*" mode="#current"/>
            <xsl:attribute
                name="ref"
                select="
                    concat(
                        'xxf:r(''',
                            frf:controlNameFromId(../@id),
                            '.',
                            local-name(),
                            ''',''fr-form-resources'',',
                            fr:build-template-param-map(fr:param, $library-name, false(), ()),
                        ')'
                    )"/>
        </xsl:copy>
    </xsl:template>

    <xsl:function name="fr:build-template-param-map">
        <xsl:param name="params"                                as="element(*)*"/>
        <xsl:param name="library-name"                          as="xs:string?"/>
        <xsl:param name="for-pdf"                               as="xs:boolean"/>
        <xsl:param name="custom-css-class-to-control-names-map" as="element(_)?"/>

        <xsl:value-of
            select="
                concat(
                    'map:merge((',
                        string-join(
                            for $p in $params
                                return for $name in ($p/*:name, name($p))[1][p:non-blank()]
                                    return for $type in ($p/*:type, $p/@type)[1] (: element first as attribute can also exist with for example value `object` :)
                            return
                                concat(
                                    'map:entry(''',
                                        $name,
                                        ''',',
                                        if ($type = ('ExpressionParam', 'formula')) then
                                            fr:maybe-replace(
                                                concat(
                                                    'string((',
                                                    frf:replaceVarReferencesWithFunctionCalls(
                                                        $p/(*:expr, *:value)[1],
                                                        $p/(*:expr, *:value)[1],
                                                        false(),
                                                        $library-name,
                                                        ()
                                                    ),
                                                    ')[1])'
                                                ),
                                                $for-pdf
                                            )
                                        else if ($type = ('ControlValueParam', 'control-value')) then
                                            fr:maybe-replace(
                                                concat(
                                                    'string((',
                                                    frf:replaceVarReferencesWithFunctionCalls(
                                                        $p/(*:controlName, *:control-name, *:controlCssClass, *:control-css-class)[1],
                                                        if (exists($p/(*:controlName, *:control-name))) then
                                                            concat('$', $p/(*:controlName, *:control-name)[1])
                                                        else if (exists($p/(*:controlCssClass, *:control-css-class))) then
                                                            (
                                                                $custom-css-class-to-control-names-map/
                                                                    entry[
                                                                        @class = $p/(*:controlCssClass, *:control-css-class)
                                                                    ]/*/concat('$', name(.)),
                                                                ''''''
                                                            )[1]
                                                        else
                                                            error(),
                                                        false(),
                                                        $library-name,
                                                        ()
                                                    ),
                                                    ')[1])'
                                                ),
                                                $for-pdf
                                            )
                                        else if (
                                            $type = (
                                                ('LinkToEditPageParam',    'link-to-edit-page'),
                                                ('LinkToViewPageParam',    'link-to-view-page'),
                                                ('LinkToNewPageParam',     'link-to-new-page'),
                                                ('LinkToSummaryPageParam', 'link-to-summary-page'),
                                                ('LinkToHomePageParam',    'link-to-home-page'),
                                                ('LinkToFormsPageParam',   'link-to-forms-page'),
                                                ('LinkToAdminPageParam',   'link-to-admin-page'),
                                                ('LinkToPdfParam',         'link-to-pdf')
                                            )
                                        ) then
                                            fr:maybe-replace(
                                                concat(
                                                    'frf:buildLinkBackToFormRunner(''',
                                                    $type,
                                                    ''', ',
                                                    if ($p/*:token = 'true') then 'true()' else 'false()',
                                                    ')'
                                                ),
                                                $for-pdf
                                            )
                                        else if ($for-pdf and $type = (('PageNumberParam', 'page-number'), ('PageCountParam', 'page-count'))) then
                                            string-join(
                                                (
                                                    '''',
                                                    '&quot; counter(',
                                                    if ($type = ('PageNumberParam', 'page-number')) then 'page' else 'pages',
                                                    ', ',
                                                    ($p/*:format, 'decimal')[1],
                                                    ') &quot;',
                                                    ''''
                                                ),
                                                ''
                                            )
                                        else if ($for-pdf and $type = ('FormTitleParam', 'form-title')) then
                                            fr:maybe-replace('fr:form-title()', $for-pdf)
                                        else if ($for-pdf and $type = ('ImageParam', 'image')) then
                                            (: TODO: `*:url` :)
                                            if (exists($p/*:visible)) then
                                                concat(
                                                    'if (',
                                                    frf:replaceVarReferencesWithFunctionCalls(
                                                        $p/*:visible,
                                                        $p/*:visible,
                                                        false(),
                                                        $library-name,
                                                        ()
                                                    ),
                                                    ') then ''&quot; element(logo) &quot;'' else '''''
                                                )
                                            else
                                                '''&quot; element(logo) &quot;'''
                                        else
                                            error(),
                                    ')'
                                ),
                            ','
                        ),
                '))'
            )"/>

    </xsl:function>

</xsl:stylesheet>
