curl -X POST -H 'Content-type: application/json' \
  'http://netgear.rohidekar.com:7474/db/data/cypher' -d '
  {
     "query": "START n=node(*) MATCH n-->c WHERE HAS(c.url) and HAS(n.type) RETURN  c.url + '\'' :: '\'' + c.title order by c.created asc", "params":{}
  }'
