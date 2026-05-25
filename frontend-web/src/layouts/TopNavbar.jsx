import React, { useMemo } from 'react';
import styled from 'styled-components';
import { useLocation } from 'react-router-dom';
import { Search, Bell, ChevronDown } from 'lucide-react';
import { findMenuByPath } from '../config/MenuConfig';

const NavContainer = styled.div`
  height: 80px;
  background: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30px;
  border-bottom: 1px solid #f0f0f0;
`;

const TitleSection = styled.div`
  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: bold;
    color: #1a392a;
  }
  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: #999;
  }
`;

const RightSection = styled.div`
  display: flex;
  align-items: center;
  gap: 20px;
`;
const SearchBar = styled.div`
  position: relative;
  display: flex;
  align-items: center;
  background: #f8f9fa;
  border-radius: 20px;
  padding: 8px 15px;
  width: 280px;
  input {
    border: none;
    background: transparent;
    margin-left: 10px;
    font-size: 14px;
    outline: none;
    width: 100%;
  }
`;
const IconBadge = styled.div`
  position: relative;
  cursor: pointer;
  .badge {
    position: absolute;
    top: -5px;
    right: -5px;
    background: #ff4d4f;
    color: white;
    font-size: 10px;
    padding: 2px 5px;
    border-radius: 10px;
    border: 2px solid white;
  }
`;
const ProfileBox = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border: 1px solid #eee;
  border-radius: 30px;
  cursor: pointer;
  .avatar {
    width: 32px;
    height: 32px;
    background: #4caf50;
    color: white;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
  }
  .info {
    text-align: left;
    .name {
      font-size: 14px;
      font-weight: bold;
    }
    .role {
      font-size: 11px;
      color: #999;
    }
  }
`;

function TopNavbar() {
  const { pathname } = useLocation();

  const currentMenu = useMemo(() => findMenuByPath(pathname), [pathname]);

  const today = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date());

  const menuName = currentMenu?.name || '상세 정보';
  const menuSubtitle = currentMenu?.subtitle || '상세 내역을 확인합니다.';

  return (
    <NavContainer>
      <TitleSection>
        <h2>{menuName}</h2>
        <p>
          {today} · {menuSubtitle}
        </p>
      </TitleSection>

      <RightSection>
        <SearchBar>
          <Search size={18} color="#bbb" />
          <input type="text" placeholder="검색..." />
        </SearchBar>

        <IconBadge>
          <Bell size={22} color="#666" />
          <span className="badge">8</span>
        </IconBadge>

        <ProfileBox>
          <div className="avatar">김</div>
          <div className="info">
            <div className="name">김사장</div>
            <div className="role">사장 회원</div>
          </div>
          <ChevronDown size={16} color="#bbb" />
        </ProfileBox>
      </RightSection>
    </NavContainer>
  );
}

export default TopNavbar;
