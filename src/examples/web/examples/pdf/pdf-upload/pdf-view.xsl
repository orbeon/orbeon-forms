<!--
    Copyright (C) 2005 TAO Consulting Pte Ltd

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xxf="http://orbeon.org/oxf/xml/xforms" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xsl:version="2.0">
	<xhtml:head>
		<xhtml:title>PDF Upload: <xsl:value-of select="/result/PDFDocument/@title"/>
		</xhtml:title>
	</xhtml:head>
	<xhtml:body>
		<xsl:variable name="pdfprocessed" select="/result/PDFDocument !=''" as="xs:boolean"/>
		<xsl:variable name="in-portlet" select="/result/request/container-type = 'portlet'" as="xs:boolean"/>
		<xsl:if test="$pdfprocessed">
			<f:xml-source>
				<xsl:copy-of select="/result/PDFDocument"/>
			</f:xml-source>
		</xsl:if>
		<xsl:if test="/result/message">
			<p style="color: red">
				<xsl:value-of select="/result/message"/>
			</p>
		</xsl:if>
		<xf:group ref="form">
			<table>
				<tr>
					<td>
						<xsl:text>Please select a</xsl:text>
						<xsl:if test="$pdfprocessed">n other</xsl:if>
						<xsl:text> PDF File* to upload:</xsl:text>
					</td>
					<td>
						<xf:upload ref="file">
							<xf:filename ref="@filename"/>
							<xf:mediatype ref="@mediatype"/>
							<xxf:size ref="@size"/>
						</xf:upload>
						<p style="color: gray; font-size : smaller;">* If the file doesn't contain what you are looking for (text, bookmarks, meta data) or is defect nothing will be displayed.</p>
					</td>
				</tr>
			</table>
			<table class="gridtable">
				<tr>
					<th>
                        Bookmarks only
                    </th>
					<td>
						<xf:submit>
							<xf:label>Bookmarks</xf:label>
							<xf:setvalue ref="action">bookmarksonly</xf:setvalue>
						</xf:submit>
					</td>
					<td>
                        This shows the PDF Meta Structure (Meta and outline) without the text
                    </td>
				</tr>
				<tr>
					<th>
                        Bookmarks with text
                    </th>
					<td>
						<xf:submit>
							<xf:label>Bookmarks/Text</xf:label>
							<xf:setvalue ref="action">bookmarks</xf:setvalue>
						</xf:submit>
					</td>
					<td>
                        This shows the meta info, the bookmarks and the text
                    </td>
				</tr>
				<tr>
					<th>
                        Pages with text
                    </th>
					<td>
						<xf:submit>
							<xf:label>Pages</xf:label>
							<xf:setvalue ref="action">pages</xf:setvalue>
						</xf:submit>
					</td>
					<td>
                        This extract the Meta info and the text by pages
                    </td>
				</tr>
				<tr>
					<th>
                        Bookmarks or Pages
                    </th>
					<td>
						<xf:submit>
							<xf:label>Bookmarks/Pages</xf:label>
							<xf:setvalue ref="action">bookmarkpages</xf:setvalue>
						</xf:submit>
					</td>
					<td>
                        This extract the Meta info and then tries to extract the bookmarks with text.
                        If there are no bookmarks then the text by pages.
                    </td>
				</tr>
				<tr>
					<th>
                        Meta data
                    </th>
					<td>
						<xf:submit>
							<xf:label>Meta</xf:label>
							<xf:setvalue ref="action">meta</xf:setvalue>
						</xf:submit>
					</td>
					<td>
                        This extracts the Meta info only
                    </td>
				</tr>				
				<tr>
					<th>
                        Send it back
                    </th>
					<td>
						<xf:submit>
							<xsl:if test="$in-portlet">
								<xsl:attribute name="xhtml:disabled">true</xsl:attribute>
							</xsl:if>
							<xf:label>PDF</xf:label>
							<xf:setvalue ref="action">test</xf:setvalue>
						</xf:submit>
					</td>
					<td>
                        This sends back the PDF as PDF. Works only outside the portal.
                        <xsl:choose>
							<xsl:when test="$in-portlet">
                                Click
                                <xf:submit xxf:appearance="link">
									<xf:label>here</xf:label>
									<xf:setvalue ref="action">goto-simple-pdf</xf:setvalue>
								</xf:submit>
                                to try.
                            </xsl:when>
							<xsl:otherwise>
                                You can try it now.
                            </xsl:otherwise>
						</xsl:choose>
					</td>
				</tr>
			</table>
			<xhtml:p>
            </xhtml:p>
		</xf:group>
	</xhtml:body>
</xhtml:html>
