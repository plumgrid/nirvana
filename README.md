# Version:
This is version 0.5.

# Purpose?
Distributed systems are hard to design and even harder to debug. Different components of the system operate in semi-independent fashion and their common interactions are difficult to characterize without knowledge of the inner workings of the system. Consequently, debugging and maintenance of such systems continues to rely on experts with deep knowledge of the underlying system architecture. This knowledge barrier constrains distributed systems’ maintenance to a handful of experts, thereby inadvertently hindering development of a knowledge base that can be understood and extended by non-experts.

In project Nirvana, we attempt to reduce this knowledge gap in the cloud management domain by building a monitoring system that can be built, extended and maintained by non-experts. More specifically, our objective is to design a system that can meet the following two objectives:
Objective 1: A non-expert should be able to extract the distributed system’s components and the interactions between the components.
Objective 2 (Longer term): A non-expert should be able to codify the knowledge of system components into a rule engine.

# Design and Architecture?
The distributed logs are collected in a central location where they are analyzed by a “Stream Processing” engine. The stream processing engine is written to work like a state machine where it can track all the transition from start to finish. If the state machine does not completes the transition and gets stuck it will time out and generate an error report with last know state where it got stuck. 
The current prototypes are built using riemann as stream processor.
The statemachine don’t need to capture all the states that exists in the system as that will make it a very difficult task. The idea is to approximately capture the state transitions. i.e. if a service is requested from SD at some point it will need to reply with a pgname of the service, we don't need to map every single thing that happens underneath this request. (If needed it can be done but there is a cost so it can be decided on need basis).
Following is the high level diagram <br />
![Alt text](./images/design.png?raw=true "Title")



# What is in this phase?
For POC, we have written few plugins which will analyze and correlate the log messages for

1. Director Bootups
2. Edge bootups
3. VM attached to edge
4.  Process crash and relaunched
5.  Director exit
6.  Edge exit
7.  Edge Reconnect

While the regular distributed logs generates thousands of log messages, Nirvana will spit quite a few log messages against these evenets like below (taken from running demo)

Thu Jan 28 09:48:45 PST 2016: Broker and SM on director <muneeb-pc> <10.22.27.51> got initialized successfully (took 1.0 seconds) <br />
Thu Jan 28 09:48:48 PST 2016: VND 'Demo' created successfully (took0.0 secs) via CDB. <br />
Thu Jan 28 09:48:49 PST 2016: Service directory (service-directory-1278ae61) on director <muneeb-pc> <10.22.27.51> got initialized successfully (took 4.0 seconds). All Director services are up and active. <br />
Thu Jan 28 09:48:49 PST 2016: Service directory (service-directory-f4e45251) on director <muneeb-pc> <10.22.27.51> got initialized successfully (took 5.0 seconds). All Director services are up and active. <br />
Thu Jan 28 09:48:50 PST 2016: Compute node on <muneeb-pc> <10.22.27.51> got initialized successfully (took 1.0 seconds). <br />
Thu Jan 28 09:50:46 PST 2016: Interface (name:tap1, type:access-vm, mac:00:00:00:00:00:01, uuid:1) successfully attached to edge PE-a7ac0691 <muneeb-pc> <10.22.27.51> (took 0.0 seconds). <br />
Thu Jan 28 09:51:40 PST 2016: Process 'bridge' (pgname 'bridge-57b5ae71', ip-pid '10.22.27.51:22338' domain 'Demo' vnf-name '/0/connectivity/domain/Demo/ne/Bridge') exited with reason WIFEXITED and got relaunched successfully with ip-pid '10.22.27.51:23347'. The recovery took 0.0 seconds. <br />

You can see that Nirvana is nicely summarizing and analyzing the regular log messages.

SETUP:
======
  1. Run setup-nirvana.sh script inside nirvana-setup directory.
  2. Create file named 00-pg.conf in /etc/rsyslog.d/ on machine whose logs you want to collect. Add following to the file
     *$template ls_json,"{%timestamp:::date-rfc3339,jsonf:@timestamp%,%source:::jsonf:@source_host%,%msg:::json%}"
     :syslogtag,isequal,"pg:" @IP-OF-DOCKER-CONTAINERS-HOST:6000;ls_json <br />*
     Please dont forget to replace the IP-OF-DOCKER-CONTAINERS-HOST with actual IP address of machine where docker containers are running.
  3. Restart rsyslog on nodes.
Riemann will start collecting and Analyzing the logs. You can see the nirvana logs in /opt/pg/log/plumgrid.info.log.
