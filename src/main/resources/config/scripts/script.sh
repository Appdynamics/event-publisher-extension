#!/bin/bash
COUNT=`ps -ef | grep tomcat| grep -v grep | wc -l`
if [ "$COUNT" = 0 ]; then
    exit 0
else
    exit 1
fi