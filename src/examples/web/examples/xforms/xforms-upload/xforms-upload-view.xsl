<xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xsl:version="2.0">
    <xhtml:body>
        <xf:group ref="form">
            <table>
                <tr>
                    <td rowspan="3">
                        <xsl:text>Please select up to three</xsl:text>
<!--                        <xsl:if test="$uploaded"> other</xsl:if>-->
                        <xsl:text> images to upload:</xsl:text>
                    </td>
                    <td>
                        <xf:upload ref="files/file[1]">
                            <xf:filename ref="@filename"/>
                            <xf:mediatype ref="@mediatype"/>
                            <xxf:size ref="@size"/>
<!--                                    <xf:alert>-->
<!--                                        <font color="red">Image must be smaller than 150K</font>-->
<!--                                    </xf:alert>-->
                        </xf:upload>
                    </td>
                </tr>
                <tr>
                    <td>
                        <xf:upload ref="files/file[2]">
                            <xf:filename ref="@filename"/>
                            <xf:mediatype ref="@mediatype"/>
                            <xxf:size ref="@size"/>
<!--                                    <xf:alert>-->
<!--                                        <font color="red">Image must be smaller than 150K</font>-->
<!--                                    </xf:alert>-->
                        </xf:upload>
                    </td>
                </tr>
                <tr>
                    <td>
                        <xf:upload ref="files/file[3]">
                            <xf:filename ref="@filename"/>
                            <xf:mediatype ref="@mediatype"/>
                            <xxf:size ref="@size"/>
<!--                                    <xf:alert>-->
<!--                                        <font color="red">Image must be smaller than 150K</font>-->
<!--                                    </xf:alert>-->
                        </xf:upload>
                    </td>
                </tr>
                <tr>
                    <td>
                        Simple file upload
                    </td>
                    <td>
                        <xf:submit>
                            <xf:label>Upload</xf:label>
                            <xf:setvalue ref="action">simple-upload</xf:setvalue>
                        </xf:submit>
                    </td>
                </tr>
                <tr>
                    <td>
                        Web Service file upload (image must be smaller than 150K)
                    </td>
                    <td>
                        <xf:submit>
                    <xf:label>Upload</xf:label>
                    <xf:setvalue ref="action">ws-upload</xf:setvalue>
                </xf:submit>
                    </td>
                </tr>
                <tr>
                    <td>
                        Database file upload (requires setup)
                    </td>
                    <td>
                        <xf:submit>
                            <xf:label>Upload</xf:label>
                            <xf:setvalue ref="action">db-upload</xf:setvalue>
                        </xf:submit>
                    </td>
                </tr>
            </table>
            <xhtml:p>
            </xhtml:p>
            <!-- Display uploaded image -->
<!--            <xsl:if test="$uploaded">-->
<!--                <xsl:for-each select="/urls/url">-->
<!--                    <xsl:if test=". != ''">-->
<!--                        <xhtml:p>Uploaded image (<xf:output ref="files/file[{position()}]/@size"/> bytes):</xhtml:p>-->
<!--                        <xhtml:p>-->
<!--                            <img src="{.}"/>-->
<!--                        </xhtml:p>-->
<!--                    </xsl:if>-->
<!--                </xsl:for-each>-->
<!--            </xsl:if>-->
        </xf:group>
    </xhtml:body>
</xhtml:html>
