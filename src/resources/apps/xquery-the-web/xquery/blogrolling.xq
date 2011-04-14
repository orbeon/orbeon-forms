<ul>
    {(: Try to run this on: http://rpc.blogrolling.com/rss.php?r=1a80e03ec214f1bac7f390fa31d80ba0 :)()}

    {for $i in /rss/channel/item return
        <li><a href="{$i/link}">{string($i/title)}</a></li>
    }
</ul>
