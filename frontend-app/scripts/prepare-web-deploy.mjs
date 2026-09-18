// Expo web export 후, Vercel 배포 전에 한 번 돌린다.
//
// Metro는 의존성에서 온 에셋을 원래 경로 그대로 내보내서 폰트가
// dist-web/assets/node_modules/@expo/vector-icons/... 에 떨어진다.
// Vercel CLI는 경로에 node_modules가 들어간 파일을 업로드에서 무조건 제외한다
// (.vercelignore의 ! 부정 패턴으로도, --archive로도 못 뚫는다).
//
// 그 결과 폰트 요청이 SPA rewrite에 걸려 index.html을 받고,
// 브라우저가 "OTS parsing error: invalid sfntVersion"을 내며 아이콘이 전부 깨진다.
// (sfntVersion 1008813135 = 0x3C21444F = "<!DO")
//
// 그래서 디렉터리 이름에서 node_modules를 지우고 번들 안의 참조도 같이 고친다.

import { readdirSync, readFileSync, writeFileSync, renameSync, existsSync, statSync } from 'node:fs';
import { join } from 'node:path';

const DIST = process.argv[2] ?? 'dist-web';
const FROM = 'assets/node_modules';
const TO = 'assets/vendor';

const fromDir = join(DIST, 'assets', 'node_modules');
const toDir = join(DIST, 'assets', 'vendor');

if (!existsSync(DIST)) {
  console.error(`[prepare-web-deploy] ${DIST} 가 없다. 먼저 expo export를 돌려라.`);
  process.exit(1);
}

if (existsSync(fromDir)) {
  renameSync(fromDir, toDir);
  console.log(`[prepare-web-deploy] ${FROM} -> ${TO}`);
} else if (existsSync(toDir)) {
  console.log('[prepare-web-deploy] 이미 처리됨');
} else {
  console.log('[prepare-web-deploy] 옮길 에셋 없음 (경로 규칙이 바뀌었는지 확인할 것)');
}

// 번들과 HTML에 박힌 참조를 고친다. 텍스트 파일만 건드린다 —
// 폰트·이미지를 문자열로 읽어 쓰면 파일이 깨진다.
const TEXT_EXT = ['.js', '.html', '.json', '.css', '.map'];
let patched = 0;

function walk(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) {
      walk(path);
      continue;
    }
    if (!TEXT_EXT.some((ext) => entry.endsWith(ext))) continue;

    const before = readFileSync(path, 'utf8');
    if (!before.includes(FROM)) continue;

    writeFileSync(path, before.split(FROM).join(TO));
    patched += 1;
    console.log(`[prepare-web-deploy] 참조 수정: ${path}`);
  }
}

walk(DIST);

if (patched === 0 && existsSync(toDir)) {
  console.log('[prepare-web-deploy] 수정할 참조 없음');
}

// 고치고 나서도 node_modules가 남아 있으면 그 파일들은 배포에서 조용히 빠진다.
const leftovers = [];
function findLeftovers(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) {
      if (entry === 'node_modules') leftovers.push(path);
      else findLeftovers(path);
    }
  }
}
findLeftovers(DIST);

if (leftovers.length > 0) {
  console.error('[prepare-web-deploy] node_modules 경로가 남아 있다 — 배포에서 제외된다:');
  leftovers.forEach((path) => console.error(`  ${path}`));
  process.exit(1);
}

// expo export --clear가 출력 디렉터리를 통째로 비우므로 배포 설정도 매번 다시 만든다.
// 없으면 Vercel이 package.json도 없는 디렉터리에서 npm ci를 돌리다 실패하고,
// SPA rewrite가 빠져 /used-trade/1 같은 동적 경로가 404가 된다.
const vercelConfig = {
  framework: null,
  installCommand: 'echo skip',
  buildCommand: 'echo skip',
  outputDirectory: '.',
  rewrites: [{ source: '/(.*)', destination: '/index.html' }],
};
writeFileSync(join(DIST, 'vercel.json'), JSON.stringify(vercelConfig, null, 2) + '\n');
console.log('[prepare-web-deploy] vercel.json 생성');

console.log('[prepare-web-deploy] 완료');
