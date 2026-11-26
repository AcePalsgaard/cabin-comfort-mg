#!/usr/bin/env python3
"""
Fake MG iSMART (SAIC) API server for testing MG Manager without a real car.

    python mock_saic_server.py                 # port 8080, 15% random failures
    python mock_saic_server.py --fail-rate 0   # no failures
    python mock_saic_server.py --outside 2     # cold day -> app suggests "Cold"

Login: any email/phone, password "test".
From the Android emulator the PC is http://10.0.2.2:8080 (pick region "Test server").

Uses only the standard library. If pycryptodome is installed
(pip install pycryptodome) requests are decrypted and responses encrypted
exactly like the real server; otherwise responses are sent as plain JSON.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import random
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

try:
    from Crypto.Cipher import AES
    from Crypto.Util.Padding import pad, unpad
    HAVE_AES = True
except ImportError:
    HAVE_AES = False

PREFIX = "/api.app/v1"
TENANT = "459771"
PASSWORD = "test"
VIN = "LSJWH4092PN000001"

RVC_NAMES = {"5": "heated seats", "6": "climate", "8": "steering wheel", "32": "rear window"}


def md5(s: str) -> str:
    return hashlib.md5(s.encode()).hexdigest()


def aes_enc(plain: str, key: str, iv: str) -> str:
    c = AES.new(bytes.fromhex(key), AES.MODE_CBC, bytes.fromhex(iv))
    return c.encrypt(pad(plain.encode(), 16)).hex()


def aes_dec(hex_text: str, key: str, iv: str) -> str:
    c = AES.new(bytes.fromhex(key), AES.MODE_CBC, bytes.fromhex(iv))
    return unpad(c.decrypt(bytes.fromhex(hex_text)), 16).decode()


class Car:
    def __init__(self, outside: int) -> None:
        self.lock = threading.Lock()
        self.outside = outside
        self.inside = outside + 2
        self.soc = 763  # 76.3 %
        self.climate_on = False
        self.target_idx = 8
        self.seats = [0, 0]
        self.wheel = False
        self.rear_window = False
        self.plugged = False
        self.charging = False
        self.target_code = 5  # 80 %
        # Older MG4 software: remote A/C refused while plugged in but not charging.
        self.old_software = True
        self.last_tick = time.time()

    @property
    def target_pct(self) -> int:
        return 30 + self.target_code * 10

    def tick(self) -> None:
        """Cabin temperature drifts toward target while climate runs."""
        now = time.time()
        steps = int((now - self.last_tick) // 20)
        if steps <= 0:
            return
        self.last_tick += steps * 20
        target = 16 + (self.target_idx - 2) if self.climate_on else self.outside
        for _ in range(steps):
            if self.inside < target:
                self.inside += 1
            elif self.inside > target:
                self.inside -= 1
            if self.climate_on:
                self.soc = max(0, self.soc - 1)
            if self.charging:
                self.soc = min(1000, self.soc + 3)
                if self.soc >= self.target_pct * 10:
                    self.charging = False
                    log("  car: reached charge limit, charging stopped")

    def basic_status(self) -> dict:
        self.tick()
        return {
            "exteriorTemperature": self.outside,
            "interiorTemperature": self.inside,
            "remoteClimateStatus": 2 if self.climate_on else 0,
            "frontLeftSeatHeatLevel": self.seats[0],
            "frontRightSeatHeatLevel": self.seats[1],
            "steeringHeatLevel": 1 if self.wheel else 0,
            "rmtHtdRrWndSt": 1 if self.rear_window else 0,
            "lockStatus": 1,
            "engineStatus": 0,
            "handBrake": 1,
            "mileage": 123450,
            "batteryVoltage": 142,
        }

    def apply(self, req_type: str, params: dict[int, int]) -> None:
        self.tick()
        if req_type == "6":
            fan = params.get(19, 0)
            self.climate_on = fan > 0
            if 20 in params and fan > 0:
                self.target_idx = params[20]
        elif req_type == "5":
            self.seats = [params.get(17, self.seats[0]), params.get(18, self.seats[1])]
        elif req_type == "8":
            self.wheel = params.get(24, 0) == 1
        elif req_type == "32":
            self.rear_window = params.get(23, 0) == 1


class State:
    def __init__(self, args) -> None:
        self.car = Car(args.outside)
        self.fail_rate = args.fail_rate
        self.slow = args.slow
        self.tokens: dict[str, float] = {}
        self.events: dict[str, int] = {}  # event id -> calls left before data is ready
        self.reject_logins_until = 0.0
        self.http401_logins_until = 0.0
        self.block_car_until = 0.0
        self.block_car_reason = 71


def make_handler(state: State):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):  # quieter default log
            pass

        def do_GET(self):
            self.handle_call("GET")

        def do_POST(self):
            self.handle_call("POST")

        # ---------- plumbing ----------

        def handle_call(self, method: str) -> None:
            url = urlsplit(self.path)
            if url.path.startswith("/mock/"):
                return self.mock_control(url)
            if not url.path.startswith(PREFIX + "/"):
                return self.send_json(404, {"code": 404, "message": "Not found"})
            sub = url.path[len(PREFIX):]  # e.g. /vehicle/list
            sign_path = sub + (("?" + url.query) if url.query else "")
            query = {k: v[0] for k, v in parse_qs(url.query).items()}

            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length).decode() if length else ""
            token = self.headers.get("blade-auth", "")
            body = self.decrypt_body(raw, sign_path, token)
            self.check_signature(sign_path, token, raw)

            if state.slow:
                time.sleep(state.slow)

            # Random flakiness, like the real thing.
            if sub != "/oauth/token" and random.random() < state.fail_rate:
                kind = random.choice(["http500", "code", "timeout"])
                log(f"{method} {sub} -> simulated failure ({kind})")
                if kind == "http500":
                    return self.send_json(500, {"code": 500, "message": "Internal error"}, plain=True)
                if kind == "timeout":
                    time.sleep(25)
                return self.send_json(200, {"code": 4, "message": "Vehicle busy, try again"})

            if sub != "/oauth/token":
                exp = state.tokens.get(token)
                if exp is None or exp < time.time():
                    log(f"{method} {sub} -> 401 (bad or expired token)")
                    return self.send_json(200, {"code": 401, "message": "Token expired"})

            routes = {
                ("POST", "/oauth/token"): self.login,
                ("GET", "/vehicle/list"): self.vehicle_list,
                ("GET", "/vehicle/status/cache"): self.status_cache,
                ("GET", "/vehicle/status"): self.status_live,
                ("GET", "/vehicle/charging/mgmtData"): self.mgmt_data,
                ("POST", "/vehicle/control"): self.control,
                ("POST", "/vehicle/charging/control"): self.charging_control,
                ("POST", "/vehicle/charging/setting"): self.charging_setting,
                ("GET", "/user/timezone"): lambda q, b: self.ok({"timezone": "GMT+01:00"}),
            }
            fn = routes.get((method, sub))
            if fn is None:
                log(f"{method} {sub} -> 404")
                return self.send_json(200, {"code": 404, "message": f"Unknown endpoint {sub}"})
            fn(query, body)

        def decrypt_body(self, raw: str, path: str, token: str) -> str:
            raw = raw.strip()
            ts = self.headers.get("APP-SEND-DATE", "")
            ct = self.headers.get("ORIGINAL-CONTENT-TYPE", "application/json")
            if not raw or not HAVE_AES or not ts:
                return raw
            key = md5(md5(path + TENANT + token + "app") + ts + "1" + ct)
            try:
                return aes_dec(raw, key, md5(ts))
            except Exception as e:
                log(f"  !! could not decrypt request body: {e}")
                return raw

        def check_signature(self, path: str, token: str, enc_body: str) -> None:
            ts = self.headers.get("APP-SEND-DATE", "")
            ct = self.headers.get("ORIGINAL-CONTENT-TYPE", "application/json")
            got = self.headers.get("APP-VERIFICATION-STRING", "")
            key = md5(md5(path + TENANT + token + "app") + ts + "1" + ct)
            msg = path + TENANT + token + "app" + ts + "1" + ct + enc_body.strip()
            want = hmac.new(md5(key + ts).encode(), msg.encode(), hashlib.sha256).hexdigest()
            if got != want:
                log(f"  !! APP-VERIFICATION-STRING mismatch on {path}")

        def ok(self, data, extra_headers=None):
            self.send_json(200, {"code": 0, "message": "success", "data": data}, extra_headers=extra_headers)

        def send_json(self, status: int, payload: dict, extra_headers=None, plain=False):
            text = json.dumps(payload)
            headers = dict(extra_headers or {})
            ct = "application/json"
            if HAVE_AES and not plain:
                ts = str(int(time.time() * 1000))
                text = aes_enc(text, md5(ts + "1" + ct), md5(ts))
                headers.update({"APP-SEND-DATE": ts, "ORIGINAL-CONTENT-TYPE": ct, "APP-CONTENT-ENCRYPTED": "1"})
            data = text.encode()
            self.send_response(status)
            self.send_header("Content-Type", f"{ct};charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            for k, v in headers.items():
                self.send_header(k, v)
            self.end_headers()
            self.wfile.write(data)

        def event_gate(self, name: str) -> bool:
            """Mimic the event-id dance: first call returns only an event-id, data comes later."""
            eid = self.headers.get("event-id", "0")
            if eid == "0" or eid not in state.events:
                new = uuid.uuid4().hex[:16]
                state.events[new] = random.randint(0, 2)
                log(f"  {name}: waking car, event-id {new}")
                self.send_json(200, {"code": 0, "message": "processing"}, extra_headers={"event-id": new})
                return False
            if state.events[eid] > 0:
                state.events[eid] -= 1
                self.send_json(200, {"code": 0, "message": "processing"}, extra_headers={"event-id": eid})
                return False
            del state.events[eid]
            return True

        def mock_control(self, url) -> None:
            """Test switches, called from the PC (plain JSON, no encryption):
            /mock/reject-logins?seconds=N  - every login says "wrong password" for N seconds
            /mock/expire-tokens           - forget all tokens so the app has to log in again
            /mock/http401-logins?seconds=N - every login gets HTTP 401 (like a blocked account) for N seconds
            /mock/block-car?seconds=N[&reason=253] - commands get code 8 "Too frequent operations (71)" (or reason 253) for N seconds
            /mock/plug?state=idle|charging|off[&soc=80][&target=80] - charging cable (idle = plugged, not charging)
            /mock/old-software?on=1|0      - 1: A/C refused while plugged in but not charging (default 1)
            /mock/fail-rate?rate=0.15      - share of calls that fail on purpose
            """
            q = {k: v[0] for k, v in parse_qs(url.query).items()}
            if url.path == "/mock/reject-logins":
                secs = float(q.get("seconds", "60"))
                state.reject_logins_until = time.time() + secs
                log(f"MOCK: rejecting all logins for {secs:.0f}s")
            elif url.path == "/mock/http401-logins":
                secs = float(q.get("seconds", "300"))
                state.http401_logins_until = time.time() + secs
                state.tokens.clear()
                log(f"MOCK: HTTP 401 on all logins for {secs:.0f}s (tokens expired)")
            elif url.path == "/mock/block-car":
                secs = float(q.get("seconds", "600"))
                state.block_car_until = time.time() + secs
                state.block_car_reason = int(q.get("reason", "71"))
                log(f"MOCK: car blocks remote commands for {secs:.0f}s (reason {state.block_car_reason})")
            elif url.path == "/mock/plug":
                st = q.get("state", "idle")
                with state.car.lock:
                    state.car.plugged = st != "off"
                    state.car.charging = st == "charging"
                    if "soc" in q:
                        state.car.soc = int(float(q["soc"]) * 10)
                    if "target" in q:
                        state.car.target_code = max(1, min(7, (int(q["target"]) - 30) // 10))
                    c = state.car
                    log(f"MOCK: cable {st}, battery {c.soc / 10:.1f}%, limit {c.target_pct}%")
            elif url.path == "/mock/fail-rate":
                state.fail_rate = float(q.get("rate", "0.15"))
                log(f"MOCK: failure rate {state.fail_rate:.0%}")
            elif url.path == "/mock/old-software":
                state.car.old_software = q.get("on", "1") == "1"
                log(f"MOCK: old software (no A/C while plugged in, not charging): {state.car.old_software}")
            elif url.path == "/mock/expire-tokens":
                state.tokens.clear()
                log("MOCK: all tokens expired")
            else:
                return self.send_json(404, {"code": 404, "message": "Unknown mock switch"}, plain=True)
            self.send_json(200, {"code": 0, "message": "ok"}, plain=True)

        # ---------- endpoints ----------

        def login(self, q, body: str):
            form = {k: v[0] for k, v in parse_qs(body).items()}
            user = form.get("username", "?")
            if time.time() < state.http401_logins_until:
                log(f"LOGIN {user} -> HTTP 401 (mock switch)")
                return self.send_json(401, {"code": 401, "message": "Unauthorized"}, plain=True)
            if time.time() < state.reject_logins_until:
                log(f"LOGIN {user} -> rejected (mock switch)")
                return self.send_json(200, {"code": 1, "message": "Incorrect username or password"})
            if not HAVE_AES:
                # Can't read the encrypted form without pycryptodome: accept anyone.
                ok = True
            else:
                ok = form.get("password") == hashlib.sha1(PASSWORD.encode()).hexdigest()
            if not ok:
                log(f"LOGIN {user} -> wrong password")
                return self.send_json(200, {"code": 1, "message": "Incorrect username or password"})
            token = "mock-" + uuid.uuid4().hex
            state.tokens[token] = time.time() + 3600
            log(f"LOGIN {user} -> ok")
            self.ok({
                "access_token": token, "expires_in": 3600, "token_type": "bearer",
                "user_name": user, "account": user, "languageType": "EN",
            })

        def vehicle_list(self, q, body):
            log("GET vehicle list")
            self.ok({"vinList": [{
                "vin": VIN, "brandName": "MG", "modelName": "MG4 Electric", "modelYear": "2023",
                "series": "EH32 S", "name": "MG4", "colorName": "Holborn Blue",
                "isCurrentVehicle": True, "isActivate": True, "isSubaccount": False,
                "subAccountList": [], "vehicleModelConfiguration": model_configuration(),
            }]})

        def status_cache(self, q, body):
            with state.car.lock:
                s = state.car.basic_status()
            log(f"GET status (cached): outside {s['exteriorTemperature']}°, inside {s['interiorTemperature']}°")
            self.ok({"basicVehicleStatus": s, "statusTime": int(time.time()), "onlineStatus": 1})

        def status_live(self, q, body):
            if not self.event_gate("status"):
                return
            with state.car.lock:
                s = state.car.basic_status()
            log(f"GET status (live): outside {s['exteriorTemperature']}°, inside {s['interiorTemperature']}°")
            self.ok({"basicVehicleStatus": s, "statusTime": int(time.time())})

        def mgmt_data(self, q, body):
            if not self.event_gate("battery"):
                return
            with state.car.lock:
                c = state.car
                c.tick()
                soc, charging, plugged, target = c.soc, c.charging, c.plugged, c.target_code
            log(f"GET battery: {soc / 10:.1f}%, plugged={plugged} charging={charging} limit={30 + target * 10}%")
            self.ok({
                "chrgMgmtData": {"bmsPackSOCDsp": soc, "bmsChrgSts": 1 if charging else 0,
                                 "bmsOnBdChrgTrgtSOCDspCmd": target},
                "rvsChargeStatus": {"chargingGunState": 1 if plugged else 0},
            })

        def charging_control(self, q, body: str):
            if not self.event_gate("charging control"):
                return
            try:
                req = json.loads(body)
            except Exception:
                req = {}
            start = req.get("chrgCtrlReq") == 1
            with state.car.lock:
                c = state.car
                c.tick()
                if start and c.plugged and c.soc < c.target_pct * 10:
                    c.charging = True
                elif not start:
                    c.charging = False
                log(f"POST charging {'start' if start else 'stop'} -> charging={c.charging}")
            self.ok({"bmsChrgSts": 1 if state.car.charging else 0})

        def charging_setting(self, q, body: str):
            if not self.event_gate("charging setting"):
                return
            try:
                req = json.loads(body)
            except Exception:
                req = {}
            code = req.get("onBdChrgTrgtSOCReq", 0)
            with state.car.lock:
                if 1 <= code <= 7:
                    state.car.target_code = code
                log(f"POST charging setting -> limit {state.car.target_pct}%")
            self.ok({"bmsOnBdChrgTrgtSOCDspCmd": state.car.target_code})

        def control(self, q, body: str):
            if not self.event_gate("control"):
                return
            if time.time() < state.block_car_until:
                log(f"POST control -> code 8 reason {state.block_car_reason} (mock switch)")
                if state.block_car_reason == 253:
                    # What a real MG4 answered right after being woken (field test 26-11-2026).
                    return self.send_json(200, {"code": 8, "message": "Please confirm that the vehicle status or the overall "
                                                "vehicle network status is normal.(253)"})
                return self.send_json(200, {"code": 8, "message": "Too frequent operations. Please use the physical key "
                                            "to restart the vehicle, lock the car and turn off the engine, and then try again.(71)"})
            try:
                req = json.loads(body)
            except Exception:
                log("POST control (body not readable without pycryptodome)")
                return self.ok({"rvcReqSts": "AQ==", "failureType": 0})
            rtype = str(req.get("rvcReqType"))
            params = {}
            for p in req.get("rvcParams") or []:
                v = base64.b64decode(p.get("paramValue", ""))
                params[int(p["paramId"])] = v[0] if v else 0
            c = state.car
            if rtype == "6" and params.get(19, 0) > 0 and c.old_software and c.plugged and not c.charging:
                # What a real MG4 answered (field test 26-11-2026): code 8 with reason 40.
                log("POST control: climate -> refused, plugged in but not charging (old software)")
                return self.send_json(200, {"code": 8, "message": "Request timed out. Please try again later.(40)"})
            with state.car.lock:
                state.car.apply(rtype, params)
                s = state.car.basic_status()
            shown = {k: v for k, v in params.items() if k != 255}
            log(f"POST control: {RVC_NAMES.get(rtype, rtype)} {shown}")
            self.ok({
                "rvcReqType": base64.b64encode(bytes([int(rtype) % 256])).decode(),
                "rvcReqSts": "AQ==", "failureType": 0, "basicVehicleStatus": s,
            })

    return Handler


def log(msg: str) -> None:
    print(time.strftime("%H:%M:%S"), msg, flush=True)



# vehicleModelConfiguration of a real MG4 EH32 S (saic-python-mqtt-gateway issue #343, no VIN).
# MG4s send no SWH (heated steering wheel); --swh adds it, e.g. --swh 0 = "car has none".
MG4_CONFIG = [
    ("J17", "Tire", "1"), ("Q00", "Airbag", "1"), ("Q01", "FAirbag", "1"), ("Q09", "AirbagS", "1"),
    ("S35", "Sunroof", "0"), ("S61", "Remote control", "1"), ("T11", "AC", "1"), ("EPS", "EPS", "1"),
    ("BONNUT", "BONNUT", "1"), ("DOOR", "DOOR", "1111"), ("BOOT", "BOOT", "1"), ("ENGINE", "ENGINE", "1"),
    ("EV", "EV", "0"), ("HeatedSeat", "HeatedSeat", "1"), ("KEYPOS", "KEYPOS", "0"), ("ENERGY", "ENERGY", "1"),
    ("BATTERY", "BATTERY", "1"), ("INTEMP", "INTEMP", "1"), ("EXTEMP", "EXTEMP", "1"), ("WINDOW", "WINDOW", "0000"),
    ("LRD", "LRD", "0"), ("BTKEY", "BTKEY", "1"), ("BType", "Battery", "1"), ("SBH", "SBH", "1"),
    ("BHI", "BHI", "1"), ("ROV", "ROV", "25857"),
]
SWH = None


def model_configuration():
    items = [{"itemCode": c, "itemName": n, "itemValue": v} for c, n, v in MG4_CONFIG]
    if SWH is not None:
        items.append({"itemCode": "SWH", "itemName": "SWH", "itemValue": SWH})
    return items


def main() -> None:
    ap = argparse.ArgumentParser(description="Fake MG iSMART API server")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--fail-rate", type=float, default=0.15, help="share of calls that fail on purpose (0-1)")
    ap.add_argument("--outside", type=int, default=4, help="outside temperature in °C")
    ap.add_argument("--swh", default=None,
                    help="report SWH (heated steering wheel) in the car config: 0 = none, 1 = fitted; default: not sent, like an MG4")
    ap.add_argument("--slow", type=float, default=0.5, help="extra delay per call in seconds")
    args = ap.parse_args()

    global SWH
    SWH = args.swh
    server = ThreadingHTTPServer(("0.0.0.0", args.port), make_handler(State(args)))
    print(f"Fake MG server on http://localhost:{args.port}{PREFIX}/")
    print(f"From the Android emulator: http://10.0.2.2:{args.port}  (region 'Test server')")
    print(f"Password: '{PASSWORD}'   failure rate: {args.fail_rate:.0%}   outside: {args.outside}°   SWH: {SWH or 'not sent'}")
    print("Encryption:", "on (pycryptodome found)" if HAVE_AES else "off - plain JSON (pip install pycryptodome for full realism)")
    print("Ctrl+C to stop.\n")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
