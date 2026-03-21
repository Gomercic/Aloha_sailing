#!/bin/sh
# Pokreni iz istog foldera gdje su docker-compose.yml i .env
set -e
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Nema datoteke .env"
  echo "  cp env.template .env"
  echo "  nano .env   # postavi POSTGRES_PASSWORD i API_KEY"
  exit 1
fi

docker compose up -d --build
echo ""
echo "Gotovo. Test: curl -s http://127.0.0.1:8080/health"
echo "(vanjski port: uredi u docker-compose.yml kao 8080:8000)"
