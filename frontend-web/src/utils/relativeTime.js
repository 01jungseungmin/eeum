// 서버가 내려주는 시각(LocalDateTime, 타임존 없음)을 "방금 전 / N분 전 / N시간 전 / N일 전"으로 표시
export const formatRelativeTime = (dateTimeStr, now = new Date()) => {
  if (!dateTimeStr) return '';

  const target = new Date(dateTimeStr);
  if (Number.isNaN(target.getTime())) return '';

  const diffMinutes = Math.floor((now - target) / 60000);

  if (diffMinutes < 1) return '방금 전';
  if (diffMinutes < 60) return `${diffMinutes}분 전`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;

  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 30) return `${diffDays}일 전`;

  return dateTimeStr.split('T')[0];
};

// 대기 시작 시각이 기준 시간(기본 24시간)을 넘겼는지 — 오래 방치된 항목을 "긴급"으로 표시할 때 사용
export const isWaitingOver = (dateTimeStr, hours = 24, now = new Date()) => {
  if (!dateTimeStr) return false;

  const target = new Date(dateTimeStr);
  if (Number.isNaN(target.getTime())) return false;

  return now - target >= hours * 60 * 60 * 1000;
};
