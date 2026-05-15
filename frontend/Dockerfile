# Frontend multi-stage build
FROM node:20-slim AS builder
WORKDIR /app
RUN apt-get update \
	&& apt-get install -y --no-install-recommends python3 build-essential \
	&& ln -sf /usr/bin/python3 /usr/bin/python \
	&& rm -rf /var/lib/apt/lists/*
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
	CYPRESS_INSTALL_BINARY=0 \
	PYTHON=/usr/bin/python3 \
	npm_config_python=/usr/bin/python3 \
	npm_config_fund=false \
	npm_config_audit=false
COPY package*.json ./
RUN npm ci --loglevel=verbose
RUN npm install --no-save @rollup/rollup-linux-x64-gnu@4.22.4
COPY . .
RUN npm run build -- --configuration production

FROM nginx:alpine
COPY --from=builder /app/dist/smarthire-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# runtime config mount point
RUN mkdir -p /usr/share/nginx/html/assets
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s CMD wget -qO- http://localhost/ >/dev/null || exit 1
CMD ["nginx","-g","daemon off;"]
