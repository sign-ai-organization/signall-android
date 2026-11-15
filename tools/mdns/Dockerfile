FROM alpine:3.20

# Contenedor mínimo que solo anuncia por mDNS. No habla con el backend: Avahi
# publica el puerto que la app debe usar, y el tráfico real va directo a Docker
# o a Caddy en la PC.
#
# Alpine publica SSH/SFTP por defecto; este contenedor no los tiene y no debe
# anunciarlos.
RUN apk add --no-cache avahi avahi-tools dbus \
    && rm -f /etc/avahi/services/ssh.service /etc/avahi/services/sftp-ssh.service

COPY signall.service /etc/avahi/services/signall.service
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
