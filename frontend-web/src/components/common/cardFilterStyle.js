import { css } from 'styled-components';

// 카드를 눌러 아래 목록을 필터링하는 페이지에서 쓰는 공통 스타일.
// 카드 styled 컴포넌트에 `${clickableCardStyle}`를 붙이고 $clickable / $active 를 넘긴다.
// (활성 카드 강조는 사장 문의 관리 카드와 같은 모양)
export const clickableCardStyle = css`
  ${(props) =>
    props.$clickable &&
    css`
      cursor: pointer;
      transition: all 0.2s ease-in-out;

      &:hover {
        border-color: ${props.$active ? '#111' : '#ccc'};
      }
    `}

  ${(props) =>
    props.$active &&
    css`
      border-color: #111;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
    `}
`;
