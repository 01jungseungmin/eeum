import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { Clock, Plus, X } from 'lucide-react';
import { productApi } from '../../../api/owner/productApi';
import TypeSelector from './modal/TypeSelector';
import SaleFormFields from './modal/SaleFormFields';
import ReservationFormFields from './modal/ReservationFormFields';
import MenuFormFields from './modal/MenuFormFields';
import ImageUploaderGrid from '../../common/ImageUploaderGrid';

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalContainer = styled.div`
  background: white;
  border-radius: 20px;
  width: 580px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  overflow: hidden;
`;

const ScrollContent = styled.div`
  padding: 35px;
  max-height: 90vh;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 10px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: #94a3b8;
  }
`;

const Title = styled.h2`
  margin-top: 0;
  margin-bottom: 25px;
  font-size: 20px;
  font-weight: bold;
  color: #1a1f2c;
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: bold;
  color: #333;
  margin-bottom: 8px;
  display: block;
  span {
    color: #ff4d4d;
    margin-left: 2px;
  }
`;

const FormGroup = styled.div`
  margin-bottom: 20px;
`;

const Input = styled.input`
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e9ecef;
  border-radius: 10px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  &:focus {
    border-color: #00a651;
  }
`;

const Select = styled.select`
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e9ecef;
  border-radius: 10px;
  font-size: 13px;
  outline: none;
  background: #fff;
  box-sizing: border-box;
  &:focus {
    border-color: #00a651;
  }
`;

const Textarea = styled.textarea`
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e9ecef;
  border-radius: 10px;
  font-size: 13px;
  outline: none;
  resize: none;
  box-sizing: border-box;
  &:focus {
    border-color: #00a651;
  }
`;

const ToggleRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  margin-bottom: 20px;
  font-size: 13px;
  font-weight: bold;
  color: #333;
  .title-area {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #495057;
  }
  .toggle-status {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #8e94a0;
    font-weight: normal;
  }
`;

const Switch = styled.label`
  position: relative;
  display: inline-block;
  width: 40px;
  height: 22px;
  input {
    opacity: 0;
    width: 0;
    height: 0;
  }
  .slider {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: #ccc;
    transition: 0.3s;
    border-radius: 22px;
  }
  .slider:before {
    position: absolute;
    content: '';
    height: 16px;
    width: 16px;
    left: 3px;
    bottom: 3px;
    background-color: white;
    transition: 0.3s;
    border-radius: 50%;
  }
  input:checked + .slider {
    background-color: #00a651;
  }
  input:checked + .slider:before {
    transform: translateX(18px);
  }
`;

const TimeDetailBlock = styled.div`
  background: #f8f9fa;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  .time-row {
    display: flex;
    gap: 12px;
    & > div {
      flex: 1;
    }
  }
  .info-text {
    font-size: 11px;
    color: #8e94a0;
    margin-top: -4px;
  }
`;

const OptionHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 25px;
  margin-bottom: 12px;

  label {
    font-size: 13px;
    font-weight: bold;
    color: #333;
  }

  .add-btn {
    color: #00a651;
    font-size: 13px;
    font-weight: bold;
    cursor: pointer;
    border: none;
    background: none;
    display: flex;
    align-items: center;
    gap: 4px;
    &:hover {
      opacity: 0.8;
    }
  }
`;

const OptionCard = styled.div`
  border: 1px solid #e9ecef;
  border-radius: 16px;
  padding: 20px;
  background: #f8f9fa;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const OptionTitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;

  .group-input {
    flex: 1;
    background: #ffffff;
  }

  .remove-group-icon {
    color: #94a3b8;
    cursor: pointer;
    transition: color 0.2s;
    &:hover {
      color: #495057;
    }
  }
`;

const OptionItemRow = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;

  .item-name-input {
    flex: 1;
    background: #ffffff;
  }

  .math-sign {
    color: #94a3b8;
    font-size: 14px;
    font-weight: 500;
    padding: 0 2px;
  }

  .price-input {
    width: 90px;
    background: #ffffff;
    text-align: center;
  }

  .currency {
    font-size: 13px;
    color: #94a3b8;
    padding-left: 2px;
    min-width: 16px;
  }
`;

const AddItemButton = styled.button`
  color: #00a651;
  font-size: 13px;
  font-weight: bold;
  cursor: pointer;
  border: none;
  background: none;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
  width: fit-content;
  margin-top: 2px;
  &:hover {
    opacity: 0.8;
  }
`;

const FooterButtonGroup = styled.div`
  display: flex;
  gap: 12px;
  margin-top: 30px;
`;

const CancelButton = styled.button`
  flex: 1;
  padding: 14px;
  background: white;
  border: 1px solid #e9ecef;
  border-radius: 10px;
  color: #495057;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
`;

const SubmitButton = styled.button`
  flex: 1;
  padding: 14px;
  background: #00a651;
  border: none;
  border-radius: 10px;
  color: white;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  &:hover {
    background: #008441;
  }
`;

function ProductFormModal({
  mode = 'CREATE',
  productId = null,
  onClose,
  onSuccess,
}) {
  const [loading, setLoading] = useState(false);

  const [productType, setProductType] = useState('SALE');
  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [basePrice, setBasePrice] = useState('');
  const [stockQuantity, setStockQuantity] = useState('');
  const [reservationCapacity, setReservationCapacity] = useState('');
  const [description, setDescription] = useState('');
  const [isTimeEnabled, setIsTimeEnabled] = useState(false);

  const [startTime, setStartTime] = useState('10:00');
  const [endTime, setEndTime] = useState('19:00');
  const [pickupInterval, setPickupInterval] = useState('30분 간격');

  const [images, setImages] = useState([]);
  const [options, setOptions] = useState([]);

  // 마운트 시 기존의 상세 정보를 받아와 상태 세팅
  useEffect(() => {
    if (mode === 'EDIT' && productId) {
      const fetchProductData = async () => {
        try {
          setLoading(true);

          const [detailResponse, imageResponse] = await Promise.all([
            productApi.getOwnerProductDetail(productId),
            productApi.getProductImages(productId), // 👈 새로 추가된 이미지 목록 조회 API
          ]);

          // 1️⃣ 상품 기본 상세 정보 매핑 바인딩
          if (detailResponse.data && detailResponse.data.success) {
            const data = detailResponse.data.data;

            setName(data.name || '');
            setCategoryId(data.categoryId || '');
            setDescription(data.description || '');
            setProductType(data.productType || 'SALE');

            if (data.productType === 'SALE') {
              setBasePrice(data.price || '');
              setStockQuantity(data.stock || '');
            } else if (data.productType === 'PREORDER') {
              setReservationCapacity(data.stock || '');
            }

            if (data.startTime || data.endTime) {
              setIsTimeEnabled(true);
              setStartTime(data.startTime || '10:00');
              setEndTime(data.endTime || '19:00');
              setPickupInterval(data.pickupInterval || '30분 간격');
            }

            setOptions(data.options || []);
          }

          if (imageResponse.data && imageResponse.data.success) {
            const imageList = imageResponse.data.data; // [{ imageId, imageUrl, displayOrder, thumbnail }]

            // ImageUploaderGrid가 사용하는 데이터 규격으로 트랜스포밍
            const mappedImages = imageList.map((img) => ({
              id: img.imageId, // Swagger: imageId ➔ 컴포넌트: id
              url: img.imageUrl, // Swagger: imageUrl ➔ 컴포넌트: url
              file: null, // 서버에서 가져온 주소이므로 File 객체는 null
              isMain: img.thumbnail, // Swagger: thumbnail(true/false) ➔ 컴포넌트: isMain
            }));

            // 정렬이 필요한 경우 displayOrder 기준으로 오름차순 정렬하여 상태 설정
            const sortedImages = mappedImages.sort(
              (a, b) => a.displayOrder - b.displayOrder,
            );
            setImages(sortedImages);
          }
        } catch (error) {
          console.error('수정용 데이터 로드 실패:', error);
          alert('기존 상품 정보를 불러오는데 실패했습니다.');
          onClose();
        } finally {
          setLoading(false);
        }
      };

      fetchProductData();
    }
  }, [mode, productId, onClose]);

  const handleTypeChange = (type) => {
    setProductType(type);
    setCategoryId('');
    setBasePrice('');
    setStockQuantity('');
    setReservationCapacity('');
  };

  const handleAddOptionGroup = () => {
    setOptions([
      ...options,
      { id: Date.now(), name: '', items: [{ name: '', price: 0 }] },
    ]);
  };

  const handleRemoveOptionGroup = (id) => {
    setOptions(options.filter((group) => group.id !== id));
  };

  const handleGroupTitleChange = (id, value) => {
    setOptions(
      options.map((group) =>
        group.id === id ? { ...group, name: value } : group,
      ),
    );
  };

  const handleAddOptionItem = (groupId) => {
    setOptions(
      options.map((group) => {
        if (group.id === groupId) {
          return { ...group, items: [...group.items, { name: '', price: 0 }] };
        }
        return group;
      }),
    );
  };

  const handleRemoveOptionItem = (groupId, itemIdx) => {
    setOptions(
      options.map((group) => {
        if (group.id === groupId) {
          return {
            ...group,
            items: group.items.filter((_, idx) => idx !== itemIdx),
          };
        }
        return group;
      }),
    );
  };

  const handleOptionItemChange = (groupId, itemIdx, field, value) => {
    setOptions(
      options.map((group) => {
        if (group.id === groupId) {
          const newSubItems = group.items.map((sub, idx) => {
            if (idx === itemIdx) {
              return {
                ...sub,
                [field]: field === 'price' ? Number(value) : value,
              };
            }
            return sub;
          });
          return { ...group, items: newSubItems };
        }
        return group;
      }),
    );
  };

  // 최종 전송 제어 (CREATE / EDIT 분기 처리)
  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!categoryId || categoryId === '') {
      alert('상품 카테고리를 반드시 선택해 주세요.');
      return;
    }

    let requestBody = {
      productType,
      name,
      categoryId: Number(categoryId),
      description,
      startTime: isTimeEnabled && productType !== 'MENU' ? startTime : null,
      endTime: isTimeEnabled && productType !== 'MENU' ? endTime : null,
      pickupInterval:
        isTimeEnabled && productType !== 'MENU' ? pickupInterval : null,
    };

    if (productType === 'SALE') {
      requestBody = {
        ...requestBody,
        basePrice: Number(basePrice) || 0,
        stockQuantity: stockQuantity === '' ? 0 : Number(stockQuantity),
        reservationCapacity: 0,
        options: options,
      };
    } else if (productType === 'PREORDER') {
      requestBody = {
        ...requestBody,
        basePrice: Number(basePrice) || 0,
        stockQuantity: 0,
        reservationCapacity:
          reservationCapacity === '' ? 0 : Number(reservationCapacity),
        options: [],
      };
    } else if (productType === 'MENU') {
      requestBody = {
        ...requestBody,
        basePrice: 0,
        stockQuantity: 0,
        reservationCapacity: 0,
        options: [],
      };
    }

    try {
      let response;
      let targetProductId = productId; // 수정 모드일 때는 폼 컴포넌트가 가진 productId 사용

      if (mode === 'EDIT') {
        response = await productApi.updateOwnerProduct(productId, requestBody);
      } else {
        response = await productApi.createProduct(requestBody);

        if (response.data && response.data.data) {
          targetProductId = response.data.data.productId;
        }
      }

      if (response.data && response.data.success) {
        const newImages = images.filter((img) =>
          String(img.id).startsWith('temp-'),
        );

        if (newImages.length > 0) {
          /*
           * 💡 [선택 전제 조건] 만약 파일 객체(img.file)를 S3에 먼저 업로드해서 진짜 URL을 받아야 한다면
           * 아래와 같은 가상의 업로드 루프 처리가 먼저 필요할 수 있습니다.
           *
           * const uploadedImages = await Promise.all(
           *   newImages.map(async (img) => {
           *     const s3Url = await productApi.uploadRawFile(img.file); // 서버 파일 업로드 API 호출 가정
           *     return { imageUrl: s3Url, thumbnail: img.isMain };
           *   })
           * );
           */

          // 3️⃣ 제공해주신 Swagger 명세(image_47d0c2.jpg) 구조에 맞춤 JSON Payload 구성
          const imagePayload = {
            images: newImages.map((img) => ({
              imageUrl: img.url, // Swagger 명세: imageUrl
              thumbnail: img.isMain, // Swagger 명세: thumbnail (대표 여부 true/false)
            })),
          };

          // 이미지 등록 API 호출 (POST /owner/products/{productId}/images)
          await productApi.registerProductImages(targetProductId, imagePayload);
        }

        alert(
          mode === 'EDIT'
            ? '상품 정보 및 이미지가 성공적으로 수정되었습니다.'
            : '상품 정보 및 이미지가 성공적으로 등록되었습니다.',
        );
        onSuccess();
        onClose();
      }
    } catch (error) {
      console.error('상품 처리 중 오류 발생:', error);
      alert(
        mode === 'EDIT'
          ? '상품 수정 또는 이미지 등록 중 오류가 발생했습니다.'
          : '상품 등록 또는 이미지 등록 중 오류가 발생했습니다.',
      );
    }
  };

  if (loading) {
    return (
      <ModalOverlay onClick={onClose}>
        <ModalContainer>
          <div
            style={{ padding: '60px', textAlign: 'center', color: '#cbd5e1' }}
          >
            🔄 정보를 준비 중입니다...
          </div>
        </ModalContainer>
      </ModalOverlay>
    );
  }

  // 대표 이미지 설정
  const handleSetMainImage = async (targetImg) => {
    const isConfirmed = window.confirm('대표이미지로 변경하시겠습니까?');

    // 사용자가 취소를 누르면 아무 행동도 하지 않고 리턴
    if (!isConfirmed) {
      return false;
    }

    try {
      const response = await productApi.setProductMainImage(
        productId,
        targetImg.id,
      );

      if (response.data && response.data.success) {
        alert('대표 이미지가 성공적으로 변경되었습니다.');
        return true;
      } else {
        alert('대표 이미지 변경에 실패했습니다: ' + response.data.message);
        return false;
      }
    } catch (error) {
      console.error('대표 이미지 변경 통신 에러:', error);
      alert('서버 오류로 인해 대표 이미지 설정에 실패했습니다.');
      return false;
    }
  };

  // 상품 이미지 삭제
  const handleDeleteImage = async (targetImg) => {
    if (!String(targetImg.id).startsWith('temp-')) {
      if (
        !window.confirm(
          '이 이미지를 즉시 삭제하시겠습니까?\n확인 클릭 시 서버에서 바로 삭제 처리됩니다.',
        )
      ) {
        return false; // 사용자가 취소하면 삭제 중단
      }

      try {
        const response = await productApi.deleteOwnerProductImage(
          productId,
          targetImg.id,
        );

        if (response.data && response.data.success) {
          alert('이미지가 성공적으로 삭제되었습니다.');
          return true; // true를 반환하면 프론트엔드 UI 화면 리스트에서도 제외됨
        } else {
          alert('이미지 삭제에 실패했습니다: ' + response.data.message);
          return false;
        }
      } catch (error) {
        console.error('서버 이미지 삭제 중 서버 통신 에러:', error);
        alert('이미지 삭제 중 오류가 발생했습니다.');
        return false;
      }
    }

    return true;
  };

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <ScrollContent>
          <Title>{mode === 'EDIT' ? '상품 수정' : '새 상품 등록'}</Title>
          <form onSubmit={handleSubmit}>
            <Label>
              상품 유형 <span>*</span>
            </Label>
            <TypeSelector
              currentType={productType}
              onChangeType={handleTypeChange}
            />

            <FormGroup>
              <Label>
                상품명 <span>*</span>
              </Label>
              <Input
                type="text"
                placeholder="상품명을 입력하세요"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </FormGroup>

            {productType === 'SALE' && (
              <SaleFormFields
                categoryId={categoryId}
                setCategoryId={setCategoryId}
                basePrice={basePrice}
                setBasePrice={setBasePrice}
                stockQuantity={stockQuantity}
                setStockQuantity={setStockQuantity}
              />
            )}

            {productType === 'PREORDER' && (
              <ReservationFormFields
                categoryId={categoryId}
                setCategoryId={setCategoryId}
                reservationCapacity={reservationCapacity}
                setReservationCapacity={setReservationCapacity}
              />
            )}

            {productType === 'MENU' && (
              <MenuFormFields
                categoryId={categoryId}
                setCategoryId={setCategoryId}
              />
            )}

            <FormGroup>
              <Label>상품 설명</Label>
              <Textarea
                rows="3"
                placeholder="상품 설명을 입력하세요"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </FormGroup>

            {productType !== 'MENU' && (
              <>
                <ToggleRow>
                  <div className="title-area">
                    <Clock size={16} color="#00a651" strokeWidth={2.5} />
                    <span>픽업/방문 시간 설정</span>
                  </div>
                  <div className="toggle-status">
                    <span>설정 {isTimeEnabled ? 'ON' : 'OFF'}</span>
                    <Switch>
                      <input
                        type="checkbox"
                        checked={isTimeEnabled}
                        onChange={(e) => setIsTimeEnabled(e.target.checked)}
                      />
                      <span className="slider" />
                    </Switch>
                  </div>
                </ToggleRow>

                {isTimeEnabled && (
                  <TimeDetailBlock>
                    <div className="time-row">
                      <div>
                        <Label>시작 시간</Label>
                        <Input
                          type="text"
                          value={startTime}
                          onChange={(e) => setStartTime(e.target.value)}
                          placeholder="오전 10:00"
                        />
                      </div>
                      <div>
                        <Label>종료 시간</Label>
                        <Input
                          type="text"
                          value={endTime}
                          onChange={(e) => setEndTime(e.target.value)}
                          placeholder="오후 07:00"
                        />
                      </div>
                    </div>
                    <div>
                      <Label>픽업 간격</Label>
                      <Select
                        value={pickupInterval}
                        onChange={(e) => setPickupInterval(e.target.value)}
                      >
                        <option value="30분 간격">30분 간격</option>
                        <option value="1시간 간격">1시간 간격</option>
                      </Select>
                      <div className="info-text">
                        고객이 {startTime} ~ {endTime} 사이에서 {pickupInterval}
                        으로 시간을 선택할 수 있습니다.
                      </div>
                    </div>
                  </TimeDetailBlock>
                )}
              </>
            )}

            <FormGroup>
              <Label>
                상품 이미지 (최대 5장){' '}
                <span
                  style={{
                    fontWeight: 'normal',
                    color: '#8e94a0',
                    fontSize: '11px',
                  }}
                >
                  · 별 클릭으로 대표 설정
                </span>
              </Label>
              <ImageUploaderGrid
                variant="product"
                images={images}
                onChange={setImages}
                onSetMain={handleSetMainImage}
                onDelete={handleDeleteImage}
                maxCount={5}
              />
            </FormGroup>

            {productType === 'SALE' && (
              <div>
                <OptionHeader>
                  <label>상품 옵션</label>
                  <button
                    type="button"
                    className="add-btn"
                    onClick={handleAddOptionGroup}
                  >
                    <Plus size={14} strokeWidth={2.5} /> 옵션 추가
                  </button>
                </OptionHeader>

                {options.map((group) => (
                  <OptionCard key={group.id}>
                    <OptionTitleRow>
                      <Input
                        type="text"
                        className="group-input"
                        value={group.name}
                        onChange={(e) =>
                          handleGroupTitleChange(group.id, e.target.value)
                        }
                        placeholder="옵션명 (예: 사이즈, 용량)"
                      />
                      <X
                        size={18}
                        className="remove-group-icon"
                        onClick={() => handleRemoveOptionGroup(group.id)}
                      />
                    </OptionTitleRow>

                    {group.items.map((subItem, itemIdx) => (
                      <OptionItemRow key={itemIdx}>
                        <Input
                          type="text"
                          className="item-name-input"
                          value={subItem.name}
                          onChange={(e) =>
                            handleOptionItemChange(
                              group.id,
                              itemIdx,
                              'name',
                              e.target.value,
                            )
                          }
                          placeholder="선택지 이름"
                        />

                        <span className="math-sign">+</span>

                        <Input
                          type="number"
                          className="price-input"
                          value={subItem.price || ''}
                          onChange={(e) =>
                            handleOptionItemChange(
                              group.id,
                              itemIdx,
                              'price',
                              e.target.value,
                            )
                          }
                          placeholder="0"
                        />

                        <span className="currency">원</span>
                      </OptionItemRow>
                    ))}

                    <AddItemButton
                      type="button"
                      onClick={() => handleAddOptionItem(group.id)}
                    >
                      <Plus size={14} strokeWidth={2.5} /> 선택지 추가
                    </AddItemButton>
                  </OptionCard>
                ))}
              </div>
            )}

            <FooterButtonGroup>
              <CancelButton type="button" onClick={onClose}>
                취소
              </CancelButton>
              <SubmitButton type="submit">
                {mode === 'EDIT' ? '수정 완료' : '등록하기'}
              </SubmitButton>
            </FooterButtonGroup>
          </form>
        </ScrollContent>
      </ModalContainer>
    </ModalOverlay>
  );
}

export default ProductFormModal;
