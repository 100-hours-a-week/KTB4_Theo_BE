import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { Counter, Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const USER_COUNT = numberFromEnvironment("USER_COUNT", 500);
const PREPARE_VUS = Math.min(
  numberFromEnvironment("PREPARE_VUS", 10),
  USER_COUNT,
);
const ACCOUNT_PREFIX = __ENV.ACCOUNT_PREFIX || "loadtest";
const ACCOUNT_DOMAIN = __ENV.ACCOUNT_DOMAIN || "example.com";
const PASSWORD = requiredEnvironment("LOAD_TEST_PASSWORD");

const createdUsers = new Counter("created_users");
const existingUsers = new Counter("existing_users");
const prepareFailures = new Rate("prepare_user_failed");

export const options = {
  scenarios: {
    prepareUsers: {
      executor: "shared-iterations",
      vus: PREPARE_VUS,
      iterations: USER_COUNT,
      maxDuration: "10m",
    },
  },
  thresholds: {
    prepare_user_failed: ["rate==0"],
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};

export default function () {
  const accountNumber = exec.scenario.iterationInTest + 1;
  const response = http.post(
    `${BASE_URL}/users/signup`,
    JSON.stringify({
      email: emailFor(accountNumber),
      password: PASSWORD,
      passwordConfirm: PASSWORD,
      nickname: nicknameFor(accountNumber),
      profileImage: null,
    }),
    {
      headers: {
        "Content-Type": "application/json",
      },
      responseCallback: http.expectedStatuses(201, 409),
      tags: {
        endpoint: "signup",
        name: "POST /users/signup",
      },
    },
  );

  const message = jsonValue(response, "message");
  const created = response.status === 201 && message === "signup_success";
  const alreadyExists =
    response.status === 409 &&
    (message === "email_already_exist" || message === "nickname_already_exist");
  const prepared = created || alreadyExists;

  check(
    response,
    {
      "테스트 계정이 준비되었다": () => prepared,
    },
    {
      scope: "prepare-users",
    },
  );

  createdUsers.add(created);
  existingUsers.add(alreadyExists);
  prepareFailures.add(!prepared);
}

function emailFor(accountNumber) {
  return `${ACCOUNT_PREFIX}${accountNumber}@${ACCOUNT_DOMAIN}`;
}

function nicknameFor(accountNumber) {
  return `load${String(accountNumber).padStart(5, "0")}`;
}

function jsonValue(response, selector) {
  try {
    return response.json(selector);
  } catch {
    return null;
  }
}

function requiredEnvironment(name) {
  const value = __ENV[name];

  if (!value) {
    throw new Error(`${name} 환경변수가 필요합니다.`);
  }

  return value;
}

function numberFromEnvironment(name, defaultValue) {
  const rawValue = __ENV[name];
  const value = rawValue === undefined ? defaultValue : Number(rawValue);

  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${name}은 1 이상의 정수여야 합니다.`);
  }

  return value;
}
