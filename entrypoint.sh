#!/usr/bin/env bash
set -euo pipefail

HTTP_PORT="${HTTP_PORT:-8080}"
DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-labdb}"
DB_USER="${DB_USER:-labuser}"
DB_PASS="${DB_PASS:-labpass}"

echo "[1/6] Build server WAR..."
mvn -q -pl server -am clean package

WAR="/work/server/target/lab-demo.war"
if [ ! -f "$WAR" ]; then
  echo "WAR not found: $WAR"
  exit 1
fi

echo "[2/6] Start GlassFish domain..."
asadmin start-domain domain1

echo "[3/6] Set HTTP port (idempotent)..."
asadmin set "configs.config.server-config.network-config.network-listeners.network-listener.http-listener-1.port=${HTTP_PORT}" || true

echo "[4/6] Configure JDBC pool/resource (idempotent)..."
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
  asadmin create-jdbc-resource --connectionpoolid "${POOL}" "${JNDI}"
else
  echo "Resource ${JNDI} already exists"
fi

echo "[5/6] Deploy WAR..."
asadmin deploy --force=true --name lab-demo "$WAR"

echo "[6/6] Tail server log (container foreground)..."
LOG="${GLASSFISH_HOME}/glassfish/domains/domain1/logs/server.log"
touch "$LOG"
tail -F "$LOG"