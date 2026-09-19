import styled from 'styled-components';
import { STORE_APPROVAL_STATUS_LABEL } from '../../../constants/storeConstants';

const RightDetailPanel = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  padding: 32px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 32px;

  h2 {
    margin: 0 0 20px 0;
    font-size: 16px;
    font-weight: 700;
    color: #262626;
  }

  .info-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px 40px;

    .field-block {
      .label {
        font-size: 12px;
        color: #8c8c8c;
        margin-bottom: 6px;
      }
      .value {
        font-size: 14px;
        color: #262626;
        font-weight: 500;
      }
    }
  }
`;

const Section = styled.div`
  &:not(:last-child) {
    padding-bottom: 32px;
    border-bottom: 1px solid #f0f0f0;
  }
`;

const HoursTable = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  td {
    padding: 8px 0;
    color: #262626;
  }
  td:first-child {
    color: #8c8c8c;
    width: 60px;
  }
`;

const ImageGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 12px;
`;

const ImageThumb = styled.img`
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
`;

const NoticeRow = styled.div`
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }

  .title {
    font-size: 14px;
    font-weight: 600;
    color: #262626;
    margin-bottom: 4px;
  }
  .content {
    font-size: 13px;
    color: #595959;
  }
`;

const EmptyText = styled.div`
  font-size: 13px;
  color: #bfbfbf;
`;

const formatTime = (time) => (time ? time.slice(0, 5) : '-');
const formatDate = (value) => {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '-';
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
};

function StoreDetailPanel({ store }) {
  return (
    <RightDetailPanel>
      <Section>
        <h2>사장 정보</h2>
        <div className="info-grid">
          <div className="field-block">
            <div className="label">이름</div>
            <div className="value">{store.ownerName || '-'}</div>
          </div>
          <div className="field-block">
            <div className="label">이메일</div>
            <div className="value">{store.ownerEmail || '-'}</div>
          </div>
          <div className="field-block">
            <div className="label">연락처</div>
            <div className="value">{store.ownerPhone || '-'}</div>
          </div>
          <div className="field-block">
            <div className="label">계정 상태</div>
            <div className="value">{store.ownerStatus || '-'}</div>
          </div>
          <div className="field-block">
            <div className="label">사업자등록번호</div>
            <div className="value">{store.businessNumber || '-'}</div>
          </div>
          <div className="field-block">
            <div className="label">개업일</div>
            <div className="value">{formatDate(store.openingDate)}</div>
          </div>
          <div className="field-block">
            <div className="label">입점 승인 상태</div>
            <div className="value">
              {STORE_APPROVAL_STATUS_LABEL[store.approvalStatus] ||
                store.approvalStatus ||
                '-'}
            </div>
          </div>
          {store.rejectionReason && (
            <div className="field-block">
              <div className="label">반려 사유</div>
              <div className="value">{store.rejectionReason}</div>
            </div>
          )}
        </div>
      </Section>

      <Section>
        <h2>정산 계좌</h2>
        {store.settlementAccount ? (
          <div className="info-grid">
            <div className="field-block">
              <div className="label">은행</div>
              <div className="value">{store.settlementAccount.bankName}</div>
            </div>
            <div className="field-block">
              <div className="label">계좌번호</div>
              <div className="value">
                {store.settlementAccount.accountNumber}
              </div>
            </div>
            <div className="field-block">
              <div className="label">예금주</div>
              <div className="value">
                {store.settlementAccount.accountHolder}
              </div>
            </div>
          </div>
        ) : (
          <EmptyText>등록된 정산 계좌가 없어요.</EmptyText>
        )}
      </Section>

      <Section>
        <h2>영업시간</h2>
        {store.businessHours?.length > 0 ? (
          <HoursTable>
            <tbody>
              {store.businessHours.map((hour) => (
                <tr key={hour.dayOfWeek}>
                  <td>{hour.dayLabel}</td>
                  <td>
                    {hour.closed
                      ? '휴무'
                      : `${formatTime(hour.openTime)} ~ ${formatTime(hour.closeTime)}`}
                  </td>
                </tr>
              ))}
            </tbody>
          </HoursTable>
        ) : (
          <EmptyText>등록된 영업시간이 없어요.</EmptyText>
        )}
      </Section>

      <Section>
        <h2>상점 이미지</h2>
        {store.images?.length > 0 ? (
          <ImageGrid>
            {store.images.map((img) => (
              <ImageThumb
                key={img.imageId}
                src={img.imageUrl}
                alt={store.name}
              />
            ))}
          </ImageGrid>
        ) : (
          <EmptyText>등록된 이미지가 없어요.</EmptyText>
        )}
      </Section>

      <Section>
        <h2>공지사항</h2>
        {store.notices?.length > 0 ? (
          store.notices.map((notice) => (
            <NoticeRow key={notice.noticeId}>
              <div className="title">
                {notice.pinned && '📌 '}
                {notice.title}
              </div>
              <div className="content">{notice.content}</div>
            </NoticeRow>
          ))
        ) : (
          <EmptyText>등록된 공지사항이 없어요.</EmptyText>
        )}
      </Section>
    </RightDetailPanel>
  );
}

export default StoreDetailPanel;
