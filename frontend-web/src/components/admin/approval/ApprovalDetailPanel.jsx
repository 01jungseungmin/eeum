import React from 'react';
import styled from 'styled-components';
import { FileText, Image as ImageIcon, Check } from 'lucide-react';

const RightDetailPanel = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  padding: 32px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);

  h2 {
    margin: 0 0 24px 0;
    font-size: 20px;
    font-weight: 700;
    color: #262626;
  }

  .info-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px 40px;
    margin-bottom: 32px;

    .field-block {
      .label {
        font-size: 12px;
        color: #8c8c8c;
        margin-bottom: 6px;
      }
      .value {
        font-size: 15px;
        color: #262626;
        font-weight: 500;
      }
    }
  }

  .section-title {
    font-size: 14px;
    font-weight: 700;
    color: #595959;
    margin-bottom: 12px;
  }

  .docs-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    margin-bottom: 32px;
  }

  .memo-textarea {
    width: 100%;
    height: 80px;
    padding: 12px 16px;
    border-radius: 6px;
    border: 1px solid #e8e8e8;
    background-color: #f5f5f5;
    font-size: 14px;
    resize: none;
    outline: none;
    font-family: inherit;
    box-sizing: border-box;
    margin-bottom: 32px; /* 버튼과의 간격 확보 */
    transition: all 0.2s;

    &:focus {
      background-color: white;
      border-color: #2d5a43;
    }
  }
`;

const DocCard = styled.div`
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #2d5a43;
    background-color: #fbfcfb;
  }

  .icon-box {
    color: #8c8c8c;
    display: flex;
    align-items: center;
  }
  .doc-info {
    font-size: 13px;
    color: #595959;
    font-weight: 500;
  }
`;

// 🔘 피그마 시안 완벽 매칭 버튼 그룹 레이아웃
const ActionButtonBar = styled.div`
  display: flex;
  justify-content: center;
  gap: 12px;

  button {
    flex: 1; /* 가로폭을 균등하게 넓혀 채워줍니다 */
    max-width: 160px; /* 너무 퍼지지 않게 적정 최대 너비 제한 */
    padding: 11px 0;
    border-radius: 6px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    border: 1px solid transparent;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    transition: all 0.15s ease;
  }

  .btn-list,
  .btn-hold {
    background: white;
    color: #262626;
    border-color: #d9d9d9;
    &:hover {
      background: #f5f5f5;
    }
  }

  .btn-reject {
    background: white;
    color: #ff4d4f;
    border-color: #ffccc7;
    &:hover {
      background: #fff1f0;
      border-color: #ff4d4f;
    }
  }

  .btn-approve {
    background: #2d5a43;
    color: white;
    max-width: 200px; /* 승인 버튼은 피그마처럼 비중을 살짝 더 넓게 세팅 */
    &:hover {
      background: #1f3f2f;
    }
  }
`;

function ApprovalDetailPanel({
  account,
  memo,
  onMemoChange,
  onGoBack,
  onReject,
  onApprove,
}) {
  const nickname = account?.nickname || '상점명 없음';
  const name = account?.name || '-';
  const email = account?.email || '-';

  return (
    <RightDetailPanel>
      <h2>{nickname}</h2>

      <div className="info-grid">
        <div className="field-block">
          <div className="label">사업자명</div>
          <div className="value">{name}</div>
        </div>
        <div className="field-block">
          <div className="label">이메일</div>
          <div className="value">{email}</div>
        </div>
      </div>

      <div className="section-title">제출 서류</div>
      <div className="docs-grid">
        <DocCard onClick={() => alert('사업자등록증 미리보기')}>
          <div className="icon-box">
            <FileText size={20} />
          </div>
          <span className="doc-info">사업자등록증.pdf</span>
        </DocCard>
        <DocCard onClick={() => alert('신분증 사본 미리보기')}>
          <div className="icon-box">
            <ImageIcon size={20} />
          </div>
          <span className="doc-info">신분증 사본.jpg</span>
        </DocCard>
        <DocCard onClick={() => alert('통신판매업 신고증 미리보기')}>
          <div className="icon-box">
            <ImageIcon size={20} />
          </div>
          <span className="doc-info">통신판매업.png</span>
        </DocCard>
      </div>

      <div className="section-title">관리자 메모</div>
      <textarea
        className="memo-textarea"
        placeholder="사장에게 공유할 메시지를 입력하세요..."
        value={memo}
        onChange={(e) => onMemoChange(e.target.value)}
      />

      <ActionButtonBar>
        <button className="btn-list" onClick={onGoBack}>
          목록으로
        </button>
        <button className="btn-reject" onClick={onReject}>
          거부
        </button>
        <button className="btn-approve" onClick={onApprove}>
          <Check size={16} /> 승인
        </button>
      </ActionButtonBar>
    </RightDetailPanel>
  );
}

export default ApprovalDetailPanel;
