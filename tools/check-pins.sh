#!/usr/bin/env bash
# Vérifie que la chaîne TLS réelle de Supabase contient au moins une clé épinglée (R-NET-07, ADR-0014),
# et que l'expiration de l'épinglage est à plus de 30 jours. Usage : tools/check-pins.sh [hôte]
set -euo pipefail
HOST="${1:-lvxdpmjyeelrxfbrtvay.supabase.co}"
NSC="$(dirname "$0")/../android/app/src/main/res/xml/network_security_config.xml"
pins=$(grep -o '<pin digest="SHA-256">[^<]*' "$NSC" | sed 's/.*>//')
expiry=$(grep -o 'expiration="[^"]*"' "$NSC" | cut -d'"' -f2)
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
echo | openssl s_client -connect "$HOST:443" -servername "$HOST" -showcerts 2>/dev/null \
  | awk -v d="$tmp" '/BEGIN CERT/{n++; f=d "/c" n ".pem"} n>0{print > f}'
found=""
for c in "$tmp"/c*.pem; do
  spki=$(openssl x509 -in "$c" -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64)
  subj=$(openssl x509 -in "$c" -noout -subject)
  if grep -qxF "$spki" <<<"$pins"; then found="$subj"; fi
  echo "$spki  $subj"
done
[ -n "$found" ] || { echo "ÉCHEC : aucune clé de la chaîne de $HOST n'est épinglée"; exit 1; }
left=$(( ( $(date -d "$expiry" +%s) - $(date +%s) ) / 86400 ))
echo "OK : épinglé ($found) ; expiration $expiry dans $left jours"
[ "$left" -gt 30 ] || { echo "ÉCHEC : renouveler l'épinglage avant $expiry"; exit 1; }
