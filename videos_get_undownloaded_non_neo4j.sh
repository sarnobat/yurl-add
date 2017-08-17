#!/bin/bash
diff --unchanged-line-format= --old-line-format= --new-line-format='%L' <(bash videos_in_category_non_neo4j.sh "$1" | sort) <(cat ~/sarnobat.git/db/yurl_flatfile_db/videos_download_succeeded.txt | perl -pe 's{::.*}{}g' | sort)
