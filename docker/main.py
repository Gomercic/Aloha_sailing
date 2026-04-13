"""
Minimal REST API za upis/čitanje telemetrije (npr. anchoring) u PostgreSQL.
Zaštita: header X-API-Key mora odgovarati env API_KEY.
"""
import json
import logging
import os
import secrets
import uuid
import hashlib
from datetime import datetime, timezone
from typing import Any, Optional
from urllib.parse import quote_plus
import math

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
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_events (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    name VARCHAR(200) NOT NULL,
                    join_code VARCHAR(64) NOT NULL UNIQUE,
                    organizer_device_id VARCHAR(128) NOT NULL DEFAULT '',
                    organizer_code_hash VARCHAR(128) NULL,
                    organizer_name VARCHAR(200) NOT NULL DEFAULT '',
                    status VARCHAR(32) NOT NULL DEFAULT 'draft',
                    active_race_id UUID NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS organizer_code_hash VARCHAR(128) NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ALTER COLUMN organizer_device_id SET DEFAULT '';
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT FALSE;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS max_boats INTEGER NOT NULL DEFAULT 50;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS start_date DATE NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS end_date DATE NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS race_end_time VARCHAR(5) NOT NULL DEFAULT '18:00';
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS regatta_length_nm DOUBLE PRECISION NOT NULL DEFAULT 0;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS notice_board TEXT NOT NULL DEFAULT '';
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_events
                ADD COLUMN IF NOT EXISTS notice_board_updated_at TIMESTAMPTZ NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_notice_posts (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    event_id UUID NOT NULL REFERENCES regatta_events(id) ON DELETE CASCADE,
                    notice_text TEXT NOT NULL,
                    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE INDEX IF NOT EXISTS idx_regatta_notice_posts_event_published
                ON regatta_notice_posts (event_id, published_at DESC);
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_races (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    event_id UUID NOT NULL REFERENCES regatta_events(id) ON DELETE CASCADE,
                    name VARCHAR(200) NOT NULL,
                    day_number INTEGER NOT NULL DEFAULT 1,
                    sequence_number INTEGER NOT NULL DEFAULT 1,
                    state VARCHAR(32) NOT NULL DEFAULT 'draft',
                    countdown_target_epoch_ms BIGINT NULL,
                    scoring_target_gate_id UUID NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_races
                ADD COLUMN IF NOT EXISTS scoring_target_gate_id UUID NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_races
                ADD COLUMN IF NOT EXISTS race_date DATE NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_races
                ADD COLUMN IF NOT EXISTS start_time VARCHAR(5) NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_races
                ADD COLUMN IF NOT EXISTS end_time VARCHAR(5) NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_races
                ADD COLUMN IF NOT EXISTS race_length_nm DOUBLE PRECISION NOT NULL DEFAULT 0;
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_organizer_sessions (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    event_id UUID NOT NULL REFERENCES regatta_events(id) ON DELETE CASCADE,
                    token_hash VARCHAR(128) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    revoked_at TIMESTAMPTZ NULL
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_regatta_organizer_sessions_event_token
                ON regatta_organizer_sessions (event_id, token_hash);
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_boat_entries (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    event_id UUID NOT NULL REFERENCES regatta_events(id) ON DELETE CASCADE,
                    device_id VARCHAR(128) NULL,
                    boat_name VARCHAR(200) NOT NULL,
                    skipper_name VARCHAR(200) NOT NULL DEFAULT '',
                    club_name VARCHAR(200) NOT NULL DEFAULT '',
                    length_value DOUBLE PRECISION NULL,
                    length_unit VARCHAR(16) NULL,
                    group_code VARCHAR(64) NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_race_participations (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    race_id UUID NOT NULL REFERENCES regatta_races(id) ON DELETE CASCADE,
                    boat_id UUID NOT NULL REFERENCES regatta_boat_entries(id) ON DELETE CASCADE,
                    status VARCHAR(32) NOT NULL DEFAULT 'joined',
                    next_gate_order INTEGER NOT NULL DEFAULT 0,
                    start_snapshot_lat DOUBLE PRECISION NULL,
                    start_snapshot_lon DOUBLE PRECISION NULL,
                    start_snapshot_epoch_ms BIGINT NULL,
                    finished_at_epoch_ms BIGINT NULL,
                    UNIQUE (race_id, boat_id)
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_course_gates (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    race_id UUID NOT NULL REFERENCES regatta_races(id) ON DELETE CASCADE,
                    gate_order INTEGER NOT NULL,
                    gate_type VARCHAR(32) NOT NULL,
                    name VARCHAR(200) NOT NULL,
                    point_a_lat DOUBLE PRECISION NOT NULL,
                    point_a_lon DOUBLE PRECISION NOT NULL,
                    point_b_lat DOUBLE PRECISION NOT NULL,
                    point_b_lon DOUBLE PRECISION NOT NULL
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_signal_points (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    race_id UUID NOT NULL REFERENCES regatta_races(id) ON DELETE CASCADE,
                    boat_id UUID NOT NULL REFERENCES regatta_boat_entries(id) ON DELETE CASCADE,
                    latitude DOUBLE PRECISION NOT NULL,
                    longitude DOUBLE PRECISION NOT NULL,
                    epoch_ms BIGINT NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE INDEX IF NOT EXISTS idx_regatta_signal_race_boat_epoch
                ON regatta_signal_points (race_id, boat_id, epoch_ms DESC);
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_track_points (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    race_id UUID NOT NULL REFERENCES regatta_races(id) ON DELETE CASCADE,
                    boat_id UUID NOT NULL REFERENCES regatta_boat_entries(id) ON DELETE CASCADE,
                    latitude DOUBLE PRECISION NOT NULL,
                    longitude DOUBLE PRECISION NOT NULL,
                    epoch_ms BIGINT NOT NULL,
                    speed_knots DOUBLE PRECISION NULL,
                    heading_deg DOUBLE PRECISION NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_track_points
                ADD COLUMN IF NOT EXISTS speed_knots DOUBLE PRECISION NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                ALTER TABLE regatta_track_points
                ADD COLUMN IF NOT EXISTS heading_deg DOUBLE PRECISION NULL;
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_crossing_events (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    race_id UUID NOT NULL REFERENCES regatta_races(id) ON DELETE CASCADE,
                    boat_id UUID NOT NULL REFERENCES regatta_boat_entries(id) ON DELETE CASCADE,
                    gate_id UUID NOT NULL REFERENCES regatta_course_gates(id) ON DELETE CASCADE,
                    crossing_epoch_ms BIGINT NOT NULL,
                    source VARCHAR(32) NOT NULL DEFAULT 'auto',
                    status VARCHAR(32) NOT NULL DEFAULT 'recorded',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_regatta_crossing_unique_gate
                ON regatta_crossing_events (race_id, boat_id, gate_id);
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS regatta_penalties (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    race_id UUID NOT NULL REFERENCES regatta_races(id) ON DELETE CASCADE,
                    boat_id UUID NOT NULL REFERENCES regatta_boat_entries(id) ON DELETE CASCADE,
                    type VARCHAR(64) NOT NULL,
                    value DOUBLE PRECISION NULL,
                    reason TEXT NOT NULL DEFAULT '',
                    created_by VARCHAR(128) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS superusers (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    username VARCHAR(128) NOT NULL UNIQUE,
                    password_hash VARCHAR(128) NOT NULL,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS superuser_sessions (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    superuser_id UUID NOT NULL REFERENCES superusers(id) ON DELETE CASCADE,
                    token_hash VARCHAR(128) NOT NULL UNIQUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    revoked_at TIMESTAMPTZ NULL
                );
                """
            )
        )
        conn.execute(
            text(
                """
                INSERT INTO superusers (id, username, password_hash, is_active, created_at, updated_at)
                VALUES (
                    gen_random_uuid(),
                    'tomislav',
                    :password_hash,
                    TRUE,
                    NOW(),
                    NOW()
                )
                ON CONFLICT (username) DO NOTHING;
                """
            ),
            {"password_hash": hash_secret("dolphin_12")},
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


class RegattaCreateRequest(BaseModel):
    name: str
    join_code: str = ""
    organizer_code: str = ""
    organizer_name: str = ""
    start_date: str
    end_date: str
    race_end_time: str
    regatta_length_nm: float
    is_public: bool = False
    max_boats: int = 50


class SuperuserLoginRequest(BaseModel):
    username: str
    password: str


class AdminEventUpdateRequest(BaseModel):
    superuser_token: str
    name: str | None = None
    join_code: str | None = None
    organizer_name: str | None = None
    status: str | None = None
    is_public: bool | None = None
    start_date: str | None = None
    end_date: str | None = None
    race_end_time: str | None = None
    regatta_length_nm: float | None = None


class RegattaOrganizerAuthRequest(BaseModel):
    join_code: str
    organizer_code: str


class RegattaJoinRequest(BaseModel):
    join_code: str
    device_id: str
    boat_name: str
    skipper_name: str = ""
    club_name: str = ""
    length_value: float | None = None
    length_unit: str | None = None
    group_code: str | None = None


class RegattaEventUpdateRequest(BaseModel):
    organizer_token: str
    name: str
    join_code: str
    organizer_name: str
    organizer_code: str
    start_date: str
    end_date: str
    race_end_time: str
    regatta_length_nm: float
    max_boats: int
    is_public: bool


class RegattaPointBody(BaseModel):
    latitude: float
    longitude: float


class RegattaGateBody(BaseModel):
    order: int
    type: str
    name: str
    point_a: RegattaPointBody
    point_b: RegattaPointBody


class RegattaCourseUpdateRequest(BaseModel):
    organizer_token: str
    gates: list[RegattaGateBody] = Field(default_factory=list)


class RegattaRaceCreateRequest(BaseModel):
    organizer_token: str
    name: str = ""
    day_number: int = 1


class RegattaCountdownRequest(BaseModel):
    organizer_token: str
    countdown_target_epoch_ms: int


class RegattaRaceDetailsUpdateRequest(BaseModel):
    organizer_token: str
    race_date: str
    start_time: str
    end_time: str
    race_length_nm: float


class RegattaScoringTargetRequest(BaseModel):
    organizer_token: str
    gate_id: str


class RegattaStateRequest(BaseModel):
    organizer_token: str
    state: str


class RegattaCrossingOverrideRequest(BaseModel):
    organizer_token: str
    status: str


class RegattaGroupUpdateRequest(BaseModel):
    organizer_token: str
    group_code: str = ""


class RegattaNoticeBoardUpdateRequest(BaseModel):
    organizer_token: str
    notice_text: str = ""


class RegattaSignalPointBody(BaseModel):
    latitude: float
    longitude: float
    epoch_millis: int
    speed_knots: float | None = None
    heading_deg: float | None = None


class RegattaSignalBatchRequest(BaseModel):
    points: list[RegattaSignalPointBody] = Field(default_factory=list)


class RegattaTrackBatchRequest(BaseModel):
    points: list[RegattaSignalPointBody] = Field(default_factory=list)


class RegattaClientCrossingRequest(BaseModel):
    gate_id: str
    crossing_epoch_ms: int
    source: str = "client_auto"


class RegattaStartSnapshotRequest(BaseModel):
    latitude: float
    longitude: float
    epoch_millis: int


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


def normalize_join_code(raw: str) -> str:
    cleaned = raw.strip().upper()
    return cleaned or secrets.token_hex(3).upper()


def normalize_organizer_code(raw: str) -> str:
    cleaned = raw.strip().upper()
    return cleaned or secrets.token_hex(4).upper()


def hash_secret(raw: str) -> str:
    return hashlib.sha256(raw.strip().encode("utf-8")).hexdigest()


def parse_iso_date(value: str, field_name: str) -> str:
    raw = value.strip()
    if not raw:
        raise HTTPException(400, f"{field_name} required")
    try:
        parsed = datetime.strptime(raw, "%Y-%m-%d").date()
    except ValueError as exc:
        raise HTTPException(400, f"{field_name} must be yyyy-mm-dd") from exc
    return parsed.isoformat()


def parse_hh_mm(value: str, field_name: str) -> str:
    raw = value.strip()
    if not raw:
        raise HTTPException(400, f"{field_name} required")
    try:
        parsed = datetime.strptime(raw, "%H:%M")
    except ValueError as exc:
        raise HTTPException(400, f"{field_name} must be HH:MM") from exc
    return parsed.strftime("%H:%M")


def create_organizer_session(session, event_id: uuid.UUID) -> str:
    token = secrets.token_urlsafe(32)
    now = datetime.now(timezone.utc)
    session.execute(
        text(
            """
            INSERT INTO regatta_organizer_sessions (
                id, event_id, token_hash, created_at, last_used_at
            )
            VALUES (
                gen_random_uuid(), :event_id, :token_hash, :created_at, :last_used_at
            )
            """
        ),
        {
            "event_id": event_id,
            "token_hash": hash_secret(token),
            "created_at": now,
            "last_used_at": now,
        },
    )
    return token


def create_superuser_session(session, superuser_id: uuid.UUID) -> str:
    token = secrets.token_urlsafe(32)
    now = datetime.now(timezone.utc)
    session.execute(
        text(
            """
            INSERT INTO superuser_sessions (
                id, superuser_id, token_hash, created_at, last_used_at
            )
            VALUES (
                gen_random_uuid(), :superuser_id, :token_hash, :created_at, :last_used_at
            )
            """
        ),
        {
            "superuser_id": superuser_id,
            "token_hash": hash_secret(token),
            "created_at": now,
            "last_used_at": now,
        },
    )
    return token


def require_superuser(session, superuser_token: str) -> uuid.UUID:
    row = session.execute(
        text(
            """
            SELECT s.id, s.superuser_id
            FROM superuser_sessions s
            WHERE s.token_hash = :token_hash
              AND s.revoked_at IS NULL
            """
        ),
        {"token_hash": hash_secret(superuser_token)},
    ).fetchone()
    if not row:
        raise HTTPException(403, "Only superuser may perform this action")
    session.execute(
        text(
            """
            UPDATE superuser_sessions
            SET last_used_at = NOW()
            WHERE id = :session_id
            """
        ),
        {"session_id": row[0]},
    )
    return row[1]


def require_event_organizer(session, event_id: str, organizer_token: str) -> uuid.UUID:
    row = session.execute(
        text(
            """
            SELECT s.id
            FROM regatta_organizer_sessions s
            WHERE s.event_id = CAST(:event_id AS uuid)
              AND s.token_hash = :token_hash
              AND s.revoked_at IS NULL
            """
        ),
        {"event_id": event_id, "token_hash": hash_secret(organizer_token)},
    ).fetchone()
    if not row:
        raise HTTPException(403, "Only organizer may modify this event/race")
    session.execute(
        text(
            """
            UPDATE regatta_organizer_sessions
            SET last_used_at = NOW()
            WHERE id = :session_id
            """
        ),
        {"session_id": row[0]},
    )
    return row[0]


def get_race_event_id(session, race_id: str) -> uuid.UUID:
    row = session.execute(
        text("SELECT event_id FROM regatta_races WHERE id = CAST(:race_id AS uuid)"),
        {"race_id": race_id},
    ).fetchone()
    if not row:
        raise HTTPException(404, "Race not found")
    return row[0]


def build_gates_payload(session, race_id: str) -> list[dict[str, Any]]:
    rows = session.execute(
        text(
            """
            SELECT id, gate_order, gate_type, name,
                   point_a_lat, point_a_lon, point_b_lat, point_b_lon
            FROM regatta_course_gates
            WHERE race_id = CAST(:race_id AS uuid)
            ORDER BY gate_order, name, id
            """
        ),
        {"race_id": race_id},
    ).fetchall()
    return [
        {
            "id": str(row[0]),
            "order": row[1],
            "type": row[2],
            "name": row[3],
            "point_a": {"latitude": row[4], "longitude": row[5]},
            "point_b": {"latitude": row[6], "longitude": row[7]},
        }
        for row in rows
    ]


def build_notice_posts_payload(session, event_id: str) -> list[dict[str, Any]]:
    rows = session.execute(
        text(
            """
            SELECT id, notice_text, published_at
            FROM regatta_notice_posts
            WHERE event_id = CAST(:event_id AS uuid)
            ORDER BY published_at DESC, id DESC
            """
        ),
        {"event_id": event_id},
    ).fetchall()
    return [
        {
            "id": str(row[0]),
            "notice_text": row[1] or "",
            "published_at": row[2].isoformat() if row[2] else "",
        }
        for row in rows
    ]


def save_start_snapshots(session, race_id: str) -> None:
    participants = session.execute(
        text(
            """
            SELECT p.boat_id
            FROM regatta_race_participations p
            WHERE p.race_id = CAST(:race_id AS uuid)
              AND p.start_snapshot_epoch_ms IS NULL
            """
        ),
        {"race_id": race_id},
    ).fetchall()
    for row in participants:
        boat_id = row[0]
        signal = session.execute(
            text(
                """
                SELECT latitude, longitude, epoch_ms
                FROM regatta_signal_points
                WHERE race_id = CAST(:race_id AS uuid)
                  AND boat_id = :boat_id
                ORDER BY epoch_ms DESC
                LIMIT 1
                """
            ),
            {"race_id": race_id, "boat_id": boat_id},
        ).fetchone()
        if not signal:
            continue
        session.execute(
            text(
                """
                UPDATE regatta_race_participations
                SET start_snapshot_lat = :lat,
                    start_snapshot_lon = :lon,
                    start_snapshot_epoch_ms = :epoch_ms,
                    status = CASE WHEN status = 'joined' THEN 'started' ELSE status END
                WHERE race_id = CAST(:race_id AS uuid)
                  AND boat_id = :boat_id
                """
            ),
            {
                "race_id": race_id,
                "boat_id": boat_id,
                "lat": signal[0],
                "lon": signal[1],
                "epoch_ms": signal[2],
            },
        )


def upsert_start_snapshot(
    session,
    race_id: str,
    boat_id: str,
    latitude: float,
    longitude: float,
    epoch_millis: int,
) -> None:
    row = session.execute(
        text(
            """
            SELECT p.start_snapshot_epoch_ms, r.countdown_target_epoch_ms
            FROM regatta_race_participations p
            JOIN regatta_races r ON r.id = p.race_id
            WHERE p.race_id = CAST(:race_id AS uuid)
              AND p.boat_id = CAST(:boat_id AS uuid)
            """
        ),
        {"race_id": race_id, "boat_id": boat_id},
    ).fetchone()
    if not row:
        raise HTTPException(404, "Boat is not joined to this race")
    existing_epoch_ms = row[0]
    countdown_target_epoch_ms = row[1]
    should_update = existing_epoch_ms is None
    if not should_update and countdown_target_epoch_ms is not None:
        existing_diff = abs(int(existing_epoch_ms) - int(countdown_target_epoch_ms))
        new_diff = abs(int(epoch_millis) - int(countdown_target_epoch_ms))
        should_update = new_diff <= existing_diff
    if should_update:
        session.execute(
            text(
                """
                UPDATE regatta_race_participations
                SET start_snapshot_lat = :lat,
                    start_snapshot_lon = :lon,
                    start_snapshot_epoch_ms = :epoch_ms,
                    status = CASE WHEN status = 'joined' THEN 'started' ELSE status END
                WHERE race_id = CAST(:race_id AS uuid)
                  AND boat_id = CAST(:boat_id AS uuid)
                """
            ),
            {
                "race_id": race_id,
                "boat_id": boat_id,
                "lat": latitude,
                "lon": longitude,
                "epoch_ms": epoch_millis,
            },
        )


def orientation(a: tuple[float, float], b: tuple[float, float], c: tuple[float, float]) -> float:
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def _project_to_meters(lat: float, lon: float, reference_lat: float) -> tuple[float, float]:
    earth_radius = 6_371_000.0
    lat_rad = math.radians(lat)
    lon_rad = math.radians(lon)
    ref_lat_rad = math.radians(reference_lat)
    x = earth_radius * lon_rad * math.cos(ref_lat_rad)
    y = earth_radius * lat_rad
    return x, y


def point_to_segment_distance_meters(
    point: tuple[float, float],
    segment_a: tuple[float, float],
    segment_b: tuple[float, float],
) -> float:
    reference_lat = (point[0] + segment_a[0] + segment_b[0]) / 3.0
    px, py = _project_to_meters(point[0], point[1], reference_lat)
    ax, ay = _project_to_meters(segment_a[0], segment_a[1], reference_lat)
    bx, by = _project_to_meters(segment_b[0], segment_b[1], reference_lat)
    abx = bx - ax
    aby = by - ay
    apx = px - ax
    apy = py - ay
    ab_len_sq = abx * abx + aby * aby
    if ab_len_sq <= 0.0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, (apx * abx + apy * aby) / ab_len_sq))
    nearest_x = ax + t * abx
    nearest_y = ay + t * aby
    return math.hypot(px - nearest_x, py - nearest_y)


def point_to_point_distance_meters(
    point_a: tuple[float, float],
    point_b: tuple[float, float],
) -> float:
    reference_lat = (point_a[0] + point_b[0]) / 2.0
    ax, ay = _project_to_meters(point_a[0], point_a[1], reference_lat)
    bx, by = _project_to_meters(point_b[0], point_b[1], reference_lat)
    return math.hypot(bx - ax, by - ay)


def signed_distance_to_gate_line_meters(
    point: tuple[float, float],
    gate_a: tuple[float, float],
    gate_b: tuple[float, float],
) -> float:
    reference_lat = (point[0] + gate_a[0] + gate_b[0]) / 3.0
    px, py = _project_to_meters(point[0], point[1], reference_lat)
    ax, ay = _project_to_meters(gate_a[0], gate_a[1], reference_lat)
    bx, by = _project_to_meters(gate_b[0], gate_b[1], reference_lat)
    abx = bx - ax
    aby = by - ay
    apx = px - ax
    apy = py - ay
    ab_len = math.hypot(abx, aby)
    if ab_len <= 0.0:
        return 0.0
    cross = abx * apy - aby * apx
    return cross / ab_len


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


@app.post("/v1/regattas/events", dependencies=[Depends(require_api_key)])
def create_regatta_event(body: RegattaCreateRequest) -> dict[str, Any]:
    if not body.name.strip():
        raise HTTPException(400, "Event name required")
    start_date = parse_iso_date(body.start_date, "start_date")
    end_date = parse_iso_date(body.end_date, "end_date")
    race_end_time = parse_hh_mm(body.race_end_time, "race_end_time")
    regatta_length_nm = float(body.regatta_length_nm)
    if regatta_length_nm <= 0.0:
        raise HTTPException(400, "regatta_length_nm must be greater than 0")
    if end_date < start_date:
        raise HTTPException(400, "end_date must be on or after start_date")
    with SessionLocal() as session:
        event_id = uuid.uuid4()
        race_id = uuid.uuid4()
        join_code = normalize_join_code(body.join_code)
        organizer_code = normalize_organizer_code(body.organizer_code)
        now = datetime.now(timezone.utc)
        session.execute(
            text(
                """
                INSERT INTO regatta_events (
                    id, name, join_code, organizer_code_hash, organizer_name,
                    start_date, end_date, race_end_time, regatta_length_nm,
                    is_public, max_boats, notice_board, notice_board_updated_at, status, active_race_id, created_at, updated_at
                )
                VALUES (
                    :id, :name, :join_code, :organizer_code_hash, :organizer_name,
                    CAST(:start_date AS date), CAST(:end_date AS date),
                    :race_end_time, :regatta_length_nm,
                    :is_public, :max_boats, '', NULL, 'draft',
                    :active_race_id, :created_at, :updated_at
                )
                """
            ),
            {
                "id": event_id,
                "name": body.name.strip(),
                "join_code": join_code,
                "organizer_code_hash": hash_secret(organizer_code),
                "organizer_name": body.organizer_name.strip(),
                "start_date": start_date,
                "end_date": end_date,
                "race_end_time": race_end_time,
                "regatta_length_nm": regatta_length_nm,
                "is_public": bool(body.is_public),
                "max_boats": max(1, int(body.max_boats)),
                "active_race_id": race_id,
                "created_at": now,
                "updated_at": now,
            },
        )
        session.execute(
            text(
                """
                INSERT INTO regatta_races (
                    id, event_id, name, day_number, sequence_number, state, created_at, updated_at
                )
                VALUES (
                    :id, :event_id, 'Race 1', 1, 1, 'draft', :created_at, :updated_at
                )
                """
            ),
            {
                "id": race_id,
                "event_id": event_id,
                "created_at": now,
                "updated_at": now,
            },
        )
        organizer_token = create_organizer_session(session, event_id)
        session.commit()
    return {
        "ok": True,
        "event_id": str(event_id),
        "race_id": str(race_id),
        "join_code": join_code,
        "organizer_code": organizer_code,
        "organizer_token": organizer_token,
        "start_date": start_date,
        "end_date": end_date,
        "race_end_time": race_end_time,
        "regatta_length_nm": regatta_length_nm,
        "is_public": bool(body.is_public),
        "max_boats": max(1, int(body.max_boats)),
    }


@app.post("/v1/admin/login", dependencies=[Depends(require_api_key)])
def superuser_login(body: SuperuserLoginRequest) -> dict[str, Any]:
    username = body.username.strip()
    password = body.password
    if not username or not password:
        raise HTTPException(400, "username and password required")
    with SessionLocal() as session:
        row = session.execute(
            text(
                """
                SELECT id, username, password_hash, is_active
                FROM superusers
                WHERE lower(username) = lower(:username)
                LIMIT 1
                """
            ),
            {"username": username},
        ).fetchone()
        if not row or not bool(row[3]):
            raise HTTPException(403, "Invalid credentials")
        if (row[2] or "") != hash_secret(password):
            raise HTTPException(403, "Invalid credentials")
        token = create_superuser_session(session, row[0])
        session.commit()
    return {"ok": True, "username": row[1], "superuser_token": token}


@app.patch("/v1/admin/regattas/events/{event_id}", dependencies=[Depends(require_api_key)])
def admin_update_regatta_event(event_id: str, body: AdminEventUpdateRequest) -> dict[str, Any]:
    with SessionLocal() as session:
        require_superuser(session, body.superuser_token)
        updates: list[str] = []
        params: dict[str, Any] = {"event_id": event_id}
        if body.name is not None:
            updates.append("name = :name")
            params["name"] = body.name.strip()
        if body.join_code is not None:
            updates.append("join_code = :join_code")
            params["join_code"] = normalize_join_code(body.join_code)
        if body.organizer_name is not None:
            updates.append("organizer_name = :organizer_name")
            params["organizer_name"] = body.organizer_name.strip()
        if body.status is not None:
            updates.append("status = :status")
            params["status"] = body.status.strip().lower()
        if body.is_public is not None:
            updates.append("is_public = :is_public")
            params["is_public"] = bool(body.is_public)
        if body.start_date is not None:
            updates.append("start_date = CAST(:start_date AS date)")
            params["start_date"] = parse_iso_date(body.start_date, "start_date")
        if body.end_date is not None:
            updates.append("end_date = CAST(:end_date AS date)")
            params["end_date"] = parse_iso_date(body.end_date, "end_date")
        if body.race_end_time is not None:
            updates.append("race_end_time = :race_end_time")
            params["race_end_time"] = parse_hh_mm(body.race_end_time, "race_end_time")
        if body.regatta_length_nm is not None:
            regatta_length_nm = float(body.regatta_length_nm)
            if regatta_length_nm <= 0.0:
                raise HTTPException(400, "regatta_length_nm must be greater than 0")
            updates.append("regatta_length_nm = :regatta_length_nm")
            params["regatta_length_nm"] = regatta_length_nm
        start_for_validation = params.get("start_date")
        end_for_validation = params.get("end_date")
        if start_for_validation is None or end_for_validation is None:
            current_dates = session.execute(
                text(
                    """
                    SELECT start_date, end_date
                    FROM regatta_events
                    WHERE id = CAST(:event_id AS uuid)
                    LIMIT 1
                    """
                ),
                {"event_id": event_id},
            ).fetchone()
            if not current_dates:
                raise HTTPException(404, "Event not found")
            if start_for_validation is None and current_dates[0] is not None:
                start_for_validation = current_dates[0].isoformat()
            if end_for_validation is None and current_dates[1] is not None:
                end_for_validation = current_dates[1].isoformat()
        if (
            start_for_validation
            and end_for_validation
            and end_for_validation < start_for_validation
        ):
            raise HTTPException(400, "end_date must be on or after start_date")
        if not updates:
            raise HTTPException(400, "No fields provided for update")
        updates.append("updated_at = NOW()")
        result = session.execute(
            text(
                f"""
                UPDATE regatta_events
                SET {", ".join(updates)}
                WHERE id = CAST(:event_id AS uuid)
                """
            ),
            params,
        )
        if result.rowcount == 0:
            raise HTTPException(404, "Event not found")
        session.commit()
    return {"ok": True}


@app.get("/v1/admin/regattas/events", dependencies=[Depends(require_api_key)])
def admin_list_regatta_events(superuser_token: str) -> dict[str, Any]:
    with SessionLocal() as session:
        require_superuser(session, superuser_token)
        rows = session.execute(
            text(
                """
                SELECT e.id, e.name, e.join_code, e.organizer_code_hash, e.organizer_name, e.status, e.is_public,
                       e.start_date, e.end_date, e.race_end_time, e.regatta_length_nm, e.updated_at,
                       COUNT(DISTINCT b.id) AS boats_count,
                       COUNT(DISTINCT r.id) AS races_count,
                       COUNT(DISTINCT ce.id) AS points_count
                FROM regatta_events e
                LEFT JOIN regatta_boat_entries b ON b.event_id = e.id
                LEFT JOIN regatta_races r ON r.event_id = e.id
                LEFT JOIN regatta_crossing_events ce ON ce.race_id = r.id
                GROUP BY
                    e.id, e.name, e.join_code, e.organizer_code_hash, e.organizer_name, e.status, e.is_public,
                    e.start_date, e.end_date, e.race_end_time, e.regatta_length_nm, e.updated_at, e.created_at
                ORDER BY e.start_date DESC NULLS LAST, e.updated_at DESC, e.created_at DESC
                LIMIT 500
                """
            )
        ).fetchall()
    return {
        "ok": True,
        "events": [
            {
                "event_id": str(row[0]),
                "name": row[1],
                "join_code": row[2],
                "organizer_code_hash": row[3] or "",
                "organizer_name": row[4],
                "status": row[5],
                "is_public": bool(row[6]),
                "start_date": row[7].isoformat() if row[7] else "",
                "end_date": row[8].isoformat() if row[8] else "",
                "race_end_time": row[9] or "",
                "regatta_length_nm": float(row[10] or 0.0),
                "updated_at": row[11].isoformat() if row[11] else "",
                "boats_count": int(row[12] or 0),
                "races_count": int(row[13] or 0),
                "points_count": int(row[14] or 0),
            }
            for row in rows
        ],
    }


@app.delete("/v1/admin/regattas/events/{event_id}", dependencies=[Depends(require_api_key)])
def admin_delete_regatta_event(event_id: str, superuser_token: str) -> dict[str, Any]:
    with SessionLocal() as session:
        require_superuser(session, superuser_token)
        deleted = session.execute(
            text("DELETE FROM regatta_events WHERE id = CAST(:event_id AS uuid)"),
            {"event_id": event_id},
        )
        if deleted.rowcount == 0:
            raise HTTPException(404, "Event not found")
        session.commit()
    return {"ok": True}


@app.post("/v1/admin/regattas/events/{event_id}/organizer-session", dependencies=[Depends(require_api_key)])
def admin_create_organizer_session(event_id: str, superuser_token: str) -> dict[str, Any]:
    with SessionLocal() as session:
        require_superuser(session, superuser_token)
        row = session.execute(
            text(
                """
                SELECT id
                FROM regatta_events
                WHERE id = CAST(:event_id AS uuid)
                LIMIT 1
                """
            ),
            {"event_id": event_id},
        ).fetchone()
        if not row:
            raise HTTPException(404, "Event not found")
        organizer_token = create_organizer_session(session, row[0])
        session.commit()
    return {"ok": True, "event_id": str(row[0]), "organizer_token": organizer_token}


@app.post("/v1/regattas/events/organizer-auth", dependencies=[Depends(require_api_key)])
def authenticate_regatta_organizer(body: RegattaOrganizerAuthRequest) -> dict[str, Any]:
    if not body.join_code.strip() or not body.organizer_code.strip():
        raise HTTPException(400, "join_code and organizer_code required")
    with SessionLocal() as session:
        event = session.execute(
            text(
                """
                SELECT id, organizer_code_hash
                FROM regatta_events
                WHERE upper(join_code) = upper(:join_code)
                """
            ),
            {"join_code": body.join_code.strip()},
        ).fetchone()
        if not event:
            raise HTTPException(404, "Event not found for join code")
        if (event[1] or "") != hash_secret(body.organizer_code):
            raise HTTPException(403, "Invalid organizer code")
        organizer_token = create_organizer_session(session, event[0])
        session.commit()
    return {
        "ok": True,
        "event_id": str(event[0]),
        "organizer_token": organizer_token,
    }


@app.get("/v1/regattas/events/by-join-code/{join_code}", dependencies=[Depends(require_api_key)])
def get_regatta_event_by_join_code(join_code: str) -> dict[str, Any]:
    normalized = join_code.strip().upper()
    if not normalized:
        raise HTTPException(400, "join_code required")
    with SessionLocal() as session:
        event = session.execute(
            text(
                """
                SELECT id, name, join_code, status, start_date, end_date
                FROM regatta_events
                WHERE upper(join_code) = :join_code
                LIMIT 1
                """
            ),
            {"join_code": normalized},
        ).fetchone()
        if not event:
            raise HTTPException(404, "Regatta not found")
    return {
        "ok": True,
        "event_id": str(event[0]),
        "name": event[1],
        "join_code": event[2],
        "status": event[3],
        "start_date": event[4].isoformat() if event[4] else "",
        "end_date": event[5].isoformat() if event[5] else "",
    }


@app.get("/v1/regattas/events/public", dependencies=[Depends(require_api_key)])
def list_public_regatta_events() -> dict[str, Any]:
    with SessionLocal() as session:
        rows = session.execute(
            text(
                """
                SELECT e.id, e.name, e.join_code, e.organizer_name, e.status, e.start_date, e.end_date,
                       e.race_end_time, e.regatta_length_nm, e.updated_at,
                       COUNT(DISTINCT b.id) AS boats_count,
                       COUNT(DISTINCT r.id) AS races_count,
                       COUNT(DISTINCT ce.id) AS points_count
                FROM regatta_events e
                LEFT JOIN regatta_boat_entries b ON b.event_id = e.id
                LEFT JOIN regatta_races r ON r.event_id = e.id
                LEFT JOIN regatta_crossing_events ce ON ce.race_id = r.id
                WHERE e.is_public = TRUE
                GROUP BY e.id, e.name, e.join_code, e.organizer_name, e.status, e.start_date, e.end_date, e.updated_at
                ORDER BY e.start_date DESC NULLS LAST, e.updated_at DESC, e.created_at DESC
                LIMIT 200
                """
            )
        ).fetchall()
    return {
        "ok": True,
        "events": [
            {
                "event_id": str(row[0]),
                "name": row[1],
                "join_code": row[2],
                "organizer_name": row[3],
                "status": row[4],
                "start_date": row[5].isoformat() if row[5] else "",
                "end_date": row[6].isoformat() if row[6] else "",
                "race_end_time": row[7] or "",
                "regatta_length_nm": float(row[8] or 0.0),
                "updated_at": row[9].isoformat() if row[9] else "",
                "boats_count": int(row[10] or 0),
                "races_count": int(row[11] or 0),
                "points_count": int(row[12] or 0),
            }
            for row in rows
        ],
    }


@app.post("/v1/regattas/events/join", dependencies=[Depends(require_api_key)])
def join_regatta_event(body: RegattaJoinRequest) -> dict[str, Any]:
    if not body.join_code.strip():
        raise HTTPException(400, "join_code required")
    if not body.boat_name.strip():
        raise HTTPException(400, "boat_name required")
    with SessionLocal() as session:
        event = session.execute(
            text(
                """
                SELECT id, active_race_id, max_boats
                FROM regatta_events
                WHERE upper(join_code) = upper(:join_code)
                """
            ),
            {"join_code": body.join_code.strip()},
        ).fetchone()
        if not event:
            raise HTTPException(404, "Event not found for join code")
        event_id, race_id, max_boats = event[0], event[1], int(event[2] or 0)
        boat_name = body.boat_name.strip()
        skipper_name = body.skipper_name.strip()
        existing = session.execute(
            text(
                """
                SELECT id
                FROM regatta_boat_entries
                WHERE event_id = :event_id
                  AND upper(boat_name) = upper(:boat_name)
                  AND upper(skipper_name) = upper(:skipper_name)
                ORDER BY created_at ASC
                LIMIT 1
                """
            ),
            {
                "event_id": event_id,
                "boat_name": boat_name,
                "skipper_name": skipper_name,
            },
        ).fetchone()
        if not existing:
            boats_count_row = session.execute(
                text(
                    """
                    SELECT COUNT(*)
                    FROM regatta_boat_entries
                    WHERE event_id = :event_id
                    """
                ),
                {"event_id": event_id},
            ).fetchone()
            boats_count = int(boats_count_row[0] if boats_count_row else 0)
            if max_boats > 0 and boats_count >= max_boats:
                raise HTTPException(409, "REGATTA_FULL")
        if existing:
            boat_id = existing[0]
            activity = None
            if race_id:
                activity = session.execute(
                    text(
                        """
                        SELECT MAX(ts) FROM (
                            SELECT MAX(sp.created_at) AS ts
                            FROM regatta_signal_points sp
                            WHERE sp.race_id = :race_id AND sp.boat_id = :boat_id
                            UNION ALL
                            SELECT MAX(tp.created_at) AS ts
                            FROM regatta_track_points tp
                            WHERE tp.race_id = :race_id AND tp.boat_id = :boat_id
                            UNION ALL
                            SELECT MAX(ce.created_at) AS ts
                            FROM regatta_crossing_events ce
                            WHERE ce.race_id = :race_id AND ce.boat_id = :boat_id
                        ) q
                        """
                    ),
                    {"race_id": race_id, "boat_id": boat_id},
                ).fetchone()
            last_activity = activity[0] if activity else None
            if last_activity is not None:
                inactive_for = (datetime.now(timezone.utc) - last_activity).total_seconds()
                if inactive_for < 15 * 60:
                    raise HTTPException(409, "Boat is already active on another device")
            session.execute(
                text(
                    """
                    UPDATE regatta_boat_entries
                    SET device_id = :device_id,
                        club_name = :club_name,
                        length_value = :length_value,
                        length_unit = :length_unit,
                        group_code = COALESCE(group_code, :group_code)
                    WHERE id = :boat_id
                    """
                ),
                {
                    "boat_id": boat_id,
                    "device_id": body.device_id.strip(),
                    "club_name": body.club_name.strip(),
                    "length_value": body.length_value,
                    "length_unit": (body.length_unit or "").strip() or None,
                    "group_code": (body.group_code or "").strip() or None,
                },
            )
        else:
            boat_id = uuid.uuid4()
            session.execute(
                text(
                    """
                    INSERT INTO regatta_boat_entries (
                        id, event_id, device_id, boat_name, skipper_name, club_name,
                        length_value, length_unit, group_code
                    )
                    VALUES (
                        :id, :event_id, :device_id, :boat_name, :skipper_name, :club_name,
                        :length_value, :length_unit, :group_code
                    )
                    """
                ),
                {
                    "id": boat_id,
                    "event_id": event_id,
                    "device_id": body.device_id.strip(),
                    "boat_name": boat_name,
                    "skipper_name": skipper_name,
                    "club_name": body.club_name.strip(),
                    "length_value": body.length_value,
                    "length_unit": (body.length_unit or "").strip() or None,
                    "group_code": (body.group_code or "").strip() or None,
                },
            )
        if race_id:
            session.execute(
                text(
                    """
                    INSERT INTO regatta_race_participations (id, race_id, boat_id, status, next_gate_order)
                    VALUES (gen_random_uuid(), :race_id, :boat_id, 'joined', 0)
                    ON CONFLICT (race_id, boat_id) DO NOTHING
                    """
                ),
                {"race_id": race_id, "boat_id": boat_id},
            )
        session.commit()
    return {
        "ok": True,
        "event_id": str(event_id),
        "race_id": str(race_id) if race_id else "",
        "boat_id": str(boat_id),
    }


@app.get("/v1/regattas/events/{event_id}/snapshot", dependencies=[Depends(require_api_key)])
def get_regatta_event_snapshot(event_id: str) -> dict[str, Any]:
    with SessionLocal() as session:
        event = session.execute(
            text(
                """
                SELECT id, name, join_code, organizer_name, start_date, end_date, race_end_time, regatta_length_nm,
                       is_public, max_boats,
                       COALESCE(notice_board, ''), notice_board_updated_at, status, active_race_id
                FROM regatta_events
                WHERE id = CAST(:event_id AS uuid)
                """
            ),
            {"event_id": event_id},
        ).fetchone()
        if not event:
            raise HTTPException(404, "Event not found")
        boats = session.execute(
            text(
                """
                SELECT id, device_id, boat_name, skipper_name, club_name, length_value, length_unit, group_code
                FROM regatta_boat_entries
                WHERE event_id = CAST(:event_id AS uuid)
                ORDER BY created_at, boat_name
                """
            ),
            {"event_id": event_id},
        ).fetchall()
        races = session.execute(
            text(
                """
                SELECT id, name, day_number, sequence_number, state, countdown_target_epoch_ms, scoring_target_gate_id,
                       race_date, start_time, end_time, race_length_nm
                FROM regatta_races
                WHERE event_id = CAST(:event_id AS uuid)
                ORDER BY day_number, sequence_number
                """
            ),
            {"event_id": event_id},
        ).fetchall()
        race_payload = []
        for race in races:
            race_payload.append(
                {
                    "id": str(race[0]),
                    "name": race[1],
                    "day_number": race[2],
                    "sequence_number": race[3],
                    "state": race[4],
                    "countdown_target_epoch_ms": race[5],
                    "scoring_target_gate_id": str(race[6]) if race[6] else "",
                    "race_date": race[7].isoformat() if len(race) > 7 and race[7] else "",
                    "start_time": race[8] if len(race) > 8 and race[8] else "",
                    "end_time": race[9] if len(race) > 9 and race[9] else "",
                    "race_length_nm": float(race[10] or 0.0) if len(race) > 10 else 0.0,
                    "gates": build_gates_payload(session, str(race[0])),
                }
            )
        notice_posts = build_notice_posts_payload(session, event_id)
    return {
        "event_id": str(event[0]),
        "name": event[1],
        "join_code": event[2],
        "organizer_name": event[3],
        "start_date": event[4].isoformat() if event[4] else "",
        "end_date": event[5].isoformat() if event[5] else "",
        "race_end_time": event[6] or "",
        "regatta_length_nm": float(event[7] or 0.0),
        "is_public": bool(event[8]),
        "max_boats": int(event[9] or 0),
        "notice_board": event[10] or "",
        "notice_board_updated_at": event[11].isoformat() if event[11] else "",
        "notice_posts": notice_posts,
        "status": event[12],
        "active_race_id": str(event[13]) if event[13] else "",
        "boats": [
            {
                "id": str(row[0]),
                "device_id": row[1] or "",
                "boat_name": row[2],
                "skipper_name": row[3],
                "club_name": row[4],
                "length_value": row[5],
                "length_unit": row[6] or "",
                "group_code": row[7] or "",
            }
            for row in boats
        ],
        "races": race_payload,
    }


@app.post("/v1/regattas/events/{event_id}/races", dependencies=[Depends(require_api_key)])
def create_regatta_race(event_id: str, body: RegattaRaceCreateRequest) -> dict[str, Any]:
    with SessionLocal() as session:
        require_event_organizer(session, event_id, body.organizer_token)
        row = session.execute(
            text(
                """
                SELECT COALESCE(MAX(sequence_number), 0) + 1
                FROM regatta_races
                WHERE event_id = CAST(:event_id AS uuid)
                """
            ),
            {"event_id": event_id},
        ).fetchone()
        next_sequence = int(row[0] if row and row[0] else 1)
        race_id = uuid.uuid4()
        now = datetime.now(timezone.utc)
        session.execute(
            text(
                """
                INSERT INTO regatta_races (
                    id, event_id, name, day_number, sequence_number, state, created_at, updated_at
                )
                VALUES (
                    :id, CAST(:event_id AS uuid), :name, :day_number, :sequence_number, 'draft', :created_at, :updated_at
                )
                """
            ),
            {
                "id": race_id,
                "event_id": event_id,
                "name": body.name.strip() or f"Race {next_sequence}",
                "day_number": max(1, body.day_number),
                "sequence_number": next_sequence,
                "created_at": now,
                "updated_at": now,
            },
        )
        session.execute(
            text(
                """
                UPDATE regatta_events
                SET active_race_id = :race_id, updated_at = :updated_at
                WHERE id = CAST(:event_id AS uuid)
                """
            ),
            {"race_id": race_id, "event_id": event_id, "updated_at": now},
        )
        boat_rows = session.execute(
            text("SELECT id FROM regatta_boat_entries WHERE event_id = CAST(:event_id AS uuid)"),
            {"event_id": event_id},
        ).fetchall()
        for boat in boat_rows:
            session.execute(
                text(
                    """
                    INSERT INTO regatta_race_participations (id, race_id, boat_id, status, next_gate_order)
                    VALUES (gen_random_uuid(), :race_id, :boat_id, 'joined', 0)
                    ON CONFLICT (race_id, boat_id) DO NOTHING
                    """
                ),
                {"race_id": race_id, "boat_id": boat[0]},
            )
        session.commit()
    return {"ok": True, "race_id": str(race_id)}


@app.put("/v1/regattas/events/{event_id}/boats/{boat_id}/group", dependencies=[Depends(require_api_key)])
def update_regatta_boat_group(event_id: str, boat_id: str, body: RegattaGroupUpdateRequest) -> dict[str, Any]:
    with SessionLocal() as session:
        require_event_organizer(session, event_id, body.organizer_token)
        session.execute(
            text(
                """
                UPDATE regatta_boat_entries
                SET group_code = :group_code
                WHERE id = CAST(:boat_id AS uuid)
                  AND event_id = CAST(:event_id AS uuid)
                """
            ),
            {
                "group_code": body.group_code.strip() or None,
                "boat_id": boat_id,
                "event_id": event_id,
            },
        )
        session.commit()
    return {"ok": True}


@app.put("/v1/regattas/events/{event_id}/notice-board", dependencies=[Depends(require_api_key)])
def update_regatta_notice_board(event_id: str, body: RegattaNoticeBoardUpdateRequest) -> dict[str, Any]:
    notice_text = body.notice_text.strip()
    if not notice_text:
        raise HTTPException(400, "notice_text required")
    with SessionLocal() as session:
        require_event_organizer(session, event_id, body.organizer_token)
        session.execute(
            text(
                """
                INSERT INTO regatta_notice_posts (id, event_id, notice_text, published_at)
                VALUES (gen_random_uuid(), CAST(:event_id AS uuid), :notice_text, NOW())
                """
            ),
            {
                "event_id": event_id,
                "notice_text": notice_text,
            },
        )
        session.execute(
            text(
                """
                UPDATE regatta_events
                SET notice_board = :notice_board,
                    notice_board_updated_at = NOW(),
                    updated_at = NOW()
                WHERE id = CAST(:event_id AS uuid)
                """
            ),
            {
                "event_id": event_id,
                "notice_board": notice_text,
            },
        )
        session.commit()
    return {"ok": True}


@app.put("/v1/regattas/events/{event_id}", dependencies=[Depends(require_api_key)])
def update_regatta_event(event_id: str, body: RegattaEventUpdateRequest) -> dict[str, Any]:
    if not body.name.strip():
        raise HTTPException(400, "name required")
    start_date = parse_iso_date(body.start_date, "start_date")
    end_date = parse_iso_date(body.end_date, "end_date")
    if end_date < start_date:
        raise HTTPException(400, "end_date must be on or after start_date")
    race_end_time = parse_hh_mm(body.race_end_time, "race_end_time")
    regatta_length_nm = float(body.regatta_length_nm)
    if regatta_length_nm <= 0.0:
        raise HTTPException(400, "regatta_length_nm must be greater than 0")
    max_boats = max(1, int(body.max_boats))
    join_code = normalize_join_code(body.join_code)
    organizer_code_hash = hash_secret(normalize_organizer_code(body.organizer_code))
    with SessionLocal() as session:
        require_event_organizer(session, event_id, body.organizer_token)
        result = session.execute(
            text(
                """
                UPDATE regatta_events
                SET name = :name,
                    join_code = :join_code,
                    organizer_name = :organizer_name,
                    organizer_code_hash = :organizer_code_hash,
                    start_date = CAST(:start_date AS date),
                    end_date = CAST(:end_date AS date),
                    race_end_time = :race_end_time,
                    regatta_length_nm = :regatta_length_nm,
                    max_boats = :max_boats,
                    is_public = :is_public,
                    updated_at = NOW()
                WHERE id = CAST(:event_id AS uuid)
                """
            ),
            {
                "event_id": event_id,
                "name": body.name.strip(),
                "join_code": join_code,
                "organizer_name": body.organizer_name.strip(),
                "organizer_code_hash": organizer_code_hash,
                "start_date": start_date,
                "end_date": end_date,
                "race_end_time": race_end_time,
                "regatta_length_nm": regatta_length_nm,
                "max_boats": max_boats,
                "is_public": bool(body.is_public),
            },
        )
        if result.rowcount == 0:
            raise HTTPException(404, "Event not found")
        session.commit()
    return {"ok": True}


@app.delete("/v1/regattas/events/{event_id}/boats/{boat_id}", dependencies=[Depends(require_api_key)])
def delete_regatta_boat(event_id: str, boat_id: str, organizer_token: str) -> dict[str, Any]:
    with SessionLocal() as session:
        require_event_organizer(session, event_id, organizer_token)
        result = session.execute(
            text(
                """
                DELETE FROM regatta_boat_entries
                WHERE id = CAST(:boat_id AS uuid)
                  AND event_id = CAST(:event_id AS uuid)
                """
            ),
            {"boat_id": boat_id, "event_id": event_id},
        )
        if result.rowcount == 0:
            raise HTTPException(404, "Boat not found for this regatta")
        session.commit()
    return {"ok": True}


@app.put("/v1/regattas/races/{race_id}/course", dependencies=[Depends(require_api_key)])
def update_regatta_course(race_id: str, body: RegattaCourseUpdateRequest) -> dict[str, Any]:
    with SessionLocal() as session:
        event_id = get_race_event_id(session, race_id)
        require_event_organizer(session, str(event_id), body.organizer_token)
        race_row = session.execute(
            text(
                """
                SELECT countdown_target_epoch_ms
                FROM regatta_races
                WHERE id = CAST(:race_id AS uuid)
                """
            ),
            {"race_id": race_id},
        ).fetchone()
        countdown_target = race_row[0] if race_row else None
        if countdown_target is not None:
            now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
            if now_ms >= int(countdown_target) - 5 * 60 * 1000:
                raise HTTPException(409, "Course editing locked from T-5 minutes onwards")
        session.execute(
            text("DELETE FROM regatta_course_gates WHERE race_id = CAST(:race_id AS uuid)"),
            {"race_id": race_id},
        )
        for gate in body.gates:
            session.execute(
                text(
                    """
                    INSERT INTO regatta_course_gates (
                        id, race_id, gate_order, gate_type, name,
                        point_a_lat, point_a_lon, point_b_lat, point_b_lon
                    )
                    VALUES (
                        gen_random_uuid(), CAST(:race_id AS uuid), :gate_order, :gate_type, :name,
                        :point_a_lat, :point_a_lon, :point_b_lat, :point_b_lon
                    )
                    """
                ),
                {
                    "race_id": race_id,
                    "gate_order": gate.order,
                    "gate_type": gate.type.strip(),
                    "name": gate.name.strip(),
                    "point_a_lat": gate.point_a.latitude,
                    "point_a_lon": gate.point_a.longitude,
                    "point_b_lat": gate.point_b.latitude,
                    "point_b_lon": gate.point_b.longitude,
                },
            )
        session.commit()
    return {"ok": True}


@app.put("/v1/regattas/races/{race_id}/countdown", dependencies=[Depends(require_api_key)])
def update_regatta_countdown(race_id: str, body: RegattaCountdownRequest) -> dict[str, Any]:
    with SessionLocal() as session:
        event_id = get_race_event_id(session, race_id)
        require_event_organizer(session, str(event_id), body.organizer_token)
        row = session.execute(
            text(
                """
                SELECT countdown_target_epoch_ms
                FROM regatta_races
                WHERE id = CAST(:race_id AS uuid)
                """
            ),
            {"race_id": race_id},
        ).fetchone()
        if not row:
            raise HTTPException(404, "Race not found")
        now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
        requested = int(body.countdown_target_epoch_ms)
        if requested < now_ms + 5 * 60 * 1000:
            raise HTTPException(400, "Start time must be at least 5 minutes from now")
        session.execute(
            text(
                """
                UPDATE regatta_races
                SET countdown_target_epoch_ms = :target_epoch_ms,
                    updated_at = NOW()
                WHERE id = CAST(:race_id AS uuid)
                """
            ),
            {"target_epoch_ms": body.countdown_target_epoch_ms, "race_id": race_id},
        )
        session.commit()
    return {"ok": True}


@app.put("/v1/regattas/races/{race_id}/details", dependencies=[Depends(require_api_key)])
def update_regatta_race_details(race_id: str, body: RegattaRaceDetailsUpdateRequest) -> dict[str, Any]:
    race_date = parse_iso_date(body.race_date, "race_date")
    start_time = parse_hh_mm(body.start_time, "start_time")
    end_time = parse_hh_mm(body.end_time, "end_time")
    race_length_nm = float(body.race_length_nm)
    if race_length_nm <= 0.0:
        raise HTTPException(400, "race_length_nm must be greater than 0")
    if end_time <= start_time:
        raise HTTPException(400, "end_time must be after start_time")
    with SessionLocal() as session:
        event_id = get_race_event_id(session, race_id)
        require_event_organizer(session, str(event_id), body.organizer_token)
        session.execute(
            text(
                """
                UPDATE regatta_races
                SET race_date = CAST(:race_date AS date),
                    start_time = :start_time,
                    end_time = :end_time,
                    race_length_nm = :race_length_nm,
                    updated_at = NOW()
                WHERE id = CAST(:race_id AS uuid)
                """
            ),
            {
                "race_id": race_id,
                "race_date": race_date,
                "start_time": start_time,
                "end_time": end_time,
                "race_length_nm": race_length_nm,
            },
        )
        session.commit()
    return {"ok": True}


@app.put("/v1/regattas/races/{race_id}/scoring-target", dependencies=[Depends(require_api_key)])
def update_regatta_scoring_target(race_id: str, body: RegattaScoringTargetRequest) -> dict[str, Any]:
    with SessionLocal() as session:
        event_id = get_race_event_id(session, race_id)
        require_event_organizer(session, str(event_id), body.organizer_token)
        gate = session.execute(
            text(
                """
                SELECT id, gate_type
                FROM regatta_course_gates
                WHERE id = CAST(:gate_id AS uuid)
                  AND race_id = CAST(:race_id AS uuid)
                """
            ),
            {"gate_id": body.gate_id, "race_id": race_id},
        ).fetchone()
        if not gate:
            raise HTTPException(404, "Gate not found in this race")
        if (gate[1] or "").strip().lower() == "start":
            raise HTTPException(400, "Start gate cannot be scoring target")
        session.execute(
            text(
                """
                UPDATE regatta_races
                SET scoring_target_gate_id = CAST(:gate_id AS uuid),
                    updated_at = NOW()
                WHERE id = CAST(:race_id AS uuid)
                """
            ),
            {"race_id": race_id, "gate_id": body.gate_id},
        )
        session.commit()
    return {"ok": True}


@app.put("/v1/regattas/races/{race_id}/state", dependencies=[Depends(require_api_key)])
def update_regatta_state(race_id: str, body: RegattaStateRequest) -> dict[str, Any]:
    next_state = body.state.strip().lower()
    if next_state not in {"draft", "lobby", "armed", "started", "finished"}:
        raise HTTPException(400, "Unsupported state")
    with SessionLocal() as session:
        event_id = get_race_event_id(session, race_id)
        require_event_organizer(session, str(event_id), body.organizer_token)
        session.execute(
            text(
                """
                UPDATE regatta_races
                SET state = :state,
                    updated_at = NOW()
                WHERE id = CAST(:race_id AS uuid)
                """
            ),
            {"state": next_state, "race_id": race_id},
        )
        if next_state == "started":
            save_start_snapshots(session, race_id)
        if next_state == "finished":
            session.execute(
                text(
                    """
                    UPDATE regatta_race_participations
                    SET status = CASE
                        WHEN finished_at_epoch_ms IS NOT NULL THEN 'finished'
                        WHEN status = 'joined' THEN 'closed'
                        ELSE status
                    END
                    WHERE race_id = CAST(:race_id AS uuid)
                    """
                ),
                {"race_id": race_id},
            )
        session.commit()
    return {"ok": True}


@app.post("/v1/regattas/races/{race_id}/boats/{boat_id}/signal-batch", dependencies=[Depends(require_api_key)])
def post_regatta_signal_batch(race_id: str, boat_id: str, body: RegattaSignalBatchRequest) -> dict[str, Any]:
    if not body.points:
        return {"ok": True, "stored": 0}
    with SessionLocal() as session:
        participation = session.execute(
            text(
                """
                SELECT 1
                FROM regatta_race_participations
                WHERE race_id = CAST(:race_id AS uuid)
                  AND boat_id = CAST(:boat_id AS uuid)
                """
            ),
            {"race_id": race_id, "boat_id": boat_id},
        ).fetchone()
        if not participation:
            raise HTTPException(404, "Boat is not joined to this race")
        for point in body.points:
            session.execute(
                text(
                    """
                    INSERT INTO regatta_signal_points (
                        id, race_id, boat_id, latitude, longitude, epoch_ms
                    )
                    VALUES (
                        gen_random_uuid(), CAST(:race_id AS uuid), CAST(:boat_id AS uuid),
                        :latitude, :longitude, :epoch_ms
                    )
                    """
                ),
                {
                    "race_id": race_id,
                    "boat_id": boat_id,
                    "latitude": point.latitude,
                    "longitude": point.longitude,
                    "epoch_ms": point.epoch_millis,
                },
            )
        session.commit()
    return {"ok": True, "stored": len(body.points)}


@app.post("/v1/regattas/races/{race_id}/boats/{boat_id}/start-snapshot", dependencies=[Depends(require_api_key)])
def post_regatta_start_snapshot(
    race_id: str, boat_id: str, body: RegattaStartSnapshotRequest
) -> dict[str, Any]:
    with SessionLocal() as session:
        upsert_start_snapshot(
            session=session,
            race_id=race_id,
            boat_id=boat_id,
            latitude=body.latitude,
            longitude=body.longitude,
            epoch_millis=body.epoch_millis,
        )
        session.commit()
    return {"ok": True}


@app.post("/v1/regattas/races/{race_id}/boats/{boat_id}/track-batch", dependencies=[Depends(require_api_key)])
def post_regatta_track_batch(race_id: str, boat_id: str, body: RegattaTrackBatchRequest) -> dict[str, Any]:
    if not body.points:
        return {"ok": True, "stored": 0}
    with SessionLocal() as session:
        for point in body.points:
            session.execute(
                text(
                    """
                    INSERT INTO regatta_track_points (
                        id, race_id, boat_id, latitude, longitude, epoch_ms, speed_knots, heading_deg
                    )
                    VALUES (
                        gen_random_uuid(), CAST(:race_id AS uuid), CAST(:boat_id AS uuid),
                        :latitude, :longitude, :epoch_ms, :speed_knots, :heading_deg
                    )
                    """
                ),
                {
                    "race_id": race_id,
                    "boat_id": boat_id,
                    "latitude": point.latitude,
                    "longitude": point.longitude,
                    "epoch_ms": point.epoch_millis,
                    "speed_knots": point.speed_knots,
                    "heading_deg": point.heading_deg,
                },
            )
        session.commit()
    return {"ok": True, "stored": len(body.points)}


@app.get("/v1/regattas/races/{race_id}/boats/{boat_id}/track", dependencies=[Depends(require_api_key)])
def get_regatta_boat_track(race_id: str, boat_id: str) -> dict[str, Any]:
    with SessionLocal() as session:
        rows = session.execute(
            text(
                """
                SELECT latitude, longitude, epoch_ms, speed_knots, heading_deg
                FROM regatta_track_points
                WHERE race_id = CAST(:race_id AS uuid)
                  AND boat_id = CAST(:boat_id AS uuid)
                ORDER BY epoch_ms ASC
                LIMIT 20000
                """
            ),
            {"race_id": race_id, "boat_id": boat_id},
        ).fetchall()
    return {
        "ok": True,
        "race_id": race_id,
        "boat_id": boat_id,
        "points": [
            {
                "latitude": float(row[0]),
                "longitude": float(row[1]),
                "epoch_millis": int(row[2]),
                "speed_knots": float(row[3]) if row[3] is not None else None,
                "heading_deg": float(row[4]) if row[4] is not None else None,
            }
            for row in rows
        ],
    }


@app.post("/v1/regattas/races/{race_id}/boats/{boat_id}/crossings/client", dependencies=[Depends(require_api_key)])
def post_regatta_client_crossing(
    race_id: str, boat_id: str, body: RegattaClientCrossingRequest
) -> dict[str, Any]:
    with SessionLocal() as session:
        race = session.execute(
            text(
                """
                SELECT state
                FROM regatta_races
                WHERE id = CAST(:race_id AS uuid)
                """
            ),
            {"race_id": race_id},
        ).fetchone()
        if not race:
            raise HTTPException(404, "Race not found")
        if (race[0] or "").strip().lower() != "started":
            raise HTTPException(409, "Race not started")
        participation = session.execute(
            text(
                """
                SELECT 1
                FROM regatta_race_participations
                WHERE race_id = CAST(:race_id AS uuid)
                  AND boat_id = CAST(:boat_id AS uuid)
                """
            ),
            {"race_id": race_id, "boat_id": boat_id},
        ).fetchone()
        if not participation:
            raise HTTPException(404, "Boat is not joined to this race")
        gate = session.execute(
            text(
                """
                SELECT id, gate_type
                FROM regatta_course_gates
                WHERE id = CAST(:gate_id AS uuid)
                  AND race_id = CAST(:race_id AS uuid)
                """
            ),
            {"gate_id": body.gate_id, "race_id": race_id},
        ).fetchone()
        if not gate:
            raise HTTPException(404, "Gate not found in this race")
        gate_type = (gate[1] or "").strip().lower()
        if gate_type == "start":
            return {"ok": True, "stored": False, "reason": "start_gate_ignored"}
        existing = session.execute(
            text(
                """
                SELECT id, crossing_epoch_ms
                FROM regatta_crossing_events
                WHERE race_id = CAST(:race_id AS uuid)
                  AND boat_id = CAST(:boat_id AS uuid)
                  AND gate_id = CAST(:gate_id AS uuid)
                ORDER BY crossing_epoch_ms ASC
                LIMIT 1
                """
            ),
            {"race_id": race_id, "boat_id": boat_id, "gate_id": body.gate_id},
        ).fetchone()
        if existing:
            return {"ok": True, "stored": False, "crossing_id": str(existing[0])}
        crossing_id = uuid.uuid4()
        session.execute(
            text(
                """
                INSERT INTO regatta_crossing_events (
                    id, race_id, boat_id, gate_id, crossing_epoch_ms, source, status
                )
                VALUES (
                    :id, CAST(:race_id AS uuid), CAST(:boat_id AS uuid), CAST(:gate_id AS uuid),
                    :crossing_epoch_ms, :source, 'recorded'
                )
                """
            ),
            {
                "id": crossing_id,
                "race_id": race_id,
                "boat_id": boat_id,
                "gate_id": body.gate_id,
                "crossing_epoch_ms": body.crossing_epoch_ms,
                "source": (body.source or "client_auto").strip()[:32],
            },
        )
        session.commit()
    return {"ok": True, "stored": True, "crossing_id": str(crossing_id)}


@app.get("/v1/regattas/races/{race_id}/live", dependencies=[Depends(require_api_key)])
def get_regatta_live_snapshot(race_id: str) -> dict[str, Any]:
    with SessionLocal() as session:
        race = session.execute(
            text(
                """
                SELECT r.id, r.event_id, r.name, r.state, r.countdown_target_epoch_ms, r.scoring_target_gate_id
                FROM regatta_races r
                JOIN regatta_events e ON e.id = r.event_id
                WHERE r.id = CAST(:race_id AS uuid)
                """
            ),
            {"race_id": race_id},
        ).fetchone()
        if not race:
            raise HTTPException(404, "Race not found")
        participants = session.execute(
            text(
                """
                SELECT b.id, b.boat_name, b.skipper_name, b.group_code,
                       p.status, p.start_snapshot_epoch_ms, p.start_snapshot_lat, p.start_snapshot_lon,
                       p.next_gate_order, p.finished_at_epoch_ms,
                       s.latitude, s.longitude, s.epoch_ms, t.speed_knots
                FROM regatta_race_participations p
                JOIN regatta_boat_entries b ON b.id = p.boat_id
                LEFT JOIN LATERAL (
                    SELECT latitude, longitude, epoch_ms
                    FROM regatta_signal_points sp
                    WHERE sp.race_id = p.race_id AND sp.boat_id = p.boat_id
                    ORDER BY epoch_ms DESC
                    LIMIT 1
                ) s ON TRUE
                LEFT JOIN LATERAL (
                    SELECT speed_knots
                    FROM regatta_track_points tp
                    WHERE tp.race_id = p.race_id AND tp.boat_id = p.boat_id
                    ORDER BY epoch_ms DESC
                    LIMIT 1
                ) t ON TRUE
                WHERE p.race_id = CAST(:race_id AS uuid)
                ORDER BY b.boat_name
                """
            ),
            {"race_id": race_id},
        ).fetchall()
        crossings = session.execute(
            text(
                """
                SELECT c.id, b.id, b.boat_name, g.id, g.name, g.gate_order, g.gate_type,
                       c.crossing_epoch_ms, c.source, c.status
                FROM regatta_crossing_events c
                JOIN regatta_boat_entries b ON b.id = c.boat_id
                JOIN regatta_course_gates g ON g.id = c.gate_id
                WHERE c.race_id = CAST(:race_id AS uuid)
                ORDER BY c.crossing_epoch_ms DESC
                LIMIT 200
                """
            ),
            {"race_id": race_id},
        ).fetchall()
        penalties = session.execute(
            text(
                """
                SELECT p.id, b.id, b.boat_name, p.type, p.value, p.reason, p.created_at
                FROM regatta_penalties p
                JOIN regatta_boat_entries b ON b.id = p.boat_id
                WHERE p.race_id = CAST(:race_id AS uuid)
                ORDER BY p.created_at DESC
                LIMIT 100
                """
            ),
            {"race_id": race_id},
        ).fetchall()
        gates = build_gates_payload(session, race_id)
    return {
        "event_id": str(race[1]),
        "race_id": str(race[0]),
        "race_name": race[2],
        "state": race[3],
        "countdown_target_epoch_ms": race[4],
        "scoring_target_gate_id": str(race[5]) if race[5] else "",
        "gates": gates,
        "participants": [
            {
                "boat_id": str(row[0]),
                "boat_name": row[1],
                "skipper_name": row[2],
                "group_code": row[3] or "",
                "status": row[4],
                "start_snapshot_epoch_ms": row[5],
                "start_snapshot_latitude": row[6],
                "start_snapshot_longitude": row[7],
                "next_gate_order": row[8],
                "finished_at_epoch_ms": row[9],
                "last_latitude": row[10],
                "last_longitude": row[11],
                "last_signal_epoch_ms": row[12],
                "last_speed_knots": row[13],
            }
            for row in participants
        ],
        "crossings": [
            {
                "id": str(row[0]),
                "boat_id": str(row[1]),
                "boat_name": row[2],
                "gate_id": str(row[3]),
                "gate_name": row[4],
                "gate_order": row[5],
                "gate_type": row[6],
                "crossing_epoch_ms": row[7],
                "source": row[8],
                "status": row[9],
            }
            for row in crossings
        ],
        "penalties": [
            {
                "id": str(row[0]),
                "boat_id": str(row[1]),
                "boat_name": row[2],
                "type": row[3],
                "value": row[4],
                "reason": row[5],
                "created_at": row[6].isoformat(),
            }
            for row in penalties
        ],
    }


@app.post("/v1/regattas/races/{race_id}/crossings/{crossing_id}/override", dependencies=[Depends(require_api_key)])
def override_regatta_crossing(
    race_id: str,
    crossing_id: str,
    body: RegattaCrossingOverrideRequest,
) -> dict[str, Any]:
    with SessionLocal() as session:
        event_id = get_race_event_id(session, race_id)
        require_event_organizer(session, str(event_id), body.organizer_token)
        session.execute(
            text(
                """
                UPDATE regatta_crossing_events
                SET status = :status,
                    source = 'manual_override'
                WHERE id = CAST(:crossing_id AS uuid)
                  AND race_id = CAST(:race_id AS uuid)
                """
            ),
            {"status": body.status.strip().lower(), "crossing_id": crossing_id, "race_id": race_id},
        )
        session.commit()
    return {"ok": True}


@app.post("/v1/regattas/races/{race_id}/penalties", dependencies=[Depends(require_api_key)])
def create_regatta_penalty(race_id: str, body: dict[str, Any]) -> dict[str, Any]:
    organizer_token = str(body.get("organizer_token", "")).strip()
    boat_id = str(body.get("boat_id", "")).strip()
    penalty_type = str(body.get("type", "")).strip()
    if not organizer_token or not boat_id or not penalty_type:
        raise HTTPException(400, "organizer_token, boat_id and type required")
    with SessionLocal() as session:
        event_id = get_race_event_id(session, race_id)
        require_event_organizer(session, str(event_id), organizer_token)
        penalty_id = uuid.uuid4()
        session.execute(
            text(
                """
                INSERT INTO regatta_penalties (id, race_id, boat_id, type, value, reason, created_by)
                VALUES (
                    :id, CAST(:race_id AS uuid), CAST(:boat_id AS uuid), :type, :value, :reason, :created_by
                )
                """
            ),
            {
                "id": penalty_id,
                "race_id": race_id,
                "boat_id": boat_id,
                "type": penalty_type,
                "value": body.get("value"),
                "reason": str(body.get("reason", "")).strip(),
                "created_by": organizer_token[:32],
            },
        )
        session.commit()
    return {"ok": True, "penalty_id": str(penalty_id)}
