<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:saxon="http://saxon.sf.net/">

    <xsl:template match="@*|node()" priority="-100">
        <xsl:copy copy-namespaces="no">
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:output method="xml" omit-xml-declaration="yes" name="xml"/>

    <xsl:template match="/">
        <div>
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <xsl:template match="section">
        <xsl:element name="h{count(ancestor-or-self::section) + 1}">
            <xsl:choose>
                <xsl:when test="exists(title/*)">
                    <xsl:apply-templates select="title/node()"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="concat(substring(title, 1, 1), lower-case(substring(title, 2)))"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
        <xsl:apply-templates select="node() except title"/>
    </xsl:template>

    <xsl:template match="header">
        <h1><xsl:apply-templates select="title/node()"/></h1>
    </xsl:template>

    <xsl:template match="note">
        <div style="font-style: italic">
            NOTE: <xsl:apply-templates/>
        </div>
    </xsl:template>

    <xsl:template match="warning">
        <div>
            WARNING: <xsl:apply-templates/>
        </div>
    </xsl:template>

    <xsl:template match="ul">
        <xsl:choose>
            <xsl:when test="not(false() = (for $li in li return count($li/p) = 1))">
                <xsl:copy>
                    <xsl:apply-templates select="@*"/>
                    <xsl:for-each select="li">
                        <xsl:copy>
                            <xsl:apply-templates select="@* | p/node()"/>
                        </xsl:copy>
                    </xsl:for-each>
                </xsl:copy>
            </xsl:when>
            <xsl:otherwise>
                <xsl:next-match/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="document | body">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="table">
        <table border="1" bordercolor="#888888" cellspacing="0" style="border-color:rgb(136, 136, 136);border-width:1px;border-collapse:collapse">
            <xsl:apply-templates/>
        </table>
    </xsl:template>

    <xsl:template match="fork | link">
        <a>
            <xsl:apply-templates select="@* | node()"/>
        </a>
    </xsl:template>
    
    <xsl:template match="@href[not(matches(., '[a-z]+://'))]">
        <xsl:attribute name="href" select="concat('http://www.orbeon.com/orbeon/', if (starts-with(., '/')) then substring(., 2) else concat('doc/', .))"/>
    </xsl:template>

    <xsl:template match="figure | img">
        <img style="display: block; margin-right: auto; margin-left: auto; text-align: center">
            <xsl:copy-of select="@* except @print-format"/>
            <xsl:attribute name="src"
                           select="if (matches(@ref, '[a-z]+://')) then @src
                                   else
                                        if (starts-with(@src, '/')) then concat('http://www.orbeon.com/orbeon/2011', @src)
                                        else resolve-uri(@src, 'http://www.orbeon.com/orbeon/2011/doc/')"/>
        </img>
    </xsl:template>

    <xsl:template match="a[@name] | comment()"/>

    <xsl:template match="xml-source">
        <xsl:choose>
            <xsl:when test="exists(*)">
                <xsl:for-each select="*">
                    <xsl:variable name="indent" select="string-length(tokenize(string-join(preceding-sibling::text(), ''), '\n')[last()])"/>
                    <xsl:variable name="spaces" select="substring('                                                ', 1, $indent)"/>

                    <xsl:variable name="document" as="node()">
                        <xsl:apply-templates select="." mode="source"/>
                    </xsl:variable>

                    <blockquote>
                        <pre style="white-space: pre-wrap">
                            <div class="sites-codeblock sites-codesnippet-block">
                                <xsl:for-each select="
                                    for $ l in tokenize(saxon:serialize($document, 'xml'), '\n')
                                        return if (starts-with($l, $spaces)) then substring($l, $indent + 1) else $l">
                                    <code><xsl:value-of select="."/></code>
                                    <br/>
                                </xsl:for-each>
                            </div>
                        </pre>
                    </blockquote>
                </xsl:for-each>
            </xsl:when>
            <xsl:when test="normalize-space()">

                <xsl:variable name="all-lines" select="for $l in tokenize(., '\n') return normalize-space($l)"/>
                <xsl:variable name="empty-lines" select="for $l in $all-lines return if (normalize-space($l)) then true() else false()"/>
                <xsl:variable name="lines" select="subsequence($all-lines, index-of($empty-lines, false())[1])"/>

                <xsl:variable name="indent" select="string-length($lines[1]) - string-length(replace($lines[1], '^\s+(.*)', '$1'))"/>
                <xsl:variable name="spaces" select="substring('                                                ', 1, $indent)"/>

                <blockquote>
                    <pre style="white-space: pre-wrap">
                        <div class="sites-codeblock sites-codesnippet-block">
                            <xsl:for-each select="for $l in $lines return if (starts-with($l, $spaces)) then substring($l, $indent + 1) else $l">
                                <code><xsl:value-of select="."/></code>
                                <br/>
                            </xsl:for-each>
                        </div>
                    </pre>
                </blockquote>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>

    </xsl:template>

    <xsl:template match="comment" mode="source">
        <xsl:comment><xsl:value-of select="."/></xsl:comment>
    </xsl:template>

    <xsl:template match="@*|node()" mode="source">
        <xsl:copy copy-namespaces="yes">
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
