"use strict";

/*
 * Modo demonstração: o motor de regras roda aqui no navegador, sem servidor e sem senha.
 * Liga sozinho no GitHub Pages ou com ?demo na URL. Com o app real no ar (docker compose),
 * este modo fica desligado e a API continua exigindo as senhas de cada perfil.
 */

/** Só subdomínios reais de hospedagem estática (com o ponto): "evilgithub.io" não liga o modo demo. */
function ehHospedagemEstatica(host) {
  return host.endsWith(".github.io") || host.endsWith(".vercel.app");
}

const MODO_DEMO = ehHospedagemEstatica(location.hostname) || new URLSearchParams(location.search).has("demo");

/*
 * Na Vercel existe uma API de demonstração em Python (api/index.py). Se ela responder, o motor roda
 * lá; se não (GitHub Pages, ou função indisponível), cai no motor deste arquivo, no navegador.
 */
let apiPython = null;

function apiPythonDisponivel() {
  if (apiPython === null) {
    apiPython = location.hostname.endsWith(".vercel.app")
      ? fetch("/api/index?p=/health", { cache: "no-store" })
        .then((r) => r.ok ? r.json() : null)
        .then((j) => Boolean(j && j.engine === "python-demo"))
        .catch(() => false)
      : Promise.resolve(false);
  }
  return apiPython;
}

async function buscar(url, opcoes) {
  if (!MODO_DEMO) {
    return fetch(url, opcoes);
  }
  if (await apiPythonDisponivel()) {
    const alvo = new URL(url, location.href);
    return fetch("/api/index?p=" + encodeURIComponent(alvo.pathname + alvo.search), {
      method: (opcoes && opcoes.method) || "GET",
      headers: { "Content-Type": "application/json" },
      body: opcoes && opcoes.body ? opcoes.body : undefined,
      cache: "no-store",
    });
  }
  return demoBuscar(url, opcoes);
}

const DEMO = {
  limiares: { revisar: 40, bloquear: 80 },
  clientes: new Map(),
  casos: [],
  proximoCaso: 1,
  versao: 1,
  regras: new Map([
    ["HIGH_AMOUNT_VS_AVERAGE", { weight: 40, enabled: true, params: { multiplier: 5 }, version: 1 }],
    ["NEW_DEVICE_OR_LOCATION", { weight: 25, enabled: true, params: {}, version: 1 }],
    ["NEW_RECIPIENT_HIGH_AMOUNT", { weight: 30, enabled: true, params: { minAmount: 2000 }, version: 1 }],
    ["UNUSUAL_HOUR", { weight: 15, enabled: true, params: { startHour: 0, endHour: 6, zone: "America/Sao_Paulo" }, version: 1 }],
    ["VELOCITY", { weight: 40, enabled: true, params: { maxTransactions: 5, windowSeconds: 300 }, version: 1 }],
  ]),
};

function demoResposta(status, corpo) {
  return new Response(corpo === undefined ? null : JSON.stringify(corpo), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function demoHora(zona) {
  const partes = new Intl.DateTimeFormat("en-GB", { hour: "numeric", hour12: false, timeZone: zona }).format(new Date());
  return Number(partes) % 24;
}

function demoPesoAtivo(codigo) {
  const regra = DEMO.regras.get(codigo);
  return regra && regra.enabled ? regra : null;
}

function demoAvaliar(tx) {
  const agora = Date.now();
  const c = DEMO.clientes.get(tx.customerId)
    || { tempos: [], media: null, dispositivos: new Set(), locais: new Set(), destinos: new Set() };
  const acertos = [];
  const disparar = (codigo, motivo) => {
    const regra = demoPesoAtivo(codigo);
    if (regra) {
      acertos.push({ code: codigo, weight: regra.weight, reason: motivo });
    }
  };

  const velocidade = DEMO.regras.get("VELOCITY");
  const janela = (velocidade ? velocidade.params.windowSeconds : 300) * 1000;
  c.tempos = c.tempos.filter((t) => agora - t < janela);
  c.tempos.push(agora);

  const alto = DEMO.regras.get("HIGH_AMOUNT_VS_AVERAGE");
  if (c.media && alto && tx.amount > c.media * alto.params.multiplier) {
    disparar("HIGH_AMOUNT_VS_AVERAGE", "valor acima de " + alto.params.multiplier + "x a média do cliente");
  }
  if (velocidade && c.tempos.length > velocidade.params.maxTransactions) {
    disparar("VELOCITY", c.tempos.length + " transações em " + velocidade.params.windowSeconds + "s (limite " + velocidade.params.maxTransactions + ")");
  }
  const horario = DEMO.regras.get("UNUSUAL_HOUR");
  if (horario) {
    const hora = demoHora(horario.params.zone);
    if (hora >= horario.params.startHour && hora < horario.params.endHour) {
      disparar("UNUSUAL_HOUR", "transação às " + hora + "h (madrugada)");
    }
  }
  const novoDisp = tx.deviceId && c.dispositivos.size > 0 && !c.dispositivos.has(tx.deviceId);
  const novoLocal = tx.location && c.locais.size > 0 && !c.locais.has(tx.location);
  if (novoDisp || novoLocal) {
    disparar("NEW_DEVICE_OR_LOCATION", novoDisp && novoLocal ? "dispositivo e localização novos" : novoDisp ? "dispositivo novo" : "localização nova");
  }
  const destino = DEMO.regras.get("NEW_RECIPIENT_HIGH_AMOUNT");
  if (destino && tx.recipientId && c.destinos.size > 0 && !c.destinos.has(tx.recipientId) && tx.amount >= destino.params.minAmount) {
    disparar("NEW_RECIPIENT_HIGH_AMOUNT", "destinatário novo com valor >= " + destino.params.minAmount);
  }

  const score = Math.min(100, acertos.reduce((soma, a) => soma + a.weight, 0));
  const outcome = score >= DEMO.limiares.bloquear ? "BLOQUEAR" : score >= DEMO.limiares.revisar ? "REVISAR" : "APROVAR";

  if (tx.deviceId) c.dispositivos.add(tx.deviceId);
  if (tx.location) c.locais.add(tx.location);
  if (tx.recipientId) c.destinos.add(tx.recipientId);
  if (outcome === "APROVAR") {
    c.media = c.media === null ? tx.amount : c.media * 0.9 + tx.amount * 0.1;
  }
  DEMO.clientes.set(tx.customerId, c);

  if (outcome === "REVISAR") {
    DEMO.casos.push({
      caseId: DEMO.proximoCaso++, status: "ABERTO", transactionId: tx.transactionId, score,
      reasons: acertos, createdAt: new Date().toISOString(), analyst: null, note: null, resolvedAt: null,
    });
  }
  return { transactionId: tx.transactionId, outcome, score, reasons: acertos, rulesetVersion: DEMO.versao };
}

function demoCorpo(opcoes) {
  try {
    return JSON.parse(opcoes && opcoes.body ? opcoes.body : "null");
  } catch {
    return undefined;
  }
}

async function demoBuscar(url, opcoes) {
  const metodo = (opcoes && opcoes.method) || "GET";
  const alvo = new URL(url, location.href);
  const caminho = alvo.pathname;

  if (metodo === "GET" && caminho === "/csrf") {
    return demoResposta(200, { headerName: "X-XSRF-TOKEN", token: "demo" });
  }

  if (metodo === "POST" && caminho === "/fraud/evaluate") {
    const tx = demoCorpo(opcoes);
    if (!tx || !tx.transactionId || !tx.customerId || !(tx.amount > 0)) {
      return demoResposta(400);
    }
    return demoResposta(200, demoAvaliar(tx));
  }

  if (metodo === "GET" && caminho === "/cases") {
    const status = alvo.searchParams.get("status") || "ABERTO";
    return demoResposta(200, DEMO.casos.filter((caso) => caso.status === status));
  }
  const caso = caminho.match(/^\/cases\/(\d+)\/(approve|block)$/);
  if (metodo === "POST" && caso) {
    const corpo = demoCorpo(opcoes);
    if (typeof corpo !== "object" || corpo === null) {
      return demoResposta(400);
    }
    const alvoCaso = DEMO.casos.find((c) => c.caseId === Number(caso[1]));
    if (!alvoCaso) {
      return demoResposta(404);
    }
    if (alvoCaso.status !== "ABERTO") {
      return demoResposta(409);
    }
    alvoCaso.status = caso[2] === "approve" ? "APROVADO" : "BLOQUEADO";
    alvoCaso.analyst = "analista (demo)";
    alvoCaso.note = corpo.note || null;
    alvoCaso.resolvedAt = new Date().toISOString();
    return demoResposta(200, alvoCaso);
  }

  if (metodo === "GET" && caminho === "/admin/rules") {
    return demoResposta(200, [...DEMO.regras.entries()].sort().map(([code, r]) => ({ code, ...r })));
  }
  const regra = caminho.match(/^\/admin\/rules\/([A-Z_]+)$/);
  if (metodo === "PUT" && regra) {
    const atual = DEMO.regras.get(regra[1]);
    const corpo = demoCorpo(opcoes);
    if (!atual) {
      return demoResposta(404);
    }
    if (!corpo || (corpo.weight !== undefined && (!Number.isInteger(corpo.weight) || corpo.weight < 0 || corpo.weight > 100))) {
      return demoResposta(400);
    }
    if (corpo.weight !== undefined) atual.weight = corpo.weight;
    if (corpo.enabled !== undefined) atual.enabled = Boolean(corpo.enabled);
    DEMO.versao += 1;
    atual.version = DEMO.versao;
    return demoResposta(200, { code: regra[1], ...atual });
  }

  return demoResposta(404);
}
