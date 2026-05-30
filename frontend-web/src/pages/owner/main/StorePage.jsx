import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import StoreProfileCard from '../../../components/owner/store/StoreProfileCard';
import StoreNoticeCard from '../../../components/owner/store/StoreNoticeCard';
import StoreInfoForm from '../../../components/owner/store/StoreInfoForm';
import StoreImageModal from '../../../components/owner/store/StoreImageModal';
import { storeApi } from '../../../api/owner/storeApi';

const PageContainer = styled.div`
  padding: 24px;
  background: #f8f9fa;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const MainContent = styled.div`
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 24px;
  align-items: start;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const LeftSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

function StorePage() {
  const [storeInfo, setStoreInfo] = useState(null);
  const [notices, setNotices] = useState([]);
  const [storeImages, setStoreImages] = useState([]);
  const [thumbnailUrl, setThumbnailUrl] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);

  // thumbnail: true인 이미지의 imageUrl을 가져옴
  const currentThumbnailUrl =
    storeImages.find((img) => img.thumbnail)?.imageUrl || null;

  // 💡 데이터 변환 헬퍼 2: UI용 요일 배열을 백엔드 전송용 문자열로 규격화
  const stringifyOperatingHours = (hoursArray) => {
    if (!hoursArray || hoursArray.length === 0) return '월~토 09:00~19:00';
    // 수정 폼에서 작업한 요일 데이터를 간소화하여 문자열로 표현 (백엔드 스펙 매칭)
    const openDays = hoursArray.filter((h) => !h.isHoliday).map((h) => h.day);
    if (openDays.length === 0) return '연중휴무';

    const startHour = hoursArray[0].start || '09:00';
    const endHour = hoursArray[0].end || '19:00';

    if (openDays.length >= 2) {
      return `${openDays[0]}~${openDays[openDays.length - 1]} ${startHour}~${endHour}`;
    }
    return `${openDays[0]} ${startHour}~${endHour}`;
  };

  // 상점 기본 정보 조회
  const fetchStoreInfo = () => {
    storeApi
      .getStoreInfo()
      .then((res) => {
        if (res.success) {
          const rawData = res.data;
          const mappedStatus =
            rawData.status === 'OPEN' ? 'OPERATING' : rawData.status;
          setStoreInfo({
            ...rawData,
            category: rawData.categoryName || '반찬/가정식',
            status: mappedStatus,
          });
        }
      })
      .catch((err) => console.error('상점 정보 로드 실패:', err));
  };

  // 공지 목록 조회
  const fetchNotices = () => {
    storeApi
      .getNotices()
      .then((res) => {
        if (res.success) setNotices(res.data || []);
      })
      .catch((err) => console.error(err));
  };

  // 이미지 목록 조회
  const fetchStoreImages = () => {
    storeApi
      .getImages()
      .then((res) => {
        if (res.success) {
          const imageList = res.data || [];
          setStoreImages(imageList);
          const currentThumbnail = imageList.find(
            (img) => img.thumbnail === true,
          );
          setThumbnailUrl(currentThumbnail ? currentThumbnail.imageUrl : '');
        }
      })
      .catch((err) => console.error('상점 이미지 로드 실패:', err));
  };

  useEffect(() => {
    fetchStoreInfo();
    fetchNotices();
    fetchStoreImages();
  }, []);

  // 영업 상태 변경
  const handleStatusChange = (newStatus) => {
    const serverStatus = newStatus === 'OPERATING' ? 'OPEN' : newStatus;

    storeApi
      .updateStatus(serverStatus)
      .then((res) => {
        if (res.success)
          setStoreInfo((prev) => ({ ...prev, status: newStatus }));
      })
      .catch((err) => {
        console.error('영업 상태 변경 실패:', err);
        setStoreInfo((prev) => ({ ...prev, status: newStatus }));
      });
  };

  // 상점 상세 정보 수정
  const handleUpdateStoreInfo = (updatedInfo) => {
    // 백엔드 PATCH 스펙에 맞게 데이터 가공 및 DTO 형태 빌드
    const requestBody = {
      name: updatedInfo.name,
      address: updatedInfo.address,
      phone: updatedInfo.phone,
      description: updatedInfo.description,
      // 요일 배열 데이터를 백엔드가 요구하는 "월~토 09:00~19:00" 형태 문자열로 직렬화
      businessHours: stringifyOperatingHours(updatedInfo.operatingHours),
      categoryId: updatedInfo.categoryId || 1, // 없으면 기본값 보정
    };

    storeApi
      .updateStoreInfo(requestBody)
      .then((res) => {
        if (res.success) {
          alert('🎉 상점 정보가 성공적으로 서버에 저장되었습니다!');
          fetchStoreInfo();
        } else {
          alert(`저장 실패: ${res.message}`);
        }
      })
      .catch((err) => {
        console.error('상점 정보 수정 중 오류 발생:', err);
        alert('서버 통신 중 오류가 발생했습니다. 다시 시도해 주세요.');
      });
  };

  // 공지 등록
  const handleAddNotice = (type, content) => {
    const titleMap = {
      CLOSED_TODAY: '오늘 휴무 안내',
      SOLD_OUT: '재료 소진 안내',
      NORMAL: '새로운 공지사항',
    };

    const noticeTitle = titleMap[type] || '새로운 공지사항';

    const requestBody = {
      title: noticeTitle,
      content: content,
      noticeType: type,
      pinned: true,
    };

    console.log(
      '서버로 보내는 최종 데이터:',
      JSON.stringify(requestBody, null, 2),
    );

    storeApi
      .addNotice(requestBody)
      .then((res) => {
        if (res.success) {
          alert('공지가 정상적으로 등록되었습니다.');
          console.log('등록된 공지 데이터:', res.data);
          fetchNotices();
        }
      })
      .catch((err) => console.error('공지 등록 실패:', err));
  };

  // 공지 수정
  const handleUpdateNotice = (noticeId, updatedData) => {
    storeApi
      .updateNotice(noticeId, updatedData)
      .then((res) => {
        if (res.success) {
          alert('공지사항이 성공적으로 수정되었습니다! ✨');
          fetchNotices(); // 최신 목록 새로고침
        } else {
          alert(`수정 실패: ${res.message}`);
        }
      })
      .catch((err) => {
        console.error('공지사항 수정 오류:', err);
        alert('통신 중 오류가 발생했습니다.');
      });
  };

  // 공지 삭제
  const handleDeleteNotice = (noticeId) => {
    if (!window.confirm('선택한 공지사항을 삭제하시겠습니까?')) return;

    storeApi
      .deleteNotice(noticeId)
      .then((res) => {
        if (res.success) {
          alert('공지가 정상적으로 삭제되었습니다.');
          fetchNotices();
        }
      })
      .catch((err) => console.error('공지 삭제 실패:', err));
  };

  // 대표 이미지 설정
  const handleSetThumbnail = (imageId) => {
    storeApi
      .setThumbnail(imageId)
      .then((res) => {
        if (res.success) {
          alert('상점 대표 이미지가 성공적으로 변경되었습니다! 🌟');
          fetchStoreImages();
        }
      })
      .catch((err) => console.error('대표 이미지 설정 실패:', err));
  };

  // 이미지 일괄 업로드
  const handleImageUpload = (fileList) => {
    if (!fileList || fileList.length === 0) return;

    const imageObjects = fileList.map((file) => ({
      imageUrl: URL.createObjectURL(file),
    }));

    storeApi
      .uploadImages({ images: imageObjects })
      .then((res) => {
        if (res.success) {
          alert(
            `${fileList.length}장의 사진이 성공적으로 일괄 등록되었습니다! 🎉`,
          );
          fetchStoreImages();
        }
      })
      .catch((err) => console.error('이미지 일괄 등록 실패:', err));
  };

  // 이미지 삭제
  const handleImageDelete = (imageId) => {
    if (!window.confirm('선택하신 사진을 상점 관리 목록에서 삭제하시겠습니까?'))
      return;

    storeApi
      .deleteImage(imageId)
      .then((res) => {
        if (res.success) {
          alert('사진이 성공적으로 삭제되었습니다! 🗑️');
          fetchStoreImages();
        }
      })
      .catch((err) => console.error('이미지 삭제 실패:', err));
  };

  // storeInfo가 아직 로드되지 않은 경우 로딩 메시지 표시
  if (!storeInfo) {
    return (
      <PageContainer style={{ justifyContent: 'center', alignItems: 'center' }}>
        <div style={{ fontSize: '15px', color: '#666', fontWeight: '600' }}>
          상점 데이터를 안전하게 불러오는 중입니다... ⏳
        </div>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <MainContent>
        <LeftSection>
          <StoreProfileCard
            storeInfo={storeInfo}
            thumbnailUrl={currentThumbnailUrl}
            onOpenImageModal={() => setIsModalOpen(true)}
            onStatusChange={handleStatusChange}
          />
          <StoreNoticeCard
            notices={notices}
            onAddNotice={handleAddNotice}
            onUpdateNotice={handleUpdateNotice}
            onDeleteNotice={handleDeleteNotice}
          />
        </LeftSection>

        <StoreInfoForm storeInfo={storeInfo} onSave={handleUpdateStoreInfo} />

        <StoreImageModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          images={storeImages}
          onUpload={handleImageUpload}
          onDelete={handleImageDelete}
          onSetThumbnail={handleSetThumbnail}
        />
      </MainContent>
    </PageContainer>
  );
}

export default StorePage;
