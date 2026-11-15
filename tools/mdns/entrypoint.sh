#!/bin/sh
set -e

# Avahi necesita un bus de sistema. Se levanta dentro del contenedor porque en
# `network_mode: host` no se debe compartir el bus de la PC.
mkdir -p /run/dbus
dbus-daemon --system --fork

exec avahi-daemon --no-chroot --debug
