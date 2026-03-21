# StartLine NAS (Docker)

## Što je u folderu

| Datoteka | Opis |
|----------|------|
| `docker-compose.yml` | Postgres + API |
| `Dockerfile`, `main.py`, `requirements.txt` | Build API kontejnera |
| `env.template` | Primjer varijabli |
| `.env` | **Tvoja** konfiguracija (ne commitaj; vidi `.gitignore`) |
| `run-nas.sh` | Jedna naredba za pokretanje |
| `portainer-extra/` | Stare Portainer varijante (opcija) |

## Na Synology NAS (SSH)

1. Kopiraj **cijeli** ovaj folder na NAS, npr. `/volume1/docker/startline/`.
2. Ako nemaš `.env`:  
   `cp env.template .env`  
   pa uredi lozinke i `API_KEY`.
3. Pokreni:

```bash
cd /volume1/docker/startline
chmod +x run-nas.sh
./run-nas.sh
```

Ili ručno:

```bash
cd /volume1/docker/startline
docker compose up -d --build
```

4. Provjera:

```bash
curl -s http://127.0.0.1:8080/health
```

Vanjski portovi u **`docker-compose.yml`**: API `8080:8000`, Postgres **`15432:5432`** (DBeaver: **port 15432**, ne 5432 — na Synologyju je 5432 često zauzet). Port baze ne forwardaj na ruter bez VPN-a.

### Prazan `docker port startline-api`

- Ako je API u **Restarting**, port se ne drži — prvo `docker logs startline-api`.
- Na DSM-u je **`ports: "8080:8000"`** fiksno u composeu (bez `${API_HOST_PORT}`) radi pouzdanosti.

### Build piše `transferring context: 64B`

Kontekst mora biti folder gdje su **Dockerfile**, **main.py**, **requirements.txt**. Pokreni iz tog foldera; jednom probaj čisti build:

`sudo docker compose build --no-cache api`

## API

- `GET /health` — bez ključa  
- `PUT/GET /v1/ships/{ship_code}/telemetry/latest` — header `X-API-Key: <API_KEY>`

### Android (Anchoring sinkronizacija)

Base URL i API ključ ugrađeni su u aplikaciju (`NasDefaults.kt`). Na ekranu **Anchoring**, ispod karte: **Enter Ship Code**, **Sending mode** / **Receiving mode**, **Start** / **STOP** (dvoklik). **Receiving** povlači samo **sidro + trag broda** s NAS-a; zone/alarm ostaju lokalne. Pri startu u Receiving modu pojavljuje se crveni dijalog potvrde (EN).

U JSON `data` šalju se (kad postoje): `anchor_lat`, `anchor_lon`, `area_mode` (`circle` / `conus`), `radius_meters`, `segment_center_deg`, `segment_width_deg`, `cone_apex_offset_meters`, `alarm_enabled`, te do zadnjih 200 točaka `track_points` (`latitude`, `longitude`, `epoch_millis`).

## Znak `%` ili `$` u `.env` (Docker Compose)

Compose ponekad tretira **`%`** i **`$`** u vrijednostima posebno. Ako lozinka ili `API_KEY` sadrži **`%`**, u `.env` ga udvostruči: **`%%`**.  
Ako sadrži **`$`**, koristi **`$$`**.

Primjer lozinke koja u aplikaciji treba biti `abc"#%`:

```env
POSTGRES_PASSWORD="abc\"#%%"
```

Nakon izmjene: `sudo docker compose down && sudo docker compose up -d --build`.

## API u petlji `Restarting`

Najčešće: u **`POSTGRES_PASSWORD`** ima znakova koji su u starom `DATABASE_URL` lomili adresu. Sada API sklapa URL u Pythonu (`quote_plus`). Ažuriraj `docker/` na NAS i:

```bash
sudo docker compose down
sudo docker compose up -d --build
```

Ako i dalje pada: `sudo docker logs startline-api --tail 80`.

## PostGIS kasnije

U `docker-compose.yml` zamijeni `postgres:16-alpine` s `postgis/postgis:16-3.4-alpine`, u bazi: `CREATE EXTENSION postgis;`
