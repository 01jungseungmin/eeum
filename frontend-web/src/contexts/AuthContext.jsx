import { createContext, useState, useContext } from 'react';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [accessToken, setAccessToken] = useState(null);

  // 로그인 함수: 메모리에는 AT를, 스토리지는 보안상 최소한의 정보만
  const login = (token, role, rfToken) => {
    setAccessToken(token); // 메모리에 저장 (보안)
    localStorage.setItem('refreshToken', rfToken); // 리프레시는 스토리지에
    localStorage.setItem('role', role); // UI 분기용
  };

  return (
    <AuthContext.Provider value={{ accessToken, login }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
