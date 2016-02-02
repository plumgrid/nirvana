#!/bin/bash -e

# *****************************************************************************
# Function: retryexec_no_assert
# Purpose : Try to execute a command. If the command returns success, this
#           function returns 0. Otherwise, the script retries for a while. If
#           it still fails, its then aborted with the status code of the failed
#           command.
# Usage   : retryexec_no_assert <command>
# *****************************************************************************
function retryexec_no_assert() {
  cmd=$1
  retries=10
  try=1
  until [[ $try == ${retries} ]]
  do
    local retval=0;
    eval $cmd || retval=$?
    if [[ $retval == "0" ]]; then
      break
    fi
    let try++
    if [[ $try == ${retries} ]]; then
      echo "Command $cmd failed with status $retval"
      return $retval
    fi
    sleep 3
  done
}

# *****************************************************************************
# Function: check_listening_on_port
# Purpose : Test if process is listening on a port using netstat
# Usage   : check_listening_on_port $IP $port $log_file
# *****************************************************************************
function check_listening_on_port() {
  IP=$1
  PORT=$2
  retryexec_no_assert "/bin/nc -zv $IP $PORT" || return $?
}

#while true; do sleep 2; done
check_listening_on_port "riemann_server" "15555"
node riemann_node_client.js
