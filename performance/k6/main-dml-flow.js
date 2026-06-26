import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const DEFAULT_BASE_URL = 'http://localhost:8080';

const baseUrl = (__ENV.BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');
const ownerToken = __ENV.OWNER_KAKAO_ACCESS_TOKEN || 'local-test:verified:hana';
const senderToken = __ENV.SENDER_KAKAO_ACCESS_TOKEN || 'local-test:verified:minseo';
const receiverToken = __ENV.RECEIVER_KAKAO_ACCESS_TOKEN || 'local-test:verified:joon';
const requestSleepSeconds = Number(__ENV.SLEEP || 1);

const createTripTrend = new Trend('dml_create_trip_duration', true);
const createTransactionTrend = new Trend('dml_create_transaction_duration', true);
const updateTransactionTrend = new Trend('dml_update_transaction_duration', true);
const previewSettlementTrend = new Trend('dml_preview_settlement_duration', true);
const confirmSettlementTrend = new Trend('dml_confirm_settlement_duration', true);
const getTransfersTrend = new Trend('dml_get_transfers_duration', true);
const confirmTransferTrend = new Trend('dml_confirm_transfer_duration', true);

export const options = {
  scenarios: {
    dml_flow: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.RAMP_UP || '10s', target: Number(__ENV.VUS || 1) },
        { duration: __ENV.HOLD || '30s', target: Number(__ENV.VUS || 1) },
        { duration: __ENV.RAMP_DOWN || '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    dml_create_trip_duration: ['p(95)<1000'],
    dml_create_transaction_duration: ['p(95)<1000'],
    dml_update_transaction_duration: ['p(95)<1000'],
    dml_confirm_settlement_duration: ['p(95)<3000'],
    dml_confirm_transfer_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const owner = login(ownerToken, 'owner');
  const sender = login(senderToken, 'sender');
  const receiver = login(receiverToken, 'receiver');

  return { owner, sender, receiver };
}

export default function (data) {
  const trip = createTrip(data);
  const participants = participantMap(trip);

  const ownerParticipantId = participants.byUserId[data.owner.userId];
  const senderParticipantId = participants.byUserId[data.sender.userId];
  const receiverParticipantId = participants.byUserId[data.receiver.userId];

  if (!ownerParticipantId || !senderParticipantId || !receiverParticipantId) {
    fail(`failed to resolve participants from trip ${trip.id}`);
  }

  const transaction = createTransaction(
    data.owner.accessToken,
    trip.id,
    senderParticipantId,
    [ownerParticipantId, senderParticipantId, receiverParticipantId],
    30000,
    10000,
  );

  updateTransaction(
    data.owner.accessToken,
    trip.id,
    transaction.summary.id,
    senderParticipantId,
    [ownerParticipantId, senderParticipantId, receiverParticipantId],
    33000,
    11000,
  );

  previewSettlement(data.owner.accessToken, trip.id);
  const settlement = confirmSettlement(data.owner.accessToken, trip.id);
  const transfers = getTransfers(data.owner.accessToken, trip.id, settlement.id);

  confirmTransfers(data, trip.id, transfers, participants.byParticipantId);

  sleep(requestSleepSeconds);
}

function login(kakaoAccessToken, label) {
  const response = http.post(
    `${baseUrl}/api/auth/oauth/kakao`,
    JSON.stringify({ accessToken: kakaoAccessToken }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: `dml-login-${label}` },
    },
  );

  check(response, {
    [`${label} login returned 200`]: (r) => r.status === 200,
    [`${label} login authenticated`]: (r) => Boolean(r.json('data.accessToken')),
  });

  const accessToken = response.json('data.accessToken');
  if (!accessToken) {
    fail(`${label} failed to login`);
  }

  const meResponse = http.get(`${baseUrl}/api/users/me`, {
    headers: authHeaders(accessToken),
    tags: { endpoint: `dml-users-me-${label}` },
  });

  check(meResponse, {
    [`${label} me returned 200`]: (r) => r.status === 200,
    [`${label} me has id`]: (r) => Boolean(r.json('data.id')),
  });

  const userId = meResponse.json('data.id');
  if (!userId) {
    fail(`${label} failed to resolve user id`);
  }

  return { accessToken, userId };
}

function createTrip(data) {
  const title = `DML_LOADTEST_${__VU}_${__ITER}_${Date.now()}`;
  const body = {
    title,
    defaultCurrency: 'KRW',
    exchangeRateBaseDate: '2026-06-01',
    startDate: '2026-06-01',
    endDate: '2026-06-10',
    countries: [
      {
        countryCode: 'KR',
        countryName: '대한민국',
        sortOrder: 1,
      },
    ],
    participants: [
      {
        displayName: 'DML 송금자',
        userId: data.sender.userId,
      },
      {
        displayName: 'DML 수신자',
        userId: data.receiver.userId,
      },
    ],
  };

  const response = http.post(
    `${baseUrl}/api/trips`,
    JSON.stringify(body),
    {
      headers: authHeaders(data.owner.accessToken),
      tags: { endpoint: 'dml-create-trip' },
    },
  );
  createTripTrend.add(response.timings.duration);

  check(response, {
    'create trip returned 200': (r) => r.status === 200,
    'create trip response is success': (r) => r.json('success') === true,
    'create trip has id': (r) => Boolean(r.json('data.id')),
  });

  const trip = response.json('data');
  if (!trip || !trip.id) {
    fail('failed to create DML trip');
  }

  return trip;
}

function createTransaction(accessToken, tripId, payerParticipantId, participantIds, amount, shareAmount) {
  const response = http.post(
    `${baseUrl}/api/trips/${tripId}/transactions`,
    JSON.stringify(transactionBody(payerParticipantId, participantIds, amount, shareAmount)),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'dml-create-transaction' },
    },
  );
  createTransactionTrend.add(response.timings.duration);

  check(response, {
    'create transaction returned 200': (r) => r.status === 200,
    'create transaction has id': (r) => Boolean(r.json('data.summary.id')),
  });

  const transaction = response.json('data');
  if (!transaction || !transaction.summary || !transaction.summary.id) {
    fail(`failed to create transaction for trip ${tripId}`);
  }

  return transaction;
}

function updateTransaction(accessToken, tripId, transactionId, payerParticipantId, participantIds, amount, shareAmount) {
  const response = http.patch(
    `${baseUrl}/api/trips/${tripId}/transactions/${transactionId}`,
    JSON.stringify(transactionBody(payerParticipantId, participantIds, amount, shareAmount)),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'dml-update-transaction' },
    },
  );
  updateTransactionTrend.add(response.timings.duration);

  check(response, {
    'update transaction returned 200': (r) => r.status === 200,
    'update transaction amount changed': (r) => Number(r.json('data.summary.amount')) === amount,
  });
}

function previewSettlement(accessToken, tripId) {
  const response = http.post(`${baseUrl}/api/trips/${tripId}/settlement-preview`, null, {
    headers: authHeaders(accessToken),
    tags: { endpoint: 'dml-preview-settlement' },
  });
  previewSettlementTrend.add(response.timings.duration);

  check(response, {
    'preview settlement returned 200': (r) => r.status === 200,
    'preview settlement response is success': (r) => r.json('success') === true,
  });
}

function confirmSettlement(accessToken, tripId) {
  const response = http.post(`${baseUrl}/api/trips/${tripId}/settlements`, null, {
    headers: authHeaders(accessToken),
    tags: { endpoint: 'dml-confirm-settlement' },
  });
  confirmSettlementTrend.add(response.timings.duration);

  check(response, {
    'confirm settlement returned 200': (r) => r.status === 200,
    'confirm settlement has id': (r) => Boolean(r.json('data.id')),
    'confirm settlement has transfers': (r) => (r.json('data.transfers') || []).length > 0,
  });

  const settlement = response.json('data');
  if (!settlement || !settlement.id) {
    fail(`failed to confirm settlement for trip ${tripId}`);
  }

  return settlement;
}

function getTransfers(accessToken, tripId, settlementId) {
  const response = http.get(
    `${baseUrl}/api/trips/${tripId}/settlement-transfers?settlementId=${settlementId}`,
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'dml-get-transfers' },
    },
  );
  getTransfersTrend.add(response.timings.duration);

  check(response, {
    'get transfers returned 200': (r) => r.status === 200,
    'get transfers has rows': (r) => (r.json('data') || []).length > 0,
  });

  const transfers = response.json('data') || [];
  if (transfers.length === 0) {
    fail(`failed to get transfers for settlement ${settlementId}`);
  }

  return transfers;
}

function confirmTransfers(data, tripId, transfers, participantById) {
  for (const transfer of transfers) {
    confirmTransferSide(
      tokenForParticipant(data, participantById[transfer.senderParticipantId]),
      tripId,
      transfer.id,
      'sender',
    );
    confirmTransferSide(
      tokenForParticipant(data, participantById[transfer.receiverParticipantId]),
      tripId,
      transfer.id,
      'receiver',
    );
  }
}

function confirmTransferSide(accessToken, tripId, transferId, side) {
  const response = http.patch(
    `${baseUrl}/api/trips/${tripId}/settlement-transfers/${transferId}/${side}-confirmation`,
    null,
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: `dml-confirm-transfer-${side}` },
    },
  );
  confirmTransferTrend.add(response.timings.duration);

  check(response, {
    [`confirm transfer ${side} returned 200`]: (r) => r.status === 200,
    [`confirm transfer ${side} has status`]: (r) => Boolean(r.json('data.status')),
  });
}

function participantMap(trip) {
  const byUserId = {};
  const byParticipantId = {};

  for (const participant of trip.participants || []) {
    if (participant.userId) {
      byUserId[participant.userId] = participant.id;
    }
    byParticipantId[participant.id] = participant;
  }

  return { byUserId, byParticipantId };
}

function tokenForParticipant(data, participant) {
  if (!participant || !participant.userId) {
    fail('transfer participant is not linked to a user');
  }

  if (participant.userId === data.owner.userId) {
    return data.owner.accessToken;
  }
  if (participant.userId === data.sender.userId) {
    return data.sender.accessToken;
  }
  if (participant.userId === data.receiver.userId) {
    return data.receiver.accessToken;
  }

  fail(`no token for participant user ${participant.userId}`);
}

function transactionBody(payerParticipantId, participantIds, amount, shareAmount) {
  return {
    transactionType: 'EXPENSE',
    amount,
    currency: 'KRW',
    payments: [
      {
        participantId: payerParticipantId,
        amount,
      },
    ],
    shares: participantIds.map((participantId) => ({
      participantId,
      shareAmount,
      shareRatio: null,
    })),
  };
}

function authHeaders(accessToken) {
  return {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  };
}
