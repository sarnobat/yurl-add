#!/bin/bash
cd ~/github/yurl/
sh cypher_category_topology.sh | jq -r '.data[][0]' | tee yurl_category_topology.txt 

# Note the process substitution doesn't work in bourne shell
cat yurl_category_topology.txt | grep '^45' | perl -pe 's{(\d+)  (\d+)}{$2 : $1\,}g'  | cat <(echo "{") - <(echo "}")  >  yurl_categories_45.json
