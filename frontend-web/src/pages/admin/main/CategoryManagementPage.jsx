import React, { useState } from 'react';
import styled from 'styled-components';
import CategoryCard from '../../../components/admin/category/CategoryCard';
import AutoFilterSection from '../../../components/admin/category/AutoFilterSection';

const Container = styled.div`
  width: 100%;
  padding: 32px;
  background-color: #f8fafc;
  min-height: 100vh;
  box-sizing: border-box;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const CardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 24px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

// --- 초기 데이터 ---
const initialCategories = {
  store: [
    { id: 1, name: '음식점', count: '1,842건', active: true },
    { id: 2, name: '카페/디저트', count: '963건', active: true },
    { id: 3, name: '편의시설', count: '745건', active: true },
  ],
  board: [
    { id: 1, name: '공구/기획', count: '1,643건', active: true, isHot: true },
    { id: 2, name: '맛집', count: '965건', active: true },
    { id: 3, name: '일반', count: '2,104건', active: true },
  ],
  market: [
    { id: 1, name: '전자기기', count: '234건', active: true },
    { id: 2, name: '가구/인테리어', count: '189건', active: true },
    {
      id: 3,
      name: '의류/잡화',
      count: '456건',
      active: true,
      isSelected: true,
    },
  ],
  report: [
    { id: 1, name: '사기/허위정보', count: '283건', active: true, isHot: true },
    { id: 2, name: '욕설/비방', count: '432건', active: true },
    { id: 3, name: '스팸/광고', count: '1,063건', active: true },
  ],
};

export default function CategoryManagementPage() {
  const [categories, setCategories] = useState(initialCategories);

  const handleToggle = (section, id) => {
    setCategories((prev) => ({
      ...prev,
      [section]: prev[section].map((item) =>
        item.id === id ? { ...item, active: !item.active } : item,
      ),
    }));
  };

  return (
    <Container>
      <CardGrid>
        <CategoryCard
          title="가게 카테고리"
          items={categories.store}
          onToggle={(id) => handleToggle('store', id)}
        />
        <CategoryCard
          title="커뮤니티 게시판"
          items={categories.board}
          onToggle={(id) => handleToggle('board', id)}
        />
        <CategoryCard
          title="중고거래 카테고리"
          items={categories.market}
          onToggle={(id) => handleToggle('market', id)}
        />
        <CategoryCard
          title="신고 사유"
          items={categories.report}
          onToggle={(id) => handleToggle('report', id)}
        />
      </CardGrid>

      <AutoFilterSection />
    </Container>
  );
}
