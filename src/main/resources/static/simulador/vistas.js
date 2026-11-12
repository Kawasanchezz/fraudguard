"use strict";

const VISTAS = ["simulador", "casos", "regras", "metricas"];
const TITULOS = { simulador: "Transações", casos: "Casos", regras: "Regras", metricas: "Métricas" };
const credenciais = { analista: "", admin: "" };
const casosEstado = { status: "ABERTO" };
const META_MS = 100;
const ESCALA_MS = 300;
const MAX_BARRAS = 40;

function criar(tag, classe, conteudo) {
  const el = document.createElement(tag);
  if (classe) {
    el.className = classe;
  }
  if (conteudo !== undefined) {
    el.textContent = conteudo;
  }
  return el;
}

function msgVista(id, conteudo, tipo) {
  const el = $(id);
  el.textContent = conteudo;
  el.className = "msg-vista" + (tipo ? " " + tipo : "");
}

let tokenCsrf = null;

async function obterTokenCsrf() {
  if (tokenCsrf) {
    return tokenCsrf;
  }
  const resposta = await buscar("/csrf", { method: "GET", credentials: "same-origin", cache: "no-store" });
  if (!resposta.ok) {
    throw new Error("Não foi possível obter o token de segurança (CSRF).");
  }
  tokenCsrf = await resposta.json();
  return tokenCsrf;
}

async function chamar(metodo, caminho, usuario, segredo, corpo) {
  const cabecalhos = { Authorization: basic(usuario, segredo) };
  const opcoes = { method: metodo, headers: cabecalhos, credentials: "omit", cache: "no-store" };
  if (metodo !== "GET") {
    const csrf = await obterTokenCsrf();
    cabecalhos[csrf.headerName] = csrf.token;
    opcoes.credentials = "same-origin";
  }
  if (corpo !== undefined) {
    cabecalhos["Content-Type"] = "application/json";
    opcoes.body = JSON.stringify(corpo);
  }
  const resposta = await buscar(caminho, opcoes);
  if (resposta.status === 403 && metodo !== "GET") {
    tokenCsrf = null;
  }
  if (resposta.status === 401) {
    throw new Error("Senha inválida para o usuário " + usuario + ".");
  }
  if (resposta.status === 403) {
    throw new Error("Esse usuário não tem permissão para esta ação.");
  }
  if (resposta.status === 409) {
    throw new Error("Este item já foi resolvido ou alterado por outra pessoa. Atualize a lista.");
  }
  if (resposta.status === 400) {
    throw new Error("O servidor recusou os dados enviados.");
  }
  if (!resposta.ok) {
    throw new Error("O serviço respondeu com erro " + resposta.status + ".");
  }
  return resposta.json();
}

/* navegação entre as telas */

function mostrarVista(nome) {
  for (const v of VISTAS) {
    const secao = v === "simulador" ? $("vista-simulador") : $("vista-" + v);
    secao.hidden = v !== nome;
  }
  document.querySelectorAll("[data-vista]").forEach((botao) => {
    const ativo = botao.dataset.vista === nome;
    botao.classList.toggle("ativa", ativo);
    if (ativo) {
      botao.setAttribute("aria-current", "page");
    } else {
      botao.removeAttribute("aria-current");
    }
  });
  $("titulo").textContent = TITULOS[nome];
  $("acesso-msg").hidden = nome !== "simulador";
  if (nome === "metricas") {
    renderMetricas();
  }
}

document.querySelectorAll("[data-vista]").forEach((botao) => {
  botao.addEventListener("click", () => mostrarVista(botao.dataset.vista));
});

$("limpar").addEventListener("click", () => {
  if ($("vista-simulador").hidden) {
    mostrarVista("simulador");
  }
});

for (const id of ["nova", "novo"]) {
  $(id).addEventListener("click", () => {
    mostrarVista("simulador");
    $("cliente").scrollIntoView({ behavior: "smooth", block: "center" });
    $("cliente").focus();
  });
}

/* casos: fila de revisão manual */

function cartaoCaso(caso) {
  const li = criar("li", "cartao-item");

  const cabeca = criar("div", "cartao-cabeca");
  const titulo = criar("div");
  titulo.append(criar("span", "rotulo-claro", "Transação"), criar("strong", "", String(caso.transactionId)));
  const placar = criar("span", "pilula-saida " + (caso.score >= 80 ? "bloquear" : "revisar"), "score " + caso.score);
  cabeca.append(titulo, placar);
  li.append(cabeca);

  const motivos = criar("ul", "lista-simples");
  for (const m of Array.isArray(caso.reasons) ? caso.reasons : []) {
    motivos.append(criar("li", "", "+" + m.weight + " · " + m.code + " — " + m.reason));
  }
  li.append(motivos);

  if (caso.status === "ABERTO") {
    const nota = criar("input", "campo-nota");
    nota.type = "text";
    nota.maxLength = 500;
    nota.placeholder = "Observação (opcional)";
    nota.setAttribute("aria-label", "Observação do caso " + caso.caseId);
    const aprovar = criar("button", "acao ok", "Aprovar");
    const bloquear = criar("button", "acao ruim", "Bloquear");
    aprovar.type = "button";
    bloquear.type = "button";
    aprovar.addEventListener("click", () => resolverCaso(caso.caseId, "approve", nota.value));
    bloquear.addEventListener("click", () => resolverCaso(caso.caseId, "block", nota.value));
    const acoes = criar("div", "cartao-acoes");
    acoes.append(nota, aprovar, bloquear);
    li.append(acoes);
  } else {
    const resolvido = "Resolvido por " + caso.analyst + (caso.note ? " · " + caso.note : "");
    li.append(criar("p", "resolvido", resolvido));
  }
  return li;
}

async function carregarCasos() {
  if (!MODO_DEMO && !credenciais.analista) {
    msgVista("casos-msg", "Digite a senha do usuário analista primeiro.", "erro");
    return;
  }
  msgVista("casos-msg", "Carregando...", "");
  try {
    const casos = await chamar("GET", "/cases?status=" + encodeURIComponent(casosEstado.status), "analista", credenciais.analista);
    $("casos-lista").replaceChildren(...casos.map(cartaoCaso));
    msgVista("casos-msg", casos.length ? casos.length + " caso(s)." : "Nenhum caso nesta situação.", "");
  } catch (erro) {
    $("casos-lista").replaceChildren();
    msgVista("casos-msg", erro.message, "erro");
  }
}

async function resolverCaso(id, acao, nota) {
  msgVista("casos-msg", "Enviando decisão...", "");
  try {
    await chamar("POST", "/cases/" + encodeURIComponent(id) + "/" + acao, "analista", credenciais.analista, { note: nota.trim() || null });
    await carregarCasos();
    msgVista("casos-msg", "Caso " + id + (acao === "approve" ? " aprovado." : " bloqueado."), "sucesso");
  } catch (erro) {
    msgVista("casos-msg", erro.message, "erro");
    await carregarCasos();
    msgVista("casos-msg", erro.message, "erro");
  }
}

$("senha-analista").addEventListener("input", (e) => {
  credenciais.analista = e.target.value;
});
$("casos-carregar").addEventListener("click", carregarCasos);

if (MODO_DEMO) {
  $("senha-analista").placeholder = "Qualquer senha (ou nenhuma)";
  $("senha-admin").placeholder = "Qualquer senha (ou nenhuma)";
}
document.querySelectorAll("[data-casos-status]").forEach((botao) => {
  botao.addEventListener("click", () => {
    casosEstado.status = botao.dataset.casosStatus;
    document.querySelectorAll("[data-casos-status]").forEach((b) => {
      const ativo = b === botao;
      b.classList.toggle("ativo", ativo);
      b.setAttribute("aria-pressed", ativo ? "true" : "false");
    });
    carregarCasos();
  });
});

/* regras: administração sem deploy */

function cartaoRegra(regra) {
  const li = criar("li", "cartao-item regra");
  const cabeca = criar("div", "cartao-cabeca");
  const titulo = criar("div");
  const versao = criar("span", "rotulo-claro", "versão " + regra.version);
  titulo.append(criar("strong", "", regra.code), versao);
  cabeca.append(titulo);
  li.append(cabeca);

  li.append(criar("p", "params", "parâmetros: " + JSON.stringify(regra.params)));

  const peso = criar("input", "campo-peso");
  peso.type = "number";
  peso.min = "0";
  peso.max = "100";
  peso.step = "1";
  peso.value = String(regra.weight);
  peso.setAttribute("aria-label", "Peso de " + regra.code);

  const ativa = criar("input");
  ativa.type = "checkbox";
  ativa.checked = Boolean(regra.enabled);
  ativa.setAttribute("aria-label", "Regra " + regra.code + " ativa");
  const rotuloAtiva = criar("label", "campo-ativa");
  rotuloAtiva.append(ativa, document.createTextNode(" ativa"));

  const salvar = criar("button", "acao ok", "Salvar");
  salvar.type = "button";
  salvar.addEventListener("click", async () => {
    const novoPeso = Number(peso.value);
    if (!Number.isInteger(novoPeso) || novoPeso < 0 || novoPeso > 100) {
      msgVista("regras-msg", "O peso deve ser um número inteiro de 0 a 100.", "erro");
      return;
    }
    salvar.disabled = true;
    try {
      const salva = await chamar("PUT", "/admin/rules/" + encodeURIComponent(regra.code), "admin", credenciais.admin,
        { weight: novoPeso, enabled: ativa.checked });
      versao.textContent = "versão " + salva.version;
      msgVista("regras-msg", regra.code + " salva como versão " + salva.version + ".", "sucesso");
    } catch (erro) {
      msgVista("regras-msg", erro.message, "erro");
    } finally {
      salvar.disabled = false;
    }
  });

  const linha = criar("div", "cartao-acoes");
  const rotuloPeso = criar("label", "campo-peso-rotulo", "Peso ");
  rotuloPeso.append(peso);
  linha.append(rotuloPeso, rotuloAtiva, salvar);
  li.append(linha);
  return li;
}

async function carregarRegras() {
  if (!MODO_DEMO && !credenciais.admin) {
    msgVista("regras-msg", "Digite a senha do usuário admin primeiro.", "erro");
    return;
  }
  msgVista("regras-msg", "Carregando...", "");
  try {
    const regras = await chamar("GET", "/admin/rules", "admin", credenciais.admin);
    $("regras-lista").replaceChildren(...regras.map(cartaoRegra));
    msgVista("regras-msg", regras.length + " regra(s) carregada(s).", "");
  } catch (erro) {
    $("regras-lista").replaceChildren();
    msgVista("regras-msg", erro.message, "erro");
  }
}

$("senha-admin").addEventListener("input", (e) => {
  credenciais.admin = e.target.value;
});
$("regras-carregar").addEventListener("click", carregarRegras);

/* métricas medidas na sessão */

function percentil(ordenados, p) {
  const indice = Math.min(ordenados.length - 1, Math.ceil((p / 100) * ordenados.length) - 1);
  return ordenados[Math.max(0, indice)];
}

function ms(valor) {
  return Math.round(valor) + " ms";
}

function renderMetricas() {
  const lat = estado.latencias;
  $("met-demo").hidden = !MODO_DEMO;
  $("met-vazio").hidden = lat.length > 0;
  $("met-corpo").hidden = lat.length === 0;
  if (lat.length === 0) {
    return;
  }
  const ordenados = [...lat].sort((a, b) => a - b);
  $("m-n").textContent = String(lat.length);
  $("m-p50").textContent = ms(percentil(ordenados, 50));
  $("m-p95").textContent = ms(percentil(ordenados, 95));
  $("m-max").textContent = ms(ordenados[ordenados.length - 1]);
  $("m-ok").textContent = Math.round((100 * lat.filter((v) => v < META_MS).length) / lat.length) + "%";

  const grafico = $("met-grafico");
  const linha = grafico.querySelector(".linha-meta");
  linha.style.bottom = (100 * META_MS) / ESCALA_MS + "%";
  grafico.replaceChildren(linha, ...lat.slice(-MAX_BARRAS).map((v) => {
    const barra = criar("span", v < META_MS ? "barra-lat" : "barra-lat lenta");
    barra.style.height = Math.max(3, Math.min(100, (100 * v) / ESCALA_MS)) + "%";
    barra.title = ms(v);
    return barra;
  }));

  const contagem = new Map();
  for (const h of estado.historico) {
    for (const m of h.resultado.reasons || []) {
      contagem.set(m.code, (contagem.get(m.code) || 0) + 1);
    }
  }
  const ranking = [...contagem.entries()].sort((a, b) => b[1] - a[1]);
  $("met-regras").replaceChildren(...(ranking.length
    ? ranking.map(([codigo, n]) => criar("li", "", codigo + " — " + n + "×"))
    : [criar("li", "", "Nenhuma regra disparou ainda.")]));
}

document.addEventListener("fg:atualizado", () => {
  if (!$("vista-metricas").hidden) {
    renderMetricas();
  }
});
