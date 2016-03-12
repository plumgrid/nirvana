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
