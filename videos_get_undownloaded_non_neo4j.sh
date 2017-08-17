#!/bin/bash
#
# Usage:
# arg 1 : the category ID
#

cat <(cat ~/sarnobat.git/db/yurl_flatfile_db/yurl_master.txt | grep -f ~/sarnobat.git/db/yurl_flatfile_db/categories_with_videos.txt) | groovy ~/github/yurl/newer_than.groovy 1502776328 | grep "^$1" | perl -pe 's{.*::(.*)::.*}{$1}g'
