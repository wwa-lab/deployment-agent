FROM node:22-alpine

WORKDIR /app

COPY package.json ./
RUN npm install

COPY . .
RUN npm run build

RUN apk add --no-cache nginx
COPY nginx.conf /etc/nginx/http.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
