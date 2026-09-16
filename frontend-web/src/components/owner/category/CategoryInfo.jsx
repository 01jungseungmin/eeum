import styled from 'styled-components';
import { Grid } from 'lucide-react';

const InfoNoticeCard = styled.div`
  background: #e6f7ed;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
  .icon-box {
    color: #00a651;
    margin-top: 2px;
  }
  .content {
    h3 {
      font-size: 13px;
      font-weight: bold;
      color: #1a1f2c;
      margin: 0 0 8px 0;
    }
    ul {
      margin: 0;
      padding-left: 14px;
      font-size: 12px;
      color: #495057;
      line-height: 1.8;
      li {
        list-style-type: disc;
      }
    }
  }
`;

function CategoryInfo() {
  return (
    <InfoNoticeCard>
      <div className="icon-box">
        <Grid size={16} strokeWidth={2.5} />
      </div>
      <div className="content">
        <h3>카테고리 관리 안내</h3>
        <ul>
          <li>
            카테고리 순서는 고객 앱에서 상품 분류 탭 순서와 동일하게 표시됩니다.
          </li>
          <li>상품이 등록된 카테고리는 삭제 전 상품을 이동시켜야 합니다.</li>
          <li>
            숨김 처리된 카테고리는 고객 앱에서 보이지 않지만 상품은 유지됩니다.
          </li>
        </ul>
      </div>
    </InfoNoticeCard>
  );
}

export default CategoryInfo;
