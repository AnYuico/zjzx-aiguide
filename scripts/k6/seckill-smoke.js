import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8500';
const activityId = __ENV.ACTIVITY_ID;
const skuId = __ENV.SKU_ID;
const runId = __ENV.RUN_ID || `${Date.now()}`;
const tokens = (__ENV.TOKENS || __ENV.TOKEN || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
const addressIds = (__ENV.ADDRESS_IDS || __ENV.ADDRESS_ID || '')
  .split(',')
  .map((value) => Number(value.trim()))
  .filter((value) => Number.isFinite(value) && value > 0);

export const options = {
  scenarios: {
    sale: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 20),
      iterations: Number(__ENV.ITERATIONS || tokens.length || 20),
      maxDuration: __ENV.MAX_DURATION || '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  if (!activityId || !skuId || tokens.length === 0 || addressIds.length === 0) {
    throw new Error('ACTIVITY_ID, SKU_ID, ADDRESS_ID(S) and TOKEN(S) are required');
  }
  if (addressIds.length !== 1 && addressIds.length !== tokens.length) {
    throw new Error('ADDRESS_IDS must contain one value or match TOKENS length');
  }
}

export default function () {
  const index = (__VU - 1) % tokens.length;
  const token = tokens[index];
  const addressId = addressIds.length === 1 ? addressIds[0] : addressIds[index];
  const requestId = `k6-${runId}-${index}`;
  const url = `${baseUrl}/api/product/seckill/auth/activity/${activityId}/sku/${skuId}/submit`;
  const response = http.post(url, JSON.stringify({
    requestId,
    userAddressId: addressId,
  }), {
    headers: {
      'Content-Type': 'application/json',
      token,
    },
  });
  let body = {};
  try {
    body = response.json();
  } catch (_) {
    body = {};
  }
  check(response, {
    'business result is expected': () =>
      [200, 237, 238, 239, 241].includes(body.code),
    'no server error': (res) => res.status < 500,
  });
  sleep(0.05);
}
