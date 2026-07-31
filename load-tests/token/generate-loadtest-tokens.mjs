import {
  createHmac,
  randomUUID,
} from "node:crypto";
import {
  chmodSync,
  mkdirSync,
  renameSync,
  writeFileSync,
} from "node:fs";
import {
  dirname,
  resolve,
} from "node:path";
import {
  spawnSync,
} from "node:child_process";

const DEFAULT_USER_COUNT = 10000;
const DEFAULT_ISSUER = "community-api";
const DEFAULT_EXPIRATION_MILLIS = 1800000;
const DEFAULT_OUTPUT =
  "load-tests/k6/.tokens/polling-users.json";

const options = parseArguments(process.argv.slice(2));
const jwtSecret = requiredEnvironment("JWT_SECRET");
const issuer = process.env.JWT_ISSUER || DEFAULT_ISSUER;
const expirationMillis = positiveInteger(
  process.env.JWT_ACCESS_TOKEN_EXPIRATION_MS,
  DEFAULT_EXPIRATION_MILLIS,
  "JWT_ACCESS_TOKEN_EXPIRATION_MS",
);
const signingKey = decodeSigningKey(jwtSecret);
const users = readLoadTestUsers(options.userCount);

validateUsers(users, options.userCount);

const issuedAtSeconds = Math.floor(Date.now() / 1000);
const expiresAtSeconds =
  issuedAtSeconds + Math.floor(expirationMillis / 1000);
const tokenRecords = users.map((user) => ({
  userId: user.userId,
  email: user.email,
  token: createAccessToken(
    user.userId,
    issuer,
    issuedAtSeconds,
    expiresAtSeconds,
    signingKey,
  ),
}));

writeTokenFile(options.outputPath, tokenRecords);

console.log("부하 테스트 JWT 생성을 완료했습니다.");
console.log(`- users: ${tokenRecords.length}`);
console.log(`- issuer: ${issuer}`);
console.log(`- expiresAt: ${new Date(expiresAtSeconds * 1000).toISOString()}`);
console.log(`- output: ${options.outputPath}`);

function readLoadTestUsers(userCount) {
  const mysqlBinary = process.env.MYSQL_BIN || "mysql";
  const databaseHost = process.env.DB_HOST || "localhost";
  const databasePort = process.env.DB_PORT || "3306";
  const databaseUsername = process.env.DB_USERNAME || "root";
  const databaseName = process.env.DB_NAME || "community_api";
  const databasePassword = process.env.DB_PASSWORD || "";
  const query = `
    SELECT
      id,
      email
    FROM users
    WHERE deleted_at IS NULL
      AND email REGEXP '^loadtest[0-9]+@example\\\\.com$'
    ORDER BY CAST(
      SUBSTRING_INDEX(
        SUBSTRING(email, CHAR_LENGTH('loadtest') + 1),
        '@',
        1
      ) AS UNSIGNED
    )
    LIMIT ${userCount}
  `;
  const childEnvironment = {
    ...process.env,
  };

  if (databasePassword) {
    childEnvironment.MYSQL_PWD = databasePassword;
  }

  const result = spawnSync(
    mysqlBinary,
    [
      "--batch",
      "--skip-column-names",
      "--host",
      databaseHost,
      "--port",
      databasePort,
      "--user",
      databaseUsername,
      "--database",
      databaseName,
      "--execute",
      query,
    ],
    {
      encoding: "utf8",
      env: childEnvironment,
      maxBuffer: 20 * 1024 * 1024,
    },
  );

  if (result.error) {
    throw new Error(
      `mysql 실행에 실패했습니다: ${result.error.message}`,
    );
  }

  if (result.status !== 0) {
    throw new Error(
      `테스트 사용자 조회에 실패했습니다: ${result.stderr.trim()}`,
    );
  }

  return result.stdout
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const [rawUserId, email] = line.split("\t");
      const userId = Number(rawUserId);

      if (!Number.isSafeInteger(userId) || !email) {
        throw new Error(`사용자 조회 결과 형식이 올바르지 않습니다: ${line}`);
      }

      return {
        userId,
        email,
      };
    });
}

function validateUsers(users, expectedCount) {
  if (users.length !== expectedCount) {
    throw new Error(
      `테스트 사용자 수가 부족합니다. expected=${expectedCount}, actual=${users.length}`,
    );
  }

  users.forEach((user, index) => {
    const accountNumber = index + 1;
    const expectedEmail = `loadtest${accountNumber}@example.com`;

    if (user.email !== expectedEmail) {
      throw new Error(
        `테스트 계정 순서가 올바르지 않습니다. expected=${expectedEmail}, actual=${user.email}`,
      );
    }
  });
}

function createAccessToken(
  userId,
  issuer,
  issuedAtSeconds,
  expiresAtSeconds,
  signingKey,
) {
  const header = {
    alg: "HS256",
    typ: "JWT",
  };
  const payload = {
    jti: randomUUID(),
    iss: issuer,
    sub: String(userId),
    type: "access",
    iat: issuedAtSeconds,
    exp: expiresAtSeconds,
  };
  const encodedHeader = base64UrlJson(header);
  const encodedPayload = base64UrlJson(payload);
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const signature = createHmac("sha256", signingKey)
    .update(signingInput)
    .digest("base64url");

  return `${signingInput}.${signature}`;
}

function decodeSigningKey(secret) {
  const signingKey = Buffer.from(secret, "base64");

  if (signingKey.length < 32) {
    throw new Error(
      "JWT_SECRET은 Base64 디코딩 후 32바이트 이상이어야 합니다.",
    );
  }

  return signingKey;
}

function base64UrlJson(value) {
  return Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
}

function writeTokenFile(outputPath, tokenRecords) {
  const resolvedOutputPath = resolve(outputPath);
  const outputDirectory = dirname(resolvedOutputPath);
  const temporaryPath = `${resolvedOutputPath}.${process.pid}.tmp`;

  mkdirSync(outputDirectory, {
    recursive: true,
    mode: 0o700,
  });
  writeFileSync(
    temporaryPath,
    `${JSON.stringify(tokenRecords, null, 2)}\n`,
    {
      encoding: "utf8",
      mode: 0o600,
    },
  );
  renameSync(temporaryPath, resolvedOutputPath);
  chmodSync(resolvedOutputPath, 0o600);
}

function parseArguments(argumentsList) {
  let userCount = DEFAULT_USER_COUNT;
  let outputPath = DEFAULT_OUTPUT;

  for (let index = 0; index < argumentsList.length; index += 1) {
    const argument = argumentsList[index];

    if (argument === "--count") {
      userCount = positiveInteger(
        argumentsList[index + 1],
        null,
        "--count",
      );
      index += 1;
      continue;
    }

    if (argument === "--output") {
      outputPath = argumentsList[index + 1];

      if (!outputPath) {
        throw new Error("--output 경로가 필요합니다.");
      }

      index += 1;
      continue;
    }

    throw new Error(`지원하지 않는 인자입니다: ${argument}`);
  }

  return {
    userCount,
    outputPath,
  };
}

function positiveInteger(rawValue, defaultValue, name) {
  const value = rawValue === undefined || rawValue === null
    ? defaultValue
    : Number(rawValue);

  if (!Number.isSafeInteger(value) || value < 1) {
    throw new Error(`${name}은 1 이상의 정수여야 합니다.`);
  }

  return value;
}

function requiredEnvironment(name) {
  const value = process.env[name];

  if (!value) {
    throw new Error(`${name} 환경변수가 필요합니다.`);
  }

  return value;
}
