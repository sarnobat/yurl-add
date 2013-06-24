console.log('test');
var neo4j = require('neo4j')
var db = new neo4j.GraphDatabase('http://netgear.rohidekar.com:7474');
var express = require('express');
var app = express();

app.get('/yurl/stash', function(req, res) {
        results = db.query ('start n=node(*) return n.name?,n.title?,n.created?', function(err, result) {
                            if(err) throw err;
                            console.log(result); // delivers an array of query results
                            res.send(result);
                            });
        
        
        });
app.get('/yurl/stash/:id', function(req, res) {
        var milliseconds = (new Date).getTime();
        results = db.query ('CREATE (n { title : "foobar" , url : "http://www.foobar.com", created: '+milliseconds+', ordinal: '+milliseconds+' }) RETURN id(n)', function(err, result) {
                            if(err) {
                            console.log('test');
                            throw err;
                            }
                            console.log(result); // delivers an array of query results
                            res.send(result);
                            });
        
        });

var port = 4450;
app.listen(port);
console.log('Listening on port '+port+'...');