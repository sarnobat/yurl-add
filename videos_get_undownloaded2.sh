sh ~/github/yurl/videos_get_undownloaded.sh | jq --raw-output '.data[][1]' | xargs -n1 -d'\n' groovy ~/github/yurl/video_download.groovy 2>&1 | tee -a ~/yurl_video_download_job.log;