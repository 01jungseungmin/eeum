import React, { useRef } from 'react';
import styled from 'styled-components';
import { Plus, Trash2, X } from 'lucide-react';

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
`;

const ModalContainer = styled.div`
  background: white;
  border-radius: 20px;
  width: 500px;
  max-height: 80vh;
  padding: 24px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
`;

const Title = styled.h3`
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin: 0;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  color: #999;
  &:hover {
    color: #333;
  }
`;

const ImageGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  overflow-y: auto;
  padding-right: 4px;
`;

const UploadButton = styled.button`
  aspect-ratio: 1 / 1;
  border-radius: 12px;
  border: 2px dashed #e1e1e1;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #888;
  font-size: 12px;
  &:hover {
    background: #f5f5f5;
    border-color: #2d5a43;
    color: #2d5a43;
  }
`;

const ImageWrapper = styled.div`
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e8e8e8;
  background: #f8f9fa;
  cursor: pointer;
  &:hover .action-overlay {
    opacity: 1;
  }
`;

const ThumbnailBadge = styled.div`
  position: absolute;
  top: 6px;
  left: 6px;
  background: #2d5a43;
  color: white;
  font-size: 9px;
  font-weight: bold;
  padding: 2px 5px;
  border-radius: 4px;
  z-index: 2;
`;

const ActionOverlay = styled.div`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 6px;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 3;
`;

const DeleteButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  color: white;
  align-self: flex-end;
  &:hover {
    color: #ff6b6b;
  }
`;

const SetThumbnailButton = styled.button`
  width: 100%;
  background: white;
  border: none;
  border-radius: 4px;
  color: #2d5a43;
  font-size: 10px;
  font-weight: bold;
  padding: 4px 0;
  cursor: pointer;
`;

const FALLBACK_IMAGE =
  "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100'><rect width='100%' height='100%' fill='%23f4faf6'/></svg>";

function StoreImageModal({
  isOpen,
  onClose,
  images = [],
  onUpload,
  onDelete,
  onSetThumbnail,
}) {
  const fileInputRef = useRef(null);
  if (!isOpen) return null;

  // thumbnail이 true인 대표 이미지를 첫 번째 자리에 무조건 고정 배치
  const sortedImages = [...images].sort(
    (a, b) => (b.thumbnail ? 1 : 0) - (a.thumbnail ? 1 : 0),
  );

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files.length > 0) {
      const fileList = Array.from(e.target.files);
      if (images.length + fileList.length > 20) {
        alert('사진은 최대 20장까지만 등록 가능합니다.');
        return;
      }
      onUpload(fileList);
      e.target.value = '';
    }
  };

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <Header>
          <Title>🖼️ 상점 사진 목록 ({images.length}/20)</Title>
          <CloseButton onClick={onClose}>
            <X size={20} />
          </CloseButton>
        </Header>

        <ImageGrid>
          {images.length < 20 && (
            <>
              <UploadButton onClick={() => fileInputRef.current?.click()}>
                <Plus size={18} />
                <span>추가</span>
              </UploadButton>
              <input
                type="file"
                ref={fileInputRef}
                style={{ display: 'none' }}
                accept="image/*"
                multiple
                onChange={handleFileChange}
              />
            </>
          )}

          {sortedImages.map((img) => (
            <ImageWrapper key={img.imageId}>
              {img.thumbnail && <ThumbnailBadge>대표</ThumbnailBadge>}
              <img
                src={img.imageUrl}
                alt="상점"
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                onError={(e) => {
                  e.target.onerror = null;
                  e.target.src = FALLBACK_IMAGE;
                }}
              />

              <ActionOverlay className="action-overlay">
                <DeleteButton
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(img.imageId);
                  }}
                >
                  <Trash2 size={14} />
                </DeleteButton>
                {!img.thumbnail && (
                  <SetThumbnailButton
                    onClick={(e) => {
                      e.stopPropagation();
                      onSetThumbnail(img.imageId);
                    }}
                  >
                    대표 지정
                  </SetThumbnailButton>
                )}
              </ActionOverlay>
            </ImageWrapper>
          ))}
        </ImageGrid>
      </ModalContainer>
    </ModalOverlay>
  );
}

export default StoreImageModal;
