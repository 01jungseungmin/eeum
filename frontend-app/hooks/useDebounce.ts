import { useState, useEffect } from 'react';

export function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    // 지정된 시간(delay) 후에 값을 업데이트합니다.
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    // 새 값이 입력되면 이전 타이머를 취소합니다. (클린업)
    return () => {
      clearTimeout(timer);
    };
  }, [value, delay]);

  return debouncedValue;
}