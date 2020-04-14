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
	cp filterState.kml archivedOutput/$1_filterState_`date +%F`.kml
	cp geoOutput.kml archivedOutput/$1_geoOutput_`date +%F`.kml
	cp /Users/mobile3/Downloads/$1.jpg archivedOutput/$1_out.jpg
}

if [ -n "$1" ]; then
	continue $1

	#if [[ $1 == *"-"* ]]; then
	#	echo "Test serial specified. usage <script> <testserial [111, 151, etc..]>"
	#else
	#	continue $1
	#fi
else
	echo "     no test serial specified. usage <script> <testserial [111, 151, etc..]>"
fi
