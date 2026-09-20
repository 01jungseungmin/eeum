// 사이드바 "승인 상태" 메뉴 옆에 붙이는 배지. 승인 완료면 배지를 달지 않는다.
export function getApprovalBadge(approvalStatus, reviewRequestedAt) {
  if (approvalStatus === 'REJECTED') {
    return { label: '반려됨', tone: 'danger' };
  }
  if (approvalStatus === 'PENDING') {
    return reviewRequestedAt
      ? { label: '심사중', tone: 'info' }
      : { label: '확인필요', tone: 'danger' };
  }
  return null;
}
