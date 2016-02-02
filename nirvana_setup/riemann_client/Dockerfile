FROM ubuntu:14.04.3
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
ENV NODE_VERSION 0.12.9
RUN apt-get update
RUN apt-get install -y curl ssh build-essential libprotobuf-dev pkg-config
RUN mkdir /home/work
WORKDIR /home/work

RUN gpg --keyserver ha.pool.sks-keyservers.net --recv-keys DD8F2338BAE7501E3DD5AC78C273792F7D83545D
RUN curl -SLO "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-x64.tar.gz" \
  && curl -SLO "https://nodejs.org/dist/v$NODE_VERSION/SHASUMS256.txt.asc" \
  && gpg --verify SHASUMS256.txt.asc \
  && grep " node-v$NODE_VERSION-linux-x64.tar.gz\$" SHASUMS256.txt.asc | sha256sum -c - \
  && tar -xzf "node-v$NODE_VERSION-linux-x64.tar.gz" -C /usr/local --strip-components=1 \
  && rm "node-v$NODE_VERSION-linux-x64.tar.gz" SHASUMS256.txt.asc

ADD riemann_node_client.js /home/work
ADD run_riemann_client.sh /home/work
ADD package.json /home/work
RUN npm install
ENTRYPOINT [ "/bin/bash", "run_riemann_client.sh" ]
