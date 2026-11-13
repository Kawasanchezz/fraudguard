import unittest

import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'api'))
from index import Estado, tratar


def tx(**kw):
    base = {"transactionId": "t1", "customerId": "c1", "amount": 100, "deviceId": "d1",
            "location": "BR-SP", "recipientId": "r1", "channel": "PIX"}
    base.update(kw)
    return base


class ApiDemoTest(unittest.TestCase):
    def setUp(self):
        self.e = Estado()

    def avaliar(self, **kw):
        return tratar("POST", "/fraud/evaluate", {}, tx(**kw), self.e)

    def test_aprova_transacao_normal(self):
        status, r = self.avaliar()
        self.assertEqual(status, 200)
        self.assertIn(r["outcome"], ("APROVAR", "REVISAR"))  # REVISAR so se rodar de madrugada

    def test_exemplo_do_readme_da_score_95_e_bloqueia(self):
        self.avaliar(transactionId="a")  # cria historico: dispositivo, local, destino e media 100
        # forca fora da madrugada para o calculo ser deterministico
        from index import avaliar
        r = avaliar(tx(transactionId="b", amount=100000, deviceId="novo", location="BR-RJ", recipientId="novo"),
                    self.e, hora=12)
        self.assertEqual(r["score"], 95)
        self.assertEqual(r["outcome"], "BLOQUEAR")
        self.assertEqual({a["weight"] for a in r["reasons"]}, {40, 25, 30})

    def test_velocidade_dispara_acima_de_cinco(self):
        from index import avaliar
        r = None
        for i in range(6):
            r = avaliar(tx(transactionId=str(i)), self.e, hora=12)
        self.assertIn("VELOCITY", [a["code"] for a in r["reasons"]])

    def test_validacao_rejeita_entradas_invalidas(self):
        for ruim in (tx(amount=0), tx(amount=-1), tx(amount="9"), tx(amount=True), tx(customerId=""),
                     tx(transactionId="x" * 65)):
            self.assertEqual(tratar("POST", "/fraud/evaluate", {}, ruim, self.e)[0], 400)
        self.assertEqual(tratar("POST", "/fraud/evaluate", {}, None, self.e)[0], 400)

    def test_caso_de_revisao_pode_ser_resolvido_uma_vez(self):
        self.e.regras["UNUSUAL_HOUR"]["weight"] = 40  # garante REVISAR no teste
        from index import avaliar
        r = avaliar(tx(transactionId="rev"), self.e, hora=3)
        self.assertEqual(r["outcome"], "REVISAR")
        status, casos = tratar("GET", "/cases", {}, None, self.e)
        self.assertEqual(len(casos), 1)
        self.assertEqual(tratar("POST", "/cases/1/approve", {}, {"note": "ok"}, self.e)[0], 200)
        self.assertEqual(tratar("POST", "/cases/1/block", {}, {}, self.e)[0], 409)
        self.assertEqual(tratar("POST", "/cases/99/block", {}, {}, self.e)[0], 404)

    def test_admin_altera_regra_e_valida_peso(self):
        self.assertEqual(tratar("PUT", "/admin/rules/VELOCITY", {}, {"weight": 500}, self.e)[0], 400)
        status, r = tratar("PUT", "/admin/rules/VELOCITY", {}, {"weight": 10, "enabled": False}, self.e)
        self.assertEqual((status, r["weight"], r["enabled"], r["version"]), (200, 10, False, 2))
        self.assertEqual(tratar("PUT", "/admin/rules/NAO_EXISTE", {}, {"weight": 1}, self.e)[0], 404)

    def test_aceita_nomes_com_acento_e_espaco(self):
        status, r = self.avaliar(customerId="Kawã Sanchez", deviceId="iPhone 13", location="São Paulo - SP",
                                 recipientId="Mercado Bom Preço")
        self.assertEqual(status, 200)
        self.assertEqual(r["transactionId"], "t1")

    def test_valida_canal_e_limites_como_no_java(self):
        for ruim in (tx(channel="BOLETO"), tx(channel=None), tx(location="x" * 65), tx(deviceId="x" * 129)):
            self.assertEqual(tratar("POST", "/fraud/evaluate", {}, ruim, self.e)[0], 400)
        self.assertEqual(tratar("POST", "/fraud/evaluate", {}, tx(deviceId="x" * 128, channel="CARTAO"), self.e)[0], 200)

    def test_limite_de_requisicoes_por_ip(self):
        from index import limite_excedido, LIMITE_REQ
        self.assertFalse(any(limite_excedido("9.9.9.9", 1000.0) for _ in range(LIMITE_REQ)))
        self.assertTrue(limite_excedido("9.9.9.9", 1001.0))
        self.assertFalse(limite_excedido("9.9.9.9", 1100.0))

    def test_rota_desconhecida_404(self):
        self.assertEqual(tratar("GET", "/qualquer", {}, None, self.e)[0], 404)


if __name__ == "__main__":
    unittest.main()
