<table>
    {(: Try this on: http://www.technorati.com/tag/ :)()}
    
    {
    let $tags := 
      for $k in //a[parent::em and encode-for-uri(.) = .] 
      return <tag popularity="{count($k/ancestor::em)}">{string($k)}</tag>
    
    let $sortedTags :=
      for $t in $tags order by xs:double($t/@popularity) descending
      return $t
    
    for $t in $sortedTags[position() le 2] return
      <tr>
        <th>{string($t)}</th>
        {
      let $images := doc(concat('http://www.orbeon.com/ops/direct/xquery-the-web?output=xml&amp;xquery=%3Cimages%3E+%7B%2F%2Fimg%5Bstarts-with%28%40src%2C+%27%2Fimages%27%29%5D%7D+%3C%2Fimage%3E&amp;url=http%3A%2F%2Fimages.google.com%2Fimages%3Fq%3D', string($)))
        for $i in $images[position() le 5] return
          <td>{$i}</td>
        }
      </tr>
    }
</table>