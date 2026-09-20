import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { ArrowLeft } from 'lucide-react';
import CommunityPostProfileCard from '../../../components/admin/community/CommunityPostProfileCard';
import CommunityPostDetailPanel from '../../../components/admin/community/CommunityPostDetailPanel';
import { communityPostApi } from '../../../api/admin/communityPostApi';

const DetailContainer = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const DetailHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;

  .btn-back {
    background: white;
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #595959;
    transition: background 0.2s;
    &:hover {
      background: #f5f5f5;
    }
  }

  .title-side {
    h1 {
      margin: 0 0 6px 0;
      font-size: 22px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 0;
      font-size: 13px;
      color: #8c8c8c;
    }
  }
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  align-items: start;
`;

function CommunityPostDetailPage() {
  const { postId } = useParams();
  const navigate = useNavigate();

  const [post, setPost] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchDetail = useCallback(async () => {
    try {
      setIsLoading(true);
      const res = await communityPostApi.getPostDetail(postId);
      if (res.data?.success) {
        setPost(res.data.data);
      } else {
        alert('상세 정보를 불러올 수 없습니다.');
        navigate('/admin/community/posts');
      }
    } catch (error) {
      console.error('커뮤니티 게시글 상세 조회 실패:', error);
      alert('데이터 로드 실패로 목록으로 이동합니다.');
      navigate('/admin/community/posts');
    } finally {
      setIsLoading(false);
    }
  }, [postId, navigate]);

  useEffect(() => {
    if (postId) queueMicrotask(() => fetchDetail());
  }, [postId, fetchDetail]);

  const handleHide = async () => {
    if (!window.confirm(`[${post.title}] 게시글을 숨기시겠습니까?`)) return;
    try {
      await communityPostApi.hidePost(postId);
      alert('게시글이 숨김 처리되었습니다.');
      fetchDetail();
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '숨김 처리 중 오류가 발생했습니다.',
      );
    }
  };

  const handleShow = async () => {
    if (!window.confirm(`[${post.title}] 게시글의 숨김을 해제하시겠습니까?`))
      return;
    try {
      await communityPostApi.showPost(postId);
      alert('게시글 숨김이 해제되었습니다.');
      fetchDetail();
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '숨김 해제 처리 중 오류가 발생했습니다.',
      );
    }
  };

  if (isLoading)
    return (
      <DetailContainer style={{ textAlign: 'center', padding: '100px' }}>
        데이터 조회 중...
      </DetailContainer>
    );
  if (!post) return null;

  return (
    <DetailContainer>
      <DetailHeader>
        <div
          className="btn-back"
          onClick={() => navigate('/admin/community/posts')}
        >
          <ArrowLeft size={18} />
        </div>
        <div className="title-side">
          <h1>{post.title}</h1>
          <p>{post.categoryName}</p>
        </div>
      </DetailHeader>

      <MainGrid>
        <CommunityPostProfileCard
          post={post}
          onHide={handleHide}
          onShow={handleShow}
        />
        <CommunityPostDetailPanel post={post} />
      </MainGrid>
    </DetailContainer>
  );
}

export default CommunityPostDetailPage;
