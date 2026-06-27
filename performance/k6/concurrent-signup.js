import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const DEFAULT_BASE_URL = 'http://localhost:8080';
const DEFAULT_CODE = '123456';

const scenario = __ENV.SCENARIO || 'same-session';
const baseUrl = (__ENV.BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');
const vus = Number(__ENV.VUS || (scenario === 'same-session' ? 20 : 10));
const confirmCode = __ENV.CONFIRM_CODE || DEFAULT_CODE;
const maxDuration = __ENV.MAX_DURATION || '5s';

const signupSuccess = new Counter('signup_success_total');
const allowedFailure = new Counter('signup_allowed_failure_total');
const unexpectedFailure = new Counter('signup_unexpected_failure_total');

export const options = {
  scenarios: {
    concurrent_signup: {
      executor: 'shared-iterations',
      vus,
      iterations: vus,
      maxDuration,
    },
  },
  thresholds: {
    signup_success_total: ['count==1'],
    signup_unexpected_failure_total: ['count==0'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  if (!['same-session', 'same-phone'].includes(scenario)) {
    fail(`unsupported SCENARIO: ${scenario}`);
  }

  const runId = __ENV.RUN_ID || String(Date.now());
  const phoneNumber = createPhoneNumber(runId, scenario);

  if (scenario === 'same-session') {
    const localUserId = `issue45-${runId}-same-session`;
    const temporaryToken = createTemporarySession(localUserId);
    seedPhoneVerification(temporaryToken, phoneNumber);

    return {
      scenario,
      runId,
      phoneNumber,
      temporaryTokens: [temporaryToken],
    };
  }

  const temporaryTokens = [];
  for (let index = 0; index < vus; index += 1) {
    const localUserId = `issue45-${runId}-same-phone-${index}`;
    const temporaryToken = createTemporarySession(localUserId);
    seedPhoneVerification(temporaryToken, phoneNumber);
    temporaryTokens.push(temporaryToken);
  }

  return {
    scenario,
    runId,
    phoneNumber,
    temporaryTokens,
  };
}

export default function (data) {
  const temporaryToken = data.scenario === 'same-session'
    ? data.temporaryTokens[0]
    : data.temporaryTokens[Math.min(__VU - 1, data.temporaryTokens.length - 1)];

  const response = http.post(
    `${baseUrl}/api/auth/phone/confirm`,
    JSON.stringify({
      temporaryToken,
      phoneNumber: data.phoneNumber,
      code: confirmCode,
    }),
    {
      headers: jsonHeaders(),
      tags: {
        endpoint: 'auth-phone-confirm',
        scenario: data.scenario,
      },
    },
  );

  const success = response.status === 200 && Boolean(response.json('data.accessToken'));
  if (success) {
    signupSuccess.add(1);
    check(response, {
      'successful confirm returned token': () => true,
    });
    return;
  }

  const errorCode = response.json('code');
  const allowed = isAllowedFailure(data.scenario, errorCode);
  if (allowed) {
    allowedFailure.add(1, { code: errorCode });
  } else {
    unexpectedFailure.add(1, { code: errorCode || 'UNKNOWN' });
    console.error(JSON.stringify({
      type: 'unexpected_signup_failure',
      scenario: data.scenario,
      status: response.status,
      code: errorCode,
      body: response.body,
    }));
  }

  check(response, {
    'failed confirm has allowed error code': () => allowed,
  });
}

function createTemporarySession(localUserId) {
  const response = http.post(
    `${baseUrl}/api/auth/oauth/kakao`,
    JSON.stringify({
      accessToken: `local-test:${localUserId}`,
    }),
    {
      headers: jsonHeaders(),
      tags: { endpoint: 'auth-oauth-kakao-setup' },
    },
  );

  check(response, {
    'local kakao login returned 200': (r) => r.status === 200,
    'local kakao login requires phone verification': (r) => r.json('data.status') === 'PHONE_VERIFICATION_REQUIRED',
    'local kakao login returned temporary token': (r) => Boolean(r.json('data.temporaryToken')),
  });

  const temporaryToken = response.json('data.temporaryToken');
  if (!temporaryToken) {
    fail(`failed to create temporary session for ${localUserId}: ${response.status} ${response.body}`);
  }

  return temporaryToken;
}

function seedPhoneVerification(temporaryToken, phoneNumber) {
  const response = http.post(
    `${baseUrl}/api/local-test/auth/phone-verifications`,
    JSON.stringify({
      temporaryToken,
      phoneNumber,
      code: confirmCode,
    }),
    {
      headers: jsonHeaders(),
      tags: { endpoint: 'local-test-auth-phone-verifications' },
    },
  );

  check(response, {
    'seed phone verification returned 200': (r) => r.status === 200,
    'seed phone verification succeeded': (r) => r.json('success') === true,
  });

  if (response.status !== 200) {
    fail(`failed to seed phone verification: ${response.status} ${response.body}`);
  }
}

function isAllowedFailure(currentScenario, errorCode) {
  if (currentScenario === 'same-session') {
    return [
      'SIGNUP_CONFIRMATION_IN_PROGRESS',
      'SIGNUP_ALREADY_COMPLETED',
      'PHONE_VERIFICATION_TOKEN_EXPIRED',
      'PHONE_VERIFICATION_CODE_EXPIRED',
    ].includes(errorCode);
  }

  return errorCode === 'PHONE_NUMBER_ALREADY_USED';
}

function createPhoneNumber(runId, currentScenario) {
  const scenarioDigit = currentScenario === 'same-session' ? '8' : '9';
  const digits = runId.replace(/\D/g, '').slice(-7).padStart(7, '0');
  return `010${scenarioDigit}${digits}`;
}

function jsonHeaders() {
  return {
    'Content-Type': 'application/json',
  };
}
