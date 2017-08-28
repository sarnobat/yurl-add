cd ~/github/yurl/sample_responses/uncategorized/
groovy yurl_uncategorized.groovy | tee generated.json

cd ~/github/yurl/sample_responses/uncategorized/urls/
groovy yurl_urls.groovy | tee generated.json

cd ~/github/yurl/sample_responses/uncategorized/categoriesRecursive/
groovy yurl_categoriesRecursive.groovy | tee generated.json

cd ~/github/yurl/sample_responses/uncategorized/categoriesRecursive/children/
groovy yurl_children.groovy | tee generated.json