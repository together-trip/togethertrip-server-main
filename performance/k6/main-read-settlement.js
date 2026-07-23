import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const DEFAULT_BASE_URL = 'http://localhost:8080';

const endpointTrends = {
  trips: new Trend('endpoint_trips_duration', true),
  tripDetail: new Trend('endpoint_trip_detail_duration', true),
  participants: new Trend('endpoint_participants_duration', true),
  transactions: new Trend('endpoint_transactions_duration', true),
  transactionStatistics: new Trend('endpoint_transaction_statistics_duration', true),
  commonFundBalance: new Trend('endpoint_common_fund_balance_duration', true),
  settlementPreview: new Trend('endpoint_settlement_preview_duration', true),
  balanceSummary: new Trend('endpoint_balance_summary_duration', true),
};

const baseUrls = (__ENV.BASE_URLS || __ENV.BASE_URL || DEFAULT_BASE_URL)
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean)
  .map((value) => value.replace(/\/+$/, ''));

const targetStrategy = (__ENV.TARGET_STRATEGY || 'sticky-vu').toLowerCase();
const kakaoAccessToken = __ENV.KAKAO_ACCESS_TOKEN || 'local-test:verified:hana';
const tripStatus = __ENV.TRIP_STATUS || 'ONGOING';
const tripTitle = __ENV.TRIP_TITLE || '';
const tripSize = Number(__ENV.TRIP_SIZE || 10);
const requestSleepSeconds = Number(__ENV.SLEEP || 1);
const slowRequestMs = Number(__ENV.SLOW_REQUEST_MS || 200);

export const options = {
  scenarios: {
    read_and_settlement_preview: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.RAMP_UP || '30s', target: Number(__ENV.VUS || 10) },
        { duration: __ENV.HOLD || '1m', target: Number(__ENV.VUS || 10) },
        { duration: __ENV.RAMP_DOWN || '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    'http_req_duration{phase:scenario}': ['p(95)<500'],
    'http_req_duration{endpoint:settlement-preview}': ['p(95)<800'],
  },
};

export function setup() {
  if (baseUrls.length === 0) {
    fail('BASE_URLS is empty.');
  }

  return {
    targets: baseUrls.map((baseUrl, index) => prepareTarget(baseUrl, index)),
  };
}

export default function (data) {
  const target = selectTarget(data.targets);
  const headers = {
    Authorization: `Bearer ${target.accessToken}`,
    'Content-Type': 'application/json',
  };

  getTrips(target, headers);
  getTrip(target, headers);
  getParticipants(target, headers);
  getTransactions(target, headers);
  getTransactionStatistics(target, headers);
  getCommonFundBalance(target, headers);
  previewSettlement(target, headers);
  getBalanceSummary(target, headers);

  sleep(requestSleepSeconds);
}

function prepareTarget(baseUrl, index) {
  const targetName = `target-${index + 1}`;
  const loginResponse = http.post(
    `${baseUrl}/api/auth/oauth/kakao`,
    JSON.stringify({ accessToken: kakaoAccessToken }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { target: targetName, endpoint: 'login', phase: 'setup' },
    },
  );
  logSlowRequest(loginResponse, {
    phase: 'setup',
    endpoint: 'login',
    method: 'POST',
    target: targetName,
    baseUrl,
  });

  check(loginResponse, {
    [`${targetName} login succeeded`]: (response) =>
      response.status === 200 && response.json('data.accessToken'),
  });

  const accessToken = loginResponse.json('data.accessToken');
  if (!accessToken) {
    fail(`${targetName} failed to obtain access token from ${baseUrl}`);
  }

  const tripResponse = http.get(
    `${baseUrl}/api/trips?status=${encodeURIComponent(tripStatus)}&size=${tripSize}`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
      tags: { target: targetName, endpoint: 'trips-setup', phase: 'setup' },
    },
  );
  logSlowRequest(tripResponse, {
    phase: 'setup',
    endpoint: 'trips-setup',
    method: 'GET',
    target: targetName,
    baseUrl,
  });

  check(tripResponse, {
    [`${targetName} trips setup succeeded`]: (response) =>
      response.status === 200 && response.json('data.items.0.id'),
  });

  const tripId = selectTripId(tripResponse);
  if (!tripId) {
    const titleHint = tripTitle ? ` with title ${tripTitle}` : '';
    fail(`${targetName} failed to find a ${tripStatus} trip${titleHint} from ${baseUrl}`);
  }

  return {
    name: targetName,
    baseUrl,
    accessToken,
    tripId,
  };
}

function selectTarget(targets) {
  if (targetStrategy === 'random') {
    return targets[Math.floor(Math.random() * targets.length)];
  }

  if (targetStrategy === 'round-robin') {
    return targets[(__ITER + __VU - 1) % targets.length];
  }

  return targets[(__VU - 1) % targets.length];
}

function selectTripId(response) {
  const items = response.json('data.items') || [];
  if (!tripTitle) {
    const firstItem = items[0];
    return firstItem && firstItem.id;
  }

  const selectedItem = items.find((item) => item.title === tripTitle);
  return selectedItem && selectedItem.id;
}

function params(target, headers, endpoint) {
  return {
    headers,
    tags: {
      target: target.name,
      base_url: target.baseUrl,
      endpoint,
      phase: 'scenario',
    },
  };
}

function recordEndpoint(trend, response, context) {
  trend.add(response.timings.duration);
  logSlowRequest(response, context);
}

function logSlowRequest(response, context) {
  if (!slowRequestMs || slowRequestMs <= 0 || response.timings.duration < slowRequestMs) {
    return;
  }

  console.warn(
    JSON.stringify({
      type: 'slow_request',
      threshold_ms: slowRequestMs,
      duration_ms: Number(response.timings.duration.toFixed(2)),
      status: response.status,
      phase: context.phase,
      endpoint: context.endpoint,
      method: context.method,
      target: context.target,
      base_url: context.baseUrl,
      vu: typeof __VU === 'undefined' ? null : __VU,
      iter: typeof __ITER === 'undefined' ? null : __ITER,
    }),
  );
}

function getTrips(target, headers) {
  const response = http.get(
    `${target.baseUrl}/api/trips?status=${encodeURIComponent(tripStatus)}&size=${tripSize}`,
    params(target, headers, 'trips'),
  );
  recordEndpoint(endpointTrends.trips, response, {
    phase: 'scenario',
    endpoint: 'trips',
    method: 'GET',
    target: target.name,
    baseUrl: target.baseUrl,
  });

  check(response, {
    'trips returned 200': (r) => r.status === 200,
    'trips response is success': (r) => r.json('success') === true,
  });
}

function getTrip(target, headers) {
  const response = http.get(
    `${target.baseUrl}/api/trips/${target.tripId}`,
    params(target, headers, 'trip-detail'),
  );
  recordEndpoint(endpointTrends.tripDetail, response, {
    phase: 'scenario',
    endpoint: 'trip-detail',
    method: 'GET',
    target: target.name,
    baseUrl: target.baseUrl,
  });

  check(response, {
    'trip detail returned 200': (r) => r.status === 200,
  });
}

function getParticipants(target, headers) {
  const response = http.get(
    `${target.baseUrl}/api/trips/${target.tripId}/participants`,
    params(target, headers, 'participants'),
  );
  recordEndpoint(endpointTrends.participants, response, {
    phase: 'scenario',
    endpoint: 'participants',
    method: 'GET',
    target: target.name,
    baseUrl: target.baseUrl,
  });

  check(response, {
    'participants returned 200': (r) => r.status === 200,
  });
}

function getTransactions(target, headers) {
  const response = http.get(
    `${target.baseUrl}/api/trips/${target.tripId}/transactions?size=20`,
    params(target, headers, 'transactions'),
  );
  recordEndpoint(endpointTrends.transactions, response, {
    phase: 'scenario',
    endpoint: 'transactions',
    method: 'GET',
    target: target.name,
    baseUrl: target.baseUrl,
  });

  check(response, {
    'transactions returned 200': (r) => r.status === 200,
  });
}

function getTransactionStatistics(target, headers) {
  const response = http.get(
    `${target.baseUrl}/api/trips/${target.tripId}/transaction-statistics?groupBy=category`,
    params(target, headers, 'transaction-statistics'),
  );
  recordEndpoint(endpointTrends.transactionStatistics, response, {
    phase: 'scenario',
    endpoint: 'transaction-statistics',
    method: 'GET',
    target: target.name,
    baseUrl: target.baseUrl,
  });

  check(response, {
    'transaction statistics returned 200': (r) => r.status === 200,
  });
}

function getCommonFundBalance(target, headers) {
  const response = http.get(
    `${target.baseUrl}/api/trips/${target.tripId}/common-fund-balance`,
    params(target, headers, 'common-fund-balance'),
  );
  recordEndpoint(endpointTrends.commonFundBalance, response, {
    phase: 'scenario',
    endpoint: 'common-fund-balance',
    method: 'GET',
    target: target.name,
    baseUrl: target.baseUrl,
  });

  check(response, {
    'common fund balance returned 200': (r) => r.status === 200,
  });
}

function previewSettlement(target, headers) {
  const response = http.post(
    `${target.baseUrl}/api/trips/${target.tripId}/settlement-preview`,
    null,
    params(target, headers, 'settlement-preview'),
  );
  recordEndpoint(endpointTrends.settlementPreview, response, {
    phase: 'scenario',
    endpoint: 'settlement-preview',
    method: 'POST',
    target: target.name,
    baseUrl: target.baseUrl,
  });

  check(response, {
    'settlement preview returned 200': (r) => r.status === 200,
    'settlement preview response is success': (r) => r.json('success') === true,
  });
}

function getBalanceSummary(target, headers) {
  const response = http.get(
    `${target.baseUrl}/api/trips/${target.tripId}/balance-summary`,
    params(target, headers, 'balance-summary'),
  );
  recordEndpoint(endpointTrends.balanceSummary, response, {
    phase: 'scenario',
    endpoint: 'balance-summary',
    method: 'GET',
    target: target.name,
    baseUrl: target.baseUrl,
  });

  check(response, {
    'balance summary returned 200': (r) => r.status === 200,
  });
}
