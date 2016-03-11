# Version:
This is version 0.5.

# Purpose:
Distributed systems are hard to design and even harder to debug. Different components of the system operate in semi-independent fashion and their common interactions are difficult to characterize without knowledge of the inner workings of the system. Consequently, debugging and maintenance of such systems continues to rely on experts with deep knowledge of the underlying system architecture. This knowledge barrier constrains distributed systems’ maintenance to a handful of experts, thereby inadvertently hindering development of a knowledge base that can be understood and extended by non-experts.

In project Nirvana, we attempt to reduce this knowledge gap in the cloud management domain by building a monitoring system that can be built, extended and maintained by non-experts. More specifically, our objective is to design a system that can meet the following two objectives:
Objective 1: A non-expert should be able to extract the distributed system’s components and the interactions between the components.
Objective 2 (Longer term): A non-expert should be able to codify the knowledge of system components into a rule engine.

# Design and Architecture:
Following is how various component of system will interact with each other
  1. Nodes, whose log messages you want to analyze, needs to send their messages to central node. This is accomplished by pushing a simple rsyslog configuration.
  2. The distributed logs are collected in a central location where they are analyzed by a “Stream Processing” engine. 
  3. The stream processing engine is written to work like a state machine where it can track all the transition from start to finish. If the state machine does not completes the transition and gets stuck it will time out and generate an error report with last know state where it got stuck. The current prototypes are built using riemann as stream processor.
  5. The statemachine don’t need to capture all the states that exists in the system as that will make it a very difficult task. The idea is to approximately capture the state transitions. 
Following is the high level diagram <br />
![Alt text](./images/design.png?raw=true "Title")

SETUP:
======
  1. ** Forward messages to central server: ** Create file named 00-fwd-to-central-server.conf in /etc/rsyslog.d/ on machine whose logs you want to collect. An example configuration looks like following
     *$template ls_json,"{%timestamp:::date-rfc3339,jsonf:@timestamp%,%source:::jsonf:@source_host%,%msg:::json%}"
     :syslogtag,isequal,"pg:" @IP-OF-DOCKER-CONTAINERS-HOST:6000;ls_json <br />*
     Please dont forget to replace the IP-OF-DOCKER-CONTAINERS-HOST with actual IP address of machine where docker containers are running.
  2. Run setup-nirvana.sh script inside nirvana-setup directory.
     It will install docker, docker-compose.
     It will also build the images for log aggregator (riemann_client) and your stream processor (riemann_server).
  3. Restart rsyslog on nodes.
Riemann will start collecting and Analyzing the logs. You can see the nirvana logs in /opt/pg/log/plumgrid.info.log.
