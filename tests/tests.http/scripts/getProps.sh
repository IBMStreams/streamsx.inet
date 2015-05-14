#!/bin/bash

#
# *******************************************************************************
# * Copyright (C)2014, International Business Machines Corporation and *
# * others. All Rights Reserved. *
# *******************************************************************************
#

props=$1
ns=$2

if [ -z "$ns" ]; then
    echo "$0 properties-file main-composite"
    exit 1
fi

finProps=""
while read line 
do
    finProps="$finProps $ns.$line"
done < $props

echo $finProps