var PORT = 6000;
var HOST = '0.0.0.0';
var dgram = require('dgram');
var server = dgram.createSocket('udp4');

server.on('listening', function () {
    var address = server.address();
    console.log('UDP Server listening on ' + address.address + ":" + address.port);
});

 var client = require('riemann').createClient({
    host: 'riemann_server',
    port: 15555
  });

client.on('connect', function() {
  console.log('connected!');
  });

client.on('disconnect', function(){
     console.log('disconnected!');
   });

//10.10.0.16:48279 - <13>Nov  5 05:05:13 tahir-dht-b-3-run-stable-4-1-faldirector-p-x-9c50b plumgrid: dht_node_8fc13020 [6e736403:174:6]<302:02:40:30.742888>[3216]: 8 clients owned; HA node-node queue occ 0; OpMgr ack queue occ 0
function sendRiemann(cli, message, remote) {
cli.send(cli.Event({
      service:'plumgrid',
      remote:remote,
      tags:['NRV'],
      metric: 1.0,
      description:message
  }), cli.tcp);
}

server.on('message', function (message, remote) {
  var new_message = message.toString();
  sendRiemann(client, new_message, remote);
  console.log(remote.address + ':' + remote.port +' - ' + new_message);
});

server.bind(PORT, HOST);

