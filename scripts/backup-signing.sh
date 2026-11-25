#!/usr/bin/env bash
set -euo pipefail
mkdir -p .signing-backup
openssl rand 48 > .signing/session.bin
key=$(head -c 32 .signing/session.bin | xxd -p -c 64)
iv=$(tail -c 16 .signing/session.bin | xxd -p -c 64)
openssl enc -aes-256-cbc -K "$key" -iv "$iv" -in .signing/development.keystore -out .signing-backup/keystore.enc
openssl pkeyutl -encrypt -pubin -inkey scripts/signing-backup-public.pem -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -in .signing/session.bin -out .signing-backup/session.enc
rm .signing/session.bin
