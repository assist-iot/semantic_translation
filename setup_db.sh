#!/usr/bin/bash

export SQLITE_SCRIPT=./src/main/resources/sql/sqlite-schema.sql

# function get_host() {
#     if [ -z "$1" ]; then
#         echo "127.0.0.1"
#     else
#         echo "$1"
#     fi
# }

function setup_sqlite () {
    # DB File in quill-jdbc
    echo "Creating sqlite DB File"
    DB_FILE=./data/ipsm.sqlite
    echo "Removing Previous sqlite DB File (if any)"
    rm -f $DB_FILE
    echo "Creating sqlite DB File"
    echo "(with the $SQLITE_SCRIPT script)"
    sqlite3 $DB_FILE < $SQLITE_SCRIPT
    echo "Setting permissions on sqlite DB File"
    chmod a+rw $DB_FILE
    echo "Sqlite ready!"
}

function send_script() {
  echo "Send Script Args: 1: $1 - 2 $2 - 3: $3"
  docker cp $2 "$(docker-compose ps -q $1)":/$3
}

# export -f setup_sqlite
# export -f send_script

setup_sqlite
