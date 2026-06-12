import React from 'react';
import styled from 'styled-components';
import { ImagePlus, Plus, Trash2, Star } from 'lucide-react';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
`;

const UploaderRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
`;

const AddButtonCard = styled.label`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 72px;
  height: 72px;
  background: #ffffff;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
  &:hover {
    background: #f8f9fa;
    border-color: #94a3b8;
  }
  input {
    display: none;
  }
  span {
    font-size: 11px;
    color: #8e94a0;
  }
`;

const HoverOverlay = styled.div`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 11px;
  opacity: 0;
  transition: opacity 0.2s ease-in-out;
  z-index: 5;
`;

const ImageItemCard = styled.div`
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 12px;
  background: #f8f9fa;
  box-sizing: border-box;

  border: ${(props) =>
    props.$variant === 'product' && props.$isMain
      ? '1.5px solid #00a651'
      : '1px solid #eef0f2'};

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 11px;
  }

  &:hover ${HoverOverlay} {
    opacity: 1;
  }
`;

const TrashDeleteButton = styled.button`
  position: absolute;
  top: 6px;
  right: 6px;
  background: none;
  border: none;
  color: #ffffff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  &:hover {
    opacity: 0.8;
  }
`;

const MainAssignButton = styled.button`
  position: absolute;
  bottom: 6px;
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 12px);
  background: #ffffff;
  color: #1e3d2f;
  font-size: 10px;
  font-weight: bold;
  border: none;
  border-radius: 6px;
  padding: 4px 0;
  text-align: center;
  cursor: pointer;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  white-space: nowrap;

  &:hover {
    background: #f8f9fa;
  }
`;

const StoreMainBadge = styled.div`
  position: absolute;
  top: 6px;
  left: 6px;
  background: #1e3d2f;
  color: white;
  font-size: 9px;
  font-weight: bold;
  padding: 2px 5px;
  border-radius: 4px;
  z-index: 4;
`;

const ProductMainBadge = styled.div`
  position: absolute;
  top: -6px;
  left: -6px;
  background: #00a651;
  color: white;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  z-index: 10;
`;

const CounterText = styled.span`
  font-size: 12px;
  color: #8e94a0;
  margin-left: 4px;
`;

function ImageUploaderGrid({
  variant = 'product',
  images = [],
  onChange,
  onDelete,
  onSetMain,
  maxCount = 5,
}) {
  // 사진을 추가할 때 실행되는 함수
  const handleFileChange = (e) => {
    const files = Array.from(e.target.files);
    if (!files.length) return;

    if (images.length + files.length > maxCount) {
      alert(`사진은 최대 ${maxCount}장까지 등록 가능합니다.`);
      return;
    }

    const newImages = files.map((file, index) => {
      // 기존에 등록된 이미지가 전혀 없다면 이번 추가 리스트의 첫 번째 항목을 대표로 세팅
      const isFirst = images.length === 0 && index === 0;
      return {
        id: `temp-${Date.now()}-${index}`,
        url: URL.createObjectURL(file),
        file: file,
        isMain: isFirst,
      };
    });

    onChange([...images, ...newImages]);
    e.target.value = '';
  };

  // 사진을 삭제할 때 실행되는 함수
  const handleInternalDelete = async (targetImg) => {
    if (onDelete) {
      const isSuccess = await onDelete(targetImg);
      if (!isSuccess) return;
    }

    const filtered = images.filter((img) => img.id !== targetImg.id);

    // 대표 이미지가 지워졌다면, 남은 사진 중 첫 번째 사진을 자동으로 대표 지정
    if (filtered.length > 0 && !filtered.some((img) => img.isMain)) {
      filtered[0].isMain = true;
    }
    onChange(filtered);
  };

  // 사진을 대표로 지정할 때 실행되는 함수
  const handleInternalSetMain = async (targetImg) => {
    if (onSetMain) {
      const isSuccess = await onSetMain(targetImg);
      if (!isSuccess) return;
    }

    // 클릭된 이미지의 속성만 isMain: true로 변경하고 나머지는 false 처리
    const updatedImages = images.map((img) => ({
      ...img,
      isMain: img.id === targetImg.id,
    }));

    // 대표 이미지(isMain: true)를 찾아서 배열의 가장 앞으로 배치시킵니다.
    const sortedImages = [
      ...updatedImages.filter((img) => img.isMain),
      ...updatedImages.filter((img) => !img.isMain),
    ];

    onChange(sortedImages);
  };

  return (
    <Container>
      <UploaderRow>
        {images.length < maxCount && (
          <AddButtonCard>
            {variant === 'product' ? (
              <ImagePlus size={18} color="#8e94a0" />
            ) : (
              <Plus size={18} color="#8e94a0" />
            )}
            <span>추가</span>
            <input
              type="file"
              accept="image/*"
              multiple
              onChange={handleFileChange}
            />
          </AddButtonCard>
        )}

        {images.map((img) => (
          <ImageItemCard key={img.id} $variant={variant} $isMain={img.isMain}>
            <img src={img.url} alt="preview" />

            {img.isMain &&
              (variant === 'store' ? (
                <StoreMainBadge>대표</StoreMainBadge>
              ) : (
                <ProductMainBadge>
                  <Star size={10} fill="white" color="white" />
                </ProductMainBadge>
              ))}

            <HoverOverlay>
              <TrashDeleteButton
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleInternalDelete(img);
                }}
              >
                <Trash2 size={16} strokeWidth={2} />
              </TrashDeleteButton>

              {!img.isMain && (
                <MainAssignButton
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleInternalSetMain(img);
                  }}
                >
                  대표 지정
                </MainAssignButton>
              )}
            </HoverOverlay>
          </ImageItemCard>
        ))}

        {variant === 'product' && (
          <CounterText>
            {images.length}/{maxCount}장
          </CounterText>
        )}
      </UploaderRow>
    </Container>
  );
}

export default ImageUploaderGrid;
