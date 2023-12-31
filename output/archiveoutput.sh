#!/bin/bash

# Input is the test serial (i.e. 111, 112.. 131)

VERSION=0.1.0
USAGE="Usage: command <dbname> -a -m"

while getopts ":i:vh" optname
  do
    case "$optname" in
      "v")
        echo "Version $VERSION"
        exit 0;
        ;;
      "h")
        echo $USAGE
        exit 0;
        ;;
    esac
  done

function continue {
	cp filterState.kml archivedOutput/$1_filterState.kml
	cp geoOutput.kml archivedOutput/$1_geoOutput.kml
	cp $1.jpg archivedOutput/$1_geoOutput.jpg
	cp $1_z.jpg archivedOutput/$1_z_geoOutput.jpg
	cp /var/log/cagelib/cagelib_testoutput.log archivedOutput/$1_testOutput.log
}

if [ -n "$1" ]; then
	continue $1
else
	echo "     no test serial specified. usage <script> <testserial [111, 151, etc..]>"
fi
