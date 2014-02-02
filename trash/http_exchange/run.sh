cd client

open "index.html" || xdg-open "index.html"

cd ..

mkdir -p ~/.groovy/lib 
cp server/.groovy/lib/*jar ~/.groovy/lib/

groovy server/webserver.groovy