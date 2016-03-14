# Version:
This is version 0.5.

# Purpose:
Distributed systems are hard to design and even harder to debug. Different components of the system operate in semi-independent fashion and their common interactions are difficult to characterize without knowledge of the inner workings of the system. Consequently, debugging and maintenance of such systems continues to rely on experts with deep knowledge of the underlying system architecture. This knowledge barrier constrains distributed systems’ maintenance to a handful of experts, thereby inadvertently hindering development of a knowledge base that can be understood and extended by non-experts.

In project Nirvana, we attempt to reduce this knowledge gap in the cloud management domain by building a monitoring system that can be built, extended and maintained by non-experts. More specifically, our objective is to design a system that can meet the following two objectives: <br />
Objective 1: A non-expert should be able to extract the distributed system’s components and the interactions between the components. <br />
Objective 2: A non-expert should be able to codify the knowledge of system components into a rule engine.

# Design and Architecture:
Following is how various component of system will interact with each other
  1. Nodes, whose log messages you want to analyze, needs to send their messages to central node. This is accomplished by pushing a simple rsyslog configuration.
  2. The distributed logs are collected in a central location where they are analyzed by a “Stream Processing” engine. 
  3. The stream processing engine is written to work like a state machine where it can track all the transition from start to finish. If the state machine does not completes the transition and gets stuck it will time out and generate an error report with last know state where it got stuck. The current prototypes are built using riemann as stream processor.
  4. The statemachine don’t need to capture all the states that exists in the system as that will make it a very difficult task. The idea is to approximately capture the state transitions. 
Following is the high level diagram <br />
![Alt text](./images/design.png?raw=true "Title")

SETUP:
======
  1. **Install necessary packages and build docker images on central server:** You will need docker and docker-compose to build images. Run setup-nirvana.sh script inside nirvana-setup directory.
     It will install docker, docker-compose.
     It will also build the images for log aggregator (riemann_client) and your stream processor (riemann_server).
     Moreover, it will bring up the docker containers.
     You should see riemann_client and riemann_server containers up and running.
  2. **Forward messages to central server:** Create file named 00-fwd-to-central-server.conf in /etc/rsyslog.d/ on machine whose logs you want to collect. An example configuration to forward all syslog messages over UDP looks like following
     *.* @CENTRAL-SERVER-IP:6000
      FOR TCP, it looks like
     *.* @@CENTRAL-SERVER-IP:6000
     A more complex configuration looks like below
     *$template ls_json,"{%timestamp:::date-rfc3339,jsonf:@timestamp%,%source:::jsonf:@source_host%,%msg:::json%}"
     :syslogtag,isequal,"pg:" @CENTRAL-SERVER-IP:6000;ls_json <br />*
     Please dont forget to replace the IP-OF-DOCKER-CONTAINERS-HOST with actual IP address of machine where docker containers are running.
  3. **Make forwarding configuration effective:** 
     Restart rsyslog service on nodes (on ubuntu 14.04, just say "sudo service rsyslog restart"). 
Your nodes will start sending messages to central server. Riemann client will recieve those messages and will forward the messages to Riemann server in format appropriate to client. 
  4. **smartlogs files**: 
     You can see the smart log messages under /opt/pg/log/plumgrid.info.log.

How to write PLUGIN:
====================
An easy to use infrastructure has been laid down to analyze and summarized logs. One does NOT need to be expert of Clojure or Riemann to write the plugin. 
Following are the list of all steps involved in writing plugin
  1. **Define the state machine of the use-case:**
     An example snippet from a state machine is given below
     *RPCIF_service_directory [10c044ae:1:6]<013:04:57:37.544513>[1]: EVENT 'service_directory boot' txn_id 526cc5e8 pid 8265 {service_directory boot} <br />
     ServiceDirectory [10c044ae:1:8]<018:16:11:45.004840>[1]: init:  my_station_id_ = 6a0f5151 ip 172.16.1.1 {service_directory ip} <br />
     service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:26.955312>[17]: [init]: rest_gateway is active {rest_gateway is active}*  
  2. **Assign a unique numeric id to plugin:**
     Go to plugins --> start_states_consts.clj and mention <plugin-name> <numeric-id>
     *:director-bootup-stage2-state 100*
  3. **Create plugin file:**
     Create a file director-bootup-stage2-state inside plugins directory.
  4. **Define function:**
     Define a function director-bootup-stage2 in plugin file. Function will hold all the logic to define states of state machine given in step 1. It will also hold the regex to extract useful information from logs.
  5. **Define individual states:**
     ![Alt text](./images/plugin_state_definition.png?raw=true "Title")
  6. **Define summarized message:**
     Once last state in your state machine hits, you can extract all the information, saved earlier in DB, and show them in nicely summarized manner. 

Please see nirvana/plugins/director_bootup_stage2.clj file for better understanding.

**NOTE:** We are planning to work on introducing further abstraction in plugin writing. 
