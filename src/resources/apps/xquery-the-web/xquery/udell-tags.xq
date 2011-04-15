<p>
    {(: Try to run this on: http://weblog.infoworld.com/udell/rss.xml :)()}
    
    <a href="http://del.icio.us/">del.icio.us</a> {' '} tags for latest {' '}
    <a href="http://weblog.infoworld.com/udell/">Jon Udell blog</a> {' '} entries:
    <ul>
    {
    for $item in /rss/channel/item return
        let $deliciousURL := concat('http://del.icio.us/url/?url=', $item/link),
        $deliciousInXmlURL := <url>http://www.orbeon.com/ops/direct/xquery-the-web?output=xml&amp;url={encode-for-uri($deliciousURL)}&amp;xquery=/*</url>,
        $deliciousXML := doc($deliciousInXmlURL),
        $allTags as xs:string* := $deliciousXML//a[@class = 'delNav' 
            and not(starts-with(., '200')) 
            and matches(@href, '/.*/')]/text(),
        $unorderedTags as element()* := for $tag in distinct-values($allTags) return
            <tag count="{count($allTags[. = $tag])}">{$tag}</tag>,
        $orderedTags as element()* := for $tag in $unorderedTags 
            order by xs:integer($tag/@count) descending return $tag
        return
        <li>
            <a href="{$item/link}">{string($item/title)}</a>
            { if ($orderedTags) then
            <div style="margin-left: 1em">
                { for $tag in $orderedTags return 
                    let $size := if ($tag/@count = 1) then '80%' else '100%' return
                    <span style="font-size: {$size}">
                        <a href="http://del.icio.us/tag/{$tag/text()}">{$tag/text()}</a>
                        &#160;({string($tag/@count)})
                    </span>
                }
            </div>
            else ()
            }
        </li>
    }
    </ul>
</p>
