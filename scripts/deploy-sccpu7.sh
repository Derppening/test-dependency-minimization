#!/usr/bin/env bash

project_root=$(dirname "$(dirname "$(realpath "$0")")")

jar="ResearchProjectToolkit-all.jar"
target=sccpu7
bridge=sccpu3.cse.ust.hk

while [ $# -gt 0 ]; do
  case "$1" in
    --bridge)
      shift
      bridge="$1"
      ;;
    *)
      ;;
  esac
  shift
done

ssh -o ConnectTimeout=5 -o ConnectionAttempts=1 chmakac@"$target" 'exit 0' >/dev/null 2>&1
target_ssh_result="$?"
if [ $target_ssh_result -eq 0 ]; then
    echo "Deploying to $target via direct connection..."
    rsync -ac --timeout 30 --progress "$project_root"/../../scripts/ chmakac@"$target":/ssddata/chmakac/scripts.unmod
    rsync -c --timeout 30 --progress "$project_root"/entrypoint/build/libs/"$jar" chmakac@"$target":/ssddata/chmakac
else
    # Stage the JAR on $bridge, then copy over to $target
    echo "Cannot establish direct connection to $target - Deploying JAR via $bridge instead..."
    rsync -ac --progress --timeout 30 -e 'ssh -J chmakac@'"$bridge" "$project_root"/../../scripts/ chmakac@"$target":/ssddata/chmakac/scripts.unmod
    rsync -c --progress --timeout 30 -e 'ssh -J chmakac@'"$bridge" "$project_root"/entrypoint/build/libs/"$jar" chmakac@"$target":/ssddata/chmakac
fi
