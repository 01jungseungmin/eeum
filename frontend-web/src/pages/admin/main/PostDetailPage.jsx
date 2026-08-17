import React from 'react';
import styled from 'styled-components';
import {
  ArrowLeft,
  Eye,
  MessageSquare,
  ThumbsUp,
  HelpCircle,
} from 'lucide-react';

// ---------------- Styled Components ----------------
const PageWrapper = styled.div`
  width: 100%;
  min-height: 100vh;
  background-color: #f9fafb;
  padding: 32px;
  box-sizing: border-box;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue',
    Arial, sans-serif;
  position: relative;
`;

const TopHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
`;

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`;

const BackButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #111827;
  border-radius: 8px;
  transition: background-color 0.15s;

  &:hover {
    background-color: #e5e7eb;
  }
`;

const TitleArea = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const PageTitle = styled.h1`
  font-size: 24px;
  font-weight: 800;
  color: #111827;
  margin: 0;
`;

const PageSubtitle = styled.span`
  font-size: 14px;
  color: #6b7280;
`;

const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const ActionButton = styled.button`
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #dc2626;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background-color: #fef2f2;
    border-color: #fca5a5;
  }
`;

const ContentLayout = styled.div`
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 24px;
  align-items: start;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

const MainCard = styled.div`
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
`;

const BadgeGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
`;

const CategoryBadge = styled.span`
  padding: 4px 10px;
  background-color: #f3f4f6;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
`;

const StatusBadge = styled.span`
  padding: 4px 10px;
  background-color: ${(props) =>
    props.$status === '신고됨' ? '#FEE2E2' : '#DCFCE7'};
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
  color: ${(props) => (props.$status === '신고됨' ? '#DC2626' : '#16A34A')};
`;

const PostTitle = styled.h2`
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 12px 0;
  line-height: 1.4;
`;

const MetricsGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 24px;
`;

const MetricItem = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;

  svg {
    color: #9ca3af;
  }
`;

const PostBody = styled.div`
  font-size: 15px;
  line-height: 1.6;
  color: #374151;
  min-height: 180px;
  padding-top: 12px;
`;

const AuthorCard = styled.div`
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
`;

const AuthorCardHeader = styled.div`
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 28px;
`;

const AuthorProfile = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding-bottom: 12px;
`;

const Avatar = styled.div`
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background-color: #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: #6b7280;
  margin-bottom: 16px;
`;

const AuthorName = styled.div`
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 4px;
`;

const AuthorEmail = styled.div`
  font-size: 14px;
  color: #6b7280;
`;

const FloatingHelpButton = styled.button`
  position: fixed;
  bottom: 24px;
  right: 24px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #1f2937;
  color: #ffffff;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition:
    transform 0.2s,
    background-color 0.2s;

  &:hover {
    background-color: #111827;
    transform: scale(1.05);
  }
`;

// ---------------- Component ----------------
const PostDetailPage = ({ post, onBack }) => {
  if (!post) return null;

  const handleHidePost = () => {
    alert(`게시글 #${post.id}이(가) 숨김 처리되었습니다.`);
  };

  const handleDeletePost = () => {
    if (window.confirm('정말로 이 게시글을 삭제하시겠습니까?')) {
      alert(`게시글 #${post.id}이(가) 삭제되었습니다.`);
      onBack();
    }
  };

  return (
    <PageWrapper>
      <TopHeader>
        <HeaderLeft>
          <BackButton onClick={onBack}>
            <ArrowLeft size={20} />
          </BackButton>
          <TitleArea>
            <PageTitle>게시글 상세</PageTitle>
            <PageSubtitle>게시글 #{post.id}</PageSubtitle>
          </TitleArea>
        </HeaderLeft>

        <HeaderRight>
          <ActionButton onClick={handleHidePost}>게시글 숨김</ActionButton>
          <ActionButton onClick={handleDeletePost}>삭제</ActionButton>
        </HeaderRight>
      </TopHeader>

      <ContentLayout>
        <MainCard>
          <BadgeGroup>
            <CategoryBadge>{post.category}</CategoryBadge>
            <StatusBadge $status={post.status}>{post.status}</StatusBadge>
          </BadgeGroup>

          <PostTitle>{post.title}</PostTitle>

          <MetricsGroup>
            <MetricItem>
              <Eye size={16} />
              <span>{post.views}</span>
            </MetricItem>
            <MetricItem>
              <MessageSquare size={16} />
              <span>{post.comments}</span>
            </MetricItem>
            <MetricItem>
              <ThumbsUp size={16} />
              <span>{post.likes}</span>
            </MetricItem>
          </MetricsGroup>

          <PostBody>{post.content}</PostBody>
        </MainCard>

        <AuthorCard>
          <AuthorCardHeader>작성자 정보</AuthorCardHeader>
          <AuthorProfile>
            <Avatar>{post.author ? post.author[0] : '무'}</Avatar>
            <AuthorName>{post.author}</AuthorName>
            <AuthorEmail>{post.authorEmail || 'email@domain.com'}</AuthorEmail>
          </AuthorProfile>
        </AuthorCard>
      </ContentLayout>

      <FloatingHelpButton aria-label="도움말">
        <HelpCircle size={20} />
      </FloatingHelpButton>
    </PageWrapper>
  );
};

export default PostDetailPage;
