
var neo4j = require('neo4j')
var db = new neo4j.GraphDatabase('http://netgear.rohidekar.com:7474');
var express = require('express');
var app = express();

app.get('/yurl/stash', function(req, res) {
        results = db.query ('start n=node(*) return n.name?,n.title?', function(err, result) {
                if(err) throw err;
                console.log(result); // delivers an array of query results
                res.send(result);
            });


        });
app.get('/wines/:id', function(req, res) {
        res.send({id:req.params.id, name: "The Name", description: "description"});
        });

var port = 4450;
app.listen(port);
console.log('Listening on port '+port+'...');