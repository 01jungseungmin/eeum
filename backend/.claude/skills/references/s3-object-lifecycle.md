# S3 객체 수명주기

이미지 객체는 두 갈래로 정리된다. **한쪽이라도 빠지면 버킷이 무한히 자란다.**

| 대상 | 정리 주체 | 근거 |
|---|---|---|
| `tmp/**` (확정 전 임시 객체) | **S3 Lifecycle 규칙** (아래) | DB 행이 없어 애플리케이션이 볼 수 없다 |
| `used/**`, `stores/**`, `products/**`, `profiles/**`, `community/**`, `chat/**` (확정된 최종 객체) | `FileObjectCleanupScheduler` | `file_object` 행을 근거로 삭제한다 |

## tmp/ 는 왜 애플리케이션이 못 지우는가

`file_object` 행은 `FileObjectLifecycleService.registerConfirmed`, 즉 `POST /files/confirm`
시점에만 생긴다. 그래서 아래 두 경우에는 S3에 객체만 남고 DB에는 아무 흔적이 없다.

1. 사용자가 presigned POST를 받아 `tmp/` 에 업로드한 뒤 확정 없이 이탈한다 (가장 흔하다).
2. 확정 성공 후 임시 객체 삭제(`deleteObject`)가 실패한다 — 이 경우 로그만 남긴다.

`FileObjectCleanupScheduler`는 `file_object`를 순회하므로 이 객체들을 영원히 보지 못한다.
**애플리케이션 코드를 고쳐서 해결할 문제가 아니라, 버킷 규칙으로 닫아야 한다.**

## 필요한 Lifecycle 규칙 (운영·스테이징 버킷 모두)

```json
{
  "Rules": [
    {
      "ID": "expire-unconfirmed-temporary-uploads",
      "Status": "Enabled",
      "Filter": { "Prefix": "tmp/" },
      "Expiration": { "Days": 1 },
      "AbortIncompleteMultipartUpload": { "DaysAfterInitiation": 1 }
    }
  ]
}
```

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket "$S3_BUCKET" \
  --lifecycle-configuration file://s3-lifecycle.json
```

`Days: 1`은 확정 유예(`FileStorageService`의 presigned URL 만료 10분)보다 한참 길어
정상 업로드를 지우지 않는다. 반대로 최종 객체 보존 기간
(`FileObjectCleanupScheduler.CONFIRMED_FILE_RETENTION_HOURS`, 24시간)과는 무관하다 —
그쪽은 `tmp/` 접두사에 걸리지 않는다.

## 확인

```bash
aws s3api get-bucket-lifecycle-configuration --bucket "$S3_BUCKET"
```

규칙이 없으면 `NoSuchLifecycleConfiguration`이 떨어진다. 새 버킷을 만들거나 버킷을
교체할 때 이 규칙을 함께 넣는다 — 규칙 없이 뜬 버킷은 조용히 자라기만 하고
어떤 알림도 울리지 않는다.
