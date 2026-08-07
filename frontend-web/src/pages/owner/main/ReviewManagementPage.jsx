import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import ReviewStats from '../../../components/owner/review/ReviewStats';
import ReviewFilterBar from '../../../components/owner/review/ReviewFilterBar';
import ReviewList from '../../../components/owner/review/ReviewList';
import ReportModal from '../../../components/owner/review/ReportModal';
import { reviewApi } from '../../../api/owner/reviewApi';

const Container = styled.div`
  padding: 24px;
  background-color: #f9fafb;
  min-height: 100vh;
  margin: 0 auto;
`;

export default function ReviewManagementPage() {
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);

  const [statusFilter, setStatusFilter] = useState('ALL');
  const [ratingFilter, setRatingFilter] = useState('ALL');

  const [selectedReviewId, setSelectedReviewId] = useState(null);
  const [isReportModalOpen, setIsReportModalOpen] = useState(false);

  // [핵심 로직] 목록 호출 후 각 아이템별로 상세 내역을 가져와 결합하는 함수
  const loadReviewsData = async () => {
    try {
      setLoading(true);

      // 전체 리뷰 목록 호출
      const listRes = await reviewApi.getStoreReviews(0, 50);

      if (listRes.data && listRes.data.success && listRes.data.data) {
        const listContent = listRes.data.data.content || [];

        // 목록에 있는 모든 리뷰의 상세 정보(reply 포함)를 병렬로 다시 가져오기
        const detailPromises = listContent.map(async (item) => {
          try {
            const detailRes = await reviewApi.getReviewDetail(
              item.storereviewId,
            );
            if (detailRes.data && detailRes.data.success) {
              // 답글(reply) 데이터가 포함된 온전한 단건 객체 반환
              return detailRes.data.data;
            }
            return item;
          } catch (error) {
            console.error(
              `${item.storereviewId}번 상세 데이터 로드 실패:`,
              error,
            );
            return item; // 실패 시 기본 목록 정보 유지
          }
        });

        // 모든 상세 조회가 완료될 때까지 대기
        const compiledReviews = await Promise.all(detailPromises);
        setReviews(compiledReviews); // 완성된 데이터 배열을 세팅하여 답글 노출 보장
      } else {
        setReviews([]);
      }
    } catch (error) {
      console.error('리뷰 데이터를 불러오는 중 오류 발생:', error);
      setReviews([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReviewsData();
  }, []);

  // 답글 등록
  const handleCreateReply = async (reviewId, content) => {
    try {
      const response = await reviewApi.createReply(reviewId, content);
      if (response.data && response.data.success) {
        alert('답글이 성공적으로 등록되었습니다.');
        await loadReviewsData();
      }
    } catch (error) {
      console.error(error);
      alert('답글 등록에 실패했습니다.');
    }
  };

  // 답글 수정
  const handleUpdateReply = async (reviewId, content) => {
    // 들어오는 데이터가 비어있는지 사전에 로깅 및 차단
    if (!reviewId || !content) {
      console.error('수정 요청 파라미터가 유효하지 않습니다:', {
        reviewId,
        content,
      });
      alert('수정할 내용이 올바르게 전달되지 않았습니다.');
      return;
    }

    try {
      const response = await reviewApi.updateReply(reviewId, content);
      if (response.data && response.data.success) {
        alert('답글이 수정되었습니다.');
        await loadReviewsData();
      } else {
        alert(`수정 실패: ${response.data.message || '알 수 없는 서버 오류'}`);
      }
    } catch (error) {
      console.error(
        '답글 수정 API 요청 실패 원인:',
        error.response?.data || error.message,
      );
      alert('답글 수정 중 오류가 발생했습니다.');
    }
  };

  // 답글 삭제
  const handleDeleteReply = async (reviewId) => {
    if (!window.confirm('답글을 삭제하시겠습니까?')) return;
    try {
      const response = await reviewApi.deleteReply(reviewId);
      if (response.data && response.data.success) {
        alert('답글이 삭제되었습니다.');
        await loadReviewsData();
      }
    } catch (error) {
      console.error(error);
      alert('답글 삭제에 실패했습니다.');
    }
  };

  const handleOpenReportModal = (reviewId) => {
    setSelectedReviewId(reviewId);
    setIsReportModalOpen(true);
  };

  // 대시보드 통계 계산용 변수
  const totalCount = reviews.length;
  const unansweredCount = reviews.filter((r) => !r.reply).length;
  const answeredCount = reviews.filter((r) => r.reply).length;

  // 상태 필터 및 별점 필터 다중 적용 필터링 처리
  const filteredReviews = reviews.filter((review) => {
    const matchesStatus =
      statusFilter === 'ALL' ||
      (statusFilter === 'UNANSWERED' && !review.reply) ||
      (statusFilter === 'ANSWERED' && !!review.reply);

    const matchesRating =
      ratingFilter === 'ALL' || Number(review.rating) === Number(ratingFilter);

    return matchesStatus && matchesRating;
  });

  if (loading) return <Container>리뷰 데이터 통합 동기화 중...</Container>;

  return (
    <Container>
      {/* 상세 조회가 보장된 데이터로 상단 통계판 자동 계산 */}
      <ReviewStats reviews={reviews} />

      {/* 탭 및 필터 */}
      <ReviewFilterBar
        currentStatus={statusFilter}
        onStatusChange={setStatusFilter}
        currentRating={ratingFilter}
        onRatingChange={setRatingFilter}
        totalCount={totalCount}
        unansweredCount={unansweredCount}
        answeredCount={answeredCount}
      />

      {/* 필터링된 리스트 컴포넌트 출력 */}
      <ReviewList
        reviews={filteredReviews}
        onCreateReply={handleCreateReply}
        onUpdateReply={handleUpdateReply}
        onDeleteReply={handleDeleteReply}
        onOpenReport={handleOpenReportModal}
      />

      {isReportModalOpen && (
        <ReportModal
          reviewId={selectedReviewId}
          onClose={() => setIsReportModalOpen(false)}
        />
      )}
    </Container>
  );
}
