import { Fragment, useEffect, useState } from 'react';
import styled from 'styled-components';
import { X } from 'lucide-react';
import { memberApi } from '../../../api/admin/memberApi';
import {
  MEMBER_ROLE_LABEL,
  MEMBER_STATUS_LABEL,
  MEMBER_PROVIDER_LABEL,
} from '../../../constants/memberConstants';
import {
  STORE_STATUS_LABEL,
  STORE_APPROVAL_STATUS_LABEL,
} from '../../../constants/storeConstants';

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalBox = styled.div`
  background: #ffffff;
  width: 100%;
  max-width: 640px;
  max-height: 85vh;
  overflow-y: auto;
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
  position: relative;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 24px;
  right: 24px;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  gap: 14px;

  .avatar {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    background: #edf5f1;
    color: #2d5a43;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    font-weight: 700;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  h3 {
    margin: 0 0 6px;
    font-size: 18px;
    font-weight: 700;
    color: #262626;
  }

  .badges {
    display: flex;
    gap: 6px;
  }
`;

const Badge = styled.span`
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  background: ${(props) => props.$bg || '#f5f5f5'};
  color: ${(props) => props.$color || '#595959'};
`;

const STATUS_BADGE_COLOR = {
  ACTIVE: { bg: '#edf5f1', color: '#2d5a43' },
  SUSPENDED: { bg: '#fff1f0', color: '#cf1322' },
  WITHDRAWN: { bg: '#f5f5f5', color: '#8c8c8c' },
  PENDING: { bg: '#fffbe6', color: '#ad6800' },
};

const Section = styled.section`
  h4 {
    margin: 0 0 10px;
    font-size: 13px;
    font-weight: 700;
    color: #262626;
  }
`;

const InfoGrid = styled.dl`
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;

  dt {
    color: #8c8c8c;
  }

  dd {
    margin: 0;
    color: #262626;
    word-break: break-all;
  }
`;

const RegionItem = styled.li`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #262626;
`;

const RegionList = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const Message = styled.div`
  padding: 40px 0;
  text-align: center;
  color: #8c8c8c;
  font-size: 14px;
`;

const formatDateTime = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

function MemberDetailModal({ accountId, onClose }) {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let isMounted = true;

    const fetchDetail = async () => {
      try {
        const response = await memberApi.getMemberDetail(accountId);
        if (isMounted && response.data?.success) {
          setDetail(response.data.data);
        }
      } catch (err) {
        console.error('회원 상세 조회 실패:', err);
        if (isMounted) {
          setError(
            err.response?.data?.error?.message ||
              '회원 정보를 불러오지 못했어요.',
          );
        }
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    fetchDetail();

    return () => {
      isMounted = false;
    };
  }, [accountId]);

  // ESC 로 닫기
  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const owner = detail?.ownerInfo;
  // 회원 상세 API 는 사장 정보를 일부만 채워 주므로, 값이 있는 항목만 보여준다
  const ownerRows = owner
    ? [
        ['상호명', owner.storeName],
        ['대표자', owner.ownerName],
        ['사업자번호', owner.businessNumber],
        ['연락처', owner.phone],
        ['사업장 주소', owner.storeAddress],
        [
          '입점 심사',
          owner.approvalStatus
            ? `${
                STORE_APPROVAL_STATUS_LABEL[owner.approvalStatus] ||
                owner.approvalStatus
              }${owner.rejectionReason ? ` (사유: ${owner.rejectionReason})` : ''}`
            : '',
        ],
        [
          '심사 요청일',
          owner.reviewRequestedAt && formatDateTime(owner.reviewRequestedAt),
        ],
        [
          '상점 상태',
          owner.storeStatus &&
            (STORE_STATUS_LABEL[owner.storeStatus] || owner.storeStatus),
        ],
      ].filter(([, value]) => Boolean(value))
    : [];
  const statusColor = STATUS_BADGE_COLOR[detail?.status] || {};

  return (
    <Overlay onClick={onClose}>
      <ModalBox onClick={(e) => e.stopPropagation()}>
        <CloseButton
          type="button"
          onClick={onClose}
        >
          <X size={20} />
        </CloseButton>

        {loading ? (
          <Message>불러오는 중...</Message>
        ) : error || !detail ? (
          <Message>{error || '회원 정보를 찾을 수 없어요.'}</Message>
        ) : (
          <>
            <Header>
              <div className="avatar">
                {detail.profileImageUrl ? (
                  <img
                    src={detail.profileImageUrl}
                    alt={detail.name}
                  />
                ) : (
                  (detail.name || detail.nickname || 'U').charAt(0)
                )}
              </div>
              <div>
                <h3>
                  {detail.name || '이름 없음'}
                  {detail.nickname ? ` (${detail.nickname})` : ''}
                </h3>
                <div className="badges">
                  <Badge>{MEMBER_ROLE_LABEL[detail.role] || detail.role}</Badge>
                  <Badge
                    $bg={statusColor.bg}
                    $color={statusColor.color}
                  >
                    {MEMBER_STATUS_LABEL[detail.status] || detail.status}
                  </Badge>
                </div>
              </div>
            </Header>

            <Section>
              <h4>기본 정보</h4>
              <InfoGrid>
                <dt>회원 ID</dt>
                <dd>{detail.accountId}</dd>
                <dt>이메일</dt>
                <dd>
                  {detail.email}
                  {detail.emailVerified === false && ' (미인증)'}
                </dd>
                <dt>가입 경로</dt>
                <dd>
                  {MEMBER_PROVIDER_LABEL[detail.provider] ||
                    detail.provider ||
                    '-'}
                </dd>
                <dt>가입일</dt>
                <dd>{formatDateTime(detail.createdAt)}</dd>
                {detail.deletedAt && (
                  <>
                    <dt>탈퇴일</dt>
                    <dd>{formatDateTime(detail.deletedAt)}</dd>
                  </>
                )}
              </InfoGrid>
            </Section>

            <Section>
              <h4>활동 지역</h4>
              {detail.regions?.length > 0 ? (
                <RegionList>
                  {detail.regions.map((region) => (
                    <RegionItem key={region.accountRegionId}>
                      {[region.siDo, region.gunGu, region.dong]
                        .filter(Boolean)
                        .join(' ')}
                      {region.isPrimary && <Badge>대표</Badge>}
                      {region.verified ? (
                        <Badge
                          $bg="#edf5f1"
                          $color="#2d5a43"
                        >
                          동네 인증
                        </Badge>
                      ) : (
                        <Badge>미인증</Badge>
                      )}
                    </RegionItem>
                  ))}
                </RegionList>
              ) : (
                <Message style={{ padding: '8px 0', textAlign: 'left' }}>
                  등록된 활동 지역이 없어요.
                </Message>
              )}
            </Section>

            {ownerRows.length > 0 && (
              <Section>
                <h4>사장 정보</h4>
                <InfoGrid>
                  {ownerRows.map(([label, value]) => (
                    <Fragment key={label}>
                      <dt>{label}</dt>
                      <dd>{value}</dd>
                    </Fragment>
                  ))}
                </InfoGrid>
              </Section>
            )}
          </>
        )}
      </ModalBox>
    </Overlay>
  );
}

export default MemberDetailModal;
