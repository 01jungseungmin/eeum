# 1단계: 정적 파일 빌드
FROM node:20.19.0-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite는 VITE_* 값을 런타임이 아니라 이 빌드 시점에 번들에 박아 넣는다.
# .env.local은 gitignore/dockerignore 대상이라 이미지 안으로 안 들어오므로,
# 결제(PortOne)처럼 안전한 기본값이 없는 값은 반드시 build-arg로 주입해야 한다.
ARG VITE_PORTONE_STORE_ID
ARG VITE_PORTONE_CHANNEL_KEY
ENV VITE_PORTONE_STORE_ID=$VITE_PORTONE_STORE_ID
ENV VITE_PORTONE_CHANNEL_KEY=$VITE_PORTONE_CHANNEL_KEY

RUN npm run build

# 2단계: nginx로 정적 파일 서빙 + API/WS 프록시
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:80/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
