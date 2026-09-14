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
  // S3 Presigned POST는 file 필드가 formFields 뒤에 와야 한다.
  formData.append('file', {
    uri,
    name: uri.split('/').pop() || `${Date.now()}.jpg`,
    type: contentType,
  } as any);

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
