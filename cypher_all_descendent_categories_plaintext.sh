sh cypher_all_descendent_categories.sh $1 | jq -r '.data[][0]'
