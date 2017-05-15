curl -X POST -H 'Content-type: application/json' \
  'http://netgear.rohidekar.com:7474/db/data/cypher' -d '
  {
     "query": "start n=node(*) match n-->d WHERE has(n.type) AND n.type = '\''categoryNode'\'' AND has(d.type) AND d.type = '\''categoryNode'\'' return id(n) + '\''  '\'' + id(d) ;", "params":{}
  }'
