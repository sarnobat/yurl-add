#!/bin/sh
while true
do
{
	echo 'Done' |  nc -l -p 8089   | grep "GET"
} | grep "HTTP" \
| perl -pe 's{.*param1=(.*).HTTP.1.1}{$1}g' \
| perl -pe 's/%([0-9a-f]{2})/sprintf("%s", pack("H2",$1))/eig' \
| tee -a ~/work.txt; echo "" \
| tee -a ~/work.txt; 
done;