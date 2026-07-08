import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import SummaryCards from '../../../components/owner/inquiry/SummaryCards';
import FilterBar from '../../../components/owner/inquiry/FilterBar';
import InquiryList from '../../../components/owner/inquiry/InquiryList';
import { inquiryApi } from '../../../api/owner/inquiryApi';
import { chatApi } from '../../../api/owner/chatApi';

const Container = styled.div`
  flex: 1;
  padding: 20px;
  background-color: #f8f9fa;
  min-height: 100vh;
  font-family: 'Noto Sans KR', sans-serif;
`;

// 💡 1. 채팅방 개설 유도용 상단 배너 스타일 추가
const ChatBanner = styled.div`
  background-color: #eafaf1; /* 브랜드 그린 연한 배경 톤 */
  border: 1px solid #42a574;
  border-radius: 8px;
  padding: 16px 20px;
  margin-top: 15px;
  margin-bottom: 5px;
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const BannerText = styled.div`
  h4 {
    margin: 0 0 4px 0;
    color: #333;
    font-size: 15px;
    font-weight: bold;
  }
  p {
    margin: 0;
    color: #666;
    font-size: 13px;
  }
`;

const CreateChatButton = styled.button`
  background-color: #42a574;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background-color: #35845d;
  }
`;

const LoadingText = styled.div`
  text-align: center;
  padding: 40px;
  font-size: 16px;
  color: #666;
`;

const STATUS_MAP = { PENDING: '미답변', ANSWERED: '답변완료' };
const CATEGORY_MAP = {
  STORE: '상품 문의',
  ORDER: '주문 문의',
  RESERVATION: '예약 문의',
  PAYMENT: '결제 문의',
  ETC: '기타',
};

export default function InquiryManagement() {
  const [inquiries, setInquiries] = useState([]);
  const [loading, setLoading] = useState(true);

  // 채팅방 존재 여부 및 개설 상태 관리 State 추가
  const [myRoomId, setMyRoomId] = useState(() => {
    return localStorage.getItem('my_shop_room_id') || null;
  });

  // 필터 상태 관리
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('전체');
  const [typeFilter, setTypeFilter] = useState('전체 유형');

  // 문의 내역 로드 함수
  const loadInquiries = async () => {
    try {
      const res = await inquiryApi.getStoreInquiries({ page: 0, size: 50 });
      if (res.data.success && res.data.data.content) {
        setInquiries(res.data.data.content);
      }
    } catch (error) {
      console.error('문의 목록 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInquiries();
  }, []);

  // 사장님 대표 단톡방 개설 핸들러 함수
  const handleCreateShopChat = async () => {
    try {
      const requestBody = {
        name: '맛있는 반찬가게 사장님 단톡방',
        type: 'GROUP',
        refType: 'NONE',
        refId: 0,
        participantAccountIds: [], // 필요한 초기 참여자 ID 목록이 있다면 추가
      };

      const res = await chatApi.createGroupChat(requestBody);

      // 백엔드에서 제공한 success 응답 및 roomId 바인딩
      if (res.data.success && res.data.data.roomId !== undefined) {
        const newRoomId = res.data.data.roomId;
        setMyRoomId(newRoomId);

        // 룸 ID를 브라우저나 전역 상태에 저장하여 다음 진입 시 배너가 안 뜨게 처리
        localStorage.setItem('my_shop_room_id', newRoomId);
        alert(
          '🎉 상점 대표 실시간 채팅방이 성공적으로 개설되었습니다! 왼쪽 [채팅] 메뉴에서 확인하세요.',
        );
      }
    } catch (error) {
      console.error('채팅방 생성 실패:', error);
      alert('채팅방 생성 중 오류가 발생했습니다.');
    }
  };

  // 백엔드 데이터를 기반으로 동적 필터링을 수행하는 로직
  const filteredInquiries = inquiries.filter((item) => {
    const mappedStatus = STATUS_MAP[item.status] || '미답변';
    const matchesStatus =
      statusFilter === '전체' || mappedStatus === statusFilter;
    const mappedType = CATEGORY_MAP[item.category] || '기타';
    const matchesType = typeFilter === '전체 유형' || mappedType === typeFilter;
    const matchesSearch =
      item.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.writerName.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesStatus && matchesType && matchesSearch;
  });

  const counts = {
    total: inquiries.length,
    pending: inquiries.filter((i) => i.status === 'PENDING').length,
    completed: inquiries.filter((i) => i.status !== 'PENDING').length,
  };

  return (
    <Container>
      {/* 요약 카드 대시보드 */}
      <SummaryCards
        total={counts.total}
        pending={counts.pending}
        completed={counts.completed}
        currentStatus={statusFilter}
        setStatusFilter={setStatusFilter}
      />

      {/* 💡 4. 채팅방이 아직 개설되지 않았을 때만 상단 안내 배너 노출 */}
      {!myRoomId ? (
        <ChatBanner>
          <BannerText>
            <h4>💬 실시간 고객 통합 채팅방이 아직 없습니다</h4>
            <p>
              채팅방을 개설하시면 고객들과 웹소켓을 통해 실시간으로 1:1 및 그룹
              상담 관리가 가능해집니다.
            </p>
          </BannerText>
          <CreateChatButton onClick={handleCreateShopChat}>
            대표 채팅방 개설하기
          </CreateChatButton>
        </ChatBanner>
      ) : (
        <ChatBanner
          style={{ backgroundColor: '#f1f3f5', borderColor: '#ced4da' }}
        >
          <BannerText>
            <h4>✅ 상점 대표 실시간 채팅방 개설 완료 (Room ID: {myRoomId})</h4>
            <p>
              실시간 상담 업무 및 메시지 확인은 좌측 메뉴의 <b>[채팅]</b> 탭에서
              진행하실 수 있습니다.
            </p>
          </BannerText>
        </ChatBanner>
      )}

      {/* 필터 바 */}
      <FilterBar
        searchTerm={searchTerm}
        setSearchTerm={setSearchTerm}
        statusFilter={statusFilter}
        setStatusFilter={setStatusFilter}
        typeFilter={typeFilter}
        setTypeFilter={setTypeFilter}
        counts={counts}
      />

      {/* 리스트 본문 컨테이너 */}
      {loading ? (
        <LoadingText>문의 내역을 불러오는 중입니다...</LoadingText>
      ) : (
        <InquiryList data={filteredInquiries} onRefresh={loadInquiries} />
      )}
    </Container>
  );
}
