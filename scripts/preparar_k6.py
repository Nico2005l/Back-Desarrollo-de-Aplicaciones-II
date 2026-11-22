
#!/usr/bin/env python3
"""Start an isolated local Compose stack and prepare 50 disposable k6 accounts."""

from __future__ import annotations

import argparse
import http.client
import json
import os
import re
import secrets
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, time as day_time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo


BACK = Path(__file__).resolve().parents[1]
COMPOSE_FILES = ("docker-compose.yml", "docker-compose.k6.yml")
ZONE = ZoneInfo("America/Argentina/Buenos_Aires")
ACCOUNT_COUNT = 50
ENV_FILE: Path | None = None


class PreparationError(RuntimeError):
    pass


def docker_compose(project: str, port: int, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    command = ["docker", "compose", "-p", project]
    if ENV_FILE is not None:
        command.extend(("--env-file", str(ENV_FILE)))
    for compose_file in COMPOSE_FILES:
        command.extend(("-f", compose_file))
    command.extend(args)
    environment = os.environ.copy()
    environment["API_PUERTO"] = str(port)
    result = subprocess.run(command, cwd=BACK, env=environment, text=True, capture_output=True)
    if check and result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise PreparationError(f"Falló {' '.join(command)}: {detail}")
    return result


def ensure_fresh_project(project: str) -> None:
    for resource, command in (
        ("contenedores", ["docker", "ps", "-aq", "--filter", f"label=com.docker.compose.project={project}"]),
        ("volúmenes", ["docker", "volume", "ls", "-q", "--filter", f"label=com.docker.compose.project={project}"]),
        ("redes", ["docker", "network", "ls", "-q", "--filter", f"label=com.docker.compose.project={project}"]),
    ):
        result = subprocess.run(command, cwd=BACK, text=True, capture_output=True)
        if result.returncode != 0:
            raise PreparationError(f"No se pudo comprobar si el proyecto tiene {resource}: {result.stderr.strip()}")
        if result.stdout.strip():
            raise PreparationError(f"El proyecto {project} ya tiene {resource}; elegí un nombre nuevo.")


def free_local_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return int(listener.getsockname()[1])


def api(method: str, url: str, token: str | None = None, body: dict | None = None) -> tuple[int, dict | list | str]:
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else ""
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        raise PreparationError(f"{method} {url} respondió HTTP {error.code}: {raw[:600]}") from error
    except (urllib.error.URLError, TimeoutError, http.client.RemoteDisconnected, ConnectionError) as error:
        raise PreparationError(f"No se pudo completar {method} {url}: {error}") from error


def wait_for_api(base_url: str) -> None:
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        try:
            status, _ = api("GET", f"{base_url}/api/health")
            if status == 200:
                return
        except PreparationError:
            time.sleep(2)
    raise PreparationError("La API aislada no quedó saludable en cuatro minutos; revisá sus logs con docker compose.")


def confirmation_token(project: str, port: int, email: str, deadline_seconds: int = 30) -> str:
    deadline = time.monotonic() + deadline_seconds
    pattern = re.compile(r"confirmar-email\?token=([^&\s]+)")
    while time.monotonic() < deadline:
        logs = docker_compose(project, port, "logs", "--no-color", "--since", "2m", "back", check=False)
        for line in logs.stdout.splitlines():
            if email not in line:
                continue
            match = pattern.search(line)
            if match:
                token = urllib.parse.unquote(match.group(1))
                if token:
                    return token
        time.sleep(1)
    raise PreparationError(
        f"No apareció el enlace de confirmación para {email}. Confirmá que el override k6 esté activo y revisá los logs."
    )


def create_confirmed_users(base_url: str, project: str, port: int) -> list[dict]:
    run_suffix = project.removeprefix("planta-k6-")
    users: list[dict] = []
    for index in range(ACCOUNT_COUNT):
        email = f"k6-{run_suffix}-{index + 1:02d}@loadtest.invalid"
        password = f"K6{secrets.token_hex(18)}7"
        status, _ = api("POST", f"{base_url}/api/auth/register", body={
            "nombre": f"Usuario de carga {index + 1:02d}",
            "email": email,
            "telefono": f"+54 11 5555 {index + 1:04d}",
            "password": password,
            "rol": "CLIENTE",
        })
        if status != 201:
            raise PreparationError(f"El alta de {email} respondió HTTP {status}.")

        raw_confirmation = confirmation_token(project, port, email)
        status, session = api("POST", f"{base_url}/api/auth/confirmar-email", body={"token": raw_confirmation})
        if status != 200 or not isinstance(session, dict) or not session.get("token"):
            raise PreparationError(f"No se pudo confirmar {email} o la respuesta no incluyó un token de acceso.")
        users.append({"email": email, "token": session["token"]})
        print(f"Cuenta {index + 1}/{ACCOUNT_COUNT} creada y confirmada.")
    return users


def candidate_slots(opening: str, closing: str) -> list[tuple[str, str]]:
    opens = day_time.fromisoformat(opening)
    closes = day_time.fromisoformat(closing)
    start_minutes = opens.hour * 60 + opens.minute
    start_minutes = ((start_minutes + 29) // 30) * 30
    close_minutes = closes.hour * 60 + closes.minute
    slots = []
    while start_minutes + 30 <= close_minutes:
        start = day_time(start_minutes // 60, start_minutes % 60)
        end_minutes = start_minutes + 30
        end = day_time(end_minutes // 60, end_minutes % 60)
        slots.append((start.strftime("%H:%M"), end.strftime("%H:%M")))
        start_minutes += 30
    return slots


def assign_unique_spaces(base_url: str, users: list[dict]) -> list[dict]:
    _, buildings = api("GET", f"{base_url}/api/edificios", token=users[0]["token"])
    if not isinstance(buildings, list):
        raise PreparationError("GET /api/edificios no devolvió una lista.")

    floors = [
        {"id": floor["id"], "apertura": building["apertura"], "cierre": building["cierre"]}
        for building in buildings
        for floor in building.get("pisos", [])
        if building.get("apertura") and building.get("cierre")
    ]
    if not floors:
        raise PreparationError("No se encontraron pisos con horario para consultar sus planos.")

    assignments: list[dict] = []
    today = datetime.now(ZONE).date()
    plan_calls = 0
    # Demo bookings are disabled in this isolated stack, so one weekday and one valid slot per
    # floor is enough to inventory its free spaces without repeatedly querying the same floor.
    for day_offset in range(1, 61):
        target_day = today + timedelta(days=day_offset)
        if target_day.weekday() >= 5:
            continue
        for floor in floors:
            slots = candidate_slots(floor["apertura"], floor["cierre"])
            if not slots:
                continue
            start, end = slots[0]
            query = urllib.parse.urlencode({"fecha": target_day.isoformat(), "desde": start, "hasta": end})
            token = users[plan_calls % len(users)]["token"]
            _, plan = api("GET", f"{base_url}/api/pisos/{floor['id']}/plano?{query}", token=token)
            plan_calls += 1
            if not isinstance(plan, dict):
                raise PreparationError(f"El plano del piso {floor['id']} no devolvió un objeto JSON.")
            for space in plan.get("espacios", []):
                if space.get("estado") != "LIBRE":
                    continue
                assignments.append({
                    "pisoId": int(floor["id"]),
                    "espacioId": int(space["id"]),
                    "fecha": target_day.isoformat(),
                    "desde": start,
                    "hasta": end,
                })
                if len(assignments) == ACCOUNT_COUNT:
                    return assignments
            time.sleep(0.1)
        if assignments:
            break
    raise PreparationError(
        f"Se encontraron solo {len(assignments)} espacios distintos libres para la primera fecha hábil; hacen falta 50."
    )


def main() -> int:
    global ENV_FILE
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", help="nombre nuevo con prefijo planta-k6-; se genera uno si se omite")
    parser.add_argument("--api-port", type=int, help="puerto local libre; se elige uno automáticamente si se omite")
    args = parser.parse_args()

    suffix = args.project or f"{datetime.now().strftime('%Y%m%d%H%M%S')}-{secrets.token_hex(3)}"
    project = suffix if suffix.startswith("planta-k6-") else f"planta-k6-{suffix}"
    if not re.fullmatch(r"planta-k6-[a-z0-9][a-z0-9-]*", project):
        raise PreparationError("El nombre de proyecto debe usar solo minúsculas, números y guiones, con prefijo planta-k6-.")
    port = args.api_port or free_local_port()
    if not 1024 <= port <= 65535:
        raise PreparationError("El puerto local debe estar entre 1024 y 65535.")

    ensure_fresh_project(project)
    data_dir = BACK / "k6" / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    ENV_FILE = data_dir / f".env-{project}"
    environment_values = {
        "API_PUERTO": str(port),
        "DB_PASSWORD": secrets.token_hex(32),
        "JWT_SECRET": secrets.token_hex(32),
        "CIFRADO_CLAVE_ACTUAL": secrets.token_hex(32),
        "MENSAJERIA_FIRMA_SECRETO": secrets.token_hex(32),
        "RABBITMQ_ADMIN_PASSWORD": secrets.token_hex(32),
        "RABBITMQ_APP_PASSWORD": secrets.token_hex(32),
        "DEMO_PASSWORD": secrets.token_hex(24),
        "MAIL_PASSWORD": "",
    }
    ENV_FILE.write_text("".join(f"{key}={value}\n" for key, value in environment_values.items()), encoding="utf-8")
    print(f"Iniciando proyecto Docker aislado {project} en localhost:{port}.")
    docker_compose(project, port, "up", "-d", "--build", "back")
    base_url = f"http://127.0.0.1:{port}"
    wait_for_api(base_url)
    print("API saludable; generando las cuentas de carga.")
    users = create_confirmed_users(base_url, project, port)
    assignments = assign_unique_spaces(base_url, users)
    dataset = [dict(user, **assignment) for user, assignment in zip(users, assignments, strict=True)]

    (data_dir / "usuarios.json").write_text(json.dumps(dataset, indent=2) + "\n", encoding="utf-8")
    (data_dir / "ejecucion.json").write_text(json.dumps({
        "project": project,
        "apiPort": port,
        "envFile": str(ENV_FILE.relative_to(BACK)).replace("\\", "/"),
    }, indent=2) + "\n", encoding="utf-8")

    print(f"Listo: {len(dataset)} cuentas confirmadas y espacios únicos en k6/data/usuarios.json.")
    print("Los tokens son credenciales temporales; el directorio k6/data está ignorado por Git.")
    print("Ejecutá la carga con:")
    print(f"docker compose -p {project} --env-file {ENV_FILE.relative_to(BACK).as_posix()} -f docker-compose.yml -f docker-compose.k6.yml --profile k6 run --rm k6 run /scripts/carga.js")
    print("No se eliminaron contenedores ni volúmenes.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PreparationError as error:
        print(f"Error de preparación: {error}", file=sys.stderr)
        raise SystemExit(1)
