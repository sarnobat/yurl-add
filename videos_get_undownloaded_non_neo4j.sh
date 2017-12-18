#!/bin/bash
touch ~/videos_get_undownloaded_non_neo4j_begin.confirmed
CATEGORY_ID="$1"
diff --unchanged-line-format= --old-line-format='%L' --new-line-format= <(bash ~/github/yurl/videos_in_category_non_neo4j.sh "$CATEGORY_ID" | sort | tee /tmp/videos_in_category_$CATEGORY_ID.txt) <(cat ~/sarnobat.git/db/yurl_flatfile_db/videos_download_succeeded.txt | perl -pe 's{::.*}{}g' | sort | tee /tmp/videos_already_downloaded_$CATEGORY_ID.txt)
touch ~/videos_get_undownloaded_non_neo4j_end.confirmed
