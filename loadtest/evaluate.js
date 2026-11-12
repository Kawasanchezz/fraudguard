import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH = 'Basic ' + encoding.b64encode('service:' + __ENV.SERVICE_PASSWORD);

export const options = {
  scenarios: {
    steady: { executor: 'constant-arrival-rate', rate: 200, timeUnit: '1s', duration: '60s',
              preAllocatedVUs: 50, maxVUs: 200 },
  },
  thresholds: {
    http_req_duration: ['p(95)<100'],
    http_req_failed: ['rate<0.01'],
  },
};

const locations = ['BR-SP', 'BR-RJ', 'BR-MG'];

export default function () {
  const customer = `cust-${Math.floor(Math.random() * 5000)}`;
  const body = JSON.stringify({
    transactionId: `${__VU}-${__ITER}-${Date.now()}`,
    customerId: customer,
    amount: Math.round(Math.random() * 3000 * 100) / 100 + 1,
    channel: 'PIX',
    deviceId: `dev-${customer}`,
    location: locations[Math.floor(Math.random() * locations.length)],
    recipientId: `rcp-${Math.floor(Math.random() * 20000)}`,
  });
  const res = http.post(`${BASE}/fraud/evaluate`, body,
    { headers: { 'Content-Type': 'application/json', Authorization: AUTH } });
  check(res, { 'status 200': (r) => r.status === 200 });
}
