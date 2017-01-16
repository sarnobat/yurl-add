curl -X POST -H 'Content-type: application/json' \
  'http://netgear.rohidekar.com:7474/db/data/cypher' -d '
  {
     "query": "START n=node(37658) MATCH n-->c WHERE HAS(c.url) AND NOT HAS(c.downloaded_video) RETURN c.url", "params":{}
  }'