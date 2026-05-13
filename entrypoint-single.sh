#!/usr/bin/env bash
set -euo pipefail

HTTP_PORT="${HTTP_PORT:-8080}"

DB_HOST="127.0.0.1"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-labdb}"
DB_USER="${DB_USER:-labuser}"
DB_PASS="${DB_PASS:-labpass}"

PGDATA="${PGDATA:-/var/lib/postgresql/data}"

pg_bin_dir() {
  ls -d /usr/lib/postgresql/*/bin 2>/dev/null | head -n 1
}

PGBIN="$(pg_bin_dir)"

if [ -z "${PGBIN}" ]; then
  echo "PostgreSQL bin directory not found in /usr/lib/postgresql/*/bin"
  exit 1
fi

echo "[0/9] Init & start PostgreSQL..."

mkdir -p "$PGDATA"
chown -R postgres:postgres "$PGDATA"
chmod 700 "$PGDATA"

if [ ! -s "${PGDATA}/PG_VERSION" ]; then
  mkdir -p "$PGDATA"
  chown -R postgres:postgres "$PGDATA"

  su - postgres -c "${PGBIN}/initdb -D '$PGDATA'"

  echo "listen_addresses = '127.0.0.1'" >> "${PGDATA}/postgresql.conf"
  echo "port = ${DB_PORT}" >> "${PGDATA}/postgresql.conf"

  echo "host all all 127.0.0.1/32 scram-sha-256" >> "${PGDATA}/pg_hba.conf"
  echo "local all all scram-sha-256" >> "${PGDATA}/pg_hba.conf"
fi

su - postgres -c "${PGBIN}/pg_ctl -D '$PGDATA' -w start"

echo "[1/9] Ensure user/db exist..."

su - postgres -c "psql -tAc \"SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'\" | grep -q 1 \
|| psql -c \"CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASS}';\""

su - postgres -c "psql -tAc \"SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'\" | grep -q 1 \
|| psql -c \"CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};\""

echo "[2/9] Start VS Code server..."

mkdir -p /root/.config/code-server

cat > /root/.config/code-server/config.yaml <<EOF
bind-addr: 0.0.0.0:8443
auth: none
cert: false
EOF

code-server /work &

echo "[3/9] Build server WAR..."

cd /work
mvn -q -pl server -am clean package

WAR="/work/server/target/lab-demo.war"

if [ ! -f "$WAR" ]; then
  echo "WAR not found: $WAR"
  exit 1
fi

echo "[4/9] Start GlassFish domain..."

asadmin start-domain domain1

echo "[5/9] Set HTTP port (idempotent)..."

asadmin set \
"configs.config.server-config.network-config.network-listeners.network-listener.http-listener-1.port=${HTTP_PORT}" \
|| true

echo "[6/9] Configure JDBC pool/resource..."

POOL="LabPool"
JNDI="jdbc/LabDS"

if ! asadmin list-jdbc-connection-pools | grep -q "^${POOL}\$"; then
  asadmin create-jdbc-connection-pool \
    --restype javax.sql.DataSource \
    --datasourceclassname org.postgresql.ds.PGSimpleDataSource \
    --property "serverName=${DB_HOST}:portNumber=${DB_PORT}:databaseName=${DB_NAME}:user=${DB_USER}:password=${DB_PASS}" \
    "${POOL}"
else
  echo "Pool ${POOL} already exists"
fi

if ! asadmin list-jdbc-resources | grep -q "^${JNDI}\$"; then
  asadmin create-jdbc-resource \
    --connectionpoolid "${POOL}" \
    "${JNDI}"
else
  echo "Resource ${JNDI} already exists"
fi

echo "[7/9] Deploy WAR..."

asadmin deploy \
  --force=true \
  --name lab-demo \
  "$WAR"

echo "[8/9] Environment is ready"

echo ""
echo "=========================================="
echo " Application:  http://localhost:${HTTP_PORT}/lab-demo"
echo " GlassFish:    http://localhost:4848"
echo " VS Code IDE:  http://localhost:8443"
echo " PostgreSQL:   localhost:${DB_PORT}"
echo "=========================================="
echo ""

echo "[9/9] Tail server log..."

LOG="${GLASSFISH_HOME}/glassfish/domains/domain1/logs/server.log"

touch "$LOG"

tail -F "$LOG"