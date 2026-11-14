# Serve the existing website only. The Android app is not a web backend.
FROM nginx:1.30.6-alpine
ENV PORT=8080
ENV NGINX_ENVSUBST_FILTER=^PORT$
COPY deploy/default.conf.template /etc/nginx/templates/default.conf.template
COPY docs/ /usr/share/nginx/html/
EXPOSE 8080
