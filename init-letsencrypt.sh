#!/usr/bin/env bash
#
# Let's Encrypt 인증서 최초 발급. EC2에서 한 번만 실행한다.
#
# 순환 문제를 푼다: nginx는 443 블록의 인증서 파일이 없으면 기동을 거부하는데,
# 인증서를 받으려면 nginx가 80에서 ACME challenge를 서빙하고 있어야 한다.
# 그래서 자체 서명 더미를 먼저 깔아 nginx를 띄우고, 진짜 인증서로 덮어쓴다.
#
# 사용법:
#   CERTBOT_EMAIL=you@example.com ./init-letsencrypt.sh
#   CERTBOT_EMAIL=you@example.com STAGING=1 ./init-letsencrypt.sh   # 리허설
set -euo pipefail

DOMAINS=(eeum.life www.eeum.life)
PRIMARY="${DOMAINS[0]}"
if [ -z "${CERTBOT_EMAIL:-}" ]; then
  echo "CERTBOT_EMAIL 환경변수가 필요하다 — 인증서 만료 알림 수신 주소로 쓰인다" >&2
  exit 1
fi
EMAIL="$CERTBOT_EMAIL"
STAGING="${STAGING:-0}"
COMPOSE=(docker compose -f docker-compose.prod.yml)

LIVE_DIR="/etc/letsencrypt/live/$PRIMARY"

# 실패한 발급을 반복하면 Let's Encrypt가 도메인당 주 5회로 막는다.
# 처음이라면 STAGING=1로 한 번 돌려 경로를 검증하는 것을 권한다.
if [ "$STAGING" != "0" ]; then
  echo "[staging] 리허설 모드 — 브라우저가 신뢰하지 않는 인증서가 발급된다"
  STAGING_ARG="--staging"
else
  STAGING_ARG=""
fi

echo "==> 1/5 더미 인증서 생성 (nginx 기동용)"
"${COMPOSE[@]}" run --rm --entrypoint sh certbot -c "
  mkdir -p '$LIVE_DIR' &&
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '$LIVE_DIR/privkey.pem' \
    -out '$LIVE_DIR/fullchain.pem' \
    -subj '/CN=localhost'
"

echo "==> 2/5 nginx 기동 (80에서 ACME challenge 서빙)"
"${COMPOSE[@]}" up -d frontend
sleep 5

echo "==> 3/5 더미 인증서 제거"
"${COMPOSE[@]}" run --rm --entrypoint sh certbot -c "
  rm -rf '/etc/letsencrypt/live/$PRIMARY' &&
  rm -rf '/etc/letsencrypt/archive/$PRIMARY' &&
  rm -f  '/etc/letsencrypt/renewal/$PRIMARY.conf'
"

echo "==> 4/5 실제 인증서 발급"
domain_args=()
for d in "${DOMAINS[@]}"; do domain_args+=(-d "$d"); done

"${COMPOSE[@]}" run --rm --entrypoint certbot certbot \
  certonly --webroot -w /var/www/certbot \
  $STAGING_ARG \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  --force-renewal \
  "${domain_args[@]}"

echo "==> 5/5 nginx reload"
"${COMPOSE[@]}" exec frontend nginx -s reload

echo
echo "완료. 확인:"
echo "  curl -I https://$PRIMARY"
echo "  docker compose -f docker-compose.prod.yml logs -f certbot"
