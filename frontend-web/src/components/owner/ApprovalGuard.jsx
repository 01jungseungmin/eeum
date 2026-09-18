import { Navigate, Outlet, useOutletContext } from "react-router-dom";

function ApprovalGuard() {
  // 💡 부모(MainLayout)가 이미 받아온 승인 상태 등을 실시간으로 공유받음
  const outletContext = useOutletContext();
  const { approvalStatus } = outletContext;

  // 아직 상태를 불러오는 중이라면 대기
  if (approvalStatus === null) {
    return (
      <div style={{ padding: "40px", textAlign: "center" }}>
        권한을 확인하는 중입니다...
      </div>
    );
  }

  // 최종 승인이 아니라면 승인 상태 페이지로 리다이렉트
  if (approvalStatus !== "APPROVED") {
    return <Navigate to="/approval-status" replace />;
  }

  // 승인 완료 유저만 통과 (MainLayout이 내려준 context를 그대로 하위 라우트에 전달)
  return <Outlet context={outletContext} />;
}

export default ApprovalGuard;
