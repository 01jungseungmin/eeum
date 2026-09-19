import { Platform } from 'react-native';

import { client } from '../api/client';

// 백엔드 FileUploadPurpose와 동일한 값이어야 함 (com.eeum.eeum.application.file.FileUploadPurpose)
export type ImageUploadPurpose = 'PROFILE' | 'STORE' | 'PRODUCT' | 'USED' | 'COMMUNITY' | 'CHAT';

const EXTENSION_MIME_MAP: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  heic: 'image/heic',
  heif: 'image/heif',
  gif: 'image/gif',
};

const guessMimeType = (uri: string): string => {
  const ext = uri.split('?')[0].split('.').pop()?.toLowerCase() || '';
  return EXTENSION_MIME_MAP[ext] || 'image/jpeg';
};

/**
 * 기기 로컬 file:// uri 하나를 Presigned URL(S3 POST) 경유로 업로드하고,
 * /files/confirm으로 확정한 뒤 서버에 저장할 objectKey를 반환한다.
 */
export async function uploadImageAsset(uri: string, purpose: ImageUploadPurpose): Promise<string> {
  const localFile = await (await fetch(uri)).blob();
  const contentType = localFile.type || guessMimeType(uri);
  const contentLength = localFile.size;

  const presignRes = await client.post('/files/presigned-url', {
    purpose,
    contentType,
    contentLength,
  });
  const { objectKey, uploadUrl, formFields } = presignRes.data?.data ?? presignRes.data;

  const formData = new FormData();
  Object.entries(formFields || {}).forEach(([key, value]) => {
    formData.append(key, value as string);
  });

  const fileName = uri.split('/').pop() || `${Date.now()}.jpg`;

  // S3 Presigned POST는 file 필드가 formFields 뒤에 와야 한다.
  //
  // 파일을 담는 방법이 플랫폼마다 다르다. 네이티브의 FormData는 {uri,name,type}
  // 객체를 파일로 알아듣지만, 브라우저의 FormData는 객체를 String()으로 바꿔
  // 문자열 "[object Object]" 를 보낸다. S3 정책 조건이 content-length-range 뿐이라
  // 15바이트짜리 그 문자열도 204로 통과한다 — 업로드가 성공한 것처럼 보이고
  // 실제로는 이미지 자리에 텍스트가 저장돼, 나중에 깨진 이미지로 드러난다.
  //
  // 웹에서는 위에서 이미 읽어 둔 Blob 을 그대로 넣는다.
  if (Platform.OS === 'web') {
    formData.append('file', localFile, fileName);
  } else {
    formData.append('file', {
      uri,
      name: fileName,
      type: contentType,
    } as any);
  }

  const uploadResponse = await fetch(uploadUrl, {
    method: 'POST',
    body: formData,
  });

  if (!uploadResponse.ok) {
    throw new Error('이미지 업로드(S3)에 실패했습니다.');
  }

  const confirmRes = await client.post('/files/confirm', { objectKey });
  const confirmed = confirmRes.data?.data ?? confirmRes.data;
  return confirmed.objectKey;
}

/** 여러 장을 순서대로 업로드하고 objectKey 배열을 반환한다. */
export async function uploadImageAssets(uris: string[], purpose: ImageUploadPurpose): Promise<string[]> {
  const objectKeys: string[] = [];
  for (const uri of uris) {
    objectKeys.push(await uploadImageAsset(uri, purpose));
  }
  return objectKeys;
}
