import React from 'react';
import styled from 'styled-components';

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
  z-index: 1200;
`;

const ModalContent = styled.div`
  background: white;
  border-radius: 20px;
  width: 440px;
  padding: 28px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);

  h3 {
    font-size: 18px;
    font-weight: bold;
    color: #1a1f2c;
    margin: 0 0 20px 0;
  }

  label {
    font-size: 13px;
    font-weight: bold;
    color: #495057;
    display: block;
    margin-bottom: 8px;
    span {
      color: #ff4d4d;
    }
  }

  input {
    width: 100%;
    border: 1px solid #00a651;
    border-radius: 12px;
    padding: 12px 16px;
    font-size: 14px;
    outline: none;
    box-sizing: border-box;
    margin-bottom: 6px;
    &::placeholder {
      color: #cbd5e1;
    }
  }

  .caption {
    font-size: 12px;
    color: #8e94a0;
    margin: 0 0 24px 0;
  }

  .btn-group {
    display: flex;
    gap: 12px;
    button {
      flex: 1;
      padding: 14px;
      border-radius: 12px;
      font-size: 14px;
      font-weight: bold;
      cursor: pointer;
    }
    .cancel {
      background: white;
      border: 1px solid #eef0f2;
      color: #495057;
      &:hover {
        background: #f8f9fa;
      }
    }
    .submit {
      background: #b1d8c4;
      border: none;
      color: white;
      &.active {
        background: #00a651;
        &:hover {
          background: #008f45;
        }
      }
    }
  }
`;

function AddCategoryModal({
  isOpen,
  onClose,
  onAdd,
  newCategoryName,
  setNewCategoryName,
}) {
  if (!isOpen) return null;

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContent onClick={(e) => e.stopPropagation()}>
        <h3>새 카테고리 추가</h3>

        <label>
          카테고리명 <span>*</span>
        </label>
        <input
          type="text"
          placeholder="예: 전/부침류, 국/탕류..."
          value={newCategoryName}
          onChange={(e) => setNewCategoryName(e.target.value)}
        />
        <p className="caption">카테고리는 목록 맨 아래에 추가됩니다.</p>

        <div className="btn-group">
          <button className="cancel" onClick={onClose}>
            취소
          </button>
          <button
            className={`submit ${newCategoryName.trim() ? 'active' : ''}`}
            disabled={!newCategoryName.trim()}
            onClick={onAdd}
          >
            추가하기
          </button>
        </div>
      </ModalContent>
    </ModalOverlay>
  );
}

export default AddCategoryModal;
