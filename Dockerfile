# ---- build stage: compile the Angular site ----
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build

# ---- run stage: Nginx serves the site and forwards /api to the backend ----
FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/iot-platform/browser /usr/share/nginx/html
EXPOSE 80
