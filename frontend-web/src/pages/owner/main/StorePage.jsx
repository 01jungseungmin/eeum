import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import StoreProfileCard from '../../../components/owner/store/StoreProfileCard';
import StoreNoticeCard from '../../../components/owner/store/StoreNoticeCard';
import StoreInfoForm from '../../../components/owner/store/StoreInfoForm';
import StoreImageModal from '../../../components/owner/store/StoreImageModal';
import StoreHoursForm from '../../../components/owner/store/StoreHoursForm';
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
  const [businessHours, setBusinessHours] = useState([]);

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

  // 영업시간 조회
  const fetchBusinessHours = () => {
    // 서버의 영어 요일을 UI용 한글 요일로 바꾸기 위한 사전
    const reverseDayMapping = {
      MONDAY: '월',
      TUESDAY: '화',
      WEDNESDAY: '수',
      THURSDAY: '목',
      FRIDAY: '금',
      SATURDAY: '토',
      SUNDAY: '일',
    };

    storeApi
      .getBusinessHours()
      .then((res) => {
        const responseData = res.data || res;

        if (responseData.success) {
          const rawHours = responseData.data || [];

          // 백엔드 데이터 포맷 -> 프론트엔드 UI 포맷으로 역매핑
          const mappedHours = rawHours.map((item) => {
            // 값이 존재할 때만 뒤의 초(:00)를 잘라내고, 없으면 기본값 세팅
            const startTime = item.openTime
              ? item.openTime.slice(0, 5)
              : '09:00';
            const endTime = item.closeTime
              ? item.closeTime.slice(0, 5)
              : '19:00';

            return {
              day: reverseDayMapping[item.dayOfWeek] || '월',
              start: startTime,
              end: endTime,
              isHoliday: item.closed,
            };
          });

          setBusinessHours(mappedHours);
        }
      })
      .catch((err) => {
        console.error('영업시간 조회 실패:', err);
      });
  };

  useEffect(() => {
    fetchStoreInfo();
    fetchNotices();
    fetchStoreImages();
    fetchBusinessHours();
  }, []);

  // 영업 상태 변경
  const handleStatusChange = (newStatus) => {
    const serverStatus = newStatus === 'OPERATING' ? 'OPEN' : newStatus;

    storeApi
      .updateStatus(serverStatus)
      .then((res) => {
        const responseData = res.data || res;
        if (responseData.success) {
          fetchStoreInfo();
        }
      })
      .catch((err) => {
        console.error('영업 상태 변경 실패:', err);
        setStoreInfo((prev) => ({ ...prev, status: newStatus }));
      });
  };

  // 상점 정보, 영업시간 수정을 한번에 처리
  const handleCombinedSave = (combinedData) => {
    // 요일 변환 매핑 사전
    const dayMapping = {
      월: 'MONDAY',
      화: 'TUESDAY',
      수: 'WEDNESDAY',
      목: 'THURSDAY',
      금: 'FRIDAY',
      토: 'SATURDAY',
      일: 'SUNDAY',
    };

    // 프론트엔드 데이터 -> 백엔드 규격으로 가공
    const formattedBusinessHours = combinedData.operatingHours.map((item) => ({
      dayOfWeek: dayMapping[item.day] || 'MONDAY',
      closed: item.isHoliday,
      openTime: item.start || '09:00',
      closeTime: item.end || '19:00',
    }));

    // A. 기본 정보 수정용 바디 빌드
    const storeInfoBody = {
      name: combinedData.name,
      address: combinedData.address,
      phone: combinedData.phone,
      description: combinedData.description,
      businessHours: stringifyOperatingHours(combinedData.operatingHours), // 기존 문자열 변환용
      categoryId: combinedData.categoryId || 1,
    };

    // B. 영업 시간 수정용 바디 빌드
    const hoursBody = {
      businessHours: formattedBusinessHours,
    };

    // 두 API 호출 및 처리
    Promise.all([
      storeApi.updateStoreInfo(storeInfoBody),
      storeApi.updateBusinessHours(hoursBody),
    ])
      .then(([infoRes, hoursRes]) => {
        const infoSuccess = infoRes.data?.success || infoRes.success;
        const hoursSuccess = hoursRes.data?.success || hoursRes.success;

        if (infoSuccess && hoursSuccess) {
          alert('🎉 상점 정보와 영업시간이 모두 성공적으로 저장되었습니다!');
          fetchStoreInfo();
          fetchBusinessHours();
        } else {
          alert('일부 정보 저장에 실패했습니다.');
        }
      })
      .catch((err) => {
        console.error('상점 정보 일괄 수정 중 오류 발생:', err);
        alert('서버 통신 중 오류가 발생했습니다.');
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

  // 영업시간 수정
  const handleUpdateHours = async (hoursArray) => {
    try {
      // 💡 API 명세에 따라 { "businessHours": [요일별 객체들] } 구조로 만듭니다.
      const requestBody = {
        businessHours: hoursArray,
      };

      const response = await storeApi.updateBusinessHours(requestBody);

      if (response.data?.success) {
        alert('영업시간이 성공적으로 수정되었습니다.');
        fetchAllData(); // 데이터 재조회로 화면 갱신
      }
    } catch (err) {
      console.error('영업시간 수정 실패:', err);
      alert('영업시간 수정에 실패했습니다.');
    }
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

  // 상점 정보가 없거나, 영업시간 배열이 아직 텅 비어있다면 로딩바를 띄웁니다.
  if (!storeInfo || !businessHours || businessHours.length === 0) {
    return (
      <PageContainer style={{ justifyContent: 'center', alignItems: 'center' }}>
        <div style={{ fontSize: '15px', color: '#666', fontWeight: '600' }}>
          상점 데이터와 영업시간을 안전하게 불러오는 중입니다... ⏳
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

        <StoreInfoForm
          storeInfo={{
            ...storeInfo,
            operatingHours: businessHours,
          }}
          onSave={handleCombinedSave}
        />

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
