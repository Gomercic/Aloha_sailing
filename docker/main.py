"""
Minimal REST API za upis/čitanje telemetrije (npr. anchoring) u PostgreSQL.
Zaštita: header X-API-Key mora odgovarati env API_KEY.
"""
import json
import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Any, Optional
from urllib.parse import quote_plus

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker


def build_database_url() -> str:
    """Postgres URL s ispravnim enkodiranjem lozinke (Compose ${VAR} u jednom stringu lomi se kod @ : # …)."""
    user = os.environ["POSTGRES_USER"]
    password = os.environ["POSTGRES_PASSWORD"]
    host = os.environ.get("POSTGRES_HOST", "db")
    db = os.environ["POSTGRES_DB"]
    u = quote_plus(user, safe="")
    p = quote_plus(password, safe="")
    d = quote_plus(db, safe="")
    return f"postgresql+psycopg://{u}:{p}@{host}:5432/{d}"


DATABASE_URL = build_database_url()
API_KEY = os.environ.get("API_KEY", "")

engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def init_db() -> None:
    with engine.begin() as conn:
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS ships (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    code VARCHAR(64) NOT NULL UNIQUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS telemetry_latest (
                    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
                    payload JSONB NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (ship_id)
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE INDEX IF NOT EXISTS idx_telemetry_latest_updated
                ON telemetry_latest (updated_at DESC);
                """
            )
        )


logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(title="StartLine NAS API", version="0.1.0")


@app.on_event("startup")
def startup() -> None:
    try:
        logger.info("Connecting to DB and running init_db()…")
        init_db()
        logger.info("init_db() OK")
    except Exception:
        logger.exception("init_db failed — see traceback below")
        raise


def require_api_key(x_api_key: Optional[str] = Header(default=None, alias="X-API-Key")) -> None:
    if not API_KEY:
        raise HTTPException(500, "Server misconfigured: API_KEY not set")
    if not x_api_key or x_api_key != API_KEY:
        raise HTTPException(401, "Invalid or missing X-API-Key")


class TelemetryUpsert(BaseModel):
    """Proizvoljan JSON za boat/anchor/status — proširuješ kasnije."""
    data: dict[str, Any] = Field(default_factory=dict)


def get_or_create_ship_id(session, code: str) -> uuid.UUID:
    row = session.execute(
        text("SELECT id FROM ships WHERE lower(code) = lower(:code)"),
        {"code": code.strip()},
    ).fetchone()
    if row:
        return row[0]
    sid = uuid.uuid4()
    session.execute(
        text("INSERT INTO ships (id, code) VALUES (:id, :code)"),
        {"id": sid, "code": code.strip()},
    )
    session.flush()
    return sid


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.put("/v1/ships/{ship_code}/telemetry/latest", dependencies=[Depends(require_api_key)])
def put_telemetry_latest(ship_code: str, body: TelemetryUpsert) -> dict[str, Any]:
    if not ship_code.strip():
        raise HTTPException(400, "ship_code required")
    with SessionLocal() as session:
        ship_id = get_or_create_ship_id(session, ship_code)
        now = datetime.now(timezone.utc)
        session.execute(
            text(
                """
                INSERT INTO telemetry_latest (ship_id, payload, updated_at)
                VALUES (:ship_id, CAST(:payload AS jsonb), :updated_at)
                ON CONFLICT (ship_id) DO UPDATE SET
                    payload = EXCLUDED.payload,
                    updated_at = EXCLUDED.updated_at
                """
            ),
            {
                "ship_id": ship_id,
                "payload": json.dumps(body.data),
                "updated_at": now,
            },
        )
        session.commit()
    return {"ok": True, "ship_code": ship_code.strip(), "updated_at": now.isoformat()}


@app.get("/v1/ships/{ship_code}/telemetry/latest", dependencies=[Depends(require_api_key)])
def get_telemetry_latest(ship_code: str) -> dict[str, Any]:
    with SessionLocal() as session:
        row = session.execute(
            text(
                """
                SELECT t.payload, t.updated_at
                FROM telemetry_latest t
                JOIN ships s ON s.id = t.ship_id
                WHERE lower(s.code) = lower(:code)
                """
            ),
            {"code": ship_code.strip()},
        ).fetchone()
    if not row:
        raise HTTPException(404, "No telemetry for this ship")
    payload, updated_at = row[0], row[1]
    return {"ship_code": ship_code.strip(), "payload": payload, "updated_at": updated_at.isoformat()}
