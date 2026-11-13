"use strict";

const $ = (id) => document.getElementById(id);
const FAIXAS = { APROVAR: "aprovar", REVISAR: "revisar", BLOQUEAR: "bloquear" };
const MAX_HISTORICO = 30;
const MAX_AVATARES = 4;
const NOMES_CANAL = { PIX: "Pix", CARTAO: "Cartão", TRANSFERENCIA: "Transferência" };

const estado = {
  historico: [],
  totais: { APROVAR: 0, REVISAR: 0, BLOQUEAR: 0 },
  somaScore: 0,
  selecionado: null,
  filtro: "TODAS",
  canal: "CARTAO",
  senha: "",
  latencias: [],
};

function idAleatorio(prefixo) {
  const bytes = crypto.getRandomValues(new Uint8Array(4));
  return prefixo + Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

const NOMES = ["Maria Silva", "João Souza", "Ana Costa", "Pedro Lima", "Carla Dias", "Lucas Rocha",
  "Beatriz Alves", "Rafael Nunes", "Juliana Melo", "Carlos Ramos", "Fernanda Cruz", "Bruno Teixeira"];
const DISPOSITIVOS = ["iPhone 13", "Galaxy S23", "Moto G54", "Redmi Note 12", "Pixel 8"];
const DESTINATARIOS = ["Padaria Central", "Mercado Bom Preço", "Farmácia Saúde", "Posto Ipiranga", "Loja do Zé"];
const LOCAIS = ["São Paulo - SP", "Rio de Janeiro - RJ", "Belo Horizonte - MG", "Curitiba - PR", "Recife - PE"];

function sorteio(lista) {
  return lista[crypto.getRandomValues(new Uint8Array(1))[0] % lista.length];
}

function basic(usuario, segredo) {
  const bytes = new TextEncoder().encode(usuario + ":" + segredo);
  return "Basic " + btoa(String.fromCharCode(...bytes));
}

function mensagem(texto, tipo) {
  const el = $("acesso-msg");
  el.textContent = texto;
  el.className = "msg" + (tipo ? " " + tipo : "");
}

function dormir(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function moeda(valor) {
  return Number(valor).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function corDoCliente(texto) {
  let h = 7;
  for (const c of texto) {
    h = (h * 131 + c.charCodeAt(0)) % 360;
  }
  return "hsl(" + h + " 70% 72%)";
}

function iniciais(cliente) {
  const palavras = cliente.trim().split(/[s-]+/).filter(Boolean);
  if (palavras.length >= 2) {
    return (palavras[0][0] + palavras[1][0]).toUpperCase();
  }
  return (palavras[0] || "?").slice(0, 2).toUpperCase();
}

function criarAvatar(cliente) {
  const avatar = document.createElement("span");
  avatar.className = "avatar";
  avatar.style.background = corDoCliente(cliente);
  avatar.textContent = iniciais(cliente);
  return avatar;
}

async function avaliar(transacao) {
  const inicio = performance.now();
  const resposta = await buscar("/fraud/evaluate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: basic("service", estado.senha),
    },
    body: JSON.stringify(transacao),
    credentials: "omit",
    cache: "no-store",
  });
  if (resposta.status === 401) {
    throw new Error("Senha inválida. Confira o valor de FRAUD_SERVICE_PASSWORD.");
  }
  if (resposta.status === 400) {
    throw new Error("Dados inválidos: confira o valor e os campos preenchidos.");
  }
  if (!resposta.ok) {
    throw new Error("O serviço respondeu com erro " + resposta.status + ".");
  }
  const resultado = await resposta.json();
  estado.latencias.push(performance.now() - inicio);
  if (estado.latencias.length > 500) {
    estado.latencias.shift();
  }
  registrar(transacao, resultado);
  return resultado;
}

function registrar(transacao, resultado) {
  if (Object.prototype.hasOwnProperty.call(estado.totais, resultado.outcome)) {
    estado.totais[resultado.outcome] += 1;
  }
  estado.somaScore += Number(resultado.score) || 0;
  estado.historico.unshift({
    id: String(transacao.transactionId),
    hora: new Date().toLocaleTimeString("pt-BR"),
    transacao,
    resultado,
  });
  estado.historico.length = Math.min(estado.historico.length, MAX_HISTORICO);
}

function lerFormulario() {
  const valor = $("valor").value.trim().replace(",", ".");
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(valor) || Number(valor) <= 0) {
    throw new Error("Informe um valor positivo, por exemplo 150.00.");
  }
  const cliente = $("cliente").value.trim();
  if (!cliente) {
    throw new Error("Informe o cliente.");
  }
  const opcional = (id) => $(id).value.trim() || undefined;
  return {
    transactionId: idAleatorio("sim-"),
    customerId: cliente,
    amount: Number(valor),
    channel: estado.canal,
    location: opcional("local"),
    deviceId: opcional("dispositivo"),
    recipientId: opcional("destinatario"),
  };
}

function renderResumo() {
  const total = estado.totais.APROVAR + estado.totais.REVISAR + estado.totais.BLOQUEAR;
  const mapa = [["APROVAR", "ap"], ["REVISAR", "re"], ["BLOQUEAR", "bl"]];
  for (const [saida, sufixo] of mapa) {
    $("k-" + sufixo).textContent = String(estado.totais[saida]);
    const parte = total ? (100 * estado.totais[saida]) / total : 0;
    $("t-" + sufixo).style.width = parte + "%";
    $("p-" + sufixo).textContent = Math.round(parte) + "%";
    $("c-" + sufixo).textContent = String(estado.totais[saida]);
    const clientes = [];
    for (const h of estado.historico) {
      if (h.resultado.outcome === saida && !clientes.includes(h.transacao.customerId)) {
        clientes.push(h.transacao.customerId);
      }
      if (clientes.length === MAX_AVATARES) {
        break;
      }
    }
    $("av-" + sufixo).replaceChildren(...clientes.map(criarAvatar));
  }
  $("k-media").textContent = String(total ? Math.round(estado.somaScore / total) : 0);
  $("c-todas").textContent = String(estado.historico.length);

  const ultima = $("k-ultima");
  const topo = estado.historico[0];
  if (topo) {
    ultima.textContent = topo.resultado.outcome + " · " + topo.resultado.score;
    ultima.className = "pilula-saida " + (FAIXAS[topo.resultado.outcome] || "neutra");
  }
}

function renderLista() {
  const visiveis = estado.historico.filter(
    (h) => estado.filtro === "TODAS" || h.resultado.outcome === estado.filtro);
  $("hist-vazio").hidden = visiveis.length > 0;
  $("hist-vazio").textContent = estado.historico.length
    ? "Nenhuma transação nesse filtro."
    : "Nada avaliado ainda. Envie uma transação ou use um cenário pronto.";

  $("historico").replaceChildren(...visiveis.map((h) => {
    const li = document.createElement("li");
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "item" + (h.id === estado.selecionado ? " selecionado" : "");
    botao.setAttribute("aria-pressed", h.id === estado.selecionado ? "true" : "false");
    botao.addEventListener("click", () => selecionar(h.id));

    const quem = document.createElement("div");
    quem.className = "quem";
    const nome = document.createElement("strong");
    nome.textContent = h.transacao.customerId;
    const detalhe = document.createElement("span");
    detalhe.textContent = h.hora + " · score " + h.resultado.score;
    quem.append(nome, detalhe);

    const valor = document.createElement("div");
    valor.className = "valor";
    valor.textContent = moeda(h.transacao.amount);
    const situacao = document.createElement("span");
    situacao.className = "estado " + (FAIXAS[h.resultado.outcome] || "");
    situacao.textContent = h.resultado.outcome;

    botao.append(criarAvatar(h.transacao.customerId), quem, situacao, valor);
    li.append(botao);
    return li;
  }));
}

function renderDetalhe() {
  const h = estado.historico.find((x) => x.id === estado.selecionado);
  $("vazio").hidden = Boolean(h);
  $("res").hidden = !h;
  if (!h) {
    return;
  }
  const resultado = h.resultado;
  const faixa = FAIXAS[resultado.outcome] || "aprovar";

  $("d-id").textContent = h.id;
  $("d-cliente").textContent = h.transacao.customerId;
  $("d-avatar").replaceChildren(criarAvatar(h.transacao.customerId));
  $("d-canal").textContent = NOMES_CANAL[h.transacao.channel] || String(h.transacao.channel);
  $("d-valor").textContent = moeda(h.transacao.amount);
  $("d-dispositivo").textContent = h.transacao.deviceId || "não informado";
  $("d-local").textContent = h.transacao.location || "não informada";
  $("d-destinatario").textContent = h.transacao.recipientId || "não informado";
  $("saida").textContent = resultado.outcome;
  $("saida").className = "pilula-saida " + faixa;

  const barra = $("barra");
  barra.style.width = Math.max(0, Math.min(100, Number(resultado.score) || 0)) + "%";
  barra.className = "barra " + faixa;
  $("score").textContent = String(resultado.score);

  const motivos = Array.isArray(resultado.reasons) ? resultado.reasons : [];
  $("versao").textContent = motivos.length + " (v" + resultado.rulesetVersion + ")";

  const lista = $("motivos");
  lista.replaceChildren();
  if (motivos.length === 0) {
    const vazio = document.createElement("li");
    vazio.className = "nenhum";
    vazio.textContent = "Nenhuma regra disparou.";
    lista.append(vazio);
  }
  for (const m of motivos) {
    const item = document.createElement("li");
    const peso = document.createElement("span");
    peso.className = "peso";
    peso.textContent = "+" + String(m.weight);
    const codigo = document.createElement("span");
    codigo.className = "codigo";
    codigo.textContent = String(m.code);
    const motivo = document.createElement("div");
    motivo.className = "texto";
    motivo.textContent = String(m.reason);
    item.append(peso, codigo, motivo);
    lista.append(item);
  }
  $("json").textContent = JSON.stringify(resultado, null, 2);
}

function selecionar(id) {
  estado.selecionado = id;
  renderLista();
  renderDetalhe();
}

function atualizarTudo() {
  renderResumo();
  renderLista();
  renderDetalhe();
  document.dispatchEvent(new Event("fg:atualizado"));
}

async function executar(tarefa) {
  const botoes = document.querySelectorAll("button");
  botoes.forEach((b) => (b.disabled = true));
  mensagem("Processando...", "");
  try {
    if (!MODO_DEMO && !estado.senha) {
      throw new Error("Digite a senha do usuário service primeiro.");
    }
    const resultado = await tarefa();
    estado.selecionado = String(resultado.transactionId);
    atualizarTudo();
    mensagem("Decisão recebida.", "sucesso");
  } catch (erro) {
    atualizarTudo();
    mensagem(erro.message, "erro");
  } finally {
    botoes.forEach((b) => (b.disabled = false));
  }
}

const CENARIOS = {
  normal: () => avaliar({
    transactionId: idAleatorio("sim-"),
    customerId: sorteio(NOMES),
    amount: 120,
    channel: "PIX",
    location: LOCAIS[0],
    deviceId: DISPOSITIVOS[0],
    recipientId: DESTINATARIOS[0],
  }),

  rajada: async () => {
    const cliente = sorteio(NOMES);
    let ultimo;
    for (let i = 0; i < 7; i++) {
      ultimo = await avaliar({
        transactionId: idAleatorio("sim-"),
        customerId: cliente,
        amount: 10,
        channel: "PIX",
        location: LOCAIS[0],
        deviceId: DISPOSITIVOS[0],
        recipientId: DESTINATARIOS[0],
      });
    }
    return ultimo;
  },

  suspeita: async () => {
    const cliente = sorteio(NOMES);
    await avaliar({
      transactionId: idAleatorio("sim-"),
      customerId: cliente,
      amount: 100,
      channel: "PIX",
      location: LOCAIS[0],
      deviceId: DISPOSITIVOS[0],
      recipientId: DESTINATARIOS[0],
    });
    await dormir(1500);
    return avaliar({
      transactionId: idAleatorio("sim-"),
      customerId: cliente,
      amount: 100000,
      channel: "PIX",
      location: LOCAIS[1],
      deviceId: DISPOSITIVOS[1],
      recipientId: DESTINATARIOS[1],
    });
  },
};

$("senha").addEventListener("input", (e) => {
  estado.senha = e.target.value;
});

$("form").addEventListener("submit", (e) => {
  e.preventDefault();
  executar(() => avaliar(lerFormulario()));
});

document.querySelectorAll("[data-cenario]").forEach((botao) => {
  botao.addEventListener("click", () => {
    const cenario = CENARIOS[botao.dataset.cenario];
    if (cenario) {
      executar(cenario);
    }
  });
});

document.querySelectorAll("[data-canal]").forEach((botao) => {
  botao.addEventListener("click", () => {
    estado.canal = botao.dataset.canal;
    document.querySelectorAll("[data-canal]").forEach((b) => {
      const ativo = b === botao;
      b.classList.toggle("ativo", ativo);
      b.setAttribute("aria-pressed", ativo ? "true" : "false");
    });
  });
});

document.querySelectorAll("[data-filtro]").forEach((botao) => {
  botao.addEventListener("click", () => {
    estado.filtro = botao.dataset.filtro;
    document.querySelectorAll("[data-filtro]").forEach((b) => {
      const ativo = b === botao;
      b.classList.toggle("ativo", ativo);
      b.setAttribute("aria-pressed", ativo ? "true" : "false");
    });
    renderLista();
  });
});

$("reavaliar").addEventListener("click", () => {
  const atual = estado.historico.find((x) => x.id === estado.selecionado);
  if (atual) {
    executar(() => avaliar({ ...atual.transacao, transactionId: idAleatorio("sim-") }));
  }
});

function irParaFormulario() {
  $("cliente").scrollIntoView({ behavior: "smooth", block: "center" });
  $("cliente").focus();
}

$("nova").addEventListener("click", irParaFormulario);
$("novo").addEventListener("click", irParaFormulario);

$("limpar").addEventListener("click", () => {
  if ($("vista-simulador").hidden) {
    return;
  }
  estado.historico.length = 0;
  estado.totais.APROVAR = 0;
  estado.totais.REVISAR = 0;
  estado.totais.BLOQUEAR = 0;
  estado.somaScore = 0;
  estado.latencias.length = 0;
  estado.selecionado = null;
  $("k-ultima").textContent = "sem decisões";
  $("k-ultima").className = "pilula-saida neutra";
  atualizarTudo();
  mensagem("Sessão limpa.", "");
});

$("cliente").value = sorteio(NOMES);
$("local").value = LOCAIS[0];
$("dispositivo").value = DISPOSITIVOS[0];
$("destinatario").value = DESTINATARIOS[0];

if (MODO_DEMO) {
  $("banner-demo").hidden = false;
  $("senha").placeholder = "Qualquer senha (ou nenhuma)";
  mensagem("Modo demonstração: pode usar qualquer senha, ou nenhuma.", "");
}

atualizarTudo();
