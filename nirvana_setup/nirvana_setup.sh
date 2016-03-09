#!/bin/bash -e

DOCKER_COMPOSE_VER="1.5.2"
STEP_ST=1
STEP_ED=4

echo "I am here"
TEMP=`getopt -o S:E:h --long step_st:,step_ed:,help -n 'setup.sh' -- "$@"`
echo "I am here 1"
eval set -- "$TEMP"
echo "I am here 2"
while true ; do
  case "$1" in
    -S| --step_st ) STEP_ST="$2"; shift 2 ;;
    -E| --step_ed ) STEP_ED="$2"; shift 2 ;;
    -h| --help ) show_setup_help; exit 0; shift;;
    --) shift ; break ;;
    *) exit 1 ;;
  esac
done

function setup() {
  local step=$1
  case "$step" in

  1)
    echo "Update repositories"
    sudo apt-get update || true
    echo "Install Docker"
    sudo curl -sSL https://get.docker.com/ | sudo sh
    echo "Add current user to docker group"
    sudo usermod -aG docker $USER
    ;;

  2)
    echo "Install docker compose"
    sudo curl -L https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VER}/docker-compose-`uname -s`-`uname -m` -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    ;;

  3)
    echo "Build docker containers"
    sudo docker-compose build
    ;;

  4)
    echo "Bring up the docker containers"
    sudo mkdir -p /opt/pg/log && sudo touch /opt/pg/log/nirvana_dockers.log
    sudo chmod 666 /opt/pg/log/nirvana_dockers.log && sudo chown -R $USER:$USER /opt/pg/log/nirvana_dockers.log
    sudo docker-compose up >& /opt/pg/log/nirvana_dockers.log &
    echo "..." && sleep 5
    echo "============================================================================================================"
    echo " Your docker containers are up. Configure your nodes to send the logs to <docker_containers_host_IP>:6000 "
    echo " Sample configuration inside /etc/rsyslog.d/00-pg.conf may look like below"
    echo ' $template ls_json,"{%timestamp:::date-rfc3339,jsonf:@timestamp%,%source:::jsonf:@source_host%,%msg:::json%}"
           :syslogtag,isequal,"pg:" @127.0.0.1:6000;ls_json'
    echo "============================================================================================================"
    ;;

  *)
    echo "NOP - $step"
  ;;
  esac
}

for STEP in `seq ${STEP_ST} ${STEP_ED}`; do
  printf "\n    === Starting Nirvana STEP $STEP === \n"
  setup $STEP
done
