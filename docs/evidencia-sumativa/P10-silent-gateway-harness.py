#!/usr/bin/env python3
"""Prueba local del timeout de latido del Panel; no acciona hardware."""

from __future__ import annotations

import base64
import datetime as dt
import secrets
import socket
import sys
import time
from pathlib import Path


HOST = "127.0.0.1"
PORT = 5051
MAX_SECONDS = 85
MIN_TIMEOUT_SECONDS = 58
MAX_TIMEOUT_SECONDS = 70


def main() -> int:
    if len(sys.argv) != 2:
        print("Uso: python P10-silent-gateway-harness.py <registro.txt>")
        return 2

    log_path = Path(sys.argv[1])
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text("", encoding="utf-8")

    def log(message: str) -> None:
        stamp = dt.datetime.now().astimezone().isoformat(timespec="seconds")
        line = f"{stamp} | {message}"
        print(line, flush=True)
        with log_path.open("a", encoding="utf-8") as output:
            output.write(line + "\n")

    nonce = base64.b64encode(secrets.token_bytes(16)).decode("ascii")
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(1)
    server.settimeout(180)
    log(f"Endpoint de prueba escuchando solo en localhost:{PORT}; esperando Panel.")

    try:
        client, address = server.accept()
    except TimeoutError:
        log("FALLO: el Panel no conectó dentro del tiempo de espera.")
        return 1

    log(f"Panel conectado desde {address[0]}:{address[1]}.")
    client.settimeout(0.5)
    client.sendall(f"HELLO;{nonce}\n".encode("ascii"))

    def send_frame(sequence: int, payload: str) -> None:
        client.sendall(f"{nonce};{sequence};{payload}\n".encode("ascii"))

    send_frame(1, "LECTURA;rasgunos;0")
    send_frame(2, "LECTURA;comedero;CERRADO")
    log("Saludo y estado inicial enviados: 0/3, CERRADO.")

    buffer = bytearray()
    first_heartbeat_at: float | None = None
    heartbeats_received = 0
    suppressed = 0
    deadline = time.monotonic() + MAX_SECONDS

    while time.monotonic() < deadline:
        try:
            chunk = client.recv(4096)
        except socket.timeout:
            continue
        except OSError as error:
            log(f"Socket finalizado: {error}.")
            break

        if not chunk:
            elapsed = (
                time.monotonic() - first_heartbeat_at
                if first_heartbeat_at is not None
                else None
            )
            if elapsed is None:
                log("FALLO: Panel cerró antes del primer latido.")
                return 1
            log(f"Panel cerró TCP tras {elapsed:.1f} s sin respuesta válida del gateway.")
            log(f"Latidos recibidos={heartbeats_received}; respuestas omitidas={suppressed}.")
            if MIN_TIMEOUT_SECONDS <= elapsed <= MAX_TIMEOUT_SECONDS:
                log("RESULTADO: APROBADA; timeout del Panel dentro de la ventana de 58–70 s.")
                return 0
            log("RESULTADO: PARCIAL; el cierre TCP no cayó dentro de la ventana esperada.")
            return 1

        buffer.extend(chunk)
        while b"\n" in buffer:
            raw_line, _, rest = buffer.partition(b"\n")
            buffer = bytearray(rest)
            line = raw_line.decode("utf-8", errors="replace").strip()
            fields = line.split(";", 4)
            if len(fields) != 5 or fields[0] != nonce:
                continue
            if fields[2:] != ["ACK", "enlace", "LATIDO"]:
                continue

            heartbeats_received += 1
            if first_heartbeat_at is None:
                first_heartbeat_at = time.monotonic()
                send_frame(3, "ACK;enlace;LATIDO")
                log("Primer latido respondido; desde aquí el gateway permanece TCP abierto y silencioso.")
            else:
                suppressed += 1
                log(f"Latido #{heartbeats_received} recibido; ACK suprimido deliberadamente.")

    log(f"FALLO: no llegó EOF del Panel tras {MAX_SECONDS} s de observación.")
    log(f"Latidos recibidos={heartbeats_received}; respuestas omitidas={suppressed}.")
    return 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("Prueba interrumpida manualmente.", file=sys.stderr)
        raise SystemExit(130)
