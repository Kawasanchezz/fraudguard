"""
FraudGuard - API de demonstracao em Python (Vercel Serverless Function).

Port do motor de regras do projeto Java para a demo publica. NAO e o servico real:
nao ha banco, Redis nem autenticacao. O estado vive na memoria da instancia e some quando
a Vercel recicla a funcao (demo de melhor esforco). Nenhum dado pessoal e gravado.

Rota unica: /api/index?p=<caminho-da-api>, ex.: /api/index?p=/fraud/evaluate
"""
import json
import threading
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

try:
    from zoneinfo import ZoneInfo
except ImportError:  # pragma: no cover
    ZoneInfo = None

MAX_BODY = 8 * 1024
MAX_CLIENTES = 500
MAX_CASOS = 200
MAX_TEXTO = 64          # transactionId, customerId, location (igual ao Java)
MAX_TEXTO_LONGO = 128   # deviceId, recipientId (igual ao Java)
CANAIS = ("PIX", "CARTAO", "TRANSFERENCIA")
LIMITE_REQ = 120        # requisicoes por IP por minuto (melhor esforco, por instancia)
LIMIARES = {"revisar": 40, "bloquear": 80}

_trava = threading.Lock()
_acessos = {}


def limite_excedido(ip, agora=None):
    """Janela deslizante de 60 s por IP. Em memoria: protege contra abuso simples, nao contra DDoS."""
    agora = time.time() if agora is None else agora
    with _trava:
        recentes = [t for t in _acessos.get(ip, []) if agora - t < 60]
        recentes.append(agora)
        _acessos[ip] = recentes
        if len(_acessos) > 2000:
            _acessos.pop(next(iter(_acessos)))
        return len(recentes) > LIMITE_REQ


def _regras_padrao():
    return {
        "HIGH_AMOUNT_VS_AVERAGE": {"weight": 40, "enabled": True, "params": {"multiplier": 5}, "version": 1},
        "NEW_DEVICE_OR_LOCATION": {"weight": 25, "enabled": True, "params": {}, "version": 1},
        "NEW_RECIPIENT_HIGH_AMOUNT": {"weight": 30, "enabled": True, "params": {"minAmount": 2000}, "version": 1},
        "UNUSUAL_HOUR": {"weight": 15, "enabled": True,
                         "params": {"startHour": 0, "endHour": 6, "zone": "America/Sao_Paulo"}, "version": 1},
        "VELOCITY": {"weight": 40, "enabled": True,
                     "params": {"maxTransactions": 5, "windowSeconds": 300}, "version": 1},
    }


class Estado:
    def __init__(self):
        self.clientes = {}
        self.casos = []
        self.proximo_caso = 1
        self.versao = 1
        self.regras = _regras_padrao()


ESTADO = Estado()


def _hora(zona):
    try:
        return datetime.now(ZoneInfo(zona)).hour
    except Exception:
        return datetime.now(timezone(timedelta(hours=-3))).hour


def avaliar(tx, estado=ESTADO, agora=None, hora=None):
    agora = time.time() if agora is None else agora
    c = estado.clientes.get(tx["customerId"]) or {
        "tempos": [], "media": None, "dispositivos": set(), "locais": set(), "destinos": set()}
    acertos = []

    def disparar(codigo, motivo):
        regra = estado.regras.get(codigo)
        if regra and regra["enabled"]:
            acertos.append({"code": codigo, "weight": regra["weight"], "reason": motivo})

    velocidade = estado.regras["VELOCITY"]
    janela = velocidade["params"]["windowSeconds"]
    c["tempos"] = [t for t in c["tempos"] if agora - t < janela]
    c["tempos"].append(agora)

    alto = estado.regras["HIGH_AMOUNT_VS_AVERAGE"]
    if c["media"] and tx["amount"] > c["media"] * alto["params"]["multiplier"]:
        disparar("HIGH_AMOUNT_VS_AVERAGE", "valor acima de %sx a média do cliente" % alto["params"]["multiplier"])
    if len(c["tempos"]) > velocidade["params"]["maxTransactions"]:
        disparar("VELOCITY", "%d transações em %ss (limite %s)" % (
            len(c["tempos"]), janela, velocidade["params"]["maxTransactions"]))
    horario = estado.regras["UNUSUAL_HOUR"]
    h = _hora(horario["params"]["zone"]) if hora is None else hora
    if horario["params"]["startHour"] <= h < horario["params"]["endHour"]:
        disparar("UNUSUAL_HOUR", "transação às %dh (madrugada)" % h)

    disp, loc, dest = tx.get("deviceId"), tx.get("location"), tx.get("recipientId")
    novo_disp = bool(disp) and len(c["dispositivos"]) > 0 and disp not in c["dispositivos"]
    novo_loc = bool(loc) and len(c["locais"]) > 0 and loc not in c["locais"]
    if novo_disp or novo_loc:
        disparar("NEW_DEVICE_OR_LOCATION",
                 "dispositivo e localização novos" if novo_disp and novo_loc
                 else "dispositivo novo" if novo_disp else "localização nova")
    destino = estado.regras["NEW_RECIPIENT_HIGH_AMOUNT"]
    if dest and len(c["destinos"]) > 0 and dest not in c["destinos"] and tx["amount"] >= destino["params"]["minAmount"]:
        disparar("NEW_RECIPIENT_HIGH_AMOUNT", "destinatário novo com valor >= %s" % destino["params"]["minAmount"])

    score = min(100, sum(a["weight"] for a in acertos))
    outcome = "BLOQUEAR" if score >= LIMIARES["bloquear"] else "REVISAR" if score >= LIMIARES["revisar"] else "APROVAR"

    if disp:
        c["dispositivos"].add(disp)
    if loc:
        c["locais"].add(loc)
    if dest:
        c["destinos"].add(dest)
    if outcome == "APROVAR":
        c["media"] = tx["amount"] if c["media"] is None else c["media"] * 0.9 + tx["amount"] * 0.1
    if len(estado.clientes) >= MAX_CLIENTES and tx["customerId"] not in estado.clientes:
        estado.clientes.pop(next(iter(estado.clientes)))
    estado.clientes[tx["customerId"]] = c

    if outcome == "REVISAR":
        estado.casos.append({
            "caseId": estado.proximo_caso, "status": "ABERTO", "transactionId": tx["transactionId"],
            "score": score, "reasons": acertos,
            "createdAt": datetime.now(timezone.utc).isoformat(), "analyst": None, "note": None, "resolvedAt": None})
        estado.proximo_caso += 1
        del estado.casos[:-MAX_CASOS]
    return {"transactionId": tx["transactionId"], "outcome": outcome, "score": score,
            "reasons": acertos, "rulesetVersion": estado.versao}


def _texto(valor):
    return isinstance(valor, str) and 0 < len(valor) <= MAX_TEXTO


def _tx_valida(tx):
    if not isinstance(tx, dict) or not _texto(tx.get("transactionId")) or not _texto(tx.get("customerId")):
        return False
    valor = tx.get("amount")
    if isinstance(valor, bool) or not isinstance(valor, (int, float)) or not (0 < valor < 1e12):
        return False
    if tx.get("channel") not in CANAIS:
        return False
    limites = {"location": MAX_TEXTO, "deviceId": MAX_TEXTO_LONGO, "recipientId": MAX_TEXTO_LONGO}
    return all(tx.get(k) is None or (isinstance(tx[k], str) and len(tx[k]) <= n) for k, n in limites.items())


def tratar(metodo, caminho, consulta, corpo, estado=ESTADO):
    """Devolve (status, objeto-json-ou-None). Funcao pura em relacao ao HTTP, facil de testar."""
    with _trava:
        if metodo == "GET" and caminho == "/csrf":
            return 200, {"headerName": "X-XSRF-TOKEN", "token": "demo"}
        if metodo == "GET" and caminho == "/health":
            return 200, {"status": "ok", "engine": "python-demo"}
        if metodo == "POST" and caminho == "/fraud/evaluate":
            if not _tx_valida(corpo):
                return 400, None
            return 200, avaliar(corpo, estado)
        if metodo == "GET" and caminho == "/cases":
            status = (consulta.get("status") or ["ABERTO"])[0]
            return 200, [c for c in estado.casos if c["status"] == status]
        partes = caminho.strip("/").split("/")
        if metodo == "POST" and len(partes) == 3 and partes[0] == "cases" \
                and partes[1].isdigit() and partes[2] in ("approve", "block"):
            if not isinstance(corpo, dict):
                return 400, None
            caso = next((c for c in estado.casos if c["caseId"] == int(partes[1])), None)
            if caso is None:
                return 404, None
            if caso["status"] != "ABERTO":
                return 409, None
            nota = corpo.get("note")
            caso.update(status="APROVADO" if partes[2] == "approve" else "BLOQUEADO",
                        analyst="analista (demo)", note=str(nota)[:500] if nota else None,
                        resolvedAt=datetime.now(timezone.utc).isoformat())
            return 200, caso
        if metodo == "GET" and caminho == "/admin/rules":
            return 200, [dict(code=k, **v) for k, v in sorted(estado.regras.items())]
        if metodo == "PUT" and len(partes) == 3 and partes[:2] == ["admin", "rules"]:
            regra = estado.regras.get(partes[2])
            if regra is None:
                return 404, None
            if not isinstance(corpo, dict):
                return 400, None
            peso = corpo.get("weight")
            if peso is not None and (isinstance(peso, bool) or not isinstance(peso, int) or not 0 <= peso <= 100):
                return 400, None
            if peso is not None:
                regra["weight"] = peso
            if "enabled" in corpo:
                regra["enabled"] = bool(corpo["enabled"])
            estado.versao += 1
            regra["version"] = estado.versao
            return 200, dict(code=partes[2], **regra)
        return 404, None


class handler(BaseHTTPRequestHandler):
    def _responder(self, metodo):
        ip = (self.headers.get("x-forwarded-for") or self.client_address[0]).split(",")[0].strip()
        if limite_excedido(ip):
            return self._enviar(429, None)
        url = urlparse(self.path)
        consulta = parse_qs(url.query)
        alvo = urlparse((consulta.get("p") or ["/health"])[0])
        corpo = None
        if metodo in ("POST", "PUT"):
            try:
                tamanho = int(self.headers.get("Content-Length") or 0)
            except ValueError:
                tamanho = -1
            if tamanho < 0 or tamanho > MAX_BODY:
                return self._enviar(413, None)
            try:
                corpo = json.loads(self.rfile.read(tamanho) or "null")
            except ValueError:
                return self._enviar(400, None)
        status, dados = tratar(metodo, alvo.path, parse_qs(alvo.query), corpo)
        self._enviar(status, dados)

    def _enviar(self, status, dados):
        carga = b"" if dados is None else json.dumps(dados, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(carga)))
        self.end_headers()
        self.wfile.write(carga)

    def do_GET(self):
        self._responder("GET")

    def do_POST(self):
        self._responder("POST")

    def do_PUT(self):
        self._responder("PUT")
