curl -X POST -H 'Content-type: application/json' \
  'http://netgear.rohidekar.com:7474/db/data/cypher' -d '
  {
     "query": "START n=node('$1') MATCH n-->c WHERE HAS(c.url) RETURN id(n) + '\''::'\'' + c.created + '\''::'\'' + c.url order by c.created asc", "params":{}
  }'
