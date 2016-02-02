#!/bin/bash

pushd /opt/pg/nirvana
/opt/pg/riemann-0.2.10/bin/riemann riemann.config
popd
