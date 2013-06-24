cd client

open "index.html" || xdg-open "index.html"

cd ..

cd server
cd .groovy/lib
mkdir -p ~/.groovy/lib
mv *jar ~/.groovy/lib/
cd ../..

groovy webserver.groovy