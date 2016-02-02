FROM ubuntu:14.04.3
MAINTAINER Tahir Rauf tahir@plumgrid.com
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get update
RUN apt-get install -y ssh git wget default-jre vim
RUN mkdir -p /opt/pg
RUN mkdir -p /opt/pg/log/nirvana
RUN wget https://aphyr.com/riemann/riemann-0.2.10.tar.bz2 -P /opt/pg/
RUN tar xvfj /opt/pg/riemann-0.2.10.tar.bz2 -C /opt/pg
RUN echo "export PATH=/opt/pg/riemann-0.2.10/bin:$PATH" >> ~/.bashrc
RUN source ~/.bashrc
RUN git clone https://github.com/plumgrid/nirvana.git /opt/pg/nirvana
ADD run_riemann_server.sh /opt/pg/nirvana
WORKDIR /opt/pg/nirvana
ENTRYPOINT [ "/bin/bash", "run_riemann_server.sh" ]
