#!/usr/bin/env bash

project_root=$(dirname "$(dirname "$(realpath "$0")")")

jar="test-dependency-minimization-all.jar"

if [[ $(cat /etc/hostname) == "david-workstation" ]]; then
  rsync --progress "$project_root"/entrypoint/build/libs/"$jar" /ssddata/chmakac

  exit
fi

ssh -o ConnectTimeout=5 -o ConnectionAttempts=1 workstation 'exit 0' >/dev/null 2>&1
workstation_ssh_result="$?"
if [ $workstation_ssh_result -eq 0 ]; then
    echo "Deploying to workstation..."
    rsync -c --timeout 30 --progress "$project_root"/entrypoint/build/libs/"$jar" workstation:/ssddata/chmakac
else
    echo "Workstation appears to be offline - Skipping..."
fi
