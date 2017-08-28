#!/bin/bash
diff --unchanged-line-format= --old-line-format='%L' --new-line-format= <(bash videos_in_category_non_neo4j.sh "$1" | sort | tee /tmp/videos_in_category.txt) <(cat ~/sarnobat.git/db/yurl_flatfile_db/videos_download_succeeded.txt | perl -pe 's{::.*}{}g' | sort | tee /tmp/videos_already_downloaded.txt)
