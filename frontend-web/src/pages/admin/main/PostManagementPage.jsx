import React, { useState } from 'react';
import styled from 'styled-components';
import StatCards from '../../../components/admin/post/StatCards';
import PostList from '../../../components/admin/post/PostList';
import PostDetailPage from './PostDetailPage';

const PageWrapper = styled.div`
  width: 100%;
  min-height: 100vh;
  background-color: #f9fafb;
  padding: 32px;
  box-sizing: border-box;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue',
    Arial, sans-serif;
`;

export default function PostManagementPage() {
  // 선택된 게시글 상태 (null이면 리스트 표시, 객체 존재 시 상세 화면 표시)
  const [selectedPost, setSelectedPost] = useState(null);

  // 게시글이 선택된 경우 -> 상세 페이지 렌더링
  if (selectedPost) {
    return (
      <PostDetailPage
        post={selectedPost}
        onBack={() => setSelectedPost(null)}
      />
    );
  }

  // 기본 상태 -> 대시보드 및 리스트 페이지 렌더링
  return (
    <PageWrapper>
      {/* 상단 현황 카드리스트 */}
      <StatCards />

      {/* 게시글 목록 (선택 시 selectedPost 상태 업데이트) */}
      <PostList onSelectPost={(post) => setSelectedPost(post)} />
    </PageWrapper>
  );
}
