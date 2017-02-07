curl -X POST -H 'Content-type: application/json' \
  'http://netgear.rohidekar.com:7474/db/data/cypher' -d '
  {
     "query": "start n=node('$1') match n-[*]->d WHERE has(d.type) AND d.type = '\''categoryNode'\'' return id(d) + '\''  '\'' +  d.name;", "params":{}
  }'
