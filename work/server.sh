#!/bin/sh
while true
do
{
	echo 'Done' |  nc -l -p 8089   | grep "GET"
} | grep "HTTP" \
| perl -pe 's{.*param1=(.*).title=(.*).HTTP.1.1}{"$2","$1"\n$1\n}g' \
| perl -pe 's/%([0-9a-f]{2})/sprintf("%s", pack("H2",$1))/eig' \
| tee -a ~/work.txt; 
done;