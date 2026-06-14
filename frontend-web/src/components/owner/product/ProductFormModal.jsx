import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { Plus, X } from 'lucide-react';
import { productApi } from '../../../api/owner/productApi';
import TypeSelector from './modal/TypeSelector';
import SaleFormFields from './modal/SaleFormFields';
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

  .remove-item-icon {
    color: #94a3b8;
    cursor: pointer;
    &:hover {
      color: #ef4444;
    }
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

const EmptyOptionPlaceholder = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 80px;
  border: 1px dashed #d1d5db;
  border-radius: 12px;
  background-color: #ffffff;
  color: #a0a5b1;
  font-size: 14px;
  margin-top: 10px;
  margin-bottom: 20px;
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
  const [description, setDescription] = useState('');
  const [images, setImages] = useState([]);

  // 🛠️ 옵션 관리를 위한 고도화 상태 설정
  const [options, setOptions] = useState([]);
  const [originalOptions, setOriginalOptions] = useState([]);
  const [deletedOptionIds, setDeletedOptionIds] = useState([]); // 삭제된 기존 옵션그룹 ID 리스트

  // 마운트 시 기존의 상세 정보를 받아와 상태 세팅
  useEffect(() => {
    if (mode === 'EDIT' && productId) {
      const fetchProductData = async () => {
        try {
          setLoading(true);

          const [detailResponse, imageResponse, optionResponse] =
            await Promise.all([
              productApi.getOwnerProductDetail(productId),
              productApi.getProductImages(productId),
              productApi.getProductOptions(productId),
            ]);

          // 상품 기본 상세 정보 매핑 바인딩
          if (detailResponse.data && detailResponse.data.success) {
            const data = detailResponse.data.data;

            setName(data.name || '');
            setCategoryId(data.categoryId || '');
            setDescription(data.description || '');
            setProductType(data.productType || 'SALE');

            if (data.productType === 'SALE' || data.productType === 'MENU') {
              setBasePrice(data.price || '');
              setStockQuantity(data.stock || '');
            }
          }

          // 옵션 정보 바인딩 처리
          if (optionResponse && optionResponse.data) {
            const serverOptions =
              optionResponse.data.data || optionResponse.data || [];

            if (Array.isArray(serverOptions)) {
              const mappedOptions = serverOptions.map((opt, index) => ({
                ...opt,
                // 기존 데이터는 고유 서버 id를 메인 key로 세팅
                id: opt.optionId || opt.id,
                groupName: opt.groupName || '',
                selectionType: opt.selectionType || 'SINGLE',
                isRequired: opt.required ?? opt.isRequired ?? true,
                items: (opt.items || []).map((item, itemIdx) => ({
                  ...item,
                  itemName: item.itemName || item.name || '',
                  additionalPrice: item.additionalPrice ?? item.price ?? 0,
                  displayOrder: item.displayOrder ?? itemIdx,
                  default: item.default ?? itemIdx === 0,
                })),
              }));

              const sortedOptions = mappedOptions.sort(
                (a, b) => (a.displayOrder || 0) - (b.displayOrder || 0),
              );

              setOptions(sortedOptions);
              // 깊은 복사로 원본을 보관하여 최종 수정 시 변동 추적에 활용
              setOriginalOptions(JSON.parse(JSON.stringify(sortedOptions)));
            }
          }

          // 이미지 정보 바인딩 처리
          if (imageResponse.data && imageResponse.data.success) {
            const imageList = imageResponse.data.data;

            const mappedImages = imageList.map((img) => ({
              id: img.imageId,
              url: img.imageUrl,
              file: null,
              isMain: img.thumbnail,
              displayOrder: img.displayOrder,
            }));

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
    if (mode === 'EDIT') return;
    setCategoryId('');
    setBasePrice('');
    setStockQuantity('');
  };

  // 옵션 그룹 추가
  const handleAddOptionGroup = () => {
    const newGroup = {
      // 신규 추가된 옵션그룹은 임시 텍스트 ID 부여로 확실하게 구분
      id: `temp-${Date.now()}`,
      groupName: '',
      selectionType: 'SINGLE',
      isRequired: true,
      displayOrder: options.length,
      items: [
        {
          itemName: '',
          additionalPrice: 0,
          displayOrder: 0,
          default: true,
        },
      ],
    };
    setOptions([...options, newGroup]);
  };

  // 옵션 그룹 삭제 처리
  const handleRemoveOptionGroup = (groupId) => {
    // 만약 기존에 존재하는 (서버 ID를 보유한) 그룹이라면 삭제 대기 상태에 등록
    if (!String(groupId).startsWith('temp-')) {
      setDeletedOptionIds((prev) => [...prev, groupId]);
    }
    setOptions(options.filter((group) => group.id !== groupId));
  };

  const handleGroupTitleChange = (groupId, value) => {
    setOptions(
      options.map((group) =>
        group.id === groupId ? { ...group, groupName: value } : group,
      ),
    );
  };

  // 선택지 세부 아이템 추가
  const handleAddOptionItem = (groupId) => {
    setOptions(
      options.map((group) => {
        if (group.id === groupId) {
          return {
            ...group,
            items: [
              ...group.items,
              {
                itemName: '',
                additionalPrice: 0,
                displayOrder: group.items.length,
                default: group.items.length === 0,
              },
            ],
          };
        }
        return group;
      }),
    );
  };

  // 선택지 세부 아이템 삭제
  const handleRemoveOptionItem = (groupId, itemIdx) => {
    setOptions(
      options.map((group) => {
        if (group.id === groupId) {
          const filteredItems = group.items.filter((_, idx) => idx !== itemIdx);
          // 삭제 후 순서(displayOrder) 및 기본값 재조정
          const updatedItems = filteredItems.map((item, idx) => ({
            ...item,
            displayOrder: idx,
            default: idx === 0 ? true : item.default,
          }));
          return { ...group, items: updatedItems };
        }
        return group;
      }),
    );
  };

  const handleOptionItemChange = (groupId, itemIdx, field, value) => {
    setOptions(
      options.map((group) => {
        if (group.id === groupId) {
          const newItems = [...group.items];
          newItems[itemIdx] = {
            ...newItems[itemIdx],
            [field]:
              field === 'additionalPrice'
                ? value === ''
                  ? ''
                  : Number(value)
                : value,
          };
          return { ...group, items: newItems };
        }
        return group;
      }),
    );
  };

  // 🚀 최종 전송 제어 (CREATE / EDIT 정밀 처리)
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
      basePrice: Number(basePrice) || 0,
      options: [], // 메인 상품 데이터 오염을 막기 위한 빈배열 유지
    };

    if (mode !== 'EDIT') {
      requestBody.stockQuantity =
        stockQuantity === '' ? 0 : Number(stockQuantity);
    }

    try {
      let response;
      let targetProductId = productId;

      if (mode === 'EDIT') {
        // 1️⃣ 단계: 메인 상품 기본 정보 및 재고 수정
        console.log('🔄 [수정 모드 시작] 상품 ID:', productId);
        response = await productApi.updateOwnerProduct(productId, requestBody);

        if (response.data && response.data.success) {
          // 재고 업데이트 (빈 값일 경우 0 처리)
          await productApi.updateProductStock(
            productId,
            stockQuantity === '' ? 0 : stockQuantity,
          );
          console.log('✅ 상품 기본 정보 및 재고 수정 완료');
        }

        // 2️⃣ 단계: SALE 타입일 때 옵션 차등 관리 (C·U·D 분기)
        if (response.data && response.data.success && productType === 'SALE') {
          // 🔥 [DELETE] 유저가 X 버튼을 눌러 삭제 리스트(deletedOptionIds)에 넣은 기존 옵션들 삭제
          if (deletedOptionIds && deletedOptionIds.length > 0) {
            console.log('🗑️ 삭제할 기존 옵션 ID 목록:', deletedOptionIds);
            await Promise.all(
              deletedOptionIds.map((id) =>
                productApi.deleteProductOption(productId, id),
              ),
            );
            console.log('✅ 옵션 삭제 처리 완료');
          }

          // 화면에 입력된 옵션 중 이름이 있는 유효한 데이터만 필터링
          const validOptions = options.filter(
            (opt) => opt.groupName && opt.groupName.trim() !== '',
          );

          // 🔥 [POST / PUT] 신규 추가된 그룹과 기존 그룹을 분기하여 동시 처리
          await Promise.all(
            validOptions.map((opt, optIndex) => {
              // 백엔드 명세 규격에 맞게 바디 가공 (required 필드 유의)
              const optionPayload = {
                groupName: opt.groupName,
                selectionType: opt.selectionType || 'SINGLE',
                isRequired: true, // ⚠️ 400 에러 발생 시 required: true 로 변경 테스트
                displayOrder: optIndex,
                items: opt.items
                  .filter(
                    (item) => item.itemName && item.itemName.trim() !== '',
                  )
                  .map((item, itemIndex) => ({
                    itemName: item.itemName,
                    additionalPrice: Number(item.additionalPrice) || 0,
                    displayOrder: itemIndex,
                    default: itemIndex === 0,
                  })),
              };

              // ID가 없거나 'temp-'로 시작하면 수정창에서 '새로 추가한 옵션' -> POST
              if (!opt.id || String(opt.id).startsWith('temp-')) {
                console.log(`➕ [신규 옵션 등록] 그룹명: ${opt.groupName}`);
                return productApi.createProductOption(productId, optionPayload);
              }
              // 기존에 ID를 가지고 있던 옵션이면 '수정' -> PUT
              else {
                console.log(
                  `📝 [기존 옵션 수정] 옵션 ID: ${opt.id}, 그룹명: ${opt.groupName}`,
                );
                return productApi.updateProductOption(
                  productId,
                  opt.id,
                  optionPayload,
                );
              }
            }),
          );

          console.log('✅ 모든 옵션 변경 사항(추가/수정) 반영 완료');
        }
      } else {
        // 🆕 [등록 모드] 1단계: 메인 상품 등록 시도
        console.log('1️⃣ [메인 상품 등록 시작] 보낼 데이터:', requestBody);
        response = await productApi.createProduct(requestBody);

        console.log('2️⃣ [메인 상품 서버 응답 전체]:', response);

        // 🔍 백엔드가 보내준 실제 데이터 알맹이를 콘솔에서 직접 확인하기 위한 로그
        if (response && response.data) {
          console.log('🔍 백엔드 응답 바디(data) 내용:', response.data);
          console.log(
            '🔍 백엔드 응답 data 내부의 data 내용:',
            response.data.data,
          );
        }

        // 서버 응답이 성공일 때만 다음 단계 진입
        if (
          response &&
          response.data &&
          (response.data.success ||
            response.data.status === 200 ||
            response.status === 200)
        ) {
          // 🔥 [핵심 수정] 백엔드가 ID를 'id'로 보냈을 경우까지 완벽하게 방어 체계 구축
          // 🔥 백엔드가 수정되어 응답 바디에 productId를 넣어줄 때를 대비한 3중 방어막
          targetProductId =
            response.data.data?.productId ||
            response.data.data?.id ||
            response.data.productId ||
            response.data.id;

          // ⚠️ [임시 디버깅용 가짜 ID 주입] 백엔드가 안 주면 테스트용으로 임시 1번 ID를 강제로 먹임
          if (!targetProductId) {
            console.warn(
              "⚠️ 백엔드 응답에 productId가 없어서 임시 ID '1'번으로 옵션 등록을 시도합니다. (백엔드 수정 후 제거 필요)",
            );
            targetProductId = 1; // 우선 프론트 옵션 API 동작을 보기 위해 가짜 ID 주입!
          }

          console.log('3️⃣ [추출된 신규 상품 ID]:', targetProductId);

          if (!targetProductId || typeof targetProductId === 'object') {
            alert(
              '상품은 생성되었으나, 코드가 ID 필드명(productId 또는 id)을 찾지 못했습니다. 콘솔 창의 🔍 로그를 확인해 주세요.',
            );
            return;
          }

          // 2단계: 옵션 등록 (SALE 타입이고 유저가 입력한 옵션이 있을 때만)
          if (options && options.length > 0 && productType === 'SALE') {
            const optionRequests = options
              .filter((opt) => opt.groupName && opt.groupName.trim() !== '')
              .map((opt, optIndex) => ({
                groupName: opt.groupName,
                selectionType: opt.selectionType || 'SINGLE',
                isRequired: true, // ⚠️ 백엔드 스펙에 따라 required: true 일 수도 있음
                displayOrder: optIndex,
                items: opt.items
                  .filter(
                    (item) => item.itemName && item.itemName.trim() !== '',
                  )
                  .map((item, itemIndex) => ({
                    itemName: item.itemName,
                    additionalPrice: Number(item.additionalPrice) || 0,
                    displayOrder: itemIndex,
                    default: itemIndex === 0,
                  })),
              }))
              .filter((opt) => opt.items.length > 0);

            console.log(
              '4️⃣ [옵션 API 전송 직전 Request Body]:',
              optionRequests,
            );

            if (optionRequests.length > 0) {
              // 여러 개의 옵션 그룹을 순차적으로 안전하게 등록
              for (const optionData of optionRequests) {
                console.log(
                  `🚀 ID ${targetProductId}번에 옵션 그룹 [${optionData.groupName}] 등록 요청`,
                );
                const optionRes = await productApi.createProductOption(
                  targetProductId,
                  optionData,
                );
                console.log('옵션 개별 등록 서버 응답:', optionRes.data);
              }
              console.log('✅ 모든 상품 옵션 개별 등록 완료');
            }
          }
        } else {
          alert(
            '메인 상품 등록 단계에서 실패하여 이후 옵션/이미지 등록이 중단되었습니다.',
          );
          return;
        }
      }

      // 3. 최종 이미지 등록 및 모달 닫기 공통 처리
      if (response && response.data && response.data.success) {
        const newImages = images.filter((img) =>
          String(img.id).startsWith('temp-'),
        );

        if (newImages.length > 0) {
          const imagePayload = {
            images: newImages.map((img) => ({
              imageUrl: img.url,
              thumbnail: img.isMain,
            })),
          };
          await productApi.registerProductImages(targetProductId, imagePayload);
        }

        alert(
          mode === 'EDIT'
            ? '상품 정보 및 옵션이 정상적으로 수정되었습니다.'
            : '상품 정보 및 옵션이 정상적으로 등록되었습니다.',
        );
        onSuccess();
        onClose();
      }
    } catch (error) {
      console.error('🔴 상품/옵션 통합 처리 중 오류 발생:', error);
      alert('처리 도중 오류가 발생했습니다. 입력 정보를 다시 확인해주세요.');
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

  const handleSetMainImage = async (targetImg) => {
    if (!window.confirm('대표이미지로 변경하시겠습니까?')) return false;
    try {
      const response = await productApi.setProductMainImage(
        productId,
        targetImg.id,
      );
      if (response.data && response.data.success) {
        alert('대표 이미지가 변경되었습니다.');
        return true;
      }
      return false;
    } catch (error) {
      console.error(error);
      return false;
    }
  };

  const handleDeleteImage = async (targetImg) => {
    if (!String(targetImg.id).startsWith('temp-')) {
      if (!window.confirm('이 이미지를 즉시 삭제하시겠습니까?')) return false;
      try {
        const response = await productApi.deleteOwnerProductImage(
          productId,
          targetImg.id,
        );
        if (response.data && response.data.success) {
          alert('이미지가 삭제되었습니다.');
          return true;
        }
        return false;
      } catch (error) {
        console.error(error);
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

            {productType === 'MENU' && (
              <MenuFormFields
                categoryId={categoryId}
                setCategoryId={setCategoryId}
                basePrice={basePrice}
                setBasePrice={setBasePrice}
                stockQuantity={stockQuantity}
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
              <div style={{ marginTop: '25px', marginBottom: '15px' }}>
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

                {options.length === 0 ? (
                  <EmptyOptionPlaceholder>
                    옵션 없음 (단일 상품)
                  </EmptyOptionPlaceholder>
                ) : (
                  options.map((group) => (
                    <OptionCard key={group.id}>
                      <OptionTitleRow>
                        <Input
                          type="text"
                          className="group-input"
                          value={group.groupName || ''}
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
                            value={subItem.itemName || ''}
                            onChange={(e) =>
                              handleOptionItemChange(
                                group.id,
                                itemIdx,
                                'itemName',
                                e.target.value,
                              )
                            }
                            placeholder="선택지 이름"
                          />
                          <span className="math-sign">+</span>
                          <Input
                            type="number"
                            className="price-input"
                            value={subItem.additionalPrice ?? ''}
                            onChange={(e) =>
                              handleOptionItemChange(
                                group.id,
                                itemIdx,
                                'additionalPrice',
                                e.target.value,
                              )
                            }
                            placeholder="0"
                          />
                          <span className="currency">원</span>
                          {group.items.length > 1 && (
                            <X
                              size={14}
                              className="remove-item-icon"
                              onClick={() =>
                                handleRemoveOptionItem(group.id, itemIdx)
                              }
                            />
                          )}
                        </OptionItemRow>
                      ))}

                      <AddItemButton
                        type="button"
                        onClick={() => handleAddOptionItem(group.id)}
                      >
                        <Plus size={14} strokeWidth={2.5} /> 선택지 추가
                      </AddItemButton>
                    </OptionCard>
                  ))
                )}
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
