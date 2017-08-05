cd ~/github/yurl/sample_responses/uncategorized/
groovy yurl_uncategorized.groovy | tee generated.json

cd ~/github/yurl/sample_responses/uncategorized/urls/
groovy yurl_urls.groovy | tee generated.json