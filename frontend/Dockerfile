# UniLink frontend — Vite build served by nginx (single origin with the API).
# NOTE: prepared for the production compose stack; not build-verified in
# environments without Docker.
FROM node:20-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
