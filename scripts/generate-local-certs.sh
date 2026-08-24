#!/usr/bin/env sh
# Local mTLS material for Payment. Do not use in production.
set -eu

out=${1:-"$(cd "$(dirname "$0")/.." && pwd)/certs/local"}
days=${CERT_DAYS:-365}

rm -rf "$out"
mkdir -p "$out"
trap 'rm -f "$out"/*.csr "$out"/*.ext "$out"/*.srl' EXIT

openssl req -x509 -newkey rsa:2048 -nodes -days "$days" \
  -keyout "$out/ca.key" -out "$out/ca.crt" \
  -subj '/CN=gym-payment-local-ca' >/dev/null 2>&1

issue() {
  name=$1 san=$2 usage=$3
  openssl req -newkey rsa:2048 -nodes -keyout "$out/$name.key" -out "$out/$name.csr" \
    -subj "/CN=$name" >/dev/null 2>&1
  printf 'subjectAltName=%s\nextendedKeyUsage=%s\nbasicConstraints=CA:FALSE\n' "$san" "$usage" >"$out/$name.ext"
  openssl x509 -req -days "$days" -in "$out/$name.csr" -CA "$out/ca.crt" -CAkey "$out/ca.key" \
    -CAcreateserial -out "$out/$name.crt" -extfile "$out/$name.ext" >/dev/null 2>&1
}

issue server 'DNS:localhost,DNS:ms-gym-payment,IP:127.0.0.1' serverAuth
issue client-member 'DNS:ms-gym-member,URI:spiffe://gym.cluster.local/ns/gym-system/sa/ms-gym-member' clientAuth
issue client-other 'DNS:not-member,URI:spiffe://gym.cluster.local/ns/gym-system/sa/not-member' clientAuth
chmod 600 "$out"/*.key
printf '%s\n' "Wrote Payment local mTLS certificates under $out"
