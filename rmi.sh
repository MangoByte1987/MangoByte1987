#!/bin/bash
export ROOT="." #Expected to be run at the MatchinEngine directory's  location,otherwise adjust.
export CLASSPATH="$ROOT/orderbook-server/target/classes:$ROOT/orderbook-utility/target/classes"
echo "Running rmiregistry"
rmiregistry
